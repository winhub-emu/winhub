#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wmissing-field-initializers"
#include "VulkanRendererContext.h"
#include "lsfg/lsfg_engine.h"
#include "lsfg/lsfg_vkd.h"
#include "winfg/winfg_engine.h"

// Set by CMake from the copied win-fg chain (upstream tag + a hash over the
// chain sources). Defaulted so this still compiles if it is ever built without
// them - an "unknown" in a log is a missing stamp, not a broken build.
#ifndef WINFG_UPSTREAM
#define WINFG_UPSTREAM "unknown"
#endif
#ifndef WINFG_CHAIN_HASH
#define WINFG_CHAIN_HASH "unknown"
#endif
#include <stdexcept>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <inttypes.h>
#include <cmath>
#include <dlfcn.h>
#include "window_vert.h"
#include "window_frag.h"
#include "upscale_vert.h"
#include "sgsr_frag.h"
#include "sgsr_quality_frag.h"
#include "nis_frag.h"
#include "fsr_easu_frag.h"
#include "fsr_rcas_frag.h"
#include "downscale_frag.h"
#include "cas_frag.h"
#include "hdr_frag.h"
#include "fxaa_frag.h"
#include "toon_frag.h"
#include "color_frag.h"
#include "ntsc_frag.h"
#include "crt_frag.h"
#include "deband_frag.h"

// Internal sentinel for upFrame.mode: high-quality supersampling downscale.
// (Not a user-selectable upscalerMode; gated by the hqDownscale flag.)
static constexpr int UPMODE_DOWNSCALE = 10;

// ---- CPU-side FSR1 constant setup (mirrors ffx_fsr1.h FsrEasuCon/FsrRcasCon) ----
static inline uint32_t fsrPackF(float f) { uint32_t u; memcpy(&u, &f, 4); return u; }

static void fsrEasuCon(uint32_t con0[4], uint32_t con1[4], uint32_t con2[4], uint32_t con3[4],
                       float inViewportX, float inViewportY,
                       float inSizeX, float inSizeY,
                       float outX, float outY) {
    con0[0] = fsrPackF(inViewportX / outX);
    con0[1] = fsrPackF(inViewportY / outY);
    con0[2] = fsrPackF(0.5f * inViewportX / outX - 0.5f);
    con0[3] = fsrPackF(0.5f * inViewportY / outY - 0.5f);
    con1[0] = fsrPackF(1.0f / inSizeX);
    con1[1] = fsrPackF(1.0f / inSizeY);
    con1[2] = fsrPackF(1.0f / inSizeX);
    con1[3] = fsrPackF(-1.0f / inSizeY);
    con2[0] = fsrPackF(-1.0f / inSizeX);
    con2[1] = fsrPackF(2.0f / inSizeY);
    con2[2] = fsrPackF(1.0f / inSizeX);
    con2[3] = fsrPackF(2.0f / inSizeY);
    con3[0] = fsrPackF(0.0f);
    con3[1] = fsrPackF(4.0f / inSizeY);
    con3[2] = 0;
    con3[3] = 0;
}

static void fsrRcasCon(uint32_t con[4], float rcasScale) {
    // The shader uses con.x as a direct multiplier on the RCAS lobe, so the linear slider
    // scale maps straight through: 0 = no sharpening (passthrough), 1 = full RCAS (the
    // exp2(-0 stops) ceiling). No over-drive past spec.
    con[0] = fsrPackF(rcasScale);
    con[1] = 0;                            // fp16 packed sharpness (unused by fp32 path)
    con[2] = 0;
    con[3] = 0;
}

VulkanRendererContext::VulkanRendererContext(ANativeWindow* win, int cW, int cH, void* aHandle,
                                             bool lsfgVk11Compat)
    : window(win), surfaceWidth(cW), surfaceHeight(cH), containerWidth(cW), containerHeight(cH),
      adrenotoolsHandle(aHandle), lsfgVk11Compat_(lsfgVk11Compat)
{
    // Seed the renderer-neutral scanout impl with the owner's window/sizes.
    scanout.setFallbackWindow(window);
    scanout.setContainerSize(containerWidth, containerHeight);
    scanout.setSurfaceSize(surfaceWidth, surfaceHeight);
    scanout.setVerboseLog(verboseLog);

    createInstance(); createSurface(); pickPhysicalDevice(); createLogicalDevice();
    createSwapchain(); createRenderPass(); createDSLayout();
    createPipeline(true, pipeline);
    createFramebuffers(); createCmdPool(); createSampler();
    createUpscaleSampler(); createOffscreenRenderPass(); createPostPipelines();
    createWinTexPool(); createCursorDS(); createCmdBufs(); createSyncObjects();
    isRunning = true;
    renderThread = std::thread(&VulkanRendererContext::renderLoop, this);
}

VulkanRendererContext::~VulkanRendererContext() {
    isRunning = false; dirtyCV.notify_all();
    if (renderThread.joinable()) renderThread.join();
    std::lock_guard<std::mutex> lk(renderMutex);
    vk_.DeviceWaitIdle(device);
    for (auto& [id, wt] : texMap) destroyWinTex(wt);
    texMap.clear();
    
    for (auto& wt : deleteQueue) {
        if (wt.ds   != VK_NULL_HANDLE) vk_.FreeDescriptorSets(device, winTexPool, 1, &wt.ds);
        if (wt.view != VK_NULL_HANDLE) vk_.DestroyImageView(device, wt.view, nullptr);
        if (wt.img  != VK_NULL_HANDLE) vk_.DestroyImage(device, wt.img, nullptr);
        if (wt.mem  != VK_NULL_HANDLE) vk_.FreeMemory(device, wt.mem, nullptr);
        if (wt.stg  != VK_NULL_HANDLE) { vk_.DestroyBuffer(device, wt.stg, nullptr); vk_.FreeMemory(device, wt.stgMem, nullptr); }
    }
    deleteQueue.clear();
    cleanupSwapchain(); cleanupCursorTex();

    // upscaler resources (DS freed back to winTexPool while it is still alive)
    destroyColorTarget(offscreenImg, offscreenMem, offscreenView, offscreenFB, offscreenDS);
    destroyColorTarget(midImg, midMem, midView, midFB, midDS);
    destroyColorTarget(fx1Img, fx1Mem, fx1View, fx1FB, fx1DS);
    destroyColorTarget(fx2Img, fx2Mem, fx2View, fx2FB, fx2DS);
    if (sgsrPipeline      != VK_NULL_HANDLE) vk_.DestroyPipeline(device, sgsrPipeline, nullptr);
    if (sgsrQualityPipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, sgsrQualityPipeline, nullptr);
    if (nisPipeline       != VK_NULL_HANDLE) vk_.DestroyPipeline(device, nisPipeline, nullptr);
    if (easuPipeline      != VK_NULL_HANDLE) vk_.DestroyPipeline(device, easuPipeline, nullptr);
    if (rcasPipeline      != VK_NULL_HANDLE) vk_.DestroyPipeline(device, rcasPipeline, nullptr);
    if (downscalePipeline != VK_NULL_HANDLE) vk_.DestroyPipeline(device, downscalePipeline, nullptr);
    if (casPipelineOff    != VK_NULL_HANDLE) vk_.DestroyPipeline(device, casPipelineOff, nullptr);
    if (casPipelineSwap   != VK_NULL_HANDLE) vk_.DestroyPipeline(device, casPipelineSwap, nullptr);
    if (hdrPipelineOff    != VK_NULL_HANDLE) vk_.DestroyPipeline(device, hdrPipelineOff, nullptr);
    if (hdrPipelineSwap   != VK_NULL_HANDLE) vk_.DestroyPipeline(device, hdrPipelineSwap, nullptr);
    if (fxaaPipelineOff   != VK_NULL_HANDLE) vk_.DestroyPipeline(device, fxaaPipelineOff, nullptr);
    if (fxaaPipelineSwap  != VK_NULL_HANDLE) vk_.DestroyPipeline(device, fxaaPipelineSwap, nullptr);
    if (toonPipelineOff   != VK_NULL_HANDLE) vk_.DestroyPipeline(device, toonPipelineOff, nullptr);
    if (toonPipelineSwap  != VK_NULL_HANDLE) vk_.DestroyPipeline(device, toonPipelineSwap, nullptr);
    if (colorPipelineOff  != VK_NULL_HANDLE) vk_.DestroyPipeline(device, colorPipelineOff, nullptr);
    if (colorPipelineSwap != VK_NULL_HANDLE) vk_.DestroyPipeline(device, colorPipelineSwap, nullptr);
    if (ntscPipelineOff   != VK_NULL_HANDLE) vk_.DestroyPipeline(device, ntscPipelineOff, nullptr);
    if (ntscPipelineSwap  != VK_NULL_HANDLE) vk_.DestroyPipeline(device, ntscPipelineSwap, nullptr);
    if (crtPipelineOff    != VK_NULL_HANDLE) vk_.DestroyPipeline(device, crtPipelineOff, nullptr);
    if (crtPipelineSwap   != VK_NULL_HANDLE) vk_.DestroyPipeline(device, crtPipelineSwap, nullptr);
    if (debandPipelineSwap!= VK_NULL_HANDLE) vk_.DestroyPipeline(device, debandPipelineSwap, nullptr);
    if (postPipeLayout    != VK_NULL_HANDLE) vk_.DestroyPipelineLayout(device, postPipeLayout, nullptr);
    if (offscreenRenderPass != VK_NULL_HANDLE) vk_.DestroyRenderPass(device, offscreenRenderPass, nullptr);
    if (compositeRenderPass != VK_NULL_HANDLE) { vk_.DestroyRenderPass(device, compositeRenderPass, nullptr); compositeRenderPass = VK_NULL_HANDLE; }
    if (cursorOverlayRenderPass != VK_NULL_HANDLE) { vk_.DestroyRenderPass(device, cursorOverlayRenderPass, nullptr); cursorOverlayRenderPass = VK_NULL_HANDLE; }
    if (upscaleSampler    != VK_NULL_HANDLE) vk_.DestroySampler(device, upscaleSampler, nullptr);

    vk_.DestroySampler(device, sampler, nullptr);
    vk_.DestroyDescriptorPool(device, winTexPool, nullptr);
    vk_.DestroyPipeline(device, pipeline, nullptr);
    vk_.DestroyPipelineLayout(device, pipeLayout, nullptr);
    vk_.DestroyDescriptorSetLayout(device, dsLayout, nullptr);
    // Semaphores are per pending present, fences per frame slot.
    for (size_t i = 0; i < renderDoneSems.size(); i++)
        vk_.DestroySemaphore(device, renderDoneSems[i], nullptr);
    for (size_t i = 0; i < imgAvailSems.size(); i++)
        vk_.DestroySemaphore(device, imgAvailSems[i], nullptr);
    for (size_t i = 0; i < inFlightFences.size(); i++)
        vk_.DestroyFence(device, inFlightFences[i], nullptr);
    lsfgEngine_.reset();
    winfgEngine_.reset();
    destroyFgQueryPool();
    vk_.DestroyCommandPool(device, cmdPool, nullptr);
    vk_.DestroyRenderPass(device, renderPass, nullptr);
    vk_.DestroyDevice(device, nullptr);
    vk_.DestroySurfaceKHR(instance, surface, nullptr);
    vk_.DestroyInstance(instance, nullptr);
    if (adrenotoolsHandle) { dlclose(adrenotoolsHandle); adrenotoolsHandle = nullptr; }
}

void VulkanRendererContext::loadInstanceDispatch() {
    auto i = [&](const char* name) { return gipa ? gipa(instance, name) : nullptr; };
#define LOAD_I2(fn) vk_.fn = (PFN_vk##fn)i("vk"#fn)
    LOAD_I2(DestroyInstance);
    LOAD_I2(EnumeratePhysicalDevices);
    LOAD_I2(GetPhysicalDeviceProperties);
    LOAD_I2(GetPhysicalDeviceMemoryProperties);
    LOAD_I2(GetPhysicalDeviceSurfaceCapabilitiesKHR);
    LOAD_I2(GetPhysicalDeviceSurfaceFormatsKHR);
    LOAD_I2(GetPhysicalDeviceSurfacePresentModesKHR);
    LOAD_I2(GetPhysicalDeviceQueueFamilyProperties);
    LOAD_I2(GetPhysicalDeviceSurfaceSupportKHR);
    LOAD_I2(GetPhysicalDeviceFormatProperties);
    LOAD_I2(GetPhysicalDeviceFeatures2);
    LOAD_I2(CreateDevice);
    LOAD_I2(DestroySurfaceKHR);
    LOAD_I2(CreateAndroidSurfaceKHR);
    LOAD_I2(GetDeviceProcAddr);
}

void VulkanRendererContext::loadDeviceDispatch() {
    auto d = [&](const char* name) -> PFN_vkVoidFunction {
        return vk_.GetDeviceProcAddr ? vk_.GetDeviceProcAddr(device, name) : nullptr;
    };
#define LOAD_D2(fn) vk_.fn = (PFN_vk##fn)d("vk"#fn)
    LOAD_D2(DestroyDevice);
    LOAD_D2(GetDeviceQueue);
    LOAD_D2(DeviceWaitIdle);
    LOAD_D2(CreateSwapchainKHR);
    LOAD_D2(DestroySwapchainKHR);
    LOAD_D2(GetSwapchainImagesKHR);
    LOAD_D2(AcquireNextImageKHR);
    LOAD_D2(QueuePresentKHR);
    LOAD_D2(QueueSubmit);
    LOAD_D2(CreateQueryPool);
    LOAD_D2(DestroyQueryPool);
    LOAD_D2(CmdResetQueryPool);
    LOAD_D2(CmdWriteTimestamp);
    LOAD_D2(GetQueryPoolResults);
    LOAD_D2(CreateRenderPass);
    LOAD_D2(DestroyRenderPass);
    LOAD_D2(CreateFramebuffer);
    LOAD_D2(DestroyFramebuffer);
    LOAD_D2(CreateImageView);
    LOAD_D2(DestroyImageView);
    LOAD_D2(CreateImage);
    LOAD_D2(DestroyImage);
    LOAD_D2(CreateBuffer);
    LOAD_D2(DestroyBuffer);
    LOAD_D2(AllocateMemory);
    LOAD_D2(FreeMemory);
    LOAD_D2(MapMemory);
    LOAD_D2(FlushMappedMemoryRanges);
    LOAD_D2(BindBufferMemory);
    LOAD_D2(BindImageMemory);
    LOAD_D2(GetBufferMemoryRequirements);
    LOAD_D2(GetImageMemoryRequirements);
    LOAD_D2(CreateDescriptorSetLayout);
    LOAD_D2(DestroyDescriptorSetLayout);
    LOAD_D2(CreateDescriptorPool);
    LOAD_D2(DestroyDescriptorPool);
    LOAD_D2(AllocateDescriptorSets);
    LOAD_D2(FreeDescriptorSets);
    LOAD_D2(UpdateDescriptorSets);
    LOAD_D2(CreatePipelineLayout);
    LOAD_D2(DestroyPipelineLayout);
    LOAD_D2(CreateShaderModule);
    LOAD_D2(DestroyShaderModule);
    LOAD_D2(CreateGraphicsPipelines);
    LOAD_D2(DestroyPipeline);
    LOAD_D2(CreateCommandPool);
    LOAD_D2(DestroyCommandPool);
    LOAD_D2(AllocateCommandBuffers);
    LOAD_D2(FreeCommandBuffers);
    LOAD_D2(BeginCommandBuffer);
    LOAD_D2(EndCommandBuffer);
    LOAD_D2(CmdDispatch);
    LOAD_D2(CreateComputePipelines);
    LOAD_D2(UnmapMemory);
    LOAD_D2(CmdClearColorImage);
    LOAD_D2(ResetDescriptorPool);
    LOAD_D2(ResetCommandBuffer);
    LOAD_D2(CmdBeginRenderPass);
    LOAD_D2(CmdEndRenderPass);
    LOAD_D2(CmdBindPipeline);
    LOAD_D2(CmdBindDescriptorSets);
    LOAD_D2(CmdDraw);
    LOAD_D2(CmdPushConstants);
    LOAD_D2(CmdSetViewport);
    LOAD_D2(CmdSetScissor);
    LOAD_D2(CmdPipelineBarrier);
    LOAD_D2(CmdCopyImage);
    LOAD_D2(CmdBlitImage);
    LOAD_D2(CmdCopyBufferToImage);
    LOAD_D2(CreateSampler);
    LOAD_D2(DestroySampler);
    LOAD_D2(CreateSemaphore);
    LOAD_D2(DestroySemaphore);
    LOAD_D2(CreateFence);
    LOAD_D2(DestroyFence);
    LOAD_D2(WaitForFences);
    LOAD_D2(ResetFences);
    LOAD_D2(GetFenceStatus);

    vk_.GetAndroidHardwareBufferPropertiesANDROID =
        (PFN_vkGetAndroidHardwareBufferPropertiesANDROID)d("vkGetAndroidHardwareBufferPropertiesANDROID");
}

void VulkanRendererContext::createInstance() {
    RLOG("createInstance: adrenotoolsHandle=%p (custom driver %s)",
        adrenotoolsHandle, adrenotoolsHandle?"ACTIVE":"NOT SET - using stock driver");

    if (adrenotoolsHandle) {
        gipa = (PFN_vkGetInstanceProcAddr)dlsym(adrenotoolsHandle, "vkGetInstanceProcAddr");
    }
    if (!gipa) {
        void* loaderLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_GLOBAL);
        if (loaderLib)
            gipa = (PFN_vkGetInstanceProcAddr)dlsym(loaderLib, "vkGetInstanceProcAddr");
    }

    vk_.CreateInstance = (PFN_vkCreateInstance)gipa(nullptr, "vkCreateInstance");
    VkApplicationInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO;
    ai.pApplicationName="Winlator"; ai.apiVersion=VK_API_VERSION_1_3;
    const char* ext[]={"VK_KHR_surface","VK_KHR_android_surface"};
    VkInstanceCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    ci.pApplicationInfo=&ai; ci.enabledExtensionCount=2; ci.ppEnabledExtensionNames=ext;
    if (vk_.CreateInstance(&ci,nullptr,&instance)!=VK_SUCCESS) throw std::runtime_error("instance");

    loadInstanceDispatch();
}

void VulkanRendererContext::createSurface() {
    VkAndroidSurfaceCreateInfoKHR ci{}; ci.sType=VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    ci.window=window;
    if (vk_.CreateAndroidSurfaceKHR(instance,&ci,nullptr,&surface)!=VK_SUCCESS) throw std::runtime_error("surface");
}

