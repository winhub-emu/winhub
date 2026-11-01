// Bannerlator: the native Xbox Cloud Gaming player's bridge to the Vulkan compositor. The stream is one
// full-screen "window" (id 1) fed with decoded frames as AHardwareBuffers (ImageReader images), so the
// compositor's effects and native frame generation (Win-FG / LSFG) apply to it as to a game. System Vulkan
// driver (no adrenotools), no X server.
#include <jni.h>
#include <android/hardware_buffer_jni.h>
#include <android/native_window_jni.h>
#include "VulkanRendererContext.h"

namespace {
constexpr int64_t kStreamWindow = 1;
VulkanRendererContext* ctx(jlong h) { return reinterpret_cast<VulkanRendererContext*>(h); }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeCreate(JNIEnv* env, jobject, jobject surface, jint videoW, jint videoH) {
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);
    if (!win) return 0;
    try {
        auto* r = new VulkanRendererContext(win, videoW, videoH, nullptr, false);
        int64_t id = kStreamWindow; int x = 0, y = 0;
        r->setRenderList(&id, &x, &y, 1);
        return reinterpret_cast<jlong>(r);
    } catch (...) {
        ANativeWindow_release(win);
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeResize(JNIEnv*, jobject, jlong h, jint w, jint ht) {
    if (auto* r = ctx(h)) r->onSurfaceResized(w, ht);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeSetTransform(JNIEnv*, jobject, jlong h, jfloat ox, jfloat oy, jfloat sx, jfloat sy) {
    if (auto* r = ctx(h)) r->setTransform(ox, oy, sx, sy);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeFrame(JNIEnv* env, jobject, jlong h, jobject hardwareBuffer, jint w, jint ht) {
    auto* r = ctx(h);
    if (!r || !hardwareBuffer) return;
    AHardwareBuffer* ahb = AHardwareBuffer_fromHardwareBuffer(env, hardwareBuffer);
    if (ahb) r->updateWindowContentAHB(kStreamWindow, ahb, (short) w, (short) ht, 0, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeFrameGen(JNIEnv* env, jobject, jlong h, jint engine, jint multiplier,
        jfloat flowScale, jfloat refreshHz, jint model, jint preset, jstring lsfgCache) {
    auto* r = ctx(h);
    if (!r) return;
    if (lsfgCache) {
        const char* path = env->GetStringUTFChars(lsfgCache, nullptr);
        r->setLsfgCachePath(path);
        env->ReleaseStringUTFChars(lsfgCache, path);
    }
    r->setFrameGenEngine(engine);
    if (engine == 1) r->setWinFgTuning(model, preset);
    r->setFrameGenTuning(flowScale, refreshHz, 0);
    r->setFrameGenArmed(multiplier >= 2, multiplier);
}

// ImageReader frames are RGBA; X-server windows arrive BGRA, which is the compositor's default.
extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeSetSwapRB(JNIEnv*, jobject, jlong h, jboolean swap) {
    if (auto* r = ctx(h)) r->setSwapRB(swap == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeSetPresentMode(JNIEnv*, jobject, jlong h, jint mode) {
    if (auto* r = ctx(h)) r->setPresentMode(static_cast<VkPresentModeKHR>(mode));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeFrameGenProblem(JNIEnv*, jobject, jlong h) {
    auto* r = ctx(h);
    return r ? r->frameGenProblem() : -1;
}

// {accepted, planned, sourceFps, presentedFps, thermal, chainMsPerGen}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeStats(JNIEnv* env, jobject, jlong h) {
    float stats[6] = {0.f, 0.f, 0.f, 0.f, -1.f, -1.f};
    if (auto* r = ctx(h)) r->frameGenStats(stats);
    jfloatArray arr = env->NewFloatArray(6);
    if (arr) env->SetFloatArrayRegion(arr, 0, 6, stats);
    return arr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_store_xbox_XboxNativeVideo_nativeDestroy(JNIEnv*, jobject, jlong h) {
    delete ctx(h);
}
