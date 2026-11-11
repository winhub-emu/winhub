/*
 * Vulkan driver capability probe for the Wayland path. See driver_probe.h for the JSON it writes.
 *
 * Loads the driver exactly as the compositor loads its own (adrenotools_open_libvulkan with the driver's folder,
 * file name and the app's native library folder; vk_loader.c does the same) but through a PRIVATE handle and
 * function table: vk_loader.c's g_vk / g_handle, the compositor's instance and device are never touched, so the
 * probe can run before, beside or without a compositor. Instance and device are destroyed before it returns;
 * the library handle is kept and reused (a Vulkan driver is never dlclose()d: its threads and thread-local state
 * outlive the handle, the compositor keeps its own handle for the same reason).
 *
 * Every wait is bounded (2 s), every VkResult is checked, a missing extension only produces `false`.
 *
 * ---- Environment switches of the Wayland path (confirmed in the code on 2026-10-03) ------------------------
 * Read by the app (XServerDisplayActivity, from the container's / shortcut's env vars) and pushed over JNI:
 *   BANNER_WAYLAND_ZERO_COPY=1|true      zero-copy layer mode is the session's starting state (the drawer switch
 *                                        flips it live); also exports BANNER_WSI_AHB=1 into the guest.
 *   BANNER_WAYLAND_UBWC=0|false|off      do not advertise DRM_FORMAT_MOD_QCOM_COMPRESSED (default on).
 *   BANNER_WAYLAND_ASYNC_COPY=0|false|off  synchronous copy path (default: asynchronous, 2 frames in flight).
 *   BANNER_WAYLAND_ZC_CLIENT_FENCE=0|false|off  ignore the render fences a banner_ahb_v1 version 3 driver attaches
 *                                        (default on; this file's sibling ahb_swapchain.c).
 *   BANNER_WAYLAND_AUTO_ACTIVATE=0|false|off|click  how a self-focused window is made Wine's foreground window
 *                                        (default: winhandler bring-to-front; Wine sessions only).
 *   BANNER_WAYLAND_NO_RENDER_NODE=1|true|on  advertise no DRM device in the dma-buf feedback (debug).
 *   BANNER_WAYLAND_HDR=1|true|on|force   HDR10 output request (force = skip the display check).
 *   BANNER_WAYLAND_HDR_SDR_NITS=<float>  SDR white inside an HDR picture (default 203).
 *   BANNER_WAYLAND_HDR_MAX_NITS / _MAX_AVG_NITS / _MIN_NITS  written BY the app into the guest env (display facts).
 *   BANNER_WAYLAND_VK_VARIANT=a7xx|a8xx|a8xx-perf|a8xx-gen8|a8xx-smxz|a8xx-white|a8xx-upstream ("" = plain) and
 *   BANNER_WAYLAND_VK_ICD=<icd.json>     the app -> Proton contract choosing the game's Vulkan driver.
 *   MESA_VK_WSI_PRESENT_MODE=fifo|mailbox|immediate|relaxed  guest Mesa WSI present mode (container presentMode).
 * Read by the guest driver / the Wayland adapter (bionic-vulkan-wrapper), inside the game's process:
 *   BANNER_WSI_AHB=0                     forces gralloc swapchain images off whatever the compositor's mode says
 *                                        (=1 is what the app exports; the mode event decides per swapchain).
 *   BANNER_WSI_AHB_LINEAR=1              linear gralloc images instead of UBWC.
 *   BANNER_WSI_AHB_EXTRA_IMAGES=0..4     extra images for gralloc MAILBOX / IMMEDIATE chains (default 2).
 *   BANNER_WSI_NO_MODIFIERS=1            old behaviour: no explicit DRM format modifiers on Wayland swapchains.
 *   BANNER_WSI_NO_DMABUF_WAIT=1          acquire does not export the dma-buf's fences into a semaphore/fence.
 *   BANNER_KGSL_POLL_FIX=0               the adapter's zero-timeout KGSL poll shim off (default on, Turnip only).
 *   TU_DEBUG=<tokens>                    Turnip debug tokens, composed by the app (gmem / sysmem and the expert list).
 * Not found anywhere in the app, the adapter or the Wayland Turnip tree: nothing else named BANNER_WAYLAND_*.
 */
#define _GNU_SOURCE
#define VK_USE_PLATFORM_ANDROID_KHR
#include "driver_probe.h"
#include "../../adrenotools/include/adrenotools/driver.h"
#include <vulkan/vulkan.h>
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define TAG "BannerWayland"
#define PROBE_BUDGET_MS 2000
#define WAIT_BOUND_NS 2000000000ULL /* 2 s: no wait in here is longer */

/* linux/dma-buf.h (not in the NDK sysroot's older headers). */
struct probe_dma_buf_sync_file { uint32_t flags; int32_t fd; };
#define PROBE_DMA_BUF_SYNC_READ 1u
#define PROBE_DMA_BUF_SYNC_WRITE 2u
#define PROBE_DMA_BUF_IOCTL_EXPORT_SYNC_FILE _IOWR('b', 2, struct probe_dma_buf_sync_file)
#define PROBE_DMA_BUF_IOCTL_IMPORT_SYNC_FILE _IOW('b', 3, struct probe_dma_buf_sync_file)

/* ---- the private function table ------------------------------------------------------------------------ */

#define PROBE_GLOBAL_FUNCS(X) X(CreateInstance) X(EnumerateInstanceVersion)
#define PROBE_INSTANCE_FUNCS(X) \
    X(DestroyInstance) X(EnumeratePhysicalDevices) X(GetPhysicalDeviceProperties) X(GetPhysicalDeviceProperties2) \
    X(GetPhysicalDeviceQueueFamilyProperties) X(GetPhysicalDeviceQueueFamilyProperties2) \
    X(EnumerateDeviceExtensionProperties) X(GetPhysicalDeviceMemoryProperties) X(CreateDevice) X(GetDeviceProcAddr)