void VulkanRendererContext::pickPhysicalDevice() {
    uint32_t n=0; vk_.EnumeratePhysicalDevices(instance,&n,nullptr);
    std::vector<VkPhysicalDevice> devs(n); vk_.EnumeratePhysicalDevices(instance,&n,devs.data());
    physicalDevice = VK_NULL_HANDLE;
    graphicsQueueFamilyIndex = 0;
    for (auto d : devs) {
        uint32_t qCount = 0;
        vk_.GetPhysicalDeviceQueueFamilyProperties(d, &qCount, nullptr);
        std::vector<VkQueueFamilyProperties> qProps(qCount);
        vk_.GetPhysicalDeviceQueueFamilyProperties(d, &qCount, qProps.data());
        for (uint32_t i = 0; i < qCount; i++) {
            VkBool32 present = VK_FALSE;
            vk_.GetPhysicalDeviceSurfaceSupportKHR(d, i, surface, &present);
            if ((qProps[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) {
                physicalDevice = d;
                graphicsQueueFamilyIndex = i;
                fgTimestampsOk_ = qProps[i].timestampValidBits > 0;
                return;
            }
        }
    }
    if (n > 0) physicalDevice = devs[0];
}

void VulkanRendererContext::createLogicalDevice() {
    float p=1.f;
    VkDeviceQueueCreateInfo qi{}; qi.sType=VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    qi.queueFamilyIndex=graphicsQueueFamilyIndex; qi.queueCount=1; qi.pQueuePriorities=&p;

    PFN_vkEnumerateDeviceExtensionProperties enumDevExts =
        (PFN_vkEnumerateDeviceExtensionProperties)gipa(instance, "vkEnumerateDeviceExtensionProperties");
    std::vector<VkExtensionProperties> av;
    { uint32_t n=0; if(enumDevExts) enumDevExts(physicalDevice,nullptr,&n,nullptr);
      av.resize(n);
      if(enumDevExts) enumDevExts(physicalDevice,nullptr,&n,av.data());
      for (auto& e:av) {
          if (strcmp(e.extensionName,"VK_EXT_filter_cubic")==0
           || strcmp(e.extensionName,"VK_IMG_filter_cubic")==0) cubicSupported=true;
      } }
    std::vector<const char*> extList = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME,
        VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME
    };
    if (cubicSupported) extList.push_back("VK_EXT_filter_cubic");
    VkDeviceCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    ci.pQueueCreateInfos=&qi; ci.queueCreateInfoCount=1;

    // --- Native LSFG frame generation: enable the three features its shaders
    // need. The renderer has historically enabled NO features at all
    // (pEnabledFeatures = nullptr, no pNext), so all three are off by default.
    // Only chain anything when the device passes every gate: on any other
    // device this block is inert and vkCreateDevice is called exactly as it
    // always has been. With the experimental Vulkan 1.1 compat flag the probe
    // may also accept a 1.1/1.2 device through extensions (lsfg_probe.h); those
    // are then enabled here and the memory model is requested through the KHR
    // struct, which a 1.1 driver understands.
    lsfgCaps_ = lsfg::Caps{};
    lsfgCaps_.features = lsfg::queryFeatures(vk_, physicalDevice, av, lsfgVk11Compat_);

    // Copy so a rejected feature chain can fall back to the pre-LSFG list.
    const std::vector<const char*> baseExtList = extList;
    VkPhysicalDeviceVulkan12Features             lsfgV12{};
    VkPhysicalDeviceVulkanMemoryModelFeaturesKHR lsfgMM{};
    VkPhysicalDeviceFeatures2                    lsfgF2{};
    if (lsfgCaps_.features.deviceGatesPass()) {
        const bool khrPath = lsfgCaps_.features.deviceApiVersion < VK_API_VERSION_1_2;
        if (khrPath) {
            lsfgMM.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_MEMORY_MODEL_FEATURES_KHR;
            lsfgMM.vulkanMemoryModel = VK_TRUE;
            lsfgMM.vulkanMemoryModelDeviceScope =
                lsfgCaps_.features.vulkanMemoryModelDeviceScope ? VK_TRUE : VK_FALSE;
            for (const char* e : lsfg::extensionNames(lsfgCaps_.features)) extList.push_back(e);
            RLOG("createLogicalDevice: LSFG Vulkan 1.1 compat - enabling spirv_1_4 + vulkan_memory_model");
        } else {
            lsfgV12.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
            lsfgV12.vulkanMemoryModel = VK_TRUE;
            // Device scope is a separate SPIR-V capability; enable it only when the
            // driver offers it, so a driver without it still gets the base model.
            lsfgV12.vulkanMemoryModelDeviceScope =
                lsfgCaps_.features.vulkanMemoryModelDeviceScope ? VK_TRUE : VK_FALSE;
        }

        lsfgF2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
        lsfgF2.pNext = khrPath ? (void*)&lsfgMM : (void*)&lsfgV12;
        lsfgF2.features.shaderStorageImageWriteWithoutFormat = VK_TRUE;
        lsfgF2.features.shaderStorageImageExtendedFormats    = VK_TRUE;

        // pEnabledFeatures MUST stay null while a VkPhysicalDeviceFeatures2 is
        // chained — the two are mutually exclusive.
        ci.pNext = &lsfgF2;
        lsfgCaps_.featuresEnabled = true;
    }
    ci.enabledExtensionCount=(uint32_t)extList.size(); ci.ppEnabledExtensionNames=extList.data();

    // Mali only: a HIGH global-priority queue, as the Wayland compositor already asks for
    // (vk_present.c). WinlatorMali measured its frame work waiting 23-40 ms behind a GPU-bound game
    // on a Mali-G615 at default priority (frame_gen.cpp, GunaCharanTeja). One extra try; a refusal
    // (NOT_PERMITTED or anything else) falls through to the device create below, unchanged.
    // Every other GPU goes straight to that create exactly as before.
    VkPhysicalDeviceProperties devProps{};
    vk_.GetPhysicalDeviceProperties(physicalDevice, &devProps);
    isMaliDevice_ = devProps.vendorID == 0x13B5;
    bool created = false;
    if (isMaliDevice_) {
        const char* gpExt = nullptr;
        for (auto& e : av) {
            if (strcmp(e.extensionName, "VK_KHR_global_priority") == 0) { gpExt = "VK_KHR_global_priority"; break; }
            if (strcmp(e.extensionName, "VK_EXT_global_priority") == 0) gpExt = "VK_EXT_global_priority";
        }
        if (gpExt) {
            VkDeviceQueueGlobalPriorityCreateInfoEXT gp{};
            gp.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_GLOBAL_PRIORITY_CREATE_INFO_EXT;
            gp.globalPriority = VK_QUEUE_GLOBAL_PRIORITY_HIGH_EXT;
            std::vector<const char*> gpList = extList;
            gpList.push_back(gpExt);
            VkDeviceCreateInfo gci = ci;
            gci.enabledExtensionCount = (uint32_t)gpList.size(); gci.ppEnabledExtensionNames = gpList.data();
            qi.pNext = &gp;
            VkResult gr = vk_.CreateDevice(physicalDevice, &gci, nullptr, &device);
            qi.pNext = nullptr;
            if (gr == VK_SUCCESS) {
                created = true;
                RLOG("createLogicalDevice: Mali - high-priority queue (%s)", gpExt);
            } else {
                device = VK_NULL_HANDLE;
                RLOG("createLogicalDevice: Mali - high-priority queue refused (%d); default priority", (int)gr);
            }
        }
    }

    if (!created && vk_.CreateDevice(physicalDevice,&ci,nullptr,&device)!=VK_SUCCESS) {
        // A driver that rejects the feature chain must not cost us the whole
        // renderer: retry once with the pre-LSFG device create, unchanged.
        if (lsfgCaps_.featuresEnabled) {
            RLOG_E("createLogicalDevice: CreateDevice failed WITH LSFG features; retrying without");
            ci.pNext = nullptr;
            ci.enabledExtensionCount=(uint32_t)baseExtList.size(); ci.ppEnabledExtensionNames=baseExtList.data();
            lsfgCaps_.featuresEnabled = false;
            if (vk_.CreateDevice(physicalDevice,&ci,nullptr,&device)!=VK_SUCCESS)
                throw std::runtime_error("device");
        } else {
            throw std::runtime_error("device");
        }
    }
    vk_.GetDeviceProcAddr = (PFN_vkGetDeviceProcAddr)gipa(instance, "vkGetDeviceProcAddr");
    loadDeviceDispatch();
    vk_.GetDeviceQueue(device,graphicsQueueFamilyIndex,0,&graphicsQueue);

    vk_.GetPhysicalDeviceMemoryProperties(physicalDevice, &memProperties);

    VkPhysicalDeviceProperties props{};
    vk_.GetPhysicalDeviceProperties(physicalDevice, &props);
    maxAnisotropy = props.limits.maxSamplerAnisotropy;
    fgTimestampPeriodNs_ = props.limits.timestampPeriod;
}

void VulkanRendererContext::createSwapchain() {
    VkSurfaceCapabilitiesKHR caps;
    vk_.GetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice,surface,&caps);
    swapchainExt=(caps.currentExtent.width!=0xFFFFFFFF)?caps.currentExtent:VkExtent2D{(uint32_t)surfaceWidth,(uint32_t)surfaceHeight};
    uint32_t fmtN=0; vk_.GetPhysicalDeviceSurfaceFormatsKHR(physicalDevice,surface,&fmtN,nullptr);
    std::vector<VkSurfaceFormatKHR> fmts(fmtN); vk_.GetPhysicalDeviceSurfaceFormatsKHR(physicalDevice,surface,&fmtN,fmts.data());
    swapchainFmt = VK_FORMAT_R8G8B8A8_UNORM;

    // Native LSFG: the composite target carries the swapchain's format so the
    // effect pipelines stay render-pass compatible with both paths, and
    // `generate` writes it through a storage image. Probe it here, where the
    // format is finally known, and settle the capability verdict.
    lsfgCaps_.probedFormat = swapchainFmt;
    lsfgCaps_.storageOnSwapchainFormat =
        lsfg::probeStorageFormat(vk_, physicalDevice, swapchainFmt);
    lsfgCaps_.linearBlitOnSwapchainFormat =
        lsfg::probeLinearBlit(vk_, physicalDevice, swapchainFmt);
    lsfg::explain(lsfgCaps_);
    RLOG("lsfg-native: %s (features enabled=%d, storage-on-fmt=%d)",
         lsfgCaps_.reason, (int)lsfgCaps_.featuresEnabled,
         (int)lsfgCaps_.storageOnSwapchainFormat);

    uint32_t imgCount=caps.minImageCount+1;
    // Frame gen presents several images per source frame, so the queue needs
    // depth to keep them in flight without stalling on AcquireNextImageKHR.
    // Only while armed: extra images cost memory.
    if (fgArmed_.load(std::memory_order_relaxed)) {
        // Enough depth for base + generated in flight, but capped: Android's
        // BufferQueue does not enjoy very deep swapchains, and the surface's
        // own maxImageCount is not always the real limit.
        const uint32_t want = std::min<uint32_t>(caps.minImageCount + kMaxPresentsPerFrame, 8u);
        if (want > imgCount) imgCount = want;
    }
    if (caps.maxImageCount>0&&imgCount>caps.maxImageCount) imgCount=caps.maxImageCount;

    uint32_t pmCount=0;
    vk_.GetPhysicalDeviceSurfacePresentModesKHR(physicalDevice,surface,&pmCount,nullptr);
    availablePresentModes.resize(pmCount);
    vk_.GetPhysicalDeviceSurfacePresentModesKHR(physicalDevice,surface,&pmCount,availablePresentModes.data());
    VkPresentModeKHR presentMode=VK_PRESENT_MODE_FIFO_KHR;
    for (auto pm:availablePresentModes) if(pm==requestedPresentMode){presentMode=pm;break;}
    if(verboseLog){
        std::string pmList;
        for(auto pm:availablePresentModes) pmList+=std::to_string((int)pm)+" ";
        RLOG("createSwapchain: %dx%d fmt=%d supportedPresentModes=[%s] chosen=%d req=%d",
            swapchainExt.width,swapchainExt.height,(int)swapchainFmt,pmList.c_str(),(int)presentMode,(int)requestedPresentMode);
    }

    VkSurfaceTransformFlagBitsKHR pre=
        (caps.supportedTransforms&VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)?
        VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR:caps.currentTransform;

    VkCompositeAlphaFlagBitsKHR compositeAlpha=
        (caps.supportedCompositeAlpha&VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)?
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR:VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;

    VkSwapchainKHR oldSwapchain=swapchain;
    VkSwapchainCreateInfoKHR ci{}; ci.sType=VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    ci.surface=surface; ci.minImageCount=imgCount; ci.imageFormat=swapchainFmt;
    ci.imageColorSpace=VK_COLOR_SPACE_SRGB_NONLINEAR_KHR; ci.imageExtent=swapchainExt;
    ci.imageArrayLayers=1; ci.imageUsage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    // The composite path copies INTO the swapchain image, which needs
    // TRANSFER_DST. Requested only while frame gen is armed, so a normal
    // session never trades away framebuffer compression for a flag it never
    // uses. Toggling arming recreates the swapchain (see setFrameGenArmed).
    if (fgArmed_.load(std::memory_order_relaxed) &&
        (caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT)) {
        ci.imageUsage |= VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        swapchainTransferDst = true;
    } else {
        swapchainTransferDst = false;
    }
    ci.imageSharingMode=VK_SHARING_MODE_EXCLUSIVE; ci.preTransform=pre;
    ci.compositeAlpha=compositeAlpha; ci.presentMode=presentMode; ci.clipped=VK_TRUE;
    ci.oldSwapchain=oldSwapchain;
    if (vk_.CreateSwapchainKHR(device,&ci,nullptr,&swapchain)!=VK_SUCCESS) throw std::runtime_error("swapchain");
    RLOG("swapchain created: %dx%d format=%d presentMode=%d compositeAlpha=%d imgCount=%u",
        swapchainExt.width,swapchainExt.height,(int)swapchainFmt,(int)presentMode,(int)compositeAlpha,imgCount);
    if (oldSwapchain!=VK_NULL_HANDLE) vk_.DestroySwapchainKHR(device,oldSwapchain,nullptr);
    vk_.GetSwapchainImagesKHR(device,swapchain,&imgCount,nullptr);
    swapchainImages.resize(imgCount); vk_.GetSwapchainImagesKHR(device,swapchain,&imgCount,swapchainImages.data());
    swapchainViews.resize(imgCount);
    for (size_t i=0;i<imgCount;i++) {
        VkImageViewCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        vi.image=swapchainImages[i]; vi.viewType=VK_IMAGE_VIEW_TYPE_2D; vi.format=swapchainFmt;
        vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        VkComponentMapping mapping{};
        mapping.r = VK_COMPONENT_SWIZZLE_IDENTITY;
        mapping.g = VK_COMPONENT_SWIZZLE_IDENTITY;
        mapping.b = VK_COMPONENT_SWIZZLE_IDENTITY;
        mapping.a = VK_COMPONENT_SWIZZLE_IDENTITY;
        vi.components = mapping;
        if (vk_.CreateImageView(device,&vi,nullptr,&swapchainViews[i])!=VK_SUCCESS) throw std::runtime_error("imgview");
    }
}

void VulkanRendererContext::createRenderPass() {
    VkAttachmentDescription att{}; att.format=swapchainFmt; att.samples=VK_SAMPLE_COUNT_1_BIT;
    att.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR; att.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE; att.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED; att.finalLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{}; sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount=1; sub.pColorAttachments=&ref;
    VkSubpassDependency dep{}; dep.srcSubpass=VK_SUBPASS_EXTERNAL; dep.dstSubpass=0;
    dep.srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT; dep.srcAccessMask=0;
    dep.dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    VkRenderPassCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount=1; ci.pAttachments=&att; ci.subpassCount=1; ci.pSubpasses=&sub;
    ci.dependencyCount=1; ci.pDependencies=&dep;
    if (vk_.CreateRenderPass(device,&ci,nullptr,&renderPass)!=VK_SUCCESS) throw std::runtime_error("renderpass");
}

void VulkanRendererContext::createDSLayout() {
    VkDescriptorSetLayoutBinding b{}; b.binding=0; b.descriptorCount=1;
    b.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; b.stageFlags=VK_SHADER_STAGE_FRAGMENT_BIT;
    VkDescriptorSetLayoutCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    ci.bindingCount=1; ci.pBindings=&b;
    if (vk_.CreateDescriptorSetLayout(device,&ci,nullptr,&dsLayout)!=VK_SUCCESS) throw std::runtime_error("dslayout");
}
 

VkShaderModule VulkanRendererContext::makeShader(const uint32_t* code, size_t sz) {
    VkShaderModuleCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    ci.codeSize=sz; ci.pCode=code; VkShaderModule m;
    if (vk_.CreateShaderModule(device,&ci,nullptr,&m)!=VK_SUCCESS) throw std::runtime_error("shader");
    return m;
}

void VulkanRendererContext::createPipeline(bool blend, VkPipeline& out) {
    if (pipeLayout==VK_NULL_HANDLE) {
        VkPushConstantRange pc{}; pc.stageFlags=VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT;
        pc.size=sizeof(WindowPushConstants);
        VkPipelineLayoutCreateInfo li{}; li.sType=VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        li.setLayoutCount=1; li.pSetLayouts=&dsLayout; li.pushConstantRangeCount=1; li.pPushConstantRanges=&pc;
        if (vk_.CreatePipelineLayout(device,&li,nullptr,&pipeLayout)!=VK_SUCCESS) throw std::runtime_error("pipelayout");
    }
    auto vert=makeShader(window_vert_code,sizeof(window_vert_code));
    auto frag=makeShader(window_frag_code,sizeof(window_frag_code));
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT; stages[0].module=vert; stages[0].pName="main";
    stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag; stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType=VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO; ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    VkDynamicState dyn[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{}; ds.sType=VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO; ds.dynamicStateCount=2; ds.pDynamicStates=dyn;
    VkPipelineViewportStateCreateInfo vp{}; vp.sType=VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO; vp.viewportCount=1; vp.scissorCount=1;
    VkPipelineRasterizationStateCreateInfo rast{}; rast.sType=VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO; rast.polygonMode=VK_POLYGON_MODE_FILL; rast.lineWidth=1.f; rast.cullMode=VK_CULL_MODE_NONE; rast.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo ms{}; ms.sType=VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ba{}; ba.colorWriteMask=0xF; ba.blendEnable=blend?VK_TRUE:VK_FALSE;
    if (blend){ba.srcColorBlendFactor=VK_BLEND_FACTOR_SRC_ALPHA;ba.dstColorBlendFactor=VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;ba.colorBlendOp=VK_BLEND_OP_ADD;ba.srcAlphaBlendFactor=VK_BLEND_FACTOR_ONE;ba.dstAlphaBlendFactor=VK_BLEND_FACTOR_ZERO;ba.alphaBlendOp=VK_BLEND_OP_ADD;}
    VkPipelineColorBlendStateCreateInfo cb{}; cb.sType=VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO; cb.attachmentCount=1; cb.pAttachments=&ba;
    VkGraphicsPipelineCreateInfo pi{}; pi.sType=VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vi; pi.pInputAssemblyState=&ia;
    pi.pViewportState=&vp; pi.pRasterizationState=&rast; pi.pMultisampleState=&ms;
    pi.pColorBlendState=&cb; pi.pDynamicState=&ds; pi.layout=pipeLayout; pi.renderPass=renderPass; pi.subpass=0;
    if (vk_.CreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&out)!=VK_SUCCESS) throw std::runtime_error("pipeline");
    vk_.DestroyShaderModule(device,frag,nullptr); vk_.DestroyShaderModule(device,vert,nullptr);
}


void VulkanRendererContext::createCursorPipeline() {  }
void VulkanRendererContext::createFramebuffers() {
    swapchainFBs.resize(swapchainViews.size());
    for (size_t i=0;i<swapchainViews.size();i++) {
        VkImageView att[]={swapchainViews[i]};
        VkFramebufferCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        fi.renderPass=renderPass; fi.attachmentCount=1; fi.pAttachments=att;
        fi.width=swapchainExt.width; fi.height=swapchainExt.height; fi.layers=1;
        if (vk_.CreateFramebuffer(device,&fi,nullptr,&swapchainFBs[i])!=VK_SUCCESS) throw std::runtime_error("fb");
    }
}

void VulkanRendererContext::createCmdPool() {
    VkCommandPoolCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    ci.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT; ci.queueFamilyIndex=graphicsQueueFamilyIndex;
    if (vk_.CreateCommandPool(device,&ci,nullptr,&cmdPool)!=VK_SUCCESS) throw std::runtime_error("cmdpool");
}

void VulkanRendererContext::createSampler() {
    bool useCubic = (filterMode == 2) && cubicSupported;
    VkFilter filter = (filterMode == 1) ? VK_FILTER_NEAREST
                    : (useCubic)         ? VK_FILTER_CUBIC_EXT
                    :                      VK_FILTER_LINEAR;
    RLOG("createSampler: filter=%s (filterMode=%d, cubicSupported=%d)",
        filterMode==2?(cubicSupported?"CUBIC":"LINEAR_FALLBACK"):filterMode==1?"NEAREST":"LINEAR",
        filterMode, (int)cubicSupported);
    VkSamplerCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    ci.magFilter=filter; ci.minFilter=filter;
    ci.addressModeU=ci.addressModeV=ci.addressModeW=VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.mipmapMode=VK_SAMPLER_MIPMAP_MODE_NEAREST;
    ci.minLod=0.f; ci.maxLod=0.f;
    if (vk_.CreateSampler(device,&ci,nullptr,&sampler)!=VK_SUCCESS) throw std::runtime_error("sampler");
}

// ============================ Spatial upscaler ==============================

void VulkanRendererContext::createUpscaleSampler() {
    // FSR/SGSR sample the offscreen/mid targets with bilinear + clamp-to-edge.
    VkSamplerCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    ci.magFilter=VK_FILTER_LINEAR; ci.minFilter=VK_FILTER_LINEAR;
    ci.addressModeU=ci.addressModeV=ci.addressModeW=VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    ci.mipmapMode=VK_SAMPLER_MIPMAP_MODE_NEAREST;
    ci.minLod=0.f; ci.maxLod=0.f;
    if (vk_.CreateSampler(device,&ci,nullptr,&upscaleSampler)!=VK_SUCCESS)
        throw std::runtime_error("upscale sampler");
}

void VulkanRendererContext::createOffscreenRenderPass() {
    // Render target that is later SAMPLED (composite offscreen + EASU output).
    VkAttachmentDescription att{}; att.format=offscreenFmt; att.samples=VK_SAMPLE_COUNT_1_BIT;
    att.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR; att.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE; att.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED; att.finalLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{}; sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount=1; sub.pColorAttachments=&ref;
    VkSubpassDependency deps[2]{};
    // make a previous frame's sampling of this image finish before we overwrite it
    deps[0].srcSubpass=VK_SUBPASS_EXTERNAL; deps[0].dstSubpass=0;
    deps[0].srcStageMask=VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    deps[0].dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[0].srcAccessMask=VK_ACCESS_SHADER_READ_BIT;
    deps[0].dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    // make this pass's writes visible to the subsequent upscale-pass sampling
    deps[1].srcSubpass=0; deps[1].dstSubpass=VK_SUBPASS_EXTERNAL;
    deps[1].srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[1].dstStageMask=VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    deps[1].srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    deps[1].dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
    VkRenderPassCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount=1; ci.pAttachments=&att; ci.subpassCount=1; ci.pSubpasses=&sub;
    ci.dependencyCount=2; ci.pDependencies=deps;
    if (vk_.CreateRenderPass(device,&ci,nullptr,&offscreenRenderPass)!=VK_SUCCESS)
        throw std::runtime_error("offscreen renderpass");
}

VkPipeline VulkanRendererContext::createPostPipeline(const uint32_t* fragCode, size_t fragSz, VkRenderPass rp) {
    auto vert=makeShader(upscale_vert_code,sizeof(upscale_vert_code));
    auto frag=makeShader(fragCode,fragSz);
    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT;   stages[0].module=vert; stages[0].pName="main";
    stages[1].sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag; stages[1].pName="main";
    VkPipelineVertexInputStateCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    VkPipelineInputAssemblyStateCreateInfo ia{}; ia.sType=VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO; ia.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
    VkDynamicState dyn[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{}; ds.sType=VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO; ds.dynamicStateCount=2; ds.pDynamicStates=dyn;
    VkPipelineViewportStateCreateInfo vp{}; vp.sType=VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO; vp.viewportCount=1; vp.scissorCount=1;
    VkPipelineRasterizationStateCreateInfo rast{}; rast.sType=VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO; rast.polygonMode=VK_POLYGON_MODE_FILL; rast.lineWidth=1.f; rast.cullMode=VK_CULL_MODE_NONE; rast.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE;
    VkPipelineMultisampleStateCreateInfo ms{}; ms.sType=VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO; ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState ba{}; ba.colorWriteMask=0xF; ba.blendEnable=VK_FALSE; // opaque post pass
    VkPipelineColorBlendStateCreateInfo cb{}; cb.sType=VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO; cb.attachmentCount=1; cb.pAttachments=&ba;
    VkGraphicsPipelineCreateInfo pi{}; pi.sType=VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vi; pi.pInputAssemblyState=&ia;
    pi.pViewportState=&vp; pi.pRasterizationState=&rast; pi.pMultisampleState=&ms;
    pi.pColorBlendState=&cb; pi.pDynamicState=&ds; pi.layout=postPipeLayout; pi.renderPass=rp; pi.subpass=0;
    VkPipeline out=VK_NULL_HANDLE;
    VkResult r=vk_.CreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&out);
    vk_.DestroyShaderModule(device,frag,nullptr); vk_.DestroyShaderModule(device,vert,nullptr);
    if (r!=VK_SUCCESS) throw std::runtime_error("post pipeline");
    return out;
}

