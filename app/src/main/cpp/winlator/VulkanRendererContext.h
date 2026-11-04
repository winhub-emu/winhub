#pragma once
#include <vulkan/vulkan.h>
#include <list>
#include <vulkan/vulkan_android.h>
struct VkTable {

    PFN_vkCreateInstance CreateInstance;

    PFN_vkDestroyInstance DestroyInstance;
    PFN_vkEnumeratePhysicalDevices EnumeratePhysicalDevices;
    PFN_vkGetPhysicalDeviceProperties GetPhysicalDeviceProperties;
    PFN_vkGetPhysicalDeviceMemoryProperties GetPhysicalDeviceMemoryProperties;
    PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR GetPhysicalDeviceSurfaceCapabilitiesKHR;
    PFN_vkGetPhysicalDeviceSurfaceFormatsKHR GetPhysicalDeviceSurfaceFormatsKHR;
    PFN_vkGetPhysicalDeviceSurfacePresentModesKHR GetPhysicalDeviceSurfacePresentModesKHR;
    PFN_vkGetPhysicalDeviceQueueFamilyProperties GetPhysicalDeviceQueueFamilyProperties;
    PFN_vkGetPhysicalDeviceSurfaceSupportKHR GetPhysicalDeviceSurfaceSupportKHR;
    PFN_vkGetPhysicalDeviceFormatProperties GetPhysicalDeviceFormatProperties;
    PFN_vkGetPhysicalDeviceFeatures2 GetPhysicalDeviceFeatures2;
    PFN_vkCreateDevice CreateDevice;
    PFN_vkDestroySurfaceKHR DestroySurfaceKHR;
    PFN_vkCreateAndroidSurfaceKHR CreateAndroidSurfaceKHR;

    PFN_vkGetDeviceProcAddr GetDeviceProcAddr;
    PFN_vkDestroyDevice DestroyDevice;
    PFN_vkGetDeviceQueue GetDeviceQueue;
    PFN_vkDeviceWaitIdle DeviceWaitIdle;
    PFN_vkCreateSwapchainKHR CreateSwapchainKHR;
    PFN_vkDestroySwapchainKHR DestroySwapchainKHR;
    PFN_vkGetSwapchainImagesKHR GetSwapchainImagesKHR;
    PFN_vkAcquireNextImageKHR AcquireNextImageKHR;
    PFN_vkQueuePresentKHR QueuePresentKHR;
    PFN_vkQueueSubmit QueueSubmit;
    PFN_vkCreateQueryPool CreateQueryPool;
    PFN_vkDestroyQueryPool DestroyQueryPool;
    PFN_vkCmdResetQueryPool CmdResetQueryPool;
    PFN_vkCmdWriteTimestamp CmdWriteTimestamp;
    PFN_vkGetQueryPoolResults GetQueryPoolResults;
    PFN_vkCreateRenderPass CreateRenderPass;
    PFN_vkDestroyRenderPass DestroyRenderPass;
    PFN_vkCreateFramebuffer CreateFramebuffer;
    PFN_vkDestroyFramebuffer DestroyFramebuffer;
    PFN_vkCreateImageView CreateImageView;
    PFN_vkDestroyImageView DestroyImageView;
    PFN_vkCreateImage CreateImage;
    PFN_vkDestroyImage DestroyImage;
    PFN_vkCreateBuffer CreateBuffer;
    PFN_vkDestroyBuffer DestroyBuffer;
    PFN_vkAllocateMemory AllocateMemory;
    PFN_vkFreeMemory FreeMemory;
    PFN_vkMapMemory MapMemory;
    PFN_vkFlushMappedMemoryRanges FlushMappedMemoryRanges;
    PFN_vkBindBufferMemory BindBufferMemory;
    PFN_vkBindImageMemory BindImageMemory;
    PFN_vkGetBufferMemoryRequirements GetBufferMemoryRequirements;
    PFN_vkGetImageMemoryRequirements GetImageMemoryRequirements;
    PFN_vkCreateDescriptorSetLayout CreateDescriptorSetLayout;
    PFN_vkDestroyDescriptorSetLayout DestroyDescriptorSetLayout;
    PFN_vkCreateDescriptorPool CreateDescriptorPool;
    PFN_vkDestroyDescriptorPool DestroyDescriptorPool;
    PFN_vkAllocateDescriptorSets AllocateDescriptorSets;
    PFN_vkFreeDescriptorSets FreeDescriptorSets;
    PFN_vkUpdateDescriptorSets UpdateDescriptorSets;
    PFN_vkCreatePipelineLayout CreatePipelineLayout;
    PFN_vkDestroyPipelineLayout DestroyPipelineLayout;
    PFN_vkCreateShaderModule CreateShaderModule;
    PFN_vkDestroyShaderModule DestroyShaderModule;
    PFN_vkCreateGraphicsPipelines CreateGraphicsPipelines;
    PFN_vkDestroyPipeline DestroyPipeline;
    PFN_vkCreateCommandPool CreateCommandPool;
    PFN_vkDestroyCommandPool DestroyCommandPool;
    PFN_vkAllocateCommandBuffers AllocateCommandBuffers;
    PFN_vkFreeCommandBuffers FreeCommandBuffers;
    PFN_vkBeginCommandBuffer BeginCommandBuffer;
    PFN_vkEndCommandBuffer EndCommandBuffer;
    PFN_vkResetCommandBuffer ResetCommandBuffer;
    PFN_vkCmdBeginRenderPass CmdBeginRenderPass;
    PFN_vkCmdEndRenderPass CmdEndRenderPass;
    PFN_vkCmdBindPipeline CmdBindPipeline;
    PFN_vkCmdBindDescriptorSets CmdBindDescriptorSets;
    PFN_vkCmdDraw CmdDraw;
    PFN_vkCmdPushConstants CmdPushConstants;
    PFN_vkCmdSetViewport CmdSetViewport;
    PFN_vkCmdSetScissor CmdSetScissor;
    PFN_vkCmdPipelineBarrier CmdPipelineBarrier;
    PFN_vkCmdCopyImage CmdCopyImage;
    PFN_vkCmdBlitImage CmdBlitImage;   // frame-gen capture resolution: composite -> swapchain upscale
    // Compute: the native LSFG chain is 25 compute dispatches.
    PFN_vkCmdDispatch CmdDispatch;
    PFN_vkCreateComputePipelines CreateComputePipelines;
    PFN_vkUnmapMemory UnmapMemory;
    // win-fg native: clears its flow scratch, resets its scratch pool on resize.
    PFN_vkCmdClearColorImage CmdClearColorImage;
    PFN_vkResetDescriptorPool ResetDescriptorPool;
    PFN_vkCmdCopyBufferToImage CmdCopyBufferToImage;
    PFN_vkCreateSampler CreateSampler;
    PFN_vkDestroySampler DestroySampler;
    PFN_vkCreateSemaphore CreateSemaphore;
    PFN_vkDestroySemaphore DestroySemaphore;
    PFN_vkCreateFence CreateFence;
    PFN_vkDestroyFence DestroyFence;
    PFN_vkWaitForFences WaitForFences;
    PFN_vkResetFences ResetFences;
    PFN_vkGetFenceStatus GetFenceStatus;

