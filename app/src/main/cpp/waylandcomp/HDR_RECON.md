# HDR on the Wayland backend — reconnaissance

> **2026-09-14: round 1 of Phase A is built** (branch `feat/wayland-hdr`, opt-in, testing only) —
> see **§10** at the end for what was built, how it is gated and how a tester's log proves it.
> The recon below is kept as written.

Written 2026-09-13. **Recon only: no product code was changed, nothing was built, nothing was
installed on the device.** Branch surveyed: app `feat/wayland-phase2`, proton-wine
`feat/winewayland-desktop-11.0-2`, Banners-Turnip `wayland` (Mesa `7cda7850`, v26.3.0).
Device reads are `dumpsys` / `getprop` / `strings` only.

Companion docs: `ZERO_COPY_SPIKE.md` (buffer path), `WAYLAND_RUNTIME.md`.

---

## 0. Answer in one paragraph

**The game half of HDR is already shipping and nobody noticed.** The Wayland Turnip inside
wcp v6 (`Proton-11.0-2.1-arm64ec-6/lib/libvulkan_freedreno_wayland.so`, verified on the device)
already contains Mesa's full `wp_color_manager_v1` client implementation and advertises
`VK_EXT_swapchain_colorspace` + `VK_EXT_hdr_metadata`; the shipping DXVK v3.1-gplasync already
carries `dxgi.enableHDR` / `DXVK_HDR` and the `VK_COLOR_SPACE_HDR10_ST2084_EXT` path;
`winewayland.drv` is a transparent 121-line shim that neither adds nor removes colour
information. **Everything that is missing is in our compositor**, which advertises no colour
protocol, offers only four 8-bit DRM formats over `zwp_linux_dmabuf_v1`, takes
`surfaceFormats[0]` for its own swapchain, runs an 8-bit scene/effects chain, and never calls
`ASurfaceTransaction_setBufferDataSpace`. The work is therefore **app-side only** — the memory
note in `project_bannerlator_wayland_phase4` that HDR "does need a new wcp" is **wrong for the
HDR10 (10-bit PQ) path**; a new wcp is only needed for scRGB/FP16 zero-copy. Estimated
**4–6 engineer-days** to a plumbed, log-verifiable HDR10 path. But the Pocket FIT's panel
reports `supportedHdrTypes=[]`, `supportedColorModes=[0]` (NATIVE only), `hdrSdrRatio
not_available` and `displayBrightnessNits=-1`, so **nothing of it can be seen on this device**,
and turning it on there would actively *cost* us the zero-copy DEVICE-composition win. Verdict
in §8: **not now.**

---

## 1. Game side — how far an HDR request gets today

### 1.1 What a Windows game does

| Call | Where it lands in DXVK | Notes |
|---|---|---|
| `IDXGIOutput6::GetDesc1` → `DXGI_OUTPUT_DESC1::ColorSpace` | `src/dxgi/dxgi_output.cpp:220-259` | **Never queries Vulkan.** Reports `RGB_FULL_G2084_NONE_P2020` purely because `dxgi.enableHDR` is on. |
| `IDXGISwapChain3::CheckColorSpaceSupport` | `src/dxgi/dxgi_swapchain.cpp:655-667` | This *is* backed by Vulkan (below). Note it is a swapchain method, not a factory method. |
| `IDXGISwapChain3::SetColorSpace1` | `dxgi_swapchain.cpp:672-688` → `d3d11_swapchain.cpp:306-331` → `Presenter::supportsColorSpace` (`dxvk_presenter.cpp:319-343`) | Returns `E_INVALIDARG` if the Vulkan surface does not list the colour space. |
| `IDXGISwapChain4::SetHDRMetaData` | `dxgi_swapchain.cpp:690-717` → `vkSetHdrMetadataEXT` | Only `DXGI_HDR_METADATA_TYPE_HDR10`. Silently dropped if `VK_EXT_hdr_metadata` is absent (`dxvk_presenter.cpp:143-149`). |

DXGI→Vulkan mapping (`src/d3d11/d3d11_swapchain.cpp:21-28`):

```
DXGI_COLOR_SPACE_RGB_FULL_G2084_NONE_P2020 -> VK_COLOR_SPACE_HDR10_ST2084_EXT
DXGI_COLOR_SPACE_RGB_FULL_G10_NONE_P709    -> VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT  (scRGB)
DXGI_COLOR_SPACE_RGB_FULL_G22_NONE_P709    -> VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
```

Swapchain formats DXVK will accept per colour space (`Presenter::pickFormat`,
`dxvk_presenter.cpp:1043-1081`):

* `HDR10_ST2084` → `A2R10G10B10_UNORM_PACK32`, `A2B10G10R10_UNORM_PACK32`,
  `E5B9G9R9_UFLOAT_PACK32`, `R16G16B16A16_UNORM`, `R16G16B16A16_SFLOAT`
* `EXTENDED_SRGB_LINEAR` (scRGB) → **`R16G16B16A16_SFLOAT` only**

`VK_EXT_hdr_metadata` is optional in both DXVK and vkd3d-proton. `VK_EXT_swapchain_colorspace`
is *not* requested as an instance extension by DXVK's win32 WSI — it simply trusts the driver to
report the extra `VkColorSpaceKHR` values from `vkGetPhysicalDeviceSurfaceFormatsKHR`. Mesa's
Wayland WSI does exactly that (§2.3), so this is not a problem for us.

**vkd3d-proton (D3D12)** ships its own swapchain (`libs/vkd3d/swapchain.c`), same colour-space
mapping (`convert_color_space`, :1717-1730), but **no fallback table**: if the requested
HDR colour space is not in `vkGetPhysicalDeviceSurfaceFormatsKHR`, `dxgi_vk_swap_chain_select_format`
(:1773-1791) returns `false` with the comment *"Refuse to present unsupported HDR since it will
look completely bogus"*, and the caller skips swapchain recreation for that cycle. A D3D12 game
that force-sets HDR without checking can therefore **stall presentation** rather than degrade.
DXGI enumeration for D3D12 titles is still DXVK's `dxgi.dll`, so `DXVK_HDR` still drives
`DXGI_OUTPUT_DESC1`.

**Verified in our shipped binaries** (device, `strings`):
`contents/DXVK/v3.1-gplasync-0/system32/dxgi.dll` contains `dxgi.enableHDR`, `DXVK_HDR`,
`VK_COLOR_SPACE_HDR10_ST2084_EXT`; `d3d11.dll`/`d3d9.dll`/`dxgi.dll` contain
`VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT`; `contents/VKD3D/3.1.0-wave64-relax-1/.../d3d12core.dll`
contains `VK_EXT_hdr_metadata`. Nothing needs rebuilding on the D3D side.

Proton-level gating: GE's `proton` script maps `PROTON_ENABLE_HDR` / `PROTON_USE_HDR` to
`DXVK_HDR=1` and nothing else. For us that is one container env var — no launcher work.

### 1.2 The failure mode today, exactly

DXVK's `DXGI_OUTPUT_DESC1` will claim HDR10 as soon as `DXVK_HDR=1`, regardless of Vulkan.
`SetColorSpace1` then fails with `E_INVALIDARG` because our compositor advertises no colour
management, so Mesa lists only `SRGB_NONLINEAR` + `PASS_THROUGH` (§2.3). Well-behaved games fall
back to SDR. Games that trust `GetDesc1` without checking `CheckColorSpaceSupport` can render an
HDR-authored image into an SDR swapchain (washed out) — this is a pre-existing foot-gun, not
something we introduce. **Recommendation regardless of whether we do HDR: do not expose a
`DXVK_HDR` container toggle until the compositor answers truthfully**, or we invite exactly that
bug class.

### 1.3 `winewayland.drv` — carries nothing for colour, and blocks nothing

`/home/claude-user/proton-wine-wayland2-wt/dlls/winewayland.drv/vulkan.c` is 121 lines:
`wayland_vulkan_surface_create` wraps the `wl_surface` in a `VkSurfaceKHR`,
`wayland_map_instance_extensions` aliases `VK_KHR_win32_surface` ⇄ `VK_KHR_wayland_surface`.
There is **no surface-format or colour-space filtering**, so whatever Mesa advertises reaches
DXVK unmodified. A grep for `color|colour|hdr|st2084|bt2020|gamut|10.?bit` across the whole
driver returns only GDI colour-bitmap helpers — nothing colorimetric.

One real consequence: `wayland_output.c` publishes no EDID, and nothing in the tree writes the
`EDID` registry value `win32u/sysparams.c:815-818` reads. DXVK's
`src/wsi/win32/wsi_monitor_win32.cpp:288-361` therefore finds no EDID and falls back to its dummy
HDR luminances (`wsi_edid.h:38-56`: min 0.01 / max 1499 / maxFullFrame 799 nits). A game will
size its tonemapper for a 1499-nit display. Correctable later by synthesising an EDID in
`winewayland.drv` (that *would* need a new wcp), but not a blocker.