#define PROBE_DEVICE_FUNCS(X) \
    X(DestroyDevice) X(GetDeviceQueue) X(CreateFence) X(DestroyFence) X(ResetFences) X(WaitForFences) \
    X(CreateSemaphore) X(DestroySemaphore) X(QueueSubmit) X(DeviceWaitIdle) X(CreateCommandPool) \
    X(DestroyCommandPool) X(AllocateCommandBuffers) X(BeginCommandBuffer) X(EndCommandBuffer) X(CmdFillBuffer) \
    X(CreateBuffer) X(DestroyBuffer) X(GetBufferMemoryRequirements) X(AllocateMemory) X(FreeMemory) \
    X(BindBufferMemory) X(GetFenceFdKHR) X(GetSemaphoreFdKHR) X(GetMemoryFdKHR)

struct probe_vk {
#define X(n) PFN_vk##n n;
    PROBE_GLOBAL_FUNCS(X) PROBE_INSTANCE_FUNCS(X) PROBE_DEVICE_FUNCS(X)
#undef X
};

/* ---- the JSON writer (bounded, never overflows, always closes the object) --------------------------------- */

struct jw { char *buf; size_t cap, len; int first; int depth_first[8]; int depth; };

static void jw_raw(struct jw *j, const char *fmt, ...) {
    if (j->len >= j->cap) return;
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(j->buf + j->len, j->cap - j->len, fmt, ap);
    va_end(ap);
    if (n < 0) return;
    j->len = (size_t)n >= j->cap - j->len ? j->cap - 1 : j->len + (size_t)n;
}

static void jw_sep(struct jw *j) {
    if (!j->first) jw_raw(j, ",");
    j->first = 0;
}

static void jw_str_value(struct jw *j, const char *s) {
    if (!s) { jw_raw(j, "null"); return; }
    jw_raw(j, "\"");
    for (const unsigned char *p = (const unsigned char *)s; *p; p++) {
        if (*p == '"' || *p == '\\') jw_raw(j, "\\%c", *p);
        else if (*p < 0x20 || *p >= 0x7f) jw_raw(j, "?"); /* ASCII only: the Java side gets modified UTF-8 */
        else jw_raw(j, "%c", *p);
    }
    jw_raw(j, "\"");
}

static void jw_key(struct jw *j, const char *key) { jw_sep(j); jw_raw(j, "\"%s\":", key); }
static void jw_str(struct jw *j, const char *key, const char *s) { jw_key(j, key); jw_str_value(j, s); }
static void jw_bool(struct jw *j, const char *key, int v) { jw_key(j, key); jw_raw(j, v ? "true" : "false"); }
static void jw_uint(struct jw *j, const char *key, unsigned long long v) { jw_key(j, key); jw_raw(j, "%llu", v); }
static void jw_dbl(struct jw *j, const char *key, double v) { jw_key(j, key); jw_raw(j, "%.2f", v); }
static void jw_null(struct jw *j, const char *key) { jw_key(j, key); jw_raw(j, "null"); }
static void jw_open(struct jw *j, const char *key, char c) {
    if (key) jw_key(j, key); else jw_sep(j);
    jw_raw(j, "%c", c);
    if (j->depth < 8) j->depth_first[j->depth] = j->first;
    j->depth++;
    j->first = 1;
}
static void jw_close(struct jw *j, char c) {
    jw_raw(j, "%c", c);
    if (j->depth > 0) j->depth--;
    j->first = 0;
}

/* ---- helpers ------------------------------------------------------------------------------------------- */

static int64_t now_us(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000LL + ts.tv_nsec / 1000;
}

static const char *vk_result_name(VkResult r) {
    switch (r) {
    case VK_SUCCESS: return "VK_SUCCESS";
    case VK_NOT_READY: return "VK_NOT_READY";
    case VK_TIMEOUT: return "VK_TIMEOUT";
    case VK_INCOMPLETE: return "VK_INCOMPLETE";
    case VK_ERROR_OUT_OF_HOST_MEMORY: return "VK_ERROR_OUT_OF_HOST_MEMORY";
    case VK_ERROR_OUT_OF_DEVICE_MEMORY: return "VK_ERROR_OUT_OF_DEVICE_MEMORY";
    case VK_ERROR_INITIALIZATION_FAILED: return "VK_ERROR_INITIALIZATION_FAILED";
    case VK_ERROR_DEVICE_LOST: return "VK_ERROR_DEVICE_LOST";
    case VK_ERROR_EXTENSION_NOT_PRESENT: return "VK_ERROR_EXTENSION_NOT_PRESENT";
    case VK_ERROR_FEATURE_NOT_PRESENT: return "VK_ERROR_FEATURE_NOT_PRESENT";
    case VK_ERROR_INCOMPATIBLE_DRIVER: return "VK_ERROR_INCOMPATIBLE_DRIVER";
    case VK_ERROR_TOO_MANY_OBJECTS: return "VK_ERROR_TOO_MANY_OBJECTS";
    case VK_ERROR_NOT_PERMITTED_KHR: return "VK_ERROR_NOT_PERMITTED_KHR";
    case VK_ERROR_INVALID_EXTERNAL_HANDLE: return "VK_ERROR_INVALID_EXTERNAL_HANDLE";
    default: return "VK_ERROR_<other>";
    }
}

static const char *errno_name(int e) {
    switch (e) {
    case ENOTTY: return "ENOTTY";
    case EINVAL: return "EINVAL";
    case EBADF: return "EBADF";
    case EPERM: return "EPERM";
    case EACCES: return "EACCES";
    case ENOSYS: return "ENOSYS";
    default: return NULL;
    }
}

static int has_ext(const VkExtensionProperties *e, uint32_t n, const char *name) {
    for (uint32_t i = 0; i < n; i++)
        if (!strcmp(e[i].extensionName, name)) return 1;
    return 0;
}

/* ---- the driver library: opened once per path, kept (see the header comment) ------------------------------ */

struct probe_lib { char path[512]; void *handle; PFN_vkGetInstanceProcAddr gipa; char loader[16]; };
static struct probe_lib g_libs[4];
static int g_nlibs;
static pthread_mutex_t g_libs_lock = PTHREAD_MUTEX_INITIALIZER;

