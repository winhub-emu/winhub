<p align="center">
  <img src="https://raw.githubusercontent.com/winhub-emu/winhub/refs/heads/master/logo.png" width="820" alt="WinHub" />
</p>

<h1 align="center">WinHub</h1>
<p align="center"><b>Windows apps and games on Android, with a console-style interface.</b></p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-0.1--beta-ff2d9b?style=for-the-badge" alt="Version 0.1-beta">
  <img src="https://img.shields.io/github/downloads/winhub-emu/winhub/total?style=for-the-badge&label=Downloads&color=7a4cff" alt="Total Downloads">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-2d9bff?style=for-the-badge" alt="Platform">
  <img src="https://img.shields.io/badge/License-GPL--3.0-2dd4bf?style=for-the-badge" alt="License">
</p>

<p align="center">
  <a href="https://github.com/winhub-emu/winhub/releases">
    <img src="https://img.shields.io/badge/⬇%20Download-Latest%20Release-ff2d9b?style=for-the-badge&logo=android&logoColor=white" alt="Download">
  </a>
</p>

<p align="center">
  <a href="#-contents">Contents</a> •
  <a href="https://github.com/winhub-emu/winhub/releases">Download</a> •
  <a href="CHANGELOG.md">Changelog</a> •
  <a href="#-credits">Credits</a>
</p>

---

## 📖 Contents

