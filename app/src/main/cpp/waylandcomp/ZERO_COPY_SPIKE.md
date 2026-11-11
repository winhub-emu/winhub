# Zero-copy window layers on Wayland — research spike + host-side prototype

Branch `spike/wayland-zero-copy-layers` (on `feat/wayland-phase2`). Written 2026-09-13.

**Question.** Today every game frame is a dma-buf the compositor imports as a `VkImage`
(`vk_present.c: vkp_image_from_dmabuf`) and blits into its own swapchain (`vkp_render`), one
full-screen copy per frame, and the FPS gap to X11 on the AIO is ~20 % for Vulkan/D3D12
(596 vs 752, 336 vs 430 on the Adreno 750). Wayland lets each window buffer become its own
Android layer (`ASurfaceControl` + `AHardwareBuffer`) so SurfaceFlinger/the DPU composites it
with no copy. The blocker: dma-buf ↔ `AHardwareBuffer` interop on Turnip/KGSL/bionic/Android 14,
with the game on the Wayland Turnip bundled in the Proton wcp and the compositor on the
adrenotools Turnip.

**Answer in one paragraph.** A raw dma-buf can never become an `AHardwareBuffer` (option b is
closed: no public API, and the game's buffers are `/dev/dma_heap/system` allocations, not
gralloc buffers). The interop must go the other way: the buffer is a **gralloc `AHardwareBuffer`
first**, and its dma-buf fd (`native_handle->data[0]`) is what the Vulkan drivers import — which is
exactly how Turnip already imports every gralloc buffer on Android (`vk_android.c`). Both Turnips
in play already have every extension needed; nothing is missing in the drivers. What is missing is
a **WSI hook**: Mesa's Wayland WSI always allocates its own dma-bufs and has no way to receive or
allocate compositor-visible buffers, so **option (a) or (c) each need a ~300-line patch to
`wsi_common_wayland.c` in our own Wayland Turnip build** (banners-turnip-wayland
`build_turnip_wayland_wsi.sh`, Mesa `7cda7850`) plus a small private Wayland protocol. Nothing in
the app can remove the copy on its own. Recommended: **option (a′) — the game's WSI allocates its
swapchain images as `AHardwareBuffer`s (gralloc-native, UBWC), imports them through the AHB path
Turnip already has, and hands the compositor the AHB over a Unix socket (`AHardwareBuffer_send/
recvHandleFromUnixSocket`, the same mechanism the X11 renderer already uses for `GPUImage`) plus
the dma-buf fd through `zwp_linux_dmabuf_v1` as today (so the blit path stays as the fallback).**
The host half — one `ASurfaceControl` layer per fullscreen window, geometry from the fullscreen
mode, release fences from `ASurfaceTransaction_setOnComplete`, black base surface, HUD/pointer as
Android views above — is implemented in this branch as a gated prototype (`sc_layer.c`,
`BANNER_WAYLAND_ZERO_COPY=1`) with a compositor-owned AHB pool and one blit, so the
SurfaceFlinger side can be measured on the device before the driver work starts.

---

## 1. Buffer path options

Facts the options rest on (Mesa `7cda7850`, the commit both Turnips are built from):

- **Game-side buffers today** are dma-bufs from a DMA heap: the KGSL winsys allocates exportable
  memory with `DMA_HEAP_IOCTL_ALLOC` on `/dev/dma_heap/system` (ION on older kernels) and imports
  the fd into KGSL with `IOCTL_KGSL_GPUOBJ_IMPORT` / `KGSL_USER_MEM_TYPE_DMABUF`
  (`tu_knl_kgsl.cc:84-107`, `279-296`, `375-436`, `1836-1856`); export is a `dup()` of that fd
  (`kgsl_bo_export_dmabuf`, `:438-442`). They are **not gralloc buffers**: no `private_handle_t`,
  no metadata fd, nothing SurfaceFlinger's mapper could import.
