<img src="logo.png" width="1500" height="500" alt="WinHub" />  
</p>

<p align="center">
  <img src="https://img.shields.io/github/downloads/winhub-emu/winhub/total" alt="Total Downloads" width="150">
</p>

<h1 align="center"> ✨ WinHub -
Your one-stop place for all your games, now on Android.</h1>

**WinHub** is an open-source application that lets you play PC games on Android with the best performance possible. It lets you access your Steam, Amazon, GOG and Epic Games library on the go without any root or strange things. It also acts like a fully functional frontend, truly making it the one stop hub for all of your games.

> 📌 Please read [this](https://github.com/winhub-emu/winhub/releases/tag/star-winhub-progrwss) page.


**Informations:**
- **Package:** `com.winhub.dev` (standard), `com.tencent.ig` (pubg), `com.ludashi.benchmark` (ludashi)

- **Version:** `codename-ivoler`

- **Android SDK:** `compileSdk 34`, `targetSdk 28`, `minSdk 26` (Android 8.0+)

- **Upstream lineage:** Winlator → cmod → Bionic Nightly → star Marcescence → WinHub

---

## Links 🔗

- [Discord server](https://discord.gg/KpwUbc2cby)
    
- [Telegram](https://t.me/winhubemul)

---

## Building 🛠️

This project is built via **GitHub Actions only**. Local builds are not supported.


Artifacts are published as workflow artifacts; tagged stable builds are also published as GitHub Releases.

---

## Credits 📃

This fork stands on a long chain of prior work. Credit, in lineage order:

| Contributor | Contribution |
|---|---|
| **brunodev85** | Original [Winlator](https://github.com/brunodev85/winlator) — Wine + Box64 + Turnip on Android. Foundation of every fork below. Also serves the `input_controls` profiles consumed by this fork: <https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/> |
| **coffincolors** | [`cmod` Winlator fork](https://github.com/coffincolors/winlator) — package `com.winlator.cmod` and the customization layer this codebase is built on. |
| **Pipetto-crypto** | [Winlator Bionic fork](https://github.com/Pipetto-crypto/winlator) (the "Bionic" half of *Star Bionic*) and the upstream [Box64 fix branch](https://github.com/Pipetto-crypto/box64). Co-credited on cmod. |
| **jacojayy** | Maintainer of the [WinHub](https://github.com/winhub-emul/winhub) line. Timeline Semaphore patches in the bundled Turnip driver for newer DXVK compatibility, ingame drawer redesign, file manager, imagefs and proton recompression, fps counter. Official site developer and mantainer. |
| **vivsi** | Controller support contributions. |
| **StevenMX** | [Winlator-Ludashi](https://github.com/StevenMXZ/Winlator-Ludashi) and extensive cherry-picked work implemented in this build. This includes the Vulkan rendering path - which was still unreleased and unfinished at the time these builds and this repo were created, along with various other cherry-picked commits. |

### Upstream stack

The Wine/translation stack this app bundles or downloads:

- **Wine** — [WineHQ](https://www.winehq.org/)
- **Box64 / Box86** — [ptitSeb](https://github.com/ptitSeb)
- **FEXCore** — [FEX-Emu](https://github.com/FEX-Emu)
- **DXVK** — [doitsujin / Philip Rebohle](https://github.com/doitsujin)
- **DXVK-GPLAsync patch** — [Ph42oN](https://gitlab.com/Ph42oN)
- **DXVK-Sarek** — [pythonlover02](https://github.com/pythonlover02)
- **VKD3D-Proton** — [Hans-Kristian Arntzen](https://github.com/HansKristian-Work)
- **Turnip / Mesa** — [Freedreno team @ Mesa](https://gitlab.freedesktop.org/mesa/mesa)
- **Proton layers (bionic)** — [GameNative](https://github.com/utkarshdalal/GameNative)

If you have contributed and are not listed, open a PR — this list is intended to be complete.

---

## Disclaimer ⚠️

Winlator and its forks are unofficial community projects. They are not affiliated with or endorsed by Microsoft, Wine, the Mesa project, Qualcomm, or any game publisher. Compatibility varies by device GPU, Android version, and individual game.

---

## License ⚖️

Inherits the license of the upstream Winlator project (GPL-3.0). See `LICENSE` for the full text.