void VulkanRendererContext::createPostPipelines() {
    VkPushConstantRange pc{}; pc.stageFlags=VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT;
    pc.offset=0; pc.size=sizeof(EasuPushConstants); // largest of the three PC structs
    VkPipelineLayoutCreateInfo li{}; li.sType=VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    li.setLayoutCount=1; li.pSetLayouts=&dsLayout; li.pushConstantRangeCount=1; li.pPushConstantRanges=&pc;
    if (vk_.CreatePipelineLayout(device,&li,nullptr,&postPipeLayout)!=VK_SUCCESS)
        throw std::runtime_error("post pipelayout");
    // EASU writes the (sampled) mid target -> offscreenRenderPass.
    // SGSR & RCAS write the swapchain -> renderPass.
    easuPipeline = createPostPipeline(fsr_easu_code, sizeof(fsr_easu_code), offscreenRenderPass);
    sgsrPipeline = createPostPipeline(sgsr_code,     sizeof(sgsr_code),     renderPass);
    // SGSR Quality (mode 8): same single pass + push constants, edge-direction weights.
    sgsrQualityPipeline = createPostPipeline(sgsr_quality_code, sizeof(sgsr_quality_code), renderPass);
    // NIS NVScaler is single-pass and writes the swapchain directly -> renderPass.
    nisPipeline  = createPostPipeline(nis_code,      sizeof(nis_code),      renderPass);
    rcasPipeline = createPostPipeline(fsr_rcas_code, sizeof(fsr_rcas_code), renderPass);
    // Supersampling downscale writes the swapchain too.
    downscalePipeline = createPostPipeline(downscale_code, sizeof(downscale_code), renderPass);
    // CAS/HDR effects: an offscreen-targeted variant (non-final, writes an fx target)
    // and a swapchain-targeted variant (final pass). Bound by stage at record time.
    casPipelineOff  = createPostPipeline(cas_code, sizeof(cas_code), offscreenRenderPass);
    casPipelineSwap = createPostPipeline(cas_code, sizeof(cas_code), renderPass);
    hdrPipelineOff  = createPostPipeline(hdr_code, sizeof(hdr_code), offscreenRenderPass);
    hdrPipelineSwap = createPostPipeline(hdr_code, sizeof(hdr_code), renderPass);
    // Phase 2 screen effects: same Off/Swap variant pair as CAS/HDR.
    fxaaPipelineOff   = createPostPipeline(fxaa_code,  sizeof(fxaa_code),  offscreenRenderPass);
    fxaaPipelineSwap  = createPostPipeline(fxaa_code,  sizeof(fxaa_code),  renderPass);
    toonPipelineOff   = createPostPipeline(toon_code,  sizeof(toon_code),  offscreenRenderPass);
    toonPipelineSwap  = createPostPipeline(toon_code,  sizeof(toon_code),  renderPass);
    colorPipelineOff  = createPostPipeline(color_code, sizeof(color_code), offscreenRenderPass);
    colorPipelineSwap = createPostPipeline(color_code, sizeof(color_code), renderPass);
    ntscPipelineOff   = createPostPipeline(ntsc_code,  sizeof(ntsc_code),  offscreenRenderPass);
    ntscPipelineSwap  = createPostPipeline(ntsc_code,  sizeof(ntsc_code),  renderPass);
    crtPipelineOff    = createPostPipeline(crt_code,   sizeof(crt_code),   offscreenRenderPass);
    crtPipelineSwap   = createPostPipeline(crt_code,   sizeof(crt_code),   renderPass);
    // Debanding is the terminal dither pass: always last -> always writes the
    // swapchain, so only the renderPass (swap) variant is needed.
    debandPipelineSwap = createPostPipeline(deband_code, sizeof(deband_code), renderPass);
}