- **Dma-buf import works on both Turnips**: `VK_KHR_external_memory_fd`,
  `VK_EXT_external_memory_dma_buf`, `VK_EXT_image_drm_format_modifier` are unconditional
  (`tu_device.cc:253, 354, 369`); the compositor proves it every frame. Only
  `VK_EXT_physical_device_drm` is off on KGSL (`:385`) — relevant to dmabuf-v4 feedback (§2).
- **AHB import in Turnip is dma-buf import.** `VK_ANDROID_external_memory_android_hardware_buffer`
  is exposed whenever a u_gralloc backend exists (`tu_device.cc:219, 428`), and the fallback backend
  always exists. `vkGetAndroidHardwareBufferPropertiesANDROID` does
  `AHardwareBuffer_getNativeHandle(buffer)->data[0]` → `lseek` for the size →
  `GetMemoryFdPropertiesKHR(DMA_BUF, data[0])` (`vk_android.c:1225-1236`); the image is created with
  `VK_IMAGE_TILING_DRM_FORMAT_MODIFIER_EXT` from the gralloc layout
  (`vk_gralloc_to_drm_explicit_layout`, `vk_android.c:140-205`; `tu_image.cc:1006-1023`). The
  layout comes from `u_gralloc_fallback.c:145-156`: on a QTI handle (magic `'gmsm'` in the first
  int) the UBWC flag `0x08000000` in the next int selects `DRM_FORMAT_MOD_QCOM_COMPRESSED`, else
  `DRM_FORMAT_MOD_LINEAR`; pitch = `pixel_stride × bpp`, offset 0. The qcom backend
  (`u_gralloc_qcom.c`, `hw_get_module`) cannot load the vendor gralloc from an app process, so the
  fallback is the one in use — the local KGSL build imports exactly `AHardwareBuffer_allocate /
  describe / getNativeHandle / acquire / release / isSupported` and `hw_get_module`, NEEDED
  `libnativewindow.so` + `libhardware.so` (readelf on `turnip608/stg/libvulkan_freedreno.so`).
  So **the Wine process already loads `libnativewindow.so`** (as a dependency of the ICD), and
  the X11 renderer already receives AHBs allocated inside guest processes over a Unix socket
  (`DRI3Extension.java:141-150` → `GPUImage(fd)` → `gpu_image.c:74-86`
  `AHardwareBuffer_recvHandleFromUnixSocket`; the sender is the X11 `libvulkan_wrapper.so` WSI).

### (a) Game side: `AHardwareBuffer_allocate` in the guest, exported as a dma-buf

Feasible with the current drivers, **but only through a WSI patch** — the app cannot do it.
Mesa's Wayland WSI creates every native swapchain image with `wsi_create_native_image_mem`
(`wsi_common_drm.c:760-850`): `vkAllocateMemory` with `VkExportMemoryAllocateInfo{DMA_BUF}` →
`vkGetMemoryFdKHR` → `zwp_linux_buffer_params_v1.add(fd, …)` + `create_immed`
(`wsi_common_wayland.c:3558-3620`). Nothing in it allocates from gralloc, and `VkNativeBufferANDROID`
(the Android-WSI import path, `tu_image.cc:880-925`) is only reachable from the Android swapchain.

The patch (in our Wayland Turnip build): a new `create_mem` for the Wayland chain, selected when the
compositor advertises a private global `banner_layer_buffer_v1`:
1. `AHardwareBuffer_allocate(w, h, R8G8B8A8, GPU_COLOR_OUTPUT | GPU_SAMPLED_IMAGE)`;
   `vkGetAndroidHardwareBufferPropertiesANDROID` → `vkAllocateMemory` with
   `VkImportAndroidHardwareBufferInfoANDROID` (Turnip: `tu_device.cc:3828`), bind — the image was
   created with the modifier/pitch gralloc reports, i.e. UBWC on Adreno gralloc (§2).
