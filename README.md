<p align="center">
  <img src="logo.jpg" width="820" alt="WinHub" />
</p>

<h1 align="center">WinHub</h1>
<p align="center"><b>Windows applications and games on Android.</b></p>

<p align="center">
  <img src="https://img.shields.io/github/downloads/The412Banner/Bannerlator/total?style=for-the-badge&label=Downloads&color=ff2d9b" alt="Total Downloads">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-7a4cff?style=for-the-badge" alt="Platform">
  <img src="https://img.shields.io/badge/License-GPL--3.0-2d9bff?style=for-the-badge" alt="License">
  <a href="https://github.com/The412Banner/Bannerlator/issues/new?template=ask-the-ai.yml"><img src="https://img.shields.io/badge/💬%20Ask%20the%20AI-Ask%20about%20the%20app-7b2ff7?style=for-the-badge&logo=claude&logoColor=white" alt="Ask the AI"></a>
</p>

<p align="center">
  <a href="https://ko-fi.com/the412banner"><img src="https://img.shields.io/badge/Ko--fi-Support%20the%20project-ff5e5b?style=flat-square&logo=ko-fi&logoColor=white" alt="Support on Ko-fi"></a>
</p>

<p align="center">
  <a href="https://github.com/The412Banner/Bannerlator/releases/latest">
    <img src="https://img.shields.io/badge/⬇%20Download-Latest%20Release-ff2d9b?style=for-the-badge&logo=android&logoColor=white" alt="Download Latest Release">
  </a>
  <a href="https://the412banner.github.io/bannerlator-game-configs/">
    <img src="https://img.shields.io/badge/🌐%20Community%20Configs-Browse%20the%20Library-8b5cf6?style=for-the-badge" alt="Community Config Library">
  </a>
  <a href="https://the412banner.github.io/Bannerlator/mali-reports/">
    <img src="https://img.shields.io/badge/🐛%20Mali%20Reports-Report%20a%20game%20issue-2dd4bf?style=for-the-badge" alt="Report a Mali GPU game issue">
  </a>
</p>

<p align="center">
  <a href="#-contents">Contents</a> •
  <a href="#-ask-me-anything">Ask AI</a> •
  <a href="https://github.com/The412Banner/Bannerlator/releases/latest">Download</a> •
  <a href="https://the412banner.github.io/bannerlator-game-configs/">Config Library</a> •
  <a href="#-report-a-mali-gpu-game-issue">Mali Reports</a> •
  <a href="https://discord.gg/n8S4G2WZQ4">Discord</a> •
  <a href="https://t.me/The412BannerGaming">Telegram</a> •
  <a href="#️-building">Builds</a> •
  <a href="#-credits">Credits</a>
</p>

---

## 📌 Project Notice

