#pragma once

#include <memory>

#include "renderer_jni.hpp"

struct GLTexture {
    int id;
    bool isDirty;
    EGLImageKHR eglImage;
    bool sizeChanged;
};

struct Drawable {
    int id = 0;
    int width = 0;
    int format = 0;
    int height = 0;
    int stride = 0;
    std::unique_ptr<GLTexture> glTexture;
    bool isDirectContent = false;
    void *data = nullptr;
    jobject drawableObj = nullptr;
    // Always owns one reference of its own, so the buffer outlives the Java GPUImage that created it.
    AHardwareBuffer *ahb = nullptr;
    int sync_fence = -1;

    Drawable() {}
    Drawable(const Drawable &) = delete;
    Drawable &operator=(const Drawable &) = delete;

    ~Drawable() {
        if (ahb != nullptr) AHardwareBuffer_release(ahb);
    }

    // Takes a new reference on buffer and drops the previous one.
    void setAhb(AHardwareBuffer *buffer) {
        if (buffer != nullptr) AHardwareBuffer_acquire(buffer);
        adoptAhb(buffer);
    }

    // Takes over a reference the caller already holds.
    void adoptAhb(AHardwareBuffer *buffer) {
        if (ahb != nullptr && ahb != buffer) AHardwareBuffer_release(ahb);
        else if (ahb != nullptr) AHardwareBuffer_release(buffer);
        ahb = buffer;
        if (ahb != nullptr) {
            AHardwareBuffer_Desc desc{};
            AHardwareBuffer_describe(ahb, &desc);
            stride = desc.stride;
            format = desc.format;
        }
    }
};