    PFN_vkGetAndroidHardwareBufferPropertiesANDROID GetAndroidHardwareBufferPropertiesANDROID;
};

#include <android/log.h>
#include <string>
#define WLOG_TAG "Winlator_Renderer"
#define RLOG(...) if(verboseLog) __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,__VA_ARGS__)
#define RLOG_E(...) __android_log_print(ANDROID_LOG_ERROR,WLOG_TAG,__VA_ARGS__)
#define SCANOUT_LOG(...) __android_log_print(ANDROID_LOG_DEBUG,"Winlator_Scanout",__VA_ARGS__)

#include <vulkan/vulkan_android.h>
#include <android/hardware_buffer.h>
#include <android/native_window.h>
#include <vector>
#include <unordered_map>
#include <thread>
#include <atomic>
#include <mutex>
#include <shared_mutex>
#include <condition_variable>
#include <chrono>

// Renderer-neutral direct-scanout impl (owns the SurfaceControl/AHB state).
// Included after SCANOUT_LOG is defined above; its own definition is guarded.
#include "../scanout/ScanoutContext.h"

// Native (compositor-side) LSFG frame generation — capability gate.
#include "lsfg/lsfg_probe.h"
#include <memory>
#include <string>

namespace lsfg { class Engine; }
namespace winfg { class Engine; }

static constexpr uint32_t MAX_FRAMES_IN_FLIGHT = 2;

struct WindowPushConstants { float ndcX0, ndcY0, ndcX1, ndcY1; int useTexAlpha; };

// Push-constant layouts for the spatial-upscaler post passes. The leading vec4
// `ndc` matches upscale.vert; the remaining members are read only by the
// fragment stage. std430 offsets line up with these C structs.
struct SgsrPushConstants {                 // 36 bytes
    float ndc[4];
    float viewportInfo[4];                 // xy = 1/inputSize, zw = inputSize px
    float edgeSharpness;                   // SGSR EdgeSharpness, from the slider
};
struct NisPushConstants {                  // 36 bytes
    float ndc[4];
    float viewportInfo[4];                 // xy = 1/inputSize, zw = inputSize px
    float sharpness;                       // NIS sharpness [0..1], from the slider
};
struct EasuPushConstants {                 // 88 bytes
    float    ndc[4];
    uint32_t con0[4], con1[4], con2[4], con3[4];
    float    outW, outH;
};
struct RcasPushConstants {                 // 40 bytes
    float    ndc[4];
    uint32_t con[4];                       // con.x = bit-packed sharpness
    float    outW, outH;
};
struct DownscalePushConstants {            // 32 bytes
    float ndc[4];
    float srcW, srcH;                      // render (source) resolution
    float dstW, dstH;                      // display (output) resolution
};
// Composable post effects (AMD CAS sharpen + fake-HDR). Both lead with vec4 ndc
// (offset 0) like the upscaler PCs and stay well under the 88-byte PC range.
struct CasPushConstants {                  // 32 bytes
    float ndc[4];
    float resolution[2];                   // input texture size in px
    float sharpness;                       // CAS SHARPNESS term [0..1]
    float _pad;
};
struct HdrPushConstants {                  // 24 bytes
    float ndc[4];
    float resolution[2];                   // input texture size in px
};
// Phase 2 screen effects ported from the GL EffectComposer. Each leads with vec4
// ndc (offset 0) like the upscaler PCs and stays well under the 88-byte PC range.
struct FxaaPushConstants {                 // 24 bytes
    float ndc[4];
    float resolution[2];                   // input texture size in px
};
struct ToonPushConstants {                 // 24 bytes
    float ndc[4];
    float resolution[2];                   // input texture size in px
};
struct ColorPushConstants {                // 32 bytes
    float ndc[4];
    float brightness;                      // additive [-1,1]
    float contrast;                        // [0,2]
    float gamma;                           // [0.1,5]
    float saturation;                      // [0,2]; 1 = unchanged, 0 = greyscale.
                                           // APPENDED last so the three offsets above
                                           // stay where color.frag already had them.
};
struct NtscPushConstants {                 // 28 bytes
    float ndc[4];
    float resolution[2];                   // screen size in px (TextureSize/resolution)
    float frameCount;                      // animated chroma-phase counter
};
struct CrtPushConstants {                  // 24 bytes
    float ndc[4];
    float resolution[2];                   // input texture size in px (unused by shader)
};
struct DebandPushConstants {               // 28 bytes
    float ndc[4];
    float resolution[2];                   // input texture size in px (layout parity)
    float strength;                        // dither amplitude in LSBs (1.0 = +/-1/255)
};