> **Bannerlator is a personal build — made by me ([The412Banner](https://github.com/The412Banner)), for my own device, my own needs, and my own use.**
>
> It's a personal continuation of the Winlator *Star Bionic* project ([star-emu/star](https://github.com/star-emu/star)) after it was discontinued and archived. None of the original developers are involved except me; it stands on their work plus cherry-picked commits from across the community — all credited below.
>
> **This is NOT an official or general-purpose Winlator release.** It is built and tuned for *my* hardware and *my* workflow, and published **as-is** purely in case it happens to be useful to someone else.
>
> - **No guarantee it works on any other device, GPU, or Android version.**
> - **No support, and no commitment to fix anything that works for me but not for you.** If a feature works on my device, it isn't broken — for me, which is what this build is for.
> - Bug reports / feature requests for setups I don't run may simply be closed. That's not personal; this just isn't a community-support project.
>
> You're **free to use, modify, fork, or share it** (GPL-3.0). If it doesn't work on your setup, that's expected — it wasn't built for it.

---

## 🤖 AI Disclaimer

All coding, features, and changes in this project are developed with the assistance of **[Claude AI](https://www.anthropic.com/claude)** by Anthropic. Claude is used to help with the heavy lifting — writing, reviewing, and modifying the emulator's code and the features added here: the Kotlin/Compose app shell and the native emulation glue (Wine/Proton, box64/FEX, DXVK/VKD3D → Vulkan), the storefront and cloud-save integrations, and the per-game/container features — as well as porting and re-deriving community changes into this build.

Before any **stable release** is published, all changes are **manually debugged and tested by me across multiple devices — both rooted and unrooted**. Debugging is done using `logcat`/`getlog` output and in-app debug log files to diagnose and verify behaviour, and no stable release is cut until the change is verified end-to-end on real hardware. Test devices:

- **Samsung Galaxy Fold 8 Ultra** — Snapdragon 8 Elite Gen 5 — *not rooted*
- **AYANEO Konkr Pocket Fit** — Snapdragon G3 Gen 3 — *rooted*
- **Odin 2 Portal** (base) — Snapdragon 8 Gen 2 — *rooted*
- **Retroid Pocket 5** — Snapdragon 865 — *rooted*

> **A note on root:** using any root-only features is entirely **your own choice and at your own risk**. I don't encourage rooting your device in general — especially without the proper knowledge and tools to do it correctly. Bannerlator runs fine unrooted; root only unlocks a few optional performance extras.

---

## ℹ️ Information

| | |
|---|---|
| **App label** | `WinHub` (standard) · `WinHub PuBG` (pubg) · `WinHub Ludashi` (ludashi) |
| **Packages** | `com.winlator.banner` (standard) · `com.tencent.ig` (pubg) · `com.ludashi.benchmark` (ludashi) |
| **Version** | Bannerlator **V 3.1.2** — built from Star **marcescence** (`versionName 3.1.2`, `versionCode 86`) |
| **Android SDK** | `compileSdk 34` · `targetSdk 28` · `minSdk 26` (Android 8.0+) |
| **Lineage** | Winlator → cmod → Bionic Nightly → Star Bionic → **marcescence** → **Bannerlator** |

---

## 🐛 Report a Mali GPU Game Issue

Games misbehaving on a **Mali GPU** (Exynos / Dimensity / Kirin / Helio devices)? We run a dedicated public bug-report board for Mali devices — file a structured report with your logs, and get answers back from the developers in a public discussion thread on each report.

<p align="center">
  <a href="https://the412banner.github.io/Bannerlator/mali-reports/">
    <img src="https://img.shields.io/badge/📝%20File%20a%20Mali%20report-Submit%20with%20logs-2dd4bf?style=for-the-badge" alt="File a Mali report">
  </a>
  <a href="https://the412banner.github.io/Bannerlator/mali-reports/reports.html">
    <img src="https://img.shields.io/badge/📋%20Browse%20all%20reports-See%20dev%20answers-8b5cf6?style=for-the-badge" alt="Browse all Mali reports">
  </a>
</p>

- **📝 [File a Mali game report](https://the412banner.github.io/Bannerlator/mali-reports/)** — a quick form; attach the log file(s) so we can actually help.
- **📋 [Browse all reports & dev answers](https://the412banner.github.io/Bannerlator/mali-reports/reports.html)** — see what's been reported, answered, and fixed.

Every report gets its own **public discussion thread**. You can reply as the original poster — no account or password needed — and the developers answer right there in the thread.

---

## 📖 Contents

- [📌 Project Notice](#-project-notice)
- [🤖 AI Disclaimer](#-ai-disclaimer)
- [ℹ️ Information](#ℹ️-information)
- [🐛 Report a Mali GPU Issue](#-report-a-mali-gpu-game-issue)
- [🐧 Linux Steam Client (3.1.3 pre-release)](#-linux-steam-client--313-pre-release-testers)
- [🆕 What's New in 3.1.2](#-whats-new-in-312)
- [🎞️ Frame Generation & Present Modes](#-frame-generation--present-modes)
- [✨ Full Features](#-full-features)
- [🎨 Adding your own ReShade effects](#-adding-your-own-reshade-effects)
- [🎮 Frontends Workaround](#-frontends-workaround)
- [🛠️ Building](#️-building)
- [🤖 Ask Me Anything](#-ask-me-anything)
- [🙏 Credits](#-credits)
- [⚖️ Disclaimer](#️-disclaimer)
- [📄 License](#-license)

---

## 🐧 Linux Steam Client — 3.1.3 pre-release (testers)

**[3.1.3 pre-release 3](https://github.com/The412Banner/Bannerlator/releases/tag/3.1.3-pre3) runs Valve's own native ARM64 Linux Steam client inside Bannerlator** — sign in, install a game from your library and play it through Valve's ARM64 Proton, with no Wine container involved. Pre-release 3 puts the games you added in the app's Games tab into the client's library, with shared saves, and gives games their sound through DirectAudio (PulseAudio where it cannot load). Pre-release 2 put the client's interface back on the GPU, where it runs at about twice the frame rate. It is a **tester pre-release**: the stable release is still **3.1.2**, and you are only offered it in-app with **Settings → Include pre-releases** switched on.

> ⚠️ **Adreno GPUs only**, and it needs Android's **Disable child process restrictions** developer setting. Mali, Xclipse and PowerVR phones get sound over a black screen. Leave the experimental **Steam Deck mode** off: it breaks game controllers. Full caveats are in the [release notes](docs/releases/3.1.3-pre3.md).

<details>
<summary><b>📖 Step-by-step in plain English — a brand-new install, or upgrading from an earlier pre-release or the September 21 test build</b> (tap to expand)</summary>

### 🆕 New here? Installing for the first time

**Before you start — check two things:**

- **Your phone needs an Adreno GPU** (Snapdragon). Mali, Xclipse and PowerVR phones will get sound over a black screen. **Contents → Linux Runtime** warns you about this before the download.
- **Turn on one Android setting first:** go to **Settings → Developer options → Disable child process restrictions** and switch it **on**. Do not see Developer options? Go to **Settings → About phone** and tap **Build number** seven times, then it appears.
  Without this switch, Android randomly kills the Steam client and your session just dies for no visible reason. Some phones do not have the switch at all — on those, this build will not work yet.

**Then:**

1. **Download and install the APK.** There are three — just take **standard** unless you specifically use the pubg or ludashi build already.
2. **Open the app and go to Contents → Linux Runtime → Download.** This is about **755 MB**, once. It is not in the APK, so this step is required.
3. When it finishes, a **Steam (Linux)** entry appears in your games list. **Tap it.**
4. **First launch takes a few minutes.** You will see a loading screen — Steam is downloading and updating itself. There is a clock on screen so you know it is alive. It may restart itself once; that is normal.
5. **Sign in** with your Steam account. Password, or the QR code with the Steam mobile app — both work.
6. **⏳ Now wait. Do not install a game yet — this is the part people get wrong.**

   Steam fetches Valve's ARM64 Proton by itself. **It may not start downloading on its own** — watch for an install box for **Proton Experimental (ARM64)** (about 2 GB). If your phone has an SD card, Steam will ask **where to put it: internal storage or the SD card**. Pick one and tap **Install**. If nothing appears, check the **Downloads** page — it may be sitting there waiting on that choice.

   **When the download finishes, still do not touch anything.** The loading screen comes back saying **"Steam is restarting once…"** — **the client restarts itself automatically.** That restart is what makes every game you install run properly. Let it happen; Steam reopens on its own, still signed in.

   ⚠️ **Installing a game before that restart breaks it.** Steam grabs the wrong set of tools — about 1.7 GB you will never use — and the game just fails to launch with no error message. Wait for the restart. It is a minute or two.
7. **Once Steam has come back on its own — now install a game from your library and play it.** Nothing to configure, nothing to pick.

**That's it.** Your existing Wine containers and games are completely untouched by any of this.

**Optional tweaks** — the ⚙️ next to the Steam (Linux) entry has resolution (720p by default, because Steam's own interface is expensive to draw), drivers, audio, frame generation and the HUD.

### ⬆️ Already running the Linux Steam client?

**From pre-release 1 or 2:** install this APK over your current install. That is all — nothing to re-download, and you stay signed in.

**From the September 21 test build:** two steps. Nothing to re-download.

1. **Install this APK over your current install.** Your data is kept — do not uninstall first.
2. **Open Contents → Linux Runtime once.**
   In the old build, Steam (Linux) lived inside a Wine container. It now stands on its own with its own settings. Just opening that tab moves your entry out of the container and carries your settings over (HUD, LSFG, audio, drivers).

Then launch **Steam (Linux)** from your games list as usual.

- ✅ You stay signed in. Your games, Valve's ARM64 Proton and your downloads are all still there.
- ❌ **Do not remove or re-download the Linux Runtime.**
- 🗑️ If you made a container *only* for Steam and nothing else lives in it, you can delete it afterwards. (The **Linux Desktop** entry stays in the container — leave that one alone if you use it.)
- If you skip step 2 it still launches, but it will keep using the old container's settings until you open that tab.

**In-app updates:** you will not be offered this automatically unless you turn on **Settings → Include pre-releases**. Stable users are never offered pre-releases.

### 🐞 If something goes wrong (both groups)

Post the **whole** session folder from `Download/Bannerlator-LinuxSteam/session-<date>-<time>/`. It is already scrubbed — tokens, Steam IDs and login files are masked or never copied, so it is safe to post.

Known and already on the list, no need to report: **Steam Deck mode breaks game controllers**, **FlatOut shrinks to the corner after Guide → Resume**, **TF2 says "insecure"**, GTA V *Enhanced* does not run, Serious Sam Shatterverse is killed for memory, NFS Hot Pursuit Remastered faults. Everything was proven on one AYANEO Pocket FIT — reports from any other device are the single most useful thing right now.

</details>

---

## 🆕 What's New in 3.1.2

**The Wayland update.** Bannerlator can now draw a game through its **own embedded Wayland compositor** instead of the Java X server — a second display backend, chosen per container. It does things X11 cannot: **HDR10**, **launching a game on your TV**, **zero-copy presentation**, **eight bundled game drivers** to pick from per game, real **mouse-look**, and frame generation in HDR.

> 🧪 **Wayland is EXPERIMENTAL and is not a replacement for X11.** It is very new and it has bugs — some games run worse than on X11, some will not run at all. **X11 remains the default and stays fully supported**, and is still the right choice for anything you actually want to play. Wayland is **off unless you turn it on**, per container, and your existing containers are untouched.
>
> It only works on the new **versionCode 8 arm64ec Proton 11 layers** (the **(v8)** rows in the catalog) — not on the x86_64 (box64) layers and not on the Proton 10 layers, which stay on v7. It also needs an **Adreno** GPU, because the bundled drivers are Turnip; Mali, Xclipse and PowerVR phones stay on X11.

- **🌊 Wayland display backend (experimental).** Turn it on per container. A game draws straight into Bannerlator's compositor, and you pick one of **eight bundled Wayland Turnip drivers** per game.
- **🌈 HDR10 on HDR screens.** Your screen's real peak, frame-average and black level are described to Windows, so a game's own HDR option stops being greyed out.
- **📺 Play on your TV.** A **TV** tab appears while an external screen is connected: launch the session straight onto the TV (in HDR when it supports it), with a companion screen on the handheld. Unplug and the game moves back and carries on.
- **🎭 GPU name spoof on Wayland**, plus a DXVK memory cap and present-mode picker in the driver gear.
- **⚡ Wayland performance, phase 1** — measured on an Adreno 750: **+9% Vulkan, +12% D3D12, +20% DirectDraw**, with higher 1% lows.
- **📦 New versionCode 8 compatibility layers** — every Proton / GE-Proton **11.x** layer rebuilt with the Wayland driver and HDR10, plus **Proton-CachyOS** joining the catalog and x86_64 builds for GE 11.0-7, 11.0-7.1 and CachyOS. Proton 10.0-4 and GE 10.0-34 stay at v7.
- **🎮 A physical controller stick can move the mouse** with the on-screen controls switched off.

### 🆕 Earlier — 3.1.1 (the XMB update)

**The XMB update.** The Games tab gets an **XMB view** — scroll through your games side to side, the way the PlayStation 3's menu works, and open every setting and tool for the game in focus right there, without pop-ups; built for a controller, in portrait and landscape. **LSFG Native** gets two experimental options from community contributor **clintOnSky** — a lower **capture resolution** that makes frame generation much lighter on weaker phones, and support for stock drivers that only report Vulkan 1.1 — and it starts faster when you turn it on. Entirely app-side — **no ImageFS reinstall**; install over 3.1.0 and everything carries over. Full notes: [`docs/releases/3.1.1.md`](docs/releases/3.1.1.md).

- **🎮 XMB view.** Tap the Games tab's view button until you reach **XMB**. Games run across, the focused game's options run down, with an info panel (store badges, settings chips, playtime, genres, Metacritic, description) and an animated background in your accent colour. Settings, Game Details, Properties, Clone, Remove, logs, community configs, cover scraping, Copy to Drive C, save backup / restore and Change executable all open as further XMB columns instead of pop-ups, and changes save as you go. Controller: left / right games, up / down options, **A** picks, **B** goes back, **LB / RB** jump five games or switch sections. Tested on an AYANEO Pocket FIT in both orientations.
- **🎞️ LSFG Native: capture resolution and Vulkan 1.1 drivers (experimental, off by default).** Run frame generation at a lower height (Panel / Game / a height, never below a quarter of the screen) and scale the result up — far cheaper on the GPU, post effects and upscalers still work. A **Vulkan 1.1 compat** switch lets it run on a stock driver that has the right extensions. From **clintOnSky** ([#512](https://github.com/The412Banner/Bannerlator/pull/512)), tested on his Adreno 710.
- **⚡ LSFG Native starts faster.** In some setups it built its shaders twice when turned on; it now builds them once.
- **🕹️ Per-game settings pop-up:** **L1 / R1** switch between its sections from anywhere.
- **🎮 Steam Controller over a USB cable** is now tested (thanks to **TAR**).

### 🆕 Earlier — 3.1.0 (the Steam Controller update)

**The Steam Controller update.** Bannerlator now talks to the **Steam Controller** directly — the 2026 model, and the original over Bluetooth — so it works as a real controller in games, with your bindings, remappable back buttons and a trackpad mouse. Frame generation now **fits your screen by itself** and warns when your numbers can't work. Plus **SGSR HQ** upscaling, per-game **texture filtering**, and new containers that start at a screen size matching your panel. Entirely app-side — **no ImageFS reinstall**; install over 3.0.9 and everything carries over. Full notes: [`docs/releases/3.1.0.md`](docs/releases/3.1.0.md).

- **🎮 Steam Controller support (opt-in).** Input Controls → Device → **Steam Controller**. Out of the box a Steam Controller acts as a keyboard and mouse; Bannerlator now opens it through **SDL3**'s Steam Controller driver and reads it as a full controller — its own player slot, rumble, your profile's **Default / Any Controller** bindings, **extra buttons** (L4 / L5 / R4 / R5 and the **…** button) mappable to any button, click or key, a **trackpad mouse** (right, left or both), and the Input Controls **Test** dialog. Tested over Bluetooth and a USB cable; the wireless puck is supported but not tested yet. Thanks to **TAR** for testing it through six builds.
- **🎞️ Frame generation that fits your screen.** A side-menu warning when Max FPS × multiplier is more than your screen can show, with a one-tap **Set Max FPS to N**; **Auto (match FPS)** fits the display to exactly Max FPS × multiplier (or the closest rate above it) and turns on while frame generation runs — turn it off and that game remembers; and a plain "can't run here" reason (Renderer Driver below Vulkan 1.3, missing `Lossless.dll`, non-Vulkan renderer) with the multiplier buttons greyed out.
- **🖼️ SGSR HQ.** A new Scaling mode next to SGSR that follows the direction of edges for cleaner diagonals, at about the same cost (Vulkan and OpenGL).
- **🧵 Per-game texture filtering.** **Anisotropic filtering** (2×–16×) and **Texture sharpness** (*Auto* matches your upscaling) in the DXVK / VEGAS settings, for DirectX 9–11 games.
- **📐 New containers fit your panel.** 1280×800 on 16:10 and 3:2 screens, 1280×960 on 4:3 ones; 16:9 stays at 1280×720. Existing containers are never changed.
- **📘 LSFG Native guide updated** for 3.1.0 — [LSFG Native Made Simple](https://the412banner.github.io/Bannerlator/lsfg-native-guide.html).

### 🆕 Earlier — 3.0.9 (the frame-generation update)

**Win-FG Native**, Bannerlator's own frame generation, moved into Bannerlator's compositor — where it finally works: nothing to buy, nothing to import, an exact 2× on real hardware, and its source is public (MIT). Full notes: [`docs/releases/3.0.9.md`](docs/releases/3.0.9.md).

- **🎞️ Win-FG Native in the compositor.** Measured 45 → 90 fps at 2× on an Adreno 750 — as an in-game layer it had been losing its generated frames — and it switches on in about 124 ms. Source: [The412Banner/win-fg](https://github.com/The412Banner/win-fg).
- **🩹 LSFG Native — three fixes.** No smear on switch-on, the switch-on stall roughly halved (4.8 s → 2.25 s), and two freeze paths closed.
- **💾 Save backups carry the emulator's account id**, so a restored save no longer shows only *New Game* after a container rebuild.
- **💽 Move an installed Steam game** between internal storage and the SD card, from the game's ⋮ menu.
- **🎛️ Preset editing** on the Container screen and in a game's own settings, scoped to where you make the edit.
- **📦 New `-arm64ec-6` compatibility layers** — controllers no longer stop working part-way through a game.

### 🆕 Earlier — 3.0.8 (the downloads-and-containers update)

**The downloads-and-containers update.** Epic, GOG and Amazon downloads move onto the same Rust engine Steam uses — two to nearly three times faster on the same titles. Containers are no longer stuck on the compatibility layer they were created with: when a newer layer appears, the container card offers **Update layer**, downloads it if needed, keeps your games, saves, shortcuts and settings, and can put the old one back. New `-arm64ec-5` layers fix the Denuvo unwinder crash (**Need for Speed Heat** now reaches gameplay). Also: one-tap **Screen Effect Looks**, a **Media tab** on every store page, and the file-manager and EA fixes reported since 3.0.7. Entirely app-side — **no ImageFS reinstall**; install over 3.0.7 and everything carries over. Full notes: [`docs/releases/3.0.8.md`](docs/releases/3.0.8.md).

- **⬇️ Epic, GOG and Amazon downloads on the Rust engine.** The last three Java download managers are replaced by the adaptive-concurrency engine Steam already uses. Same title, same Wi-Fi: GOG 4.5 s → **2.2 s**, Epic 11.4 s → **4.3 s**, Amazon 68 s → **48 s**. A **Store download speed** row in Log Manager (Slow/Medium/Fast/Blazing, default Fast) sets how many pieces are pulled at once; the Java engines stay as a fallback.
- **🔄 Update a container's compatibility layer in place.** When a newer build of your container's layer exists, the container card offers **Update layer → vN** with a **?** explainer. It backs up the registry, switches the layer and refreshes only Wine's own files — **games, saves, shortcuts and container settings are kept**, DXVK / FEX / game files untouched. If the layer is only in the catalog it is downloaded first. The old layer stays installed and **Revert layer** is in the ⋮ menu.
- **📦 New `-arm64ec-5` compatibility layers.** All seven Proton / GE-Proton layers rebuilt ([`build-bionic-layers-20260907-eccode`](https://github.com/The412Banner/proton-wine/releases/tag/build-bionic-layers-20260907-eccode), now the catalog default): a Wine `RtlIsEcCode` bounds check that stops Denuvo-protected games dying in the exception unwinder (**Need for Speed Heat** now reaches gameplay, and **Titanfall 2** boots), DirectAudio v1.3.2, and one `.wcp` per layer for both 4 KB and 16 KB page sizes. *Components matter: Heat was proven on this layer with a FEXCore nightly, and black-screens on a different one — see the release notes.*
- **🎨 Screen Effect "Looks".** One-tap presets heading the drawer's Screen Effects section on both renderer paths — Game Clarity, Vivid, Cinematic, Competitive, Filmic, Arcade, Retro CRT and more — plus a new **Saturation** control on both paths so the colour Looks grade identically.
- **🛒 Store Media tab.** Screenshots and trailers on the Steam, GOG, Epic and Amazon detail pages; the official catalog can also be used as a Contents repository, and a download can be kept as a save archive only.
- **🛠️ Fixes.** File manager: the up arrow and Back work after opening a Favorite or a side-bar shortcut, and every folder lists a **`..`** row ([#476](https://github.com/The412Banner/Bannerlator/issues/476)); EA setup finds games added before EA support existed (the Need for Speed Heat "couldn't locate the install folder" report); layers with a version code of 10 or higher resolve instead of falling back to Proton 9; Relative Mouse saved per game ([#431](https://github.com/The412Banner/Bannerlator/issues/431)); uniform file-manager card height ([#475](https://github.com/The412Banner/Bannerlator/issues/475)); the launch failure card's Close works from the Games screen.

### 🆕 Earlier — 3.0.7 (the EA update)

**EA-published games from your Steam library** launch the way they do on a PC — Steam starts EA Desktop, EA Desktop starts the game. A one-time **Set up EA Desktop** session per container, an **EA** badge on detected titles, a card naming EA's reason when it refuses a licence, `-arm64ec-4` layers with the EA networking and installer fixes, and a rebuilt **Input Controls import** flow with live previews. Full notes: [`docs/releases/3.0.7.md`](docs/releases/3.0.7.md).

- **🎮 EA games from your Steam library.** Origin-era and EA-app-era titles are detected and get an **EA** badge. The first launch offers a one-time **Set up EA Desktop** session (you click through EA's installer; the app handles Mono, Gecko, the registry and the Steam install script). After that it is press-play: the game starts through Steam and EA Desktop in the right order, and the session stays alive through EA's hand-offs. Device-proven with **Need for Speed Payback / Most Wanted (2012) / Rivals** and **STAR WARS Jedi: Survivor**. Needs the **SteamLite** launch mode (selected and remembered for EA titles), the new **`-4` compatibility layers** from the catalog and the hosted **SteamLite v7** client. Titles using EA's Javelin anti-cheat are refused up front. *Researched and implemented by The412Banner by reverse-engineering GameHub's EA launch methods and techniques.*
- **📦 New compatibility layers.** All seven Proton / GE-Proton layers rebuilt into a new `-arm64ec-4` slot ([`build-bionic-layers-20260906-eanet2`](https://github.com/The412Banner/proton-wine/releases/tag/build-bionic-layers-20260906-eanet2)): EA Desktop can go online (dual-stack DNS + a real default route) and, on the Wine 11 layers, EA's installer no longer aborts on a GDI+ assertion. Create a fresh container on a `-4` layer for EA titles; existing containers keep their layer.
- **⚠️ EA limits to know.** Older EA games (e.g. Payback) ask for the EA sign-in on every launch, and EA counts each one — too many in a short period and EA refuses the game for a while ("too many computers"; clears in hours, or via *Deauthorize computers* in your EA account). Every container looks like a separate PC to EA. Need for Speed (2015) goes black after activation — open.
- **🛑 When EA says no, you see why.** If EA Desktop refuses to license a game, a card names EA's reason with Retry / Open log folder / Close instead of a black screen.
- **🕹️ Input Controls import rebuilt.** Arrows point the right way, a proper import chooser (in-app file manager / system picker / community list), and a per-row **preview** in Download Profiles so you can see a layout before importing it.
- **🎞️ LSFG Native follow-ups.** Each generated frame is presented as soon as it is ready; the drawer shows the chain's GPU cost in ms per generated frame; the shader cache is built at import; the pacer's ceiling comes from the panel's top refresh mode.
- **🛠️ Fixes.** HUD API line reads the game's real API on EA titles (D3D11 · DXVK, not D3D12); stray "EA" cards no longer appear in the Games grid; SteamLite / Goldberg updates can't leave the client missing; one broken shortcut file no longer crashes the app at start; EA titles render on app-built containers (ASCII install path); install-script registry parity with the real Steam client.

### 🆕 Earlier — 3.0.2 (the Steam-focused update)

**A Steam-focused update** on top of the `3.0.1` point release — Friends & Chat, real-Steam (VAC) online play, achievements & cloud saves, one-touch save syncing and SD-card installs, plus graphics / frame-gen and runtime polish. Entirely app-side — **no ImageFS reinstall**; install over 3.0.x and your containers, Wine installs, themes, accent colour and per-game settings all carry over. (Real-Steam is a separate one-time in-app download.)

- **🖥️ Steam Friends & Chat (off by default).** A full friends list with live presence (online / in-game / away), unread badges and collapsible sections; **per-friend chat with text *and* images**, persisted history that opens at the newest / first-unread message, add-by-code / name-search / quick-invite, an in-app friend profile, a landscape two-pane view, chat notifications with a sound toggle, typing indicators, and instant reconnect after a background drop. The whole feature is a **master opt-in** that stays dormant until you turn it on. 🙏 Thanks to **iamtooshaar** (Discord) for helping test and shake out the bugs.
- **🔐 SteamLite — real Steam (VAC) online.** A new launch mode that runs a game through the **genuine Steam client** for a VAC-eligible, online session — chosen per game in the launch popup (RealSteam / Goldberg / Raw). It watches the game's **launcher exe** (not the arch-specific `…_win64`) so the game launches *secure* regardless of which exe the shortcut names, pops the in-game gold achievement pill for real-Steam unlocks, and can capture an opt-in scrubbed log for diagnosing a launch. The Steam client + clean-room headless agent ship as a downloadable **[`steamlite-v1`](https://github.com/The412Banner/winlator-contents/releases/tag/steamlite-v1)** content package, installed from inside the app. **Device-proven online** on **Counter-Strike 1.6**, **Counter-Strike: Source**, **Team Fortress 2** and **Left 4 Dead 2** (VAC-secured), plus **Brawlhalla** and **Stumble Guys** (live-service); **Borderlands 2** is in testing.
- **🏆 Achievements & cloud saves — in both launch modes.** Real Steam achievements are seeded from your account into a **tabbed detail-page grid**, with in-game **gold pills** as you unlock them — and they unlock **and** sync whether you play through **Goldberg** (emulated) or **SteamLite** (real Steam / VAC), the same pill and the same sync-back either way. Cloud saves keep parity around each play session, and a new one-touch **"Sync Now"** button on the Save Manager banner syncs **every** flagged game at once — each in the correct direction (upload what's newer locally, pull what's newer in the cloud) — with a live "Syncing 3/7…" count instead of tapping each game.
- **💾 Install Steam games to an SD card.** A per-game toggle installs a Steam download onto a removable SD card to free internal space; the shortcut resolves through a **container drive letter**, so secure launch, saves and achievements are unaffected.
- **🏪 Rebuilt Steam store.** The per-game detail screen was redesigned into a cleaner, **landscape-aware tabbed** layout (overview · achievements grid · DLC) with the store badge, connection state and launch actions in one place — plus a live connection pill, auto-connect on launch, download hardening and full owned-DLC listing.
- **🎞️ Graphics & frame-gen fixes.** A fuller frame-gen pause / teardown / resume reset on level / model / preset change, a swapchain-recreate config reload, an Adreno-840 present-id freeze fix, and in-app frame-gen diagnostics.
- **🎛️ Containers, launch & file manager.** **DXVK HUD is now off by default** on new containers (still toggleable in the env-var editor), a redesigned source- and orientation-adaptive launch popup, a **CUSTOM** origin badge for games you add yourself, and file attributes (Read-only / Hidden) shown in the File Manager.

Also rolled up from the **`3.0.1`** point release on 3.0.0: **swipeable on-screen controls** (slide-to-press, a per-element toggle and a dedicated **Swipe** drawer tab), an LSFG frame-gen fix, a Steam depot **OOM** fix ([#408](https://github.com/The412Banner/Bannerlator/issues/408)), a controls-alignment fix ([#413](https://github.com/The412Banner/Bannerlator/issues/413)), and assorted File-Manager / ETA / crash fixes.

### 🆕 Earlier — 3.0.0 (the big 3.0 stable)

**The big 3.0 stable** — rolls up everything since 2.9.9, including the `3.0.0-pre1` / `pre2` opt-in betas. Entirely app-side — **no ImageFS reinstall**; install over 2.9.9 and your containers, Wine installs, themes, accent colour and per-game settings all carry over. Full notes on the [**3.0.0 release page**](https://github.com/The412Banner/Bannerlator/releases/tag/3.0.0).

- **🔊 New three-engine audio stack (the headline).** The ground-up audio rebuild that was "coming soon" in 2.9.9 has landed: a new **adaptive PulseAudio engine** (grows its buffer on crackle/underruns, re-opens cleanly when your output changes), a second **adaptive ALSA** engine, and an experimental third **DirectAudio** driver — each with an Audio cog in the container editor, per-game shortcut settings and the in-game side-menu, presets + fine-tune rows, a per-row **"?"** explanation, and a latency slider that really drives the buffer. Strict per-engine / per-scope settings mean changing one never rewrites another.
- **🛒 Storefronts + automatic cloud saves across GOG, Epic & Steam.** GOG gets a major overhaul — automatic cloud saves (auto-path, upload-on-exit / download-on-launch, content-hash skip), MD5 verify + repair, redistributable auto-install, per-DLC installs, faster parallel downloads, and a Save Manager tab with cover art. Epic / EOS adds cloud saves, real launch-time sign-in + EOS game IDs, EPIC/EOS badges, Denuvo & offline support, and a robust cancelable downloader. Steam adds one-tap **Lossless Scaling "Detect from store"**, beta-branch selection, and non-game apps in your library.
- **🎮 Games & shortcuts.** The internal-storage intro-movie hang fix — **add a game from Drive C** and **Copy game to Drive C** (moves a game onto the container's fast C: drive and re-points the shortcut), plus **Change executable**, a file picker that opens at your storage root with a working Back button, responsive shortcut settings, per-DLL toggles, per-game graphics persistence, and an opt-in **Big Picture games wall**.
- **🖥️ Graphics & performance.** Hardened Native Rendering with a per-container / per-game **backend picker**, clean-room **win-fg** frame generation (replaces bionic-fg, adapts AMD FidelityFX FSR3), Samsung Galaxy **no-root power control**, a live **Performance dashboard**, an upgraded **Fusion HUD** (battery temp, accurate DXVK/VKD3D label), and a new **Contents hub** for browsing/installing/removing GPU drivers & components. Plus a **wrapper + Adreno Vulkan fix ([#403](https://github.com/The412Banner/Bannerlator/issues/403))** — DXVK/games start again on wrapper drivers and low-tier Adreno (e.g. 610).
- **🐛 Stability.** Rootless native crash-capture with real backtraces, a background-pause fix for games with very long executable names, malformed-environment-variable resilience, and assorted X-server leak/crash fixes.

---

## 🎞️ Frame Generation & Present Modes

**Frame generation** inserts in-between frames to make motion look smoother — it helps most when a game runs *below* your screen's refresh rate. It runs on the **Vulkan renderer**, and there are two engines:

| Engine | Where it runs | Needs |
|---|---|---|
| **Win-FG Native** | inside Bannerlator's compositor (Android side) | nothing — Bannerlator's own, weightless |
| **LSFG Native** | inside Bannerlator's compositor (Android side) | your own `Lossless.dll` |

*(The older **lsfg-vk** engine, which ran the same shaders as a layer inside the game, is retired: its generated frames were routinely lost on the way to the screen. LSFG Native uses the same DLL and replaces it; containers still set to lsfg-vk run LSFG Native.)*

Both engines are what to reach for on a game you've capped: lock the game at **30**, pick **4×**, and the panel gets **120** — with the game itself untouched, the device cooler than running it uncapped, and the in-game HUD showing `30→120`. They work best at or under **panel ÷ 4** (36 fps on a 144 Hz screen fills every refresh). **Win-FG Native** needs no files at all; **LSFG Native** runs the Lossless Scaling shaders out of your own DLL.

**Present mode** decides how finished frames are handed to your screen:

| Mode | What it does |
|---|---|
| **FIFO** (default) | "Vsync on" — smooth, tear-free, most battery-friendly, but it makes the game wait for the display. |
| **Mailbox** | "Fast vsync" — never makes the game wait, still tear-free. |
| **Immediate** | "Vsync off" — lowest input lag, but can tear. |

Bannerlator handles the present mode for you. **Both native engines force FIFO** while they're generating — they queue the real and generated frames together and need FIFO to show them one per refresh; under Mailbox the display would keep only the newest and discard the rest. Your chosen mode is restored the moment frame generation turns off. While a native engine is generating it also switches the **FPS limiter on** and turns **Auto (match FPS)** on, which fits the screen to Max FPS × multiplier — the exact rate, or the closest one above it (turn Auto off and that game remembers). If Max FPS × multiplier is more than your screen can show, the side menu warns you and offers a one-tap fix. Both controls come back as you had them when frame generation stops. New to it? Read **[LSFG Native Made Simple](https://the412banner.github.io/Bannerlator/lsfg-native-guide.html)**. You can also switch modes live from the **Present Mode selector** in the in-game Graphics tab, and every mode is explained by a **"?"** button and in the in-app **"What is all this?"** glossary.

### Should I switch my games to Wayland?

**No — not the ones you care about.** Wayland is **experimental** and new. X11 is still the default, still fully supported, and still the right choice for playing. Wayland exists so you can *try* the things X11 cannot do — HDR10, playing on a TV, zero-copy presentation, picking a game driver per game — ideally on a spare container rather than the one with your save files.

It also has hard requirements: a **versionCode 8 arm64ec Proton 11 layer** (the **(v8)** rows in the catalog) and an **Adreno** GPU. On anything else the option is greyed out and a launch falls back to X11 with a notice. If a game misbehaves on Wayland, switch that container back to X11 — one setting — and report it with the log from `Download/Wayland-logs/`.

### Why is my FPS reading different from another emulator?

With frame generation on, two apps' FPS numbers can look very different — because they **count frames at different points in the pipeline**:

- An app that reads the game's **raw output** shows a clean **2× / 3× / 4×** — impressive, but it counts frames your screen never actually displays.
- Bannerlator's HUD counts the frames the **game** delivers. With either native engine the generated frames are added *after* that point, so every HUD style shows both numbers — **`30→120`** — the game's rate and what actually reaches the panel. On a guest-side layer (the frame-gen training-capture path) the same arrow appears only if frames are being *lost* between the game and the panel (e.g. `70→35`), which is a problem rather than a feature.

**Neither number is "frames on glass."** Your panel's refresh rate (e.g. 120 or 144 Hz) is the true ceiling — above it, frames are generated but not shown. A result like **65 → 107 fps at 2×** on a demanding game, with the frametime roughly **halving**, is frame generation working correctly.

---

## ✨ Full Features

Everything Bannerlator offers, at a glance. No PC and no root required — it runs Windows apps and games directly on your Android device.

<details>
<summary><b>🍷 Windows compatibility</b></summary>

- **Wine** Windows compatibility layer — run native Win32/Win64 applications and games.
- **Box64 / Box86** x86 & x86-64 → ARM translation, with selectable performance presets.
- **WOWBox64** for arm64ec containers (correctly labelled per container).
- **FEXCore** as an alternative x86/x64 emulation backend — with **automatic unixlib (`.so`) matching**: whichever FEXCore version you pick per game or container, the native `.so` companion is kept in sync on every launch (matched for unixlib builds, cleared for DLL-only), so there's never a stale or mismatched `.so`.
- **arm64ec** and **x64** container support.

</details>

<details>
<summary><b>🎨 Graphics & translation layers</b></summary>

> 📖 **Not sure which graphics driver or wrapper to use? [Read the wrapper & driver guide →](docs/graphics-wrappers-guide.md)** — what a wrapper actually does, a pick-by-GPU table (Adreno / Mali / Xclipse / PowerVR), every built-in driver explained, all 18 catalog wrappers with their authors and upstream links, which of them are **byte-identical across projects** (so you don't test the same file twice), and troubleshooting.
- **DXVK** — DirectX 8 / 9 / 10 / 11 → Vulkan (with GPLAsync and Sarek variants).
- **VKD3D-Proton** — DirectX 12 → Vulkan.
- **WineD3D / DirectDraw** OpenGL fallback paths for older titles.
- **D7VK** — DirectX 7 / DirectDraw (Direct3D 3–7) → Vulkan for old 2D/3D titles that otherwise take the slow OpenGL path ([WinterSnowfall](https://github.com/WinterSnowfall/d7vk)'s DXVK-lineage Vulkan implementation). Selectable in the **DDraw Wrapper** picker **per container and per game**; ships **bundled** as the default and is **catalog-backed** — a **"D7VK Version"** dropdown lets you download and switch between nightly d7vk builds.
- **Proton bionic** translation layers (via GameNative) — including **Proton 11.0-1** in **arm64ec** and **x86-64** builds, packaged per Android SDK (**SDK 28** for Android 9-era, **SDK 35** for Android 15) and downloadable from the Compatibility Layers menu.
- **VEGAS** — Adreno-optimized DXVK for reduced stutter and real-time upscaling on mobile GPUs.
  - > 📖 **New to VEGAS?** Read the **[VEGAS DXVK FAQ](https://htmlpreview.github.io/?https://github.com/The412Banner/Bannerlator/blob/main/docs/vegas_faq.html)** — install, config, FSR, tiers, frame generation & shader-stutter troubleshooting.
  - > 🚀 **Support VEGAS Development** — low-level graphics dev & vibecoder: debugging, refactoring & improving original DXVK code for Adreno. **[❤️ Sponsor isygold →](https://github.com/sponsors/isygold)**
- **Per-game texture filtering** *(new in 3.1.0)* — **Anisotropic filtering** (2× / 4× / 8× / 16×) and **Texture sharpness** (Game default / Auto / −0.25 to −1.0; *Auto* matches your upscaling, following AMD's FSR guidance) in the DXVK / VEGAS settings, per container or per game. DirectX 9–11 (DXVK) only; nothing changes at *Game default*.
- **Turnip / Mesa** open-source Adreno Vulkan drivers, with Timeline Semaphore patches for newer DXVK; bundled and downloadable driver options.
- **Driver-source management** — add, toggle and remove your own **adrenotools GPU-driver feeds** (a custom JSON URL or a GitHub `owner/repo`) on top of the built-in sources, so you can pull Turnip / driver builds straight from the repos you trust. *(Requested in [#160](https://github.com/The412Banner/Bannerlator/issues/160).)*
- **BCn transcoding for Mali / Xclipse** — a **"Wrapper + bcn_layer"** graphics driver ([leegao](https://github.com/leegao)'s [bcn_layer](https://github.com/leegao/bcn_layer), shader-v3) that decodes BC textures on the GPU, so BCn games run on GPUs without hardware BC support — with a **BCn Layer Settings** panel (force-decode, ETC2 / ASTC transcode, image-view mode, debug logging). An experimental **"Wrapper-gamenative"** driver (BCn baked into the wrapper, Adreno-only) is also selectable. *(Device-proven on Mali-G57.)*
- **Wrapper Version Manager** — bring your own graphics wrapper: **import / update / delete** any `.tzst` wrapper (from another project or your own build), browse a **curated downloadable catalog** of wrappers from across the Winlator family (each credited to its source, flagged **"Mali only"** where relevant), and get **auto-detected settings** — real toggles / sliders / dropdowns read straight from what each wrapper actually supports, with driver internals and log noise filtered out. Per-entry **Update / Reset / Edit / Delete / Details** plus a pre-import inspection view. *(Modeled on [WinlatorMali](https://github.com/GunaCharanTeja/WinlatorMali)'s graphics-driver manager; requested in [#132](https://github.com/The412Banner/Bannerlator/issues/132).)* 📖 **[Which wrapper for my device? →](docs/graphics-wrappers-guide.md)**
- **Mali DX12 (experimental)** — a new opt-in **6th graphics driver, "Wrapper + compat + bcn"**, pairing [leegao](https://github.com/leegao)'s BCn transcode layer with a **DX12 compat layer** and a **"Use GameNative engine (DX12)"** toggle, for **Valhall-class Mali** GPUs. Inert unless selected and unaffected on Qualcomm / Adreno; DX12 on Mali is still being proven on hardware — treat it as a **test path** and report back with logs.

</details>

<details>
<summary><b>🖥️ Renderers</b></summary>

- Multiple host renderers — **Vulkan**, **OpenGL**, **SurfaceFlinger**, and **VirGL**.
- **Wayland display backend** *(experimental, new in 3.1.2)* — an alternative to the Java X server: the game draws into Bannerlator's own embedded **Wayland compositor**. Brings **HDR10**, **launching a game on an external screen / TV**, **zero-copy presentation**, real **mouse-look** and **eight bundled Wayland Turnip drivers** selectable per game. Chosen **per container**, off by default.
  - > ⚠️ **Experimental, and not a replacement for X11.** Expect bugs; X11 stays the default and stays supported. Requires a **versionCode 8 arm64ec Proton 11 layer** (the **(v8)** catalog rows) and an **Adreno** GPU — Mali / Xclipse / PowerVR stay on X11.
- **SurfaceFlinger renderer colour fix** — the SurfaceFlinger (ASurfaceRenderer) host renderer got a crash + colour-accuracy fix (red/blue channel swap corrected, GPU-side format converter, proper fencing), with a **"Correct SurfaceFlinger colours"** toggle available **per container and per game** (shown inline under the Renderer picker when SurfaceFlinger is selected, on by default). *(Ported from [GameNative](https://github.com/utkarshdalal/GameNative) #1620 / #1644.)*
- > ℹ️ The **Vulkan host renderer** uses the rendering path from **[StevenMXZ](https://github.com/StevenMXZ/Winlator-Ludashi)** (Winlator-Ludashi); its `AHardwareBuffer` present path — what makes Vulkan / DXVK / VKD3D content actually display correctly — was ported from / cross-examined against **[GameNative](https://github.com/utkarshdalal/GameNative)**. See [Credits](#-credits).
- **Native Rendering (Low-Latency Mode)** — low-latency direct-scanout presentation on **both the Vulkan *and* OpenGL renderers**, skipping the compositor blit to cut input lag (mutually exclusive with that renderer's post-processing effects / scaling, since it bypasses the compositor).
- **Spatial upscalers on *both* the Vulkan *and* OpenGL renderers** — **SGSR** (Snapdragon GSR 1.0), **SGSR HQ** *(new in 3.1.0 — its edge-direction variant, for cleaner diagonals)* and **FSR / FSR-Fit** (AMD FidelityFX Super Resolution 1.0), plus **NIS** (NVIDIA Image Scaling, Vulkan), a **Sharpen** (RCAS) mode and Linear / Nearest, all switchable live in the in-game drawer. On Vulkan it engages when a game renders below display resolution; on OpenGL it renders the scene at a reduced internal resolution and reconstructs it back up. Every sharpness slider runs 0 (off) → 100 (max). Your chosen scaling mode is now **remembered per game** across relaunch.
- **Fullscreen aspect-ratio modes** — control how a game fills the screen, **per container and per game**: **Off** (windowed, letterboxed), **Fit** (fullscreen, aspect preserved), **Stretch** (fills, ignores aspect), **Fill** (fills with aspect kept, cropping the overflow — no bars, no distortion) and **Integer** (largest whole-number scale, pixel-perfect and centered). A five-button selector in the in-game drawer switches modes live without closing the drawer, on all three host renderers. Your choice is saved per game.
- **Supersampling (Render scale)** — render above display resolution (1.25× / 1.5× / 2×) and downsample with a Lanczos-2 filter for DSR / OGSSAA-style anti-aliasing; set per container / per shortcut.
- **Screen effects on both the OpenGL *and* Vulkan renderers** — FXAA, Toon, CRT, NTSC, Color grading, **CAS** sharpening, and fake-HDR (the Vulkan path runs them through a new post-processing pipeline; previously they were OpenGL-only).
- **Debanding (Vulkan)** — an optional terminal dither pass that removes the visible banding from smooth gradients, skies, and dark scenes on 8-bit output, with an adjustable strength.
- **ReShade post-processing** — run real ReShade `.fx` effects (colour grading, sharpen, film grain, CRT, tonemap…) on **DXVK / VKD3D** games. Effects compile **on-device** via a bundled **[vkBasalt](https://github.com/DadSchoorse/vkBasalt)** layer; pick from an **on-demand catalog** of ~100 curated MIT/CC0 effects or drop your own into the `ReShade/` folder. A dedicated in-game **ReShade tab** auto-generates properly typed controls (sliders / toggles / dropdowns / colour pickers) from each shader, so you can **toggle and tune effects live** with a Reset-to-defaults button. Effects are configured **per game** (container or shortcut), persist across relaunch, and can be run **solo or stacked**. *(Color effects today; depth effects such as SSAO/DOF are not included yet.)*
  - > ⚠️ **Stacking multiple effects? Add them a few at a time.** Each effect compiles on-device and costs GPU — **selecting too many at once can stop a game from starting**, showing a **flat / blank screen** instead of the game. If that happens, **uncheck effects one at a time** (or the specific heavy one) in the per-game **ReShade effect** settings until it boots, then add more gradually.
- **Match refresh rate to FPS (VRR)** — the display's refresh rate can follow your frame rate: an **Auto (match FPS)** toggle or a manual **60 / 90 / 120 / 144 Hz** slider, on all three host renderers, auto-disabled on displays that don't support variable refresh.
- Adjustable resolution and frame-rate limit.

</details>

<details>
<summary><b>🎞️ Frame generation & pacing</b></summary>

- **Two selectable frame-generation engines** — pick **Off / Win-FG Native / LSFG Native** per container; the running engine is shown as a badge in the in-game drawer. **Both now run inside Bannerlator's own compositor**, which is what lets their generated frames actually reach the panel.
  - **Win-FG Native** — Bannerlator's own **clean-room** frame generation ([source, MIT](https://github.com/The412Banner/win-fg)): FSR3-family optical flow plus from-scratch synthesis, no proprietary weights, **nothing to buy and nothing to import**. Built in and ready out of the box. Device-proven 45 → 90 fps at 2× on an Adreno 750, and it arms in ~124 ms because its shaders are compiled in rather than parsed from a file. It previously ran as a layer *inside the game*, where the compositor discarded most of its generated frames — the in-game counter read 2× while the panel showed 1×; running it natively removes that hop.
  - **LSFG Native** *(new in 3.0.6)* — Lossless Scaling's frame generation running **inside Bannerlator's own compositor**, ported from the **Eden** emulator's native present path with **WinNative**'s implementation as the reference. The shaders come from *your own* `Lossless.dll`, extracted on device. Device-proven 30 → 120 fps at the panel. **Device support:** generally Adreno **A7xx and newer** GPUs, plus some MediaTek **Dimensity** devices in community testing; elsewhere it reports itself unsupported in-game or may not keep up. **Experimental (3.1.1, off by default):** a **capture resolution** that runs the frame generation at a lower height and scales it up — far cheaper on weaker GPUs — and a **Vulkan 1.1 compat** switch for stock drivers that report only Vulkan 1.1 but have the needed extensions ([#512](https://github.com/The412Banner/Bannerlator/pull/512) by clintOnSky). *(The former **lsfg-vk** in-game layer engine is retired; anything still set to it runs LSFG Native.)*
- > ℹ️ **Why win-fg exists, and what it is.** Bannerlator previously bundled a different frame-generation layer that was later found to embed compute shaders whose model weights were **essentially identical to the proprietary Lossless Scaling frame-generation model** — which cannot be redistributed. That layer was **removed**, and **win-fg** is its **clean-room replacement**: a frame-gen engine we built from the ground up. Its motion estimation is our MIT adaptation of **AMD FidelityFX FSR3 optical flow** (an algorithm — no learned weights), and its frame synthesis is written from first principles using published, permissively-licensed math. It bundles **no proprietary code or weights**. That's also why the two engines sit side by side: **LSFG Native** relies on *your own* legally-owned `Lossless.dll`, while **Win-FG Native** is entirely Bannerlator's own and needs nothing to run. Its full source, shader-by-shader provenance and engineering log are public at **[The412Banner/win-fg](https://github.com/The412Banner/win-fg)**.
- > ⚠️ **LSFG Native requires you to supply your own `Lossless.dll`.** Bannerlator bundles **no** proprietary Lossless Scaling files. You must own **[Lossless Scaling](https://store.steampowered.com/app/993090/Lossless_Scaling/)** (THS, on Steam) and import its `Lossless.dll` via **Settings → Frame Generation — LSFG Native** (Detect from the in-app Steam store, or import a copy). The DLL is copied into app storage, its shaders are extracted and cached on device, and it serves all containers. Until you import a valid `Lossless.dll`, the **LSFG Native** option stays greyed out; **Win-FG Native** needs no DLL and works without it.
- **Live in-game controls** for whichever engine the container runs: switch between **Off / 2× / 3× / 4×** and adjust the **flow-scale** slider right from the in-game Graphics drawer, hot-reloaded with no restart.
- **Screen fit & warnings** *(new in 3.1.0)* — while frame generation runs, **Auto (match FPS)** fits the display to Max FPS × multiplier (the exact rate or the closest one above; a per-game opt-out), the side menu warns when your numbers are more than the screen can show and offers a one-tap **Set Max FPS to N**, and a plain reason appears when frame generation can't run on your driver or renderer.
- **FPS Limiter** — a **standalone, engine-independent** live frame cap. It paces the X11 Present extension by delaying the `IdleNotify` that frees the guest's buffer, so the game itself throttles (the in-game HUD reflects the cap and GPU/power draw drops). Works the same with frame gen **Off**, **win-fg**, or **LSFG Native**, on both host renderers, all guest APIs. While **LSFG Native** is generating the limiter is **locked on** — a steady, capped game is what frame generation is built on — and restored when it stops. This guest-side present-pacing mechanism was ported from **[GameNative](https://github.com/utkarshdalal/GameNative)** (see [Credits](#-credits)).
- **Performance model** — LSFG Native always runs Lossless Scaling's lighter "performance" model (about 2 ms of GPU per generated frame on an Adreno 750). *(The lsfg-vk Performance-mode toggle requested by [@Tony57319](https://github.com/Tony57319) in [#152](https://github.com/The412Banner/Bannerlator/issues/152) is retired with lsfg-vk; that model is now the one LSFG Native uses.)*
- Confirmed on **both** the OpenGL and Vulkan host renderers.

</details>

<details>
<summary><b>⚡ Performance & thermal controls</b></summary>

Power-user device-tuning, reachable from **App Settings → Performance** and mirrored live in the in-game **Debug** tab (kept in two-way sync), with **global defaults** and optional **per-game overrides** — an override is honored only when it differs from the global default, and each has a one-tap **reset-to-global**.
- **No root required** — **Sustained Performance Mode** (steadies clock speeds over long sessions), **Thread Priority Boost** (raises the guest CPU-worker threads for more CPU time, never downgrading an already-hot thread), and **Prefer Big Cores** (pins the running game to the fastest CPU cluster instead of the efficiency cores).
- **Opt-in root tier** (**Magisk / KernelSU / APatch**) — behind a **Grant Root** gate and a scroll-to-accept ***"USE AT YOUR OWN RISK"*** disclaimer: **CPU governor → performance**, **lock CPU frequency to max**, **keep all cores online**, **lock GPU to max clock**, **disable thermal throttling**, **fan to maximum**, and a one-shot **"Free memory now"** action. Entirely optional — nothing writes to your system files unless you grant root and accept the warning.
- **Always-on snapshot-revert** — the first time a setting is touched its exact prior value is captured, and everything is restored to precisely that value on **game-exit, app-background or crash**. The snapshot is **persisted to disk**, so even a hard kill is repaired on next launch. It never guesses defaults and can't be disabled.
- **Temperature Watchdog** — anchored to your device's **own thermal trip points**, it polls the hottest CPU/GPU zone and force-reverts all performance state *before* the device overheats. Presets **Conservative / Balanced / Aggressive / Manual**, on by default (turning it off needs the same scroll-to-accept disclaimer).
- **In-app help** — a **"?"** explainer on every toggle, an **"Explain toggles"** overview, a watchdog **"What's this?"**, and live **CPU / GPU temps** plus your device's own thermal limits shown inline. *(Device-verified on an AYANEO Pocket FIT / Adreno.)*

</details>

<details>
<summary><b>📦 Containers</b></summary>

- Create and manage **multiple isolated Wine containers**.
- **Redesigned container cards** — a clean spec-chip layout (renderer · DXVK on top, driver · VKD3D · backend beneath) that matches the game cards.
- **Auto-close on game exit** — the session closes itself once the launched game quits (per-container "Close when game exits" toggle, on by default), so you're not left at a black Wine desktop.
- **Import / export** containers to move or back up setups.
- Per-container control of Wine version, graphics driver, DXVK / VKD3D version, Box64 preset, drive mappings, Z-drive selector, and environment variables — the **Add Environment Variable** picker includes a large set of **presets** (DXVK / VKD3D / Wine / Mesa) with typed value editors, so common tuning vars are one tap away.
- **Desktop wallpaper picker** — set an image as a container's Wine desktop wallpaper from the container editor, and choose whether it applies to **just this container** or **globally** to all of them.
- **Compatibility Layers download menu** — a cloud button on each component (Wine/Proton, DXVK, VKD3D, Box64/WOWBox64, FEXCore) opens a downloader to browse, install or remove versions, with **Wine/Proton tabs**, an **"in use"** marker, **install-from-file**, and **byte-accurate download + install progress bars**.
- **In-game refresh-rate unlock** — a per-container / per-game toggle that lets a game pick a refresh rate above 60 Hz from its own display menu (requires a "Refreshed" Proton 10.0-4 / 11.0-1 layer; stays off on older layers by design). Distinct from the display's *Match refresh rate to FPS (VRR)*.
- **Custom startup-services mode** — alongside Normal / Essential / Aggressive, a **Custom** startup option starts with every Wine service off so you enable only the ones you need. *(Requested in [#168](https://github.com/The412Banner/Bannerlator/issues/168).)*
- **DXVK HUD off by default (new in 3.0.2)** — new containers no longer show the DXVK overlay on top of the game. The `DXVK_HUD` variable stays **listed but empty** in the env-var editor, so it's one tap to opt back in whenever you want it.
- **Screen size that fits your panel** *(new in 3.1.0)* — new containers start at 1280×720 on 16:9 screens, 1280×800 on 16:10 and 3:2, and 1280×960 on 4:3 (Retroid Pocket, foldable inner screens). Existing containers and games are never changed.

</details>

<details>
<summary><b>🕹️ Games, shortcuts & input</b></summary>

- **Game library** with grid or list layout, sorting, and installed/updated filters.
- **Redesigned game cards** — primary chips (renderer · DXVK · frame-gen) over a muted driver · VKD3D · backend line, with the resolution in the subtitle; long component names no longer blank the game title.
- Add shortcuts from external storage — a single **`.exe`**, or a whole **games folder** (point at a library folder and every game subfolder is scanned for its real executable, named and cover-arted for one-tap batch add).
- **Smart import** — importing an `.exe` auto-resolves the **authoritative game name and cover art from the Steam store**, fixing generic launcher-exe misnames, with a **Search Steam** confirm step.
- **Recommended components** — a game's bundled redistributables are detected and surfaced as one-tap install chips.
- **Back up & restore game saves** as **GameHub-compatible zips**, with per-game save discovery and a confirm checklist before anything is overwritten.
- **SteamGridDB** cover-art scraping.
- Per-game settings including display language / locale.
- **Virtual Controller Pro on-screen controls** — a Compose-rebuilt controls editor with an **in-game live editor**, control types **Button / D-Pad / Range / Stick / Trackpad** plus **Dynamic Stick**, **Mouse Area**, **Button Grid** (with optional multitouch and QWERTY / F-row / NumPad quick-fill) and **Expandable Buttons** (radial or list fly-outs). Includes **control groups** (show/hide a whole set), **key combos**, **per-element dead zones**, a **Hold key**, **custom control icons** (import, tint or use as the whole button), a category-filtered binding picker, an editor **reference image**, and a **control scale up to 300%**. Profiles export as **ICpx** (or best-effort legacy **ICP**). Overlays **follow your app theme** or take a **per-game custom colour** set in the Controls editor. *(PR [#156](https://github.com/The412Banner/Bannerlator/pull/156) by [arro000](https://github.com/arro000).)* **Swipeable controls (new in 3.0.1)** — a per-element **slide-to-press** toggle plus live per-category swipe gates (Buttons / D-Pad / Sticks), mirrored to a dedicated **Swipe** tab in the in-game drawer so you can slide across buttons instead of tapping each one.
- **Physical controller** support (SDL2), plus touchpad / mouse emulation with adjustable cursor speed. The **external controller-binding screen** lists each input as a card with readable labels, and buttons you press while binding appear instantly.
- **Steam Controller support** *(new in 3.1.0, opt-in)* — the **2026 Steam Controller**, and the original over Bluetooth, read directly through **SDL3**'s Steam Controller driver (**Input Controls → Device → Steam Controller**): a real controller in games with rumble and your **Default / Any Controller** bindings, **extra buttons** (L4 / L5 / R4 / R5 and the **…** button) mappable to any button, click or key, a **trackpad mouse** (right / left / both / off), and full support in the Input Controls **Test** dialog. Other controllers are untouched.
- **Controller vibration** — **PC-accurate dual-motor** rumble (strong/weak driven independently) with a **per-container vibration mode and intensity**, backed by a **winebus duration patch** so sustained rumble doesn't auto-expire mid-effect (Proton 10/11, arm64ec + x86-64). Plus **per-slot** rumble toggles and a **master switch** (in the in-game Vibration section) that silences all rumble regardless of slot, saved globally.
- **Gyroscope — motion aim** — tilt to aim, driving the **right stick**, **left stick** or the **mouse**, in either **Rate** mode (how fast you turn) or **Tilt to Aim** orientation mode (the angle you hold), with a choice of **activator button** (L1 / L2 / R1 / R3 / always-on), **Hold or Toggle** activation, adjustable **sensitivity / deadzone / smoothing / invert**, **device-level drift calibration**, and settings **saved per container and per game**. 📖 **[Full guide →](docs/gyro-controls-guide.md)**

</details>

<details>
<summary><b>🌐 Community Configs</b></summary>

Browse **community-shared, per-game / per-device tuning configs** in-app and apply a known-good setup in one tap.
- **Catalog browser** (globe button in the Games header) with search, Steam / Title filters, sort by upvotes / name / device count, and a **"Matches my device"** filter that narrows to configs shared from hardware like yours.
- **Per-config cards** showing **★ upvotes** and **↓ downloads** (best-rated first), the source device / SoC and the date, aggregated across every folder a game is known by.
- **One-tap Apply** that **surgically merges** just the config's settings — DXVK / VKD3D / Turnip driver / FEX preset / renderer / resolution / launch args / environment variables — into your shortcut, **preserving everything else you've set**. Applies to any shortcut, warning you if it doesn't match the game.
- **Smart install** of a config's missing **DXVK / VKD3D / FEXCore** build or **Turnip GPU driver**: an exact match installs with one confirm, otherwise pick from the closest versions (or browse all), and the config **auto-applies** afterward. FEX date builds match by their **YYMM** monthly tag; components you already have are recognised, not re-installed.
- **Config detail page** — provenance (source device / SoC / app / date), a plain-language list of **what the config sets** in Bannerlator's own component terms, and a **before-you-apply diff** against your shortcut, plus the config's **live description, upvotes, downloads and comments** — you can **upvote** and **comment** yourself.
- **Read-only for your setup** — nothing changes unless *you* tap Apply; your containers, imagefs and existing settings are never touched.
- **Share your own setups** — export a game's working settings and **upload** them for the community in a couple of taps. The export captures the full recipe (graphics translator + all its options, driver, renderer, resolution, launch args, env vars and the rest) plus your device / graphics chip — but **never your files, store logins, or device-specific driver tuning**. Sharing is **anonymous by default** and Bannerlator keeps its configs in **its own space**, separate from other apps' libraries. **My uploads** lets you edit a description inline or delete an upload any time.
- **Optional account (no email needed)** — you never *need* one, but a **username + password** account makes your uploads **follow you to a new device**, puts **your name and picture** on configs you share, and is recovered with a **one-time recovery key** instead of an email reset. Everything — browse, apply, share, manage, upvote, comment — works fully **anonymously** without it.
- 📖 **[Read the full plain-English guide →](docs/community-configs-guide.md)** for a friendly, non-technical walkthrough of browsing, applying, sharing, and the optional account/recovery-key system.

</details>

<details>
<summary><b>🛒 Built-in stores & cross-store Download Manager</b></summary>

Sign in to your existing storefronts and play from libraries **you already own** — Bannerlator does not sell, bundle or circumvent any game or DRM.
- **Steam** — sign in with **username / password or QR code**, browse your owned library, and **download + install** games through a built-in **depot engine** (built on **[JavaSteam](https://github.com/Longi94/JavaSteam)**). Includes a **4-tier download-speed** picker (Slow / Medium / Fast / Blazing), **session hardening** that recovers from Steam's ~1-hour connection-manager logoff so long installs finish, a **connection / login status pill**, and a depot-download **OOM fix**.
  - **DLC picker** — a **"Choose DLC"** sheet lets you opt out of owned DLC before downloading, with the download size updating live as you check and uncheck, plus an **"Includes DLC"** line and a **size breakdown** (footprint / download / catalog / free space) and **download ETA + speed** on the detail page. A **true-size install fix** fetches real depot-manifest sizes so fully-downloaded games are no longer wrongly flagged "incomplete."
  - **Optional Goldberg auto-patch** on a game's detail page — a **[Goldberg](https://mr_goldberg.gitlab.io/goldberg_emulator/) / gbe_fork** Steam-emulator patch for **offline / emulated** play, in **Regular / Experimental / ColdClient** tiers, installed automatically and cleanly reverted on switch-back. *(Modifies a game's shipped files — **use at your own risk**, for games you own.)*
  - **Real-Steam (VAC) online — "SteamLite" launch mode** — a per-game launch option (**RealSteam / Goldberg / Raw**) that runs the game through the **genuine Steam client** driven by a **clean-room headless agent**, for a **VAC-eligible online** session with real friends, matchmaking and achievements. It stages the game so Steam sees it *installed* (secure `LaunchApp`, not an insecure CreateProcess) and watches the **launcher exe** so the game launches secure regardless of which exe the shortcut names. The Steam client + agent ship as a downloadable **[`steamlite-v1`](https://github.com/The412Banner/winlator-contents/releases/tag/steamlite-v1)** content package, installed from inside the app. **Device-proven online:** CS 1.6, CS: Source, Team Fortress 2, Left 4 Dead 2 (VAC-secured), Brawlhalla and Stumble Guys (live-service); Borderlands 2 in testing.
  - **EA games from your Steam library** *(new in 3.0.7)* — EA-published Steam titles get an **EA** badge and a one-time **Set up EA Desktop** session; after that the game launches through Steam → EA Desktop automatically (SteamLite mode, GE-Proton 11.0-6 / Proton 11 layers). Proven with Need for Speed Payback / Most Wanted / Rivals and STAR WARS Jedi: Survivor. *Origin-era titles ask for the EA sign-in every launch and EA rate-limits those activations — see the 3.0.7 notes.*
  - **Steam Friends & Chat** *(off by default)* — a **master-opt-in** friends list with live presence (online / in-game / away), unread badges and collapsible sections; **per-friend chat with text and images**, persisted history, add-by-code / name-search / quick-invite, an in-app friend profile, a landscape two-pane layout, notifications with a sound toggle, typing indicators, and instant reconnect after a background drop.
  - **Steam achievements** — seeded from your account into a **tabbed detail-page grid**, with in-game **gold unlock pills** — working **and syncing in both launch modes**: unlocks fire and sync back whether you play through **Goldberg** (emulated) or **SteamLite** (real Steam / VAC).
  - **Steam cloud saves + Save Manager "Sync Now"** — automatic cloud-save parity around each play session, plus a **one-touch "Sync Now"** button on the Save Manager banner that syncs **every** flagged game at once, each in the correct direction (upload what's newer locally, pull what's newer in the cloud), with a live "Syncing 3/7…" count.
  - **Install to an SD card** — a per-game toggle installs a Steam download onto a removable SD card to free internal space; the shortcut resolves through a container **drive letter**, so secure launch, saves and achievements are unaffected.
  - **Rebuilt game detail pages** — a redesigned, **landscape-aware tabbed** per-game screen (overview · achievements grid · DLC) with the store badge, connection state and launch actions together in one place.
- **Epic Games** — sign in, browse your library, and **download / install / launch** your titles (including Epic **free games**).
- **Amazon Games** — sign in, browse your library, and **download / install / launch** your titles.
- **[GOG](https://www.gog.com/)** — sign in and browse your owned library; **download and install** your **DRM-free** games with **cloud-save** sync and one-tap launch into a container.
- **⬇ Cross-store Download Manager** — one unified manager across **all four stores**: see every active download and your whole installed library in one place, with **live two-bar** download/install progress, **background downloads + notification-shade** support (a foreground service keeps them running when you leave the app), and **launch / verified uninstall** for any installed game. Install state, cover art and update-available status stay in sync across a game's detail page, its download card and the store list.
- > 🔒 These sign-ins are a **third-party login system, exactly like any other emulator/launcher** that logs into these stores — **use them at your own risk** (see [Security Hardening](#-security-hardening--your-store-accounts)).

</details>

<details>
<summary><b>🔒 Security Hardening & your store accounts</b></summary>

The Steam / Epic / GOG / Amazon sign-ins are a **third-party login system, exactly like any other emulator or launcher** that logs into these stores. **Bannerlator is not affiliated with, authorised by, or endorsed by Valve/Steam, Epic Games, GOG, or Amazon.**
- **Use at your own risk.** You are logging your **real store account** into a community app. That's a normal trade-off for this kind of tool — but it's your account and your call.
- **Steam save sync & achievements — use at your own risk.** Steam cloud-save sync and achievements run through **third-party systems** (Valve's Steam and Goldberg), exactly like the store sign-ins. They depend on your connection — an **unstable or dropped internet connection or network, while downloading (syncing saves, achievements or games) or uploading, can leave data un-synced, incomplete or out of date**. It is **your responsibility** to ensure a proper, stable connection and to complete the sync/download/upload when required; verify it finished before relying on it across devices.
- **Your credentials are redacted from logs.** This release strips sensitive values out of everything the stores write, to **logcat *and* the shareable diagnostic files**, via a new `StoreLog.redactUrl` helper: **signed download / manifest URLs** (Amazon / Epic / GOG CDN links carry access tokens in the query), **OAuth authorization codes**, **GOG `client_secret` + `refresh_token`**, and **account identity IDs** (Epic account ID, GOG user ID). Steam credentials were already redacted. None of this changes how login, downloads or cloud saves work — only what gets written to a log.
- **Still be careful sharing logs.** Even with redaction, a log or debug file can contain other diagnostic detail — so only share one publicly if you're comfortable doing so.

</details>

<details>
<summary><b>🧰 Bundled Start-menu utilities</b></summary>

- New containers ship with handy Windows tools in the Start menu — **[Banner File Manager](https://github.com/The412Banner/banner-file-manager)** (our own file manager — see below), **[AIO Graphics Test](https://github.com/The412Banner/AIO-Graphics-Test)**, and **Game Controller Test**.
- **`.lnk` working-directory ("Start in") support** so shortcuts for apps that only run from their own folder launch correctly.

</details>

<details>
<summary><b>📁 Banner File Manager</b></summary>

The bundled Windows file manager (`C:\windows\wfm.exe`) is **[Banner File Manager](https://github.com/The412Banner/banner-file-manager)** — our own native Win32 file manager, forked from [BrunoSX's Winlator File Manager](https://github.com/brunodev85/wfm) (MIT). It ships in every new container's Start menu.
- **Dual-pane split view** (`View ▸ Split View`) — two independent panes, active one highlighted, copy between them.
- **File actions** — Open as administrator, Open with ▸ (registered apps + choose another program), Properties.
- **Native Win32 copy / move / delete** instead of shell32 `SHFileOperation` — sidesteps the Wine shell32 copy-paste crash on Proton 10.0-4.
- **Quality of life** — keyboard shortcuts (F2 / Del / F5 / F6 / Backspace / Enter / Ctrl+C·X·V·A), Show Hidden Files, byte-accurate copy progress bar with cancel, status-bar total size.
- **Theme-aware** (light + dark, follows the container theme; owner-drawn header / status bar / search to match) and **universal x86-64** (Box64 / wowbox64 / FEXCore).

Source, releases & issues: **[github.com/The412Banner/banner-file-manager](https://github.com/The412Banner/banner-file-manager)**.

</details>

<details>
<summary><b>🎛️ Interface & in-game overlay</b></summary>

- Modern **Jetpack Compose** user interface with a redesigned, icon-led navigation drawer.
- **Theme-aware everywhere** — your selected preset / accent recolours the **whole app *and* the in-game side drawer**, including dialogs, chips, sliders and overlays.
- **Customizable themes** — **16 presets** (AMOLED default, Classic Dark, Ocean, Forest, Sunset, Rose, Steel, plus Midnight Cobalt, Phosphor, Carbon & Ember, Amethyst, Crimson, Synthwave, Royal Gold, Frost and Monochrome) plus an **HSV custom-accent picker**.
- **XMB view** *(new in 3.1.1)* — a fourth Games-tab layout after List, Grid and Compact: games run across and the focused game's options run down, with every per-game setting and tool (Settings, Game Details, Properties, Clone, Remove, logs, community configs, cover scraping, Copy to Drive C, save backup / restore, Change executable) opening as further XMB columns instead of pop-ups. Built for a controller (A / B, LB / RB, ◀ ▶ to change values), with touch and swipe too; portrait and landscape.
- **Big Picture mode** — a **Compose-built couch launcher** for TV / handheld use: full D-pad navigation, direct rails to your games, settings and features, and **per-game spec chips** showing what each title is actually set to.
- In-game overlay drawer for settings, input, and quick toggles, with a Task Manager that lists processes as cards and can launch new tasks on any renderer. The **Controls tab is split into Touch / Mouse / Vibration / Gyro sub-tabs**, with a uniform 3-across grid of toggle chips.
- **In-game Task Manager — a full control panel.** Beyond the process cards it now offers Windows-style **per-process Processor Affinity** ("Set affinity" on a process's ⋮ menu — pin it to specific CPU cores **live, mid-game**, applied through the guest's real affinity path with no relaunch), a **live telemetry header** (CPU & GPU usage and temperatures, GPU clock, FPS + minimum, RAM, swap, battery level / watts / temperature / charging, and a per-core clock strip), and a collapsible **container info panel** (Wine/Proton, DX wrapper, renderer, driver, resolution, device).
- **Built-in File Manager with Favorites** — bookmark folders and jump to them from a dedicated list, each labelled by storage source (Internal / SD card / a container's Drive C: or Z:) and full path. Image files show **real thumbnails**, and the File Manager doubles as the app's **file picker for every import** (WCP / ICP / wallpaper / drivers / assets) — reliable on OEM skins where Android's system picker fails, with the system picker still available as a secondary option. It also handles **multi-select bulk operations**, **archive extraction** (zip / 7z / tar / tar.gz / xz / bz2 / zst, Zip-Slip-guarded with progress and cancel), and **search / sort / hidden-file toggle / free-space** display.
- **Performance HUD** — FPS, frame time, CPU/GPU temperature, and RAM, in vertical or horizontal layout, with its on-screen **position saved per game**. Four switchable overlay styles — **Classic**, **GameHub**, a **GameNative-style** HUD with live graphs, and the new **Fusion HUD** (MangoHud-style: size modes Full / Tiles / Pill / Minimal / Mega, VRAM, GPU model, 1% / 0.1% lows and a frametime graph) — with **long-press to lock any HUD in place**.
- **FEX runtime indicator** — a live badge in the Graphics tab shows what's actually translating the running game: **arm64ec** vs **x86-64**, the translator (**FEXCore / wowbox64 / Box64**), and — for FEXCore — whether the native **unixlib (`.so`)** or the classic **DLL** path is active. Read straight from the running process, so it reflects reality, not just the setting.

</details>

<details>
<summary><b>📥 Builds & distribution</b></summary>

- **Three build flavors** with distinct package IDs — *standard*, *PuBG*, and *Ludashi*.
- **Optimized release builds** (not debug) for a smoother Compose UI, AOSP-testkey signed so updates install over previous installs.
- **In-app updater** — **Settings → Updates** checks GitHub for a newer release and installs the correct **flavor APK** for you, with **notify-on-update** and an opt-in **include-prereleases** (beta channel) toggle.
- Continuous **GitHub Actions** action builds and tagged stable releases.

</details>

---

## 🎨 Adding your own ReShade effects

<details>
<summary><b>Show / hide</b></summary>

Besides the built-in download catalog, you can add **any** ReShade effect yourself by dropping it into a folder. Follow these steps exactly:

**1. Open the ReShade drop-in folder on your device** (create the `ReShade` folder if it isn't there yet):

```
Android/data/com.winlator.banner/files/ReShade/
```

> 📁 That path is for the **Standard** build. For the other builds, swap the package name: **PuBG** → `Android/data/com.tencent.ig/files/ReShade/` · **Ludashi** → `Android/data/com.ludashi.benchmark/files/ReShade/`.

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

## 🎮 Frontends Workaround

<details>
<summary><b>Show / hide</b></summary>

Bannerlator does not work by itself on frontends out of the box. See the [frontends workaround guide](https://github.com/The412Banner/Bannerlator/blob/main/marcescence-frontends.md) to get it running.

</details>

---

## 🛠️ Building

This project is built via **GitHub Actions only** — local builds are not supported.

- **Action builds** — every fix is compiled and published as a downloadable workflow artifact.
- **Releases** — tagged stable builds are published as GitHub Releases.

---

## 🤖 Ask Me Anything

<details>
<summary><b>Show / hide</b></summary>

Got a question about Bannerlator? **Ask the codebase directly.** An AI reads the
actual source code and answers with the exact file names and line numbers, so you
can check it yourself. It never guesses — if the answer isn't in the code, it says so.

<p align="center">
  <a href="https://github.com/The412Banner/Bannerlator/issues/new">
    <img src="https://img.shields.io/badge/💬%20Ask%20a%20Question-Open%20an%20issue-7b2ff7?style=for-the-badge&logo=claude&logoColor=white" alt="Ask a Question">
  </a>
</p>

**It's three steps:**

1. **[Open an issue](https://github.com/The412Banner/Bannerlator/issues/new)** (you'll need a free GitHub account).
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

## 🙏 Credits

<details>
<summary><b>Show / hide</b></summary>

This build stands on a long chain of prior work — its direct lineage, plus the projects whose commits and work are cherry-picked and implemented here:

| Contributor | Contribution |
|---|---|
| **brunodev85** | Original [Winlator](https://github.com/brunodev85/winlator) — Wine + Box64 + Turnip on Android. Foundation of every fork below. Also serves the `input_controls` profiles consumed by this fork: <https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/> |
| **coffincolors** | [`cmod` Winlator fork](https://github.com/coffincolors/winlator) — package `com.winlator.cmod` and the customization layer this codebase is built on. |
| **Pipetto-crypto** | [Winlator Bionic fork](https://github.com/Pipetto-crypto/winlator) (the "Bionic" half of *Star Bionic*) and the upstream [Box64 fix branch](https://github.com/Pipetto-crypto/box64). Co-credited on cmod. Also packaged **vkBasalt** into the Winlator shortcut pipeline — the integration Bannerlator's **ReShade** feature builds on. |
| **jacojayy** | Maintainer of the [Star](https://github.com/jacojayy/star) line. Timeline Semaphore patches in the bundled Turnip driver for newer DXVK compatibility. Official site developer and maintainer. |
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

## ⚖️ Disclaimer

Winlator and its forks are unofficial community projects. They are **not** affiliated with or endorsed by Microsoft, Wine, the Mesa project, Qualcomm, **Valve/Steam, Epic Games, GOG, Amazon**, or any game publisher. The built-in store sign-ins are a third-party login system for libraries **you already own** — see [Security Hardening & your store accounts](#-security-hardening--your-store-accounts), and **use them at your own risk**. Compatibility varies by device GPU, Android version, and individual game.

---

## 📄 License

**Bannerlator is licensed under [GPL-3.0](LICENSE) as a whole**, because it incorporates GPL-3.0-licensed components (notably **GameNative** and **lsfg-vk**), whose copyleft governs the combined distribution.

The upstream **Winlator → cmod → Bionic → Star → Ludashi** lineage it builds on is **MIT © 2023 BrunoSX** (permissive, and GPL-3.0-compatible). That MIT notice — and the license and copyright of *every* incorporated component (GameNative, lsfg-vk, gbe_fork/Goldberg, vkBasalt, bcn_layer, JavaSteam, Wine/Box64/Mesa/DXVK, FSR/NIS/SGSR, ReShade shaders, …) — is preserved in **[`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md)**.

See [`LICENSE`](LICENSE) for the full GPL-3.0 text and [`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md) for all third-party attributions.