bool VulkanRendererContext::createColorTarget(int w, int h, VkImage& img, VkDeviceMemory& mem,
        VkImageView& view, VkFramebuffer& fb, VkDescriptorSet& ds) {
    VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.extent={(uint32_t)w,(uint32_t)h,1}; ii.mipLevels=1; ii.arrayLayers=1; ii.format=offscreenFmt;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    ii.usage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT;
    ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    if (vk_.CreateImage(device,&ii,nullptr,&img)!=VK_SUCCESS) return false;
    VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,img,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size;
    ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (vk_.AllocateMemory(device,&ai,nullptr,&mem)!=VK_SUCCESS){vk_.DestroyImage(device,img,nullptr);img=VK_NULL_HANDLE;return false;}
    vk_.BindImageMemory(device,img,mem,0);
    VkImageViewCreateInfo vci{}; vci.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO; vci.image=img; vci.viewType=VK_IMAGE_VIEW_TYPE_2D; vci.format=offscreenFmt;
    vci.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    vci.components={VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
    if (vk_.CreateImageView(device,&vci,nullptr,&view)!=VK_SUCCESS){destroyColorTarget(img,mem,view,fb,ds);return false;}
    VkImageView att[]={view};
    VkFramebufferCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    fi.renderPass=offscreenRenderPass; fi.attachmentCount=1; fi.pAttachments=att; fi.width=(uint32_t)w; fi.height=(uint32_t)h; fi.layers=1;
    if (vk_.CreateFramebuffer(device,&fi,nullptr,&fb)!=VK_SUCCESS){destroyColorTarget(img,mem,view,fb,ds);return false;}
    VkDescriptorSetAllocateInfo dsai{}; dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
    if (vk_.AllocateDescriptorSets(device,&dsai,&ds)!=VK_SUCCESS){ds=VK_NULL_HANDLE;destroyColorTarget(img,mem,view,fb,ds);return false;}
    VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; dii.imageView=view; dii.sampler=upscaleSampler;
    VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; wr.dstSet=ds; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    return true;
}

// ======================= Native LSFG: composite target ring ==================
// See VulkanRendererContext.h. When frame generation is armed the final pass
// draws into a composite image we own instead of the acquired swapchain image,
// and a copy moves it across at the end of the command buffer. The composite
// carries the SWAPCHAIN's format, so every existing pipeline stays render-pass
// compatible with both targets and none of them has to be rebuilt.

bool VulkanRendererContext::createCompositeRenderPass() {
    if (compositeRenderPass != VK_NULL_HANDLE) return true;

    // Format-identical to renderPass (and to offscreenRenderPass — both are
    // R8G8B8A8_UNORM today), so pipelines built against either remain
    // compatible: render-pass compatibility is attachment format + sample
    // count, not load/store ops or layouts.
    VkAttachmentDescription att{}; att.format=swapchainFmt; att.samples=VK_SAMPLE_COUNT_1_BIT;
    att.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR; att.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE; att.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    // GENERAL, so both a compute dispatch (generate) and the copy can touch the
    // finished frame without another layout transition per use.
    att.finalLayout=VK_IMAGE_LAYOUT_GENERAL;

    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{}; sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount=1; sub.pColorAttachments=&ref;

    VkSubpassDependency deps[2]{};
    // A previous frame may still be sampling or computing on this target.
    deps[0].srcSubpass=VK_SUBPASS_EXTERNAL; deps[0].dstSubpass=0;
    deps[0].srcStageMask=VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT;
    deps[0].dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[0].srcAccessMask=VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_TRANSFER_READ_BIT;
    deps[0].dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    // Make this pass's writes visible to the copy and to the LSFG chain.
    deps[1].srcSubpass=0; deps[1].dstSubpass=VK_SUBPASS_EXTERNAL;
    deps[1].srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    deps[1].dstStageMask=VK_PIPELINE_STAGE_TRANSFER_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    deps[1].srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    deps[1].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT|VK_ACCESS_SHADER_READ_BIT;

    VkRenderPassCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount=1; ci.pAttachments=&att; ci.subpassCount=1; ci.pSubpasses=&sub;
    ci.dependencyCount=2; ci.pDependencies=deps;
    if (vk_.CreateRenderPass(device,&ci,nullptr,&compositeRenderPass)!=VK_SUCCESS) {
        RLOG_E("createCompositeRenderPass failed");
        compositeRenderPass = VK_NULL_HANDLE;
        return false;
    }
    return true;
}

bool VulkanRendererContext::ensureCompositeTargets(uint32_t w, uint32_t h, uint32_t count) {
    if (w == 0 || h == 0 || count == 0) return false;
    if (count > kMaxCompositeTargets) count = kMaxCompositeTargets;

    if (compositeW == w && compositeH == h && compositeTargets.size() == count) return true;
    if (!createCompositeRenderPass()) return false;

    // Changing the multiplier resizes the ring, and the images being replaced
    // may still be referenced by a submitted command buffer. Destroying them
    // under the GPU is a use-after-free; wait first.
    if (!compositeTargets.empty()) vk_.DeviceWaitIdle(device);
    destroyCompositeTargets();

    compositeTargets.resize(count);
    for (uint32_t i = 0; i < count; i++) {
        CompositeTarget& t = compositeTargets[i];

        VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        ii.imageType=VK_IMAGE_TYPE_2D; ii.extent={w,h,1};
        ii.mipLevels=1; ii.arrayLayers=1; ii.format=swapchainFmt;
        ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        // The full set the frame-gen path needs: drawn into by the effect
        // chain, sampled as the next frame's LSFG input, written by `generate`
        // through a storage view, and copied into the swapchain image.
        ii.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                 | VK_IMAGE_USAGE_SAMPLED_BIT
                 | VK_IMAGE_USAGE_STORAGE_BIT
                 | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                 | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
        if (vk_.CreateImage(device,&ii,nullptr,&t.img)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,t.img,&req);
        VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        ai.allocationSize=req.size;
        ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (vk_.AllocateMemory(device,&ai,nullptr,&t.mem)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }
        vk_.BindImageMemory(device,t.img,t.mem,0);

        VkImageViewCreateInfo vci{}; vci.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        vci.image=t.img; vci.viewType=VK_IMAGE_VIEW_TYPE_2D; vci.format=swapchainFmt;
        vci.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        vci.components={VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,
                        VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
        if (vk_.CreateImageView(device,&vci,nullptr,&t.view)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }
        // Storage views must carry identity swizzle and no component mapping;
        // same descriptor otherwise.
        if (vk_.CreateImageView(device,&vci,nullptr,&t.storageView)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkImageView att[]={t.view};
        VkFramebufferCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        fi.renderPass=compositeRenderPass; fi.attachmentCount=1; fi.pAttachments=att;
        fi.width=w; fi.height=h; fi.layers=1;
        if (vk_.CreateFramebuffer(device,&fi,nullptr,&t.fb)!=VK_SUCCESS) { destroyCompositeTargets(); return false; }

        VkDescriptorSetAllocateInfo dsai{}; dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
        if (vk_.AllocateDescriptorSets(device,&dsai,&t.ds)!=VK_SUCCESS) { t.ds=VK_NULL_HANDLE; destroyCompositeTargets(); return false; }
        VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_GENERAL;
        dii.imageView=t.view; dii.sampler=upscaleSampler;
        VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        wr.dstSet=t.ds; wr.dstBinding=0;
        wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1;
        wr.pImageInfo=&dii;
        vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    }

    compositeW = w; compositeH = h; compositeIndex = 0;
    RLOG("composite ring: %ux%u x%u targets", w, h, count);
    return true;
}

void VulkanRendererContext::destroyCompositeTargets() {
    for (CompositeTarget& t : compositeTargets) {
        if (t.ds          !=VK_NULL_HANDLE){ vk_.FreeDescriptorSets(device,winTexPool,1,&t.ds); t.ds=VK_NULL_HANDLE; }
        if (t.fb          !=VK_NULL_HANDLE){ vk_.DestroyFramebuffer(device,t.fb,nullptr); t.fb=VK_NULL_HANDLE; }
        if (t.storageView !=VK_NULL_HANDLE){ vk_.DestroyImageView(device,t.storageView,nullptr); t.storageView=VK_NULL_HANDLE; }
        if (t.view        !=VK_NULL_HANDLE){ vk_.DestroyImageView(device,t.view,nullptr); t.view=VK_NULL_HANDLE; }
        if (t.img         !=VK_NULL_HANDLE){ vk_.DestroyImage(device,t.img,nullptr); t.img=VK_NULL_HANDLE; }
        if (t.mem         !=VK_NULL_HANDLE){ vk_.FreeMemory(device,t.mem,nullptr); t.mem=VK_NULL_HANDLE; }
    }
    compositeTargets.clear();
    compositeW = compositeH = 0;
    compositeIndex = 0;
}

// ===================== Native LSFG: generation passes ========================

bool VulkanRendererContext::ensureLsfgEngine() {
    if (lsfgEngine_) return lsfgEngine_->valid();
    if (lsfgEngineTried_) return false;      // failed once; don't retry every frame
    lsfgEngineTried_ = true;

    if (lsfgCachePath_.empty()) {
        RLOG_E("lsfg-native: no shader cache path set");
        return false;
    }
    if (!lsfgVkdInit(vk_)) {
        RLOG_E("lsfg-native: dispatch incomplete; frame generation unavailable");
        return false;
    }

    auto engine = std::make_unique<lsfg::Engine>();
    if (!engine->init(device, physicalDevice, lsfgCachePath_, lsfgCaps_.features.spirvTarget)) {
        RLOG_E("lsfg-native: engine init failed (cache %s, spirv target 0x%x)",
               lsfgCachePath_.c_str(), lsfgCaps_.features.spirvTarget);
        return false;
    }
    lsfgEngine_ = std::move(engine);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
    RLOG("lsfg-native: engine ready (spirv target 0x%x)", lsfgCaps_.features.spirvTarget);
    return true;
}

void VulkanRendererContext::ensureFgQueryPool() {
    if (fgQueryPool_ != VK_NULL_HANDLE || !fgTimestampsOk_ || !vk_.CreateQueryPool) return;
    VkQueryPoolCreateInfo qi{}; qi.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
    qi.queryType = VK_QUERY_TYPE_TIMESTAMP; qi.queryCount = MAX_FRAMES_IN_FLIGHT * 2;
    if (vk_.CreateQueryPool(device, &qi, nullptr, &fgQueryPool_) != VK_SUCCESS) {
        fgQueryPool_ = VK_NULL_HANDLE;
        fgTimestampsOk_ = false;
        RLOG_E("lsfg-native: timestamp query pool unavailable; chain cost will not be reported");
    }
}

void VulkanRendererContext::destroyFgQueryPool() {
    if (fgQueryPool_ != VK_NULL_HANDLE && vk_.DestroyQueryPool)
        vk_.DestroyQueryPool(device, fgQueryPool_, nullptr);
    fgQueryPool_ = VK_NULL_HANDLE;
    for (auto& p : fgQueryPending_) p = false;
}

void VulkanRendererContext::readFgQueryResult() {
    if (fgQueryPool_ == VK_NULL_HANDLE || !fgQueryPending_[currentFrame]) return;
    fgQueryPending_[currentFrame] = false;      // one attempt per sample; a miss is dropped
    uint64_t ts[2] = {0, 0};
    const VkResult r = vk_.GetQueryPoolResults(device, fgQueryPool_, currentFrame * 2, 2,
                                               sizeof(ts), ts, sizeof(uint64_t),
                                               VK_QUERY_RESULT_64_BIT);
    if (r != VK_SUCCESS || ts[1] <= ts[0]) return;
    const double ms = (double)(ts[1] - ts[0]) * (double)fgTimestampPeriodNs_ / 1.0e6;
    const float perGen = (float)(ms / (double)std::max(1u, fgQueryGens_[currentFrame]));
    fgChainMsPerGen_ = fgChainMsPerGen_ < 0.0f
        ? perGen
        : fgChainMsPerGen_ + (perGen - fgChainMsPerGen_) * 0.1f;
    // Same cadence as the engine's own telemetry, so a log carries the cost
    // next to the rates it explains.
    if ((fgChainLogCount_++ % 120u) == 0u)
        RLOG("lsfg-native: chain %.2f ms per generated frame (%.2f ms for %u, smoothed %.2f)",
             perGen, (float)ms, fgQueryGens_[currentFrame], fgChainMsPerGen_);
}

bool VulkanRendererContext::ensureWinFgEngine() {
    if (winfgEngine_) return winfgEngine_->valid();
    if (winfgEngineTried_) return false;     // failed once; don't retry every frame
    winfgEngineTried_ = true;

    auto engine = std::make_unique<winfg::Engine>();
    if (!engine->init(vk_, physicalDevice, device, graphicsQueueFamilyIndex, graphicsQueue)) {
        RLOG_E("winfg-native: engine init failed");
        return false;
    }
    winfgEngine_ = std::move(engine);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
    // Says WHICH win-fg is running. The chain is copied into this tree rather
    // than linked, so without this a log can only name the Bannerlator commit.
    RLOG("winfg-native: engine ready (chain %s, src %s)", WINFG_UPSTREAM, WINFG_CHAIN_HASH);
    return true;
}

bool VulkanRendererContext::fgCapsOk() const {
    if (fgEngineKind_.load(std::memory_order_relaxed) == 1)
        return lsfgCaps_.storageOnSwapchainFormat;
    return lsfgCaps_.supported();
}

int VulkanRendererContext::frameGenProblem() const {
    // explain() replaces "not probed" once the swapchain format has been checked;
    // before that the caps are incomplete, so "unsupported" would be a guess.
    if (std::strcmp(lsfgCaps_.reason, "not probed") == 0) return -1;
    if (!fgCapsOk()) return 1;
    // Read without renderMutex, like frameGenStats: a stale answer here only
    // delays the notice by one poll.
    // An engine that came up (its modules loaded) can still fail to build the
    // chain at vkCreateComputePipelines - proprietary drivers often do the real
    // compile there, so a lowered module (Vulkan 1.1 compat) may pass module
    // creation and be rejected here. The engine object stays alive with
    // unavailable() set; report that as a start failure too, or the drawer
    // would show frame gen armed while nothing generates.
    if (fgEngineKind_.load(std::memory_order_relaxed) == 1)
        return ((winfgEngineTried_ && !winfgEngine_) || (winfgEngine_ && winfgEngine_->unavailable())) ? 2 : 0;
    return ((lsfgEngineTried_ && !lsfgEngine_) || (lsfgEngine_ && lsfgEngine_->unavailable())) ? 2 : 0;
}

void VulkanRendererContext::setFrameGenEngine(int kind) {
    std::lock_guard<std::mutex> lk(renderMutex);
    const int was = fgEngineKind_.exchange(kind, std::memory_order_relaxed);
    if (was == kind) return;
    RLOG("native-fg: engine %s -> %s", was == 1 ? "win-fg" : "lsfg", kind == 1 ? "win-fg" : "lsfg");
    // Only one engine holds GPU resources at a time; the other is rebuilt on
    // demand if the user switches back.
    lsfgEngine_.reset();   lsfgEngineTried_ = false;
    winfgEngine_.reset();  winfgEngineTried_ = false;
    fgConfigDirty_.store(true, std::memory_order_relaxed);
}

void VulkanRendererContext::setWinFgTuning(int model, int perfPreset) {
    fgModel_.store(model, std::memory_order_relaxed);
    fgPerfPreset_.store(perfPreset, std::memory_order_relaxed);
    fgConfigDirty_.store(true, std::memory_order_relaxed);
}

void VulkanRendererContext::recordFrameGenProcess(VkCommandBuffer cb) {
    // Which engine generates is a runtime choice: LSFG needs the user's
    // Lossless.dll, win-fg carries its own shaders. They are mutually
    // exclusive and only one holds GPU resources at a time.
    const bool useWinFg = fgEngineKind_.load(std::memory_order_relaxed) == 1;
    if (!compositeActive() || compositeTargets.empty()) return;
    if (useWinFg ? !winfgEngine_ : !lsfgEngine_) return;
    const CompositeTarget& src = compositeTargets[compositeIndex];
    ensureFgQueryPool();
    if (fgQueryPool_ != VK_NULL_HANDLE && fgPlan_.generations > 0) {
        vk_.CmdResetQueryPool(cb, fgQueryPool_, currentFrame * 2, 2);
        vk_.CmdWriteTimestamp(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, fgQueryPool_, currentFrame * 2);
    }
    // Take frame N as the chain's newest input and run everything that is
    // shared across generations (mipmaps -> alpha -> beta -> gamma -> delta).
    if (useWinFg) winfgEngine_->process(cb, src.img, compositeW, compositeH, fgPlan_.generations);
    else          lsfgEngine_->process(cb, src.img, compositeW, compositeH, fgPlan_.generations);
}

void VulkanRendererContext::recordFrameGenGeneration(VkCommandBuffer cb, uint32_t g) {
    const bool useWinFg = fgEngineKind_.load(std::memory_order_relaxed) == 1;
    if (!compositeActive() || compositeTargets.empty()) return;
    if (useWinFg ? !winfgEngine_ : !lsfgEngine_) return;
    if (g >= fgPlan_.generations) return;
    const uint32_t w = compositeW, h = compositeH;
    {
        const size_t slot = (compositeIndex + 1 + g) % compositeTargets.size();
        const CompositeTarget& dst = compositeTargets[slot];
        if (dst.img == VK_NULL_HANDLE || dst.storageView == VK_NULL_HANDLE) return;

        if (useWinFg) winfgEngine_->generateInto(cb, g, fgPlan_.generations, dst.img, dst.storageView, w, h);
        else          lsfgEngine_->generateInto(cb, g, g, dst.img, dst.storageView, w, h);

        if (fgQueryPool_ != VK_NULL_HANDLE && g + 1 == fgPlan_.generations) {
            vk_.CmdWriteTimestamp(cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, fgQueryPool_, currentFrame * 2 + 1);
            fgQueryPending_[currentFrame] = true;
            fgQueryGens_[currentFrame]    = fgPlan_.generations;
        }

        // dst is left in GENERAL by the chain; move it and the target
        // swapchain image into transfer layouts, copy, then hand the swapchain
        // image to the presentation engine.
        const uint32_t imgIdx = fgPlan_.imgIdx[g];
        VkImageMemoryBarrier pre[2]{};
        pre[0].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        pre[0].oldLayout=VK_IMAGE_LAYOUT_GENERAL; pre[0].newLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
        pre[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        pre[0].image=dst.img; pre[0].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        pre[0].srcAccessMask=VK_ACCESS_SHADER_WRITE_BIT; pre[0].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;

        pre[1].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        pre[1].oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; pre[1].newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        pre[1].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        pre[1].image=swapchainImages[imgIdx]; pre[1].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        pre[1].srcAccessMask=0; pre[1].dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;

        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0,nullptr, 0,nullptr, 2,pre);

        // Copy at panel resolution, blit up when the ring is smaller.
        recordCompositeToSwapchainTransfer(cb, dst.img, imgIdx);

        // Restore the generation target first; the swapchain image's own final
        // transition depends on whether the cursor overlay runs for it.
        VkImageMemoryBarrier post[2]{};
        post[0].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        post[0].oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; post[0].newLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        post[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; post[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        post[0].image=swapchainImages[imgIdx]; post[0].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        post[0].srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; post[0].dstAccessMask=0;
        // Put the generation target back in GENERAL for the next dispatch.
        post[1].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        post[1].oldLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL; post[1].newLayout=VK_IMAGE_LAYOUT_GENERAL;
        post[1].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; post[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        post[1].image=dst.img; post[1].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        post[1].srcAccessMask=VK_ACCESS_TRANSFER_READ_BIT; post[1].dstAccessMask=VK_ACCESS_SHADER_WRITE_BIT;

        if (cursorDrawnPerPresent() && cursorOverlay_.draw) {
            // Only the generation target goes back to GENERAL here; the
            // overlay pass takes the swapchain image to PRESENT_SRC.
            vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0,nullptr, 0,nullptr, 1,&post[1]);
            recordCursorOverlay(cb, imgIdx);
        } else {
            vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                0, 0,nullptr, 0,nullptr, 2,post);
        }
    }
}

void VulkanRendererContext::setLsfgCachePath(const char* path) {
    std::lock_guard<std::mutex> lk(renderMutex);
    const std::string next = path ? path : "";
    // Only a genuinely NEW cache justifies throwing the engine away. This is
    // called on every multiplier change, and rebuilding here meant the chain,
    // the pacer's rate history and the governor's accepted level were all
    // discarded each time - so the governor restarted at zero generations and
    // never survived long enough to probe. Frame generation reported gen=0
    // forever while looking perfectly healthy.
    if (next == lsfgCachePath_ && lsfgEngine_) return;
    lsfgCachePath_ = next;
    lsfgEngineTried_ = false;    // a new cache deserves a fresh attempt
    lsfgEngine_.reset();
}

void VulkanRendererContext::setFrameGenTuning(float flowScale, float refreshHz,
                                              int32_t captureHeight) {
    if (captureHeight < 0) captureHeight = kFgCaptureGame;
    fgFlowScale_.store(flowScale, std::memory_order_relaxed);
    // A zero here means the display could not be read, not "0 Hz". Keeping the
    // last good value stops the pacer's refresh ceiling flapping between 144
    // and unknown, which was visible in the logs as max= alternating.
    if (refreshHz > 1.0f) fgRefreshHz_.store(refreshHz, std::memory_order_relaxed);
    const int32_t wasCapture = fgCaptureHeight_.exchange(captureHeight, std::memory_order_relaxed);
    if (wasCapture != captureHeight)
        RLOG("native-fg: tuning captureHeight=%d->%d (-1 = game, 0 = panel)", wasCapture, captureHeight);
    // A capture-height change resizes the composite ring on the next armed
    // frame (ensureCompositeTargets sees a new extent) and the engine's
    // prepare() rebuilds the chain for it; nothing else has to be forced.
    fgConfigDirty_.store(true, std::memory_order_relaxed);
}

void VulkanRendererContext::compositeExtentFor(uint32_t& w, uint32_t& h) const {
    w = swapchainExt.width; h = swapchainExt.height;
    const int32_t sel = fgCaptureHeight_.load(std::memory_order_relaxed);
    // "Game" is resolved here, not in Java: containerHeight is the X screen the
    // game really renders to (shortcut override, TV resolution and render scale
    // already applied when nativeInit received it).
    const uint32_t want = sel == kFgCaptureGame ? (uint32_t)std::max(containerHeight, 0)
                                                : (uint32_t)std::max(sel, 0);
    if (want == 0 || swapchainExt.width == 0 || swapchainExt.height == 0) return;
    // Never below a quarter of the panel (the chain's 7-level pyramid needs
    // pixels to work with) and never above it (that is just the panel).
    const uint32_t minH = std::max(16u, swapchainExt.height / 4u);
    uint32_t H = std::min(std::max(want, minH), swapchainExt.height);
    if (H >= swapchainExt.height) return;
    uint32_t W = (uint32_t)std::lround((double)H * (double)swapchainExt.width / (double)swapchainExt.height);
    W &= ~1u; H &= ~1u;
    if (W < 2u || H < 2u) return;
    w = W; h = H;
}

void VulkanRendererContext::recordCompositeToSwapchainTransfer(VkCommandBuffer cb, VkImage src,
                                                               uint32_t imgIdx) {
    const bool sameExtent = compositeW == swapchainExt.width && compositeH == swapchainExt.height;
    if (sameExtent || !vk_.CmdBlitImage) {
        // Same format and same extent, so a copy is enough — no filtering.
        VkImageCopy region{};
        region.srcSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        region.dstSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        region.extent={compositeW, compositeH, 1};
        vk_.CmdCopyImage(cb, src, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                         swapchainImages[imgIdx], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
        return;
    }
    // Capture resolution below the panel: the ring is smaller than the
    // swapchain, so the frame - real or generated - is upscaled on its way
    // out. NEAREST is the filter every blittable format supports; LINEAR
    // additionally needs SAMPLED_IMAGE_FILTER_LINEAR on the source format
    // (mandatory for the usual swapchain formats, probed once in
    // createSwapchain), so fall back when it is missing.
    const VkFilter filter = lsfgCaps_.linearBlitOnSwapchainFormat ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
    VkImageBlit blit{};
    blit.srcSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
    blit.srcOffsets[1]={(int32_t)compositeW,(int32_t)compositeH,1};
    blit.dstSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
    blit.dstOffsets[1]={(int32_t)swapchainExt.width,(int32_t)swapchainExt.height,1};
    vk_.CmdBlitImage(cb, src, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                     swapchainImages[imgIdx], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                     1, &blit, filter);
}

// ================== Native LSFG: per-present software cursor =================
// LSFG warps whatever it is handed along the estimated flow field. A cursor
// composited into the frame is therefore smeared across the generated frames,
// which is very visible on a Wine desktop. So while frame gen is armed the
// cursor is left OUT of the composite and drawn once into every presented
// image - real and generated alike - by a load-op pass that also performs the
// final transition to PRESENT_SRC.

bool VulkanRendererContext::cursorDrawnPerPresent() const {
    return compositeActive() && cursorOverlayRenderPass != VK_NULL_HANDLE;
}

bool VulkanRendererContext::createCursorOverlayRenderPass() {
    if (cursorOverlayRenderPass != VK_NULL_HANDLE) return true;

    // Format-compatible with renderPass, so the existing swapchain
    // framebuffers and the window pipeline can both be reused as they are;
    // only the load op and the layouts differ.
    VkAttachmentDescription att{}; att.format=swapchainFmt; att.samples=VK_SAMPLE_COUNT_1_BIT;
    att.loadOp=VK_ATTACHMENT_LOAD_OP_LOAD;       // keep the frame we just copied in
    att.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    att.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    att.finalLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference ref{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{}; sub.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount=1; sub.pColorAttachments=&ref;

    VkSubpassDependency dep{}; dep.srcSubpass=VK_SUBPASS_EXTERNAL; dep.dstSubpass=0;
    dep.srcStageMask=VK_PIPELINE_STAGE_TRANSFER_BIT;
    dep.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
    dep.dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dep.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_READ_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    ci.attachmentCount=1; ci.pAttachments=&att; ci.subpassCount=1; ci.pSubpasses=&sub;
    ci.dependencyCount=1; ci.pDependencies=&dep;
    if (vk_.CreateRenderPass(device,&ci,nullptr,&cursorOverlayRenderPass)!=VK_SUCCESS) {
        RLOG_E("createCursorOverlayRenderPass failed");
        cursorOverlayRenderPass = VK_NULL_HANDLE;
        return false;
    }
    return true;
}

void VulkanRendererContext::recordCursorOverlay(VkCommandBuffer cb, uint32_t imgIdx) {
    if (!cursorDrawnPerPresent()) return;
    const CursorOverlay& c = cursorOverlay_;
    if (!c.draw || cursorDS == VK_NULL_HANDLE || imgIdx >= swapchainFBs.size()) return;

    // The copy left the image in TRANSFER_DST; the render pass wants
    // COLOR_ATTACHMENT_OPTIMAL and hands it on as PRESENT_SRC itself.
    VkImageMemoryBarrier toAttachment{};
    toAttachment.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    toAttachment.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toAttachment.newLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    toAttachment.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    toAttachment.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    toAttachment.image=swapchainImages[imgIdx];
    toAttachment.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    toAttachment.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
    toAttachment.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_READ_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, 0, 0,nullptr, 0,nullptr, 1,&toAttachment);

    VkRenderPassBeginInfo rpi{}; rpi.sType=VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    rpi.renderPass=cursorOverlayRenderPass; rpi.framebuffer=swapchainFBs[imgIdx];
    rpi.renderArea={{0,0},swapchainExt};
    rpi.clearValueCount=0; rpi.pClearValues=nullptr;   // LOAD_OP_LOAD: nothing to clear
    vk_.CmdBeginRenderPass(cb,&rpi,VK_SUBPASS_CONTENTS_INLINE);

    VkViewport vp{0,0,(float)swapchainExt.width,(float)swapchainExt.height,0,1};
    VkRect2D   sc{{0,0},swapchainExt};
    vk_.CmdSetViewport(cb,0,1,&vp);
    vk_.CmdSetScissor(cb,0,1,&sc);
    // `pipeline` is the alpha-blended window pipeline, built against renderPass;
    // format compatibility is what makes it usable with the overlay pass too.
    vk_.CmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);
    vk_.CmdBindDescriptorSets(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeLayout,0,1,&cursorDS,0,nullptr);

    const float cx=(float)std::max(0,(int)c.ptrX-c.hotX), cy=(float)std::max(0,(int)c.ptrY-c.hotY);
    WindowPushConstants pc{};
    pc.ndcX0=(c.ox+cx*c.sx)/c.cw*2.f-1.f;
    pc.ndcY0=(c.oy+cy*c.sy)/c.ch*2.f-1.f;
    pc.ndcX1=(c.ox+(cx+c.w)*c.sx)/c.cw*2.f-1.f;
    pc.ndcY1=(c.oy+(cy+c.h)*c.sy)/c.ch*2.f-1.f;
    pc.useTexAlpha=1;
    vk_.CmdPushConstants(cb,pipeLayout,VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,
                         0,sizeof(pc),&pc);
    vk_.CmdDraw(cb,4,1,0,0);
    vk_.CmdEndRenderPass(cb);
}

void VulkanRendererContext::frameGenStats(float out[6]) const {
    out[0] = out[1] = out[2] = 0.0f;
    out[4] = -1.0f;
    // The presented rate is measured by the renderer itself, per present, and is
    // valid for EVERY engine - including win-fg, whose generated frames arrive
    // from inside the guest. It is reported even with no LSFG engine present.
    out[3] = fgPresentedRate_;
    out[5] = fgChainMsPerGen_;
    if (fgEngineKind_.load(std::memory_order_relaxed) == 1) {
        // win-fg has no governor: what is planned is what is generated.
        if (!winfgEngine_) return;
        out[0] = out[1] = (float)fgPlan_.generations;
        out[2] = fgSourceRate_;
        return;
    }
    if (!lsfgEngine_) return;
    out[0] = (float)lsfgEngine_->acceptedGenerations();
    out[1] = (float)fgPlan_.generations;
    out[2] = lsfgEngine_->sourceRate();
    out[4] = (float)lsfgEngine_->thermalStatus();
}

void VulkanRendererContext::trackPresentedRate(uint32_t presents) {
    const auto now = std::chrono::steady_clock::now();
    if (!fgRateWindowOpen_) {
        fgRateWindowStart_ = now;
        fgRateWindowOpen_  = true;
        fgPresentAccum_    = 0;
        fgSourceAccum_     = 0;
    }
    fgPresentAccum_ += presents;
    fgSourceAccum_  += 1;

    const float elapsed = std::chrono::duration<float>(now - fgRateWindowStart_).count();
    if (elapsed < 0.5f) return;                    // half-second window
    const float rate = (float)fgPresentAccum_ / elapsed;
    // Same smoothing shape the pacer uses, so the two numbers are comparable.
    fgPresentedRate_ = fgPresentedRate_ > 0.0f
        ? fgPresentedRate_ + (rate - fgPresentedRate_) * 0.25f
        : rate;
    const float srcRate = (float)fgSourceAccum_ / elapsed;
    fgSourceRate_ = fgSourceRate_ > 0.0f
        ? fgSourceRate_ + (srcRate - fgSourceRate_) * 0.25f
        : srcRate;
    fgRateWindowStart_ = now;
    fgPresentAccum_    = 0;
    fgSourceAccum_     = 0;
}

void VulkanRendererContext::setFrameGenArmed(bool armed, int multiplier) {
    const bool was = fgArmed_.load(std::memory_order_relaxed);
    const int  wasMult = fgMultiplier_.load(std::memory_order_relaxed);
    fgMultiplier_.store(multiplier, std::memory_order_relaxed);
    // A multiplier change must reach the pacer, or it keeps capping at the
    // level it was built with.
    if (wasMult != multiplier) {
        fgConfigDirty_.store(true, std::memory_order_relaxed);
        // Logged because a multiplier change that does NOT flip the armed state
        // takes the early return below and was previously invisible - exactly
        // the case the r3 run could not explain.
        RLOG("native-fg: multiplier %d -> %d (armed=%d)", wasMult, multiplier, (int)armed);
    }
    if (was == armed) return;

    fgArmed_.store(armed, std::memory_order_relaxed);
    RLOG("native-fg: frame gen %s (multiplier=%d) - recreating swapchain",
         armed ? "ARMED" : "disarmed", multiplier);
    // The swapchain's usage flags and image count both depend on this, so it
    // has to be rebuilt. The existing resize path already does that safely.
    fbResized.store(true);
    dirtyCV.notify_one();
}

bool VulkanRendererContext::compositeActive() const {
    // Every gate must hold, or we run the pre-LSFG path unchanged.
    if (!fgArmed_.load(std::memory_order_relaxed)) return false;
    if (!fgCapsOk()) return false;
    if (scanoutActive.load()) return false;   // direct scanout bypasses the compositor entirely
    return compositeArmed;
}

VkRenderPass VulkanRendererContext::targetRenderPass() const {
    return compositeActive() ? compositeRenderPass : renderPass;
}

VkFramebuffer VulkanRendererContext::targetFramebuffer(uint32_t imgIdx) const {
    if (compositeActive() && compositeIndex < compositeTargets.size())
        return compositeTargets[compositeIndex].fb;
    return swapchainFBs[imgIdx];
}

void VulkanRendererContext::copyCompositeToSwapchain(VkCommandBuffer cb, uint32_t imgIdx) {
    if (!compositeActive() || compositeIndex >= compositeTargets.size()) return;
    const CompositeTarget& t = compositeTargets[compositeIndex];

    // composite: GENERAL (render pass left it there) -> TRANSFER_SRC
    // swapchain: UNDEFINED (nothing has touched it this frame) -> TRANSFER_DST
    VkImageMemoryBarrier pre[2]{};
    pre[0].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    pre[0].oldLayout=VK_IMAGE_LAYOUT_GENERAL; pre[0].newLayout=VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    pre[0].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[0].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    pre[0].image=t.img; pre[0].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    pre[0].srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT|VK_ACCESS_SHADER_WRITE_BIT;
    pre[0].dstAccessMask=VK_ACCESS_TRANSFER_READ_BIT;

    pre[1].sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    pre[1].oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; pre[1].newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    pre[1].srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; pre[1].dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    pre[1].image=swapchainImages[imgIdx]; pre[1].subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    pre[1].srcAccessMask=0; pre[1].dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;

    vk_.CmdPipelineBarrier(cb,
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0,nullptr, 0,nullptr, 2,pre);

    // Same format: a copy at panel resolution, a linear blit when the ring is
    // smaller (experimental capture resolution).
    recordCompositeToSwapchainTransfer(cb, t.img, imgIdx);

    // The cursor overlay, when it runs, takes the image from TRANSFER_DST to
    // PRESENT_SRC itself; only do it here when there is no overlay.
    if (cursorDrawnPerPresent() && cursorOverlay_.draw) {
        recordCursorOverlay(cb, imgIdx);
        return;
    }

    VkImageMemoryBarrier post{};
    post.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    post.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; post.newLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    post.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; post.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    post.image=swapchainImages[imgIdx]; post.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    post.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; post.dstAccessMask=0;
    vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT,
        VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0,nullptr, 0,nullptr, 1,&post);
}

void VulkanRendererContext::destroyColorTarget(VkImage& img, VkDeviceMemory& mem, VkImageView& view,
        VkFramebuffer& fb, VkDescriptorSet& ds) {
    if (ds  !=VK_NULL_HANDLE){ vk_.FreeDescriptorSets(device,winTexPool,1,&ds); ds=VK_NULL_HANDLE; }
    if (fb  !=VK_NULL_HANDLE){ vk_.DestroyFramebuffer(device,fb,nullptr); fb=VK_NULL_HANDLE; }
    if (view!=VK_NULL_HANDLE){ vk_.DestroyImageView(device,view,nullptr); view=VK_NULL_HANDLE; }
    if (img !=VK_NULL_HANDLE){ vk_.DestroyImage(device,img,nullptr); img=VK_NULL_HANDLE; }
    if (mem !=VK_NULL_HANDLE){ vk_.FreeMemory(device,mem,nullptr); mem=VK_NULL_HANDLE; }
}

bool VulkanRendererContext::ensureOffscreen(int w, int h) {
    if (offscreenImg!=VK_NULL_HANDLE && offscreenW==w && offscreenH==h) return true;
    if (w<=0||h<=0) return false;
    if (offscreenImg!=VK_NULL_HANDLE) { vk_.DeviceWaitIdle(device); destroyColorTarget(offscreenImg,offscreenMem,offscreenView,offscreenFB,offscreenDS); }
    offscreenW=offscreenH=0;
    if (!createColorTarget(w,h,offscreenImg,offscreenMem,offscreenView,offscreenFB,offscreenDS)) {
        RLOG_E("ensureOffscreen: createColorTarget %dx%d failed", w, h);
        return false;
    }
    offscreenW=w; offscreenH=h;
    return true;
}

bool VulkanRendererContext::ensureMid(int w, int h) {
    if (midImg!=VK_NULL_HANDLE && midW==w && midH==h) return true;
    if (w<=0||h<=0) return false;
    if (midImg!=VK_NULL_HANDLE) { vk_.DeviceWaitIdle(device); destroyColorTarget(midImg,midMem,midView,midFB,midDS); }
    midW=midH=0;
    if (!createColorTarget(w,h,midImg,midMem,midView,midFB,midDS)) {
        RLOG_E("ensureMid: createColorTarget %dx%d failed", w, h);
        return false;
    }
    midW=w; midH=h;
    return true;
}

bool VulkanRendererContext::ensureFx1(int w, int h) {
    if (fx1Img!=VK_NULL_HANDLE && fx1W==w && fx1H==h) return true;
    if (w<=0||h<=0) return false;
    if (fx1Img!=VK_NULL_HANDLE) { vk_.DeviceWaitIdle(device); destroyColorTarget(fx1Img,fx1Mem,fx1View,fx1FB,fx1DS); }
    fx1W=fx1H=0;
    if (!createColorTarget(w,h,fx1Img,fx1Mem,fx1View,fx1FB,fx1DS)) {
        RLOG_E("ensureFx1: createColorTarget %dx%d failed", w, h);
        return false;
    }
    fx1W=w; fx1H=h;
    return true;
}

bool VulkanRendererContext::ensureFx2(int w, int h) {
    if (fx2Img!=VK_NULL_HANDLE && fx2W==w && fx2H==h) return true;
    if (w<=0||h<=0) return false;
    if (fx2Img!=VK_NULL_HANDLE) { vk_.DeviceWaitIdle(device); destroyColorTarget(fx2Img,fx2Mem,fx2View,fx2FB,fx2DS); }
    fx2W=fx2H=0;
    if (!createColorTarget(w,h,fx2Img,fx2Mem,fx2View,fx2FB,fx2DS)) {
        RLOG_E("ensureFx2: createColorTarget %dx%d failed", w, h);
        return false;
    }
    fx2W=w; fx2H=h;
    return true;
}

void VulkanRendererContext::createWinTexPool() {

    // 128 window textures + cursor + offscreen + mid + fx1 + fx2 (upscaler/effect) targets.
    VkDescriptorPoolSize ps{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 160};
    VkDescriptorPoolCreateInfo ci{}; ci.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    ci.flags=VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
    ci.poolSizeCount=1; ci.pPoolSizes=&ps; ci.maxSets=160;
    if (vk_.CreateDescriptorPool(device,&ci,nullptr,&winTexPool)!=VK_SUCCESS) throw std::runtime_error("wintexpool");
}


void VulkanRendererContext::createCursorDS() {
    VkDescriptorSetAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    ai.descriptorPool=winTexPool; ai.descriptorSetCount=1; ai.pSetLayouts=&dsLayout;
    vk_.AllocateDescriptorSets(device,&ai,&cursorDS);
}

void VulkanRendererContext::createCmdBufs() {
    cmdBufs.resize(MAX_FRAMES_IN_FLIGHT * kMaxPresentsPerFrame);
    VkCommandBufferAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    ai.commandPool=cmdPool; ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY; ai.commandBufferCount=(uint32_t)cmdBufs.size();
    if (vk_.AllocateCommandBuffers(device,&ai,cmdBufs.data())!=VK_SUCCESS) throw std::runtime_error("cmdbuf");
}

void VulkanRendererContext::createSyncObjects() {
    // One semaphore PAIR per pending present (frame slot x presents), but still
    // one fence per frame slot: all presents of a source frame come from a
    // single submit, so they share its completion.
    const uint32_t semCount = MAX_FRAMES_IN_FLIGHT * kMaxPresentsPerFrame;
    imgAvailSems.resize(semCount); renderDoneSems.resize(semCount); inFlightFences.resize(MAX_FRAMES_IN_FLIGHT);
    VkSemaphoreCreateInfo si{}; si.sType=VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    VkFenceCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO; fi.flags=VK_FENCE_CREATE_SIGNALED_BIT;
    for (uint32_t i=0;i<semCount;i++) {
        if (vk_.CreateSemaphore(device,&si,nullptr,&imgAvailSems[i])!=VK_SUCCESS||
            vk_.CreateSemaphore(device,&si,nullptr,&renderDoneSems[i])!=VK_SUCCESS)
            throw std::runtime_error("sync");
    }
    for (uint32_t i=0;i<MAX_FRAMES_IN_FLIGHT;i++) {
        if (vk_.CreateFence(device,&fi,nullptr,&inFlightFences[i])!=VK_SUCCESS)
            throw std::runtime_error("sync");
    }
}

void VulkanRendererContext::recreateSyncObjects() {
    // Caller has already waited on every fence, so nothing is executing; the
    // only thing that can still be outstanding is an acquire semaphore that was
    // signalled for a frame which never got submitted.
    vk_.DeviceWaitIdle(device);

    for (size_t i = 0; i < imgAvailSems.size(); i++)
        if (imgAvailSems[i] != VK_NULL_HANDLE) vk_.DestroySemaphore(device, imgAvailSems[i], nullptr);
    for (size_t i = 0; i < renderDoneSems.size(); i++)
        if (renderDoneSems[i] != VK_NULL_HANDLE) vk_.DestroySemaphore(device, renderDoneSems[i], nullptr);

    const uint32_t semCount = MAX_FRAMES_IN_FLIGHT * kMaxPresentsPerFrame;
    imgAvailSems.assign(semCount, VK_NULL_HANDLE);
    renderDoneSems.assign(semCount, VK_NULL_HANDLE);

    VkSemaphoreCreateInfo si{}; si.sType=VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    for (uint32_t i = 0; i < semCount; i++) {
        if (vk_.CreateSemaphore(device,&si,nullptr,&imgAvailSems[i])!=VK_SUCCESS ||
            vk_.CreateSemaphore(device,&si,nullptr,&renderDoneSems[i])!=VK_SUCCESS)
            throw std::runtime_error("sync");
    }
    currentFrame = 0;
}

void VulkanRendererContext::cleanupSwapchain() {
    // The composite ring is sized to the swapchain extent and its framebuffers
    // reference compositeRenderPass, so it goes with the swapchain.
    destroyCompositeTargets();
    compositeArmed = false;
    for (auto fb:swapchainFBs) vk_.DestroyFramebuffer(device,fb,nullptr); swapchainFBs.clear();
    for (auto iv:swapchainViews) vk_.DestroyImageView(device,iv,nullptr); swapchainViews.clear();
    if (!cmdBufs.empty()){vk_.FreeCommandBuffers(device,cmdPool,(uint32_t)cmdBufs.size(),cmdBufs.data());cmdBufs.clear();}
    if (swapchain!=VK_NULL_HANDLE) { vk_.DestroySwapchainKHR(device,swapchain,nullptr); swapchain=VK_NULL_HANDLE; }
}

uint32_t VulkanRendererContext::findMemType(uint32_t filter, VkMemoryPropertyFlags props) {
    for (uint32_t i=0;i<memProperties.memoryTypeCount;i++)
        if ((filter&(1u<<i))&&(memProperties.memoryTypes[i].propertyFlags&props)==props) return i;
    throw std::runtime_error("memtype");
}

void VulkanRendererContext::createBuffer(VkDeviceSize sz, VkBufferUsageFlags usage,
    VkMemoryPropertyFlags props, VkBuffer& buf, VkDeviceMemory& mem)
{
    VkBufferCreateInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO; bi.size=sz; bi.usage=usage; bi.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    if (vk_.CreateBuffer(device,&bi,nullptr,&buf)!=VK_SUCCESS) throw std::runtime_error("buffer");
    VkMemoryRequirements req; vk_.GetBufferMemoryRequirements(device,buf,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size; ai.memoryTypeIndex=findMemType(req.memoryTypeBits,props);
    if (vk_.AllocateMemory(device,&ai,nullptr,&mem)!=VK_SUCCESS) throw std::runtime_error("bufmem");
    vk_.BindBufferMemory(device,buf,mem,0);
}

VkCommandBuffer VulkanRendererContext::beginOneTime() {
    VkCommandBufferAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    ai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY; ai.commandPool=cmdPool; ai.commandBufferCount=1;
    VkCommandBuffer cb; vk_.AllocateCommandBuffers(device,&ai,&cb);
    VkCommandBufferBeginInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO; bi.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vk_.BeginCommandBuffer(cb,&bi); return cb;
}

void VulkanRendererContext::endOneTime(VkCommandBuffer cb) {
    vk_.EndCommandBuffer(cb);
    VkSubmitInfo si{}; si.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO; si.commandBufferCount=1; si.pCommandBuffers=&cb;
    VkFenceCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO; VkFence fence;
    vk_.CreateFence(device,&fi,nullptr,&fence);
    vk_.QueueSubmit(graphicsQueue,1,&si,fence);
    // ERL bug report #9: bound the wait so a never-signalling fence can't hang the render
    // thread forever with no log output (was UINT64_MAX).
    if (vk_.WaitForFences(device,1,&fence,VK_TRUE,2000000000ULL) != VK_SUCCESS) {
        RLOG_E("endOneTime: fence wait timed out after 2s");
    }
    vk_.DestroyFence(device,fence,nullptr); vk_.FreeCommandBuffers(device,cmdPool,1,&cb);
}

void VulkanRendererContext::transition(VkCommandBuffer cb, VkImage img,
    VkImageLayout ol, VkImageLayout nl, VkAccessFlags sa, VkAccessFlags da,
    VkPipelineStageFlags ss, VkPipelineStageFlags ds)
{
    VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    b.oldLayout=ol; b.newLayout=nl; b.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED; b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
    b.image=img; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}; b.srcAccessMask=sa; b.dstAccessMask=da;
    vk_.CmdPipelineBarrier(cb,ss,ds,0,0,nullptr,0,nullptr,1,&b);
}

bool VulkanRendererContext::createWinTexResources(WinTex& wt, int w, int h) {

    VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.extent={(uint32_t)w,(uint32_t)h,1}; ii.mipLevels=1; ii.arrayLayers=1; ii.format=VK_FORMAT_B8G8R8A8_UNORM;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    ii.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT; ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    if (vk_.CreateImage(device,&ii,nullptr,&wt.img)!=VK_SUCCESS) return false;
    VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,wt.img,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size; ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (vk_.AllocateMemory(device,&ai,nullptr,&wt.mem)!=VK_SUCCESS){vk_.DestroyImage(device,wt.img,nullptr);wt.img=VK_NULL_HANDLE;return false;}
    vk_.BindImageMemory(device,wt.img,wt.mem,0);
    VkImageViewCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO; vi.image=wt.img; vi.viewType=VK_IMAGE_VIEW_TYPE_2D; vi.format=VK_FORMAT_B8G8R8A8_UNORM; vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    vi.components={swapRB?VK_COMPONENT_SWIZZLE_B:VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,swapRB?VK_COMPONENT_SWIZZLE_R:VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
    if (vk_.CreateImageView(device,&vi,nullptr,&wt.view)!=VK_SUCCESS){destroyWinTex(wt);return false;}
    VkDescriptorSetAllocateInfo dsai{}; dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO; dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
    if (vk_.AllocateDescriptorSets(device,&dsai,&wt.ds)!=VK_SUCCESS){destroyWinTex(wt);return false;}
    VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; dii.imageView=wt.view; dii.sampler=sampler;
    VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; wr.dstSet=wt.ds; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    VkDeviceSize stgSz=(VkDeviceSize)w*h*4;
    createBuffer(stgSz,VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,wt.stg,wt.stgMem);
    vk_.MapMemory(device,wt.stgMem,0,stgSz,0,&wt.mapped);
    wt.cap=stgSz; wt.w=w; wt.h=h; wt.needsTransition=true;
    return true;
}

bool VulkanRendererContext::importAHBToWinTex(WinTex& wt, AHardwareBuffer* ahb) {
    if (!vk_.GetAndroidHardwareBufferPropertiesANDROID)
        return false;

    VkAndroidHardwareBufferFormatPropertiesANDROID fmtP{};
    fmtP.sType=VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID;
    VkAndroidHardwareBufferPropertiesANDROID props{};
    props.sType=VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID;
    props.pNext=&fmtP;
    if (vk_.GetAndroidHardwareBufferPropertiesANDROID(device,ahb,&props)!=VK_SUCCESS)
        return false;

    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(ahb,&desc);

    // externalFormat must be 0 or a value from vkGetAndroidHardwareBufferPropertiesANDROID, and a
    // non-zero one also needs format UNDEFINED; a VkFormat number here is invalid usage. Adreno drivers
    // have always accepted it, so they keep it unchanged; on Mali it is 0 (= the plain format below).
    // Same for the view below.
    VkExternalFormatANDROID ef{};
    ef.sType=VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID;
    ef.externalFormat=isMaliDevice_ ? 0 : (swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM);

    VkExternalMemoryImageCreateInfo emi{};
    emi.sType=VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;
    emi.handleTypes=VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;
    ef.pNext=const_cast<void*>(emi.pNext);
    emi.pNext=&ef;

    VkImageCreateInfo ii{};
    ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ii.pNext=&emi; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.format=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;
    ii.extent={desc.width,desc.height,1};
    ii.mipLevels=1; ii.arrayLayers=1; ii.samples=VK_SAMPLE_COUNT_1_BIT;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.usage=VK_IMAGE_USAGE_SAMPLED_BIT;
    ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    if (vk_.CreateImage(device,&ii,nullptr,&wt.img)!=VK_SUCCESS)
        return false;

    VkImportAndroidHardwareBufferInfoANDROID imp{};
    imp.sType=VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID;
    imp.buffer=ahb;

    VkMemoryDedicatedAllocateInfo ded{};
    ded.sType=VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    ded.pNext=&imp; ded.image=wt.img;

    VkMemoryAllocateInfo mai{};
    mai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.pNext=&ded; mai.allocationSize=props.allocationSize;
    mai.memoryTypeIndex=findMemType(props.memoryTypeBits,0);
    if (vk_.AllocateMemory(device,&mai,nullptr,&wt.mem)!=VK_SUCCESS){
        vk_.DestroyImage(device,wt.img,nullptr);
        wt.img=VK_NULL_HANDLE;
        return false;
    }
    vk_.BindImageMemory(device,wt.img,wt.mem,0);

    VkExternalFormatANDROID vef{};
    vef.sType=VK_STRUCTURE_TYPE_EXTERNAL_FORMAT_ANDROID;
    vef.externalFormat=isMaliDevice_ ? 0 : (swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM);

    VkImageViewCreateInfo vi{};
    vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    vi.pNext=&vef; vi.image=wt.img; vi.viewType=VK_IMAGE_VIEW_TYPE_2D;
    vi.format=swapRB ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_B8G8R8A8_UNORM;
    vi.components={VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY,
                   VK_COMPONENT_SWIZZLE_IDENTITY,VK_COMPONENT_SWIZZLE_IDENTITY};
    vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    if (vk_.CreateImageView(device,&vi,nullptr,&wt.view)!=VK_SUCCESS){
        destroyWinTex(wt);
        return false;
    }

    VkDescriptorSetAllocateInfo dsai{};
    dsai.sType=VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    dsai.descriptorPool=winTexPool; dsai.descriptorSetCount=1; dsai.pSetLayouts=&dsLayout;
    VkResult dsRes=vk_.AllocateDescriptorSets(device,&dsai,&wt.ds);
    if (dsRes==VK_ERROR_OUT_OF_POOL_MEMORY){
        RLOG_E("importAHBToWinTex: descriptor pool exhausted for AHB texture");
        destroyWinTex(wt);
        return false;
    }
    if (dsRes!=VK_SUCCESS){ destroyWinTex(wt); return false; }

    VkDescriptorImageInfo dii{};
    dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    dii.imageView=wt.view; dii.sampler=sampler;

    VkWriteDescriptorSet wr{};
    wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    wr.dstSet=wt.ds; wr.dstBinding=0;
    wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);

    wt.needsTransition=true;
    wt.isAHB=true;
    wt.w=(int)desc.width;
    wt.h=(int)desc.height;
    return true;
}

void VulkanRendererContext::destroyWinTex(WinTex& wt) {
    if (wt.isAHB) {


        wt = {};
        return;
    }
    if (wt.img!=VK_NULL_HANDLE || wt.stg!=VK_NULL_HANDLE) {
        
        WinTex deferred = wt;
        deferred.isAHB = false;
        deleteQueue.push_back(deferred);
    }
    wt={};
}

void VulkanRendererContext::ensureCursorTex(short w, short h) {
    if (cursorImg!=VK_NULL_HANDLE && cursorTexW==w && cursorTexH==h) return;
    cleanupCursorTex();
    VkImageCreateInfo ii{}; ii.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO; ii.imageType=VK_IMAGE_TYPE_2D;
    ii.extent={(uint32_t)w,(uint32_t)h,1}; ii.mipLevels=1; ii.arrayLayers=1; ii.format=VK_FORMAT_B8G8R8A8_UNORM;
    ii.tiling=VK_IMAGE_TILING_OPTIMAL; ii.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
    ii.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT; ii.samples=VK_SAMPLE_COUNT_1_BIT; ii.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
    vk_.CreateImage(device,&ii,nullptr,&cursorImg);
    VkMemoryRequirements req; vk_.GetImageMemoryRequirements(device,cursorImg,&req);
    VkMemoryAllocateInfo ai{}; ai.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO; ai.allocationSize=req.size; ai.memoryTypeIndex=findMemType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    vk_.AllocateMemory(device,&ai,nullptr,&cursorMem); vk_.BindImageMemory(device,cursorImg,cursorMem,0);
    VkImageViewCreateInfo vi{}; vi.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO; vi.image=cursorImg; vi.viewType=VK_IMAGE_VIEW_TYPE_2D; vi.format=VK_FORMAT_B8G8R8A8_UNORM; vi.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
    vk_.CreateImageView(device,&vi,nullptr,&cursorView);
    VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; dii.imageView=cursorView; dii.sampler=sampler;
    VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET; wr.dstSet=cursorDS; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; wr.descriptorCount=1; wr.pImageInfo=&dii;
    vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);

    cursorTexW=w; cursorTexH=h;
}

void VulkanRendererContext::cleanupCursorTex() {
    if (cursorView!=VK_NULL_HANDLE){vk_.DestroyImageView(device,cursorView,nullptr);cursorView=VK_NULL_HANDLE;}
    if (cursorImg!=VK_NULL_HANDLE){vk_.DestroyImage(device,cursorImg,nullptr);cursorImg=VK_NULL_HANDLE;}
    if (cursorMem!=VK_NULL_HANDLE){vk_.FreeMemory(device,cursorMem,nullptr);cursorMem=VK_NULL_HANDLE;}
    if (cursorStg!=VK_NULL_HANDLE){vk_.DestroyBuffer(device,cursorStg,nullptr);vk_.FreeMemory(device,cursorStgM,nullptr);cursorStg=VK_NULL_HANDLE;cursorStgP=nullptr;cursorStgC=0;}
    cursorTexW=0; cursorTexH=0;
}

void VulkanRendererContext::ensureCursorStaging(VkDeviceSize sz) {
    if (cursorStgC>=sz) return;
    if (cursorStg!=VK_NULL_HANDLE){vk_.DestroyBuffer(device,cursorStg,nullptr);vk_.FreeMemory(device,cursorStgM,nullptr);}
    createBuffer(sz,VK_BUFFER_USAGE_TRANSFER_SRC_BIT,VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,cursorStg,cursorStgM);
    vk_.MapMemory(device,cursorStgM,0,sz,0,&cursorStgP); cursorStgC=sz;
}

void VulkanRendererContext::recordCmdBuf(VkCommandBuffer cb, uint32_t imgIdx,
    const std::vector<DrawEntry>& draws,
    std::vector<VkImageMemoryBarrier>& ahbTransitions,
    std::vector<VkImageMemoryBarrier>& preUpload,
    std::vector<VkImageMemoryBarrier>& postUpload,
    VkBuffer cursorUpload, bool hasCursorUpload,
    float ox, float oy, float sx, float sy, float cw, float ch,
    short ptrX, short ptrY, short curHotX, short curHotY,
    short curW, short curH, bool curVis)
{
    VkCommandBufferBeginInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    if (vk_.BeginCommandBuffer(cb,&bi)!=VK_SUCCESS) throw std::runtime_error("begin cb");







    ahbTransitions.clear(); preUpload.clear(); postUpload.clear();

    for (auto& d : draws) {
        if (d.img==VK_NULL_HANDLE) continue;
        if (d.isAHB && d.needsTransition) {
            VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            b.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            b.image=d.img; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
            b.srcAccessMask=0; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
            ahbTransitions.push_back(b);
        } else if (!d.isAHB && (d.needsTransition || d.upload!=VK_NULL_HANDLE)) {
            VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
            b.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED; b.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            b.image=d.img; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
            b.srcAccessMask=0; b.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
            preUpload.push_back(b);
            b.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            b.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
            postUpload.push_back(b);
        }
    }

    if (!ahbTransitions.empty())
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, 0, nullptr, 0, nullptr, (uint32_t)ahbTransitions.size(), ahbTransitions.data());
    if (!preUpload.empty())
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
            0, 0, nullptr, 0, nullptr, (uint32_t)preUpload.size(), preUpload.data());


    for (auto& d : draws) {
        if (d.isAHB || d.upload==VK_NULL_HANDLE || d.img==VK_NULL_HANDLE) continue;
        VkBufferImageCopy r{}; r.bufferOffset=0; r.bufferRowLength=0; r.bufferImageHeight=0;
        r.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        r.imageExtent={(uint32_t)d.w,(uint32_t)d.h,1};
        vk_.CmdCopyBufferToImage(cb, d.upload, d.img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &r);
    }

    bool cursorDrawn = curVis && cursorImg!=VK_NULL_HANDLE && cursorDS!=VK_NULL_HANDLE;

    // Frame gen: keep the cursor OUT of the composite - LSFG would warp it
    // along the flow field and smear it - and draw it into every presented
    // image instead, real and generated alike.
    cursorOverlay_ = CursorOverlay{};
    if (cursorDrawnPerPresent()) {
        cursorOverlay_.draw = cursorDrawn;
        cursorOverlay_.ox = ox; cursorOverlay_.oy = oy;
        cursorOverlay_.sx = sx; cursorOverlay_.sy = sy;
        cursorOverlay_.cw = cw; cursorOverlay_.ch = ch;
        cursorOverlay_.ptrX = ptrX; cursorOverlay_.ptrY = ptrY;
        cursorOverlay_.hotX = curHotX; cursorOverlay_.hotY = curHotY;
        cursorOverlay_.w = curW; cursorOverlay_.h = curH;
        cursorDrawn = false;
    }
    bool hasCursorCopy = hasCursorUpload && cursorImg!=VK_NULL_HANDLE && cursorUpload!=VK_NULL_HANDLE;
    if (hasCursorCopy) {
        VkImageMemoryBarrier b{}; b.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        b.oldLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL; b.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        b.srcQueueFamilyIndex=b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        b.image=cursorImg; b.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        b.srcAccessMask=VK_ACCESS_SHADER_READ_BIT; b.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
            0, 0, nullptr, 0, nullptr, 1, &b);
        VkBufferImageCopy r{}; r.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
        r.imageExtent={(uint32_t)curW,(uint32_t)curH,1};
        vk_.CmdCopyBufferToImage(cb, cursorUpload, cursorImg, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &r);
        b.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        b.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
        postUpload.push_back(b);
    }

    if (!postUpload.empty())
        vk_.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
            0, 0, nullptr, 0, nullptr, (uint32_t)postUpload.size(), postUpload.data());

    // Experimental capture resolution: while frame gen runs on a composite
    // ring smaller than the swapchain, the whole scene is drawn at the ring's
    // size and blitted up on the way out. Both paths below size themselves by
    // renderExtent(), so the effect chain, the spatial upscalers and the
    // render-scale downscale keep working on the smaller ring.
    const VkExtent2D targetExt = renderExtent();

    if (upFrame.active) {
        // Spatial-upscaler / effects path: composite to an offscreen target at
        // game res, then upscale / run the effect chain into the render target
        // (see recordUpscalePasses).
        recordUpscalePasses(cb, imgIdx, draws, cursorDrawn,
            ptrX, ptrY, curHotX, curHotY, curW, curH,
            ox, oy, sx, sy, cw, ch);
    } else {

    VkRenderPassBeginInfo rpi{}; rpi.sType=VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    // Frame gen redirects the final target to a composite image we own; the
    // accessors return the swapchain pair unchanged when it is off.
    rpi.renderPass=targetRenderPass(); rpi.framebuffer=targetFramebuffer(imgIdx); rpi.renderArea={{0,0},targetExt};
    VkClearValue clr={{{0.f,0.f,0.f,1.f}}}; rpi.clearValueCount=1; rpi.pClearValues=&clr;

    vk_.CmdBeginRenderPass(cb, &rpi, VK_SUBPASS_CONTENTS_INLINE);
    // The window quads are placed in NDC (normalised to the surface), so a
    // same-aspect smaller target needs only a smaller viewport, not new maths.
    VkViewport vp{0,0,(float)targetExt.width,(float)targetExt.height,0,1};
    vk_.CmdSetViewport(cb, 0, 1, &vp);
    // #413: clip the compositor draws to the game region (a half of the surface on TOP/BOTTOM alignment)
    // so a FILL/STRETCH game that overflows the region is cropped and can't bleed into the on-screen-
    // controls half. The renderpass clear still covers the whole renderArea (full swapchain, above), so
    // the controls half is painted black each frame. A full-surface (or w<=0) clip region disables the
    // crop, making the scissor the whole swapchain -> byte-identical to the historical CENTER output.
    VkRect2D sc{{0,0},targetExt};
    {
        int32_t cx = clipRegionX.load(std::memory_order_relaxed);
        int32_t cy = clipRegionY.load(std::memory_order_relaxed);
        int32_t cw = clipRegionW.load(std::memory_order_relaxed);
        int32_t ch = clipRegionH.load(std::memory_order_relaxed);
        if (cw > 0 && ch > 0) {
            // The clip rect is in surface pixels; scale it onto a downsized ring.
            const float kx = (float)targetExt.width  / (float)std::max(1u, swapchainExt.width);
            const float ky = (float)targetExt.height / (float)std::max(1u, swapchainExt.height);
            int32_t l = std::max(0, (int32_t)std::lround(cx * kx));
            int32_t t = std::max(0, (int32_t)std::lround(cy * ky));
            int32_t r = std::min((int32_t)targetExt.width,  (int32_t)std::lround((cx + cw) * kx));
            int32_t b = std::min((int32_t)targetExt.height, (int32_t)std::lround((cy + ch) * ky));
            if (r > l && b > t) { sc.offset = {l, t}; sc.extent = {(uint32_t)(r - l), (uint32_t)(b - t)}; }
        }
    }
    vk_.CmdSetScissor(cb, 0, 1, &sc);

    vk_.CmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    for (auto& d : draws) {
        if (d.ds==VK_NULL_HANDLE) continue;
        vk_.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLayout, 0, 1, &d.ds, 0, nullptr);
        WindowPushConstants pc{};
        pc.ndcX0=(ox+(float)d.x*sx)/cw*2.f-1.f;
        pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
        pc.ndcX1=(ox+(float)(d.x+d.w)*sx)/cw*2.f-1.f;
        pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
        pc.useTexAlpha = 0;
        vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(pc), &pc);
        vk_.CmdDraw(cb, 4, 1, 0, 0);
    }

    if (cursorDrawn) {

        vk_.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLayout, 0, 1, &cursorDS, 0, nullptr);
        float cx=(float)std::max(0,(int)ptrX-curHotX), cy=(float)std::max(0,(int)ptrY-curHotY);
        WindowPushConstants cpc{};
        cpc.ndcX0=(ox+cx*sx)/cw*2.f-1.f; cpc.ndcY0=(oy+cy*sy)/ch*2.f-1.f;
        cpc.ndcX1=(ox+(cx+curW)*sx)/cw*2.f-1.f; cpc.ndcY1=(oy+(cy+curH)*sy)/ch*2.f-1.f;
        cpc.useTexAlpha = 1;
        vk_.CmdPushConstants(cb, pipeLayout, VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(cpc), &cpc);
        vk_.CmdDraw(cb, 4, 1, 0, 0);
    }
    vk_.CmdEndRenderPass(cb);
    } // end !upFrame.active

    // Frame gen: the chain's shared passes and the FIRST generated frame go in
    // this command buffer. Every later generated frame and the real frame are
    // recorded into their own buffers in renderFrame, each submitted and
    // presented as soon as it is done. With no generation this is just the
    // real frame's copy (a no-op off the composite path).
    //
    // process() runs EVERY composite frame, generating or not: it is where the
    // engine counts frames and seeds its input history, and it only starts
    // generating once it has seen enough of them. r1 of this change called it
    // only when a generation was already planned, so the count never advanced,
    // the engine stayed cold for the whole session and plan() returned 0 every
    // frame - presented rate equal to the guest rate, exactly.
    recordFrameGenProcess(cb);
    if (fgPlan_.generations > 0) recordFrameGenGeneration(cb, 0);
    else                         copyCompositeToSwapchain(cb, imgIdx);

    VkResult endStatus = vk_.EndCommandBuffer(cb);
    if (endStatus!=VK_SUCCESS) {
        RLOG_E("recordCmdBuf: EndCommandBuffer failed with status=%d (swapRB=%d draws=%zu imgIdx=%u)",
            (int)endStatus, (int)swapRB, draws.size(), imgIdx);
        throw std::runtime_error("end cb");
    }
}