### 1.4 Our bundled Turnip already speaks colour management — verified on the device

```
$ strings .../Proton/11.0-2.1-arm64ec-6/lib/libvulkan_freedreno_wayland.so
wp_color_manager_v1                        PRESENT
wp_color_management_surface_v1             PRESENT
wp_image_description_creator_params_v1     PRESENT
banner_ahb_v1                              PRESENT     (our zero-copy patch)
VK_EXT_swapchain_colorspace                PRESENT
VK_EXT_hdr_metadata                        PRESENT
```

The plain adrenotools driver the compositor itself loads
(`contents/adrenotools/Mesa Turnip v26.3.0-7cda785 (Android + Wayland)/libvulkan_freedreno.so`)
has the same colour-management symbols. Our own WSI patch is already aware of the protocol —
`patches/wayland/banner_ahb_wsi.py` anchors one of its edits on Mesa's
`if (display->color_manager) wp_color_manager_v1_destroy(...)` teardown line.

**And the zero-copy patch already maps 10-bit to gralloc** — `banner_ahb_wsi.py:99-100, 225-237`:

```c
#define BANNER_AHB_FORMAT_R8G8B8A8_UNORM    1u
#define BANNER_AHB_FORMAT_R10G10B10A2_UNORM 0x2bu   /* AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM */
...
case VK_FORMAT_A2B10G10R10_UNORM_PACK32:  return BANNER_AHB_FORMAT_R10G10B10A2_UNORM;
```

So a 10-bit HDR10 swapchain can already be gralloc-backed and go onto the display layer with the
**shipped** wcp. `VK_FORMAT_R16G16B16A16_SFLOAT` is *not* mapped, so `banner_ahb_skip_format`
(:239-242, applied at `:606-617`) hides FP16 surface formats whenever zero-copy is on — scRGB
zero-copy is the one thing that needs a driver change (one enum: `AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT = 0x16`).

### 1.5 X11 has no path — confirmed

`app/src/main/cpp/winlator/VulkanRendererContext.cpp:439` hardcodes
`swapchainFmt = VK_FORMAT_R8G8B8A8_UNORM` and `:492` hardcodes
`imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR`. Upstream of that, the game presents through
`winex11.drv` into our pure-Java X11 server, which has no colour extension of any kind. HDR on X11
would mean inventing a private X extension *and* teaching DXVK to use it. Correctly out of scope.

---

## 2. Wayland side

### 2.1 Protocol status

`wp_color_management_v1` is **merged and stable-ish**: wayland-protocols MR !14, merged
2025-02-13, shipped in **wayland-protocols 1.41**, file
`staging/color-management/color-management-v1.xml`. Interfaces are at **version 3** upstream today
(v2 added `get_image_description`/`preferred_changed2`; v3 added `create_windows_bt2100` and the
`absolute_no_adaptation` intent). Predecessors: `zwp_linux_color_management_v1` →
`xx_color_management_v4` (the name most 2023–2025 shipping code targeted) → `wp_*` on landing.
`frog_color_management_v1` (Valve) is still vendored by gamescope alongside the real one and is
still functionally load-bearing on the Deck.

Implementers: KWin (reference impl, most mature), Mutter (merged 2025-02-25 but still does not
advertise enough features for automatic HDR), wlroots 0.20 / Sway 1.12 (HDR10 on the Vulkan
renderer), gamescope (v1 semantics + frog), Weston (experimental, no tone mapping).
Proton-CachyOS's changelog notes that its `winewayland.drv` `windows_bt2100` path needs protocol
**v3** and that *"no compositor supports it yet"* — everybody is converged on **v1**.

**We only need v1.** That is what Mesa binds.

### 2.2 Minimum viable compositor subset

At bind, on the `wp_color_manager_v1` global, send one event per supported item then `done`:

* `supported_intent`: `perceptual` (mandatory anyway — it is all Mesa ever asks for)
* `supported_feature`: `parametric` (gates `create_parametric_creator`); optionally
  `set_mastering_display_primaries`, and `extended_target_volume` **only if** we want scRGB
* `supported_primaries_named`: `bt2020` (HDR10), `srgb` (needed for scRGB and for Mesa's
  "use a colour surface for sRGB too" branch), `display_p3` (wide gamut)
* `supported_tf_named`: `st2084_pq` (HDR10), `ext_linear` (scRGB), `srgb`

Then implement: `create_parametric_creator` → `wp_image_description_creator_params_v1`
(`set_primaries_named`, `set_tf_named`, `set_mastering_display_primaries`,
`set_mastering_luminance`, `set_max_cll`, `set_max_fall`) → `create` → emit
`wp_image_description_v1.ready` (or `failed`) → `wp_color_manager_v1.get_surface` →
`wp_color_management_surface_v1.set_image_description(desc, intent)`, double-buffered on
`wl_surface.commit`.

**Not needed:** ICC creator, `wp_color_management_surface_feedback_v1`,
`wp_image_description_info_v1`, output image descriptions, v2/v3 requests. Mesa uses none of them.

### 2.3 What Mesa's WSI does — read from the exact commit we build

Read from `src/vulkan/wsi/wsi_common_wayland.c` at Mesa `7cda7850` (4121 lines — the commit
recorded in `banners-turnip-wayland/mesa_hash.txt`). Landed upstream as MR !32038 (Xaver Hugl,
merged 2025-02-25, Mesa 25.1).

* **Bind:** `registry_handle_global` binds `wp_color_manager_v1` at **version 1** unconditionally
  when advertised (`:1536-1538`), and installs `color_manager_listener` (`:1217-1223`).
* **Colour-space list:** `wsi_wl_display_determine_colorspaces` (`:1122-1160`) always adds
  `VK_COLOR_SPACE_SRGB_NONLINEAR_KHR` and `VK_COLOR_SPACE_PASS_THROUGH_EXT`; everything else comes
  from the static `colorspace_mapping[]` table (`:1038-1118`) and is added **only if** the
  compositor advertised that entry's primaries *and* transfer function (and, for entries flagged
  `needs_extended_range`, `feature.extended_target_volume`). Table entries include
  `HDR10_ST2084` (bt2020 + st2084_pq), `EXTENDED_SRGB_LINEAR` (srgb + ext_linear + extended range),
  `DISPLAY_P3_NONLINEAR`, `DISPLAY_P3_LINEAR`, `BT709_LINEAR`, `BT709_NONLINEAR`, `BT2020_LINEAR`,
  `HDR10_HLG`, `ADOBERGB_LINEAR`.
* **Surface formats:** `wsi_wl_surface_get_formats2` (`:2205-2246`) emits the **full cross product** of
  `display.colorspaces` × `display.formats` with **no bit-depth gating**:

  ```c
  u_vector_foreach(cs, &display.colorspaces) {
      u_vector_foreach(disp_fmt, &display.formats) {
          if (!(disp_fmt->flags & WSI_WL_FMT_ALPHA) || !(disp_fmt->flags & WSI_WL_FMT_OPAQUE))
              continue;
          out_fmt->surfaceFormat.format    = disp_fmt->vk_format;
          out_fmt->surfaceFormat.colorSpace = *cs;
      }
  }
  ```

  Consequence: if we advertise BT.2020 + PQ but keep only 8-bit DRM formats, Mesa will happily
  offer `R8G8B8A8_UNORM` + `HDR10_ST2084` and DXVK will **not** take it (`pickFormat`'s HDR10 list
  is 10-bit/16-bit only) — so there is no accidental 8-bit-PQ trap, but there is also no HDR
  unless we also advertise a 10-bit DRM format.
* **Apply:** `wsi_wl_swapchain_update_colorspace` (`:1323-1455`) builds the parametric creator,
  blocks on `ready`/`failed` (it dispatches the queue in a loop), sets mastering primaries /
  luminance / max_cll / max_fall when `should_use_hdr_metadata` is set for that colour space, and
  calls `set_image_description(..., RENDER_INTENT_PERCEPTUAL)`. `is_hdr_metadata_legal`
  (`:1277-1299`) drops illegal combinations rather than risking a protocol error. On `failed`
  without `extended_target_volume` it retries once without metadata.
* **`VK_EXT_hdr_metadata`:** `wsi_wl_swapchain_set_hdr_metadata` (`:1459-1463`) just stashes
  `VkHdrMetadataEXT`; the real work is in `update_colorspace` above.

**Nothing to build in the driver for HDR10.** Mesa is done.

### 2.4 Our compositor advertises nothing colorimetric

Globals (`compositor.c:2527-2538`, plus `ahb_swapchain.c:406`,
`wl_clipboard.c:522-523`, `wl_text_input.c:275`, `wl_toplevel_icon.c:35`):
`wl_compositor` v6, `wl_subcompositor`, `wp_viewporter`, `wl_output` v2, `xdg_wm_base`,
`zwp_linux_dmabuf_v1` **v3**, `wl_seat` v5, `banner_desktop_v1`, `wp_presentation` v2,
pointer constraints, relative pointer, `banner_ahb_v1` v2, data device/control, text input,
toplevel icon. **No `wp_color_manager_v1`.** `protocols/` has no colour-management XML, and
protocol C glue is pre-generated into `generated/` (no `wayland-scanner` at app build time —
`CMakeLists.txt:1-4`), so adding a protocol means generating and committing the files offline.