2. `wsi_image.dma_buf_fd = dup(native_handle->data[0])`, `drm_modifier` / `row_pitches[0]` from
   the same layout → the existing `zwp_linux_dmabuf_v1` path stays untouched: the compositor keeps
   importing a dma-buf and can keep the blit path as the fallback.
3. `banner_layer_buffer_v1.attach(wl_buffer, socket_fd)`: the WSI creates a `socketpair`, sends one
   end in the request, and calls `AHardwareBuffer_sendHandleToUnixSocket(ahb, other_end)`; the
   compositor's request handler does `AHardwareBuffer_recvHandleFromUnixSocket` and stores the AHB
   on `struct dmabuf_buffer`. Per swapchain image, once — not per frame.
Extensions used by the game: `VK_ANDROID_external_memory_android_hardware_buffer` only (the
dma-buf/modifier extensions are used internally by the same driver). Note winevulkan does not
expose the AHB extension to PE code — irrelevant here, the WSI is unix-side inside the ICD.
Effort: 2–3 days WSI + protocol + wcp rebuild (wine-compat-engineer for the packaging).

### (b) Import the game's existing dma-buf as an `AHardwareBuffer`

**No.** There is no public API: `AHardwareBuffer_createFromHandle` exists only in
`vndk/hardware_buffer.h` (exported by `libnativewindow.so`, so `dlsym` finds it), but it takes a
gralloc `native_handle_t` that the vendor mapper (`IMapper::importBuffer`) validates — on QTI a
`private_handle_t` with the `'gmsm'` magic, a metadata dma-buf, and vendor-specific ints. The game's
buffers are bare DMA-heap fds. Fabricating a vendor handle around a foreign fd is unversioned
private ABI (differs across gralloc4 releases/OEMs) and is exactly the kind of thing that
reboots non-Adreno/old SoCs. `AHardwareBuffer_createFromHandle` also requires the caller to be
in the VNDK namespace for its stub; only `dlsym` reaches it. Rejected.

### (c) Compositor-owned pool offered to the game

Compositor allocates AHBs, gets the fd from the native handle, and the game imports them. On the
game side this is the same driver path as (a) minus gralloc (`VK_EXT_external_memory_dma_buf` +
explicit modifier/pitch from the compositor). It **still needs the same WSI patch** (a `create_mem`
that imports instead of exports) and in addition a protocol to carry `fd + modifier + pitch + size`
before the swapchain image is created, plus reallocation on `vkCreateSwapchainKHR` (size changes),
plus fence plumbing in both directions. `zwp_linux_dmabuf_v1` v4 feedback cannot express this (it
only steers format/modifier/device; the client still allocates), and on KGSL v4 is a trap anyway:
the driver has no DRM `dev_t`, so `same_gpu` stays false (`wsi_common_wayland.c:1660-1686`) and
Mesa takes the prime-blit path (one extra copy per frame). The one advantage over (a) — the
compositor decides linear vs UBWC — is moot because gralloc's decision is the right one for the DPU.
Effort: 3–4 days; strictly more moving parts than (a). Not recommended.

**Recommendation: (a).** It reuses two proven mechanisms (Turnip's AHB import, the X11 path's
AHB-over-socket), keeps `zwp_linux_dmabuf_v1` as the fallback transport, and confines the new code
to our own Mesa build plus one small protocol.

## 2. Modifiers / UBWC

- Turnip advertises exactly two modifiers: `DRM_FORMAT_MOD_LINEAR` and
  `DRM_FORMAT_MOD_QCOM_COMPRESSED` (UBWC), the latter whenever the format tiles and
  `ubwc_possible()` (`tu_formats.cc:416-443`); explicit-modifier images accept only these two
  (`:530-575`). `QCOM_COMPRESSED` forces UBWC (`tu_image.cc:723-727`); an explicit plane layout is
  validated by `fdl6_layout_image` and rejected with
  `VK_ERROR_INVALID_DRM_FORMAT_MODIFIER_PLANE_LAYOUT_EXT` if the pitch does not fit
  (`:762-806`). Export/import of both is symmetric (same driver, same `fdl6` layout).