class VulkanRendererContext {
public:
    // `lsfgVk11Compat` (experimental) lets the LSFG capability probe accept a
    // Vulkan 1.1/1.2 device that offers the extensions the chain needs; it has
    // to be known before vkCreateDevice, hence a constructor argument.
    VulkanRendererContext(ANativeWindow* window, int cWidth, int cHeight, void* adrenotoolsHandle = nullptr,
                          bool lsfgVk11Compat = false);
    ~VulkanRendererContext();

    void onSurfaceResized(int width, int height);
    void setTransform(float ox, float oy, float sx, float sy);
    // #413: game-region clip rect (surface px). Stored and applied as the swapchain scissor in
    // recordCmdBuf so a FILL/STRETCH game confined to a half (TOP/BOTTOM alignment) can't bleed into
    // the on-screen-controls half. A full-surface (or w<=0) rect means "no clip" (CENTER).
    void setClipRegion(int x, int y, int w, int h);
    void updatePointerPosition(short x, short y);
    void updateWindowContent(int64_t id, void* pixels, short w, short h, short stride, int x, int y);
    void updateWindowContentAHB(int64_t id, AHardwareBuffer* ahb, short w, short h, int x, int y);
    void updateCursorImage(void* pixels, short w, short h, short hotX, short hotY);
    void setCursorVisible(bool visible);
    void setRenderList(const int64_t* ids, const int* xs, const int* ys, int count);
    void removeWindow(int64_t id);
    void clearBackbuffer() {}
    void beginBatch() {}
    void endBatch() {}
    void initScanout();
    void destroyScanout();
    void applyScanoutBuffer();
    void initScanoutFromWindows(ANativeWindow* gameWin, ANativeWindow* cursorWin);
    void scanoutSetDst(int x, int y, int w, int h);
    void scanoutSetBuffer(AHardwareBuffer* ahb, int x, int y, int w, int h, int fenceFd = -1);
    void scanoutSetCursorImage(void* pixels, short w, short h, short stride);
    void scanoutSetCursorPos(short x, short y, short hotX, short hotY);
    // The renderer-neutral scanout implementation. The scanout methods above are
    // thin forwarders to this (see VulkanRendererScanout.cpp); the cursor render-
    // thread signalling stays in the forwarders, not in ScanoutContext.
    ScanoutContext scanout;
    // Bound to scanout's atomics so the existing JNI ABI (r->scanoutActive /
    // r->gameFrameDelivered direct member access) keeps compiling against a
    // single source of truth. Must be declared after `scanout`.
    std::atomic<bool>& scanoutActive = scanout.scanoutActive;
    std::atomic<bool>& gameFrameDelivered = scanout.gameFrameDelivered;
    std::atomic<bool> surfaceDetached{false};

    void detachSurface();
    bool reattachSurface(ANativeWindow* newWindow);

    bool verboseLog = true;
    void setVerboseLog(bool v) { verboseLog = v; scanout.setVerboseLog(v); }
    void dumpRendererInfo();

    std::string adrenoDriverPath;
    std::string adrenoDriverName;
    std::string adrenoNativeLibDir;
    void* vulkanHandle = nullptr;
    std::atomic<bool> scanoutBlackFrameDone{false};
    PFN_vkGetInstanceProcAddr gipa = nullptr;
    VkTable vk_ = {};
    void loadCustomDriver();
    void loadInstanceDispatch();
    void loadDeviceDispatch();

    void setFilterMode(int mode);
    void setUpscaler(int mode);
    void setHqDownscale(bool enabled);
    void setCas(bool enabled, int sharpness);
    void setHdr(bool enabled);
    void setDeband(bool enabled, int strength);
    void setUpscaleSharpness(int sharpness);
    void setFxaa(bool enabled);
    void setToon(bool enabled);
    void setCrt(bool enabled);
    void setNtsc(bool enabled);
    void setColorGrade(float brightness, float contrast, float gamma, float saturation);
    void setSwapRB(bool enabled);
    void setPresentMode(VkPresentModeKHR mode);
    std::vector<int> getSupportedPresentModes() const;

    // --- Native LSFG frame generation -------------------------------------
    // Capability verdict for the compositor-side LSFG engine. Filled at device
    // creation and completed once the swapchain format is known; read by the
    // UI (through JNI) to grey the engine out with a reason.
    const lsfg::Caps& lsfgCaps() const { return lsfgCaps_; }

    // Arm/disarm native LSFG frame generation for this session. Changing the
    // armed state recreates the swapchain, because the composite path needs
    // TRANSFER_DST usage and a deeper image queue that a normal session does
    // not pay for.
    void setFrameGenArmed(bool armed, int multiplier);
    bool frameGenArmed() const { return fgArmed_.load(std::memory_order_relaxed); }
    // Live frame-gen telemetry for the in-game readout:
    //   [0] generations the governor currently trusts
    //   [1] generations actually planned for the last source frame
    //   [2] measured source (real) frames per second
    //   [3] measured presented frames per second
    //   [4] thermal status, -1 when the device gives no signal
    //   [5] GPU milliseconds the chain spends per generated frame, -1 unknown
    void frameGenStats(float out[6]) const;
    // Why the selected native frame-gen engine cannot run here, for the UI:
    // -1 not known yet (caps not probed), 0 fine, 1 this driver lacks what the
    // engine needs (lsfgCaps().reason says which gate), 2 the engine failed to start.
    int frameGenProblem() const;
    // Path to the SPIR-V cache built from the user's Lossless.dll. Setting it
    // drops any existing engine so the next armed frame rebuilds from it.
    void setLsfgCachePath(const char* path);
    // Flow scale (0.25-1.0) and the panel's real refresh rate. The pacer never
    // generates above the refresh rate.
    // Experimental: `captureHeight` sizes the composite ring the chain runs on,
    // the width following the swapchain's aspect, so a phone GPU generates at
    // game-like resolution and the result is blitted up. 0 = panel;
    // kFgCaptureGame = the X screen's height (containerHeight), which already
    // carries the per-game override and render scale; otherwise a pixel height.
    static constexpr int32_t kFgCaptureGame = -1;
    void setFrameGenTuning(float flowScale, float refreshHz, int32_t captureHeight = 0);
    // Which native engine generates: 0 = LSFG (needs the cache built from the
    // user's Lossless.dll), 1 = win-fg (our own chain, embedded, needs nothing).
    // Switching drops the other engine so only one ever holds GPU resources.
    void setFrameGenEngine(int kind);
    // win-fg only: interpolation model (3/4) and performance preset (0..2).
    void setWinFgTuning(int model, int perfPreset);

private:
    struct WinTex {
        VkImage              img            = VK_NULL_HANDLE;
        VkDeviceMemory       mem            = VK_NULL_HANDLE;
        VkImageView          view           = VK_NULL_HANDLE;
        VkDescriptorSet      ds             = VK_NULL_HANDLE;
        VkBuffer             stg            = VK_NULL_HANDLE;
        VkDeviceMemory       stgMem         = VK_NULL_HANDLE;
        void*                mapped         = nullptr;
        VkDeviceSize         cap            = 0;
        int                  w              = 0;
        int                  h              = 0;
        bool                 dirty          = false;
        bool                 isAHB          = false;
        bool                 needsTransition = false;
        AHardwareBuffer*     ahb            = nullptr;
    };

