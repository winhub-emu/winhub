# 📜 WinHub Changelog

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
