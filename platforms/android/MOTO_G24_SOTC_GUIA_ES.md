# Moto G24 + Shadow of the Colossus — Guía de mega-optimización (≈20 FPS estables)

> Guía oficial de ARMSX2 para el **Motorola Moto G24** (y teléfonos de su misma
> clase: Helio G85, Mali-G52). El objetivo: que **Shadow of the Colossus** corra
> a **mínimo ~20 FPS estables**, con drivers, presets y ajustes del sistema.

---

## 0. Tu hardware, en 30 segundos

| Pieza | Moto G24 | Qué significa para PS2 |
|---|---|---|
| CPU | Helio G85: 2x Cortex-A75 @ 2.0 GHz + 6x A55 | Los 2 núcleos A75 hacen casi todo el trabajo del EE/VU. Son justos: SOTC es de los juegos más CPU-exigentes de PS2. |
| GPU | Mali-G52 MC2 (2 núcleos) | Fill-rate bajo; el blending y la resolución alta la matan. Vulkan obligatorio. |
| RAM | 4 u 8 GB | Suficiente; no es el cuello de botella. |
| Pantalla | 720x1612, 90 Hz | 720p ayuda (menos píxeles que presentar). |
| Android | 14 (API 34) | Compatible con ADPF y con el driver MaliSX2. |

**Por qué SOTC cuesta tanto:** mundo abierto con mucha geometría (VU1),
físicas del coloso y de Wander (EE), y toneladas de blending alfa —agua,
polvo, pelaje, bloom— que en Mali se paga caro. En PS2 real el juego ya baja
a ~20 FPS en peleas duras; aquí buscamos igualar eso, no superarlo.

---

## 1. Instala el driver MaliSX2 (el "driver custom" para tu Mali)

Tu Moto G24 **no** puede usar drivers Turnip (esos son solo para Snapdragon/
Adreno). Para Mali, ARMSX2 trae su propio driver Vulkan: **MaliSX2**, y tu
Mali-G52 está soportado. Se instala desde la app, sin root:

1. **En el setup inicial:** en la página **Driver de GPU** descarga
   `ARMSX2 · MaliSX2` (la versión más nueva que liste).
2. **Si ya pasaste el setup:** Ajustes → driver de GPU → descargar
   `ARMSX2 · MaliSX2` y seleccionarlo.
3. **Fíjalo solo para SOTC (recomendado):** abre SOTC, pausa, ve a los ajustes
   del juego (scope **Juego**, no Global) y deja MaliSX2 como driver de ese
   título. Así, si otro juego se lleva mal con él, no te afecta.

**Si algo sale mal con MaliSX2** (cuelgues al arrancar, pantalla negra):
vuelve al driver del sistema para ese juego. Orden de pruebas:
`MaliSX2 → driver del sistema (Vulkan) → OpenGL del sistema`.
No toques nada más entre prueba y prueba.

> Nota técnica: en algunos Mali el driver necesita un kernel kbase más nuevo
> que el del teléfono. Si el tuyo es muy viejo, ARMSX2 vuelve solo al driver
> del sistema. No se rompe nada por intentarlo.

---

## 2. Preset Ultra global (tu base para todos los juegos)

En instalaciones nuevas sobre un Moto G24 (o cualquier Helio G85 / Mali-G52),
ARMSX2 **ya arranca con el preset Ultra**. Lo verás confirmado en el setup,
página **Rendimiento**: tarjeta *“Preset Ultra activado ⚡”*.

Si actualizaste desde una versión anterior o lo cambiaste sin querer:

1. Pausa → **Rendimiento** → **Perfil de speedhack** → **Ultra**.
2. Hazlo en scope **Global** para que sea tu base en todos los juegos.

El preset Ultra aplica (ver tabla completa en §6):

- EE Cycle Skip 2 + todos los speedhacks seguros + **Hilo del IOP** + MTVU
- Resolución interna 1x (nativa de PS2) + superficie de salida a **448p**
  (el composer la estira gratis a tu panel 720p)
- Blending **mínimo**, sin mipmaps HW, precarga parcial de texturas
- Pasadas de render **coalescentes** (win gratis en GPUs tiler como Mali)
- Renderer **Vulkan** fijo (donde vive el path de MaliSX2)

---

## 3. Preset Shadow of the Colossus (un toque, solo para este juego)

Con SOTC cargado (cualquier región: `SCUS-97472`, `SCES-53326`, `SCPS-15097`,
`SCPS-19320`, `SCPS-19335`, `SCKA-20061`, `SCKA-20106`, demos incluidas):

