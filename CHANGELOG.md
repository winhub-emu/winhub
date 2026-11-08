# 📜 WinHub Changelog

## ✨ Version 1.2

> Everything new since 1.1. **15 new Deck themes**, a **Deck-style Controls tab**, **OpenGL and Mods in the XMB game settings**, a rebuilt **fake-HDR look**, a tidier **Storage** tab, and **Unreal Engine HDR is gone**. 🚀

**Jump to:** [🎨 New themes](#-15-new-deck-themes) · [🎮 Controls tab](#-deck-style-controls-tab) · [🕹️ XMB settings](#️-xmb-game-settings-opengl-and-mods) · [🌈 HDR](#-a-better-fake-hdr-look) · [🗄️ Storage](#️-storage-tab) · [🛒 Stores & Home](#-stores-and-home) · [🔄 Updates](#-update-check) · [🗑️ Removed](#️-removed-unreal-engine-hdr) · [🧱 Behind the scenes](#-behind-the-scenes)

---

### 🎨 15 new Deck themes

The theme picker grows from **7 to 22**. Every new theme is dark, recolours the whole app (popups and touch controls included) and shows up in **Deck Settings → Theme**, **Appearance** and the **first-run wizard**.

| | Theme | Accent | Look |
|---|---|---|---|
| 🌊 | **Ocean** | teal `#14B8A6` | deep navy-teal surfaces |
| 🟡 | **Amber** | gold `#F59E0B` | warm brown-black, a "retro handheld" feel |
| 🌇 | **Sunset** | rose `#FB7185` + violet | dusky plum surfaces, violet on toggles and chips |
| ❄️ | **Nord** | frost blue `#88C0D0` | muted blue-grey, low contrast and easy on the eyes |
| 🌌 | **Midnight** | indigo `#6366F1` | pure-black OLED background, very dark cards |
| 🎄 | **Christmas** | red `#E11D48` + green | festive pair, in the style of Halloween |
| 🧛 | **Dracula** | purple `#BD93F9` + pink | the classic dark purple-grey |
| 🍂 | **Gruvbox** | orange `#FE8019` | warm retro browns with cream text |
| 🌅 | **Solarized** | cyan `#2AA198` + yellow | dark teal base |
| 🏙️ | **Tokyo Night** | blue `#7AA2F7` | cool navy surfaces |
| 🐈 | **Catppuccin** | mauve `#CBA6F7` | soft pastel on a mocha base |
| 🌹 | **Rose Pine** | rose `#EBBCBA` + lilac | dusky muted pastels |
| 🤖 | **Cyberpunk** | neon yellow `#FCEE0A` + cyan | black surfaces |
| 🎛️ | **Synthwave** | hot pink `#FF2E97` + cyan | deep violet surfaces |
| 🌲 | **Forest** | moss `#84CC16` | dark olive-brown, different from the mint Green |

- 🔤 Colours from Dracula, Gruvbox, Solarized, Nord, Tokyo Night, Catppuccin and Rose Pine are *inspired by* those well-known editor palettes.
- 👁️ Text on every accent is picked for readability (dark ink on Amber, Ocean, Nord, Gruvbox and the other light accents, white on the rest).
- 💾 **Your theme is kept.** The new themes were added before the hidden Custom slot, and a one-time remap makes sure anyone on **Custom** stays on Custom.

---

### 🎮 Deck-style Controls tab

The Controls tab now looks and navigates like the rest of Deck mode instead of Material cards.

- 🧱 **New layout:** a header, a rail (chips in portrait), a **Profile** group and Deck rows and buttons replace the Material cards and segmented tabs.
- 🔁 **One index:** sections are driven by the Deck tab host, so **L1 / R1** and the rail always agree on which section is open.
- 💬 **Deck dialogs and menus:** name prompts, confirmations, the import chooser, the profile download list and the layout preview all use the Deck dialog and Deck buttons. Profile and copy menus use the Deck colours.
- 👥 **Assign tab:** each player slot is now a **Deck choice row**. The container and shortcut editors keep their existing slot editor unchanged.
- 🖼️ A design mockup for the tab was added under `docs/`.

---

### 🕹️ XMB game settings: OpenGL and Mods

Two things that existed in the pop-up and Deck game editors were missing from the XMB (the PSP-style game menu, landscape and portrait). They are there now: pick a game, then **Settings**.

**Graphics → OpenGL** ⚡
- ⚡ **Fast OpenGL**, 📊 **Show OpenGL FPS** and 🔓 **OpenGL vsync off**, each **Auto / On / Off** where Auto follows the container (and says what it would be, e.g. *Auto (On)*).
- 🔒 Fast OpenGL greys out, with the reason, when the game's driver isn't a Turnip or this build has no X11 GL set.

**Mods** 🧩 (a new section after Controller)
- 🧩 **Game mods:** a switch for each mod the game's EXE can take (**REFramework** for RE Engine games, **Special K** for 32 and 64-bit Windows games). Switching one on downloads it the first time, with its state shown (*Downloading…*, *ready*, *download failed*).
- 🏷️ **Special K DLL name:** pick the name it loads as, or leave it on *(auto)*.
- ▶️ **Start with the game:** add programs by Windows path (a trainer, for example), then open one to change its path or **wait before starting**, move it **up or down**, or remove it. The first waits from the game launching, each next one after the one before it starts.
- ✅ Everything is saved to the game's settings straight away, the same way the pop-up editor does it. Section list: General · Graphics · Win Components · Env Vars · Advanced · Controller · **Mods** · TV.

---

### 🌈 A better fake-HDR look

The in-game **HDR** toggle is still the same one-tap on/off, but the effect behind it is rewritten.

- ☀️ **Highlights only.** It works in linear light, lifts just the bright areas and adds a soft highlight-only bloom, instead of a gamma-space curve that clipped highlights and muddied colours.
- 🎞️ **Soft shoulder.** Everything rolls off smoothly under SDR white, so nothing clips.
- 🎨 **Depth and colour.** Mids and darks get a touch more depth and a small saturation boost, kept back in bright areas so they don't over-saturate.
- 📏 **Same on any screen:** the glow is sized in pixels scaled to the picture, so it looks the same at any resolution.
- 🛡️ **Safe by design:** it is still SDR in and SDR out. The display never switches mode and nothing about the swapchain changes. It is a nicer "HDR look", not real HDR, so it can't go brighter than SDR white.

---

### 🗄️ Storage tab

- 🍷 **Container icon.** Containers in **Storage** and in the **add-game container picker** now show the same wine-glass container icon as the Containers tab, tinted with your Deck theme colour so it follows every theme.
- 🧬 **Cronos containers stay in the Cronos tab.** Containers made by the Cronos add-game flow are now tagged and listed **only** in the **Cronos** tab. They no longer appear in the **Containers** tab or in the **Master Containers** pickers. Older single-game containers are tagged automatically the first time you open Storage.
- 🎯 **Per-button focus rings.** On Storage and Saves cards, the ring now lights up the exact button you are on instead of the whole card, so a D-pad always shows where you are.

---

### 🛒 Stores and Home

- 🔥 **Featured on Steam.** A new row on the **Stores** page (landscape picker and portrait list) shows Steam's featured titles. Tap one to open its store page. Nothing shows until the catalog answers, so there is never an empty box.
- 👋 **Home greeting.** Portrait Home now says **Good morning / Good afternoon / Good evening** in place of "WinHub".
- 🎮 **Stores L2 / R2.** Stores reports its pages to the Deck shell, so **L2 / R2** page through it without relying on Compose focus.

---

### 🔄 Update check

- ⏱️ The update check on app open no longer hashes the installed APK, which made it time out and wrongly report "no update".
- 🥇 It now runs **first**, before the background step, and always tells you the result: **Update available**, **No updates found** or **Couldn't check for updates**.

---

### 🗑️ Removed: Unreal Engine HDR

The *Unreal Engine HDR* setting (Off / DirectX 12 fix / DirectX 11 NVAPI) is gone from the project.

- ❌ Removed from the game settings, the XMB and the launch path, along with the bundled **dxvk-nvapi** and its download step in the build.
- 🧹 Old `unrealHdr` entries in saved containers and shortcuts are simply ignored.
- ℹ️ A prefix that ever ran the DirectX 11 mode keeps its two nvapi files. Nothing loads them any more, so they are harmless.

---

### 🧱 Behind the scenes

- 🔢 **Version 1.2, versionCode 92.**
- 🧪 Fake-HDR shader (`hdr.frag`) rewritten and its SPIR-V (`hdr_frag.h`) regenerated.
- 🎨 15 presets added to `ThemePreset.kt`; a `preset_schema_v4` migration keeps Custom pointing at the right slot.
- 🧩 New `XmbSettingsPhase2Mods.kt` for the XMB Mods section; the delay and program-name helpers of the Start programs editor are shared with it.
- 🧰 Removed `core/UnrealHdr`, `core/DxvkNvapi`, `GpuSpoof.isNvidia` and the `dxvk-nvapi` assets and CI fetch.
- 📚 The README now links to the **Telegram** and **Discord** communities.

---

## ✨ Version 1.1

> Everything new since 1.0. A **first-run setup wizard** for fresh installs, plus a fully **Deck-style Log Manager, log viewer and Report a problem dialog**. 🚀

**Jump to:** [🧭 Setup wizard](#-first-run-setup-wizard) · [🗂️ Log Manager](#️-deck-style-log-manager) · [🔍 Log viewer](#-deck-style-log-viewer) · [📝 Report a problem](#-report-a-problem) · [🧱 Behind the scenes](#-behind-the-scenes-1)

---

### 🧭 First-run setup wizard

A fresh install no longer starts downloading straight away. WinHub first walks you through a five-step wizard, then installs everything behind the usual setup overlay.

- 🪜 **Five steps, one progress bar:** Layout → Theme → Contents → Social Hub → Install, with a "STEP n OF 5" header and a step strip so you always know where you are.
- 📐 **Layout.** *How do you play?* Choose **Landscape** (the Deck home with its rail) or **Portrait** (one hand on a phone: chips replace the rail, big touch targets). A live preview shows the Deck home in the layout you pick. Change it any time in **Settings → App Orientation**.
- 🎨 **Theme.** *Pick your look.* The whole app, including the wizard itself, follows the theme as you try them. Change it any time in Settings.
- 📦 **Contents.** The same browser as the Contents tab, with a checkbox per release, search, and family → repository → release navigation. **Proton 9 arm64ec, DXVK 2.4.1 gplasync, FEXCore 2610 PPA unix and VKD3D 3.0.1 are always installed** and carry an **Included** badge. Tick anything else you want now, or add it later from Contents.
- 💬 **Social Hub.** *Connect to Social Hub* with GitHub (device code: open the link on any device and enter the code). Shows *Connected as @you* when done, or **Not now** to skip and connect later from the Social tab. It explains what you get: import configs in one tap, share your own, and join in with posts, replies and votes. Sign-in problems show a message and a **Try again**.
- ⬇️ **Install.** An **Install plan** lists every item (system files, the four included components and your extras), the item count, and a reminder that it needs an internet connection and Wi-Fi is recommended.
- 📊 **Setup overlay.** The install list now shows each chosen extra as its own row, and scrolls so a long list never pushes the button off screen. The progress is split into equal slices, one for the system files and one per component.
- 🎮 **Controller first.** **A** selects, **B** goes back a step, **LB / RB** switch steps, with on-screen button hints in the footer. Back and Next / Install buttons work by touch too.
- 🔁 **Only on a fresh install.** Updates that only need a newer ImageFS skip the wizard, and it only runs once.
- 🛠️ **Fix: Deck follows your layout.** Deck reads the orientation preference once when it starts, and it used to start behind the wizard, so picking Portrait left it on the landscape layout rotated upright. The shell is now held back until the wizard finishes, and the shown orientation is saved even if you never tap a choice.
- 🔒 The permission prompts wait until you tap **Proceed**, so nothing interrupts the wizard.

---

### 🗂️ Deck-style Log Manager

In Deck mode, Settings now opens a themed Log Manager instead of the classic dialog. It uses the same preferences and log inventory as before, so nothing you set is lost.

- 💾 **Storage meter** at the top showing how much space logs use, and the folder logs are saved to.
- 🔀 **Four sections:** **Capture**, **Storage**, **Game logs** and **Tools**. Switch with **LB / RB**.
- 🎙️ **Capture.** Switches for Wine debug (with a channel picker and *Browse all channels*), DXVK and VKD3D logs, **Controller input**, SteamLite, **Android logcat**, **Crash reports** and **Exit reasons** (auto-save on launch, Android 11+). Each has a short plain-language hint about what it costs, and an **ⓘ** that explains it.
- 📁 **Where logs go.** Log location (default, custom folder or *Choose folder…*), **Folder for each game**, and **Keep last** (how many runs to keep, or no history).
- 🧹 **Storage.** Total size, **Clear all** (with a confirmation that only files WinHub wrote are removed) and a **Biggest folders** list, with a delete confirmation for the app and crash logs.
- 🎮 **Game logs.** Every game's logs in one place, with a helpful empty state: *Turn on a log in Capture, then launch a game.*
- 🧰 **Tools.** **Capture logcat now**, **Read last crash** (why Android ended the app last time, saved with your logs), the **Native engines** switches (Rust engine for Epic, GOG and Amazon downloads), a **Store download speed** ceiling, and **Explain the log types**.
- 🎮 **Controller friendly** with focus rings on every row and action.

---

### 🔍 Deck-style log viewer

- 🎨 Opens in the **Deck palette** from the Deck Log Manager, with 44dp actions and clear focus rings.
- 🔎 **Find**, **Copy** and **Share** actions along the bottom, plus a follow switch for live logs.
- 🔗 A shortcut's **View logs** opens the same Deck viewer when you are in Deck mode.
- 🧱 The classic Log Manager and viewer are unchanged when Deck mode is off, and the shortcut editor still uses the classic look.

---

### 📝 Report a problem

- 🪟 Now a **Deck dialog with controller-editable fields**: *What went wrong?* and *Details* (optional).
- 📎 A **Will be attached** list shows exactly which logs go into the report, with a toggle to also include the app logcat and crash reports.
- 🔐 The report is saved as a **zip in Downloads**. E-mail addresses, auth tokens and your Steam account name are stripped; file paths are kept so they stay useful, so glance over them if a folder name identifies you.
- 🌐 **Continue on GitHub** opens a prefilled issue. GitHub can't take a file from a link, so attach the zip with the paperclip once the form opens. If there is no browser, you get a clear message instead of a crash.
- ⚡ Building the report runs off the main thread, with a *Working…* state on the button.

---

### 🧱 Behind the scenes

- ⏱️ The release workflow now **times out the patches job after 15 minutes**, so a hung flavor cannot hold up a release. A flavor that times out just ships full-size updates without `.wh` patches.
- 🧩 The Contents browser pieces (category cards, badges, icons) are shared between the Contents tab and the wizard, instead of copied.
- 📚 Release notes use the WinHub logo, and the default repo for release notes is `winhub-emu/winhub`.

---


## 🏆 Version 1.0

> Everything new since 0.1-beta. The big ones: a brand new **controller engine (Hermes)**, a rebuilt **Fusion HUD**, **Fast OpenGL**, a **Sync selector**, a **Music Player**, and a lot of portrait and Deck polish. 🚀

**Jump to:** [🎮 Controllers](#-controllers-hermes) · [📊 Fusion HUD](#-fusion-hud) · [🖼️ Graphics](#️-graphics-and-opengl) · [⚙️ Containers](#️-containers-and-games) · [🎵 Music Player](#-music-player) · [📱 Portrait](#-portrait-mode) · [🪟 Deck UI](#-deck-ui-polish) · [🧱 Behind the scenes](#-behind-the-scenes-2) · [🗑️ Removed in 1.0](#️-removed-in-10)

---

### 🎮 Controllers (Hermes)

A new way to get your controller into the game. It replaces the old shared-memory ring.

- 🪽 **Hermes controller transport.** Each player slot gets one fixed-size shared state file. The game side compares it to the last read and turns the changes into real evdev events.
- ⚡ **No lost presses.** A button pressed and released between two reads is still delivered, both the press and the release.
- 🔌 **Real hot-plug.** Plugging or unplugging a controller is seen as a real hot-plug. Slot nodes only exist while a controller holds the slot, and readers reattach by themselves.
- 🔄 **Live reconnect** of held slots, and the **on-screen pad is seated before Wine boots**, so it is ready from the first frame.
- 🆔 **Per-slot controller identity**, so each player shows up as its own device.
- 📳 **DirectInput force feedback** is forwarded to a new force-feedback engine.
- 🧩 **Direct XInput.** Replacement `xinput` DLLs (x86_64, i386 and ARM64X) are built per container, with **in-game switching** and **rumble** over the existing UDP path.
- 🕐 **Stick updates paced to the game's frame rate**: *Auto*, *Fixed* or *Unpaced*.
- 🎛️ **New controller settings:** controller type/identity, DirectInput visibility, DirectInput vibration and stick updates.
- 🧪 **Controls → Pad sub-tab** in the in-game menu shows your controller live.
- 📝 **Log Manager** has a new **Controller input** log (`controller.txt`), and community configs carry the new keys.
- 🆕 **New containers start with Direct XInput on.** Existing containers keep their own setting, and a saved new-container defaults profile keeps its choice when it stored one.
- 🧭 **Same settings everywhere.** The Deck container editor, the classic and Deck per-game editors and the XMB **Controller** menu all show them. In a game each one reads *Use container default* until you pick your own.
- 🔀 **One simple switch.** *Enable XInput*, *Enable DInput* and *Exclusive Input* became a single **"DirectInput games can see the controller"** switch in the container editors, the per-game editors and the XMB menu. It is **off for new containers** (unless your saved defaults say otherwise), and a game can follow the container or choose its own. It stores the same `inputType` / `exclusiveXInput` values, so **old data reads exactly the same**.

---

### 📊 Fusion HUD

The performance overlay was rebuilt and is now the only HUD.

- 🧱 **Four Steam Deck style levels:**
  1. **FPS** only
  2. Adds the **frametime graph**
  3. Adds **GPU, CPU and memory**
  4. **Everything**
- 🎨 **MangoHud colours**, a big FPS number that turns **amber and red** as it drops, **per-core bars** and dividers.
- 📐 **Layout follows the mock-up:** right-aligned groups, dim regular-weight units, a grey hairline border, a 62% panel and a **clock on the level 4 TIME row**.
- 🔁 **Old sizes still work.** Minimal, pill, tiles, full and mega map to the nearest level.
- 🆕 **New containers start at level 2.**
- ⏱️ **Present latency and skipped frames.** While SurfaceDirect runs, level 4 shows a **PRES** row: how long SurfaceFlinger takes to latch each frame, and how many of the game's frames are replaced before they are shown. On older Android, where the latch-time call is missing, there is just no latency sample.

---

### 🖼️ Graphics and OpenGL

- 🚀 **Fast OpenGL.** OpenGL games now present through the bundled **Mesa + Zink** over a Vulkan swapchain, instead of XMesa's readback on every frame.
  - 🧵 Layers that use Wine's **EGL** backend take the EGL route.
  - 🧰 The others take **GLX** through a new **opt-in server-side GLX** (GLX and Composite extensions, DRI3/Present 1.2).
  - 🔧 `WINHUB_X11_EGL` and `WINHUB_X11_GLX` override the choice.
- 🔢 **Show FPS** (`GALLIUM_HUD=fps`) and **Vsync off** (`vblank_mode=0`) sit beside it.
- 🎚️ **Where to find it:** the **OpenGL card** in the classic and Deck container editors (switches) and in the classic and Deck shortcut editors (**Auto / On / Off** per game).
- 📤 Community-config export now carries the three OpenGL settings.
- 🛠️ Fixed a compile error in the GLX extension (a missing `XOutputStream.writeIntPair`).

---

### ⚙️ Containers and games

- ⏲️ **Sync option: esync / ntsync / fsync / wineserver.**
  - 📍 Sits under the Wine version in both container editors and in the per-game editors (a game follows the container unless it overrides it).
  - ✍️ At launch it is the **only writer** of `WINEESYNC` / `WINENTSYNC`.
  - 🚫 Options the Wine layer cannot run are **greyed out** and fall back to the layer default.
  - 🧹 Old env-string choices are read as the starting pick and dropped from the Env Vars tab when you save.

---

### 🎵 Music Player

- 🎶 **Settings → General → Music Player.** Choose an MP3 (it is copied into app storage) and switch it on or off.
- 🎮 A separate **"Play in games"** switch, **off by default**, lets the track play inside games too.
- 🔁 It loops, **pauses when the app leaves the screen**, and **takes no audio focus**, so it never interrupts other apps.

---

### 📱 Portrait mode

- 🌊 **Liquid waves on the basic tabs.** Storage, Files, Controls, Contents, Saves, Social Hub, Settings and Stores now show the animated wave backdrop like Home.
- ✨ **Drifting glows stay on Home.** The other tabs get only the waves.
- 🫥 **Saves is transparent** in portrait so the waves show through. Stores joined the wave tabs.
- 🏷️ **Store logo or folder badge on each game cover**, so you see where a game comes from at a glance.
- 🌫️ **Content fades out above the bottom bar** instead of a hard cut.
- 🚫 **No waves behind the per-game settings editor.** It is a full page of its own, and the waves used to show through under it.

---

### 🪟 Deck UI polish

- 🃏 **Classic game menu is a centered rounded card**, not a full-screen takeover.
- 💬 **Install popup and small confirm/menu dialogs** in Contents are now a **compact centered card**, not near-fullscreen.
- ✏️ **Text fields get a full rounded outline** with centered text.
- ↔️ **Text rows and Env Var value fields get symmetric margins**, and text stays left-aligned.

---

### 🧱 Behind the scenes

- 🦀 **Rust workspace for Hermes:** the core protocol and writer engine, a guest static library linked into `libfakeinput.so` (Wine and the Linux runtime), the JNI writer `libhermes.so`, the force-feedback engine and a loom model check.
- 🔁 `fakeinput.cpp` / `fakeinput_steam.cpp` now read the slot files.
- 🏗️ **CI:** the APK build compiles the transport library and the XInput DLLs. The `hermes-check` and `hermes-xinput` workflows (host tests, model check, Wine end-to-end) are **manual only**, because they are slow. Executable bits on the build and test scripts were restored.
- 🧹 Removed the AMA answer workflow, and Python bytecode is now ignored.
- 📖 **Docs:** README rewritten for WinHub, release pages open with the centered WinHub logo, the project lineage is clarified, and the frontends guide uses the WinHub name and package.
- 🔧 Fixed a missing brace in `seatOnScreenPadBeforeBoot` that broke `javac`.

---

### 🔼 Upgrading from 0.1

- 📦 **Existing containers keep their own controller settings.** Only **new** containers start with Direct XInput on and the DirectInput switch off.
- 📊 A saved HUD style that no longer exists becomes **Fusion**, and old HUD sizes map to the nearest level.
- 🔀 Old input values (`inputType` / `exclusiveXInput`) read exactly as before.

---

### 🗑️ Removed in 1.0

- 🧽 The **Classic, GameHub and GameNative HUD styles**, their widgets, renderer hooks and settings. A saved HUD style of any other value now becomes **Fusion**.
- 🔀 The separate **Enable XInput / Enable DInput / Exclusive Input** switches, replaced by the single DirectInput switch (stored values unchanged).
- 📨 The old **mmap ring** controller transport, replaced by Hermes.

---

## 🚀 Version 0.1-beta

> This page covers everything new in this release, grouped by what you will actually notice. 🚀

**Jump to:** [🎨 New UI](#-a-fully-revamped-ui) · [🖼️ Renderers](#️-new-renderers-egl-and-surfacedirect) · [💬 Social Hub & Game Configs](#-social-hub-and-game-configs) · [🟢 Mali](#-new-mali-support) · [🧩 Mod Loader](#-mod-loader) · [🔄 Updater](#-updater) · [🛠️ Other improvements](#️-other-improvements) · [🗑️ Removed](#️-removed) · [🙏 Credits](#-credits)

---

## 🎨 A fully revamped UI

WinHub now looks and feels like a console. Everything was rebuilt around a **Deck-style shell** that works fully with a gamepad **and** with touch, in landscape **and** in portrait.

### 🕹️ The Deck shell (landscape)
- 🏠 **Console-style Home.** A top-left carousel of your games with big glowing covers, a launch zoom when you press play, a blurred cover backdrop, a status strip, a details panel, an intro, and L2/R2 to switch shelves (**All, Recent, Favourites, Store games**).
- 🎛️ **Two Home styles** (Settings → Appearance): **XMB** (default) with the game options column, and **Classic**, which opens a full console-style game menu when you hold a cover or press **Y**.
- 🧭 **Hint bar, Start quick menu, download tray, idle screensaver and a sound set**, so the whole app reacts like a console.
- 🔁 **Shoulder buttons switch things the same way everywhere.** L1/R1 move between sections, L2/R2 move between sub-tabs, **A** selects, **B** goes back and never exits the app by accident.
- 🌊 **Stretchy tab pills and a gliding focus ring** that follows your D-pad across grids, lists, buttons and dialogs. Rings hide after you touch the screen.
- ⌨️ **A-to-edit text fields**, so a controller never gets stuck inside a keyboard field.
- 🪟 **Full-screen Deck dialogs** with controller navigation, plus warning popups for backup and restore.

### 📱 Portrait mode, built from scratch
- 🫧 **Liquid Home.** Soft glows drift behind the page and three layers of waves roll along the bottom. Colours follow your theme.
- 🔮 **Glass cards** for the Continue Playing banner, the Add game tile and shelf chips.
- 🎮 **Continue playing banner** that floats gently and jumps straight back into your last game.
- 💊 **Floating, rounded bottom bar** with every section as an icon and label tab.
- 👆 **Hold a game cover to open its menu**, with a little haptic tick. Tap it to open the game page (banner, stats, Play, Settings, Options, About).
- 🕘 **Home header** with the clock and your Social Hub avatar.
- 📚 Settings, Contents, the container editor and Storage use a **top Deck tab strip with icons**, or a category list if you prefer (Settings → Appearance → Portrait Tabs).
- 🏪 **Stores page** with each store's real logo and brand colours.

### 🎨 Themes
- 🌈 **Seven themes** (including a new blue **Azure**) with a live theme picker.
- 🧩 The in-game drawer, launch banner and portrait page all follow the active theme, light or dark.

### 🧰 Editors rebuilt for the Deck
- 📦 **Container editor** in three layouts (**tabs, side rail or category pages**), split into labelled sections, with inline config panels and a structured **Env Vars** editor.
- 🎯 **Shortcut (per-game) editor** rebuilt Deck-native: General, Graphics, Win Components, Env Vars, Advanced, Controller, **Mods**, plus TV when a display is connected. Wrapper Manager, Audio, Performance and download sheets are all Deck-native.
- 🧮 **Presets editable in Settings.** Box64 and FEXCore presets can now be added, edited, duplicated, deleted, exported and imported from Settings → General.
- ⚙️ **Custom startup services.** Choosing **Custom** under Startup Selection now shows a switch for every Wine service, in both the container and the shortcut editor.
- 🚀 **Launch method picker** (SteamLite, Goldberg, Raw) in the game menu and shortcut editor.

### 🛒 Stores
- 🏬 **Steam, Epic, GOG, Amazon and Xbox** all share the same Deck tab strip (Store, Library, Profile).
- 🟩 **Xbox store**, including an in-Deck install screen with a PC package picker, store tabs, downloads and a cloud-gaming Stream launch method.
- ⭐ Store pages show **Metacritic and How Long To Beat**, with richer game details and Steam achievements.
- 🏷️ Your library tags each game by store, and hand-added games are tagged **Custom**.

### 🕹️ In-game menu
- 🪟 **Console-style quick menu.** A right-docked glass panel with a header of quick tiles, a tab rail and a **Quick** landing page, fully controller-navigable.
- 🧩 New **Mods** tab in the drawer (see [Mod Loader](#-mod-loader)).
- 🫥 Transparent in-game menu controls, with option groups in one rounded outline.

### 🧼 First run and polish
- 🛠️ **WinHub setup screen** and a one-time **launch screen** that brings the store services online.
- 🖼️ New app icons.
- 🪄 **Launch banner** with an *Update available* message that never waits more than five seconds on the update check.
- 📂 **File manager and Saves** have location pills and a Deck look. Pickers use the Deck file manager in portrait or landscape.
- 🧠 Shared Wine base prefix through a copy-on-write overlay, so each container is only a thin skeleton on disk.

---

## 🖼️ New renderers: EGL and SurfaceDirect

A **renderer** is the part of WinHub that takes the game's window and puts it on your screen. WinHub now has three, and you can choose per container **and** per game:

### 🔷 Vulkan (unchanged)
The existing renderer. Draws windows with Vulkan and keeps all its current options.

### 🟦 EGL (new)
- 🧠 **What it does:** an **OpenGL ES** renderer. A native render thread mirrors the X server's windows, cursors and pointer and draws them with OpenGL ES.
- ⚡ **Why it's good:** window contents are stored in **AHardwareBuffer** memory and sampled **without copying**. DXVK and VKD3D games can hand their finished frames straight to it through the Present extension.
- 🎯 **Pick it when:** your GPU's Vulkan driver is unreliable for presenting, or you want the simplest, most compatible path.
- 🛟 **Safe fallback:** if the native side can't start, WinHub falls back to Vulkan by itself.
- ⚙️ **Settings:** none. EGL needs no tuning, so its renderer settings row is hidden.

### 🟪 SurfaceDirect (new)
- 🧠 **What it does:** hands each X window to **Android's SurfaceFlinger** as its own **SurfaceControl layer** that shows the game's frame buffer directly. Android's compositor does the final combine in hardware, so WinHub does **no extra GPU composition pass**.
- ⚡ **Why it's good:** the shortest route from the game's frame to the display, which means lower overhead and less latency on supported devices.
- 🎚️ **Settings:**
  - 🚀 **Performance mode**
  - 🔄 **Present at refresh rate**
  - 📤 **Submit every buffer**
  - 🎯 **Precise presentation**
- 🛟 **Safe fallback:** needs Android 10 or newer. On older versions WinHub falls back to Vulkan.

Each renderer now only shows **its own** settings in the Deck, classic and XMB editors, so you never see options that don't apply.

---

## 💬 Social Hub and Game Configs

The **Social Hub** is a built-in community space, now with two sub-tabs and your own profile. It runs on GitHub Issues in `winhub-emu/social-hub`, so there is nothing extra to install.

### 📰 Feed
- ✍️ Post, comment, search, refresh. A post is a GitHub issue and a comment is an issue comment.
- 🖼️ Add pictures, which are uploaded and embedded in the post.
- 🗑️ Deleting your own post hides it (GitHub has no true delete for non-owners).
- 🔐 Sign in with GitHub inside the app.

### 🎮 Game Configurations
Share the exact settings you use for a game, and import other people's in one tap.
- 📤 **Upload** a game's config from your library with an optional note. It carries the container and shortcut settings, **plus a record of which repository each component (driver, DXVK, VKD3D, wrapper...) came from**.
- 📥 **Import** a config in one tap. WinHub fetches each component **from that exact source**, asks you to pick the game's `.exe`, and sets the shortcut up.
- 🔍 **Search** configs by game name.
- 📱 **Matches my device** filter shows only configs from phones like yours (same SoC and GPU).
- 👍 **Like** and 💬 **reply** to config threads so the good ones rise to the top.
- 🧭 Works with the controller (L2/R2 or LB/RB switch sub-tabs) and follows your layout setting (rail, tabs or pages).

### 🌍 Community Configs
In a game's menu you can also browse the **community config index**. It's cached on the device, loads instantly after the first fetch, works offline, and falls back to a clean empty state instead of an error.

### 🙋 Profile
Pick a **custom display name and picture**. It's stored on a hidden profiles issue and shown across the feed and the Home header.

---

## 🟢 New Mali support

WinHub now has real support for **Arm Mali and Immortalis GPUs**, which many phones use. Everything below only turns on for Mali. **Adreno phones behave exactly as before.** 🧑‍🔧 *These features are new and Mali is hard to test everywhere, so please report what works and what doesn't.*

### 🔍 Detection and the force switch
- 🧭 **Force Mali mode** (Settings → General). Turn this on if the app doesn't detect a non-Adreno GPU. It makes every Mali option available on any GPU.

### 📥 Drivers
- 🧱 **PanVK driver sources.** On Mali, the driver download list shows three PanVK feeds first: **PanVK-kbase (Mali)**, **FristOneRR PanVK (Mali)** and **PanVK G615**. PanVK is the open-source Vulkan driver for Mali.
- 🧪 **Newer compat layer.** The Mali compat layer (used by the *Wrapper + compat + bcn* driver on Valhall Mali) is updated to leegao's `22960fb3`, adding an immutable-YCbCr fallback, fake `maintenance5` and calibrated-timestamps handling. Existing containers re-extract it once.

### 🖼️ Textures
- 🚀 **Faster BCn layer (Fcharan build).** An opt-in checkbox in the graphics driver dialog. It swaps the bundled BCn texture decoder for a faster build with the same memory use, which helps with slow texture loading and stutter. It's only offered where the BCn layer actually runs.

### 🎛️ PanVK tuning
In the graphics driver dialog, a **Mali (PanVK) driver** section:
- 🧮 **PANVK_HEAP_MB** (256–16384): the driver's heap size.
- 🧮 **PANVK_TILER_HEAP_MB** (16–2048) and **PANVK_POLY_HEAP_MB** (4–512): the tiler and polygon heaps.
- 🔢 **PANVK_ATOM_STRIDE** (Auto, 56 or 64).
- 🪵 **PANVK_TRACE**: driver tracing for bug reports.
- 🙈 **Hide present-wait / dynamic-rendering extensions.** Hides `present_id`, `present_wait` and `dynamic_rendering` from the game and turns on *Disable present wait*, for freezes and black screens on PanVK. Out-of-range values are simply not used.

### 📊 HUD
- 📈 **GPU usage and clock from hardware counters** for CSF-generation Mali (Mali-G310, G510, G610, G615, G710, G715, G720, G925 and Immortalis). It reads the kernel driver's counters and needs no root, and it's used only when the usual sysfs paths are blocked.
- 📟 **MediaTek GPU-load and Pixel/Tensor voltage fallbacks**, for phones that block the standard GPU nodes.

### 🖥️ X11 renderer
- ⏱️ **High-priority GPU queue** on Mali so WinHub's own frame work doesn't wait behind a GPU-bound game.
- 🧾 **Valid AHB import format.** The Android hardware-buffer image format is now correct for Mali drivers.

### 🧠 Emulation
- 🧬 Two new **FEXCore presets**: **Unreal Engine 3** (all TSO options on, reduced x87 precision, multiblock off) and **Performance (full x87)** for games whose physics break under reduced precision.
- 🛡️ **Registry fix.** A Wine registry key that can't be written no longer crashes the app.

> 💚 **Mali work by [The412Banner](https://github.com/The412Banner)**, built on WinlatorMali by GunaCharanTeja, leegao's compat layer and BCn layer, FristOneRR's PanVK driver and the PanVK-kbase project.

---

## 🧩 Mod Loader

A new per-game **mod loader** adds popular game mods without touching your game files by hand.

### 📚 Where to find it
- ⚙️ **Game settings → Mods** tab, with a switch for every mod your game's EXE can take. Mods are **off by default**.
- 🎮 **In-game drawer → Mods** tab, with a card for each mod and a button to show or hide its menu.

### 🦖 REFramework
- 🎯 The modding framework for **RE Engine games** (Resident Evil 2/3/4/7/8/9, Devil May Cry 5, Monster Hunter Rise/Wilds/Stories 3, Street Fighter 6, Dragon's Dogma 2, Onimusha, Pragmata and more). It only appears for those EXEs and for 64-bit.
- 🧱 An **ARM64EC build** that skips x86-64 inline hooks on ARM64EC code (fixing the "illegal instruction" crash), opens its menu with **L3 + R3** on a controller, works in games whose DirectInput blocks key messages (RE2), no longer hangs at startup, and keeps the game's cursor hidden while it draws its own, so you see one cursor.
- 📂 Placed as `dinput8.dll`, with `dinput8=n,b` merged into your DLL overrides (**your own entries are kept**). Its menu starts closed.
- ⌨️ Drawer menu button sends **Insert**.

### ✨ Special K
- 🔭 The popular overlay and tweak tool for almost any game. Works with 32-bit and 64-bit EXEs.
- 🏷️ Placed under the right graphics name: `dxgi.dll`, `d3d11.dll`, `d3d9.dll`, `d3d8.dll` or `opengl32.dll`, **picked automatically from the EXE's imports**, or chosen by you per game.
- 🤝 Works together with REFramework, and closing its panel no longer freezes the game.
- ⌨️ Drawer menu button sends **Ctrl + Shift + Backspace**.

### 🛡️ How it stays safe
- 🌐 Mods are **downloaded the first time you switch one on**, not bundled. This keeps the app smaller.
- ✅ Each download is checked against its **size and SHA-256** and cached once per device.
- 🏷️ A small **marker file** records exactly what was placed, so WinHub only ever replaces or removes **its own** file. A DLL your game already ships is **never touched**.
- 📋 Every step is written to the session log.

### 🚦 Start programs with the game (trainers, Cheat Engine)
- ➕ **Add any number of programs** to start when a game launches, such as a FLiNG trainer or Cheat Engine.
- ⏱️ **Set a wait for each one.** The first waits after the game launches. Each next one waits after the previous one starts. Pick from *Right away* to 5 minutes (for example, "launch, wait 5 s, launch the next").
- 🔀 **Arrange button.** Opens the whole list so you can reorder it. Pick a program up, tap where it should go (or press **Up/Down**), tap again to drop it. **Remove** takes the picked one out.
- ▶️ **Start button in the drawer** for each program, to run it again mid-game.
- 📁 Programs start **from their own folder**, using a small batch file, so long paths work.
- 🧵 **Long Task Manager commands work now.** Commands longer than the helper accepts (51 bytes) used to fail silently, and now go through the same batch-file route.

> 💜 **Mod Loader work by [The412Banner](https://github.com/The412Banner)**, with REFramework by praydog and Special K by Kaldaien and the SpecialKO contributors.

---

## 🔄 Updater

- 📡 **Updater tab in Settings** with a **Stable / Preview** channel switch.
- 🪶 **Small updates.** Instead of the full APK you can download a compact patch (`.wh`) built against your installed version.
- ⬇️ **Download and install button** in Settings → Updates when an update is available.
- 🏷️ Releases and their `update.json` are published from `winhub-emu/winhub`.
- 🔒 Downloads are verified by SHA-256 before installing.

---

## 🛠️ Other improvements

- ⚡ **Lower overhead.** The performance HUD only updates the overlay you can see, and the X server no longer polls `/proc` or busy-spins at launch.
- 🎮 **Controller reliability.** Focus lands on the first control after a tab switch, drag rings no longer stick or balloon, and the app reads the D-pad/stick hat axes properly.
- 🧩 **Per-game launch fixes.** A game in a Master container edits its own shortcut, the saved container is used at launch, and container save failures now show a message instead of failing silently.
- 🪄 **Frame generation fixes.** A per-game *Off* now inherits the container's LSFG setting properly, and picking LSFG in the Deck editor no longer saves as *Off*.
- 🧾 **Driver and DXVK versions** no longer revert to defaults when you save.
- 🪟 **Hide Vulkan-only options** on devices without Vulkan.
- 🗃️ **Storage page** shows sizes, with a Cronos/Containers split and remove actions.
- 🧰 **GitHub workflows** and repository links now point to `winhub-emu/winhub`.

---

## 🗑️ Removed

To keep WinHub focused and stable, these were retired:
- 🐧 The **Linux (gamescope) runtime**. Every session is now Wine on X11.
- 🌊 The **embedded Wayland compositor** and its capture paths.
- 🧱 Wayland and bionic-fg **from every selection list** (native LSFG frame generation stays).
- 🏛️ The **classic Material launcher UI**. WinHub is Deck-only now.

---

## 🙏 Credits

| Who | For |
|---|---|
| **[The412Banner](https://github.com/The412Banner)** 💚 | The **Mali support** and the **Mod Loader** features |
| **praydog** | [REFramework](https://github.com/praydog/REFramework) (MIT) |
| **Kaldaien and the SpecialKO contributors** | [Special K](https://github.com/SpecialKO/SpecialK) (GPL-3.0) |
| **GunaCharanTeja** | WinlatorMali, the source of the Mali HUD counters, the faster BCn build, FEX presets and the registry fix |
| **leegao** | The Mali compat layer and the BCn layer (MIT) |
| **FristOneRR** | The PanVK driver for Mali |
| **brunodev85, coffincolors, Pipetto-crypto, the Star and Frost teams** | The Winlator lineage WinHub stands on |

Thank you to everyone who tested, reported bugs and shared configs. 💙
