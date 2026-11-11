#ifndef DRIVER_PROBE_H
#define DRIVER_PROBE_H
/*
 * Vulkan driver capability probe for the Wayland path (driver_probe.c).
 *
 * banner_probe_driver() loads the given driver the way the compositor loads its own (adrenotools,
 * vk_loader.c's mechanism, but with its OWN dlopen handle, function table, instance and device - the
 * running compositor is never touched), measures in at most ~2 s what the Wayland runtime depends on, destroys
 * instance and device, and writes one JSON object. Reached from Java as
 * WaylandCompositor.nativeProbeDriver(String libPath) -> String (waylandcomp_jni.c). Callable before, beside
 * or without a running compositor; every Vulkan result is checked and a missing extension only yields `false`.
 *
 * JSON (keys always present once the step that fills them ran; a step that could not run leaves its key out,
 * and `error` says why the probe stopped early):
 *
 *   {
 *     "ok": true,                           // instance + device came up on this driver
 *     "driver_lib": "/data/.../libvulkan_freedreno.so" | null,
 *     "loader": "adrenotools" | "system",   // how the library was opened (null path = system libvulkan)
 *     "native_lib_dir": "/data/app/.../lib/arm64",
 *     "device_name": "Turnip Adreno (TM) 750",
 *     "vendor_id": 20803, "device_id": 67305985,
 *     "api_version": "1.4.305",             // VkPhysicalDeviceProperties::apiVersion
 *     "driver_version": "26.0.0",            // decoded as major.minor.patch; "driver_version_raw" is the uint32
 *     "driver_id": 23, "driver_name": "turnip", "driver_info": "Mesa 26.0.0-devel (git-...)",  // VK 1.2+ only
 *     "extensions": { "external_fence_fd": true, "external_semaphore_fd": true, "external_memory_fd": true,
 *                     "external_memory_dma_buf": true, "image_drm_format_modifier": true,
 *                     "android_external_memory_ahb": true, "global_priority": "VK_KHR_global_priority" | null,
 *                     "global_priority_query": true },
 *     "sync_fd_fence": true,                 // VK_KHR_external_fence_fd present AND a signalled fence exports as SYNC_FD
 *     "sync_fd_semaphore": true,             // VK_KHR_external_semaphore_fd present AND a signalled semaphore exports as SYNC_FD
 *     "drm_modifiers": true,                 // VK_EXT_image_drm_format_modifier AND VK_EXT_external_memory_dma_buf
 *     "ahb_export": true,                    // VK_ANDROID_external_memory_android_hardware_buffer present
 *     "global_priority": { "ext": "VK_KHR_global_priority" | null, "high_accepted": true,
 *                          "allowed": ["low","medium","high","realtime"],    // VK_EXT_global_priority_query only
 *                          "refusal": "VK_ERROR_NOT_PERMITTED_KHR" },        // when high_accepted is false
 *     "kgsl_zero_timeout_bug": false,        // vkWaitForFences(timeout 0) blocked until the GPU finished
 *     "kgsl_zero_timeout_samples": [ { "result": "VK_TIMEOUT", "call_ms": 0.02, "gpu_ms": 9.8, "fills": 16 }, ... ],
 *     "kgsl_zero_timeout_conclusive": 3,     // samples that could tell (the work was still running at the call)
 *     "dmabuf_sync_file_ioctl": false,       // DMA_BUF_IOCTL_EXPORT_SYNC_FILE works on a dma-buf this driver exported
 *     "dmabuf_sync_file_import": false,      // DMA_BUF_IOCTL_IMPORT_SYNC_FILE works on it (needs a sync_fd fence above)
 *     "dmabuf_sync_file_errno": "ENOTTY",    // when the export failed
 *     "probe_ms": 412,
 *     "error": null | "vkCreateDevice: VK_ERROR_INITIALIZATION_FAILED"
 *   }
 *
 * The kgsl_zero_timeout_bug test submits a real transfer batch (N vkCmdFillBuffer over a 32 MB buffer, N doubled
 * while the GPU finishes before the call can be timed) and times vkWaitForFences(timeout 0) right after the
 * submit: a correct driver answers VK_TIMEOUT at once; a KGSL Turnip with the zero-timeout "poll" bug returns
 * VK_SUCCESS only after the work completed (call_ms >= ~1 ms). Three conclusive samples; the fence is then
 * waited for with a 2 s bound so the device never stays busy behind the probe.
 */
#include <stddef.h>

/* Enough for the JSON above with the samples array. */
#define BANNER_PROBE_JSON_CAP 8192

/* Probe the driver at driver_lib_path (full path of the .so; NULL = the system libvulkan) and write JSON into
 * out_json (cap bytes, always NUL-terminated, always a complete object). Returns 0 when instance + device came up
 * (`ok` true), -1 otherwise (`error` says why; whatever was measured before is still in the JSON). */
int banner_probe_driver(const char *driver_lib_path, char *out_json, size_t cap);

#endif