1. Pausa → **Rendimiento**.
2. Verás la tarjeta **Preset Shadow of the Colossus**.
3. Toca **Aplicar preset 20 FPS**.

Eso aplica el preset Ultra **más** los dos movimientos específicos de SOTC:

| Ajuste | Valor | Por qué |
|---|---|---|
| EE Cycle Rate | 75% (underclock suave) | SOTC en un A75 a 2 GHz va limitado por EE; este es el mayor ahorro de CPU y el juego lo tolera bien. |
| Resolución interna | 0.75x (sub-nativa) | SOTC abusa del blending/fill; bajar de 1x alivia los 2 núcleos del G52. |

El preset **se guarda solo para SOTC** (scope Juego) y **respeta tu Ajuste
automático de rendimiento**: si lo tenías activado, sigue activado para que
el núcleo pueda apretar más (gobernador dinámico de EE, etc.) donde el preset
no llegue.

> La base de datos del juego ya hace su parte sola: hilo IOP, fix OPHFlag
> (fps estables) y sombras volumétricas a resolución nativa. No desactives
> los game fixes.

---

## 4. Ajustes extra del teléfono y de la app (la otra mitad del “de todo”)

En **Rendimiento** (scope Global):

| Ajuste | Ponlo en | Motivo |
|---|---|---|
| Sugerencia de reloj de CPU (ADPF) | **ON** | Tu Android 14 lo soporta; le dice al governor que suba frecuencia en los hilos de emulación. Mide con tu propio A/B, pero en el G85 suele ayudar. |
| Rendimiento sostenido | **OFF** | Pone techo al reloj máximo para aguantar horas. En el G24 ese techo te roba FPS en SOTC. |
| Modo de baja latencia | OFF | La cola de 2 frames da más suavidad; latencia cero no sirve si el juego va a 20 fps. |
| Límite de FPS de pantalla | 0 (off)… o **20** | Off = todos los frames que el teléfono pueda. **20** = pacing perfectamente parejo si prefieres estable sobre máximo (ver §7). |
| Afinidad (Affinity) | Performance Cores (7) | El default; deja los hilos del emu en los 2 A75. |

En el teléfono:

- **Pantalla a 60 Hz** mientras juegas (Ajustes → Pantalla → frecuencia).
  A 90 Hz el sistema gasta batería/GPU en presentar de más.
- **No juegues cargando** (calor = throttling en minutos en un G85).
- Quita la funda gruesa / juega en sitio fresco. El G85 throttlea; cada grado cuenta.
- Modo avión + cerrar apps pesadas (redes sociales con video, etc.).
- Brillo moderado: la pantalla es de lo que más calienta en sesiones largas.
- **Versión del juego:** si tienes ambas, la **PAL** (`SCES-53326`, 50 Hz) pide
  menos frames/segundo a velocidad completa que la NTSC (60 Hz). La diferencia
  se nota en peleas.
- **Audio:** si crepita cuando baja de 20 fps, sube el buffer de audio a
  80–100 ms (Audio). El time-stretch ya viene activado.

---

## 5. Qué esperar (expectativas honestas)

Con driver + Ultra + preset SOTC, en un Moto G24 típico:

- **Campo abierto / a caballo:** 20–30 FPS. ✅ meta cumplida.
- **Peleas de coloso normales:** ~20 FPS con bajones puntuales. ✅ jugable.
- **Bosque, agua, colosos 13/16 y mucho polvo en pantalla:** puede caer a
  15–18 FPS en picos. ⚠️ Es el límite del silicio, no un mal ajuste.
- **Activa el OSD de FPS** para medir (no “a ojo”): pausa → OSD → FPS.

Si una pelea se te queda clavada bajo 20:

1. Sube **EE Cycle Skip a 3** (solo para SOTC). Puede dar micro-glitches en
   algún juego; en SOTC suele ir bien.
2. Pon **Límite de FPS de pantalla = 20**: no sube los FPS, pero el pacing
   parejo se siente mucho más estable que oscilar 17–24.
3. Baja la resolución interna a **0.5x** como último recurso (se ve peor,
   pero suma varios FPS en zonas fill-bound).

---

## 6. Tabla completa del preset SOTC (para curiosos y tweakers)

`Settings.shadowColossusPreset` = Ultra + underclock EE + 0.75x:

| Sección | Ajuste | Valor | Comentario |
|---|---|---|---|
| CPU | EE Cycle Rate | −1 (75%) | el ahorro grande de CPU |
| CPU | EE Cycle Skip | 2 | 3 si aún falta (ver §5) |
| CPU | MTVU | ON (8 núcleos) | VU1 en su propio hilo |
| CPU | Hilo del IOP | ON | sonido/disco/pad fuera del hilo EE |
| CPU | VU1 instantáneo / VU flag hack / INTC / WaitLoop / FastCDVD | ON | speedhacks seguros estándar |
| GS | Renderer | Vulkan | obligatorio para MaliSX2 |
| GS | Resolución interna | 0.75x | sub-nativa, por el fill-rate |
| GS | Salida (HW scaler) | 1 (448p) | el composer escala a 720p |
| GS | Blending | Mínimo | SOTC mostrará un aviso de “blending recomendado”: ignóralo, es el precio de los 20 fps |
| GS | HW mipmaps | OFF | trabajo GPU opcional fuera |
| GS | Precarga de texturas | Parcial | menos stalls de subida |
| GS | Coalesce render passes | ON | menos boundaries de pase en tiler |
| GS | ROV / paleta GPU | OFF | nunca ganan en Mali |
| Frame | Skip dupes | ON (default) | no presentar repetidos |
| Audio | Time-stretch | ON (default) | sin pitidos raros a bajo fps |

---

## 7. Troubleshooting rápido

| Síntoma | Prueba |
|---|---|
| Pantalla negra / cuelgue al arrancar con MaliSX2 | Driver del sistema (Vulkan) para ese juego; si persiste, OpenGL |
| Texturas negras o que faltan | Cambiar de driver (ver arriba); no toques `ForceMaliFramebufferFetch` (es para Mali-G615, no para tu G52) |
| El juego va lento pero el contador dice 60 | SOTC escribe los registros de pantalla cada campo: el contador miente. Mira el OSD de **VPS/velocidad %** además de FPS |
| Audio crepitante | Buffer 80–100 ms; time-stretch ON |
| Se calienta y baja con el tiempo | Normal (throttling): descansos, sin funda, sin cargar, 60 Hz |
| Un ajuste “no se guarda para SOTC” | Revisa el scope: **Juego**, no Global (cabecera del overlay) |
| Auto-tune cambió cosas y van peor | Rendimiento → resetear auto-tune de este juego |

---

## 8. Anexo: compilar tu propio APK con GitHub Actions

No necesitas compilar en tu PC: el repositorio trae el workflow
**APK Release (fork)** (`.github/workflows/apk-release.yml`) que compila el
APK universal (núcleo dual 4K+16K + PGO) y lo publica como un **release
normal** (no draft, no prerelease) en la pestaña Releases de tu fork.

1. En GitHub, abre tu fork → pestaña **Actions** → **APK Release (fork)** →
   **Run workflow** (elige la rama con tus cambios).
2. Rellena:
   - `version`: etiqueta del release y versionName, p. ej. `moto-g24-1`.
   - `version_code`: número entero **mayor** que el del APK instalado
     (p. ej. `1090`, luego `1091`…).
   - `package`: deja `com.armsx2` (desinstala otro ARMSX2 primero, §7) o usa
     p. ej. `com.armsx2.moto` para instalarlo al lado sin tocar nada.
   - `notes`: texto extra para el release (opcional).
3. Espera a que termine (compila el núcleo dos veces: tarda 1–3 h).
4. Descarga el APK desde **Releases** → `ARMSX2-<version>-Android-arm64.apk`
   e instálalo en el Moto G24.

Sin secrets, el APK sale firmado con clave debug temporal: funciona igual,
pero cada build nuevo exige desinstalar el anterior. Si quieres que tus
builds se actualicen entre sí, genera un keystore propio y añádelo como
secrets del repo: `APK_RELEASE_KS_B64`, `APK_RELEASE_KS_PASS`,
`APK_RELEASE_KEY_ALIAS` (y `APK_RELEASE_KEY_PASS` si difiere).
Ojo: estos builds no traen integración de Discord ni hardcore de
RetroAchievements (necesitan secrets privados del upstream).

---

*Última actualización: noviembre 2026. Presets: `Settings.ultraLowPreset` y
`Settings.shadowColossusPreset`; detección: `DeviceTier.isUltraLowEnd`;
seed en instalación fresca: `ConfigStore.seedFreshInstallDefaults`;
APK por CI: `.github/workflows/apk-release.yml` + `tools/ci-fork-dualcore.sh`.*
