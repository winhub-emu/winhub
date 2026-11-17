# Third-Party Notices

This file lists third-party components that are **not** covered by this project's GPL-3.0
license and are used under their own terms.

## Samsung Galaxy Performance SDK

The optional **Galaxy Performance** feature (`com.winlator.star.perf.galaxy.*`) integrates
Samsung's **Galaxy Performance SDK** (`com.samsung.sdk.sperf`, `perfsdk-v1.0.0.jar`) to control
CPU / GPU / RAM-bus performance levels on Samsung Galaxy devices.

- The SDK is **proprietary to Samsung Electronics** and is **NOT** covered by this project's
  GPL-3.0 license. It is used under the terms of Samsung's SDK License Agreement.
- The SDK jar is **not distributed with this repository**. It is git-ignored and must be
  supplied at build time by the person building the app (see
  `app/libs/GalaxyPerf/README.md`). The application references the SDK only through reflection
  and functions normally, with the feature dormant, when the jar is absent.
- Source: https://developer.samsung.com/galaxy-performance
- If you redistribute a build that bundles this SDK, verify your right to redistribute it under
  the license you accepted.

## Console illustrations (retro library)

The retro library's console pictures (`ic_sys_*` in `app/src/main/res/drawable*`) are KyleBing's
**retro-game-console-icons** (GPL-3.0, <https://github.com/KyleBing/retro-game-console-icons>), plus
flat icons in the same style for consoles that pack lacks. They are GPL-3.0 like this project.