- The compositor advertises `LINEAR` and `INVALID` only (`compositor.c: bind_dmabuf`,
  `uint64_t mods[] = {MOD_LINEAR, MOD_INVALID}`); `INVALID` is dropped by the WSI
  (`wsi_common_wayland.c:449-451`), so every game swapchain is linear today. DXVK/VKD3D render to
  their own tiled/UBWC targets and copy into the linear swapchain image at present — a full-frame
  linear write per frame on the game side, on top of the compositor's blit. This is a real part of
  the 20 %.
- Advertising `QCOM_COMPRESSED` as well: one line in `bind_dmabuf`. The WSI intersects the
  compositor's list with the driver's (`wsi_common_drm.c:686-733`) and Turnip prefers UBWC when
  offered; `vkp_image_from_dmabuf` already passes any modifier through. Two cautions: the pitch
  the game sends is the UBWC pixel-plane pitch (Mesa reports plane 0 of the modifier, `:817-830`)
  and both sides run the same `fdl6` code, so the import must succeed on the same GPU; and a
  UBWC buffer must never be read by the CPU path (`wl_shm` is unaffected). This is independent of
  layers and worth its own A/B (experiment plan step 0).
- For layers the buffer is gralloc's, so UBWC is decided by gralloc: QTI allocates UBWC for
  `GPU_COLOR_OUTPUT` RGBA8 unless a CPU usage bit is set (the prototype exploits exactly that:
  `sc_layer.c: alloc_slot` retries with `CPU_READ_RARELY` when a UBWC import fails). The DPU
  scans out UBWC RGBA natively; a prior device experiment (GL native rendering, 2026-06-29)
  found the SDE pipes accepted both UBWC and linear layers — rotation+scale was the blocker, not
  UBWC.

## 3. Per-layer composition (design; the prototype implements the single-layer subset)

- **wl_surface → SurfaceControl.** One `ASurfaceControl_createFromWindow(surfaceView_window,
  name)` child per mapped toplevel (and per subsurface tree flattened per toplevel); the desktop
  surface is the base layer at z 0 (or, with `g_hide_shell`, a black base — the SurfaceView's own
  buffer); toplevels get `setZOrder` from the `g_toplevels` order (`apply_zorder`), with
  `INT_MAX` reserved for the app's cursor/HUD if they ever move native. Position/scale:
  `ASurfaceTransaction_setGeometry(src = buffer rect, dst = output rect, transform 0)`, with the
  output rect produced by the same `update_map()` / `draw_to_blit()` arithmetic the blit path uses
  (`vk_present.c: vkp_map_draw`) — letterbox/FILL crop/TOP-BOTTOM half all come out identical, so
  **input stays consistent by construction**: the app maps touch through `ViewTransformation`
  with the same inputs (see `WAYLAND_RUNTIME.md` "Fullscreen mode"), and the compositor's own
  `vkp_output_to_scene` keeps working because the map is still updated every frame.
  `setBufferTransparency(OPAQUE)` for XR24/XB24; `setBufferTransform` stays 0 — the DPU refuses
  rotation+scale on one pipe (prior experiment), so a rotated panel must be handled by the
  activity's orientation, not the layer.
- **HUD.** Already a separate layer: `PerfHudView`/`FrameRating`/cursor are Android views in
  `rootView` above `waylandSurfaceView`, i.e. the ViewRoot's SurfaceFlinger layer above the
  SurfaceView and all its child SurfaceControls. Nothing to do; the prototype keeps it.
