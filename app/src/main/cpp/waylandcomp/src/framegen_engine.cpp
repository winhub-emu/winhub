// C++ side of the Wayland frame-generation bridge - see framegen_engine.h.
//
// Hosts the SAME engine objects the X11 renderer drives (lsfg::Engine, winfg::Engine), fed from
// the compositor's own Turnip entry points. Nothing in winlator/lsfg or winlator/winfg is
// modified: the engines take a VkTable (VulkanRendererContext.h) for their dispatch, and one is
// filled here by name from the compositor's vkGetInstanceProcAddr / vkGetDeviceProcAddr.
#include "framegen_engine.h"

#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include <android/log.h>

#include "VulkanRendererContext.h"   // VkTable
#include "lsfg/lsfg_engine.h"
#include "lsfg/lsfg_probe.h"
#include "lsfg/lsfg_vkd.h"
#include "winfg/winfg_engine.h"

#define ELOG(...) __android_log_print(ANDROID_LOG_INFO, "BannerWayland", "framegen: " __VA_ARGS__)
#define ELOGE(...) __android_log_print(ANDROID_LOG_ERROR, "BannerWayland", "framegen: " __VA_ARGS__)

namespace {

VkTable g_table{};
lsfg::Caps g_caps{};
VkPhysicalDeviceVulkan12Features g_v12{};
VkPhysicalDeviceFeatures2 g_f2{};
VkPhysicalDevice g_pd = VK_NULL_HANDLE;
VkDevice g_dev = VK_NULL_HANDLE;
VkQueue g_queue = VK_NULL_HANDLE;
uint32_t g_qfam = 0;
bool g_probed = false, g_ready = false;

std::unique_ptr<lsfg::Engine> g_lsfg;
std::unique_ptr<winfg::Engine> g_winfg;
int g_kind = -1;
char g_build[96] = "";

// Instance-level entry points the engines and the probe use.
#define FGE_INSTANCE_FUNCS(X)                                                              \
    X(GetPhysicalDeviceProperties) X(GetPhysicalDeviceMemoryProperties)                    \
    X(GetPhysicalDeviceFeatures2) X(GetPhysicalDeviceFormatProperties)                     \
    X(GetPhysicalDeviceQueueFamilyProperties) X(GetDeviceProcAddr)
// Device-level: the LSFG chain's 34 (lsfg_vkd.h), win-fg's extras (winfg_vkd.cpp) and the
// generic set either may reach for. Anything left null in VkTable is never called.
#define FGE_DEVICE_FUNCS(X)                                                                \
    X(AllocateDescriptorSets) X(AllocateMemory) X(BindBufferMemory) X(BindImageMemory)     \
    X(CmdBindDescriptorSets) X(CmdBindPipeline) X(CmdCopyImage) X(CmdDispatch)             \
    X(CmdPipelineBarrier) X(CreateBuffer) X(CreateComputePipelines) X(CreateDescriptorPool)  \
    X(CreateDescriptorSetLayout) X(CreateImage) X(CreateImageView) X(CreatePipelineLayout)  \
    X(CreateSampler) X(CreateShaderModule) X(DestroyBuffer) X(DestroyDescriptorPool)        \
    X(DestroyDescriptorSetLayout) X(DestroyImage) X(DestroyImageView) X(DestroyPipeline)    \
    X(DestroyPipelineLayout) X(DestroySampler) X(DestroyShaderModule) X(FreeMemory)         \
    X(GetBufferMemoryRequirements) X(GetImageMemoryRequirements) X(MapMemory) X(UnmapMemory) \
    X(UpdateDescriptorSets) X(CmdClearColorImage) X(DeviceWaitIdle) X(ResetDescriptorPool)  \
    X(QueueSubmit) X(CreateFence) X(DestroyFence) X(WaitForFences)        \
    X(ResetFences) X(GetFenceStatus) X(CreateSemaphore) X(DestroySemaphore)                 \
    X(CreateCommandPool) X(DestroyCommandPool) X(AllocateCommandBuffers)                    \
    X(FreeCommandBuffers) X(BeginCommandBuffer) X(EndCommandBuffer) X(ResetCommandBuffer)   \
    X(CmdBlitImage) X(CmdCopyBufferToImage) X(FreeDescriptorSets) X(FlushMappedMemoryRanges) \
    X(GetDeviceQueue) X(CreateQueryPool) X(DestroyQueryPool) X(CmdResetQueryPool)           \
    X(CmdWriteTimestamp) X(GetQueryPoolResults)

void fill_instance(PFN_vkGetInstanceProcAddr gipa, VkInstance inst) {
#define X(n) g_table.n = reinterpret_cast<PFN_vk##n>(gipa(inst, "vk" #n));
    FGE_INSTANCE_FUNCS(X)
#undef X
}

void fill_device(VkDevice dev) {
    if (!g_table.GetDeviceProcAddr) return;
#define X(n) g_table.n = reinterpret_cast<PFN_vk##n>(g_table.GetDeviceProcAddr(dev, "vk" #n));
    FGE_DEVICE_FUNCS(X)
#undef X
}

} // namespace