The dmabuf format table is 8-bit only (`compositor.c:1323-1326`):

```c
#define DMABUF_NFMT 4
static struct { uint32_t fmt; uint64_t mods[DMABUF_NMOD]; int n; } g_dmabuf_fmts[DMABUF_NFMT] = {
    {DRM_ARGB8888}, {DRM_XRGB8888}, {DRM_ABGR8888}, {DRM_XBGR8888}};
```

Good news: the machinery around it is already format-agnostic.
`vkp_dmabuf_modifiers` (`vk_present.c`) takes a DRM fourcc and asks the compositor's own Turnip via
`VkDrmFormatModifierPropertiesListEXT`, and `drm_to_vk` (`vk_present.c:105-123`) **already** maps
`AB30`/`XB30` → `A2B10G10R10_UNORM_PACK32`, `AR30`/`XR30` → `A2R10G10B10_UNORM_PACK32`, and
`AB4H`/`XB4H` → `R16G16B16A16_SFLOAT`. Adding 10-bit is a table edit plus the matching
`FOURCC` defines next to `compositor.c:415-421`.

The compositor's own output swapchain is oblivious (`vk_present.c:301-311, 437-466`): two instance
extensions (`VK_KHR_surface`, `VK_KHR_android_surface` — **no `VK_EXT_swapchain_colorspace`**),
and `VkSurfaceFormatKHR chosen = fmts[0];` (`:441`) — the first format the Android WSI happens to
return, with whatever colour space rides along. The scene image and the whole 13-pass effects
chain are 8-bit (`vk_present.c:54` `SCENE_FMT = VK_FORMAT_R8G8B8A8_UNORM`; `effects_chain.c:29`
`FX_FORMAT = VK_FORMAT_R8G8B8A8_UNORM`).

> **Naming collision, flag it in any UI work.** The drawer already has an "HDR" effect. It is
> `effects_chain.c:197` `P_HDR` / `app/src/main/cpp/winlator/hdr.frag` — a dual-radius bloom +
> contrast lift ported from `HDREffect.java`, `HDRPower = 1.30`, entirely SDR and entirely fake.
> Real HDR must be named something else ("HDR output", "HDR10") or users will conflate them.

### 2.5 Is there a protocol-free shortcut?

No. A `DRM_FORMAT_ABGR2101010` buffer is equally valid as SDR BT.709 or as PQ BT.2020; the format
and modifier describe byte layout, not colorimetry. That ambiguity is precisely why
`wp_color_management_v1` exists. We *could* add a `set_dataspace` request to our own
`banner_ahb_v1` and skip the standard protocol — but Mesa would never call it, so that would need
a driver patch (new wcp) to buy something Mesa already gives us for free. **Implement
`wp_color_manager_v1` v1 server-side; it is strictly cheaper.**

---

## 3. Android side

### 3.1 The real symbols, their API levels, and what the device has

`frameworks/native/include/android/surface_control.h`:

| Symbol | API level | Present in `/system/lib64/libandroid.so` on the Pocket FIT (API 34)? |
|---|---|---|
| `ASurfaceTransaction_setBufferDataSpace(tx, sc, ADataSpace)` | **29** | **PRESENT** |
| `ASurfaceTransaction_setHdrMetadata_smpte2086(tx, sc, AHdrMetadata_smpte2086*)` | **29** | **PRESENT** |
| `ASurfaceTransaction_setHdrMetadata_cta861_3(tx, sc, AHdrMetadata_cta861_3*)` | **29** | **PRESENT** |
| `ASurfaceTransaction_setExtendedRangeBrightness(tx, sc, float cur, float desired)` | **34** | **PRESENT** |
| `ASurfaceTransaction_setDesiredHdrHeadroom(tx, sc, float)` | **35** | absent (expected — device is 34) |
| `ASurfaceTransaction_setLuts` | 36 | n/a |
| `ASurfaceTransaction_setColorSpace` | **does not exist** | — |
| `ASurfaceTransaction_setLuma` | **does not exist** | — |

(Checked with `strings` on the device's own `libandroid.so`; `setEnableBackPressure` and
`setFrameRateWithChangeStrategy` confirmed present alongside, matching what `sc_layer.c` already
dlsyms.)

Our dlsym table is `sc_layer.c:82-97` — 15 entries, **none of them colorimetric**. The additions
needed are `setBufferDataSpace` (the only mandatory one), plus optionally
`setHdrMetadata_smpte2086` / `_cta861_3` and `setExtendedRangeBrightness`. All four are API ≤ 34,
i.e. inside the range `sc_layer.c` already targets, so the existing "missing symbol → path
unavailable" pattern covers older devices unchanged.

### 3.2 Dataspaces we would emit

`ADataSpace` = `STANDARD (63<<16) | TRANSFER (31<<22) | RANGE (7<<27)`, all in the NDK since 29:

| Constant | Value | Maps from |
|---|---|---|
| `ADATASPACE_BT2020_PQ` | 163971072 | `VK_COLOR_SPACE_HDR10_ST2084_EXT` |
| `ADATASPACE_BT2020_HLG` | 168165376 | `VK_COLOR_SPACE_HDR10_HLG_EXT` |
| `ADATASPACE_SCRGB_LINEAR` | 406913024 | `VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT` |
| `ADATASPACE_DISPLAY_P3` | 143261696 | `VK_COLOR_SPACE_DISPLAY_P3_NONLINEAR_EXT` |
| `ADATASPACE_SRGB` | 142671872 | `VK_COLOR_SPACE_SRGB_NONLINEAR_KHR` (today's implicit default) |

Every layer on the device right now reports `dataspace=UNKNOWN (0)`, which SF treats as sRGB.

### 3.3 Buffer formats

`AHARDWAREBUFFER_FORMAT_R10G10B10A2_UNORM = 0x2b` (API 26) ↔ `VK_FORMAT_A2B10G10R10_UNORM_PACK32`
— the normal 10-bit scanout format on Adreno DPUs. QTI gralloc on this device knows it:
`strings /vendor/lib64/libqdMetaData.so` lists `RGBA_1010102`, `RGBX_1010102`, `BGRA_1010102`,
`BGRX_1010102`. `AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT = 0x16` (API 26) exists but is a
compositing intermediate, not normally a scanout format — it was **not** found in the vendor
gralloc strings, so treat FP16 gralloc allocation on this SoC as unproven.
`AHardwareBuffer_isSupported()` (API 29) is the correct runtime probe, and we should use it rather
than assume.

The compositor's own AHB pool is hardcoded 8-bit (`sc_layer.c:302`
`.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM`) and the imported view is built with
`DRM_ABGR8888` (`sc_layer.c:326`). That is the *copy* path; the zero-copy path
(`sc_layer_present_ahb`, `sc_layer.c:430`) takes the game's own buffer and so already carries
whatever format the game allocated — it just never labels it.

### 3.4 What SurfaceFlinger does with an HDR layer on an SDR panel

From `services/surfaceflinger/CompositionEngine/src/Output.cpp`, `getBestDataspace()` /
`pickColorProfile()`:

* A `BT2020_PQ` layer sets `outHdrDataSpace = BT2020_PQ` but the output only *adopts* it when
  `DisplayColorProfile::hasLegacyHdrSupport(BT2020_PQ)` is true — i.e. when the Composer HAL
  reported HDR types for that display. Otherwise the output dataspace stays `DISPLAY_P3` with
  `COLORIMETRIC` intent, and the layer is **tone-mapped, not rejected**.
* Because the HWC for an SDR-only display cannot consume a PQ buffer, the standard
  `validateDisplay()` negotiation marks that layer **CLIENT** instead of **DEVICE**, and
  RenderEngine/Skia composites it with the `libtonemap` SkSL gain shader
  (`libtonemap_LookupTonemapGain`, uniforms `in_libtonemap_displayMaxLuminance` /
  `in_libtonemap_inputMaxLuminance`).
* **This is the trap for us.** Our whole zero-copy win is that `banner_wayland_game` composites
  DEVICE (proven 2026-09-13: DEVICE with ROT_90 + 1.5× scale at 144 Hz, −4 pts GPU busy, −3 %
  power). Tagging that layer `BT2020_PQ` on a panel with no HDR types is very likely to drop it to
  CLIENT — i.e. HDR would **buy a washed-out tone-map and pay for it with the zero-copy saving**.
  Any implementation must gate the dataspace on the *display's* reported HDR types, not on the
  game's request.