/* The folder libbannerwayland.so lives in = the app's native library folder, where adrenotools' hook
 * libraries are: the same hookLibDir the compositor passes (nativeLibDir from Java). */
static int native_lib_dir(char *out, size_t cap) {
    Dl_info info;
    if (!dladdr((const void *)banner_probe_driver, &info) || !info.dli_fname) return -1;
    const char *slash = strrchr(info.dli_fname, '/');
    if (!slash) return -1;
    size_t n = (size_t)(slash - info.dli_fname);
    if (n + 1 > cap) return -1;
    memcpy(out, info.dli_fname, n);
    out[n] = 0;
    return 0;
}

static struct probe_lib *probe_lib_open(const char *lib_path, const char *nld, char *err, size_t errcap) {
    const char *key = lib_path ? lib_path : "";
    pthread_mutex_lock(&g_libs_lock);
    for (int i = 0; i < g_nlibs; i++)
        if (!strcmp(g_libs[i].path, key)) { pthread_mutex_unlock(&g_libs_lock); return &g_libs[i]; }
    if (g_nlibs == (int)(sizeof(g_libs) / sizeof(g_libs[0]))) {
        pthread_mutex_unlock(&g_libs_lock);
        snprintf(err, errcap, "too many distinct drivers probed in this process");
        return NULL;
    }
    struct probe_lib *l = &g_libs[g_nlibs];
    memset(l, 0, sizeof(*l));
    snprintf(l->path, sizeof(l->path), "%s", key);
    if (lib_path) {
        const char *slash = strrchr(lib_path, '/');
        if (!slash || !slash[1]) {
            pthread_mutex_unlock(&g_libs_lock);
            snprintf(err, errcap, "driver path is not a /folder/file.so path");
            return NULL;
        }
        /* vk_loader.c's layout: the folder with its trailing slash, the file name, a temp folder inside it. */
        char dir[512], tmpdir[560];
        size_t n = (size_t)(slash - lib_path) + 1;
        if (n >= sizeof(dir)) n = sizeof(dir) - 1;
        memcpy(dir, lib_path, n);
        dir[n] = 0;
        snprintf(tmpdir, sizeof(tmpdir), "%sprobe-temp", dir);
        mkdir(tmpdir, S_IRWXU | S_IRWXG);
        if (!nld) {
            pthread_mutex_unlock(&g_libs_lock);
            snprintf(err, errcap, "cannot locate the app's native library folder for adrenotools");
            return NULL;
        }
        l->handle = adrenotools_open_libvulkan(RTLD_LOCAL | RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, tmpdir, nld, dir,
                                               slash + 1, NULL, NULL);
        snprintf(l->loader, sizeof(l->loader), "adrenotools");
        if (!l->handle) {
            pthread_mutex_unlock(&g_libs_lock);
            snprintf(err, errcap, "adrenotools_open_libvulkan failed for %s", lib_path);
            return NULL;
        }
    } else {
        l->handle = dlopen("libvulkan.so", RTLD_LOCAL | RTLD_NOW);
        snprintf(l->loader, sizeof(l->loader), "system");
        if (!l->handle) {
            pthread_mutex_unlock(&g_libs_lock);
            snprintf(err, errcap, "dlopen(libvulkan.so) failed: %s", dlerror());
            return NULL;
        }
    }
    l->gipa = (PFN_vkGetInstanceProcAddr)dlsym(l->handle, "vkGetInstanceProcAddr");
    if (!l->gipa) {
        pthread_mutex_unlock(&g_libs_lock);
        snprintf(err, errcap, "the library has no vkGetInstanceProcAddr");
        return NULL;
    }
    g_nlibs++;
    pthread_mutex_unlock(&g_libs_lock);
    return l;
}

/* ---- the probe ------------------------------------------------------------------------------------------ */

struct probe {
    struct probe_vk vk;
    VkInstance inst;
    VkPhysicalDevice pd;
    VkDevice dev;
    VkQueue queue;
    uint32_t qfam;
    VkPhysicalDeviceMemoryProperties mem;
    int64_t t0_us;
    int gpu_ok;              /* every fence so far signalled: the device may be waited idle and destroyed */
    char err[200];
};

static int past_budget(const struct probe *p) { return (now_us() - p->t0_us) / 1000 > PROBE_BUDGET_MS; }

static int set_err(struct probe *p, const char *fmt, ...) {
    if (p->err[0]) return -1;
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(p->err, sizeof(p->err), fmt, ap);
    va_end(ap);
    return -1;
}

/* A fence wait with the probe's bound. 1 = signalled, 0 = not within the bound (gpu_ok drops: the device is
 * then left alone, never waited idle), -1 = error. */
static int wait_fence(struct probe *p, VkFence f) {
    VkResult r = p->vk.WaitForFences(p->dev, 1, &f, VK_TRUE, WAIT_BOUND_NS);
    if (r == VK_SUCCESS) return 1;
    p->gpu_ok = 0;
    if (r == VK_TIMEOUT) { set_err(p, "the GPU did not finish the probe's work within 2 s"); return 0; }
    set_err(p, "vkWaitForFences: %s", vk_result_name(r));
    return -1;
}

static uint32_t pick_memory_type(const struct probe *p, uint32_t bits, VkMemoryPropertyFlags want) {
    for (uint32_t i = 0; i < p->mem.memoryTypeCount; i++)
        if ((bits & (1u << i)) && (p->mem.memoryTypes[i].propertyFlags & want) == want) return i;
    for (uint32_t i = 0; i < p->mem.memoryTypeCount; i++)
        if (bits & (1u << i)) return i;
    return UINT32_MAX;
}

/* sync_fd_fence: a signalled fence (empty submit) exports as a SYNC_FD. Leaves the exported fd in *out_fd (-1
 * when the driver reported "already signalled" with no fd, or on failure) for the dma-buf import test. */
