#!/usr/bin/env bash
# ============================================================================
# ci-fork-dualcore.sh — personal/fork dual-core (4k + 16k) + PGO release APK.
# ----------------------------------------------------------------------------
# Same recipe as ci-nightly-dualcore.sh (the core compiled twice, at 0x1000 and
# 0x4000 host page size, merged into one universal APK with PGO=optimize), but
# with fork-friendly signing instead of the upstream rotation lineage:
#
#   * If APK_RELEASE_KS (+ _PASS / _ALIAS / _KEY_PASS) is provided — decoded by
#     the apk-release.yml workflow from the APK_RELEASE_* repo secrets — the APK
#     is signed with that stable key, so rebuilds update over each other.
#   * Otherwise a throwaway debug key is generated on the runner and the APK is
#     signed with it. It installs and runs fine, but it can NEVER update over an
#     upstream-signed (or otherwise-signed) install of the same package, nor over
#     a previous throwaway build: uninstall first, every time.
#
# This is for personal/fork releases (side builds, device-specific tuning).
# NEVER ship its output as an official ARMSX2 release.
#
# Runs from platforms/android (the gradle root); the workflow sets
# working-directory accordingly.
#
# Env:
#   VC, VN                versionCode / versionName            (required)
#   FLAVOR                Github (sideload) | Play (default Github)
#   APK_ID                applicationId (default com.armsx2). A distinct id
#                         installs BESIDE any other ARMSX2 instead of replacing
#                         it — the way to avoid the uninstall-first dance below.
#   APP_LABEL             android:label override (default: @string/app_name when
#                         APK_ID is com.armsx2, else @string/app_name_nightly)
#   CHANNEL               BuildConfig channel (default stable)
#   PROF                  merged .profdata  (default pgo/armsx2.profdata; if the
#                         file is absent the build falls back to PGO=none rather
#                         than failing, so a missing profile never breaks CI)
#   OUT                   output apk path
#   APK_RELEASE_KS / APK_RELEASE_KS_PASS / APK_RELEASE_KEY_ALIAS / APK_RELEASE_KEY_PASS
#                         fork-owned stable signing key (optional; see above)
# ============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # platforms/android
GRADLE="$ROOT_DIR/gradlew"

VC="${VC:?set VC=<versionCode>}"
VN="${VN:?set VN=<versionName>}"
FLAVOR="${FLAVOR:-Github}"                                    # Github | Play
flavor_lc="$(printf '%s' "$FLAVOR" | tr '[:upper:]' '[:lower:]')"
APK_ID="${APK_ID:-com.armsx2}"
if [[ -n "${APP_LABEL:-}" ]]; then
	LABEL="$APP_LABEL"
elif [[ "$APK_ID" == "com.armsx2" ]]; then
	LABEL="@string/app_name"
else
	LABEL="@string/app_name_nightly"
fi
CHANNEL="${CHANNEL:-stable}"
PROF="${PROF:-$ROOT_DIR/pgo/armsx2.profdata}"
OUT="${OUT:-$ROOT_DIR/app/build/outputs/apk/fork/ARMSX2-fork-${VN}-vc${VC}.apk}"

# --- resolve Android SDK build-tools (Linux CI exports ANDROID_HOME/SDK_ROOT) ---
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK" && -d "$SDK" ]] || { echo "FATAL: ANDROID_HOME/ANDROID_SDK_ROOT not set" >&2; exit 1; }
BT="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
ZIPALIGN="$BT/zipalign"; APKSIGNER="$BT/apksigner"; AAPT="$BT/aapt2"
# aapt2 is required (not optional): the VERIFY step uses it to assert the APK really carries
# $APK_ID, and a missing tool must fail the build rather than silently skip that check.
for t in "$ZIPALIGN" "$APKSIGNER" "$AAPT"; do
	[[ -x "$t" ]] || { echo "FATAL: missing build-tool $t" >&2; exit 1; }
done

# --- PGO: optimize when the profile exists, else fall back so CI never fails ---
if [[ -f "$PROF" ]]; then
	PGO_ARGS=(-Parmsx2.pgo=optimize -Parmsx2.pgoProfile="$PROF")
	echo "PGO: optimize using $PROF ($(( $(wc -c < "$PROF") / 1024 / 1024 )) MB)"
else
	PGO_ARGS=(-Parmsx2.pgo=none)
	echo "WARNING: PGO profile not found at $PROF — building PGO=none (slower core)." >&2
fi

if [[ -z "${DISCORD_SDK_DIR:-}" ]]; then
	echo "NOTE: DISCORD_SDK_DIR unset — this build has NO Discord integration (expected on forks)." >&2
fi

WORK="$ROOT_DIR/app/build/fork-dualcore"; rm -rf "$WORK"
mkdir -p "$WORK/lib-stage/lib/arm64-v8a" "$(dirname "$OUT")"
BUILT="$ROOT_DIR/app/build/outputs/apk/${flavor_lc}/release/app-${flavor_lc}-release.apk"

fsize() { stat -c%s "$1" 2>/dev/null || stat -f%z "$1"; }   # Linux || macOS