* `setExtendedRangeBrightness` is a different mechanism: it applies only to `RANGE_EXTENDED`
  (scRGB) layers and works by having SF dim the *other* layers by `whitePointNits / maxWhitePoint`
  (`renderengine/LayerSettings.h`) while leaving the extended layer undimmed, so the boosted layer
  reads brighter by simultaneous contrast without exceeding the panel ceiling.
  **It needs the display to expose an HDR/SDR ratio.** This panel does not (§4).
* `setDesiredHdrHeadroom` (API 35) is the PQ/HLG equivalent and overrides/`is overridden by`
  `setExtendedRangeBrightness` — last call wins. Not available on the test device at all.

### 3.5 `has_HDR_display` vs `getHdrCapabilities()`

`ro.surface_flinger.has_HDR_display` is a **build-time** declaration (`TARGET_HAS_HDR_DISPLAY`)
that the device *has* a wide-gamut, high-luminance panel; it gates SF-internal colour-management
machinery. `Display.getHdrCapabilities().getSupportedHdrTypes()` is the **runtime, per-display**
answer, built from the Composer HAL's `getHdrCapabilities()` for that connector, itself derived
from the EDID CTA-861.3 Static Metadata Data Block. **They can disagree, and on this device they
do.** An empty list does not mean rejection — it means "tone-map it."

### 3.6 External display

On hotplug, the framework queries `getDisplayConfigs`, `getColorModes`, `getHdrCapabilities`,
`getDisplayCapabilities` for the new display handle exactly as for the internal panel, so
`Display.getHdrCapabilities()` on an external `Display` *is* architecturally wired to reflect the
monitor's EDID. Whether it actually does is a vendor-HWC question; empty lists for genuinely
HDR10 USB-C monitors are a commonly reported Snapdragon gap. **Unproven for the Pocket FIT — the
only way to know is to plug one in.**

---

## 4. What the test device can and cannot show

All read today from the Pocket FIT (Adreno 750, Android 14 / API 34), read-only:

```
DisplayDeviceInfo{"Built-in Screen": 1080 x 1920, ...
  supportedModes [... supportedHdrTypes=[] (all four modes) ],
  colorMode 0, supportedColorModes [0],
  hdrCapabilities HdrCapabilities{mSupportedHdrTypes=[], mMaxLuminance=500.0,
                                  mMaxAverageLuminance=500.0, mMinLuminance=0.0},
  hdrSdrRatio NaN, brightnessMaximum 1.0 ...}
mBaseDisplayInfo: ... hdrSdrRatio not_available, userDisabledHdrTypes []
```

```
dumpsys SurfaceFlinger:
  Wide-Color information:
    Device supports wide color: 1
    Device uses color management: 1
    DisplayColorSetting: Managed
    Display 4630946852602928515 color modes:
        ColorMode::NATIVE (0)
        Current color mode: ColorMode::NATIVE (0)
  colorMode=NATIVE (0) renderIntent=COLORIMETRIC (0) dataspace=UNKNOWN (0) targetDataspace=UNKNOWN (0)
  displayBrightnessNits=-1.000000 sdrWhitePointNits=-1.000000 displayBrightness=nullopt
  (every layer) dataspace=UNKNOWN (0) hdr metadata types=0
```

```
getprop:
  [ro.surface_flinger.has_HDR_display]:        [true]
  [ro.surface_flinger.has_wide_color_display]: [true]
  [ro.surface_flinger.wcg_composition_dataspace]: [143261696]   = ADATASPACE_DISPLAY_P3
  [persist.sys.sf.color_mode]:                 [9]              = ColorMode::DISPLAY_P3
  [vendor.display.enable_hdr10_gpu_target]:    [1]
```

Reading:

* The **build** declares an HDR, wide-colour device and even asks for `DISPLAY_P3` as the system
  colour mode (`persist.sys.sf.color_mode=9`), but the **HWC reports `supportedColorModes=[0]`
  (NATIVE only) and no HDR types**, so SF runs the display in NATIVE with COLORIMETRIC intent and
  the P3 request is inert. The props are aspirational; the HAL is the truth.
* `hdrSdrRatio not_available` / `NaN` and `displayBrightnessNits = -1` mean
  `Display.isHdrSdrRatioAvailable()` is false → **`setExtendedRangeBrightness` has nothing to act
  on here.** The "brighten highlights on an SDR panel" trick is unavailable on this device.
* `supportedColorModes=[0]` also means **Display-P3 wide gamut is not available either** — not
  just HDR. A P3-tagged layer would be colour-converted into the panel's native gamut, i.e. into
  whatever the panel natively is, with no way for us to know what that is.
* 500 nits max luminance is below the ~540-nit bar AOSP's own `has_HDR_display` doc cites, and
  there is no HDR type to signal anyway.

**Conclusion: visual HDR cannot be demonstrated on the Pocket FIT's internal panel, and neither
can wide gamut, and neither can extended-range brightness.** The only things provable on this
device are (a) 10-bit buffers end to end (banding reduction, measurable), and (b) plumbing
correctness by logs and `dumpsys`.

Vulkan-side, for completeness (device `strings`):

| Library | `VK_EXT_swapchain_colorspace` | `VK_EXT_hdr_metadata` |
|---|---|---|
| `/system/lib64/libvulkan.so` (platform loader — implements the Android swapchain) | **PRESENT** | **PRESENT** |
| `/vendor/lib64/hw/vulkan.adreno.so` (Qualcomm blob) | absent | absent |
| `/vendor/lib64/hw/vulkan.turnip.so` (vendor-shipped Turnip) | **PRESENT** | **PRESENT** |
| wcp v6 `libvulkan_freedreno_wayland.so` (game driver) | **PRESENT** | **PRESENT** |

The platform loader contains the string `native_window_set_buffers_data_space(%d) failed: %s (%d)`
— i.e. Android's own swapchain implementation is what turns a `VkColorSpaceKHR` into a surface
dataspace. So for the **copy path**, the compositor's output HDR is genuinely just "enable
`VK_EXT_swapchain_colorspace` at instance creation and pick a better surface format than
`fmts[0]`". No driver work.

---

## 5. The gap list, ordered, with sizes

Sizes are engineer-days for someone who already knows this code. "Prove" = device-visible in logs
or `dumpsys`, not by eye. **App-only unless marked.**

### Phase A — make the truth reachable (no user-visible HDR yet)

| # | Repo | Work | Size |
|---|---|---|---|
| A1 | app | **Advertise 10-bit DRM formats over `zwp_linux_dmabuf_v1`.** Add `AB30`/`XB30` (and optionally `AR30`/`XR30`, `AB4H`/`XB4H`) to `compositor.c:1323-1326` + `FOURCC` defines near `:415-421`. `vkp_dmabuf_modifiers` and `drm_to_vk` already handle them, so this is a table edit + a log line. Risk: a client picking a 10-bit format while the scene/effects chain stays 8-bit — the import works (`drm_to_vk`), the blit downconverts. Gate behind a flag until A4. | **0.5 d** |
| A2 | app | **Generate + commit `color-management-v1` protocol glue.** Fetch `staging/color-management/color-management-v1.xml` from wayland-protocols ≥ 1.41, run `wayland-scanner` offline, commit XML + `generated/color-management-v1-{protocol.c,server-protocol.h}`, add to `CMakeLists.txt`. Mechanical. | **0.5 d** |
| A3 | app | **Implement `wp_color_manager_v1` v1 server-side** (new `wl_color_mgmt.c`, ~400–500 lines): the global + bind-time feature/intent/primaries/tf events; the parametric creator with `set_primaries_named`/`set_tf_named`/`set_max_cll`/`set_max_fall`/`set_mastering_*`; image-description objects with `ready`/`failed`; `get_surface` → `wp_color_management_surface_v1` and `set_image_description` stored double-buffered on our `struct surface`. **Watch the blocking loop:** Mesa dispatches its queue waiting for `ready`, so we must send `ready` from the request handler's own flush, not defer it to the next frame, or a game stalls at swapchain creation. Advertise only what the *current output* can actually honour (see A5). | **2 d** |
| A4 | app | **Carry the image description onto the Android layer.** dlsym `ASurfaceTransaction_setBufferDataSpace` (+ `setHdrMetadata_smpte2086` / `_cta861_3`) in `sc_layer.c:82-97`; map surface image description → `ADataSpace` + `AHdrMetadata_*`; apply in `sc_layer_present_ahb` (`sc_layer.c:430-470`, next to the existing `setBuffer`). Plumb the per-surface description from `compositor.c` through `ahb_swapchain.c:219`. Also set an explicit `ADATASPACE_SRGB` on the existing 8-bit path so we stop relying on `UNKNOWN`. | **1 d** |
| A5 | app | **Capability gate.** Read `Display.getHdrCapabilities()` / `getSupportedHdrTypes()` / `isHdrSdrRatioAvailable()` (and the external `Display` when `ExternalDisplayController` has one) in Java, pass down over JNI, and *only then* advertise `bt2020`/`st2084_pq` from A3. Without this we advertise HDR on a panel that will tone-map it and drop the layer to CLIENT (§3.4). Include a `banner_log("color", ...)` line naming exactly what was advertised and why. | **0.5 d** |

