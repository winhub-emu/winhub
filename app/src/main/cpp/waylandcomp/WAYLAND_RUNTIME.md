# Bannerlator Wayland runtime (feat/wayland-runtime)

Experimental **parallel** display runtime: run/launch games through Wine's
`winewayland.drv` talking to our own embedded Wayland compositor, instead of the
X11 path (pure-Java X11 server + `libwinlator.so`). The X11 runtime stays the
default and is untouched — this is a separate flavor/branch.

## What's already proven (spike repo `bannerlator-wayland`, device-tested on Adreno 750)
- Minimal libwayland-server compositor: globals + xdg-shell handshake + buffer commit.
- **Turnip's Vulkan WSI exports real zero-copy dmabufs to our external compositor**
  (same Mesa path winewayland.drv uses for DXVK/VKD3D) — risk #1 retired.
- Compositor imports that dmabuf into its own Turnip VkImage (`vk_import.c`).
The staged `src/` here is that proven code, to grow into the app-embedded compositor.

## Dependencies
1. **A Proton 11 arm64ec wcp that ships `winewayland.drv`** — built on branch
   `The412Banner/proton-wine:feat/winewayland` (task #1). Nothing runs end-to-end
   without it.
2. **Wayland runtime libs in the imagefs** so `winewayland.so` (unixlib) loads:
   `libwayland-client.so`, `libwayland-egl.so`, `libxkbcommon.so`, `libxkbregistry.so`
   (bionic aarch64). The wcp bundles them in its `lib/` as a fallback; the clean home
   is the imagefs — add via `ImageFsInstaller` (new `installWaylandLibs()`), same
   pattern as `installFFmpeg8()`.

## Integration plan (M4)
- **CMake**: add `waylandcomp` as a native lib (`libbannerwayland.so`) built with the
  NDK, linking the bionic `libwayland-server`/`libvulkan`. Generate protocol glue from
  `protocols/*.xml` at build time (host `wayland-scanner`).
- **Surface**: a `WaylandDisplayActivity` (parallel to `XServerDisplayActivity`) hosts a
  `SurfaceView`; JNI hands the `ANativeWindow` to the compositor, which creates a Vulkan
  swapchain on it and blits the imported game VkImage each frame (the last un-proven
  render step; standard Vulkan once the window exists).
- **Input**: Android `MotionEvent`/`KeyEvent` → `wl_seat`/`wl_pointer`/`wl_keyboard`.
- **Launch wiring**: start the compositor, export `WAYLAND_DISPLAY`, select the wayland
  driver per-prefix (registry `Drivers\Graphics = winewayland`) instead of `winex11`,
  and point the container at the winewayland wcp.

## Status
- ✅ **Native lib foundation done + compile-verified.** Compositor + vk_import + pre-generated
  protocol glue build as `libbannerwayland.so` (CMake target added), linking the vendored
  bionic `libwayland-server`. Verified: compiles/links clean as an aarch64 bionic `.so`,
  exports `banner_wayland_run`, NEEDED = libwayland-server + libvulkan. JNI entry
  (`waylandcomp_jni.c`) + `WaylandCompositor.java` bring it up on a thread. Compositor-process
  runtime deps (libwayland-server/libffi/libandroid-support) staged in `jniLibs/arm64-v8a`.
- ⏭️ **Next phase (gated on the winewayland wcp landing green):** `WaylandDisplayActivity`
  (SurfaceView) + JNI `ANativeWindow`→Vulkan swapchain + blit the imported game VkImage to the
  window (last un-proven render step) + input + launch wiring (start compositor, `WAYLAND_DISPLAY`,
  per-prefix `Drivers\Graphics=winewayland`) + `ImageFsInstaller.installWaylandLibs()` (client/egl/xkb
  into the imagefs for winewayland.so).

## Pointer lock / relative mouse (zwp_pointer_constraints_v1 + zwp_relative_pointer_manager_v1)
- Both globals are advertised (version 1); glue is pre-generated in `generated/` with
  `wayland-scanner server-header` / `private-code` from `protocols/*-unstable-v1.xml` (same
  scanner version as the rest, 1.24.0), no build-time scanner needed.
- winewayland's use: `ClipCursor`/fullscreen + hidden cursor → `lock_pointer` (persistent);
  `SetCursorPos` → lock + `set_cursor_position_hint` + commit + unlock; visible cursor +
  `ClipCursor(rect)` → `confine_pointer` with a one-rectangle region. It only turns relative
  motion on for a window its `wl_pointer` has entered, so **pointer focus follows an active
  constraint** (the desktop surface gets `leave`, the constrained surface `enter`) and goes
  back to the desktop on the next motion after the constraint ends.
- Lock: the pointer is frozen; every input delta becomes `relative_motion` (no `motion`).
  Confine: absolute motion is clamped to surface ∩ region. The position hint is applied on
  the surface's commit (pointer moves there) and is where the pointer stays when the lock ends.
  One constraint holds at a time (a newer request ends the older one); oneshot constraints
  are defunct after ending, persistent ones re-take hold on focus re-entry.
- App side: input type 6 = relative delta (1/256 px); `banner_on_pointer_lock(locked, x, y)`
  tells Java to switch its touch/mouse path to deltas (`XServer.setExternalRelativeMode`) and,
  on unlock, to re-sync the X pointer (the absolute input's source) to x,y. With Wayland mode
  `WinHandler.mouseEvent` routes relative-mode input to the compositor instead of the guest.
- Session log tag `pointer`: `lock requested by …`, `locked: … frozen at x,y`, `unlocked: … (why)`,
  `confined: …`, `unconfined: …`, `relative pointer created for …`, `position hint: …`.
  Test FIFO gained `rel DX DY`.

## Screen surface, swapchain recovery (vk_present.c)
- The app's UI thread never waits on the renderer: `vk_present_set_window()` only leaves the new
  `ANativeWindow` (or NULL) in a request slot; the compositor thread applies it before its next
  frame and on every vsync tick (`vkp_apply_window_request`), tearing the old swapchain down and
  releasing the old window's reference. Acquire waits at most 1 s, never forever.
- OUT_OF_DATE / SURFACE_LOST on acquire or present rebuild the swapchain (once inline on acquire, so
  the frame isn't lost). SUBOPTIMAL is presented as is: the swapchain uses IDENTITY preTransform on
  purpose, so a rotated panel reports it on every frame. DEVICE_LOST logs
  `GPU device lost … the compositor has stopped presenting` once and stops touching the device;
  clients keep being paced (`pace_without_output`) so they don't wedge.
- A swapchain that can't be created is retried every 0.5 s and logged once per streak.

## Buffer lifetime, FPS limiter (compositor.c)
- `struct dmabuf_buffer` is reference counted: one ref for the wl_buffer resource, one per surface
  showing it. Mesa destroys a swapchain's wl_buffers when the game rebuilds its swapchain, while the
  last committed one is still on screen; the surface keeps the import (`s->dmabuf_buf`) until its
  next commit, so the window neither blinks to black nor is unmapped/remapped ("closed"/"opened").
- Surface destroy releases its buffer immediately (`drop_dmabuf(s, 0)`), replaced buffers go back on
  the limiter's cadence. The per-surface release schedule is bounded: a slot in the past is brought
  to now, and the schedule never runs further ahead than `(releases still pending + 1)` intervals.

## Fullscreen mode + screen alignment
- `WaylandCompositor.nativeSetScaleMode(fullscreenMode, screenAlignment)` takes the app's
  `Container.FULLSCREEN_OFF/FIT/STRETCH/FILL/INTEGER` (0..4) and `ALIGN_CENTER/TOP/BOTTOM` (0..2),
  any thread, any time (the drawer changes it live). `update_map()` in vk_present.c is a line-for-line
  mirror of `ViewTransformation.update()`: OFF and FIT both letterbox (OFF only differs in the app's
  fullscreen gates), TOP/BOTTOM confine the picture to the top/bottom half of the output (the
  handheld split), FILL overflow is clipped to the region. The app maps touch through the same class
  (TouchpadView → X server → input sink → scene input), the overlay arrow through
  `waylandSceneToView`, and the SurfaceView's own INPUT_SPACE touches through `vkp_output_to_scene`.
- Log tag `screen`: `<mode>, <alignment>: WxH scene shown WxH at x,y on the WxH output` whenever the
  mapping changes (mode, alignment, scene or output size).

## Clipboard, text input, window icons (feat/wayland-clipboard-ime)
Three globals winewayland used to complain about at startup, each in its own file behind the
small interface in `src/banner_ext.h` (compositor.c only gained hook calls + accessors):
- **Clipboard** (`src/wl_clipboard.c`): `wl_data_device_manager` **v3** and
  `zwlr_data_control_manager_v1` **v1**, selection only (start_drag is refused with `cancelled`).
  Wine prefers data-control: its desktop process then owns the clipboard without needing keyboard
  focus and the "clipboard functionality will be limited" ERR goes away too. One shared selection:
  a program's source, or text from Android (`nativeSetClipboardText`), or nothing. Data-control
  devices hear every change; `wl_data_device`s only while their client holds keyboard focus (hook in
  `keyboard_focus()`), and on focus arrival. Guest → Android: a selection with a text mime is read
  once over a pipe on the event loop (1 MiB cap, 3 s timeout) → `banner_on_clipboard_text` →
  `WaylandClipboardSync` → `ClipboardManager`. Android → guest: `OnPrimaryClipChangedListener` plus a
  re-read on resume / window focus (Android hides clipboard changes from background apps); `receive`
  is served by a non-blocking writer. Echo guard on both sides. Log tag `clipboard`.
- **Text input** (`src/wl_text_input.c`): `zwp_text_input_manager_v3` **v1**, double-buffered state,
  `done(serial)` only when we sent events. winewayland enables text input for whichever surface gets
  `enter` and posts IME updates inside *that window's process*, so text input can't follow keyboard
  focus (the desktop surface): it follows the last clicked program window (`g_ime_click`, set in
  `pointer_input`), else the topmost non-shell window. `set_cursor_rectangle` arrives when an edit
  control calls `ImmSetCompositionWindow`; the app (`WaylandTextInput`) shows the soft keyboard only
  then (and with no hardware keyboard), hides it on disable unless the user's own toggle opened it.
  Typed text: `SurfaceInputView`'s `InputConnection` → `commit_string` / `preedit_string` + `done`.
  Deletions become Backspace/Delete key presses — winewayland's `delete_surrounding_text` handler is
  empty. Log tag `text-input`.
- **Window icons** (`src/wl_toplevel_icon.c`): `xdg_toplevel_icon_manager_v1` **v1**, accepts and
  drops icons (`done` at bind, no sizes).
- Host → compositor text crosses as UTF-8 `byte[]` through `banner_ext.c`'s queue (mutex + wake
  pipe), drained on the compositor thread. Glue for the three protocols is pre-generated with
  wayland-scanner 1.24.0 from `protocols/`.

## Compressed (UBWC) game buffers (feat/wayland-ubwc)
- `zwp_linux_dmabuf_v1` used to advertise `LINEAR` (+`INVALID`) only, so Turnip's Wayland WSI in the
  game allocated linear swapchain images and DXVK/VKD3D resolved every frame from their tiled/UBWC
  render targets into that linear copy, before the compositor's own blit. Now `bind_dmabuf` builds
  its table at the first bind from the renderer's driver (`vkp_dmabuf_modifiers`):
  `vkGetPhysicalDeviceFormatProperties2` + `VkDrmFormatModifierPropertiesListEXT` per fourcc
  (AR24/XR24/AB24/XB24), each modifier confirmed with `vkGetPhysicalDeviceImageFormatProperties2`
  for a dma-buf-backed `TRANSFER_SRC` image. Only `DRM_FORMAT_MOD_LINEAR` (0) and
  `DRM_FORMAT_MOD_QCOM_COMPRESSED` (`0x0500000000000001`, UBWC, single memory plane on Adreno) are
  ever advertised; anything else the driver reports goes to logcat as "not advertised".
- Mesa's WSI hands every advertised+supported modifier to `vkCreateImage` as a modifier *list* and
  Turnip picks `QCOM_COMPRESSED` whenever it is in the list (`tu_image.cc`), so with the
  advertisement the game's swapchain is UBWC. The `wl_buffer` then arrives with that modifier and
  the pixel-plane pitch/offset from the game's `vkGetImageSubresourceLayout(MEMORY_PLANE_0)`;
  `vkp_image_import_dmabuf` creates the compositor's image with the explicit modifier and that one
  plane layout (`VkImageDrmFormatModifierExplicitCreateInfoEXT`), which Turnip validates
  (`INVALID_DRM_FORMAT_MODIFIER_PLANE_LAYOUT` if the pitch does not fit its `fdl6` alignment). The
  swapchain blit (`vkp_render`) and the layer-mode blit (`vkp_blit_image`) read it like any other
  source; nothing CPU-side ever touches a dma-buf. Layer mode still blits into its own gralloc pool.
- **A/B switch:** `BANNER_WAYLAND_UBWC=0` (or `false`/`off`) in the container's/shortcut's
  environment variables → `nativeSetUbwc(false)` → linear-only advertisement, exactly the old
  behaviour. Default on. The driver is still queried, so the log shows what it could have done.
- Log tag `dmabuf`: `formats: AR24 linear+qcom_compressed, XR24 …, AB24 …, XB24 …` at the first
  bind (with ` (BANNER_WAYLAND_UBWC=0: qcom_compressed not advertised)` when off, or a second line
  `the compositor's driver (…) reports no importable qcom_compressed layout: game swapchains stay
  linear` when the adrenotools Turnip lacks it). Per window, the existing `vulkan` line now names
  the modifier: `… is presenting GPU frames through Wayland: 1920x1080, format XB24, qcom_compressed
  (zero-copy)` vs `… linear (zero-copy)`. Import failures are `error` lines naming the stage,
  modifier, size, pitch and VkResult (`dmabuf: vkCreateImage(qcom_compressed, …) -> …`).
- Caveat: both sides must agree on the UBWC encoding for the GPU. The game runs the wcp's Wayland
  Turnip, the compositor the user's adrenotools Turnip; the layout code (`fdl6`) is the same, but a
  very different Mesa version on one side is the first suspect if a compressed frame imports fine
  yet looks scrambled — `=0` is the workaround, and the `gpu` line names both the GPU and the
  compositor's driver.

## Phones with no DRM node (OpenGL) — `BANNER_WAYLAND_NO_RENDER_NODE=1`
- The feedback's `main_device` is the first of `/dev/dri/renderD128`, `renderD129`, `card0` the app
  can `stat`; retail phones (Adreno 830/840 reports, 2026-09-14) expose none, so it is `0:0`.
  Vulkan games do not care. Mesa's EGL did: its Wayland DRM initialiser failed the display in
  `dri2_setup_device()` without a render node and fell back to a software path that has no
  rasteriser in these layers — native OpenGL black, sound playing. Wayland layer versionCode 9
  (Banners-Turnip `patches/wayland/egl_wayland_no_drm_node.py`) runs zink + kopper on the Vulkan
  device without a node instead; nothing app-side is needed for that.
- `BANNER_WAYLAND_NO_RENDER_NODE=1` (container or shortcut env) makes the compositor advertise
  `main device 0:0` on a device that does have a node, to reproduce those phones here.
- Log lines: `dmabuf` `… no display (DRM) device …: OpenGL games need Wayland layer versionCode 9
  or newer …` when the main device is 0:0; `opengl` `<program> asked for GPU buffers but has drawn
  only software (shared-memory) frames …` once per program that asked for dma-buf feedback (EGL's
  GPU path always does) and then committed 150 wl_shm frames without making a dma-buf buffer.

## Zero-copy window layers (spike, `BANNER_WAYLAND_ZERO_COPY=1`)
Research + host-side prototype in `ZERO_COPY_SPIKE.md`: why a dma-buf can't become an
`AHardwareBuffer` (the game's buffers are DMA-heap allocations, not gralloc's), why the interop
must start from a gralloc buffer whose native-handle fd the drivers import (Turnip's own AHB path),
and the WSI patch this needs in our Wayland Turnip. `src/sc_layer.c` implements the receive side:
with the variable in the container's environment, one fullscreen game window is shown on its own
`ASurfaceControl` child of the SurfaceView (pool of three compositor-allocated AHBs, one blit,
release fences from `setOnComplete`, geometry from the fullscreen mode); any other scene falls back
to the swapchain blit. Log tag `layer`. Off by default.

## Zero-copy game frames (`BANNER_WAYLAND_ZERO_COPY=1`, drawer switch live)
Option (a') of `ZERO_COPY_SPIKE.md`, both halves:
- **Guest (our Wayland Turnip, banners-turnip-wayland `patches/wayland/banner_ahb_wsi.py`, all
  variants):** while the compositor's mode is on (see the live switch below), the Wayland
  WSI allocates every swapchain image as a gralloc `AHardwareBuffer` (usage GPU_SAMPLED_IMAGE |
  GPU_FRAMEBUFFER | COMPOSER_OVERLAY; UBWC when gralloc picks it and a test `vkCreateImage` with the
  explicit `QCOM_COMPRESSED` layout succeeds, else linear via a CPU usage bit; `BANNER_WSI_AHB_LINEAR=1`
  forces linear), imports the handle's dma-buf with an explicit modifier + pitch (the compositor's own
  pool-import path; `vk_android.c` is not in a platforms=wayland build), shares it through
  `zwp_linux_dmabuf_v1` as before (blit fallback), and sends the `AHardwareBuffer` once per image:
  `socketpair` + `AHardwareBuffer_sendHandleToUnixSocket` + `banner_ahb_v1.attach(wl_buffer, fd, w, h,
  stride, modifier hi/lo, image_count)`. Surface formats are limited to `R8G8B8A8_UNORM/SRGB` (and
  `A2B10G10R10`) while the mode is on: gralloc has no BGRA (DXVK/Zink blit into the swapchain image
  anyway). Marker: `strings libvulkan_freedreno_wayland*.so | grep banner_ahb_v1`.
- **Compositor (`src/ahb_swapchain.c`, protocol `protocols/banner-ahb-v1.xml`):** the global is
  advertised on every session where a display layer is possible at all (`sc_layer_available()`);
  whether gralloc buffers are WANTED right now is the `mode` event, not the global's presence.
  `attach` → `AHardwareBuffer_recvHandleFromUnixSocket`, record on the `dmabuf_buffer`.
  When that buffer is the one fullscreen frame (`layer_candidate`, or the layer-only candidate when
  this renderer could not import it), `sc_layer_present_ahb` sets it on the `banner_wayland_game`
  SurfaceControl as is. **Acquire fence** = `DMA_BUF_IOCTL_EXPORT_SYNC_FILE(READ)` of the dma-buf: Mesa
  imports the render fence into the dma-buf (`wsi_signal_dma_buf_from_semaphore`) *before* the
  `wl_surface.commit`, so the export always carries the frame; if the ioctl fails the compositor
  polls the dma-buf instead (logged once). **Release**: `setOnComplete` →
  `getPreviousReleaseFenceFd` → `DMA_BUF_IOCTL_IMPORT_SYNC_FILE(READ)` into the dma-buf → then
  `wl_buffer.release` (still on the FPS limiter's cadence); Mesa's acquire
  (`wsi_create_sync_for_dma_buf_wait`) exports every fence of the dma-buf, so the game waits for the
  display. If the import fails the release waits on the fence fd in the event loop instead. A hidden
  or retired layer gets a 16x16 blank buffer so SurfaceFlinger actually lets go of the game's buffer.
- **App:** `BANNER_WAYLAND_ZERO_COPY=1` (container/shortcut env) is the session's STARTING state and
  also exports `BANNER_WSI_AHB=1` into the guest (`XServerDisplayActivity.isWaylandZeroCopyRequested`),
  which is what an older driver (bound at `banner_ahb_v1` version 1) needs.

### The live switch ("Zero-copy presentation", Graphics tab)
`banner_ahb_v1` version 2 adds one event, `mode(enabled)`, sent to every bound client on bind and
again on every flip. The compositor owns the state:
- `WaylandCompositor.nativeSetZeroCopy(on)` is live and callable from any thread. Before the
  compositor starts it just seeds `g_zero_copy`; afterwards it posts through the `banner_ext.c` host
  queue (like the clipboard/IME setters) so `ahb_swapchain_set_mode()` runs on the compositor thread,
  which flips `g_zero_copy`, broadcasts `mode`, flushes the clients and redraws.
- **Guest:** the WSI follows `display->banner_ahb_mode`, decided per swapchain at creation. When a
  live swapchain was built for the other mode, the next `vkAcquireNextImageKHR` /
  `vkQueuePresentKHR` returns `VK_ERROR_OUT_OF_DATE_KHR` (it reuses Mesa's own `chain->retired`
  early-outs), which DXVK, vkd3d-proton and Zink all answer by rebuilding the swapchain. The mode
  event is read with a non-blocking dispatch of the display queue on each acquire/present, because
  in MAILBOX that queue is otherwise only dispatched when the acquire loop runs out of images.
- **Compatibility, both directions.** The registry bind clamps to `MIN(advertised, 2)` — binding
  above the advertised version is a fatal `wl_display` error, and a Bannerlator from before the live
  switch advertises version 1. On version 1, and on version 2 before the first `mode` event arrives,
  the driver keeps the original contract exactly: gralloc images iff `BANNER_WSI_AHB=1`, decided per
  swapchain, and no swapchain is ever retired. Once the mode is in play `BANNER_WSI_AHB=1` no longer
  forces it on (the app exports that on every zero-copy launch, which would freeze the switch);
  `BANNER_WSI_AHB=0` still forces the whole feature off.
- **No black frame, either way.** Switching OFF does not tear down anything: the old gralloc chain's
  frames keep being shown — on the layer when this renderer could not import them, through the copy
  path when it could — until the game has rebuilt, and only then does `sc_layer_hide()` run. Buffers
  already on the layer keep their deferred release (`ahb_swapchain_defer_release` is no longer gated
  on `g_zero_copy`, or the display would be handed a buffer it is still scanning out). Switching ON,
  the layer path resumes as soon as the first `attach` of the new chain lands.
- **Drawer:** the toggle applies live *and* writes the env as the next launch's default. Its status
  line follows the compositor, not the switch: `nativeZeroCopyLastFrameAgeMs()` (updated on every
  zero-copy present, unlike the 10 s counter) is what makes it say "Switching on…" / "Switching off…"
  only for as long as it really is.
- **Log lines (tag `layer`):** `zero-copy: banner_ahb_v1 version 2 advertised …`, `zero-copy: <exe>
  bound banner_ahb_v1 version N …`, `zero-copy: on|off at launch …`, `zero-copy switched on|off from
  the drawer: N bound programs told to rebuild their swapchains …`, `zero-copy: AHB swapchain from <exe> (N images, WxH, UBWC (QCOM_COMPRESSED)|linear,
  stride S px)`, `zero-copy: presenting "<title>" (<exe>) without a copy`; the 10 s `stats` line ends
  with `| N zero-copy frames`. Guest side (Mesa log, stderr of the game): `banner-ahb: WxH swapchain
  (N images) on gralloc buffers: UBWC|linear, stride S px` or the reason it stayed on standard
  buffers, plus `banner-ahb: compositor wants gralloc|standard swapchain images` and `banner-ahb:
  zero-copy switched on|off: retiring the WxH swapchain …` on a live flip.

## Multi-layer presentation (feat/wayland-multilayer, `src/sc_layer.c`)
Layer mode hands the scene to SurfaceFlinger as a small, deliberately ordered **set** of Android
display layers instead of one. The whole model, top to bottom:

```
app window ....... Compose UI, the in-game drawer, the perf HUD, the on-screen controls, the
                   pointer arrow - ordinary Android views in the activity's window, ALWAYS on top
+-- SurfaceView .. the compositor's Vulkan swapchain (black while layer mode is up)
     +-- z=1  "banner_wayland_game" ...... the one fullscreen game window
     +-- z=2  "banner_wayland_overlay" ... at most ONE window drawn above the game
```

- **Two layers is the hard cap** (`SC_LAYER_COUNT`). HWC composes only a few layers before
  SurfaceFlinger falls back to GPU client composition, which would throw the whole benefit away, so
  the compositor never asks for a third: a scene with two or more windows above the game goes back
  to the copy path. The count is said out loud the first time the overlay goes up (`layer`: `2
  display layers in use: "banner_wayland_game" (z=1) and "banner_wayland_overlay" (z=2) above it …`).
- **Who owns what.** Each layer has its own `ASurfaceControl`, its own gralloc buffer pool (3
  buffers each), its own geometry and its own lifetime; `struct layer`
  in `sc_layer.c` holds all of it and the z-order is fixed by the id (nothing is re-ordered at
  runtime). The SurfaceControls are children of the SurfaceView's surface, created on first use and
  retired together when the output window changes or goes away.
- **What can be on the game layer**, cheapest first: the game's own gralloc buffer (zero-copy, no
  copy anywhere — `ahb_swapchain.c`); one blit of the game's frame into a compositor buffer
  (`sc_layer_present`); or, with screen effects on, the compositor pass's **result** blitted into
  such a buffer (`sc_layer_present_pass` → `vkp_pass_begin` / `vkp_pass_copy_to`). The last one is
  what keeps a Look from dropping the session back to the app's swapchain: the scene → output
  mapping (Fullscreen Mode / Alignment, and the scaling a mode like FSR asks for) is then done by
  the display through `setGeometry` instead of by a second full-screen GPU blit.
- **Present order matters, and it was measured.** The layers go up first; the base surface's black
  frame is presented *after* the layer transaction, and the effects chain runs in its own submit
  with **no swapchain image acquired**. A present holds an acquired image and the acquire semaphore
  is a vblank gate: folding the chain into that submit (one command buffer, one present — the
  shape that looks cheaper) put the whole 13-pass chain behind a vblank and measured **72 fps**
  against **111 fps** for the split shape, on HL2 + Retro CRT at 1920x1080 on the Pocket FIT (the
  copy path is 129 fps on the same scene). Anyone tempted to "optimise" this into one submit should
  read this paragraph first.
- **What goes on the overlay layer**: exactly one draw above the game — a second Wayland toplevel
  (a launcher or settings window, a message box), copied 1:1 into the overlay pool and then
  *cropped and placed* by the display through the same `vkp_map_draw` mapping the copy path uses.
  The point is what does **not** happen: the game's frames are no longer copied through the
  compositor's swapchain just because something small sits on top of them.
- **Transparency.** The compositor composes with blits, which overwrite — it never alpha-blends,
  on either path — so every layer is marked `ASC_TRANSPARENCY_OPAQUE` and the overlay layer is
  cropped to the window it carries. There is no blending to get wrong: no black box over the game,
  no double-darkening, and the picture is the copy path's, pixel for pixel.
- **Input is untouched.** An `ASurfaceControl` has no input channel, so neither layer can take a
  touch: everything still reaches the app's `SurfaceView` (and the views above it) exactly as
  before, and the scene mapping touch goes through (`vkp_output_to_scene` / `ViewTransformation`)
  is the same one the layers are placed with, so the cursor still lands where it is pointing. The
  SurfaceView is not Z-on-top, so its whole subtree — both layers included — stays under the
  activity's own window: the drawer, the HUD and every Compose dialog draw above them.
- **What cannot be layered, and why.**
  - *Frame generation*: each generated frame needs a present of its own on consecutive vblanks, and
    a display layer latches one buffer per refresh — pacing that is the swapchain's job. While an
    engine is armed the whole scene takes the copy path (unchanged).
  - *Effects with a window above the game*: the chain has to see the whole scene to look the way it
    does on the copy path (and on X11), and the game layer only carries the game. Copy path.
  - *Two or more draws above the game* (several windows, or a window with subsurfaces): a third
    layer is not worth the client-composition risk. Copy path.
- **Lifecycle.** Hiding is top-down so nothing is uncovered for a frame; a hidden or retired layer
  gets the shared 16x16 blank buffer so SurfaceFlinger really lets go of the game's buffer (and
  reports a release fence for it) instead of holding it while invisible. `sc_layer_hide()` takes
  every layer down (scene no longer a fullscreen game, zero-copy switched off live, session end),
  `sc_layer_hide_overlay()` is used when only the window above the game closed, and
  `sc_layer_window_gone()` retires both SurfaceControls and drains both pools on a surface loss /
  HOME / resume. **The overlay layer is always RETIRED, never merely hidden** (both paths) — see
  the HWC note below: a live second SurfaceControl keeps SurfaceFlinger composing on the GPU even
  when it is invisible. The game layer keeps its SurfaceControl through a hide, because it goes up
  and down with every effects / frame-generation toggle.
- **The display frame-rate vote belongs to one layer.** The refresh-rate stream's
  `sc_layer_set_frame_rate()` (the game's cadence, the same value the app votes on its own surface)
  is carried **only** by the layer the game presents on — the game layer. The overlay layer is
  explicitly voted `0`, so a window that redraws once a second can never hold the panel at the
  game's cadence, nor drag the game's cadence down to its own. Each layer remembers what its live
  SurfaceControl carries (`fps_applied`) and re-applies on the next transaction, and a retired
  SurfaceControl resets it so a re-created layer is re-voted from scratch. Log: `display frame-rate
  vote on banner_wayland_game: 60.00 Hz`.
- **Stats.** The 10 s `stats` line now ends with `| N zero-copy frames` (the game's own buffers) and
  `| N layer frames` (frames the compositor put on a layer through one of its own buffers — the
  plain layer blit or the effects result). Both are hardware-composed; only the first is copy-free.
- **Measured on the Pocket FIT (Adreno 750, portrait panel, landscape session → every layer is
  ROT_90 + scaled), `dumpsys android.hardware.graphics.composer3.IComposer/default`:** one layer is
  `composition: DEVICE/DEVICE` (game's own gralloc buffer scanned out by the DPU), with effects on
  the layer it stays `DEVICE/DEVICE`, but **two** layers flip the whole frame to `DEVICE/CLIENT` —
  SurfaceFlinger composes it on the GPU — **and it does not come back when the overlay goes away**:
  retiring the overlay's SurfaceControl (which the compositor now does rather than merely hiding
  it) leaves the frame in client composition; only re-creating the *game* layer's SurfaceControl
  clears it (HOME + resume does, reproduced twice). Retiring is still right — one fewer live layer,
  and it leaves the HWC list — it is just not the whole cure; the open follow-up is to retire and
  immediately re-create the game layer when the overlay goes, which would cost one black frame
  unless the new SurfaceControl is shown before the old one is dropped. The likely mechanism is the
  DPU's rotator budget (one rotated+scaled layer), not the layer count as such, so a device or
  orientation that needs no rotation may well take both on the DPU. Even in client composition the
  overlay layer is not a loss (SurfaceFlinger does the one blit the compositor would have done), but
  the hardware-composition win is only real for the single-layer cases. Note `VRI[ScreenDecorHwcOverlay]`
  is always `DISPLAY_DECORATION/CLIENT` (the system's rounded corners) — that is why SurfaceFlinger's
  `clientCompositionFrames` counter reads 100 % on this device in every state and is useless here.
- **Not done yet:** a second toplevel that is itself rendering into gralloc buffers could go on the
  overlay layer zero-copy too (the token machinery in `ahb_swapchain.c` is already per-buffer, not
  per-layer); today the overlay always costs one small blit.

## Screen effects (feat/wayland-effects, `src/effects_chain.c`)
The X11 renderer's screen-effect chain, run by the compositor between the composited scene and the
output. Same SPIR-V as the X11 Vulkan renderer (`winlator/*_frag.h`, made by `winlator/gen_shaders.sh`
and committed — the NDK build compiles no GLSL, so there is no build-time shader tooling), same
push-constant layouts, uniform ranges and pass order, so a saved preset looks the same on both backends.

**Present-pass hook order (`vk_present.c`, `vkp_render`):**
```
scene  (every draw blitted 1:1 into a scene-sized R8G8B8A8 image; compositor pass only)
  -> vkp_effects_run()     effects_chain.c: scaling -> effects (below)
  -> vkp_framegen_run()    framegen_bridge.c: frame generation on the chain's result (next section)
  -> update_map / blit     Fullscreen Mode + Screen Alignment map the result (and each generated
                           frame) onto its own swapchain image
  -> present               generated frames first, the real frame last, FIFO
```
The compositor pass runs when either stage needs it (`vkp_effects_active() || vkp_framegen_active()`).
Effects operate on the scene; the mapping operates on the output — the chain's result stands for
the whole scene and goes through the same `draw_to_blit` as a plain frame. With everything off the
frame takes the old path unchanged (draws blitted straight through the mapping); only the blit filter
follows the scaling mode (Nearest = `VK_FILTER_NEAREST`).

**Pass order (locked to the X11 Vulkan renderer, `VulkanRendererContext::recordUpscalePasses`):**
scaling → FXAA → Toon → Colour (brightness/contrast/gamma/saturation) → CAS → HDR → NTSC → CRT →
Debanding (terminal dither). Scaling modes: 0 None / 1 Linear / 2 Nearest = a filtered resize;
3 SGSR, 8 SGSR HQ, 7 NIS = one pass; 4 FSR / 5 FSR Fit = EASU → RCAS (fill vs fit is the Fullscreen
Mode's business here, both run the same pair); 6 Sharpen = RCAS 1:1 (nearest scale + sharpen, as X11).
Render scale is "Not used on Wayland": the scaling modes resize the scene to its **mapped output size**
(3/4/5/7/8 only when that is larger than the scene, the X11 "render below display" gate; 6 always).
Every later pass runs at that resolution, ping-ponging between two targets (+ one EASU mid target).

**Settings** (`WaylandCompositor.nativeSetUpscaler / nativeSetUpscaleSharpness / nativeSetCas /
nativeSetHdr / nativeSetDeband / nativeSetScreenEffects / nativeSetLookName`, 1:1 with
`VulkanRenderer`'s): any thread; the compositor thread snapshots them per frame, redraws even a
static scene, and writes one `effects` line per change:
`effects  scaling=SGSR HQ 75%, CAS on 55%, Look="Game Clarity", colour b=+2 c=+12 g=1.00 s=108%`
(`effects  all off: scaling=Linear (plain blit)` when nothing is on). The chain's Vulkan objects are
built on the first active frame (`effects  chain ready: 13 passes … on <GPU>`; a driver that refuses
them logs an `error` and effects stay off for the session). The app wires the drawer's Vulkan
post-chain block (`XServerDialogState.on*Apply`) to these in `XServerDisplayActivity.initWaylandEffects`
and remembers the same per-game keys as the Vulkan path (#382).

**Zero-copy interplay:** the chain's result no longer has to land in the app's swapchain — with a
fullscreen game alone on screen it is copied into the game layer's own gralloc buffer and the game
**stays on its display layer** while a Look is applied (see "Multi-layer presentation" below). It
still takes the copy path when a window is drawn above the game (the chain has to see the whole
scene to look as it does on X11) — `effects  zero-copy paused: a window above the game needs the
compositor pass for the whole scene` — and always while frame generation is armed
(`framegen  zero-copy paused: frame generation needs the compositor pass`); the layer comes back with
`effects  zero-copy resumed: the game is back on its own display layer`. A frame this renderer could
not import (layer-only candidate) still goes on the layer, effects/frame generation skipped, said once.

## Frame generation (feat/wayland-framegen, `src/framegen_bridge.c`)
The two **native** engines the X11 renderer hosts inside `libwinlator`'s compositor run inside the
Wayland compositor's own Turnip device: **LSFG Native** (the user's Lossless.dll chain,
`winlator/lsfg`) and **Win-FG Native** (our FSR3-derived optical-flow chain, `winlator/winfg`).
The engine sources are compiled into `libbannerwayland.so` unmodified (CMake, app build only; the
standalone build gets `framegen_engine_stub.c`) and driven by `src/framegen_engine.cpp`, which fills
their `VkTable` by name from the compositor's `vkGetInstanceProcAddr`/`vkGetDeviceProcAddr`.
**bionic-fg** (the guest-side win-fg Vulkan layer) is X11-only: its frames are born inside the guest
and have nothing to attach to here, so on Wayland the "bionic" engine is always Win-FG Native.
- **Where it runs** (the hook order above): frame generation takes the effects chain's RESULT
  (`vkp_effects_run`'s output, at its size - the scene, or the mapped output size under a scaling
  mode - moved to GENERAL), so generated frames carry the same look as real ones. The ONE hook
  `vkp_framegen_run(cmd, image, view, w, h, fmt, gens[])` records the engine's chain into the same
  command buffer and returns 0..3 generated images. Each image is then blitted through the scene ->
  output mapping into its own acquired swapchain image and presented: **generated frames first, the
  real frame last** (interpolation produces frames between N-1 and N; that one output interval of
  latency is inherent). One acquire/render-done semaphore pair and command buffer per present
  (`MAX_PRESENTS` = 4), the frame fence on the last submit. With the FIFO swapchain the presentation
  engine shows them on consecutive vblanks: that is the pacing, there are no sleeps and no host pacer.
  The swapchain is rebuilt with `multiplier - 1` extra images while armed so every present of a frame
  queues without a vblank wait. Frame generation off and effects off = the direct path, byte-for-byte
  as before.
- **Device**: `dev_init` asks the bridge for the LSFG feature chain (`VkPhysicalDeviceVulkan12Features`
  vulkanMemoryModel + `shaderStorageImageWriteWithoutFormat/ExtendedFormats`, the same chain
  `VulkanRendererContext::createLogicalDevice` uses; instance apiVersion is 1.3 for the probe) and
  retries device creation without it if the driver refuses. Win-FG needs only a storage-capable ring
  format (RGBA8). The generation ring (`STORAGE|SAMPLED|TRANSFER_SRC|DST`, GENERAL) and the engines'
  own history live for the session; arming/disarming is a flag, a multiplier change resizes the ring.
- **FPS limiter / vsync**: untouched. The limiter still paces the game's buffer returns (real frames);
  `applyNativeFgLocks` forces it on while multiplying, exactly as on X11 (`pacedLimitWithSlack` still
  applies through `nativeSetFpsLimit`). Generated frames never touch the limiter, the frame callbacks
  or the `wp_presentation` feedback - the guest sees one present per real frame.
- **Zero-copy**: the layer bypasses the compositor pass, so while frame generation is armed the
  fullscreen window takes the copy path (`render_scene`: `framegen  zero-copy paused: frame generation
  needs the compositor pass`) and the layer resumes when both it and the effects are off (see the
  effects section's zero-copy interplay).
- **App side**: `WaylandCompositor.nativeSetFrameGenEngine/Armed/Tuning`, `nativeSetLsfgCachePath`,
  `nativeSetWinFgTuning`, `nativeFrameGenProblem/CapsReason/Stats` (same codes and six-float shape
  as `VulkanRenderer`). `XServerDisplayActivity.applyWaylandFrameGen` is the single apply:
  `applyLsfgNative`/`applyWinFgNative` route to it in Wayland mode (launch and drawer), the drawer's
  `onBionicFgConfigChange` has a Wayland branch, `nativeFgProblemReason`/`startLsfgStatsReadout` read
  the bridge. Every native setter is a value store, so the launch code may arm before the compositor
  thread exists.
- **Log tag `framegen`**: `engines ready on the compositor's device: LSFG Native available (supported),
  Win-FG Native available`, `<engine> engine ready (...)`, `<engine> x2 armed (flow scale 0.80, panel
  120 Hz): ...`, `generating: <engine> x2 at 1920x1080 (1 interpolated frame per game frame)`, the
  fallbacks (`... can't start: no shader cache`, `... failed to start on the compositor's driver`,
  `... could not build its chain at WxH`, `the driver refused the LSFG feature set`), `frame generation
  off (N frames generated this session)`, and the zero-copy pause/resume above. The 10 s `stats` line
  counts generated frames in `frames on screen` and ends with `| N generated frames`; `GPU frames from
  games` never includes them.