- **Vsync / frame callbacks.** Keep the app's Choreographer tick (`nativeVsync`) as the pacing
  edge: on each tick the compositor applies **one transaction** containing every surface that
  committed since the last tick (`setBuffer` per changed layer, geometry/z for changed ones), then
  fires `wl_callback.done` for those surfaces (as today, `fire_all_frames`). `wp_presentation`
  feedback moves from "after our present" to the transaction's `ASurfaceTransaction_setOnComplete`
  → `ASurfaceTransactionStats_getLatchTime` / `getPresentFenceFd` (API 29) give the real on-glass
  time — better than today's `now_ns()`; `setFrameTimeline` (API 33) can be added later for
  `VK_GOOGLE_display_timing` work. Without a window, `pace_without_output` stays as is.
- **`wl_buffer.release` timing.** Today a replaced buffer is released on the limiter's cadence,
  which is safe because the blit was waited for. With layers the buffer is on the display until
  SurfaceFlinger says otherwise: keep the buffer un-released until the OnComplete callback of the
  transaction that **replaced** it delivers `ASurfaceTransactionStats_getPreviousReleaseFenceFd`
  (this is what `ASurfaceRendererContext::transactionCompleteCallback` does), wait that fence on
  the compositor thread (or, better, hand it to the game: with option (a) the WSI can import it as
  the `WSI_ES_RELEASE` point — Turnip/KGSL implements `vk_sync.import_sync_file`,
  `tu_knl_kgsl.cc:1261-1303`), then `wl_buffer_send_release`. The FPS limiter keeps working: it only
  delays the release further. Android 16's `setBufferWithRelease` is not available on 14.
- **Acquire fence.** Two sources: (1) if the kernel supports `DMA_BUF_IOCTL_EXPORT_SYNC_FILE`
  (6.x GKI), the WSI already attaches its present semaphore to the dma-buf as a sync_file
  (`wsi_drm_init_swapchain_implicit_sync`, `wsi_common_drm.c:368-395`, gated on the driver
  exporting `SYNC_FD` semaphores — KGSL does, `:1278-1303`), and the compositor can
  `EXPORT_SYNC_FILE(READ)` it and pass the fd to `setBuffer` — the prototype probes this on the
  first frame and logs the answer; (2) otherwise the option-(a) protocol carries a sync_fd per
  present (`vkGetSemaphoreFdKHR`), which is what the X11 wrapper does. Never `-1` for a real
  zero-copy frame.
- **Lifecycle.** SurfaceView destroyed/recreated (background, rotation): `vkp_apply_window_request`
  already serialises window changes on the compositor thread; the layer code retires its
  SurfaceControls (hide + `reparent(NULL)` + release in the OnComplete callback) and recreates
  them on the new window. Buffers stay valid across that (gralloc buffers are process-independent
  refcounted objects), so the first frame after resume is the last committed one.

## 4. Risks

- **HWC fallback to GPU composition** (the whole point is DEVICE composition). Known from the
  2026-06-29 experiments on the Adreno 750 device: a layer with `transform 90` **and** scale went
  CLIENT and dragged the base layer with it; the same layer pre-rotated at panel resolution with
  src == dst was DEVICE on every layer, GPU_TARGET empty. So: no SurfaceControl rotation ever; on
  a landscape-native panel (handheld) the identity-transform game layer with a DPU scale should
  promote; on a portrait phone in landscape it will not, and the base swapchain path is the better
  one there — decide per device by measurement, not by assumption. Measure with
  `dumpsys SurfaceFlinger` (layer `composition: DEVICE/DEVICE` vs `DEVICE/CLIENT`, and the SDE pipe
  table `GPU_TARGET` line) with the app foreground and the drawer closed.