static int test_fence_fd(struct probe *p, struct jw *j, int *out_fd) {
    *out_fd = -1;
    if (!p->vk.GetFenceFdKHR) { jw_bool(j, "sync_fd_fence", 0); return 0; }
    VkExportFenceCreateInfo efci = {.sType = VK_STRUCTURE_TYPE_EXPORT_FENCE_CREATE_INFO,
                                    .handleTypes = VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT};
    VkFenceCreateInfo fci = {.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO, .pNext = &efci};
    VkFence f = VK_NULL_HANDLE;
    VkResult r = p->vk.CreateFence(p->dev, &fci, NULL, &f);
    if (r != VK_SUCCESS) { jw_bool(j, "sync_fd_fence", 0); jw_str(j, "sync_fd_fence_error", vk_result_name(r)); return 0; }
    int ok = 0;
    r = p->vk.QueueSubmit(p->queue, 0, NULL, f);
    if (r != VK_SUCCESS) {
        jw_str(j, "sync_fd_fence_error", vk_result_name(r));
    } else if (wait_fence(p, f) == 1) {
        VkFenceGetFdInfoKHR gi = {.sType = VK_STRUCTURE_TYPE_FENCE_GET_FD_INFO_KHR, .fence = f,
                                  .handleType = VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT};
        int fd = -1;
        r = p->vk.GetFenceFdKHR(p->dev, &gi, &fd);
        if (r == VK_SUCCESS) { ok = 1; *out_fd = fd; }
        else jw_str(j, "sync_fd_fence_error", vk_result_name(r));
    }
    jw_bool(j, "sync_fd_fence", ok);
    if (p->gpu_ok) p->vk.DestroyFence(p->dev, f, NULL);
    return ok;
}

/* sync_fd_semaphore: a semaphore signalled by an (otherwise empty) submit exports as a SYNC_FD. */
static int test_semaphore_fd(struct probe *p, struct jw *j) {
    if (!p->vk.GetSemaphoreFdKHR) { jw_bool(j, "sync_fd_semaphore", 0); return 0; }
    VkExportSemaphoreCreateInfo esci = {.sType = VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO,
                                        .handleTypes = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
    VkSemaphoreCreateInfo sci = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO, .pNext = &esci};
    VkSemaphore s = VK_NULL_HANDLE;
    VkResult r = p->vk.CreateSemaphore(p->dev, &sci, NULL, &s);
    if (r != VK_SUCCESS) { jw_bool(j, "sync_fd_semaphore", 0); jw_str(j, "sync_fd_semaphore_error", vk_result_name(r)); return 0; }
    VkFenceCreateInfo fci = {.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    VkFence f = VK_NULL_HANDLE;
    int ok = 0;
    if (p->vk.CreateFence(p->dev, &fci, NULL, &f) == VK_SUCCESS) {
        VkSubmitInfo si = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .signalSemaphoreCount = 1, .pSignalSemaphores = &s};
        r = p->vk.QueueSubmit(p->queue, 1, &si, f);
        if (r != VK_SUCCESS) {
            jw_str(j, "sync_fd_semaphore_error", vk_result_name(r));
        } else if (wait_fence(p, f) == 1) {
            VkSemaphoreGetFdInfoKHR gi = {.sType = VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR, .semaphore = s,
                                          .handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT};
            int fd = -1;
            r = p->vk.GetSemaphoreFdKHR(p->dev, &gi, &fd);
            if (r == VK_SUCCESS) { ok = 1; if (fd >= 0) close(fd); }
            else jw_str(j, "sync_fd_semaphore_error", vk_result_name(r));
        }
        if (p->gpu_ok) p->vk.DestroyFence(p->dev, f, NULL);
    }
    jw_bool(j, "sync_fd_semaphore", ok);
    if (p->gpu_ok) p->vk.DestroySemaphore(p->dev, s, NULL);
    return ok;
}

/* A device-local buffer for the GPU workload and the dma-buf export. external = add the DMA_BUF handle type
 * (export). Returns 0 and fills buf/mem, or -1 with a VkResult name in *why. */