    struct RenderEntry { int64_t id; int x, y; };
    struct DrawEntry {
        VkImage         img            = VK_NULL_HANDLE;
        VkDescriptorSet ds             = VK_NULL_HANDLE;
        VkBuffer        upload         = VK_NULL_HANDLE;
        int             x=0, y=0, w=0, h=0;
        bool            needsTransition = false;
        bool            isAHB          = false;
    };

    ANativeWindow* window;
    int surfaceWidth, surfaceHeight, containerWidth, containerHeight;
    // #413 compositor clip rect (surface px). clipRegionW<=0 => disabled (whole swapchain). Written from
    // the renderer thread (setClipRegion), read from the render thread (recordCmdBuf) -> atomic.
    std::atomic<int> clipRegionX{0}, clipRegionY{0}, clipRegionW{0}, clipRegionH{0};
    void* adrenotoolsHandle = nullptr;
    bool  lsfgVk11Compat_   = false;   // see the constructor
    int filterMode = 0;
    bool swapRB = false;
    float maxAnisotropy           = 1.0f;
    bool  cubicSupported          = false;
    VkPhysicalDeviceMemoryProperties memProperties{};
    VkPresentModeKHR requestedPresentMode = VK_PRESENT_MODE_FIFO_KHR;
    uint32_t graphicsQueueFamilyIndex = 0;
    std::vector<VkPresentModeKHR> availablePresentModes;

    std::unordered_map<int64_t, WinTex>         texMap;

    std::unordered_map<AHardwareBuffer*, WinTex>              ahbImportCache;
    std::unordered_map<int64_t, std::vector<AHardwareBuffer*>> windowAhbs;

    std::vector<WinTex>    deleteQueue;
    std::vector<RenderEntry> renderList;

    std::vector<DrawEntry>             frameDraws;
    std::vector<VkImageMemoryBarrier>  frameAhbTransitions;
    std::vector<VkImageMemoryBarrier>  framePreUpload;
    std::vector<VkImageMemoryBarrier>  framePostUpload;

    // [P0] All SurfaceControl / AHardwareBuffer scanout state moved to
    // ScanoutContext (member `scanout` above). Dead members (lastDst*,
    // ScanoutPending/scanoutPending*, fnSTSetBackPressure) were dropped.

    std::atomic<int>  pointerX{0}, pointerY{0};
    float sceneOffsetX=0.f, sceneOffsetY=0.f, sceneScaleX=1.f, sceneScaleY=1.f;

    std::atomic<bool> cursorVisible{false};
    short  cursorHotX=0, cursorHotY=0, cursorTexW=0, cursorTexH=0;
    std::vector<uint32_t>  cursorPixels;
    std::atomic<bool> isCursorImageDirty{false};
    std::atomic<bool> cursorMoved{false};

    VkImage         cursorImg   = VK_NULL_HANDLE;
    VkDeviceMemory  cursorMem   = VK_NULL_HANDLE;
    VkImageView     cursorView  = VK_NULL_HANDLE;
    VkDescriptorSet  cursorDS   = VK_NULL_HANDLE;
    VkBuffer         cursorStg  = VK_NULL_HANDLE;
    VkDeviceMemory   cursorStgM = VK_NULL_HANDLE;
    void*            cursorStgP = nullptr;
    VkDeviceSize     cursorStgC = 0;
    VkDeviceSize     cursorUploadSize = 0;

    VkInstance       instance;
    VkSurfaceKHR     surface;
    VkPhysicalDevice physicalDevice;
    VkDevice         device;
    VkQueue          graphicsQueue;
    VkSwapchainKHR   swapchain   = VK_NULL_HANDLE;
    VkFormat         swapchainFmt;
    VkExtent2D       swapchainExt;

    std::vector<VkImage>       swapchainImages;
    std::vector<VkImageView>   swapchainViews;
    std::vector<VkFramebuffer> swapchainFBs;

    VkRenderPass          renderPass  = VK_NULL_HANDLE;
    VkDescriptorSetLayout dsLayout    = VK_NULL_HANDLE;
    VkPipelineLayout      pipeLayout  = VK_NULL_HANDLE;

    VkPipeline            pipeline    = VK_NULL_HANDLE;