**Phase A total: ~4.5 days. App-only. No new wcp.** End state: a game with `DXVK_HDR=1` on a
genuinely HDR display gets an HDR10 swapchain, its buffer reaches an `ASurfaceControl` layer
tagged `BT2020_PQ` with real mastering metadata, and `dumpsys SurfaceFlinger` proves it.

### Phase B — the copy path and the effects chain (only if A is kept)

| # | Repo | Work | Size |
|---|---|---|---|
| B1 | app | **10-bit output swapchain.** Add `VK_EXT_swapchain_colorspace` to `vk_present.c:301-311` (`enabledExtensionCount` 2 → 3), replace `chosen = fmts[0]` (`:441`) with a real preference walk (HDR10 + `A2B10G10R10` when the display supports it, else `R8G8B8A8_UNORM` + sRGB). The Android platform loader does the rest. | **0.5 d** |
| B2 | app | **Widen the scene + effects chain.** `SCENE_FMT` (`vk_present.c:54`) and `FX_FORMAT` (`effects_chain.c:29`) → `A2B10G10R10_UNORM_PACK32` (or FP16 for real HDR headroom). Every one of the 13 passes samples/writes it; the debanding pass in particular must be re-tuned because its strength is calibrated for 8-bit quantisation. Memory and bandwidth go up ~25 % (or 100 % for FP16). This is the expensive item and it regresses SDR perf if done unconditionally. | **2 d** |
| B3 | app | **Layer pool at 10-bit.** `sc_layer.c:302` pool format → `R10G10B10A2_UNORM` when the chain is 10-bit, guarded by `AHardwareBuffer_isSupported()`, with the existing `sniff_modifier`/linear-fallback retry extended. | **0.5 d** |

**Phase B total: ~3 days. App-only. No new wcp.**

### Phase C — scRGB, and the one thing that needs a wcp

| # | Repo | Work | Size |
|---|---|---|---|
| C1 | **driver (new wcp)** | Map `VK_FORMAT_R16G16B16A16_SFLOAT` → `AHARDWAREBUFFER_FORMAT_R16G16B16A16_FLOAT` (0x16) in `banner_ahb_format_for` (`patches/wayland/banner_ahb_wsi.py:225-237`) so FP16 swapchains are not hidden by `banner_ahb_skip_format` when zero-copy is on. One enum + a rebuild of eight drivers. **This is the only wcp-requiring item, and only for scRGB zero-copy.** Blocked on proving FP16 gralloc allocation works on Adreno at all (§3.3). | **0.5 d + a driver build cycle** |
| C2 | app | Advertise `extended_target_volume` + `ext_linear` + `AB4H`/`XB4H`, emit `ADATASPACE_SCRGB_LINEAR`, and wire `ASurfaceTransaction_setExtendedRangeBrightness` from `Display.getHdrSdrRatio()`. | **1 d** |
| C3 | **proton-wine (new wcp)** | *Optional, quality only.* Synthesise an EDID with a CTA-861.3 HDR static metadata block in `winewayland.drv` so DXVK reports the real panel luminance in `DXGI_OUTPUT_DESC1` instead of its 1499-nit dummy. | **1–2 d** |

---

## 6. Cheap partial wins — and which are visible on a 500-nit panel

| Idea | Verdict on the Pocket FIT | Notes |
|---|---|---|
| **10-bit output (banding)** | **Visible, and the best value here.** | This is the one real win. B1 alone (0.5 d) gets a 10-bit output swapchain; A1 (0.5 d) lets the *game* render 10-bit. Adreno 750 + the DPU handle `RGBA_1010102` natively, and `vendor.display.enable_hdr10_gpu_target=1` says the GPU target path expects it. Pairs with the existing debanding pass — arguably makes it unnecessary for 10-bit-capable games, which saves a full-screen pass. Visible on gradients (skyboxes, fog, dark interiors) where our deband pass currently earns its keep. |
| **Display-P3 wide gamut on SDR** | **Not visible. Do not build.** | `supportedColorModes=[0]` / `ColorMode::NATIVE` — the HWC exposes no P3 mode. `persist.sys.sf.color_mode=9` asks for P3 and is ignored. Tagging a layer `ADATASPACE_DISPLAY_P3` would make SF convert P3 → the panel's native gamut, which on a NATIVE-mode display is an unknown-to-us transform. Net effect on this device: at best nothing, at worst a colour shift. |
| **`setExtendedRangeBrightness` to lift highlights** | **Not available.** | `hdrSdrRatio not_available` / `NaN`, `displayBrightnessNits=-1`, `sdrWhitePointNits=-1`. `Display.isHdrSdrRatioAvailable()` is false, so there is no headroom to claim and SF has no nits data to dim the other layers against. |
| **`setDesiredHdrHeadroom`** | **Not available.** | API 35; device is API 34. Absent from `libandroid.so`. |
| **Set `ADATASPACE_SRGB` explicitly on our layers** | **Invisible but worth doing anyway (part of A4, ~0 extra cost).** | Every layer currently reports `dataspace=UNKNOWN (0)`. It works today because SF treats UNKNOWN as sRGB, but it is an accident we are relying on, and it will become wrong the moment any colour management is enabled. |

**So: of the three "cheap wins" in the brief, exactly one — 10-bit — is real on this hardware,
and it is worth about 1 engineer-day (A1 + B1) on its own, independent of HDR.**

---

## 7. How we would ever prove it

### 7.1 Which games actually emit HDR under DXVK / VKD3D

Requirements: a flip-model swapchain at `R10G10B10A2_UNORM` or `R16G16B16A16_FLOAT`, an
`IDXGISwapChain3::SetColorSpace1` call, and `DXVK_HDR=1` in the environment. Known-good
candidates, easiest first:

* **D3D11 + scRGB:** *Shadow of the Tomb Raider*, *Resident Evil 2/3 Remake*, *Devil May Cry 5*,
  *Mass Effect Legendary Edition*. RE Engine titles are the usual smoke test on Linux because they
  call `SetColorSpace1` early and unconditionally when DXGI reports HDR.
* **D3D12 + HDR10:** *Cyberpunk 2077*, *Forza Horizon 5*, *Horizon Zero Dawn*. These go through
  vkd3d-proton's stricter path (§1.1) — good for catching a half-done implementation, since a
  wrong answer **stalls presentation** instead of degrading.
* **Sanity harness, no game needed:** anything that calls `vkGetPhysicalDeviceSurfaceFormatsKHR`
  under the layer. `vulkaninfo` is not available in the prefix, but a tiny Win32 Vulkan probe
  built against the layer's headers, or simply DXVK's own `VK_INSTANCE_LAYERS`-free log with
  `DXVK_LOG_LEVEL=info` and `DXVK_HDR=1`, will say whether the colour space was accepted. **Start
  here** — it is the cheapest signal and needs no HDR display at all.

### 7.2 Platform-side verification (not by eye)

Order of proof, each step gating the next:

1. **Compositor log.** New `banner_log("color", ...)` lines: which primaries/tf we advertised and
   why; every image description created (primaries, tf, max_cll, max_fall); every dataspace set on
   the layer. This is the only step that works on any device.
2. **Guest log.** `DXVK_HDR=1` + `DXVK_LOG_LEVEL=info` → DXVK prints the chosen colour space and
   format at swapchain creation. If `SetColorSpace1` was refused, DXVK says so. Mesa's WSI prints
   `Not using HDR metadata to avoid protocol errors` when `is_hdr_metadata_legal` rejects metadata
   — catching that string is a direct test of A3's metadata plumbing.
3. **`dumpsys SurfaceFlinger | grep banner_wayland_game`.** Today it prints
   `dataspace=UNKNOWN (0) hdr metadata types=0`. Success = `dataspace:0x9c60000` (`BT2020_PQ`, 163971072 =
   `STANDARD_BT2020|TRANSFER_ST2084|RANGE_FULL`) and `hdr metadata types=` non-zero. **Also record `composition:` on the same line**
   — if it flips DEVICE → CLIENT, HDR cost us the zero-copy win (§3.4) and that is a failure, not a
   success.
4. **`dumpsys display`.** On whatever display is in use: `supportedHdrTypes=[...]` non-empty,
   `colorMode` ≠ 0, `hdrSdrRatio` ≠ `not_available`. This is the gate for steps 5–6; on the Pocket
   FIT's internal panel it will never pass.
5. **`dumpsys SurfaceFlinger` Wide-Color section.** `Current color mode` should move off
   `ColorMode::NATIVE (0)`, and the output line's `dataspace=`/`targetDataspace=` should stop being
   `UNKNOWN (0)`.
6. **Only then, by eye.**

### 7.3 Getting a display that can actually show it — three options, ranked