void VulkanRendererContext::recordUpscalePasses(VkCommandBuffer cb, uint32_t imgIdx,
    const std::vector<DrawEntry>& draws, bool cursorDrawn,
    short ptrX, short ptrY, short curHotX, short curHotY, short curW, short curH,
    float ox, float oy, float sx, float sy, float scw, float sch)
{
    const VkClearValue blk={{{0.f,0.f,0.f,1.f}}};
    // The output extent: the swapchain, or the composite ring while frame gen
    // runs below panel resolution (planUpscaleFrame sized everything by it).
    const VkExtent2D outExt = renderExtent();
    const VkViewport fullVp{0,0,(float)outExt.width,(float)outExt.height,0,1};
    const VkRect2D   fullSc{{0,0},outExt};

    const bool hasScaling = (upFrame.mode>=3 && upFrame.mode<=8) || upFrame.mode==UPMODE_DOWNSCALE;
    // Must include EVERY chainable effect (matches planUpscaleFrame's fxOn) — else a
    // scaling mode (SGSR/FSR/Sharpen/downscale) treats the scale as final and writes
    // straight to the swapchain, skipping the effect chain (CRT/NTSC/etc. silently dropped).
    const bool fxOn       = upFrame.cas || upFrame.hdr || upFrame.fxaa || upFrame.toon
                          || upFrame.color || upFrame.ntsc || upFrame.crt || upFrame.deband;

    // Composite all game windows (+ cursor) into `fb` (an offscreenRenderPass target).
    // `identity` 1:1-maps each window into a W x H target (upscaler input at game res);
    // otherwise the normal scene transform places them (effects-only path = direct path
    // geometry, just rendered to fx1 so an effect can sample it).
    auto composite=[&](VkFramebuffer fb,int W,int H,bool identity,float cw,float ch){
        VkRenderPassBeginInfo rpi{}; rpi.sType=VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        rpi.renderPass=offscreenRenderPass; rpi.framebuffer=fb;
        rpi.renderArea={{0,0},{(uint32_t)W,(uint32_t)H}};
        rpi.clearValueCount=1; rpi.pClearValues=&blk;
        vk_.CmdBeginRenderPass(cb,&rpi,VK_SUBPASS_CONTENTS_INLINE);
        VkViewport vp{0,0,(float)W,(float)H,0,1}; vk_.CmdSetViewport(cb,0,1,&vp);
        VkRect2D scr{{0,0},{(uint32_t)W,(uint32_t)H}}; vk_.CmdSetScissor(cb,0,1,&scr);
        vk_.CmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);
        for (auto& d : draws) {
            if (d.ds==VK_NULL_HANDLE) continue;
            vk_.CmdBindDescriptorSets(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeLayout,0,1,&d.ds,0,nullptr);
            WindowPushConstants pc{};
            if (identity) {
                pc.ndcX0=(float)d.x/cw*2.f-1.f;       pc.ndcY0=(float)d.y/ch*2.f-1.f;
                pc.ndcX1=(float)(d.x+d.w)/cw*2.f-1.f; pc.ndcY1=(float)(d.y+d.h)/ch*2.f-1.f;
            } else {
                pc.ndcX0=(ox+(float)d.x*sx)/cw*2.f-1.f;        pc.ndcY0=(oy+(float)d.y*sy)/ch*2.f-1.f;
                pc.ndcX1=(ox+(float)(d.x+d.w)*sx)/cw*2.f-1.f;  pc.ndcY1=(oy+(float)(d.y+d.h)*sy)/ch*2.f-1.f;
            }
            pc.useTexAlpha=0;
            vk_.CmdPushConstants(cb,pipeLayout,VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,0,sizeof(pc),&pc);
            vk_.CmdDraw(cb,4,1,0,0);
        }
        if (cursorDrawn) {
            vk_.CmdBindDescriptorSets(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeLayout,0,1,&cursorDS,0,nullptr);
            float cx=(float)std::max(0,(int)ptrX-curHotX), cy=(float)std::max(0,(int)ptrY-curHotY);
            WindowPushConstants cpc{};
            if (identity) {
                cpc.ndcX0=cx/cw*2.f-1.f; cpc.ndcY0=cy/ch*2.f-1.f;
                cpc.ndcX1=(cx+curW)/cw*2.f-1.f; cpc.ndcY1=(cy+curH)/ch*2.f-1.f;
            } else {
                cpc.ndcX0=(ox+cx*sx)/cw*2.f-1.f; cpc.ndcY0=(oy+cy*sy)/ch*2.f-1.f;
                cpc.ndcX1=(ox+(cx+curW)*sx)/cw*2.f-1.f; cpc.ndcY1=(oy+(cy+curH)*sy)/ch*2.f-1.f;
            }
            cpc.useTexAlpha=1;
            vk_.CmdPushConstants(cb,pipeLayout,VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,0,sizeof(cpc),&cpc);
            vk_.CmdDraw(cb,4,1,0,0);
        }
        vk_.CmdEndRenderPass(cb);
    };

    // A full-target post pass: sample `srcDS`, draw the quad into `fb` at the full
    // swapchain viewport. `toSwapchain` selects which render pass `fb` belongs to.
    auto postPass=[&](VkPipeline pl, VkFramebuffer fb, bool toSwapchain,
                      VkDescriptorSet srcDS, const void* pcd, uint32_t pcsz){
        VkRenderPassBeginInfo rpi{}; rpi.sType=VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        rpi.renderPass = toSwapchain ? targetRenderPass() : offscreenRenderPass;
        rpi.framebuffer=fb; rpi.renderArea={{0,0},outExt};
        rpi.clearValueCount=1; rpi.pClearValues=&blk;
        vk_.CmdBeginRenderPass(cb,&rpi,VK_SUBPASS_CONTENTS_INLINE);
        vk_.CmdSetViewport(cb,0,1,&fullVp); vk_.CmdSetScissor(cb,0,1,&fullSc);
        vk_.CmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,pl);
        vk_.CmdBindDescriptorSets(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,postPipeLayout,0,1,&srcDS,0,nullptr);
        vk_.CmdPushConstants(cb,postPipeLayout,VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,0,pcsz,pcd);
        vk_.CmdDraw(cb,4,1,0,0);
        vk_.CmdEndRenderPass(cb);
    };

    // ---- Stage 1 (+ scaling): produce the pre-effects image; curDS = its sampler.
    VkDescriptorSet curDS = VK_NULL_HANDLE;
    if (hasScaling) {
        // composite 1:1 into offscreen at game res (the upscaler input)
        composite(offscreenFB, offscreenW, offscreenH, /*identity*/true, (float)offscreenW, (float)offscreenH);
        // The scaling stage writes the swapchain directly only when no effect follows;
        // otherwise it writes fx1 so CAS/HDR can sample it. Cross-binding a swapchain-
        // renderPass post pipeline into an offscreenRenderPass fx target reuses the
        // exact pattern the composite stage already uses (window `pipeline` -> offscreen).
        const bool scaleFinal = !fxOn;
        VkFramebuffer stFB = scaleFinal ? targetFramebuffer(imgIdx) : fx1FB;
        if (upFrame.mode==3 || upFrame.mode==6 || upFrame.mode==7 || upFrame.mode==8 || upFrame.mode==UPMODE_DOWNSCALE) {
            VkPipeline   pl   = (upFrame.mode==3) ? sgsrPipeline
                              : (upFrame.mode==8) ? sgsrQualityPipeline
                              : (upFrame.mode==7) ? nisPipeline
                              : (upFrame.mode==6) ? rcasPipeline
                              :                     downscalePipeline;
            const void*  pcd; uint32_t pcsz;
            if (upFrame.mode==3 || upFrame.mode==8) { pcd=&upFrame.sgsrPC; pcsz=sizeof(upFrame.sgsrPC); }
            else if (upFrame.mode==7) { pcd=&upFrame.nisPC;  pcsz=sizeof(upFrame.nisPC);  }
            else if (upFrame.mode==6) { pcd=&upFrame.rcasPC; pcsz=sizeof(upFrame.rcasPC); }
            else                      { pcd=&upFrame.dsPC;   pcsz=sizeof(upFrame.dsPC);   }
            postPass(pl, stFB, scaleFinal, offscreenDS, pcd, pcsz);
        } else {
            // FSR: EASU offscreen -> mid (at output res, mid viewport), then RCAS mid -> stFB.
            VkRenderPassBeginInfo rpi{}; rpi.sType=VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
            rpi.renderPass=offscreenRenderPass; rpi.framebuffer=midFB;
            rpi.renderArea={{0,0},{(uint32_t)midW,(uint32_t)midH}};
            rpi.clearValueCount=1; rpi.pClearValues=&blk;
            vk_.CmdBeginRenderPass(cb,&rpi,VK_SUBPASS_CONTENTS_INLINE);
            VkViewport mvp{0,0,(float)midW,(float)midH,0,1}; vk_.CmdSetViewport(cb,0,1,&mvp);
            VkRect2D msc{{0,0},{(uint32_t)midW,(uint32_t)midH}}; vk_.CmdSetScissor(cb,0,1,&msc);
            vk_.CmdBindPipeline(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,easuPipeline);
            vk_.CmdBindDescriptorSets(cb,VK_PIPELINE_BIND_POINT_GRAPHICS,postPipeLayout,0,1,&offscreenDS,0,nullptr);
            vk_.CmdPushConstants(cb,postPipeLayout,VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,0,sizeof(upFrame.easuPC),&upFrame.easuPC);
            vk_.CmdDraw(cb,4,1,0,0);
            vk_.CmdEndRenderPass(cb);
            postPass(rcasPipeline, stFB, scaleFinal, midDS, &upFrame.rcasPC, sizeof(upFrame.rcasPC));
        }
        if (scaleFinal) return;     // no effects: scaling wrote the swapchain, done
        curDS = fx1DS;
    } else {
        // Effects-only (no scaling mode active): composite with the normal scene
        // transform straight into fx1 (full output extent), then run the effect chain.
        composite(fx1FB, (int)outExt.width, (int)outExt.height, /*identity*/false, scw, sch);
        curDS = fx1DS;
    }

    // ---- Effect chain in the LOCKED canonical order:
    //          FXAA -> Toon -> Color -> CAS -> HDR -> NTSC -> CRT
    //      (AA first, stylize/grade the clean image, sharpen, bloom, then the
    //       output-medium emulation last: analog NTSC signal, then the CRT tube.)
    //      The last active effect writes the swapchain; earlier ones ping-pong
    //      between fx1 and fx2 (2 buffers suffice for any chain length).
    //      Debanding is the TERMINAL pass: appended LAST so it always ends the
    //      chain and writes the swapchain (the dither must match the display
    //      quantizer, after every other effect has produced final pixels).
    enum { FX_FXAA=0, FX_TOON, FX_COLOR, FX_CAS, FX_HDR, FX_NTSC, FX_CRT, FX_DEBAND };
    int effects[8]; int n=0;
    if (upFrame.fxaa)  effects[n++]=FX_FXAA;
    if (upFrame.toon)  effects[n++]=FX_TOON;
    if (upFrame.color) effects[n++]=FX_COLOR;
    if (upFrame.cas)   effects[n++]=FX_CAS;
    if (upFrame.hdr)   effects[n++]=FX_HDR;
    if (upFrame.ntsc)  effects[n++]=FX_NTSC;
    if (upFrame.crt)   effects[n++]=FX_CRT;
    if (upFrame.deband)effects[n++]=FX_DEBAND;
    for (int i=0;i<n;i++) {
        const bool toSwap = (i==n-1);
        VkFramebuffer tgtFB; VkDescriptorSet tgtDS;
        if (toSwap)               { tgtFB=targetFramebuffer(imgIdx); tgtDS=VK_NULL_HANDLE; }
        else if (curDS==fx1DS)    { tgtFB=fx2FB; tgtDS=fx2DS; }
        else                      { tgtFB=fx1FB; tgtDS=fx1DS; }
        switch (effects[i]) {
            case FX_FXAA:  postPass(toSwap?fxaaPipelineSwap :fxaaPipelineOff,  tgtFB, toSwap, curDS, &upFrame.fxaaPC,  sizeof(upFrame.fxaaPC));  break;
            case FX_TOON:  postPass(toSwap?toonPipelineSwap :toonPipelineOff,  tgtFB, toSwap, curDS, &upFrame.toonPC,  sizeof(upFrame.toonPC));  break;
            case FX_COLOR: postPass(toSwap?colorPipelineSwap:colorPipelineOff, tgtFB, toSwap, curDS, &upFrame.colorPC, sizeof(upFrame.colorPC)); break;
            case FX_CAS:   postPass(toSwap?casPipelineSwap  :casPipelineOff,   tgtFB, toSwap, curDS, &upFrame.casPC,   sizeof(upFrame.casPC));   break;
            case FX_HDR:   postPass(toSwap?hdrPipelineSwap  :hdrPipelineOff,   tgtFB, toSwap, curDS, &upFrame.hdrPC,   sizeof(upFrame.hdrPC));   break;
            case FX_NTSC:  postPass(toSwap?ntscPipelineSwap :ntscPipelineOff,  tgtFB, toSwap, curDS, &upFrame.ntscPC,  sizeof(upFrame.ntscPC));  break;
            case FX_CRT:   postPass(toSwap?crtPipelineSwap  :crtPipelineOff,   tgtFB, toSwap, curDS, &upFrame.crtPC,   sizeof(upFrame.crtPC));   break;
            // Deband is always last (toSwap == true here) -> swap-only pipeline.
            case FX_DEBAND:postPass(debandPipelineSwap,                        tgtFB, toSwap, curDS, &upFrame.debandPC,sizeof(upFrame.debandPC));break;
        }
        if (!toSwap) curDS = tgtDS;
    }
}