    // ---- Spatial upscaler (SGSR / FSR1) ----
    // upscalerMode enum (mirrored by JNI nativeSetUpscaler):
    //   0 = none     (passthrough; current sampler governs scaling)
    //   1 = linear   (rebuild base sampler LINEAR; no shader pass)
    //   2 = nearest  (rebuild base sampler NEAREST; no shader pass)
    //   3 = sgsr     (Snapdragon GSR 1.0; single pass; aspect-fit / letterbox)
    //   4 = fsr      (AMD FSR1 EASU+RCAS; two passes; fill / stretch)
    //   5 = fsr_fit  (AMD FSR1 EASU+RCAS; two passes; aspect-fit / letterbox)
    //   6 = sharpen  (RCAS-only; any resolution; aspect-fit / letterbox)
    //   7 = nis      (NVIDIA Image Scaling NVScaler; single pass; aspect-fit)
    //   8 = sgsr_quality (SGSR 1 edge-direction variant; single pass; aspect-fit)
    // Shader upscaling only engages for modes 3-8 AND when the game render
    // resolution (container) is smaller than the swapchain. Otherwise the
    // existing direct-to-swapchain path is used unchanged.
    int               upscalerMode      = 0;
    // Independent of upscalerMode: high-quality Lanczos downscale for the
    // supersampling case (game render res > display res). When enabled and the
    // render res exceeds the swapchain, replaces the bilinear minify with a
    // proper downscale pass. Supersampling and the upscale modes are mutually
    // exclusive in practice (one is render>display, the other render<display).
    bool              hqDownscale       = false;

    // Composable post effects, independent of the scaling mode. CAS layers a sharpen
    // on top of any mode (incl. native res); HDR is a binary fake-HDR. The upscaler
    // sharpness (RCAS lobe scale / SGSR EdgeSharpness) is driven by its own slider as a
    // linear 0..1 value: 0 = neutral (no sharpening; the upscale still runs), 1 = max.
    bool              casEnabled        = false;
    int               casSharpness      = 60;     // slider 0..100 -> CAS SHARPNESS
    bool              hdrEnabled        = false;
    float             upscaleSharpness01 = 0.75f; // slider/100; RCAS lobe scale, SGSR edge derived
    // Debanding: terminal TPDF/IGN dither pass before the 8-bit swapchain quantize.
    bool              debandEnabled     = false;
    int               debandStrength    = 100;    // slider 0..200 -> strength/100 LSBs

    // Phase 2 screen effects (GL EffectComposer parity). FXAA/Toon/CRT/NTSC are
    // binary; Color is the brightness/contrast/gamma grade (always-applied via the
    // sliders, no toggle) and is "enabled" only when not at neutral (0,0,1).
    bool              fxaaEnabled       = false;
    bool              toonEnabled       = false;
    bool              crtEnabled        = false;
    bool              ntscEnabled       = false;
    bool              colorEnabled      = false;   // derived: !neutral grade
    float             colorBrightness   = 0.0f;    // [-1,1] (slider/100, clamped)
    float             colorContrast     = 0.0f;    // [0,2]  (slider/100, clamped)
    float             colorGamma        = 1.0f;    // [0.1,5]
    float             colorSaturation   = 1.0f;    // [0,2]  (slider/100, clamped)
    uint32_t          ntscFrameCounter  = 0;       // animates NTSC chroma phase

    VkSampler         upscaleSampler    = VK_NULL_HANDLE; // linear clamp; offscreen/mid input

    // === Native LSFG: composite target ring ===================================
    // Frame generation cannot composite straight into a swapchain image: the
    // finished frame has to be READABLE (it becomes the next frame's LSFG
    // input) and generated frames have to be STORAGE-WRITABLE by a compute
    // dispatch. Android swapchain images are COLOR_ATTACHMENT only, and
    // storage support on a swapchain format is not something a driver owes us.
    //
    // So when frame gen is armed the whole existing recording is redirected at
    // a composite image we own — format-identical to the swapchain, so every
    // existing pipeline stays render-pass compatible — and a copy moves it into
    // the acquired swapchain image at the end. With frame gen off, not one of
    // these objects is created and the direct-to-swapchain path is untouched.
    struct CompositeTarget {
        VkImage         img         = VK_NULL_HANDLE;
        VkDeviceMemory  mem         = VK_NULL_HANDLE;
        VkImageView     view        = VK_NULL_HANDLE;  // colour attachment + sampled
        VkImageView     storageView = VK_NULL_HANDLE;  // compute writes (generate)
        VkFramebuffer   fb          = VK_NULL_HANDLE;
        VkDescriptorSet ds          = VK_NULL_HANDLE;  // sampled, for later passes
    };
    // Hard ceiling: (max generations + 1) presentable frames per source frame,
    // times a queue depth of 2, capped so the footprint stays bounded.
    static constexpr uint32_t kMaxCompositeTargets = 7;

    std::vector<CompositeTarget> compositeTargets;
    VkRenderPass compositeRenderPass = VK_NULL_HANDLE;  // CLEAR -> GENERAL
    uint32_t     compositeW = 0, compositeH = 0;
    uint32_t     compositeIndex = 0;      // rotates per composite; gives history for free
    bool         compositeArmed = false;  // targets exist AND this frame uses them
    bool         swapchainTransferDst = false; // swapchain was created with TRANSFER_DST