1. **External HDR display over USB-C (recommended, cheapest).** The app already reparents the game
   into a `Presentation` on a secondary display —
   `app/src/main/java/com/winhub/hernandez/display/ExternalDisplayController.java`,
   `findPresentationDisplay()` picks a `DISPLAY_CATEGORY_PRESENTATION` display that is not the
   activity's own. In the Wayland session the compositor's `SurfaceView` is the reparented view, so
   `sc_layer.c`'s `ASurfaceControl_createFromWindow(vkp_window(), ...)` follows it automatically —
   **no new plumbing to test on an external panel.** Risk: `Display.getHdrCapabilities()` for a
   DP-alt-mode display depends on the vendor HWC reading the external EDID, and empty lists for
   genuinely HDR10 monitors are a common Snapdragon gap (§3.6). **Step zero is a five-minute,
   zero-code test: plug an HDR monitor in and run `dumpsys display | grep -A2 hdrCapabilities`.**
   If the external display reports HDR types, everything else follows. If it does not, options 2
   and 3 are all that remain.
2. **The user's Galaxy Fold (Adreno 840).** Samsung foldables/flagships do report HDR10+ on the
   internal panel, so this is the most likely device to show real HDR with no extra hardware. But
   the Fold is *already* the outstanding unknown for basic Wayland bring-up (checkpoint item 1:
   "Other GPUs… the Galaxy Fold (A840) logs are still outstanding"). Chasing HDR there before
   plain Wayland is proven there inverts the priority order.
3. **Plumbing-only verification on SDR.** Everything in §7.2 steps 1–3 works on the Pocket FIT.
   This proves the code is *correct* but proves nothing about whether it *looks right* — and
   tone-mapping quality is the entire user-visible payload of HDR. **Do not ship on this evidence
   alone.**

---

## 8. Verdict

**Not now. Do the 10-bit slice; park HDR behind the Fold.**

The reasoning, plainly:

* **The effort is genuinely small and smaller than we thought.** ~4.5 days to a plumbed HDR10
  path, **app-only, no new wcp** — because Mesa's colour-management client is already inside the
  shipped wcp v6 driver and our zero-copy WSI patch already maps 10-bit to gralloc. The phase-4
  memory note saying HDR "does need a new wcp" should be corrected: only scRGB/FP16 zero-copy (C1)
  does, and that is optional.
* **But the payload is zero on the only device we can test on.** The Pocket FIT reports no HDR
  types, no colour modes but NATIVE, no HDR/SDR ratio and no brightness nits. We could not see the
  feature, could not tune the tone-mapping, and could not tell a correct implementation from a
  subtly wrong one. Shipping HDR proven only by log lines would be the exact opposite of this
  project's "device-proven, not CI-green" rule.
* **On this device it would be a net negative if enabled.** §3.4: an HDR-dataspace layer on a
  display with no HDR types drops from DEVICE to CLIENT composition. That trades the measured
  zero-copy win (−4 pts GPU busy, −3 % power) for a GPU tone-map pass and a washed-out image. A
  capability gate (A5) prevents the harm, but a gate that is always closed on our test hardware is
  a feature nobody can exercise.
* **It is also out of order.** The pre-4 checkpoint's ranked list puts "other GPUs" and "prove
  zero-copy where it can win, then make it the default" first, with multi-layer presentation in
  flight. HDR competes with those for the same `sc_layer.c` surface area — and multi-layer will
  rewrite exactly the code A4 touches. Landing HDR into `sc_layer.c` now means merging it twice.

**What the user would actually see for the effort, honestly:**

* **Today, Pocket FIT, full HDR (7.5 days):** nothing. Identical picture, plus a `dumpsys` line
  that says `BT2020_PQ`, plus a risk of losing zero-copy.
* **Today, Pocket FIT, the 10-bit slice only (A1 + B1, ~1 day):** *slightly* smoother gradients in
  dark scenes and skyboxes for games that offer a 10-bit swapchain, and a plausible route to
  retiring the debanding pass (one fewer fullscreen pass) for those games. Small, real, and it is
  the foundation Phase A sits on anyway — so it is not wasted work if HDR later goes ahead.
* **Later, on an HDR display (external monitor or the Fold), full Phase A:** genuinely brighter
  highlights and a wider gamut in the ~15 PC titles that implement HDR properly, on the one Android
  emulator that can do it. That is a real headline feature and a real differentiator — but only
  once there is a panel to show it on.

**Recommended sequence:**

1. **Five minutes, zero code:** plug an HDR monitor into the Pocket FIT's USB-C and run
   `dumpsys display | grep -A2 hdrCapabilities`. This single reading decides everything —
   it is the difference between "we can develop and prove HDR today on hardware we own" and
   "HDR is blocked on the Fold."
2. **~1 day, independent of HDR:** the 10-bit slice (A1 + B1), gated and A/B'd for banding and
   perf. Ship it as a banding/quality improvement on its own merits.
3. **Park Phase A (A2–A5)** until either step 1 comes back positive or the Fold is in the loop
   and plain Wayland is proven there. Revisit *after* multi-layer presentation lands, so the
   `sc_layer.c` dataspace work is written once against the final layer API.

---

## 9. What I could not establish, and why

* **Whether FP16 (`R16G16B16A16_FLOAT`) AHardwareBuffers are allocatable on this SoC.** Not in the
  vendor gralloc strings, and I did not install or run a probe binary (other agents are testing on
  the device). Decides whether scRGB zero-copy (C1) is possible at all. Cheap to settle later with
  a one-call `AHardwareBuffer_isSupported()` check inside the compositor's existing startup log.
* **Whether an external HDR display over USB-C actually reports HDR types on the Pocket FIT.**
  No external display was connected and I did not connect one. This is the single highest-value
  unknown in the whole report (§8, step 1).
* **Whether an HDR-tagged layer really drops DEVICE → CLIENT on this HWC.** The AOSP code path is
  clear (§3.4) but it is vendor-HWC behaviour and I did not test it — testing it would mean
  launching a game, which was out of scope.
* **What the Galaxy Fold (Adreno 840, Android version unknown to me) reports.** Not connected.
  Its `getHdrCapabilities()` and `isHdrSdrRatioAvailable()` are the other decisive readings.
* **Exact Mesa line numbers.** Cited against `wsi_common_wayland.c` fetched from
  `gitlab.freedesktop.org/mesa/mesa` at `7cda7850edd103ace21aac37d416d2fdf7a282e1`, the commit in
  `banners-turnip-wayland/mesa_hash.txt`. They are accurate for that file; they will drift if the
  Turnip base moves.
* **DXVK/vkd3d-proton line numbers** are cited against upstream `doitsujin/dxvk@8759acd1` and
  `HansKristian-Work/vkd3d-proton@2a38e799` (master as of today), not against the exact
  v3.1-gplasync / 3.1.0-wave64-relax builds we ship. The feature presence in *our* builds was
  verified independently by `strings` on the device (§1.1); the line numbers are for orientation.
* **Whether any of the games in §7.1 launch and run correctly under our Wayland backend at all.**
  Untested. Picking the HDR test title is its own small exercise and should start from games
  already known to work in a Wayland container.

---

## 10. Round 1 — as built (2026-09-14, `feat/wayland-hdr`)

Phase A (A1–A5) for HDR10, opt-in, proven by the app's own logs because the first HDR panel in the
loop is a tester's non-rooted Galaxy Fold (Adreno 840, Android API 37: HDR10/HLG/HDR10+, 1351 nits,
HDR/SDR ratio available). App-only; no layer change.

### 10.1 The gate (`wl_color_mgmt.c`, `banner_color_init`)

Open only when ALL hold when the compositor starts:

| Input | Where it comes from |
|---|---|
| `BANNER_WAYLAND_HDR=1` (or `true`/`on`) | container or shortcut env (`XServerDisplayActivity.resolvedWaylandHdrMode`) |
| the game's display lists **HDR10** | `DisplayHdrInfo.supportsHdr10` for `hdrTargetDisplay()` (the TV when the game is on it) |
| display layers with dataspace control | `sc_layer_can_tag_hdr()` = SurfaceControl API + `ASurfaceTransaction_setBufferDataSpace` |
| the zero-copy global | `ahb_swapchain_advertised()` |

