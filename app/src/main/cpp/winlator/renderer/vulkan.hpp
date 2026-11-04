#pragma once

#include <renderer_jni.hpp>

struct VulkanTable {
    PFN_vkGetFenceFdKHR GetFenceFdKHR;
    // Extension entry point: libvulkan does not export it, so it is fetched from the device.
    PFN_vkGetAndroidHardwareBufferPropertiesANDROID GetAndroidHardwareBufferPropertiesANDROID;
};