    // Set from the app when the native LSFG engine is selected for this
    // session. Read on the render thread; false keeps every path as it was.
    std::atomic<bool> fgArmed_{false};
    std::atomic<int>  fgMultiplier_{0};
    // Tuning is written by the UI thread and consumed by the render thread, so
    // it is published through atomics and applied to the engine at the top of
    // the frame-gen block. The UI thread never touches the engine itself.
    std::atomic<float> fgFlowScale_{1.0f};
    std::atomic<float> fgRefreshHz_{0.0f};
    std::atomic<bool>  fgConfigDirty_{true};
    // Experimental (see setFrameGenTuning): capture height of the composite
    // ring. 0 = panel resolution, kFgCaptureGame = the X screen's height.
    std::atomic<int32_t> fgCaptureHeight_{0};
    // Extent the composite ring should have for the current swapchain and
    // capture height (width follows the swapchain aspect, both even).
    void compositeExtentFor(uint32_t& w, uint32_t& h) const;
    // The extent the scene is rendered at this frame: the composite ring while
    // frame gen runs on a ring smaller than the swapchain (experimental capture
    // resolution), otherwise the swapchain. Every full-target pass (the effect
    // chain, the spatial upscalers, the render-scale downscale) sizes itself by
    // this, so those keep working below panel resolution and their
    // intermediates shrink with the ring.
    VkExtent2D renderExtent() const {
        if (compositeActive() && (compositeW != swapchainExt.width || compositeH != swapchainExt.height))
            return VkExtent2D{compositeW, compositeH};
        return swapchainExt;
    }
    // Copy (same extent) or blit (composite smaller than the swapchain) a
    // composite-ring image, already in TRANSFER_SRC, into a swapchain image
    // already in TRANSFER_DST.
    void recordCompositeToSwapchainTransfer(VkCommandBuffer cb, VkImage src, uint32_t imgIdx);

    bool  createCompositeRenderPass();
    bool  ensureCompositeTargets(uint32_t w, uint32_t h, uint32_t count);
    void  destroyCompositeTargets();
    // True when this frame should composite off-swapchain. Call on the render
    // thread; every gate must hold or we fall back to the untouched path.
    bool  compositeActive() const;
    // Render pass / framebuffer this frame draws its FINAL pass into.
    VkRenderPass  targetRenderPass() const;
    VkFramebuffer targetFramebuffer(uint32_t imgIdx) const;
    // Copy the finished composite into the acquired swapchain image and leave
    // it in PRESENT_SRC. No-op when the composite path is not active.
    void  copyCompositeToSwapchain(VkCommandBuffer cb, uint32_t imgIdx);

    // === Native LSFG: the software cursor ====================================
    // LSFG interpolates whatever it is given, so a cursor composited into the
    // frame gets warped along the flow field and smears. It is therefore
    // excluded from the composite while frame gen is armed and drawn once into
    // EVERY presented image instead - real and generated alike - through a
    // load-op render pass that leaves the image ready to present.
    VkRenderPass cursorOverlayRenderPass = VK_NULL_HANDLE;
    struct CursorOverlay {
        bool  draw = false;
        float ox = 0, oy = 0, sx = 0, sy = 0, cw = 0, ch = 0;
        short ptrX = 0, ptrY = 0, hotX = 0, hotY = 0, w = 0, h = 0;
    };
    CursorOverlay cursorOverlay_{};

    bool createCursorOverlayRenderPass();
    // Draw the cursor into an already-copied swapchain image and leave it in
    // PRESENT_SRC. Takes over the final transition from the copy helpers.
    void recordCursorOverlay(VkCommandBuffer cb, uint32_t imgIdx);
    // True when the cursor is being drawn per present rather than composited.
    bool cursorDrawnPerPresent() const;

    // === Native LSFG: the per-source-frame present plan =======================
    // Interpolation produces frames that belong BETWEEN N-1 and N, so the
    // generated frames are presented FIRST and real frame N is held back one
    // slot. All presents for one source frame are queued together: with FIFO
    // the driver then shows them on consecutive vblanks, so the pacing falls
    // out of the present mode for free - no sleeps, no render-mode change.
    static constexpr uint32_t kMaxPresentsPerFrame = 4;   // 1 real + up to 3 generated
    struct FrameGenPlan {
        uint32_t generations = 0;
        uint32_t presents    = 1;
        uint32_t imgIdx[kMaxPresentsPerFrame] = {};
    };
    FrameGenPlan fgPlan_{};
    std::unique_ptr<lsfg::Engine> lsfgEngine_;
    uint64_t    fgSourceFrames_ = 0;
    std::string lsfgCachePath_;
    bool        lsfgEngineTried_ = false;
    std::unique_ptr<winfg::Engine> winfgEngine_;
    bool        winfgEngineTried_ = false;
    std::atomic<int> fgEngineKind_{0};     // 0 = lsfg, 1 = win-fg
    std::atomic<int> fgModel_{4};
    std::atomic<int> fgPerfPreset_{1};
    bool ensureWinFgEngine();
    // Capability gate for the SELECTED engine. win-fg's shaders need only a
    // storage-capable swapchain format; the LSFG chain also needs the fp16 /
    // memory-model feature set, which some otherwise capable GPUs lack.
    bool fgCapsOk() const;

    // Sync objects are indexed per PRESENT, not per composite: each pending
    // present needs its own image-available and render-finished semaphore.
    uint32_t syncSlot(uint32_t k) const { return currentFrame * kMaxPresentsPerFrame + k; }
    // Destroy and rebuild every acquire/present semaphore. Called with the
    // swapchain, so no stale pending signal can survive into the new one.
    void recreateSyncObjects();
    uint32_t fgAcquireFailLog_ = 0;

    // Measured PRESENTS per second - the rate that actually reaches the panel,
    // counting generated frames. The pacer's own "loop rate" cannot serve this
    // purpose: it is sampled once per SOURCE frame, so it always equals the
    // guest rate and contains no evidence that generation happened at all.
    float    fgPresentedRate_   = 0.0f;
    // Measured SOURCE frames per second over the same window, for engines that
    // do not measure it themselves (win-fg).
    float    fgSourceRate_      = 0.0f;
    uint32_t fgSourceAccum_     = 0;
    uint32_t fgPresentAccum_    = 0;
    std::chrono::steady_clock::time_point fgRateWindowStart_{};
    bool     fgRateWindowOpen_  = false;
    void     trackPresentedRate(uint32_t presents);