extern "C" {

const void *fge_probe(PFN_vkGetInstanceProcAddr gipa, VkInstance inst, VkPhysicalDevice pd,
                      VkFormat ring_fmt) {
    g_pd = pd;
    fill_instance(gipa, inst);
    std::vector<VkExtensionProperties> exts;
    auto enumExts = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
        gipa(inst, "vkEnumerateDeviceExtensionProperties"));
    if (enumExts) {
        uint32_t n = 0;
        enumExts(pd, nullptr, &n, nullptr);
        exts.resize(n);
        if (n) enumExts(pd, nullptr, &n, exts.data());
    }
    g_caps = lsfg::Caps{};
    // No Vulkan 1.1 compat here: the Wayland compositor only ever runs on Turnip (adrenotools),
    // which reports 1.3+; the stock-driver case the compat flag exists for cannot reach it.
    g_caps.features = lsfg::queryFeatures(g_table, pd, exts, /*allowVk11=*/false);
    g_caps.probedFormat = ring_fmt;
    g_caps.storageOnSwapchainFormat = g_table.GetPhysicalDeviceFormatProperties
        ? lsfg::probeStorageFormat(g_table, pd, ring_fmt) : false;
    g_caps.linearBlitOnSwapchainFormat = g_table.GetPhysicalDeviceFormatProperties
        ? lsfg::probeLinearBlit(g_table, pd, ring_fmt) : false;
    g_probed = true;
    if (!g_caps.features.deviceGatesPass()) {
        lsfg::explain(g_caps);
        ELOG("device gates for the LSFG chain fail: %s", g_caps.reason);
        return nullptr;
    }
    // Same chain VulkanRendererContext::createLogicalDevice hangs on its device.
    g_v12 = VkPhysicalDeviceVulkan12Features{};
    g_v12.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
    g_v12.vulkanMemoryModel = VK_TRUE;
    g_v12.vulkanMemoryModelDeviceScope =
        g_caps.features.vulkanMemoryModelDeviceScope ? VK_TRUE : VK_FALSE;
    g_f2 = VkPhysicalDeviceFeatures2{};
    g_f2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
    g_f2.pNext = &g_v12;
    g_f2.features.shaderStorageImageWriteWithoutFormat = VK_TRUE;
    g_f2.features.shaderStorageImageExtendedFormats = VK_TRUE;
    return &g_f2;
}

void fge_device_ready(PFN_vkGetInstanceProcAddr gipa, VkInstance inst, VkPhysicalDevice pd,
                      VkDevice dev, VkQueue queue, uint32_t qfam, int features_enabled) {
    if (!g_probed) fill_instance(gipa, inst);
    g_pd = pd; g_dev = dev; g_queue = queue; g_qfam = qfam;
    fill_device(dev);
    g_caps.featuresEnabled = features_enabled != 0;
    lsfg::explain(g_caps);
    g_ready = true;
    ELOG("device ready: lsfg caps '%s', storage on ring format %d", g_caps.reason,
         (int)g_caps.storageOnSwapchainFormat);
}

int fge_caps_ok(int kind) {
    if (!g_ready) return 0;
    if (kind == 1) return g_caps.storageOnSwapchainFormat ? 1 : 0;
    return g_caps.supported() ? 1 : 0;
}

const char *fge_caps_reason(void) { return g_caps.reason; }

int fge_format_ok(VkFormat fmt) {
    if (!g_ready || !g_table.GetPhysicalDeviceFormatProperties || fmt == VK_FORMAT_UNDEFINED) return 0;
    return lsfg::probeStorageFormat(g_table, g_pd, fmt) && lsfg::probeLinearBlit(g_table, g_pd, fmt) ? 1 : 0;
}