- [ℹ️ About](#️-about)
- [📥 Install and update](#-install-and-update)
- [🆕 What's new in 0.1-beta](#-whats-new-in-01-beta)
- [✨ Features](#-features)
- [🎮 Controller guide](#-controller-guide)
- [🐛 Report a Mali GPU game issue](#-report-a-mali-gpu-game-issue)
- [🎨 Adding your own ReShade effects](#-adding-your-own-reshade-effects)
- [🎮 Frontends workaround](#-frontends-workaround)
- [🛠️ Building](#️-building)
- [🤖 Ask me anything](#-ask-me-anything)
- [🙏 Credits](#-credits)
- [⚖️ Disclaimer](#️-disclaimer)
- [📄 License](#-license)

---

## ℹ️ About

**WinHub** runs Windows applications and games on Android using Wine, Box64 and FEXCore, with DXVK, VKD3D-Proton and Vulkan drivers (Turnip and others) for graphics. It wraps all of that in a **console-style interface** that works fully with a gamepad and with touch, in landscape and in portrait.

WinHub is an unofficial community project. It continues the Winlator line (Winlator → cmod → Bionic → Star Bionic → Bannerlator) and stands on the work of everyone listed in the [Credits](#-credits).

> 🤖 Code in this project is written with the assistance of [Claude](https://www.anthropic.com/claude) by Anthropic.

| | |
|---|---|
| **App name** | `WinHub` |
| **Package** | `com.winhub.hernandez` (two alternate-package builds are published next to it) |
| **Version** | **0.1-beta** (`versionCode 89`) |
| **Android** | `minSdk 26` (Android 8.0+) · `targetSdk 28` · `compileSdk 34` |
| **Releases** | [github.com/winhub-emu/winhub/releases](https://github.com/winhub-emu/winhub/releases) |

---

## 📥 Install and update

1. Download the APK from the [latest release](https://github.com/winhub-emu/winhub/releases).
2. Allow installing from your browser or file manager when Android asks, then open the APK.
3. On first launch WinHub shows its setup screen and installs the components it needs. Keep the app open until it finishes.
4. Add a game from **Home → Add game**, or sign in to a store under **Stores**.

**Updating:** open **Settings → Updater**. Pick the **Stable** or **Preview** channel, and WinHub downloads and installs updates for you. When possible it uses a small patch (`.wh`) instead of the full APK.

---

## 🆕 What's new in 0.1-beta

- 🎨 **A fully revamped UI.** A console-style Deck shell with a controller-first design, a liquid-glass portrait mode, seven themes, Deck-style editors and a rebuilt in-game menu.
- 🖼️ **Two new renderers.** **EGL** (OpenGL ES, zero-copy window buffers) and **SurfaceDirect** (each window becomes an Android SurfaceFlinger layer, with no extra GPU composition pass), next to the existing Vulkan renderer.
- 💬 **Social Hub game configs.** Share and import complete game settings in one tap, search them, like and reply, and filter to configs that match your device.
- 🟢 **Mali support.** PanVK driver sources, a faster BCn texture layer, PanVK tuning, Mali GPU counters in the HUD, renderer fixes and a **Force Mali mode** switch.
- 🧩 **Mod Loader.** REFramework and Special K as per-game mods, plus programs (trainers, Cheat Engine) that start with the game, in the order and with the waits you set.

The full, detailed list is in the **[changelog](CHANGELOG.md)**.

---

## ✨ Features

### 🖥️ Interface
- Console-style Home with a game carousel, shelves (All, Recent, Favourites, Store games) and two Home styles (XMB and Classic).
- Portrait mode with a liquid Home, glass cards, a floating bottom bar, a Continue playing banner and a hold-a-cover game menu.
- Seven themes, a gliding focus ring, shoulder-button navigation and A-to-edit text fields.
- Container and per-game editors in three layouts (tabs, side rail or category pages).
- A console-style in-game quick menu with Graphics, FPS, Effects, Controls, Audio, Advanced, Tasks and Mods tabs.

### 🛒 Stores and library
- Built-in **Steam, Epic, GOG, Amazon and Xbox** stores sharing one tab layout (Store, Library, Profile).
- Steam library, depot downloads, Friends and chat, and achievements; Xbox PC package install and cloud gaming.
- Add any Windows game from storage; each game keeps its own settings, shortcuts and saves.

### 🧠 Emulation and compatibility
- **Wine / Proton** with **Box64** and **FEXCore**, and presets you can add, edit, duplicate, export and import from Settings.
- Per-container and per-game Wine components, environment variables and **custom startup services**.
- DXVK, VKD3D-Proton and D7VK wrappers, with downloadable builds.

### 🖼️ Graphics
- **Vulkan, EGL and SurfaceDirect** renderers, each with only its own settings.
- Turnip, Wrapper and PanVK driver support, with a driver and wrapper manager.
- Native **frame generation** (LSFG), **ReShade** effects, upscaling and a performance HUD.

### 🟢 Mali
- PanVK driver downloads, the faster BCn layer, PanVK tuning variables, GPU usage from hardware counters, and Mali-specific renderer fixes. Everything is Mali-only and does nothing on other GPUs.

### 🧩 Mods
- REFramework (RE Engine games) and Special K (most games) as per-game mods, downloaded on first use and checked by SHA-256.
- Programs that start with the game, with ordering and a wait for each.

### 💬 Community
- **Social Hub** with a feed, Game Configurations and a profile.
- **Community configs** in the game menu, cached for offline use.

### 🎮 Controls
- Gamepad, touch and on-screen controls, gyro aiming, vibration, and Steam Controller support.

---

## 🎮 Controller guide

| Button | What it does |
|---|---|
| **A** | Select / confirm |
| **B** | Back one level (never exits the app by accident) |
| **L1 / R1** | Switch section |
| **L2 / R2** | Switch sub-tab (Storage, Contents, Settings, Social...) |
| **Y** | Game menu (Classic Home) or favourite (XMB Home) |
| **Start** | Quick menu |
| **D-pad / stick** | Move focus; hold to repeat |

---

## 🐛 Report a Mali GPU Game Issue

Games misbehaving on a **Mali GPU** (Exynos / Dimensity / Kirin / Helio devices)? We run a dedicated public bug-report board for Mali devices — file a structured report with your logs, and get answers back from the developers in a public discussion thread on each report.

<p align="center">
  <a href="https://the412banner.github.io/WinHub/mali-reports/">
    <img src="https://img.shields.io/badge/📝%20File%20a%20Mali%20report-Submit%20with%20logs-2dd4bf?style=for-the-badge" alt="File a Mali report">
  </a>
  <a href="https://the412banner.github.io/WinHub/mali-reports/reports.html">
    <img src="https://img.shields.io/badge/📋%20Browse%20all%20reports-See%20dev%20answers-8b5cf6?style=for-the-badge" alt="Browse all Mali reports">
  </a>
</p>

- **📝 [File a Mali game report](https://the412banner.github.io/WinHub/mali-reports/)** — a quick form; attach the log file(s) so we can actually help.
- **📋 [Browse all reports & dev answers](https://the412banner.github.io/WinHub/mali-reports/reports.html)** — see what's been reported, answered, and fixed.

Every report gets its own **public discussion thread**. You can reply as the original poster — no account or password needed — and the developers answer right there in the thread.

---

## 🎨 Adding your own ReShade effects

<details>
<summary><b>Show / hide</b></summary>

Besides the built-in download catalog, you can add **any** ReShade effect yourself by dropping it into a folder. Follow these steps exactly:

**1. Open the ReShade drop-in folder on your device** (create the `ReShade` folder if it isn't there yet):

```
Android/data/com.winhub.hernandez/files/ReShade/
```

> 📁 That path is for the standard build. The alternate-package builds use their own package name in place of `com.winhub.hernandez`.

**2. Make one folder per effect.** Name the folder whatever you want the effect to be **called in the menu** — for example `MySepia`.

**3. Put the effect's files inside that folder — all in the same place, next to the `.fx`:**
- the effect's **`.fx`** file (required),
- any **`.fxh`** files it `#include`s (very common — e.g. `ReShade.fxh`, `ReShadeUI.fxh`),
- any **image / texture** files the effect uses.

```
ReShade/
  MySepia/
    MySepia.fx          ← the effect (folder name match = used first)
    ReShade.fxh         ← copy in any .fxh the .fx #includes
    ReShadeUI.fxh
    noise.png           ← copy in any textures it uses
```

**4. Pick it in the app.** Open the app → edit a **container** or a **game shortcut** → **ReShade effect** picker. Your folder now appears in the list — select it.

**5. Use it in-game.** Launch a **DirectX (DXVK / VKD3D) game**, open the in-game drawer → **ReShade tab**, and turn the effect on/off and tune its sliders **live**.

> #### ⚠️ Read this if something doesn't show up or work
> - **Only colour effects work** — sharpen, colour grading, film grain, CRT, tonemap, vignette, etc. **Depth effects (SSAO, depth-of-field, MXAO) do not work yet.**
> - ReShade only affects **DirectX games running through DXVK / VKD3D** — it does nothing on OpenGL / WineD3D / older 2D titles.
> - **Effect not in the list?** Make sure it's in **its own subfolder** and that the subfolder actually contains a `.fx` file (a loose `.fx` sitting directly in `ReShade/` is ignored).
> - **Effect selected but no change in-game?** Most often a missing `#include` — open the `.fx` in a text editor, find any `#include "Something.fxh"` lines, and make sure each of those `.fxh` files is copied into the **same folder** as the `.fx`. Same for any texture files.
> - **Game won't start / flat or blank screen after enabling effects?** You likely **stacked too many effects at once**. Each one compiles on-device and costs GPU, and too many together can stop the game from launching. Go back into the per-game **ReShade effect** settings and **uncheck the effects one at a time** (or the specific heavy one) until the game boots correctly, then re-enable them gradually. Adding effects **a few at a time** avoids this.
> - **Can't even find `Android/data`?** Many stock file managers hide it on Android 11+. Use a file manager that can open `Android/data`, or copy the effect folder over from a PC via a USB cable, then drop it in.

</details>

---

---

## 🎮 Frontends Workaround

<details>
<summary><b>Show / hide</b></summary>

WinHub does not work by itself on frontends out of the box. See the [frontends workaround guide](marcescence-frontends.md) to get it running.

</details>

---

---

## 🛠️ Building

This project is built via **GitHub Actions only** — local builds are not supported.

- **Action builds** — every fix is compiled and published as a downloadable workflow artifact.
- **Releases** — tagged stable builds are published as GitHub Releases.

---

---

## 🤖 Ask Me Anything

<details>
<summary><b>Show / hide</b></summary>

Got a question about WinHub? **Ask the codebase directly.** An AI reads the
actual source code and answers with the exact file names and line numbers, so you
can check it yourself. It never guesses — if the answer isn't in the code, it says so.

<p align="center">
  <a href="https://github.com/winhub-emu/winhub/issues/new">
    <img src="https://img.shields.io/badge/💬%20Ask%20a%20Question-Open%20an%20issue-7b2ff7?style=for-the-badge&logo=claude&logoColor=white" alt="Ask a Question">
  </a>
</p>

**It's three steps:**

1. **[Open an issue](https://github.com/winhub-emu/winhub/issues/new)** (you'll need a free GitHub account).
2. Type your question — be specific, and name a feature, setting, or file.
3. Submit. The AI replies in a comment on your issue, usually within **1–2 minutes**.

That's it — no form, no approval step, nothing else to do.

> ℹ️ The AI replies to **every** new issue automatically. A few per person per day
> are free; past that, it will ask you to try again later.

**Good things to ask:**

- *"How does the FPS limiter work?"*
- *"Where is the GOG store integration implemented?"*
- *"What values does the scaling mode picker accept?"*
- *"How are release builds signed and distributed?"*

*Avoid device-specific troubleshooting like "why is my game slow?" — the AI explains
what the **code** does, not how a game runs on your phone.*

<details>
<summary>Prefer the command line?</summary>

With [opencode](https://opencode.ai) installed (`npm install -g opencode-ai`), run the
same agent locally against a clone of this repo:

```
opencode run "your question" --agent ama-agent --model opencode/big-pickle
```
</details>

<details>
<summary><b>Maintainers / forks — one-time setup</b></summary>

The bot runs on the **opencode/big-pickle** model via your opencode credentials
(not a separate API key). To enable it on a fork:

1. Locally run `cat ~/.local/share/opencode/auth.json` and copy the whole JSON.
2. Add it as a repository secret named **`OPENCODE_AUTH`** under
   **Settings → Secrets and variables → Actions**.
3. Make sure the `answered` and `question` labels exist.

Every newly opened issue is answered automatically, bounded by a per-user daily
limit and a monthly cap — tune both at the top of
`.github/workflows/ama-answer.yml` (`PER_USER_PER_DAY`, `MONTHLY_CAP`;
maintainers are exempt from the daily limit). You can also force a re-run on an
older issue by adding the **`question`** label. Without the secret, the bot posts
a notice explaining what's missing.
</details>

</details>

---

---

## 🙏 Credits

<details>
<summary><b>Show / hide</b></summary>

> Credits below were written while this project was still called **Bannerlator**; "Bannerlator" in them means the project WinHub grew out of.

This build stands on a long chain of prior work — its direct lineage, plus the projects whose commits and work are cherry-picked and implemented here:

| Contributor | Contribution |
|---|---|
| **brunodev85** | Original [Winlator](https://github.com/brunodev85/winlator) — Wine + Box64 + Turnip on Android. Foundation of every fork below. Also serves the `input_controls` profiles consumed by this fork: <https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/> |
| **coffincolors** | [`cmod` Winlator fork](https://github.com/coffincolors/winlator) — package `com.winlator.cmod` and the customization layer this codebase is built on. |
| **Pipetto-crypto** | [Winlator Bionic fork](https://github.com/Pipetto-crypto/winlator) (the "Bionic" half of *Star Bionic*) and the upstream [Box64 fix branch](https://github.com/Pipetto-crypto/box64). Co-credited on cmod. Also packaged **vkBasalt** into the Winlator shortcut pipeline — the integration Bannerlator's **ReShade** feature builds on. |
| **jacojayy** | Maintainer of the [Star](https://github.com/jacojayy/star) line. Timeline Semaphore patches in the bundled Turnip driver for newer DXVK compatibility. Official site developer and maintainer. |
| **The412Banner** | **WinHub 0.1-beta:** the **Mali support** (PanVK driver sources, the faster BCn layer, the compat layer update, Mali HUD counters, PanVK tuning, the renderer fixes, the extra FEXCore presets) and the **Mod Loader** (REFramework, Special K, start programs). |
| **FristOneRR** | The [PanVK driver](https://github.com/FristOneRR/FristOneRR-Panvk-Driver) for Mali that the Mali options are built around. |
| **praydog** | [REFramework](https://github.com/praydog/REFramework) (MIT) for RE Engine games, offered as a per-game mod in Game settings → Mods. |
| **Kaldaien and SpecialKO contributors** | [Special K](https://github.com/SpecialKO/SpecialK) (GPL-3.0), offered as a per-game mod in Game settings → Mods. |
| **Star / Frost dev team** | The [star-emu](https://github.com/star-emu) team behind the original *Star Bionic* and *Winlator Frost* lines this build continues from. |
| **isygold** (AGBOOLA Israel Oluwagbogo) | [Star Engine / VEGAS](https://github.com/isygold/vegas-releases) — the Adreno-optimized DXVK fork this build's `v1.3-vegas` is named for, eliminating stutter and adding real-time upscaling on mobile GPUs, plus tuned [dxvk.conf profiles](https://github.com/isygold/DXVK.CONF-FILE-SETTINGS-). See the **[VEGAS DXVK FAQ](https://htmlpreview.github.io/?https://github.com/The412Banner/Bannerlator/blob/main/docs/vegas_faq.html)** for help & configuration.<br>🚀 **Support VEGAS Development** — low-level graphics dev & vibecoder: debugging, refactoring & improving original DXVK code for Adreno. **[❤️ Sponsor →](https://github.com/sponsors/isygold)** |
| **vivsi** | Controller support contributions. |
| **arro000** | **Virtual Controller Pro** ([#156](https://github.com/The412Banner/Bannerlator/pull/156)) — a ground-up on-screen touch-controls overhaul: new control types (Dynamic Stick, Mouse Area, Button Grid, Expandable Buttons), a Jetpack Compose-rebuilt controls editor with an **in-game live editor**, control groups, key combos, per-element dead zones, custom control icons, and a control scale limit raised to 300%. |
| **clintOnSky** | **LSFG Native capture resolution + Vulkan 1.1 driver compat** ([#512](https://github.com/The412Banner/Bannerlator/pull/512), 3.1.1) — runs LSFG Native's frame generation and the post-effect chain at a lower capture height and scales the result up, and lowers the LSFG shaders' SPIR-V so a Vulkan 1.1 stock driver with the right extensions can run them; tested on his Adreno 710. Also found the double shader build that slowed LSFG Native's start. |
| **StevenMXZ** | [Winlator-Ludashi](https://github.com/StevenMXZ/Winlator-Ludashi) and extensive cherry-picked work implemented in this build. This includes the **new user interface** and the **Vulkan rendering** path — both of which were **still unreleased and unfinished at the time these builds and this repo were created** — along with various other cherry-picked commits. This work is set to be released properly in his upcoming **3.1**. |
| **GameNative** | [GameNative](https://github.com/utkarshdalal/GameNative) by **utkarshdalal** — Proton bionic translation layers and cherry-picked commits adapted into this build. Its rendering pipeline was also the **reference used to fix and rewire Bannerlator's render options** — the `AHardwareBuffer` present path that makes Vulkan / DXVK / VKD3D content render correctly on both the OpenGL and Vulkan host renderers (GPUImage socket-buffer locking + EGLImage sampling, DRI3 direct-scanout, the Present extension's FLIP / COPY branches, and the Native Rendering+ direct-scanout path) was ported from and cross-examined against GameNative's implementation. The **standalone FPS limiter** is GameNative's too — its guest-side present-pacing mechanism (delaying the X11 Present `IdleNotify` to throttle the game itself, plus the rule that lsfg-vk's own pacing governs when its multiplier is ≥ 2) was ported from GameNative. For the **Steam store** (2.3), the **session-hardening patterns** (derived-`loggedIn` state, off-pump PICS sync, single reconnect funnel, dead-token clearing, keep-alive / watchdog) and the **`DownloadSpeedConfig` cores × ratio 4-tier download-speed model** were also ported / adapted from GameNative. The **PC-accurate controller vibration** feature (dual-motor rumble with per-container mode + intensity) originates from GameNative **#1214**, reaching this build via **TideGear**'s port (see below). **In 2.9.8**, the **external-display ("game on TV") concept** and the **suspend-sink audio-recovery approach** (re-establishing the guest's audio output after backgrounding or a mid-game output-route change) were referenced from GameNative and reimplemented clean-room — the TV Options suite and Bannerlator's native `pasink` libpulse audio client are original work, and the **wireless casting** feature is entirely Bannerlator's own with no upstream equivalent. |
| **TideGear** | [GameHub-Vibration-Fix](https://github.com/TideGear/GameHub-Vibration-Fix) — the **PC-accurate controller vibration** work this build's rumble is built on. TideGear authored both halves: the original vibration feature PR (carrying [GameNative](https://github.com/utkarshdalal/GameNative) **#1214** forward) and the **preload-free `winebus.so` rumble-duration patch** (PR **#91**) that drops the `libevshim` `LD_PRELOAD` hook and instead byte-patches SDL's rumble duration to never auto-expire — which is what makes sustained rumble actually *hold* instead of dying after ~1s. Adopted with the author's permission. Bannerlator re-derived the patch patterns per Proton build (9.0 / 10 / 11 aarch64 + Wine 10.0 x86-64) and added a build-agnostic structural fallback. |
| **WinNative** | [WinNative](https://github.com/WinNative-Emu/WinNative) — the reference for Bannerlator's **gyroscope (motion aim)** support. Its rate-mode gyro implementation is what ours is derived from: the sensor→stick pipeline (deadzone → sensitivity → exponential low-pass → clamp), the axis and sign conventions, and the fractional-remainder accumulator that keeps slow tilts from rounding away to nothing. Bannerlator adapted it to our evdev gamepad-injection path and extended the **gyro-mouse** mode to also drive the X pointer directly, so motion control works on a Wine container desktop and not only in captured mouse-look games. **In 3.0.6**, WinNative's native-side LSFG frame-generation implementation was the design reference for **LSFG Native** — its choice to run the shaders in the emulator's own present path rather than as a guest layer, verified against its merged code stage by stage. GPL-3.0, same as this project. |
| **[Win-FG Native](https://github.com/The412Banner/win-fg)** (Bannerlator) | Bannerlator's own **clean-room** frame generation — FSR3-family optical flow (MIT, AMD FidelityFX) plus a from-scratch synthesis, compiled into the compositor. No third-party frame-generation code or weights are bundled. Public repo, MIT, with per-shader provenance. |
| **Eden Team / camillelavey** | [Eden](https://git.eden-emu.dev/eden-emu/eden) — **LSFG Native** (new in 3.0.6) is ported from Eden's native Lossless Scaling frame-generation present path (PR #4263 by **camillelavey**, GPL-3.0): the PE-resource shader map, the ALPHA/BETA/GAMMA/DELTA stage decomposition and the pacer, re-hosted inside Bannerlator's compositor. Bannerlator bundles no Lossless Scaling shaders — they are extracted on device from the user's own `Lossless.dll` ([Lossless Scaling](https://store.steampowered.com/app/993090/Lossless_Scaling/) by THS). |
| **doitsujin (DXVK)** | [DXVK](https://github.com/doitsujin/dxvk) — its `dxbc` shader compiler (Zlib) is vendored to translate the Lossless Scaling DXBC shaders from the user's DLL into SPIR-V for **LSFG Native**, the same step **lsfg-vk** takes. |
| **PancakeTAS** | [lsfg-vk](https://github.com/PancakeTAS/lsfg-vk) — the open-source Vulkan frame-generation layer (a Vulkan-layer reimplementation of Lossless Scaling's frame generation) that Bannerlator's former **lsfg-vk** engine was built on, and whose DXBC→SPIR-V translation approach **LSFG Native**'s on-device shader step follows. *(The lsfg-vk engine itself is retired in 3.0.6.)* |
| **FrankBarretta** | [lsfg-vk-android](https://github.com/FrankBarretta/lsfg-vk-android) — the Android/bionic port of lsfg-vk (AHardwareBuffer path + `vkCmdPipelineBarrier2` shim) that ran as Bannerlator's lsfg-vk engine on the Turnip stack through 3.0.5. The in-game live multiplier/flow-scale reload uses the `conf.toml` mtime-watch mechanism from **GameNative's** [lsfg-vk-android fork](https://github.com/GameNative). No proprietary shaders are bundled — users supply their own `Lossless.dll` ([Lossless Scaling](https://store.steampowered.com/app/993090/Lossless_Scaling/) by THS) via the in-app picker. |
| **DadSchoorse** | [vkBasalt](https://github.com/DadSchoorse/vkBasalt) (zlib) — the Vulkan post-processing layer that embeds the ReShade FX compiler. Bannerlator's **ReShade** feature is a continuation of this work: the bundled layer is built from DadSchoorse's source, patched for live on-device toggle and slider control. The bundled / catalog `.fx` effects are MIT / CC0 shaders by the **ReShade ([crosire](https://github.com/crosire/reshade-shaders))**, **prod80 ([prod80-reshade-repository](https://github.com/prod80/prod80-reshade-repository))**, **luluco250 ([FXShaders](https://github.com/luluco250/FXShaders))** and **fubax** authors, each under their own MIT / CC0 license. |
| **leegao** (Lee Gao) | Vulkan texture-compression work used for mobile-GPU compatibility and performance — the [BCn decompression layer](https://github.com/leegao/bcn_layer) (**shader-v3**, powering 2.5's **"Wrapper + bcn_layer"** Mali driver) and the **DX12 `compat_layer`** that, alongside it, powers 2.7's new opt-in **"Wrapper + compat + bcn"** Mali DX12 driver — including the just-landed **layer-composition fix** — plus real-time [ASTC/ETC compute-shader encoders](https://github.com/leegao) and the [bionic-vulkan-wrapper](https://github.com/leegao/bionic-vulkan-wrapper) (**ETC2-Milestone-2**) bundled as the base wrapper for the Mali BCn path. |
| **WinterSnowfall** | [d7vk](https://github.com/WinterSnowfall/d7vk) — the DXVK-lineage Vulkan implementation of DirectX 7 / DirectDraw (Direct3D 3–7) that powers Bannerlator's new **D7VK** DDraw-wrapper option (new in 2.9.6). Bundled as the default and offered as downloadable nightly catalog builds. |
| **WinlatorMali** (GunaCharanTeja / Charan) | [WinlatorMali](https://github.com/GunaCharanTeja/WinlatorMali) — the **Wrapper Version Manager** (new in 2.7) is modeled on WinlatorMali's graphics-driver manager, introduced in [Winlator Mali Bionic 1.1](https://github.com/GunaCharanTeja/WinlatorMali/releases/tag/bionic-mali-1.1); a number of the downloadable catalog wrappers come from WinlatorMali too (each credited in-app). The feature was requested in [#132](https://github.com/The412Banner/Bannerlator/issues/132) by [@6ui99uhkllj](https://github.com/6ui99uhkllj). |
| **BrunoSX** | The bundled Windows file manager is **[Banner File Manager](https://github.com/The412Banner/banner-file-manager)** (new in 2.7.1), Bannerlator's fork of BrunoSX's [Winlator File Manager](https://github.com/brunodev85/wfm) (**MIT**) — rebuilt with **native Win32 file operations** (sidestepping the Proton 10.0-4 shell32 copy-paste crash), a **dual-pane split view**, and **Open-as-administrator / Open-with**. |
| **[@Tony57319](https://github.com/Tony57319)** | Reported / requested the **lsfg Performance mode** frame-gen toggle new in 2.7.1 ([#152](https://github.com/The412Banner/Bannerlator/issues/152)). |
| **[@clintOnSky](https://github.com/clintOnSky)** 🌱 *(first-time contributor)* | Downstream fixes surfaced in [#96](https://github.com/The412Banner/Bannerlator/pull/96), applied to `main` individually: the **Xiaomi/HyperOS `libjpeg.so` symlink-shadow removal** that lets the frame-gen Vulkan layer load; the **sign-agnostic battery-wattage fix** for Xiaomi/MTK devices that report discharge current as positive; and the **`WOWBOX64` content-type fix** for arm64ec Box64 downloads with live refresh of the component version lists after a download sheet closes. |
| **JavaSteam** | [JavaSteam](https://github.com/Longi94/JavaSteam) (`in.dragonbra:javasteam`) by **Longi94** — the Steam **connection-manager client** the built-in Steam store logs in and talks to Steam with, and — via the **`javasteam-depotdownloader`** fork by **joshuatam** — the **entire depot-download engine** Bannerlator's Steam store is built on. |
| **Goldberg Steam Emu / gbe_fork** | [Goldberg Steam Emu](https://mr_goldberg.gitlab.io/goldberg_emulator/) by **Mr_Goldberg**, and **gbe_fork** by **[Detanup01](https://github.com/Detanup01/gbe_fork)** — the Steam emulator Bannerlator's **Goldberg auto-patch** installs (Regular / Experimental / ColdClient tiers) for offline / emulated play of games you own. |
| **Pluvia** | [Pluvia](https://github.com/oxters168/Pluvia) — an Android Steam client whose patterns were **referenced alongside GameNative** while building the Steam store's login / session handling. |
| **The412Banner** | Full Jetpack Compose UI migration, in-game overlay rewrite, controller-support restore (SDL2 SoName fix + four event files), Box64 edit-dialog fix, theme system, and CI/release infrastructure. **In 2.3**, building on JavaSteam / GameNative / Goldberg, the original engineering is Bannerlator's own: the **cross-store Download Manager**, the **four storefront integrations** (Steam / Epic / GOG / Amazon), the multi-week **Steam session-hardening** work, the depot **OOM fix**, the **Goldberg auto-patch** integration, the store **Material-3 restyle**, and the store-log **credential redaction** (`StoreLog.redactUrl`). **In 2.4**, the **fullscreen aspect-ratio pipeline** (Off/Fit/Stretch/Fill/Integer across all three renderers), the **in-app File-Manager import picker** replacing SAF (with image thumbnails + percent/ETA import progress), the **DLC picker**, the **true-size depot install fix**, **per-game persistence** of scaling / fullscreen / HUD position, and the **container wallpaper picker**. **In 2.5**, the **Mali / BCn hardening** — wiring leegao's bcn_layer (shader-v3) + ETC2-Milestone-2 wrapper into the **"Wrapper + bcn_layer"** and experimental **"Wrapper-gamenative"** drivers, the **BCn Layer Settings** UI, and the **in-game logging overhaul** (copy-logs button, selectable log location, co-located DXVK/VKD3D logs, scrollable debug-channels dialog). **In 2.5.1**, the **SurfaceFlinger colour + crash fix** and per-container / per-game **"Correct SurfaceFlinger colours"** toggle (ASurfaceRenderer R/B-swap fix + GPU converter, ported from GameNative #1620 / #1644), the in-game **FEX runtime indicator** (arm64ec / x86-64 · FEXCore / wowbox64 / Box64 · unixlib / DLL, read live from `/proc/<pid>/maps`), the **FEXCore unixlib (`.so`) auto-match** at launch (per-game version sync + uninstall cleanup), and **18 new Environment Variable presets** (DXVK / VKD3D / Wine / Mesa). **In 2.5.2**, the **Community Configs** system — the in-app catalog browser, per-uploaded-config cards with live upvotes / downloads / comments, the surgical config-apply engine, and the smart inline installer for DXVK / VKD3D / FEXCore + Turnip drivers — plus **two new Proton 11.0-1 x86-64 compatibility layers** (SDK 28 / SDK 35) built and published to the downloadable catalog. Also maintains the [Nightlies WCP Hub](https://github.com/The412Banner/Nightlies) and [Banners-Turnip](https://github.com/The412Banner/Banners-Turnip). |

### Upstream stack

The Wine/translation stack this app bundles or downloads:

| Component | Author |
|---|---|
| **Wine** | [WineHQ](https://www.winehq.org/) |
| **Box64 / Box86** | [ptitSeb](https://github.com/ptitSeb) |
| **FEXCore** | [FEX-Emu](https://github.com/FEX-Emu) |
| **DXVK** | [doitsujin / Philip Rebohle](https://github.com/doitsujin) |
| **DXVK-GPLAsync patch** | [Ph42oN](https://gitlab.com/Ph42oN) |
| **DXVK-Sarek** | [pythonlover02](https://github.com/pythonlover02) |
| **VEGAS** (Adreno-tuned DXVK / GPLAsync fork — `v1.3-vegas`) | [isygold](https://github.com/isygold/vegas-releases) · [FAQ](https://htmlpreview.github.io/?https://github.com/The412Banner/Bannerlator/blob/main/docs/vegas_faq.html) · [❤️ Sponsor](https://github.com/sponsors/isygold) |
| **D7VK** (DirectX 7 / DirectDraw → Vulkan) | [WinterSnowfall](https://github.com/WinterSnowfall/d7vk) |
| **VKD3D-Proton** | [Hans-Kristian Arntzen](https://github.com/HansKristian-Work) |
| **Turnip / Mesa** | [Freedreno team @ Mesa](https://gitlab.freedesktop.org/mesa/mesa) |
| **Proton layers (bionic)** | [GameNative](https://github.com/utkarshdalal/GameNative) |
| **Steam depot engine** | [JavaSteam](https://github.com/Longi94/JavaSteam) by [Longi94](https://github.com/Longi94) · depotdownloader fork [joshuatam](https://github.com/joshuatam) |
| **Steam emulator (Goldberg auto-patch)** | [Goldberg Steam Emu](https://mr_goldberg.gitlab.io/goldberg_emulator/) (Mr_Goldberg) · [gbe_fork](https://github.com/Detanup01/gbe_fork) (Detanup01) |
| **Controller vibration (PC-accurate rumble)** | Feature [GameNative](https://github.com/utkarshdalal/GameNative) #1214 · port + preload-free `winebus` duration patch [TideGear](https://github.com/TideGear/GameHub-Vibration-Fix) (#91) |
| **Gyroscope (motion aim)** | Rate-mode pipeline + axis conventions from [WinNative](https://github.com/WinNative-Emu/WinNative) |
| **Steam Controller support** | [SDL3](https://github.com/libsdl-org/SDL) (Sam Lantinga & the SDL contributors, zlib) — the official 3.4.16 Android release, bundled unmodified |
| **EA games from Steam (EA Desktop chain)** | Researched and implemented by **The412Banner** — the methods and techniques [GameHub](https://github.com/utkarshdalal/GameNative) uses to launch EA titles were reverse-engineered and re-implemented for Bannerlator on its SteamLite agent |
| **Frame Generation (win-fg)** | Bannerlator (clean-room) |
| **Frame Generation (lsfg-vk)** | [PancakeTAS](https://github.com/PancakeTAS/lsfg-vk) · Android port [FrankBarretta](https://github.com/FrankBarretta/lsfg-vk-android) · live-reload fork [GameNative](https://github.com/utkarshdalal/GameNative) · DLL [Lossless Scaling](https://store.steampowered.com/app/993090/Lossless_Scaling/) (user-supplied) |
| **Post-processing (ReShade / vkBasalt)** | [vkBasalt](https://github.com/DadSchoorse/vkBasalt) by [DadSchoorse](https://github.com/DadSchoorse) (zlib) · Winlator packaging [Pipetto-crypto](https://github.com/Pipetto-crypto/winlator) · effects by [crosire](https://github.com/crosire/reshade-shaders) · [prod80](https://github.com/prod80/prod80-reshade-repository) · [luluco250](https://github.com/luluco250/FXShaders) · fubax (MIT / CC0) |

### Community reports & requests

Much of Bannerlator's polish is driven by the people who file issues and test builds. Recent features came directly from:

- **Angel** — requested the environment-variable presets that seeded **2.5.1's** new preset set (`DXVK_DISABLE_TIMELINE_SEMAPHORES`, `VKD3D_SHADER_MODEL`).
- **[@kylinzang](https://github.com/kylinzang)** — the driving force behind **2.5's Mali / BCn support** ([#70](https://github.com/The412Banner/Bannerlator/issues/70), originally #54 / #53): the original request, the env-var spec, the in-game logging overhaul, and iterative on-device testing on Mali-G57 through a full sign-off. Also fullscreen aspect-ratio modes ([#71](https://github.com/The412Banner/Bannerlator/issues/71)) and the in-app File-Manager import picker ([#73](https://github.com/The412Banner/Bannerlator/issues/73)).
- **[@rizky2-crypto](https://github.com/rizky2-crypto)** — Mali-G610 BCn testing ([#30](https://github.com/The412Banner/Bannerlator/issues/30)).
- **[@SombraShadow](https://github.com/SombraShadow)** — the container wallpaper picker ([#66](https://github.com/The412Banner/Bannerlator/issues/66)).
- **[@abdogm](https://github.com/abdogm)** — magnifier cursor-follow & no-dim fixes ([#44](https://github.com/The412Banner/Bannerlator/issues/44)).
- **[@Devaspe](https://github.com/Devaspe)** — the Steam install-blocker report that drove the true-size depot install fix.
- **iamtooshaar** (Discord) — testing and bug-hunting on **Steam Friends & Chat** (3.0.2), helping shake out the presence, history and image-send issues on real hardware.
- **TAR** (Discord) — tested the **Steam Controller** support through six builds (3.1.0) and shaped it: the bindings fix, the remappable back buttons and **…** button, the 18-input controller test and the left / both trackpad options.

…and everyone in the Discord and on GitHub who tests builds and reports issues. 🙏

Additional credits surfaced in the **Star Bionic REVAMPED** project (`star.bionic-revamp`):

- **@The412Banner** — Converting the UI to Jetpack Compose and rewriting the controller implementation.
- **@jacojayy** — Timeline Semaphore patches in Turnip.

> If you have contributed and are not listed, open a PR — this list is intended to be complete.

</details>

---

---

## ⚖️ Disclaimer

Winlator and its forks are unofficial community projects. They are **not** affiliated with or endorsed by Microsoft, Wine, the Mesa project, Qualcomm, **Valve/Steam, Epic Games, GOG, Amazon**, or any game publisher. The built-in store sign-ins are a third-party login system for libraries **you already own** — see [Security Hardening & your store accounts](#-security-hardening--your-store-accounts), and **use them at your own risk**. Compatibility varies by device GPU, Android version, and individual game.

---

---

## 📄 License

**WinHub is licensed under [GPL-3.0](LICENSE) as a whole**, because it incorporates GPL-3.0-licensed components (notably **GameNative** and **lsfg-vk**), whose copyleft governs the combined distribution.

The upstream **Winlator → cmod → Bionic → Star → Ludashi** lineage it builds on is **MIT © 2023 BrunoSX** (permissive, and GPL-3.0-compatible). That MIT notice — and the license and copyright of *every* incorporated component (GameNative, lsfg-vk, gbe_fork/Goldberg, vkBasalt, bcn_layer, JavaSteam, Wine/Box64/Mesa/DXVK, FSR/NIS/SGSR, ReShade shaders, …) — is preserved in **[`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md)**.

See [`LICENSE`](LICENSE) for the full GPL-3.0 text and [`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md) for all third-party attributions.