    bool ensureLsfgEngine();
    // One command buffer per pending present: slot 0 carries the composite,
    // the chain's shared passes and generated frame 0; each later generated
    // frame and the real frame are recorded and submitted on their own, so a
    // finished frame reaches the presentation engine without waiting for the
    // rest of the chain.
    uint32_t cmdSlot(uint32_t k) const { return currentFrame * kMaxPresentsPerFrame + k; }
    // Shared chain passes (the 24 shaders every generated frame depends on).
    void recordFrameGenProcess(VkCommandBuffer cb);
    // Generated frame g: synthesise into a spare composite target and copy it
    // into the swapchain image reserved for it.
    void recordFrameGenGeneration(VkCommandBuffer cb, uint32_t g);

    // Chain cost: a timestamp pair per frame slot, start before the shared
    // passes, end after the last generation's compute (before its copy, so a
    // vblank wait on the swapchain image is not counted). Read back after the
    // slot's fence wait, one frame later.
    VkQueryPool fgQueryPool_        = VK_NULL_HANDLE;
    bool        fgTimestampsOk_     = false;
    // Arm Mali (vendorID 0x13B5): set in createLogicalDevice; gates the Mali-only paths below.
    bool        isMaliDevice_       = false;
    float       fgTimestampPeriodNs_ = 0.0f;
    bool        fgQueryPending_[MAX_FRAMES_IN_FLIGHT] = {};
    uint32_t    fgQueryGens_[MAX_FRAMES_IN_FLIGHT]    = {};
    float       fgChainMsPerGen_    = -1.0f;
    uint32_t    fgChainLogCount_    = 0;
    void ensureFgQueryPool();
    void destroyFgQueryPool();
    void readFgQueryResult();

    VkFormat          offscreenFmt      = VK_FORMAT_R8G8B8A8_UNORM;
    VkRenderPass      offscreenRenderPass = VK_NULL_HANDLE; // CLEAR -> SHADER_READ_ONLY
    VkPipelineLayout  postPipeLayout    = VK_NULL_HANDLE;
    VkPipeline        sgsrPipeline      = VK_NULL_HANDLE;
    VkPipeline        nisPipeline       = VK_NULL_HANDLE; // NVIDIA Image Scaling (mode 7)
    VkPipeline        sgsrQualityPipeline = VK_NULL_HANDLE; // SGSR 1 edge-direction (mode 8)
    VkPipeline        easuPipeline      = VK_NULL_HANDLE;
    VkPipeline        rcasPipeline      = VK_NULL_HANDLE;
    VkPipeline        downscalePipeline = VK_NULL_HANDLE;
    // CAS/HDR can be either a non-final pass (writes an fx target via
    // offscreenRenderPass) or the final pass (writes the swapchain via renderPass);
    // a pipeline is render-pass-specific, so keep one variant of each.
    VkPipeline        casPipelineOff    = VK_NULL_HANDLE; // -> fx target (offscreenRenderPass)
    VkPipeline        casPipelineSwap   = VK_NULL_HANDLE; // -> swapchain (renderPass)
    VkPipeline        hdrPipelineOff    = VK_NULL_HANDLE;
    VkPipeline        hdrPipelineSwap   = VK_NULL_HANDLE;
    // Phase 2 effects: same Off (-> fx target) / Swap (-> swapchain) variant pair.
    VkPipeline        fxaaPipelineOff   = VK_NULL_HANDLE;
    VkPipeline        fxaaPipelineSwap  = VK_NULL_HANDLE;
    VkPipeline        toonPipelineOff   = VK_NULL_HANDLE;
    VkPipeline        toonPipelineSwap  = VK_NULL_HANDLE;
    VkPipeline        colorPipelineOff  = VK_NULL_HANDLE;
    VkPipeline        colorPipelineSwap = VK_NULL_HANDLE;
    VkPipeline        ntscPipelineOff   = VK_NULL_HANDLE;
    VkPipeline        ntscPipelineSwap  = VK_NULL_HANDLE;
    VkPipeline        crtPipelineOff    = VK_NULL_HANDLE;
    VkPipeline        crtPipelineSwap   = VK_NULL_HANDLE;
    // Debanding is always the LAST effect (terminal) -> only a swapchain variant.
    VkPipeline        debandPipelineSwap = VK_NULL_HANDLE;

    // offscreen composite target @ game/container resolution
    VkImage           offscreenImg  = VK_NULL_HANDLE;
    VkDeviceMemory    offscreenMem  = VK_NULL_HANDLE;
    VkImageView       offscreenView = VK_NULL_HANDLE;
    VkFramebuffer     offscreenFB   = VK_NULL_HANDLE;
    VkDescriptorSet   offscreenDS   = VK_NULL_HANDLE;
    int               offscreenW = 0, offscreenH = 0;

    // FSR intermediate (EASU output) @ upscale output resolution
    VkImage           midImg  = VK_NULL_HANDLE;
    VkDeviceMemory    midMem  = VK_NULL_HANDLE;
    VkImageView       midView = VK_NULL_HANDLE;
    VkFramebuffer     midFB   = VK_NULL_HANDLE;
    VkDescriptorSet   midDS   = VK_NULL_HANDLE;
    int               midW = 0, midH = 0;

    // Effect-chain intermediates @ swapchain resolution. fx1 holds the scaled (or
    // composited) image fed into CAS/HDR; fx2 ping-pongs when both effects run.
    VkImage           fx1Img  = VK_NULL_HANDLE;
    VkDeviceMemory    fx1Mem  = VK_NULL_HANDLE;
    VkImageView       fx1View = VK_NULL_HANDLE;
    VkFramebuffer     fx1FB   = VK_NULL_HANDLE;
    VkDescriptorSet   fx1DS   = VK_NULL_HANDLE;
    int               fx1W = 0, fx1H = 0;

    VkImage           fx2Img  = VK_NULL_HANDLE;
    VkDeviceMemory    fx2Mem  = VK_NULL_HANDLE;
    VkImageView       fx2View = VK_NULL_HANDLE;
    VkFramebuffer     fx2FB   = VK_NULL_HANDLE;
    VkDescriptorSet   fx2DS   = VK_NULL_HANDLE;
    int               fx2W = 0, fx2H = 0;