int fge_start(int kind, const char *cache_path) {
    fge_stop();
    if (!g_ready) return -1;
    if (kind == 1) {
        auto e = std::make_unique<winfg::Engine>();
        if (!e->init(g_table, g_pd, g_dev, g_qfam, g_queue)) {
            ELOGE("winfg-native: engine init failed");
            return -1;
        }
        g_winfg = std::move(e);
        g_kind = 1;
#if defined(WINFG_UPSTREAM) && defined(WINFG_CHAIN_HASH)
        std::snprintf(g_build, sizeof(g_build), "chain %s, src %s", WINFG_UPSTREAM, WINFG_CHAIN_HASH);
#else
        std::snprintf(g_build, sizeof(g_build), "embedded chain");
#endif
        return 0;
    }
    if (!cache_path || !*cache_path) return -1;
    if (!lsfgVkdInit(g_table)) {
        ELOGE("lsfg-native: dispatch incomplete");
        return -1;
    }
    auto e = std::make_unique<lsfg::Engine>();
    if (!e->init(g_dev, g_pd, cache_path, g_caps.features.spirvTarget)) {
        ELOGE("lsfg-native: engine init failed (cache %s, spirv target 0x%x)", cache_path,
              g_caps.features.spirvTarget);
        return -1;
    }
    g_lsfg = std::move(e);
    g_kind = 0;
    std::snprintf(g_build, sizeof(g_build), "spirv target 0x%x, cache %s",
                  g_caps.features.spirvTarget, cache_path);
    return 0;
}

void fge_stop(void) {
    if ((g_lsfg || g_winfg) && g_table.DeviceWaitIdle && g_dev) g_table.DeviceWaitIdle(g_dev);
    g_lsfg.reset();
    g_winfg.reset();
    g_kind = -1;
}

int fge_unavailable(void) {
    if (g_kind == 1) return g_winfg && g_winfg->unavailable() ? 1 : 0;
    return g_lsfg && g_lsfg->unavailable() ? 1 : 0;
}

void fge_configure(uint32_t multiplier, float flow_scale, float refresh_hz, int model,
                   int perf_preset) {
    if (multiplier < 2) multiplier = 2;
    if (g_kind == 1 && g_winfg) g_winfg->configure(multiplier, model, perf_preset, flow_scale);
    else if (g_lsfg) g_lsfg->configure(multiplier, 0, flow_scale, refresh_hz);
}

int fge_prepare(uint32_t w, uint32_t h, VkFormat fmt) {
    if (g_kind == 1) return g_winfg && g_winfg->prepare(w, h, fmt) ? 1 : 0;
    if (!g_lsfg) return 0;
    // The Wayland scene is the game's own size when it runs fullscreen, so the flow pyramid is
    // scaled against that (X11 passes the X screen size here for the same reason).
    g_lsfg->setGuestExtent(w, h);
    return g_lsfg->prepare(w, h, fmt) ? 1 : 0;
}

uint32_t fge_plan(uint32_t capacity, uint64_t source_frames, float presented_rate) {
    if (g_kind == 1) return g_winfg ? g_winfg->plan(capacity) : 0;
    if (!g_lsfg) return 0;
    g_lsfg->setPresentedRate(presented_rate);
    return g_lsfg->plan(capacity, source_frames);
}

void fge_process(VkCommandBuffer cmd, VkImage source, uint32_t w, uint32_t h, uint32_t gens) {
    if (g_kind == 1) { if (g_winfg) g_winfg->process(cmd, source, w, h, gens); return; }
    if (g_lsfg) g_lsfg->process(cmd, source, w, h, gens);
}

void fge_generate(VkCommandBuffer cmd, uint32_t g, uint32_t count, VkImage img, VkImageView view,
                  uint32_t w, uint32_t h) {
    if (g_kind == 1) { if (g_winfg) g_winfg->generateInto(cmd, g, count, img, view, w, h); return; }
    if (g_lsfg) g_lsfg->generateInto(cmd, g, g, img, view, w, h);
}

void fge_forget_targets(void) {
    if (g_lsfg) g_lsfg->forgetTargets();
}

void fge_telemetry(float *accepted, float *source_rate, float *thermal) {
    *accepted = 0.0f; *source_rate = 0.0f; *thermal = -1.0f;
    if (g_kind == 0 && g_lsfg) {
        *accepted = (float)g_lsfg->acceptedGenerations();
        *source_rate = g_lsfg->sourceRate();
        *thermal = (float)g_lsfg->thermalStatus();
    }
}

const char *fge_engine_name(int kind) { return kind == 1 ? "Win-FG Native" : "LSFG Native"; }
const char *fge_build_info(void) { return g_build; }

} // extern "C"