- **Layer count.** The DPU has a handful of pipes (4–8 usable RGB pipes on SDM/SM8xxx); 2–3 game
  layers + base + HUD + cursor is fine, a desktop with 10 windows is not — SurfaceFlinger then
  composites the overflow on the GPU (still correct, just no gain). Policy: layers only for
  fullscreen/topmost windows, everything else stays in the base swapchain (the prototype's rule).
- **Buffer ownership across processes.** The game's `AHardwareBuffer` is alive in the guest; the
  compositor's received copy holds its own gralloc reference, SurfaceFlinger a third. A guest crash
  mid-present leaves SurfaceFlinger with a valid buffer (no black flash). Fds: one dma-buf fd + one
  socket per swapchain image; clean up on `wl_buffer` destroy (today's `dmabuf_buffer_unref`).
- **Fences.** Passing `-1` acquire fences with a buffer the GPU is still writing shows tearing or
  stale content; KGSL has no BO implicit sync, so the sync_file path in §3 is mandatory for the
  real thing (the prototype waits on the CPU instead, which is safe).
- **Background / rotation / device lost.** Covered by the window-request serialisation; a device
  loss in the compositor's Turnip does not affect layers already on screen (SurfaceFlinger owns
  them) — the old path's `device_lost` message stays.
- **Non-Adreno / old SoCs.** Everything here is NDK API 29 + gralloc-allocated buffers; the only
  vendor-specific piece is the `'gmsm'` handle sniff, which degrades to "linear with a CPU bit".
  Same risk class as the X11 ASR renderer; keep it opt-in until proven on a second SoC.
- **`vk_present.c` stays.** Fallback for: no `ASurfaceControl` API, multi-window scenes, popups
  above the game, the first frames before the layer is up, and any device where composition goes
  CLIENT. The prototype switches per frame (`layer_candidate` in `compositor.c`).

## 5. Experiment plan (each step ≤ 1 hour on the device, in order)

Prerequisite: a build of this branch, container on Wayland, the AIO Graphics Test (Vulkan and
D3D12 tabs) as the workload, session log in `Download/Wayland-logs/`.

0. **UBWC modifier A/B (no layers).** Done on `feat/wayland-ubwc` (advertised when the compositor's
   driver imports it, `BANNER_WAYLAND_UBWC=0` = old linear-only list; see `WAYLAND_RUNTIME.md`
   "Compressed (UBWC) game buffers"). Run the AIO Vulkan/D3D12 with and without. Expect
   `vulkan: … is presenting GPU frames through Wayland: WxH, format XB24, tiled (zero-copy)` and
   no `dmabuf import failed`. Records how much of the 20 % is the linear swapchain alone.
1. **Layer mode smoke.** Put `BANNER_WAYLAND_ZERO_COPY=1` in the container's environment
   variables, launch the AIO fullscreen. Expected log lines (tag `layer`):
   `SurfaceControl "banner_wayland_game" created as a child of the screen surface`,
   `pool buffer 1920x1080 UBWC (QCOM_COMPRESSED), stride 1920 px (gralloc handle 2 fds / N ints)`
   (or `linear` after `import of a UBWC … failed` — also a result: it tells whether the compositor
   Turnip accepts gralloc's UBWC pitch), `geometry: buffer 0,0-1920,1080 -> screen …`,
   `presenting 1920x1080 game frames on their own SurfaceControl layer (UBWC pool, 3 buffers)`,
   and the probe line `kernel exports sync_file fences from the game's dma-buf …` or
   `DMA_BUF_IOCTL_EXPORT_SYNC_FILE … failed (…)`. Picture correct, HUD and pointer on top, fullscreen
   mode changes move the layer (`geometry:` lines).
2. **Composition type.** With the AIO running: `dumpsys SurfaceFlinger | grep -A3 banner_wayland_game`
   and the `GPU_TARGET` line of the pipe table. DEVICE/DEVICE on the game layer with an empty GPU
   target = the DPU does the scaling; CLIENT = no gain on this panel orientation → try the
   handheld's native orientation / a resolution equal to the panel's.
3. **Cost of the host copy.** AIO Vulkan fps with the flag on vs off (same cost expected ± noise,
   since the blit only changed its destination) and `dumpsys gfxinfo`/battery for 5 min each. This
   is the baseline the WSI patch is measured against.
4. **Fence probe outcome → choose the acquire-fence design** (§3) before starting the WSI patch.
5. **WSI patch (option a) in banners-turnip-wayland**, wcp rebuild, then the same steps 1–3 with
   the AHB arriving from the guest: log line to add on the compositor side `layer: buffer N of
   "<window>" received from the game (AHardwareBuffer WxH, UBWC)` and the fps delta vs X11.

Work estimates: step 0 = 0.5 day incl. A/B; host layer prototype = done (this branch, ~1 day to
harden: async fence instead of CPU wait, per-window layers, presentation feedback from
`getLatchTime`); option (a) WSI + protocol + wcp = 2–3 days + 1 day device proving; option (c) =
3–4 days + the same proving; (b) = not buildable.

## 6. What the prototype in this branch does (`BANNER_WAYLAND_ZERO_COPY=1`)

Files: `src/sc_layer.{c,h}` (new), `src/vk_present.{c,h}` (`vkp_image_import_dmabuf` with a
blit-destination role, `vkp_blit_image`, `vkp_update_map`, `vkp_map_draw`, `vkp_window`,
`vkp_signal_first_frame`; the layer is retired on window changes), `src/compositor.c`
(`g_zero_copy`, `layer_candidate`, the `render_scene` branch, the dma-buf sync_file probe),
`src/waylandcomp_jni.c` + `WaylandCompositor.java` (`nativeSetZeroCopy`),
`XServerDisplayActivity.startWaylandCompositor` (reads the flag from the container's / shortcut's
environment variables before the compositor starts).

Behaviour: when the topmost draw is a client GPU frame covering the whole scene (one fullscreen
game, nothing above it), the screen swapchain is presented black and the frame is blitted (1:1,
`vkCmdBlitImage`, CPU-waited) into one of three compositor-allocated `AHardwareBuffer`s
(R8G8B8A8, `GPU_COLOR_OUTPUT | GPU_SAMPLED_IMAGE`, imported into the compositor's Turnip through
its native-handle dma-buf fd with the modifier from the `'gmsm'` sniff, linear fallback), which
is set on a child `ASurfaceControl` of the SurfaceView with the fullscreen-mode geometry, z 1,
opaque. `setOnComplete` returns the previous buffer's release fence, which is waited on before
that pool slot is reused; no free slot = frame dropped (logged at most every 5 s). Any other scene
hides the layer and draws the old way; the layer is retired when the SurfaceView goes away.
Everything else (frame callbacks, presentation feedback, FPS limiter, HUD counting, input) is
unchanged. Off by default: with the variable unset no new code runs.

Not zero-copy yet — it is the receive side. The copy disappears when step 5 lands and
`sc_layer_present` is handed the game's own `AHardwareBuffer` instead of a pool slot.

## 7. Step 5 implemented (feat/wayland-zero-copy-wsi + banners-turnip-wayland `banner_ahb_wsi.py`)

Option (a′) as recommended, with one deviation forced by the build: the Wayland Turnip is a
`platforms=wayland` build with Android detection off, so `VK_ANDROID_external_memory_android_hardware_buffer`
/ `vk_android.c` do not exist in it. The WSI therefore allocates the `AHardwareBuffer` itself
(`libnativewindow.so` dlopen'd), sniffs the gralloc handle for UBWC exactly like `u_gralloc_fallback.c`,
and imports `native_handle->data[0]` as a dma-buf with an explicit modifier + pitch — the same path
this compositor's pool uses (`vkp_image_import_dmabuf`) — after a test `vkCreateImage` proves the
driver accepts that layout (linear retry otherwise). Fences: implicit via the dma-buf in both
directions (§3's option 1, proven by the probe; the compositor imports SurfaceFlinger's release
fence back into the dma-buf before `wl_buffer.release`). Details in `WAYLAND_RUNTIME.md`.