// Decide whether and how the spatial upscaler runs this frame, and pre-compute
// the FSR constants / destination rect. Must be called on the render thread,
// before recordCmdBuf. Resources are (re)created here as needed.
void VulkanRendererContext::planUpscaleFrame() {
    upFrame.active=false;
    // CAS sharpness 0 = OFF (skip the pass entirely) so the slider's 0 stop is true neutral.
    const bool casOn = casEnabled && casSharpness > 0;
    upFrame.cas  = casOn;
    upFrame.hdr  = hdrEnabled;
    upFrame.fxaa = fxaaEnabled;
    upFrame.toon = toonEnabled;
    upFrame.color= colorEnabled;
    upFrame.ntsc = ntscEnabled;
    upFrame.crt  = crtEnabled;
    upFrame.deband = debandEnabled;
    if (containerWidth<=0 || containerHeight<=0) return;
    if (swapchain==VK_NULL_HANDLE) return;

    // Any of the 7 composable effects engages the post chain (even at mode 0/1/2).
    const bool fxOn = casOn || hdrEnabled || fxaaEnabled || toonEnabled ||
                      colorEnabled || ntscEnabled || crtEnabled || debandEnabled;
    // Everything below is sized by the output extent: the swapchain, or the
    // composite ring while frame gen runs below panel resolution (experimental
    // capture resolution). renderFrame arms/resizes the ring before this runs.
    const VkExtent2D outExt = renderExtent();
    const float scW=(float)outExt.width, scH=(float)outExt.height;
    const bool renderAboveDisplay =
        ((int)outExt.width<containerWidth || (int)outExt.height<containerHeight);
    const bool renderBelowDisplay =
        ((int)outExt.width>containerWidth || (int)outExt.height>containerHeight);

    // Aspect-fit letterbox rect for the output extent (used by all fit paths).
    auto fitRect=[&](int& outX,int& outY,int& outW,int& outH){
        float a=std::min(scW/(float)containerWidth, scH/(float)containerHeight);
        outW=(int)((float)containerWidth*a+0.5f);
        outH=(int)((float)containerHeight*a+0.5f);
        outX=((int)outExt.width-outW)/2;
        outY=((int)outExt.height-outH)/2;
    };

    bool scaling=false;

    // ---- Supersampling: high-quality downscale (render res > display res) ----
    // Independent of upscalerMode; takes priority when its precondition holds.
    if (hqDownscale && renderAboveDisplay) {
        int outX,outY,outW,outH; fitRect(outX,outY,outW,outH);
        if (outW>0 && outH>0 && ensureOffscreen(containerWidth,containerHeight)) {
            upFrame.active=true; upFrame.mode=UPMODE_DOWNSCALE; scaling=true;
            upFrame.outX=outX; upFrame.outY=outY; upFrame.outW=outW; upFrame.outH=outH;
            DownscalePushConstants& d=upFrame.dsPC;
            d.ndc[0]=(float)outX/scW*2.f-1.f;          d.ndc[1]=(float)outY/scH*2.f-1.f;
            d.ndc[2]=(float)(outX+outW)/scW*2.f-1.f;   d.ndc[3]=(float)(outY+outH)/scH*2.f-1.f;
            d.srcW=(float)containerWidth; d.srcH=(float)containerHeight;
            d.dstW=(float)outW;           d.dstH=(float)outH;
        }
    } else {
        // Upscale modes 3-5 need the game to render below display; mode 6 (sharpen)
        // runs at any resolution (it is a pure sharpen / cheap scale+sharpen).
        int mode=upscalerMode;
        if (mode>=3 && (mode==6 || renderBelowDisplay)) {
            int outX,outY,outW,outH;
            if (mode==4) {                               // fsr (fill / stretch)
                outX=0; outY=0; outW=(int)outExt.width; outH=(int)outExt.height;
            } else {                                     // sgsr(3/8) / fsr_fit(5) / sharpen(6) / nis(7)
                fitRect(outX,outY,outW,outH);
            }
            bool ok = (outW>0 && outH>0) && ensureOffscreen(containerWidth,containerHeight);
            if (ok && (mode==4||mode==5)) ok = ensureMid(outW,outH);
            if (ok) {
                float nx0=(float)outX/scW*2.f-1.f, ny0=(float)outY/scH*2.f-1.f;
                float nx1=(float)(outX+outW)/scW*2.f-1.f, ny1=(float)(outY+outH)/scH*2.f-1.f;
                upFrame.active=true; upFrame.mode=mode; scaling=true;
                upFrame.outX=outX; upFrame.outY=outY; upFrame.outW=outW; upFrame.outH=outH;

                if (mode==3 || mode==8) {                // sgsr / sgsr quality share the layout
                    SgsrPushConstants& p=upFrame.sgsrPC;
                    p.ndc[0]=nx0; p.ndc[1]=ny0; p.ndc[2]=nx1; p.ndc[3]=ny1;
                    p.viewportInfo[0]=1.f/(float)containerWidth;
                    p.viewportInfo[1]=1.f/(float)containerHeight;
                    p.viewportInfo[2]=(float)containerWidth;
                    p.viewportInfo[3]=(float)containerHeight;
                    // SGSR EdgeSharpness from the slider, span DOUBLED: 0.5 (neutral, slider 0)
                    // .. 4.5 (slider 100). The spatial reconstruction runs regardless of edge.
                    p.edgeSharpness = 0.5f + upscaleSharpness01 * 4.0f;
                } else if (mode==6) {
                    // Sharpen-only: RCAS samples the offscreen (game res) 1:1; the quad maps
                    // [0,1] across the fit rect, so outputSize = game res keeps texelFetch in
                    // bounds (1:1 at native, nearest scale+sharpen below native).
                    RcasPushConstants& r=upFrame.rcasPC;
                    r.ndc[0]=nx0; r.ndc[1]=ny0; r.ndc[2]=nx1; r.ndc[3]=ny1;
                    fsrRcasCon(r.con, upscaleSharpness01);
                    r.outW=(float)containerWidth; r.outH=(float)containerHeight;
                } else if (mode==7) {
                    // NIS NVScaler: single pass, samples the offscreen (game res) and
                    // upscales into the fit rect. ViewportInfo mirrors SGSR (xy=1/inSize,
                    // zw=inSize); the slider drives NIS sharpness [0..1].
                    NisPushConstants& p=upFrame.nisPC;
                    p.ndc[0]=nx0; p.ndc[1]=ny0; p.ndc[2]=nx1; p.ndc[3]=ny1;
                    p.viewportInfo[0]=1.f/(float)containerWidth;
                    p.viewportInfo[1]=1.f/(float)containerHeight;
                    p.viewportInfo[2]=(float)containerWidth;
                    p.viewportInfo[3]=(float)containerHeight;
                    p.sharpness = upscaleSharpness01;
                } else {                                     // fsr (4) / fsr_fit (5)
                    EasuPushConstants& e=upFrame.easuPC;
                    e.ndc[0]=-1.f; e.ndc[1]=-1.f; e.ndc[2]=1.f; e.ndc[3]=1.f; // full mid target
                    fsrEasuCon(e.con0,e.con1,e.con2,e.con3,
                               (float)containerWidth,(float)containerHeight,
                               (float)containerWidth,(float)containerHeight,
                               (float)outW,(float)outH);
                    e.outW=(float)outW; e.outH=(float)outH;
                    RcasPushConstants& r=upFrame.rcasPC;
                    r.ndc[0]=nx0; r.ndc[1]=ny0; r.ndc[2]=nx1; r.ndc[3]=ny1;
                    fsrRcasCon(r.con, upscaleSharpness01);
                    r.outW=(float)outW; r.outH=(float)outH;
                }
            }
        }
    }

    // ---- Effects-only path: no scaling mode engaged, but CAS/HDR are on. Composite
    //      at output (swapchain) resolution and run the effect chain (mirror mode 6's
    //      "any resolution" handling). ----
    if (!scaling) {
        if (!fxOn) return;                       // nothing to do -> direct path
        upFrame.active=true; upFrame.mode=0;
        upFrame.outX=0; upFrame.outY=0;
        upFrame.outW=(int)outExt.width; upFrame.outH=(int)outExt.height;
    }

    // ---- Ensure the effect-chain intermediates and pack each effect's push const.
    if (fxOn && upFrame.active) {
        const int fw=(int)outExt.width, fh=(int)outExt.height;
        const int nEffects=(int)fxaaEnabled+(int)toonEnabled+(int)colorEnabled+
                           (int)casOn+(int)hdrEnabled+(int)ntscEnabled+(int)crtEnabled+
                           (int)debandEnabled;
        bool ok = ensureFx1(fw,fh) && (nEffects<2 || ensureFx2(fw,fh));
        if (!ok) {
            // Out of memory for the fx targets: skip effects this frame. Keep the
            // scaling pass if there was one; otherwise fall back to the direct path.
            upFrame.cas=upFrame.hdr=upFrame.fxaa=upFrame.toon=
                upFrame.color=upFrame.ntsc=upFrame.crt=upFrame.deband=false;
            if (!scaling) upFrame.active=false;
            return;
        }
        // Every effect's full-target quad spans the whole swapchain (NDC -1..1).
        const float full[4]={-1.f,-1.f,1.f,1.f};
        if (fxaaEnabled) {
            FxaaPushConstants& f=upFrame.fxaaPC;
            f.ndc[0]=full[0]; f.ndc[1]=full[1]; f.ndc[2]=full[2]; f.ndc[3]=full[3];
            f.resolution[0]=(float)fw; f.resolution[1]=(float)fh;
        }
        if (toonEnabled) {
            ToonPushConstants& t=upFrame.toonPC;
            t.ndc[0]=full[0]; t.ndc[1]=full[1]; t.ndc[2]=full[2]; t.ndc[3]=full[3];
            t.resolution[0]=(float)fw; t.resolution[1]=(float)fh;
        }
        if (colorEnabled) {
            ColorPushConstants& c=upFrame.colorPC;
            c.ndc[0]=full[0]; c.ndc[1]=full[1]; c.ndc[2]=full[2]; c.ndc[3]=full[3];
            c.brightness=colorBrightness; c.contrast=colorContrast; c.gamma=colorGamma;
            c.saturation=colorSaturation;
        }
        if (casOn) {
            CasPushConstants& c=upFrame.casPC;
            c.ndc[0]=full[0]; c.ndc[1]=full[1]; c.ndc[2]=full[2]; c.ndc[3]=full[3];
            c.resolution[0]=(float)fw; c.resolution[1]=(float)fh;
            int s = casSharpness; if (s<0) s=0; if (s>100) s=100;
            c.sharpness = (float)s / 100.0f;     // higher slider = stronger sharpen
        }
        if (hdrEnabled) {
            HdrPushConstants& h=upFrame.hdrPC;
            h.ndc[0]=full[0]; h.ndc[1]=full[1]; h.ndc[2]=full[2]; h.ndc[3]=full[3];
            h.resolution[0]=(float)fw; h.resolution[1]=(float)fh;
        }
        if (ntscEnabled) {
            NtscPushConstants& nt=upFrame.ntscPC;
            nt.ndc[0]=full[0]; nt.ndc[1]=full[1]; nt.ndc[2]=full[2]; nt.ndc[3]=full[3];
            nt.resolution[0]=(float)fw; nt.resolution[1]=(float)fh;
            // cos/sin(chromaPhase*0.5) has integer-FrameCount period 4 -> mod 4 keeps
            // the analog shimmer byte-identical to an unbounded counter, with no float
            // precision loss. Advance only when NTSC is actually running.
            nt.frameCount = (float)(ntscFrameCounter & 3u);
            ntscFrameCounter++;
        }
        if (crtEnabled) {
            CrtPushConstants& cr=upFrame.crtPC;
            cr.ndc[0]=full[0]; cr.ndc[1]=full[1]; cr.ndc[2]=full[2]; cr.ndc[3]=full[3];
            cr.resolution[0]=(float)fw; cr.resolution[1]=(float)fh;
        }
        if (debandEnabled) {
            DebandPushConstants& d=upFrame.debandPC;
            d.ndc[0]=full[0]; d.ndc[1]=full[1]; d.ndc[2]=full[2]; d.ndc[3]=full[3];
            d.resolution[0]=(float)fw; d.resolution[1]=(float)fh;
            int s = debandStrength; if (s<0) s=0; if (s>200) s=200;
            d.strength = (float)s / 100.0f;      // slider 100 -> 1.0 LSB (default)
        }
    }
}