static int make_buffer(struct probe *p, VkDeviceSize size, int external, VkBuffer *buf, VkDeviceMemory *mem,
                       const char **why) {
    *buf = VK_NULL_HANDLE; *mem = VK_NULL_HANDLE;
    VkExternalMemoryBufferCreateInfo embci = {.sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_BUFFER_CREATE_INFO,
                                              .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkBufferCreateInfo bci = {.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO, .pNext = external ? &embci : NULL,
                              .size = size, .usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                              .sharingMode = VK_SHARING_MODE_EXCLUSIVE};
    VkResult r = p->vk.CreateBuffer(p->dev, &bci, NULL, buf);
    if (r != VK_SUCCESS) { *why = vk_result_name(r); return -1; }
    VkMemoryRequirements req;
    p->vk.GetBufferMemoryRequirements(p->dev, *buf, &req);
    uint32_t type = pick_memory_type(p, req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (type == UINT32_MAX) { *why = "no memory type"; p->vk.DestroyBuffer(p->dev, *buf, NULL); *buf = VK_NULL_HANDLE; return -1; }
    VkExportMemoryAllocateInfo emai = {.sType = VK_STRUCTURE_TYPE_EXPORT_MEMORY_ALLOCATE_INFO,
                                       .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    VkMemoryAllocateInfo mai = {.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, .pNext = external ? &emai : NULL,
                                .allocationSize = req.size, .memoryTypeIndex = type};
    r = p->vk.AllocateMemory(p->dev, &mai, NULL, mem);
    if (r != VK_SUCCESS) { *why = vk_result_name(r); p->vk.DestroyBuffer(p->dev, *buf, NULL); *buf = VK_NULL_HANDLE; return -1; }
    r = p->vk.BindBufferMemory(p->dev, *buf, *mem, 0);
    if (r != VK_SUCCESS) {
        *why = vk_result_name(r);
        p->vk.FreeMemory(p->dev, *mem, NULL); p->vk.DestroyBuffer(p->dev, *buf, NULL);
        *buf = VK_NULL_HANDLE; *mem = VK_NULL_HANDLE;
        return -1;
    }
    return 0;
}

/* kgsl_zero_timeout_bug: time vkWaitForFences(timeout 0) right after submitting real GPU work. Correct: VK_TIMEOUT
 * at once. The KGSL "poll" bug: the call blocks until the work is done and answers VK_SUCCESS (the compositor's
 * frame ring and the X11 renderer both learned to avoid zero-timeout queries because of it). A sample where the
 * work had already finished (VK_SUCCESS in under 1 ms) says nothing: the batch is doubled and tried again. */
static void test_zero_timeout(struct probe *p, struct jw *j) {
    VkBuffer buf; VkDeviceMemory mem;
    const char *why = NULL;
    VkDeviceSize size = 32u << 20;
    if (make_buffer(p, size, 0, &buf, &mem, &why) != 0) {
        size = 8u << 20;
        if (make_buffer(p, size, 0, &buf, &mem, &why) != 0) { jw_str(j, "kgsl_zero_timeout_error", why); return; }
    }
    VkCommandPoolCreateInfo pci = {.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
                                   .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT, .queueFamilyIndex = p->qfam};
    VkCommandPool pool = VK_NULL_HANDLE;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    VkFence f = VK_NULL_HANDLE;
    VkFenceCreateInfo fci = {.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    VkResult r = p->vk.CreateCommandPool(p->dev, &pci, NULL, &pool);
    if (r == VK_SUCCESS) {
        VkCommandBufferAllocateInfo cai = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, .commandPool = pool,
                                           .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY, .commandBufferCount = 1};
        r = p->vk.AllocateCommandBuffers(p->dev, &cai, &cmd);
    }
    if (r == VK_SUCCESS) r = p->vk.CreateFence(p->dev, &fci, NULL, &f);
    if (r != VK_SUCCESS) {
        jw_str(j, "kgsl_zero_timeout_error", vk_result_name(r));
        goto out;
    }
    unsigned fills = 16; /* 16 x 32 MB = 512 MB of writes: ~10 ms on an Adreno 750, longer on anything older */
    int yes = 0, no = 0, conclusive = 0, tries = 0;
    jw_open(j, "kgsl_zero_timeout_samples", '[');
    while (conclusive < 3 && tries < 6 && !past_budget(p) && p->gpu_ok) {
        tries++;
        VkCommandBufferBeginInfo bi = {.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
                                       .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT};
        if (p->vk.ResetFences(p->dev, 1, &f) != VK_SUCCESS || p->vk.BeginCommandBuffer(cmd, &bi) != VK_SUCCESS) break;
        for (unsigned i = 0; i < fills; i++) p->vk.CmdFillBuffer(cmd, buf, 0, VK_WHOLE_SIZE, 0x01020304u + i);
        if (p->vk.EndCommandBuffer(cmd) != VK_SUCCESS) break;
        VkSubmitInfo si = {.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = &cmd};
        r = p->vk.QueueSubmit(p->queue, 1, &si, f);
        if (r != VK_SUCCESS) { jw_close(j, ']'); jw_str(j, "kgsl_zero_timeout_error", vk_result_name(r)); goto out_objs; }
        const int64_t t1 = now_us();
        VkResult zr = p->vk.WaitForFences(p->dev, 1, &f, VK_TRUE, 0);
        const int64_t t2 = now_us();
        int done = wait_fence(p, f); /* bounded; the device is never left with the probe's work pending */
        const int64_t t3 = now_us();
        const double call_ms = (double)(t2 - t1) / 1000.0, gpu_ms = (double)(t3 - t1) / 1000.0;
        const char *verdict;
        if (zr == VK_TIMEOUT) { no++; conclusive++; verdict = "correct"; }
        else if (zr == VK_SUCCESS && call_ms >= 1.0) { yes++; conclusive++; verdict = "blocked"; }
        else if (zr == VK_SUCCESS) { verdict = "inconclusive (work already done)"; if (fills < 256) fills *= 2; }
        else { verdict = "error"; }
        jw_open(j, NULL, '{');
        jw_str(j, "result", vk_result_name(zr));
        jw_dbl(j, "call_ms", call_ms);
        jw_dbl(j, "gpu_ms", gpu_ms);
        jw_uint(j, "fills", fills);
        jw_str(j, "verdict", verdict);
        jw_close(j, '}');
        if (done != 1) break;
        if (zr != VK_TIMEOUT && zr != VK_SUCCESS) break;
    }
    jw_close(j, ']');
    jw_uint(j, "kgsl_zero_timeout_conclusive", (unsigned)conclusive);
    if (conclusive) jw_bool(j, "kgsl_zero_timeout_bug", yes > no);
    else jw_null(j, "kgsl_zero_timeout_bug");
out_objs:
    if (p->gpu_ok) {
        if (f) p->vk.DestroyFence(p->dev, f, NULL);
        if (pool) p->vk.DestroyCommandPool(p->dev, pool, NULL);
    }
out:
    if (p->gpu_ok) {
        p->vk.DestroyBuffer(p->dev, buf, NULL);
        p->vk.FreeMemory(p->dev, mem, NULL);
    }
}

/* dmabuf_sync_file_ioctl: export a small allocation as a dma-buf and try the sync_file ioctls on it. The
 * import test needs a sync_file to import: the fence fd from test_fence_fd (consumed and closed here). */
static void test_dmabuf_ioctls(struct probe *p, struct jw *j, int has_mem_fd, int has_dma_buf, int fence_fd) {
    if (!has_mem_fd || !has_dma_buf || !p->vk.GetMemoryFdKHR) {
        jw_bool(j, "dmabuf_sync_file_ioctl", 0);
        jw_bool(j, "dmabuf_sync_file_import", 0);
        jw_str(j, "dmabuf_sync_file_errno", "no dma-buf export on this driver");
        if (fence_fd >= 0) close(fence_fd);
        return;
    }
    VkBuffer buf; VkDeviceMemory mem;
    const char *why = NULL;
    if (make_buffer(p, 4096, 1, &buf, &mem, &why) != 0) {
        jw_bool(j, "dmabuf_sync_file_ioctl", 0);
        jw_bool(j, "dmabuf_sync_file_import", 0);
        jw_str(j, "dmabuf_sync_file_errno", why);
        if (fence_fd >= 0) close(fence_fd);
        return;
    }
    VkMemoryGetFdInfoKHR gi = {.sType = VK_STRUCTURE_TYPE_MEMORY_GET_FD_INFO_KHR, .memory = mem,
                               .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_DMA_BUF_BIT_EXT};
    int dmabuf = -1;
    VkResult r = p->vk.GetMemoryFdKHR(p->dev, &gi, &dmabuf);
    if (r != VK_SUCCESS || dmabuf < 0) {
        jw_bool(j, "dmabuf_sync_file_ioctl", 0);
        jw_bool(j, "dmabuf_sync_file_import", 0);
        jw_str(j, "dmabuf_sync_file_errno", vk_result_name(r));
    } else {
        struct probe_dma_buf_sync_file exp = {.flags = PROBE_DMA_BUF_SYNC_READ, .fd = -1};
        int export_ok = ioctl(dmabuf, PROBE_DMA_BUF_IOCTL_EXPORT_SYNC_FILE, &exp) == 0;
        int export_errno = errno;
        if (export_ok && exp.fd >= 0) close(exp.fd);
        jw_bool(j, "dmabuf_sync_file_ioctl", export_ok);
        if (!export_ok) {
            const char *n = errno_name(export_errno);
            char tmp[32];
            if (!n) { snprintf(tmp, sizeof(tmp), "errno %d", export_errno); n = tmp; }
            jw_str(j, "dmabuf_sync_file_errno", n);
        }
        if (fence_fd >= 0) {
            struct probe_dma_buf_sync_file imp = {.flags = PROBE_DMA_BUF_SYNC_WRITE, .fd = fence_fd};
            int import_ok = ioctl(dmabuf, PROBE_DMA_BUF_IOCTL_IMPORT_SYNC_FILE, &imp) == 0;
            int import_errno = errno;
            jw_bool(j, "dmabuf_sync_file_import", import_ok);
            if (!import_ok) {
                const char *n = errno_name(import_errno);
                char tmp[32];
                if (!n) { snprintf(tmp, sizeof(tmp), "errno %d", import_errno); n = tmp; }
                jw_str(j, "dmabuf_sync_file_import_errno", n);
            }
        } else {
            jw_null(j, "dmabuf_sync_file_import"); /* no sync_fd fence to import: untested */
        }
        close(dmabuf);
    }
    if (fence_fd >= 0) close(fence_fd);
    p->vk.DestroyBuffer(p->dev, buf, NULL);
    p->vk.FreeMemory(p->dev, mem, NULL);
}

static const char *priority_name(VkQueueGlobalPriorityKHR pr) {
    switch (pr) {
    case VK_QUEUE_GLOBAL_PRIORITY_LOW_KHR: return "low";
    case VK_QUEUE_GLOBAL_PRIORITY_MEDIUM_KHR: return "medium";
    case VK_QUEUE_GLOBAL_PRIORITY_HIGH_KHR: return "high";
    case VK_QUEUE_GLOBAL_PRIORITY_REALTIME_KHR: return "realtime";
    default: return "other";
    }
}

int banner_probe_driver(const char *driver_lib_path, char *out_json, size_t cap) {
    if (!out_json || cap < 64) return -1;
    struct jw j = {.buf = out_json, .cap = cap, .len = 0, .first = 1};
    struct probe P;
    memset(&P, 0, sizeof(P));
    struct probe *p = &P;
    p->t0_us = now_us();
    p->gpu_ok = 1;
    int ok = 0;
    jw_open(&j, NULL, '{');
    jw_str(&j, "driver_lib", driver_lib_path);

    char nld[512];
    const char *nldp = native_lib_dir(nld, sizeof(nld)) == 0 ? nld : NULL;
    jw_str(&j, "native_lib_dir", nldp);

    struct probe_lib *lib = probe_lib_open(driver_lib_path, nldp, p->err, sizeof(p->err));
    if (!lib) goto done;
    jw_str(&j, "loader", lib->loader);
    PFN_vkGetInstanceProcAddr gipa = lib->gipa;
#define X(n) p->vk.n = (PFN_vk##n)gipa(NULL, "vk" #n);
    PROBE_GLOBAL_FUNCS(X)
#undef X
    if (!p->vk.CreateInstance) { set_err(p, "no vkCreateInstance"); goto done; }

    /* The instance: 1.3 like the compositor's, capped at what the loader reports. No surface extensions - the
     * probe never presents. */
    uint32_t loader_version = VK_API_VERSION_1_0;
    if (p->vk.EnumerateInstanceVersion) p->vk.EnumerateInstanceVersion(&loader_version);
    uint32_t api = loader_version >= VK_API_VERSION_1_3 ? VK_API_VERSION_1_3
                 : loader_version >= VK_API_VERSION_1_1 ? loader_version : VK_API_VERSION_1_0;
    VkApplicationInfo app = {.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO, .pApplicationName = "banner-driver-probe",
                             .apiVersion = api};
    VkInstanceCreateInfo ici = {.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app};
    VkResult r = p->vk.CreateInstance(&ici, NULL, &p->inst);
    if (r != VK_SUCCESS) { set_err(p, "vkCreateInstance: %s", vk_result_name(r)); goto done; }
#define X(n) p->vk.n = (PFN_vk##n)gipa(p->inst, "vk" #n);
    PROBE_INSTANCE_FUNCS(X)
#undef X
    if (!p->vk.EnumeratePhysicalDevices || !p->vk.GetPhysicalDeviceProperties || !p->vk.CreateDevice ||
        !p->vk.GetDeviceProcAddr || !p->vk.EnumerateDeviceExtensionProperties || !p->vk.GetPhysicalDeviceMemoryProperties ||
        !p->vk.GetPhysicalDeviceQueueFamilyProperties) {
        set_err(p, "instance entry points missing");
        goto done;
    }

    uint32_t npd = 0;
    p->vk.EnumeratePhysicalDevices(p->inst, &npd, NULL);
    VkPhysicalDevice pds[8];
    if (npd > 8) npd = 8;
    if (!npd) { set_err(p, "no physical devices"); goto done; }
    p->vk.EnumeratePhysicalDevices(p->inst, &npd, pds);
    p->pd = VK_NULL_HANDLE;
    for (uint32_t i = 0; i < npd && p->pd == VK_NULL_HANDLE; i++) {
        uint32_t nq = 0;
        p->vk.GetPhysicalDeviceQueueFamilyProperties(pds[i], &nq, NULL);
        VkQueueFamilyProperties qs[16];
        if (nq > 16) nq = 16;
        p->vk.GetPhysicalDeviceQueueFamilyProperties(pds[i], &nq, qs);
        for (uint32_t q = 0; q < nq; q++)
            if (qs[q].queueFlags & VK_QUEUE_GRAPHICS_BIT) { p->pd = pds[i]; p->qfam = q; break; }
    }
    if (p->pd == VK_NULL_HANDLE) { set_err(p, "no graphics queue"); goto done; }

    /* Identity. */
    VkPhysicalDeviceProperties props;
    p->vk.GetPhysicalDeviceProperties(p->pd, &props);
    p->vk.GetPhysicalDeviceMemoryProperties(p->pd, &p->mem);
    char ver[48];
    jw_str(&j, "device_name", props.deviceName);
    jw_uint(&j, "vendor_id", props.vendorID);
    jw_uint(&j, "device_id", props.deviceID);
    snprintf(ver, sizeof(ver), "%u.%u.%u", VK_API_VERSION_MAJOR(props.apiVersion), VK_API_VERSION_MINOR(props.apiVersion),
             VK_API_VERSION_PATCH(props.apiVersion));
    jw_str(&j, "api_version", ver);
    snprintf(ver, sizeof(ver), "%u.%u.%u", VK_API_VERSION_MAJOR(props.driverVersion), VK_API_VERSION_MINOR(props.driverVersion),
             VK_API_VERSION_PATCH(props.driverVersion));
    jw_str(&j, "driver_version", ver);
    jw_uint(&j, "driver_version_raw", props.driverVersion);

    uint32_t ne = 0;
    p->vk.EnumerateDeviceExtensionProperties(p->pd, NULL, &ne, NULL);
    VkExtensionProperties *exts = calloc(ne ? ne : 1, sizeof(*exts));
    if (!exts) { set_err(p, "out of memory"); goto done; }
    if (p->vk.EnumerateDeviceExtensionProperties(p->pd, NULL, &ne, exts) != VK_SUCCESS) ne = 0;
    const int has_fence_fd = has_ext(exts, ne, VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME);
    const int has_sem_fd = has_ext(exts, ne, VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME);
    const int has_mem_fd = has_ext(exts, ne, VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME);
    const int has_dma_buf = has_ext(exts, ne, VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME);
    const int has_modifiers = has_ext(exts, ne, VK_EXT_IMAGE_DRM_FORMAT_MODIFIER_EXTENSION_NAME);
    const int has_ahb = has_ext(exts, ne, VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME);
    const int has_driver_props = props.apiVersion >= VK_API_VERSION_1_2 || has_ext(exts, ne, VK_KHR_DRIVER_PROPERTIES_EXTENSION_NAME);
    const char *gp_ext = has_ext(exts, ne, VK_KHR_GLOBAL_PRIORITY_EXTENSION_NAME) ? VK_KHR_GLOBAL_PRIORITY_EXTENSION_NAME
                       : has_ext(exts, ne, VK_EXT_GLOBAL_PRIORITY_EXTENSION_NAME) ? VK_EXT_GLOBAL_PRIORITY_EXTENSION_NAME : NULL;
    const int has_gp_query = has_ext(exts, ne, VK_EXT_GLOBAL_PRIORITY_QUERY_EXTENSION_NAME);
    free(exts);

    if (has_driver_props && p->vk.GetPhysicalDeviceProperties2 && api >= VK_API_VERSION_1_1) {
        VkPhysicalDeviceDriverProperties dp = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES};
        VkPhysicalDeviceProperties2 p2 = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2, .pNext = &dp};
        p->vk.GetPhysicalDeviceProperties2(p->pd, &p2);
        jw_uint(&j, "driver_id", (unsigned)dp.driverID);
        jw_str(&j, "driver_name", dp.driverName);
        jw_str(&j, "driver_info", dp.driverInfo);
    }
    jw_open(&j, "extensions", '{');
    jw_bool(&j, "external_fence_fd", has_fence_fd);
    jw_bool(&j, "external_semaphore_fd", has_sem_fd);
    jw_bool(&j, "external_memory_fd", has_mem_fd);
    jw_bool(&j, "external_memory_dma_buf", has_dma_buf);
    jw_bool(&j, "image_drm_format_modifier", has_modifiers);
    jw_bool(&j, "android_external_memory_ahb", has_ahb);
    jw_str(&j, "global_priority", gp_ext);
    jw_bool(&j, "global_priority_query", has_gp_query);
    jw_close(&j, '}');
    jw_bool(&j, "drm_modifiers", has_modifiers && has_dma_buf);
    jw_bool(&j, "ahb_export", has_ahb);

    /* The device: every extension the tests need that the driver has; HIGH global priority first, then without. */
    const char *dev_exts[8];
    uint32_t n_dev_exts = 0;
    if (has_fence_fd) dev_exts[n_dev_exts++] = VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME;
    if (has_sem_fd) dev_exts[n_dev_exts++] = VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME;
    if (has_mem_fd) dev_exts[n_dev_exts++] = VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME;
    if (has_mem_fd && has_dma_buf) dev_exts[n_dev_exts++] = VK_EXT_EXTERNAL_MEMORY_DMA_BUF_EXTENSION_NAME;
    if (gp_ext) dev_exts[n_dev_exts++] = gp_ext;
    if (gp_ext && has_gp_query) dev_exts[n_dev_exts++] = VK_EXT_GLOBAL_PRIORITY_QUERY_EXTENSION_NAME;

    jw_open(&j, "global_priority", '{');
    jw_str(&j, "ext", gp_ext);
    if (gp_ext && has_gp_query && p->vk.GetPhysicalDeviceQueueFamilyProperties2 && api >= VK_API_VERSION_1_1) {
        uint32_t nq = 0;
        p->vk.GetPhysicalDeviceQueueFamilyProperties2(p->pd, &nq, NULL);
        if (nq > 16) nq = 16;
        VkQueueFamilyGlobalPriorityPropertiesKHR gpp[16];
        VkQueueFamilyProperties2 q2[16];
        for (uint32_t i = 0; i < nq; i++) {
            gpp[i] = (VkQueueFamilyGlobalPriorityPropertiesKHR){.sType = VK_STRUCTURE_TYPE_QUEUE_FAMILY_GLOBAL_PRIORITY_PROPERTIES_KHR};
            q2[i] = (VkQueueFamilyProperties2){.sType = VK_STRUCTURE_TYPE_QUEUE_FAMILY_PROPERTIES_2, .pNext = &gpp[i]};
        }
        p->vk.GetPhysicalDeviceQueueFamilyProperties2(p->pd, &nq, q2);
        if (p->qfam < nq) {
            jw_open(&j, "allowed", '[');
            for (uint32_t i = 0; i < gpp[p->qfam].priorityCount && i < VK_MAX_GLOBAL_PRIORITY_SIZE_KHR; i++) {
                jw_sep(&j);
                jw_str_value(&j, priority_name(gpp[p->qfam].priorities[i]));
            }
            jw_close(&j, ']');
        }
    }
    float prio = 1.0f;
    VkDeviceQueueGlobalPriorityCreateInfoKHR gpci = {.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_GLOBAL_PRIORITY_CREATE_INFO_KHR,
                                                     .globalPriority = VK_QUEUE_GLOBAL_PRIORITY_HIGH_KHR};
    VkDeviceQueueCreateInfo qci = {.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, .pNext = gp_ext ? &gpci : NULL,
                                   .queueFamilyIndex = p->qfam, .queueCount = 1, .pQueuePriorities = &prio};
    VkDeviceCreateInfo dci = {.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, .queueCreateInfoCount = 1, .pQueueCreateInfos = &qci,
                              .enabledExtensionCount = n_dev_exts, .ppEnabledExtensionNames = dev_exts};
    r = p->vk.CreateDevice(p->pd, &dci, NULL, &p->dev);
    if (gp_ext) {
        jw_bool(&j, "high_accepted", r == VK_SUCCESS);
        if (r != VK_SUCCESS) {
            jw_str(&j, "refusal", vk_result_name(r));
            qci.pNext = NULL;
            r = p->vk.CreateDevice(p->pd, &dci, NULL, &p->dev);
        }
    } else {
        jw_bool(&j, "high_accepted", 0);
    }
    jw_close(&j, '}');
    if (r != VK_SUCCESS) { p->dev = VK_NULL_HANDLE; set_err(p, "vkCreateDevice: %s", vk_result_name(r)); goto done; }
#define X(n) p->vk.n = (PFN_vk##n)p->vk.GetDeviceProcAddr(p->dev, "vk" #n);
    PROBE_DEVICE_FUNCS(X)
#undef X
    if (!p->vk.DestroyDevice || !p->vk.GetDeviceQueue || !p->vk.CreateFence || !p->vk.WaitForFences || !p->vk.QueueSubmit ||
        !p->vk.CreateBuffer || !p->vk.AllocateMemory || !p->vk.CmdFillBuffer || !p->vk.DeviceWaitIdle) {
        set_err(p, "device entry points missing");
        goto done;
    }
    if (!has_fence_fd) p->vk.GetFenceFdKHR = NULL;
    if (!has_sem_fd) p->vk.GetSemaphoreFdKHR = NULL;
    if (!(has_mem_fd && has_dma_buf)) p->vk.GetMemoryFdKHR = NULL;
    p->vk.GetDeviceQueue(p->dev, p->qfam, 0, &p->queue);
    ok = 1;
    jw_bool(&j, "ok", 1);

    /* The measurements, each skipped once the budget is spent (the JSON then says so). */
    int fence_fd = -1;
    if (!past_budget(p)) test_fence_fd(p, &j, &fence_fd);
    if (!past_budget(p) && p->gpu_ok) test_semaphore_fd(p, &j);
    if (!past_budget(p) && p->gpu_ok) test_zero_timeout(p, &j);
    if (!past_budget(p) && p->gpu_ok) test_dmabuf_ioctls(p, &j, has_mem_fd, has_dma_buf, fence_fd);
    else if (fence_fd >= 0) close(fence_fd);
    if (past_budget(p)) set_err(p, "probe budget of %d ms spent; later steps skipped", PROBE_BUDGET_MS);

done:
    /* Tear down - unless the GPU never finished the probe's work: then the device is left as it is (a wait
     * for idle could park this thread for good) and the JSON says so. */
    if (p->dev) {
        if (p->gpu_ok) {
            p->vk.DeviceWaitIdle(p->dev);
            p->vk.DestroyDevice(p->dev, NULL);
        } else {
            jw_bool(&j, "device_leaked", 1);
        }
    }
    if (p->inst && p->vk.DestroyInstance && (p->gpu_ok || !p->dev)) p->vk.DestroyInstance(p->inst, NULL);
    if (!ok) jw_bool(&j, "ok", 0);
    jw_uint(&j, "probe_ms", (unsigned long long)((now_us() - p->t0_us) / 1000));
    if (p->err[0]) jw_str(&j, "error", p->err); else jw_null(&j, "error");
    jw_close(&j, '}');
    out_json[j.len < cap ? j.len : cap - 1] = 0;
    __android_log_print(ok ? ANDROID_LOG_INFO : ANDROID_LOG_WARN, TAG, "driver probe (%s): %s",
                        driver_lib_path ? driver_lib_path : "system libvulkan", ok ? "done" : p->err);
    return ok ? 0 : -1;
}