build_core() { # pagesize libname
	local ps="$1" ln="$2"
	echo "=== core $ln (page=$ps, flavor=$FLAVOR, id=$APK_ID, channel=$CHANNEL) ==="
	rm -f "$BUILT"
	"$GRADLE" -p "$ROOT_DIR" ":app:assemble${FLAVOR}Release" \
		-Parmsx2.hostPageSize="$ps" \
		-Parmsx2.nativeLibName="$ln" \
		"${PGO_ARGS[@]}" \
		-Parmsx2.versionCode="$VC" \
		-Parmsx2.versionName="$VN" \
		-Parmsx2.applicationId="$APK_ID" \
		-Parmsx2.channel="$CHANNEL" \
		-Parmsx2.appLabel="$LABEL" \
		--stacktrace
	[[ -f "$BUILT" ]] || { echo "FATAL: gradle produced no APK for $ln ($BUILT)" >&2; exit 1; }
	unzip -l "$BUILT" "lib/arm64-v8a/lib${ln}.so" >/dev/null \
		|| { echo "FATAL: lib${ln}.so missing from $BUILT" >&2; exit 1; }
	cp -f "$BUILT" "$WORK/base-${ln}.apk"
	unzip -p "$BUILT" "lib/arm64-v8a/lib${ln}.so" > "$WORK/lib-stage/lib/arm64-v8a/lib${ln}.so"
}

build_core 0x1000 emucore_4k
build_core 0x4000 emucore_16k

for so in emucore_4k emucore_16k; do
	sz="$(fsize "$WORK/lib-stage/lib/arm64-v8a/lib${so}.so")"
	echo "  lib${so}.so = $(( sz / 1024 / 1024 )) MB"
	[[ "$sz" -gt 10000000 ]] || { echo "FATAL: lib${so}.so too small ($sz bytes)" >&2; exit 1; }
done

# --- merge: 4k APK as base, drop old signatures + both cores, re-add them compressed ---
# (not stored: Android extracts every library at install anyway, see build-release-apk.sh)
UNS="$WORK/universal-unsigned.apk"; ALN="$WORK/universal-aligned.apk"
cp -f "$WORK/base-emucore_4k.apk" "$UNS"
zip -qd "$UNS" "META-INF/*" >/dev/null 2>&1 || true
zip -qd "$UNS" "lib/arm64-v8a/libemucore_4k.so" "lib/arm64-v8a/libemucore_16k.so" >/dev/null 2>&1 || true
( cd "$WORK/lib-stage" && zip -qr -9 "$UNS" lib )
"$ZIPALIGN" -f -P 16 4 "$UNS" "$ALN"

# --- sign ----------------------------------------------------------------------
rm -f "$OUT"
if [[ -n "${APK_RELEASE_KS:-}" && -f "${APK_RELEASE_KS:-/nope}" \
   && -n "${APK_RELEASE_KS_PASS:-}" && -n "${APK_RELEASE_KEY_ALIAS:-}" ]]; then
	echo "Signing with the fork-owned stable key (rebuilds update over each other)."
	"$APKSIGNER" sign \
		--ks "$APK_RELEASE_KS" --ks-key-alias "$APK_RELEASE_KEY_ALIAS" \
		--ks-pass "pass:$APK_RELEASE_KS_PASS" --key-pass "pass:${APK_RELEASE_KEY_PASS:-$APK_RELEASE_KS_PASS}" \
		--in "$ALN" --out "$OUT"
	echo "FORK-SIGNING:stable"
else
	echo "==================================================================" >&2
	echo "WARNING: no APK_RELEASE_* signing secrets — signing with a THROWAWAY" >&2
	echo "debug key generated on this runner. This APK installs and runs fine," >&2
	echo "but it can NEVER update over an upstream-signed (or otherwise-signed)" >&2
	echo "install of the same package, nor over a previous throwaway build." >&2
	echo "Uninstall first, every time — or add the APK_RELEASE_* repo secrets" >&2
	echo "(see .github/workflows/apk-release.yml) for a stable certificate." >&2
	echo "==================================================================" >&2
	KEYTOOL="${JAVA_HOME:-}/bin/keytool"
	[[ -x "$KEYTOOL" ]] || KEYTOOL="$(command -v keytool || true)"
	[[ -n "$KEYTOOL" ]] || { echo "FATAL: keytool not found (no JAVA_HOME and none on PATH)" >&2; exit 1; }
	"$KEYTOOL" -genkeypair \
		-keystore "$WORK/throwaway.jks" -alias forkkey \
		-keyalg RSA -keysize 2048 -validity 1095 \
		-storepass android -keypass android \
		-dname "CN=ARMSX2 Fork Build, OU=Personal, O=Fork, C=MX" >/dev/null 2>&1
	"$APKSIGNER" sign \
		--ks "$WORK/throwaway.jks" --ks-key-alias forkkey \
		--ks-pass pass:android --key-pass pass:android \
		--in "$ALN" --out "$OUT"
	echo "FORK-SIGNING:throwaway"
fi

echo; echo "================= VERIFY =================="
echo "-- both cores present --"
unzip -l "$OUT" | grep -E "libemucore_(4k|16k)\.so" || { echo "FATAL: cores missing" >&2; exit 1; }
echo "-- 16k alignment --"; "$ZIPALIGN" -c -P 16 4 "$OUT" && echo "  align OK"
echo "-- signature --"; "$APKSIGNER" verify "$OUT" && echo "  sig OK"
# The whole point of APK_ID is a side-by-side install when it differs from com.armsx2; if the -P
# property ever fails to reach AGP the APK silently comes out as com.armsx2 again and would
# replace (or refuse to install over) the wrong app instead. Fail closed rather than publish
# that. (aapt2 is required at the top of the script, so this cannot be skipped by a missing tool.)
badging="$("$AAPT" dump badging "$OUT" 2>/dev/null)"
echo "$badging" | grep -E "package: name|versionCode|versionName" | head -2
echo "$badging" | grep -q "package: name='${APK_ID}'" \
	|| { echo "FATAL: APK package is not ${APK_ID} — applicationId property did not take" >&2; exit 1; }
echo; echo "OUTPUT: $OUT"
echo "FORK-DUALCORE-DONE"