void VulkanRendererContext::renderLoop() {

    while (isRunning) {
        { std::unique_lock<std::mutex> lk(dirtyMutex);
          dirtyCV.wait(lk,[this]{
              return !isRunning||(!surfaceDetached.load()&&(needsRender.load()||fbResized.load()))||cursorMoved.load(); }); }
        if (!isRunning) break;

        if (swapchain == VK_NULL_HANDLE || cmdBufs.empty()) continue;
        try { renderFrame(); } catch(...) {}
    }
}

void VulkanRendererContext::flushDeleteQueue() {


    std::lock_guard<std::mutex> lk(renderMutex);
    if (deleteQueue.empty()) return;
    vk_.DeviceWaitIdle(device);
    for (auto& wt:deleteQueue) {
        if (wt.ds  !=VK_NULL_HANDLE) vk_.FreeDescriptorSets(device,winTexPool,1,&wt.ds);
        if (wt.view!=VK_NULL_HANDLE) vk_.DestroyImageView(device,wt.view,nullptr);
        if (wt.img !=VK_NULL_HANDLE) vk_.DestroyImage(device,wt.img,nullptr);
        if (wt.mem !=VK_NULL_HANDLE) vk_.FreeMemory(device,wt.mem,nullptr);
        if (wt.stg !=VK_NULL_HANDLE){vk_.DestroyBuffer(device,wt.stg,nullptr);vk_.FreeMemory(device,wt.stgMem,nullptr);}
    }
    deleteQueue.clear();
}