    // Per-frame upscale plan, computed in renderFrame, consumed by recordCmdBuf.
    // upFrame.mode reuses the upscalerMode enum (3=sgsr,4=fsr,5=fsr_fit,6=sharpen,7=nis,8=sgsr_quality)
    // plus an internal sentinel (UPMODE_DOWNSCALE) for the supersampling path.
    struct UpscaleFrame {
        bool active = false;
        int  mode = 0;
        int  outX = 0, outY = 0, outW = 0, outH = 0;
        bool cas = false;   // run CAS this frame (snapshot of casEnabled)
        bool hdr = false;   // run HDR this frame (snapshot of hdrEnabled)
        bool fxaa = false;  // snapshot of fxaaEnabled
        bool toon = false;  // snapshot of toonEnabled
        bool color = false; // snapshot of colorEnabled (non-neutral grade)
        bool ntsc = false;  // snapshot of ntscEnabled
        bool crt = false;   // snapshot of crtEnabled
        bool deband = false;// snapshot of debandEnabled (terminal dither)
        SgsrPushConstants      sgsrPC{};
        NisPushConstants       nisPC{};
        EasuPushConstants      easuPC{};
        RcasPushConstants      rcasPC{};
        DownscalePushConstants dsPC{};
        CasPushConstants       casPC{};
        HdrPushConstants       hdrPC{};
        FxaaPushConstants      fxaaPC{};
        ToonPushConstants      toonPC{};
        ColorPushConstants     colorPC{};
        NtscPushConstants      ntscPC{};
        CrtPushConstants       crtPC{};
        DebandPushConstants    debandPC{};
    } upFrame;

    VkCommandPool                cmdPool = VK_NULL_HANDLE;
    std::vector<VkCommandBuffer> cmdBufs;

    std::vector<VkSemaphore> imgAvailSems;
    std::vector<VkSemaphore> renderDoneSems;
    std::vector<VkFence>     inFlightFences;
    std::vector<VkFence>     imgInFlight;
    uint32_t                 currentFrame = 0;

    VkSampler        sampler    = VK_NULL_HANDLE;
    VkDescriptorPool winTexPool = VK_NULL_HANDLE;

    std::atomic<bool> needsRender{false};
    std::thread       renderThread;
    std::atomic<bool> isRunning{false};
    std::atomic<bool> fbResized{false};
    std::mutex        renderMutex;
    std::mutex        dirtyMutex;
    std::condition_variable dirtyCV;
    std::shared_mutex frameMutex;

    void createInstance();
    void createSurface();
    void pickPhysicalDevice();
    lsfg::Caps lsfgCaps_{};

    void createLogicalDevice();
    void createSwapchain();
    void createRenderPass();
    void createDSLayout();
    void createPipeline(bool blend, VkPipeline& out);
    void createFramebuffers();
    void createCmdPool();
    void createSampler();
    void createUpscaleSampler();
    void createOffscreenRenderPass();
    void createPostPipelines();
    VkPipeline createPostPipeline(const uint32_t* fragCode, size_t fragSz, VkRenderPass rp);
    bool createColorTarget(int w, int h, VkImage& img, VkDeviceMemory& mem,
                           VkImageView& view, VkFramebuffer& fb, VkDescriptorSet& ds);
    void destroyColorTarget(VkImage& img, VkDeviceMemory& mem, VkImageView& view,
                            VkFramebuffer& fb, VkDescriptorSet& ds);
    bool ensureOffscreen(int w, int h);
    bool ensureMid(int w, int h);
    bool ensureFx1(int w, int h);
    bool ensureFx2(int w, int h);
    void recordUpscalePasses(VkCommandBuffer cb, uint32_t imgIdx,
                             const std::vector<DrawEntry>& draws, bool cursorDrawn,
                             short ptrX, short ptrY, short curHotX, short curHotY,
                             short curW, short curH,
                             float ox, float oy, float sx, float sy,
                             float cw, float ch);
    void planUpscaleFrame();
    void createWinTexPool();
    void createCursorPipeline();
    void createCursorDS();
    void createCmdBufs();
    void createSyncObjects();
    void cleanupSwapchain();

    bool  createWinTexResources(WinTex& wt, int w, int h);
    bool  importAHBToWinTex(WinTex& wt, AHardwareBuffer* ahb);
    void  cleanupAllAHBCache();
    void  flushDeleteQueue();
    void  destroyWinTex(WinTex& wt);
    void  ensureCursorTex(short w, short h);
    void  cleanupCursorTex();
    void  ensureCursorStaging(VkDeviceSize sz);

    void recordCmdBuf(VkCommandBuffer cb, uint32_t imgIdx,
        const std::vector<DrawEntry>& draws,
        std::vector<VkImageMemoryBarrier>& ahbTransitions,
        std::vector<VkImageMemoryBarrier>& preUpload,
        std::vector<VkImageMemoryBarrier>& postUpload,
        VkBuffer cursorUpload, bool hasCursorUpload,
        float ox, float oy, float sx, float sy, float cw, float ch,
        short ptrX, short ptrY, short curHotX, short curHotY,
        short curW, short curH, bool curVis);
    void renderLoop();
    void renderFrame();

    uint32_t        findMemType(uint32_t filter, VkMemoryPropertyFlags props);
    void            createBuffer(VkDeviceSize sz, VkBufferUsageFlags usage,
                                 VkMemoryPropertyFlags props, VkBuffer& buf, VkDeviceMemory& mem);
    VkCommandBuffer beginOneTime();
    void            endOneTime(VkCommandBuffer cmd);
    void            transition(VkCommandBuffer cmd, VkImage img,
                               VkImageLayout oldL, VkImageLayout newL,
                               VkAccessFlags srcA, VkAccessFlags dstA,
                               VkPipelineStageFlags srcS, VkPipelineStageFlags dstS);
    VkShaderModule  makeShader(const uint32_t* code, size_t sz);
};