`BANNER_WAYLAND_HDR=force` skips the display row only (testing the negotiation on an SDR panel —
SurfaceFlinger then tone-maps the layer). A **closed** gate advertises nothing (no
`wp_color_manager_v1` global, no 10-bit dma-buf rows), writes `color … HDR gate CLOSED: <why>` and
`HDR on screen: no, because <why>`, and the session is otherwise byte-for-byte the old one. With the
switch unset the module is silent unless the display lists HDR10 or `DXVK_HDR=1` is set (one hint line).
On an HDR10 display the app also turns **zero-copy presentation on** for the session (an HDR frame is
only right on the game's own layer) and exports `BANNER_WSI_AHB=1` to match.

### 10.2 What an open gate offers (§2.2, exactly what Mesa binds)

`wp_color_manager_v1` **version 1**: intent `perceptual`; features `parametric` +
`set_mastering_display_primaries` (Mesa only sends mastering luminance inside that feature's branch);
primaries `bt2020`; transfer `st2084_pq`. Not advertised: `srgb` (so Mesa creates no colour surface for
SDR swapchains at all), HLG (no DXGI swapchain uses it), `ext_linear`/`extended_target_volume` (scRGB =
Phase C, needs FP16 gralloc + a layer change), ICC, `windows_scrgb`. `zwp_linux_dmabuf_v1` gains
`AB30`/`XB30` (A2B10G10R10 — Mesa lists a VkFormat only when both the alpha and opaque fourcc are there),
appended after the four 8-bit rows. Protocol glue generated from wayland-protocols **1.41** with
wayland-scanner 1.24.0 (`protocols/color-management-v1.xml`).

Strictness: protocol errors only where a conforming client can never hit them (un-advertised
feature/intent/primaries/tf, a property set twice, an incomplete set, a not-ready description).
Everything a conforming client can produce on a bad day is logged and absorbed instead — out-of-range
HDR metadata is dropped from what Android gets (and `max_cll`/`max_fall` of 0 = "unknown", which Mesa
sends as-is), a second colour surface for the same `wl_surface` replaces the first, requests on inert
objects are ignored. `ready` is sent from inside `create` (Mesa blocks its swapchain on it).
Output and surface-feedback image descriptions answer `failed` (Mesa uses neither).

### 10.3 The layer path (`sc_layer.c`)

`setBufferDataSpace` (+ `setHdrMetadata_smpte2086` / `_cta861_3` when present) ride the same
transaction as the buffer, per layer, only on change, re-sent on every new SurfaceControl (recovery
swap, window change). A layer that was never tagged is never touched; once tagged, an untagged frame
puts it back to `UNKNOWN` and clears the metadata. The zero-copy frame gets the surface's current
description; the 8-bit pool copy of an HDR frame keeps the PQ tag (colours right, 8-bit precision,
logged once); the effects pass and the overlay layer are always untagged.

### 10.4 When an HDR frame cannot be zero-copy — round 1's decision

No tone-mapping anywhere (the copy path is `vkCmdBlitImage` into an 8-bit sRGB swapchain; a PQ→SDR
shader pass is its own project). Instead:

- **Effects on / frame generation on:** an HDR fullscreen game **keeps its display layer**; the
  effects chain and frame generation are skipped for it (both are 8-bit SDR and would wash it out),
  and the base surface's black frame skips them too (`vkp_render_plain`) so generated presents cannot
  pace the loop. Logged when it starts and when it ends.
- **A window above the game on a display that cannot compose a second layer** (phase 5's
  rotated+scaled rule is untouched), **a windowed HDR game**, **zero-copy switched off**, **layer
  unavailable:** the scene goes through the copy path and the HDR frames are shown **untone-mapped
  (washed out)**; every such scene is counted and the reason logged.

### 10.5 Proof without root — what a tester's logs say

`Download/Wayland-logs/wayland-*.log`, tag `color` (plus the existing `display` capability line):

| Line | Means |
|---|---|
| `HDR gate OPEN: … "Built-in Screen" … reports HDR types HDR10, …` | the offer is on, with its inputs and which Android colour calls exist |
| `<program> bound wp_color_manager_v1 version 1 …` | Mesa saw it: `VK_COLOR_SPACE_HDR10_ST2084_EXT` is now listed |
| `<window>: colour-management surface created` | a swapchain asked for a non-sRGB colour space |
| `image description #N from <program>: BT.2020, ST 2084 (PQ); mastering …; max CLL …, max FALL …` | the game's colour space + `vkSetHdrMetadataEXT` values |
| `<window> presents WxH buffers in XB30 (10-bit A2B10G10R10) …, gralloc RGBA1010102 (10-bit)` | the 10-bit swapchain really arrived |
| `banner_wayland_game: dataspace BT2020_PQ (0x9c60000) set on the display layer …` | the tag + metadata SurfaceFlinger received |
| `HDR last 10 s: N frames on the display layer tagged BT2020_PQ …` | steady state |
| `display HDR/SDR ratio X (was Y; HDR frames on screen: yes …)` | `Display.getHdrSdrRatio()` — rises above 1.00 only when Android grants HDR headroom |
| `HDR on screen: yes / tagged but NOT confirmed / no, because …` | the verdict (written on changes, at game exit and at session end; the last one counts) |

DXVK's own `<exe>_dxgi.log` / `<exe>_d3d11.log` add `Color space: VK_COLOR_SPACE_HDR10_ST2084_EXT` and
the 10-bit format at swapchain creation; Mesa's `Not using HDR metadata to avoid protocol errors`
(stderr → `wine_debug.log`) means the game's metadata failed Mesa's own legality check.

## 11. Round 2 — as built (2026-09-14, `feat/wayland-hdr`; CI only, not device-proven yet)

Round 1 is proven on the tester's Fold (God of War, DXVK v3.1: 10-bit zero-copy, `BT2020_PQ` on the
layer, HDR/SDR ratio 1.00 → 2.51, `HDR on screen: yes`). Round 2 makes it a setting and removes
round 1's big limitation (§10.4: no tone-mapping, effects/frame generation skipped, washed-out copies).
The gate itself is unchanged (§10.1) apart from its first input.

### 11.1 The setting, and what the launch does with it

- `Container.isWaylandHdr()` (extraData `waylandHdr`) and the shortcut extra `waylandHdr` (`1`/`0`/unset
  = the container's) — one resolver, `display/WaylandHdr.effective()`, used by the three editors
  (container, game shortcut, XMB game settings) and the launch path. `BANNER_WAYLAND_HDR` in the
  environment still overrides it (`1` / `0` / `force`). Editors grey the row with the reason when the
  built-in screen does not list HDR10 (`WaylandHdr.unavailableReason`), and say it applies from the
  next launch.
- HDR on for the session (setting resolved on AND HDR10 display, or `force`): `DXVK_HDR=1` is exported
  after the user's env merge unless the user set `DXVK_HDR` themselves.
- Every Wayland session whose display lists **HDR10** exports `BANNER_WAYLAND_HDR_MAX_NITS`,
  `…_MAX_AVG_NITS`, `…_MIN_NITS` (decimal nits from `Display.getHdrCapabilities()`; unknown values and
  a max of 0 are left out) — the contract the Wayland layer from versionCode 10 turns into EDID HDR
  metadata for DXGI (checked by the Wine side against DXVK's own EDID parser: the Fold's 1351/1351/0 →
  DXGI max 1345.43, max full-frame 1345.43, min 0.01). Never on an SDR display, whose EDID would then
  claim PQ support. One `session environment:` line logs them. Android has no live absolute nits; the
  live evidence stays the HDR/SDR ratio.
- A "DXVK before 3.0 needs layer v10" warning was built and then **removed**: its premise was wrong
  (Wine already exposes `VK_EXT_swapchain_colorspace` at instance level; DXVK ≥ 2.1 looks for it in the
  device list, logs 0 on every Mesa system — the working v3.1 run logs 0 too — and never uses it for the
  HDR decision). Why God of War offered no HDR option on DXVK 2.4.1 is open (Fold A/B).

### 11.2 HDR-aware composition (`hdr_compose.c`, `hdr_encode.frag`, compositor.c `hdr_plan`)

Every scene with an HDR draw takes one of four routes (logged on each change, with the reason):

| Route | When | What reaches the display |
|---|---|---|
| 0 | the HDR game alone fullscreen, zero-copy on, no effects, nothing above it (or one window the overlay layer can carry) | the game's own 10-bit gralloc frames on its layer, `BT2020_PQ` (round 1) |
| 1 | effects on, a window above on a display that cannot take a second layer, a windowed game, zero-copy off | **the HDR picture**: every draw blitted 1:1 into a 10-bit "mixed" image (each pixel still in its own encoding), ONE encode pass to PQ BT.2020 (SDR pixels sRGB → linear → BT.2020, placed at 203 nits, `BANNER_WAYLAND_HDR_SDR_NITS` overrides), the effects chain run on it in 10-bit (`vkp_effects_set_formats`), copied into a 10-bit (`RGBA1010102`) layer buffer, tagged with the game's description; 8-bit buffers if gralloc refuses 10-bit (logged) |
| 2 | frame generation on | composed into PQ in the 8-bit scene image, through the frame-generation engine, presented through an **HDR10 swapchain** (`VK_EXT_swapchain_colorspace` enabled on the compositor instance in HDR sessions only; first `A2B10G10R10` else FP16 pair the surface lists with `HDR10_ST2084`); where the surface lists none, **tone-mapped** into the ordinary swapchain. 8 bits through the engine: gradients may band |
| 3 | an HDR frame the compositor could not import | layer only; effects/frame generation skipped for it (round 1's behaviour, now the rare case) |

The tone map (mode 1 of `hdr_encode.frag`): PQ → nits → BT.2020 → BT.709, relative to SDR white, maxRGB
curve linear to a 0.8 knee then an exponential roll-off that puts the content peak (the game's max CLL,
else mastering max, else 1000 nits) at 98 %, sRGB-encoded. Which pixels are HDR comes from up to six
top-first scene rects + a mask in the push constants (more windows over the game → the lowest are
treated as SDR, logged once). A composed frame that cannot reach the layer falls back to route 2 for as
long as that lasts (logged once each way).

### 11.3 The live switch (lead's addition): drawer → Graphics → "HDR output"

Shown only while the gate is open (the activity's 1 s HDR sampler flips `waylandHdrAvailable`). Per
session, starts on, nothing saved. Off (`banner_color_set_output(0)`, posted through the host queue
to the compositor thread, logged, redraw): route 0 becomes route 1 with the tone map, the picture goes
on the layer **untagged** on 8-bit buffers (the layer's dataspace returns to `UNKNOWN`); route 2
rebuilds the swapchain as SDR and tone-maps; route 3 cannot (nothing can read those frames) and stays
HDR, said in the log. The game is told nothing (its DXVK_HDR and colour-manager offer were decided at
launch). The HUD reads `Wayland · HDR` while HDR frames are on screen (tagged in the last 1.5 s and
ratio > 1.01 where reported), `Wayland · HDR off` while the switch is off. Frames tone-mapped by the
switch are counted separately in the verdict.

### 11.4 Live headroom (lead's addition after the second Fold run)

The Fold's second run (brightness slider at maximum) showed the ratio at 3.23 for a minute and then
**1.00 for 3+ minutes with HDR frames on screen the whole time**, while the verdict kept saying "rose to
3.23". Android simply gave the layer no headroom (SDR white already at the panel's limit). Now, from the
ratio samples the app already sends (1 s + the display listener): the time with HDR frames on screen and
the part of it with ratio > 1.01; a no-headroom streak (HDR frames on screen, ratio <= 1.01) that after
5 s writes `no HDR headroom for 5 s … the screen brightness is probably at maximum …` (and `HDR headroom
is back …` when it ends), tags the 10 s line and the steady ratio line, puts the HUD on `Wayland · HDR
(no headroom)` and the drawer row on the same hint (`banner_color_hdr_state` = 2); and the verdict
reports `headroom above 1.00 for P% of the HDR time (a of b s), now R` plus the hint while the streak
lasts. Cheap: no new sampling, a few integers under the existing lock.

**Round-2 Fold run (20:23, run 34911468350):** the first no-headroom streak was a **screen recording**,
not the brightness: the user's recording (210.8 s, BT.709 SDR H.264, finalised 20:27:30, so started
~20:23:59) lines up with the ratio falling 2.98 → 1.00 at 20:24:00.9 and staying there to the end. So
Android (at least Samsung's) turns HDR headroom off while the screen is recorded. Every hint now names
both causes; whether maximum brightness does it too is still unmeasured (tester note step H).

### 11.5 The HUD's HDR line (round 2b, the user's layout request)

`40.3ms · Wayland · HDR (no headroom)` ran off the pill's right edge (the capsule's ends curve in, and the
latency line is the bottom of the stack). The display-server label is plain `Wayland` again, and the HDR
state is a line of its own directly under it, fed as a `FusionHdr` code (`FusionHudView.setHdrState`):
`HDR` / `HDR (no headroom)` / `HDR off` / `HDR tone-mapped` (switch on, frame generation without an HDR10
swapchain) / `HDR ready` (gate open, no HDR frames right now). `NONE` - every session whose gate is
closed - draws nothing, so those HUDs are unchanged to the pixel. Pill: the extra line makes the capsule
taller and its ends rounder, so in HDR sessions `fitCapsule()` measures every glyph's ink against the
two rounded ends (plus the outline and 2 sp) and shifts/widens the pill until nothing crosses. Full: an
`HDR` row under `DISP`; Tiles: the *Display* tile's sub-line; Minimal: a line under the footer; Mega: a
fragment after `DISP` in the bottom band. Only the Fusion HUD shows the display server at all; the
classic, GameHub and GameNative HUDs never received it and are untouched.

### 11.6 Round 2d (Fold evidence from 2c)

- **The format scan was a false negative.** FG with the AIO HDR card logged `lists no HDR10 swapchain
  format (37/0 37/1000104001 … 37/1000104012)`: `swap_init` read the surface's list into
  `VkSurfaceFormatKHR fmts[32]`, and with `VK_EXT_swapchain_colorspace` the Android WSI lists every
  format once per colour space (11 on the Fold) - the 10-bit/FP16 rows were past entry 32, and the log's
  160-char buffer cut the list too. Now: all pairs read (heap), one line with the total, colour spaces
  per format and every HDR-capable pair (10-bit/FP16 first); HDR10 pick A2B10G10R10 → A2R10G10B10 →
  FP16 → 8-bit; a driver that refuses the HDR10 swapchain gets the SDR one at once. The only other
  fixed array near it (queue families, 16) does not hold surface formats or present modes; FIFO is the
  only present mode used, so no list is read.
- **VK_EXT_hdr_metadata** enabled in HDR sessions where the device lists it; the HDR10 swapchain gets the
  game's SMPTE 2086 / CTA-861.3 once per swapchain and image description (none if the game sent none).
- **FP16 frame generation for HDR.** The bridge and both engines already take a format (the chain,
  the ring); lsfg-vk itself runs HDR in `R16G16B16A16_SFLOAT`. With HDR frames through an HDR10
  swapchain the scene (`g_fgscene`), the encode pass (new FP16 output), the effects and the engine run
  in FP16 where `fge_format_ok` (storage + linear sampling) passes; the PQ signal is fed as-is (the
  shaders' own HDR flags stay off - PQ is already perceptual). A chain that fails to build in FP16 is
  sticky inside both engines, so the bridge refuses the format for the session and restarts the engine
  in 8 bits instead of marking frame generation failed.
- **Evidence beside the headroom** (app, non-root): PowerManager thermal status (+ listener) and
  `getThermalHeadroom(10)` every 10 s, `Settings.System` brightness + mode (+ observer), screenshots
  (`registerScreenCaptureCallback`, API 34) and screen recording (`addScreenRecordingCallback`, API 35,
  reflection) with the normal `DETECT_SCREEN_CAPTURE` / `DETECT_SCREEN_RECORDING` permissions. Changes
  are logged; the no-headroom lines, the 10 s line and the verdict carry the values and name the likely
  cause (recording / hot / brightness at maximum) or say none is visible. The verdict lists only the
  paths that carried frames, to leave room for it.

### 11.7 Round 2e (headroom request, display ceiling, coverage)

2d on the Fold: all pass (68 surface pairs, HDR10 swapchain A2B10G10R10, FP16 FG at 120 fps, 0 tone-mapped,
VK_EXT_hdr_metadata, headroom 3.00 at brightness 255 **auto**). A tester's ROG Phone 9 Pro (Adreno 830,
API 36): correct HDR frames, composed path, HDR layer ~80% of the 2400x1080 screen (windowed card), not
hot, not recording, brightness 8-223 manual - and the HDR/SDR ratio at **1.00 the whole session**.
- `ASurfaceTransaction_setDesiredHdrHeadroom` (API 35) on the game layer, per HDR frame, only on change:
  content peak (max CLL, else mastering max, else the display's peak) / SDR white, capped at the display's
  highest ratio; 0 (no preference) when the layer stops carrying HDR; re-sent on a fresh SurfaceControl.
  The screen surface (HDR10 swapchain for frame generation) gets the same through
  `SurfaceView.setDesiredHdrHeadroom` (API 35, reflection; `SurfaceControl.Transaction` fallback), applied
  by the app from `banner_color_screen_headroom()`.
- `Display.getHighestHdrSdrRatio()` (Android 16+, reflection) in the display line and the verdict; 1.00
  means no boost at all, and the request is then not made.
- HDR layer coverage in the dataspace line, the composed-picture line and on geometry changes.
- "brightness at maximum" only for MANUAL >= 250/255; no visible cause -> "this phone may not boost HDR
  from apps (the requested headroom was …, the display's highest ratio is …)".

### 11.8 What is still NOT done / not proven

- Round 2's composition, switch and HUD line were device-tested in parts on the Fold (setting-driven
  gate, auto environment, composed picture, headroom lines, HUD); 2d is CI only.
- The engines' own HDR flags (lsfg `hdr_support` / FSR3 HDR input) stay off: the PQ signal is treated as
  perceptual SDR-like data. If interpolation artefacts show only in HDR, that is the next thing to try.
- Colour effects (brightness/contrast/saturation/gamma, the "HDR" bloom) operate on the PQ signal in
  route 1 and look stronger than in SDR; sharpening/AA/CRT/upscalers are perceptually fine.
- scRGB (FP16) swapchains (Phase C) — unchanged: needs FP16 gralloc and a layer change.