void VulkanRendererContext::renderFrame() {
    std::shared_lock<std::shared_mutex> frameLock(frameMutex);

    needsRender.store(false,std::memory_order_relaxed);
    cursorMoved.store(false,std::memory_order_relaxed);

    if (surfaceDetached.load(std::memory_order_acquire)) return;
    if (scanoutActive.load()) {
        applyScanoutBuffer();

        if (!scanoutBlackFrameDone.load()) {
            scanoutBlackFrameDone.store(true);

            std::lock_guard<std::mutex> lk(renderMutex);
            renderList.clear();
        } else {
            return;
        }
    } else {
        scanoutBlackFrameDone.store(false);
    }
    if (surfaceWidth==0||surfaceHeight==0) return;

    if (fbResized.load()) {
        // ERL bug report #9: on timeout, do NOT proceed to cleanupSwapchain() (which would
        // destroy resources possibly still in flight) - leave fbResized true and retry next frame.
        bool fencesOk = true;
        for (auto& f:inFlightFences) {
            if (vk_.WaitForFences(device,1,&f,VK_TRUE,2000000000ULL) != VK_SUCCESS) {
                RLOG_E("renderFrame: fence wait timed out during resize (2s) - deferring swapchain recreation");
                fencesOk = false;
                break;
            }
        }
        if (!fencesOk) return;
        cleanupSwapchain();
        bool ok=false;
        try{
            // Rebuild the acquire/present semaphores along with the swapchain.
            // A frame that acquired an image and then bailed out - which any
            // early return between AcquireNextImageKHR and QueueSubmit does,
            // and arming/disarming frame gen forces mid-flight - leaves its
            // image-available semaphore SIGNALLED with nothing left to wait on
            // it. Reusing such a semaphore in the next AcquireNextImageKHR is
            // invalid, and Adreno answers with VK_ERROR_OUT_OF_DATE_KHR, which
            // sets fbResized again: a swapchain-recreate loop that never
            // presents another frame. Recreating them is the only way to
            // guarantee no pending signal survives.
            recreateSyncObjects();
            createSwapchain();createFramebuffers();createCmdBufs();imgInFlight.assign(swapchainImages.size(),VK_NULL_HANDLE);
ok=true;}catch(...){}
        if (ok) fbResized.store(false);
        return;
    }

    if (cmdSlot(kMaxPresentsPerFrame - 1) >= cmdBufs.size() || cmdBufs[cmdSlot(0)] == VK_NULL_HANDLE) return;
    bool currentFenceWaited = false;
    if (!vk_.GetFenceStatus || vk_.GetFenceStatus(device, inFlightFences[currentFrame]) == VK_NOT_READY) {
        if (vk_.WaitForFences(device,1,&inFlightFences[currentFrame],VK_TRUE,2000000000ULL) != VK_SUCCESS) {
            RLOG_E("renderFrame: current-frame fence wait timed out (2s), skipping frame");
            return;
        }
        currentFenceWaited = true;
    }
    // This slot's previous frame is complete, so its chain timestamps are final.
    readFgQueryResult();

    // --- Frame gen: decide whether THIS frame composites off-swapchain. The
    // targets are created lazily on the first armed frame and torn down when it
    // disarms, so a session that never turns frame gen on never allocates them.
    if (fgArmed_.load(std::memory_order_relaxed) && fgCapsOk() && !scanoutActive.load()) {
        const int mult = fgMultiplier_.load(std::memory_order_relaxed);
        const uint32_t want = (uint32_t)std::min(std::max(mult, 2), 4) + 1u;
        // Experimental capture resolution: the ring (and so the whole chain)
        // can run below the panel and be blitted up on the way out.
        uint32_t ringW = 0, ringH = 0;
        compositeExtentFor(ringW, ringH);
        compositeArmed = ensureCompositeTargets(ringW, ringH, want);
        if (compositeArmed) createCursorOverlayRenderPass();
        if (compositeArmed && !compositeTargets.empty())
            compositeIndex = (compositeIndex + 1) % (uint32_t)compositeTargets.size();
    } else if (compositeArmed || !compositeTargets.empty()) {
        // Disarmed (or the swapchain went away): drop the ring so the direct
        // path is byte-identical to a session that never armed it.
        compositeArmed = false;
        vk_.DeviceWaitIdle(device);
        destroyCompositeTargets();
    }

    // --- Frame gen: decide how many frames to synthesise for this source
    // frame, BEFORE acquiring, since that sets how many images we need.
    fgPlan_ = FrameGenPlan{};
    const uint32_t fgCapacity = (uint32_t)std::min<size_t>(
        kMaxPresentsPerFrame - 1,
        compositeTargets.empty() ? 0 : compositeTargets.size() - 1);
    if (fgEngineKind_.load(std::memory_order_relaxed) == 1) {
        // win-fg: no cache, no governor, no pacer. Configure AFTER ensure so a
        // freshly built engine is prepared at the right preset straight away
        // instead of at the default and then rebuilt one frame later.
        if (compositeActive() && ensureWinFgEngine()) {
            if (fgConfigDirty_.exchange(false, std::memory_order_relaxed)) {
                winfgEngine_->configure(
                    (uint32_t)std::max(fgMultiplier_.load(std::memory_order_relaxed), 2),
                    fgModel_.load(std::memory_order_relaxed),
                    fgPerfPreset_.load(std::memory_order_relaxed),
                    fgFlowScale_.load(std::memory_order_relaxed));
            }
            if (winfgEngine_->prepare(compositeW, compositeH, swapchainFmt)) {
                fgPlan_.generations = winfgEngine_->plan(fgCapacity);
                ++fgSourceFrames_;
            }
        }
    } else {
        if (compositeActive() && ensureLsfgEngine()) {
            // Configure AFTER ensure, as win-fg does above. ensureLsfgEngine()
            // marks the config dirty when it creates the engine, but this used
            // to run before it, while lsfgEngine_ was still null - so a fresh
            // engine was prepared at its default flow scale (1.00), and the
            // user's scale arrived one frame later and forced a second chain
            // build. A dirty config seen while disarmed now simply waits for
            // the next armed frame; nothing reads it in between.
            if (fgConfigDirty_.exchange(false, std::memory_order_relaxed)) {
                lsfgEngine_->configure(
                    (uint32_t)std::max(fgMultiplier_.load(std::memory_order_relaxed), 2), 0,
                    fgFlowScale_.load(std::memory_order_relaxed),
                    fgRefreshHz_.load(std::memory_order_relaxed));
            }
            // Tell the engine how large the GUEST actually renders BEFORE asking
            // it to build anything. The flow pyramid's resolution is derived from
            // that ratio, so preparing first builds the most expensive chain
            // there is - the pyramid at full composite resolution - and then
            // throws all 25 pipelines away one frame later, when the guest extent
            // arrives and the scale changes. Both calls were already here; only
            // the order was wrong, and it cost an entire second chain build every
            // time frame generation armed.
            //
            // Device log 2026-09-09, arming at 1080p on a 720p game:
            //   16:49:51.400  chain built ... flow 1920x1080 scale 1.00 (guest 0x0)
            //   16:49:53.769  chain built ... flow 1344x756  scale 0.70 (guest 1280x720)
            // ~2.4 s apiece, and the game visibly froze for about five seconds on
            // the toggle. containerWidth/Height are set in the constructor, so a
            // real guest extent is always available here - no first frame
            // legitimately needs the 0x0 path.
            if (containerWidth > 0 && containerHeight > 0)
                lsfgEngine_->setGuestExtent((uint32_t)containerWidth, (uint32_t)containerHeight);
            if (lsfgEngine_->prepare(compositeW, compositeH, swapchainFmt)) {
                // The governor judges whether an extra generated frame paid off,
                // so it must be given the rate that actually reaches the PANEL,
                // not the guest rate wearing a different name.
                lsfgEngine_->setPresentedRate(fgPresentedRate_);
                fgPlan_.generations = lsfgEngine_->plan(fgCapacity, ++fgSourceFrames_);
            }
        }
    }   // lsfg
    fgPlan_.presents = fgPlan_.generations + 1;

    // ERL bug report #9: real timeout so VK_TIMEOUT is reachable; existing non-success guard below returns on it.
    for (uint32_t k = 0; k < fgPlan_.presents; k++) {
        uint32_t idx = 0;
        VkResult ar = vk_.AcquireNextImageKHR(device,swapchain,2000000000ULL,
                                              imgAvailSems[syncSlot(k)],VK_NULL_HANDLE,&idx);
        if (ar==VK_ERROR_OUT_OF_DATE_KHR||ar==VK_ERROR_SURFACE_LOST_KHR){
            // Logged deliberately: when this fires every frame it IS the
            // swapchain-recreate loop, and it used to be invisible.
            if ((fgAcquireFailLog_++ % 60u) == 0u)
                RLOG_E("renderFrame: acquire %u/%u -> %s (recreating swapchain)",
                       k, fgPlan_.presents,
                       ar==VK_ERROR_OUT_OF_DATE_KHR ? "OUT_OF_DATE" : "SURFACE_LOST");
            fbResized.store(true);
            return;
        }
        if (ar!=VK_SUCCESS&&ar!=VK_SUBOPTIMAL_KHR) {
            // Could not get every image we planned for. Anything already
            // acquired has a semaphore nobody will wait on, so the only safe
            // move is to drop this frame entirely rather than leak a signal.
            if (k == 0) return;
            RLOG_E("renderFrame: acquire %u/%u failed (res=%d) - dropping frame",
                   k, fgPlan_.presents, (int)ar);
            fbResized.store(true);
            return;
        }
        if (idx >= swapchainFBs.size() || idx >= swapchainImages.size()) {
            RLOG_E("renderFrame: invalid acquired image index=%u (fb=%zu images=%zu)",
                idx, swapchainFBs.size(), swapchainImages.size());
            // Everything acquired so far has a signalled image-available
            // semaphore that nothing will ever wait on. Leaving one behind is
            // the swapchain-recreate freeze, so ask for the rebuild that
            // recreates the semaphores rather than just dropping the frame.
            fbResized.store(true);
            return;
        }
        fgPlan_.imgIdx[k] = idx;
    }
    // Real frame N is presented LAST; generated frames take the earlier slots.
    const uint32_t imgIdx = fgPlan_.imgIdx[fgPlan_.presents - 1];
    VkResult res = VK_SUCCESS;

    if (imgInFlight.size()!=swapchainImages.size()) imgInFlight.assign(swapchainImages.size(),VK_NULL_HANDLE);
    for (uint32_t k = 0; k < fgPlan_.presents; k++) {
        const uint32_t idx = fgPlan_.imgIdx[k];
        if (imgInFlight[idx]!=VK_NULL_HANDLE &&
            (!currentFenceWaited || imgInFlight[idx] != inFlightFences[currentFrame])) {
            if (!vk_.GetFenceStatus || vk_.GetFenceStatus(device, imgInFlight[idx]) == VK_NOT_READY) {
                if (vk_.WaitForFences(device,1,&imgInFlight[idx],VK_TRUE,2000000000ULL) != VK_SUCCESS) {
                    RLOG_E("renderFrame: in-flight image fence wait timed out (2s), skipping frame");
                    // Same reasoning as the acquire failures above: this frame
                    // has already acquired every image it planned to present,
                    // and returning here strands their semaphores signalled.
                    fbResized.store(true);
                    return;
                }
            }
        }
        imgInFlight[idx]=inFlightFences[currentFrame];
    }

    for (uint32_t k = 0; k < fgPlan_.presents; k++) vk_.ResetCommandBuffer(cmdBufs[cmdSlot(k)],0);

    float ox,oy,sx,sy,cw,ch;
    short ptrX,ptrY,curHotX,curHotY,curW,curH; bool curVis;
    VkBuffer curUpload=VK_NULL_HANDLE; bool hasCurUpload=false;

    {
        std::lock_guard<std::mutex> lk(renderMutex);


        if (!deleteQueue.empty()) {
            for (auto& wt:deleteQueue) {
                if (wt.ds  !=VK_NULL_HANDLE) vk_.FreeDescriptorSets(device,winTexPool,1,&wt.ds);
                if (wt.view!=VK_NULL_HANDLE) vk_.DestroyImageView(device,wt.view,nullptr);
                if (wt.img !=VK_NULL_HANDLE) vk_.DestroyImage(device,wt.img,nullptr);
                if (wt.mem !=VK_NULL_HANDLE) vk_.FreeMemory(device,wt.mem,nullptr);
                if (wt.stg !=VK_NULL_HANDLE){vk_.DestroyBuffer(device,wt.stg,nullptr);vk_.FreeMemory(device,wt.stgMem,nullptr);}
            }
            deleteQueue.clear();
        }

        ox=sceneOffsetX; oy=sceneOffsetY; sx=sceneScaleX; sy=sceneScaleY;
        cw=(float)containerWidth; ch=(float)containerHeight;
        ptrX=(short)pointerX.load(); ptrY=(short)pointerY.load();
        curHotX=cursorHotX; curHotY=cursorHotY; curW=cursorTexW; curH=cursorTexH;
        curVis=cursorVisible.load();

        frameDraws.clear();
        for (auto& re:renderList) {
            auto it=texMap.find(re.id);
            if (it==texMap.end()) continue;
            WinTex& wt=it->second;
            if (wt.ds==VK_NULL_HANDLE) continue;
            DrawEntry de{wt.img,wt.ds,VK_NULL_HANDLE,re.x,re.y,wt.w,wt.h};
            de.isAHB=wt.isAHB;
            if (wt.needsTransition) { de.needsTransition=true; wt.needsTransition=false; }
            if (wt.dirty && !wt.isAHB && wt.stg!=VK_NULL_HANDLE) {
                de.upload=wt.stg;
                wt.dirty=false;
            } else if (wt.isAHB) {
                wt.dirty=false;
            }
            frameDraws.push_back(de);
        }

        if (isCursorImageDirty.load() && cursorImg!=VK_NULL_HANDLE && !cursorPixels.empty()) {
            VkDeviceSize csz=(VkDeviceSize)cursorTexW*cursorTexH*4;
            ensureCursorStaging(csz);
            isCursorImageDirty.store(false); hasCurUpload=true; curUpload=cursorStg;

            cursorUploadSize = csz;
        }
    }


    if (hasCurUpload && cursorStgP && !cursorPixels.empty())
        memcpy(cursorStgP, cursorPixels.data(), cursorUploadSize);

    bool effectiveCurVis = curVis && !scanoutActive.load();

    planUpscaleFrame();   // decide/prepare spatial-upscaler passes for this frame
    recordCmdBuf(cmdBufs[cmdSlot(0)],imgIdx,frameDraws,
        frameAhbTransitions,framePreUpload,framePostUpload,
        curUpload,hasCurUpload,
        ox,oy,sx,sy,cw,ch,ptrX,ptrY,curHotX,curHotY,curW,curH,effectiveCurVis);

    // Each pending present gets its own submit. cmdBufs[cmdSlot(0)] (recorded
    // above) carries the composite, the chain's shared passes and generated
    // frame 0; every later generated frame and finally the real frame are
    // recorded and submitted here one at a time, each presented the moment its
    // submit is queued. With one submit for everything, nothing reached the
    // presentation engine until the chain had finished for EVERY generated
    // frame; now the first one is on its way while the GPU is still working on
    // the next. FIFO still spaces them onto consecutive vblanks.
    //
    // On the composite path the first thing to touch an acquired swapchain
    // image is the copy, a TRANSFER write - and TRANSFER precedes
    // COLOR_ATTACHMENT_OUTPUT in pipeline order, so waiting only on the latter
    // would let the copy run before the presentation engine released the image.
    const VkPipelineStageFlags waitStage = compositeActive()
        ? (VkPipelineStageFlags)(VK_PIPELINE_STAGE_TRANSFER_BIT|VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        : (VkPipelineStageFlags)VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    VkSwapchainKHR scs[]={swapchain};

    // The slot's fence rides on the LAST submit. A fence signal is ordered
    // after every command submitted earlier on the same queue, so it still
    // covers all of this frame's work.
    vk_.ResetFences(device,1,&inFlightFences[currentFrame]);
    uint32_t presented = 0;
    bool fenceSubmitted = false;
    // Generated frames belong between N-1 and N, so they go out first and the
    // real frame is presented last.
    for (uint32_t k = 0; k < fgPlan_.presents; k++) {
        VkCommandBuffer cb = cmdBufs[cmdSlot(k)];
        if (k > 0) {
            VkCommandBufferBeginInfo bi{}; bi.sType=VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
            if (vk_.BeginCommandBuffer(cb,&bi)!=VK_SUCCESS) { fbResized.store(true); break; }
            // Order this buffer after everything already submitted for this
            // frame: the chain's shared passes and the earlier generations.
            // Barriers span command buffers on one queue, so this is enough.
            VkMemoryBarrier mb{}; mb.sType=VK_STRUCTURE_TYPE_MEMORY_BARRIER;
            mb.srcAccessMask=VK_ACCESS_MEMORY_WRITE_BIT;
            mb.dstAccessMask=VK_ACCESS_MEMORY_READ_BIT|VK_ACCESS_MEMORY_WRITE_BIT;
            vk_.CmdPipelineBarrier(cb,
                VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT|VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT|VK_PIPELINE_STAGE_TRANSFER_BIT,
                0, 1,&mb, 0,nullptr, 0,nullptr);
            if (k < fgPlan_.generations) recordFrameGenGeneration(cb, k);
            else                          copyCompositeToSwapchain(cb, imgIdx);
            if (vk_.EndCommandBuffer(cb)!=VK_SUCCESS) {
                RLOG_E("renderFrame: EndCommandBuffer failed for present %u/%u", k, fgPlan_.presents);
                fbResized.store(true);
                break;
            }
        }
        const bool last = (k + 1 == fgPlan_.presents);
        VkSemaphore wSem = imgAvailSems[syncSlot(k)];
        VkSemaphore sSem = renderDoneSems[syncSlot(k)];
        VkSubmitInfo si{}; si.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO;
        si.waitSemaphoreCount=1; si.pWaitSemaphores=&wSem; si.pWaitDstStageMask=&waitStage;
        si.commandBufferCount=1; si.pCommandBuffers=&cb;
        si.signalSemaphoreCount=1; si.pSignalSemaphores=&sSem;
        if (vk_.QueueSubmit(graphicsQueue,1,&si, last ? inFlightFences[currentFrame] : VK_NULL_HANDLE)!=VK_SUCCESS) {
            // The fence was reset above and may now never signal; replace it
            // with a signalled one so the next use of this slot does not wait
            // on it forever.
            vk_.DestroyFence(device,inFlightFences[currentFrame],nullptr);
            VkFenceCreateInfo fi{}; fi.sType=VK_STRUCTURE_TYPE_FENCE_CREATE_INFO; fi.flags=VK_FENCE_CREATE_SIGNALED_BIT;
            vk_.CreateFence(device,&fi,nullptr,&inFlightFences[currentFrame]);
            return;
        }
        fenceSubmitted = last;
        VkPresentInfoKHR pi{}; pi.sType=VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        pi.waitSemaphoreCount=1; pi.pWaitSemaphores=&sSem;
        pi.swapchainCount=1; pi.pSwapchains=scs; pi.pImageIndices=&fgPlan_.imgIdx[k];
        res=vk_.QueuePresentKHR(graphicsQueue,&pi);
        presented++;
        if (res==VK_ERROR_OUT_OF_DATE_KHR||res==VK_ERROR_SURFACE_LOST_KHR) {
            // The swapchain recreate this triggers also rebuilds the
            // semaphores, so the acquires we never waited on cannot strand a
            // signal into the next swapchain (recreateSyncObjects).
            fbResized.store(true);
            break;
        }
    }
    if (!fenceSubmitted) {
        // Left early before the last submit: an empty submit carrying the
        // fence is ordered after everything queued above, so the slot's fence
        // still means "this frame's work is done".
        VkSubmitInfo si{}; si.sType=VK_STRUCTURE_TYPE_SUBMIT_INFO;
        vk_.QueueSubmit(graphicsQueue,1,&si,inFlightFences[currentFrame]);
    }
    trackPresentedRate(presented);
    currentFrame=(currentFrame+1)%MAX_FRAMES_IN_FLIGHT;
}

void VulkanRendererContext::onSurfaceResized(int w, int h) {
    std::lock_guard<std::mutex> lk(renderMutex);
    if (w==0||h==0) return;
    surfaceWidth=w; surfaceHeight=h; scanout.setSurfaceSize(w, h); fbResized.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::detachSurface() {
    surfaceDetached.store(true, std::memory_order_release);
    dirtyCV.notify_all();

    { std::unique_lock<std::shared_mutex> frameLock(frameMutex); }

    vk_.DeviceWaitIdle(device);
    cleanupSwapchain();
    if (surface != VK_NULL_HANDLE) {
        vk_.DestroySurfaceKHR(instance, surface, nullptr);
        surface = VK_NULL_HANDLE;
    }
    if (window) {
        ANativeWindow_release(window);
        window = nullptr;
    }
}

bool VulkanRendererContext::reattachSurface(ANativeWindow* newWindow) {
    if (window) { ANativeWindow_release(window); window = nullptr; }
    window = newWindow;
    VkAndroidSurfaceCreateInfoKHR ci{};
    ci.sType  = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    ci.window = window;
    if (vk_.CreateAndroidSurfaceKHR(instance, &ci, nullptr, &surface) != VK_SUCCESS) {
        __android_log_print(ANDROID_LOG_ERROR, "Winlator_Renderer", "reattachSurface: CreateAndroidSurface failed");
        ANativeWindow_release(window); window = nullptr;
        return false;
    }
    {
        std::unique_lock<std::shared_mutex> frameLock(frameMutex);
        try {
            createSwapchain();
            createFramebuffers();
            createCmdBufs();
            imgInFlight.assign(swapchainImages.size(), VK_NULL_HANDLE);
        } catch (...) {
            __android_log_print(ANDROID_LOG_ERROR, "Winlator_Renderer", "reattachSurface: swapchain recreate failed");
            return false;
        }
        surfaceDetached.store(false, std::memory_order_release);
    }
    needsRender.store(true, std::memory_order_release);
    dirtyCV.notify_all();
    __android_log_print(ANDROID_LOG_DEBUG, "Winlator_Renderer", "reattachSurface: OK");
    return true;
}

void VulkanRendererContext::setTransform(float ox, float oy, float sx, float sy) {
    { std::lock_guard<std::mutex> lk(renderMutex); sceneOffsetX=ox;sceneOffsetY=oy;sceneScaleX=sx;sceneScaleY=sy; }
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setClipRegion(int x, int y, int w, int h) {
    // #413 game-region scissor (surface px), consumed by recordCmdBuf. Atomics only (no renderMutex) —
    // it composes with setTransform, which already kicks a render.
    clipRegionX.store(x, std::memory_order_relaxed);
    clipRegionY.store(y, std::memory_order_relaxed);
    clipRegionW.store(w, std::memory_order_relaxed);
    clipRegionH.store(h, std::memory_order_relaxed);
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updatePointerPosition(short x, short y) {
    pointerX.store(x); pointerY.store(y);
    if (cursorVisible.load()) { cursorMoved.store(true); dirtyCV.notify_one(); }
}

void VulkanRendererContext::setCursorVisible(bool v) {
    cursorVisible.store(v); cursorMoved.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updateCursorImage(void* px, short w, short h, short hotX, short hotY) {
    if (!px||w<=0||h<=0) return;
    std::lock_guard<std::mutex> lk(renderMutex);
    ensureCursorTex(w,h);
    cursorPixels.resize((size_t)w*h); memcpy(cursorPixels.data(),px,(size_t)w*h*4);
    cursorHotX=hotX; cursorHotY=hotY;
    isCursorImageDirty.store(true); needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updateWindowContent(int64_t id, void* px, short w, short h, short stride, int, int) {
    if (!px||w<=0||h<=0) return;

    void* mapped=nullptr;
    {
        std::lock_guard<std::mutex> lk(renderMutex);
        WinTex& wt=texMap[id];
        if (wt.img==VK_NULL_HANDLE || wt.w!=w || wt.h!=h) {
            if (wt.img!=VK_NULL_HANDLE) destroyWinTex(wt);
            if (!createWinTexResources(wt,w,h)) { texMap.erase(id); return; }
        }
        mapped=wt.mapped;
    }

    if (!mapped) return;
    const size_t dstPitch=(size_t)w*4;
    const int32_t srcStride=stride>0?stride:w;
    uint32_t* src2=static_cast<uint32_t*>(px);
    uint8_t*  dst2=static_cast<uint8_t*>(mapped);
    for (int row=0;row<h;++row)
        memcpy(dst2+(size_t)row*dstPitch,
               &src2[(size_t)row*srcStride],(size_t)w*4);
    {
        std::lock_guard<std::mutex> lk(renderMutex);
        auto it=texMap.find(id);
        if (it!=texMap.end()) it->second.dirty=true;
    }
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::updateWindowContentAHB(int64_t id, AHardwareBuffer* ahb, short, short, int, int) {
    if (!ahb) return;
    std::lock_guard<std::mutex> lk(renderMutex);





    auto cit = ahbImportCache.find(ahb);
    if (cit == ahbImportCache.end()) {
        WinTex tmp{};
        if (!importAHBToWinTex(tmp, ahb)) {
            RLOG_E("updateWindowContentAHB: import failed for id=%" PRId64, id);
            return;
        }
        AHardwareBuffer_acquire(ahb);
        ahbImportCache[ahb] = tmp;
        windowAhbs[id].push_back(ahb);
        cit = ahbImportCache.find(ahb);
        RLOG("updateWindowContentAHB: imported new AHB %p for id=%" PRId64 " (%dx%d)",
            (void*)ahb, id, tmp.w, tmp.h);

        // ERL bug report #9: a window that receives many distinct AHB pointers over its
        // lifetime (rather than cycling a small fixed swapchain pool) otherwise accumulates
        // one VkImage+VkDeviceMemory+descriptor set per import forever. Cap at 4 tracked
        // per window; evict the oldest via the existing deleteQueue deferred-destruction path
        // (same one used for whole-window teardown, drained under renderMutex each frame).
        // The just-imported AHB is newest (at back) so it is never the one evicted, keeping cit valid.
        auto& list = windowAhbs[id];
        constexpr size_t kMaxTrackedPerWindow = 4;
        while (list.size() > kMaxTrackedPerWindow) {
            AHardwareBuffer* stale = list.front();
            list.erase(list.begin());
            auto sit = ahbImportCache.find(stale);
            if (sit != ahbImportCache.end()) {
                WinTex deferred = sit->second;
                deferred.isAHB = false;
                deleteQueue.push_back(deferred);
                AHardwareBuffer_release(stale);
                ahbImportCache.erase(sit);
            }
        }
    }


    WinTex& src = cit->second;
    WinTex& wt  = texMap[id];
    wt.img  = src.img;
    wt.mem  = src.mem;
    wt.view = src.view;
    wt.ds   = src.ds;
    wt.isAHB = true;
    wt.ahb  = ahb;
    wt.w    = src.w;
    wt.h    = src.h;

    if (src.needsTransition) {
        wt.needsTransition  = true;
        src.needsTransition = false;
    }
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setRenderList(const int64_t* ids, const int* xs, const int* ys, int count) {
    std::lock_guard<std::mutex> lk(renderMutex);
    renderList.resize(count);
    for (int i=0;i<count;i++) renderList[i]={ids[i],xs[i],ys[i]};
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::removeWindow(int64_t id) {
    std::lock_guard<std::mutex> lk(renderMutex);



    auto it = texMap.find(id);
    if (it != texMap.end()) {
        if (!it->second.isAHB) destroyWinTex(it->second);
        else it->second = {};
        texMap.erase(it);
    }


    auto wit = windowAhbs.find(id);
    if (wit != windowAhbs.end()) {
        for (AHardwareBuffer* ahb : wit->second) {
            auto cit = ahbImportCache.find(ahb);
            if (cit != ahbImportCache.end()) {
                WinTex deferred = cit->second;
                deferred.isAHB  = false;
                deleteQueue.push_back(deferred);
                AHardwareBuffer_release(ahb);
                ahbImportCache.erase(cit);
            }
        }
        windowAhbs.erase(wit);
    }

    renderList.erase(std::remove_if(renderList.begin(),renderList.end(),
        [id](const RenderEntry& e){return e.id==id;}),renderList.end());
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::cleanupAllAHBCache() {
    for (auto& [ahb, wt] : ahbImportCache) {
        if (wt.ds   != VK_NULL_HANDLE) vk_.FreeDescriptorSets(device, winTexPool, 1, &wt.ds);
        if (wt.view != VK_NULL_HANDLE) vk_.DestroyImageView(device, wt.view, nullptr);
        if (wt.img  != VK_NULL_HANDLE) vk_.DestroyImage(device, wt.img, nullptr);
        if (wt.mem  != VK_NULL_HANDLE) vk_.FreeMemory(device, wt.mem, nullptr);
        AHardwareBuffer_release(ahb);
    }
    ahbImportCache.clear();
    windowAhbs.clear();
}


void VulkanRendererContext::dumpRendererInfo() {
    VkPhysicalDeviceProperties props{};
    vk_.GetPhysicalDeviceProperties(physicalDevice,&props);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "=== RENDERER INFO ===");
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "GPU: %s vendorID=0x%x driverVersion=0x%x apiVersion=%d.%d.%d",
        props.deviceName,props.vendorID,props.driverVersion,
        VK_VERSION_MAJOR(props.apiVersion),VK_VERSION_MINOR(props.apiVersion),VK_VERSION_PATCH(props.apiVersion));
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Swapchain: %dx%d fmt=%d",swapchainExt.width,swapchainExt.height,(int)swapchainFmt);
    std::string pmList;
    for(auto pm:availablePresentModes) pmList+=std::to_string((int)pm)+" ";
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "SupportedPresentModes: [%s] current=%d",pmList.c_str(),(int)requestedPresentMode);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Filter: mode=%d (%s)", filterMode, filterMode==2?(cubicSupported?"CUBIC":"LINEAR"):filterMode==1?"NEAREST":"LINEAR");
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Scanout: active=%d gameFrameDelivered=%d scanoutGameSC=%p",
        (int)scanoutActive.load(),(int)gameFrameDelivered.load(),scanout.debugGameSC());
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,
        "Surface: %dx%d container: %dx%d",
        surfaceWidth,surfaceHeight,containerWidth,containerHeight);
    __android_log_print(ANDROID_LOG_DEBUG,WLOG_TAG,"=== END RENDERER INFO ===");
}

void VulkanRendererContext::setFilterMode(int mode) {
    RLOG("setFilterMode: %d -> %d (%s->%s)", filterMode, mode,
        filterMode==2?(cubicSupported?"CUBIC":"LINEAR"):filterMode==1?"NEAREST":"LINEAR", mode==2?(cubicSupported?"CUBIC":"LINEAR"):mode==1?"NEAREST":"LINEAR");
    if (filterMode==mode) { RLOG("setFilterMode: already set, skipping"); return; }
    filterMode=mode;
    vk_.DeviceWaitIdle(device);
    if (sampler!=VK_NULL_HANDLE){vk_.DestroySampler(device,sampler,nullptr);sampler=VK_NULL_HANDLE;}
    createSampler();
    auto updateDS=[&](VkDescriptorSet ds, VkImageView view){
        if(ds==VK_NULL_HANDLE||view==VK_NULL_HANDLE) return;
        VkDescriptorImageInfo dii{}; dii.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        dii.imageView=view; dii.sampler=sampler;
        VkWriteDescriptorSet wr{}; wr.sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        wr.dstSet=ds; wr.dstBinding=0; wr.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        wr.descriptorCount=1; wr.pImageInfo=&dii;
        vk_.UpdateDescriptorSets(device,1,&wr,0,nullptr);
    };
    
    for (auto& [id,wt]:texMap) updateDS(wt.ds, wt.view);

    for (auto& [ahb,wt]:ahbImportCache) updateDS(wt.ds, wt.view);
    if (cursorDS!=VK_NULL_HANDLE&&cursorView!=VK_NULL_HANDLE) updateDS(cursorDS, cursorView);
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setUpscaler(int mode) {
    if (mode<0||mode>8) mode=0;   // 0=none 1=linear 2=nearest 3=sgsr 4=fsr 5=fsr_fit 6=sharpen 7=nis 8=sgsr_quality
    if (upscalerMode==mode) { RLOG("setUpscaler: already %d, skipping", mode); return; }
    RLOG("setUpscaler: %d -> %d", upscalerMode, mode);
    upscalerMode=mode;
    // Modes 1/2 are "no shader upscaler"; they just drive the base sampler filter
    // (linear/nearest) exactly like setFilterMode. Modes 3-5 engage the shader
    // post-passes and sample the offscreen target through upscaleSampler instead.
    if      (mode==1) setFilterMode(0);   // linear
    else if (mode==2) setFilterMode(1);   // nearest
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setHqDownscale(bool enabled) {
    if (hqDownscale==enabled) { RLOG("setHqDownscale: already %d, skipping", (int)enabled); return; }
    RLOG("setHqDownscale: %d -> %d", (int)hqDownscale, (int)enabled);
    hqDownscale=enabled;
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setCas(bool enabled, int sharpness) {
    if (sharpness<0) sharpness=0; if (sharpness>100) sharpness=100;
    if (casEnabled==enabled && casSharpness==sharpness) return;
    RLOG("setCas: %d/%d -> %d/%d", (int)casEnabled, casSharpness, (int)enabled, sharpness);
    casEnabled=enabled; casSharpness=sharpness;
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setHdr(bool enabled) {
    if (hdrEnabled==enabled) return;
    RLOG("setHdr: %d -> %d", (int)hdrEnabled, (int)enabled);
    hdrEnabled=enabled;
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setDeband(bool enabled, int strength) {
    if (strength<0) strength=0; if (strength>200) strength=200;
    if (debandEnabled==enabled && debandStrength==strength) return;
    RLOG("setDeband: %d/%d -> %d/%d", (int)debandEnabled, debandStrength, (int)enabled, strength);
    debandEnabled=enabled; debandStrength=strength;
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setFxaa(bool enabled) {
    if (fxaaEnabled==enabled) return;
    RLOG("setFxaa: %d -> %d", (int)fxaaEnabled, (int)enabled);
    fxaaEnabled=enabled;
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setToon(bool enabled) {
    if (toonEnabled==enabled) return;
    RLOG("setToon: %d -> %d", (int)toonEnabled, (int)enabled);
    toonEnabled=enabled;
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setCrt(bool enabled) {
    if (crtEnabled==enabled) return;
    RLOG("setCrt: %d -> %d", (int)crtEnabled, (int)enabled);
    crtEnabled=enabled;
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setNtsc(bool enabled) {
    if (ntscEnabled==enabled) return;
    RLOG("setNtsc: %d -> %d", (int)ntscEnabled, (int)enabled);
    ntscEnabled=enabled;
    needsRender.store(true); dirtyCV.notify_one();
}

// Color grade: inputs are GL slider values (brightness/contrast -100..100, gamma
// 0.5..3.0, saturation 0..200 percent). Replicate ColorEffect: map slider/100 then apply
// ColorEffectMaterial.use() clamps; "enabled" only when not at the neutral (0,0,1,100)
// grade (GL removes it then) — saturation is part of that neutral test, so a Look that
// only moves saturation still switches the Color pass on.
void VulkanRendererContext::setColorGrade(float brightness, float contrast, float gamma, float saturation) {
    bool enabled = !(brightness==0.0f && contrast==0.0f && gamma==1.0f && saturation==100.0f);
    float b = brightness / 100.0f; if (b<-1.0f) b=-1.0f; if (b>1.0f) b=1.0f;
    float c = contrast   / 100.0f; if (c< 0.0f) c= 0.0f; if (c>2.0f) c=2.0f;
    float s = saturation / 100.0f; if (s< 0.0f) s= 0.0f; if (s>2.0f) s=2.0f;
    float g = gamma;               if (g< 0.1f) g= 0.1f; if (g>5.0f) g=5.0f;
    if (colorEnabled==enabled && colorBrightness==b && colorContrast==c && colorGamma==g &&
        colorSaturation==s) return;
    RLOG("setColorGrade: en=%d b=%.3f c=%.3f g=%.3f s=%.3f", (int)enabled, b, c, g, s);
    colorEnabled=enabled; colorBrightness=b; colorContrast=c; colorGamma=g; colorSaturation=s;
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setUpscaleSharpness(int sharpness) {
    if (sharpness<0) sharpness=0; if (sharpness>100) sharpness=100;
    // Slider 0..100 -> linear sharpness 0..1: 0 = neutral (RCAS lobe scale 0 = passthrough;
    // SGSR edge at its 0.5 floor; the spatial upscale still runs), 100 = max (full RCAS,
    // SGSR edge 4.5). Continuous for SGSR/FSR/Sharpen.
    float s01 = (float)sharpness / 100.0f;
    if (upscaleSharpness01==s01) return;
    RLOG("setUpscaleSharpness: slider=%d -> %.3f", sharpness, s01);
    upscaleSharpness01=s01;
    needsRender.store(true); dirtyCV.notify_one();
}

void VulkanRendererContext::setSwapRB(bool enabled) {
    if (swapRB == enabled) return;
    swapRB = enabled;
    RLOG("setSwapRB: %d", (int)swapRB);


}

void VulkanRendererContext::setPresentMode(VkPresentModeKHR mode) {
    bool supported = false;
    for (auto pm : availablePresentModes) if (pm == mode) { supported = true; break; }
    VkPresentModeKHR target = supported ? mode : VK_PRESENT_MODE_FIFO_KHR;
    RLOG("setPresentMode: requested=%d supported=%d -> applying=%d",
        (int)mode, (int)supported, (int)target);
    if (requestedPresentMode==target) { RLOG("setPresentMode: already set, skipping"); return; }
    requestedPresentMode=target;
    fbResized.store(true); dirtyCV.notify_one();
}

std::vector<int> VulkanRendererContext::getSupportedPresentModes() const {
    std::vector<int> out;
    for (auto pm:availablePresentModes) out.push_back((int)pm);
    return out;
}

#pragma GCC diagnostic pop
