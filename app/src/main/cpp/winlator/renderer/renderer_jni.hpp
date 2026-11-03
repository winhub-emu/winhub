#pragma once

#include <atomic>
#include <string>
#include <jni.h>
#include <android/native_window_jni.h>
#include <android/asset_manager_jni.h>
#include <android/hardware_buffer.h>
#include <android/log.h>

#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#define HAL_PIXEL_FORMAT_BGRA_8888 5

class JNIXServer {
    public:
        jobject windowManager = nullptr;
        jobject inputDeviceManager = nullptr;
        jobject xserver = nullptr;
};

class JNICache {
    public:
        JavaVM *vm = nullptr;
        bool ready = false;

        jclass xserverClass;
        jfieldID windowManager;
        jfieldID inputDeviceManager;

        jclass inputDeviceManagerClass;
        jmethodID getPointWindow;

        jclass windowManagerClass;
        jfieldID rootWindow;

        jclass cursorClass;
        jmethodID cursorIsVisible;
        jfieldID cursorID;
        jfieldID cursorHotspotX;
        jfieldID cursorHotspotY;
        jfieldID cursorImage;

        jclass windowClass;
        jmethodID windowGetWidth;
        jmethodID windowGetHeight;
        jmethodID windowGetX;
        jmethodID windowGetY;
        jmethodID windowGetContent;
        jmethodID windowIsInputOutput;
        jfieldID windowID;
        jfieldID windowAttributes;

        jclass windowAttributesClass;
        jmethodID windowAttributesIsEnabled;

        jclass drawableClass;
        jfieldID drawableID;
        jfieldID drawableWidth;
        jfieldID drawableHeight;
        jmethodID drawableGetBackingAHB;

        JNICache() {}

        JNIEnv *getEnv() {
            JNIEnv *env = nullptr;
            jint result = vm->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (result == JNI_EDETACHED) {
                vm->AttachCurrentThread(&env, nullptr);
            }
            return env;
        }

        void detachEnv() {
            vm->DetachCurrentThread();
        }

        // Resolves every Java class/member the renderer reads. Any miss clears the pending Java exception
        // and reports failure, so the caller can fall back to another renderer instead of crashing.
        bool init(JNIEnv *env) {
            if (ready) return true;
            if (vm == nullptr && env->GetJavaVM(&vm) != JNI_OK) return false;

            bool ok = true;
            auto cls = [&](const char *name) -> jclass {
                jclass local = env->FindClass(name);
                if (local == nullptr) { ok = false; env->ExceptionClear(); return nullptr; }
                jclass global = (jclass)env->NewGlobalRef(local);
                env->DeleteLocalRef(local);
                return global;
            };
            auto field = [&](jclass c, const char *name, const char *sig) -> jfieldID {
                if (c == nullptr) { ok = false; return nullptr; }
                jfieldID id = env->GetFieldID(c, name, sig);
                if (id == nullptr) { ok = false; env->ExceptionClear(); }
                return id;
            };
            auto method = [&](jclass c, const char *name, const char *sig) -> jmethodID {
                if (c == nullptr) { ok = false; return nullptr; }
                jmethodID id = env->GetMethodID(c, name, sig);
                if (id == nullptr) { ok = false; env->ExceptionClear(); }
                return id;
            };

            xserverClass = cls("com/winhub/hernandez/xserver/XServer");
            windowClass = cls("com/winhub/hernandez/xserver/Window");
            windowAttributesClass = cls("com/winhub/hernandez/xserver/WindowAttributes");
            windowManagerClass = cls("com/winhub/hernandez/xserver/WindowManager");
            inputDeviceManagerClass = cls("com/winhub/hernandez/xserver/InputDeviceManager");
            drawableClass = cls("com/winhub/hernandez/xserver/Drawable");
            cursorClass = cls("com/winhub/hernandez/xserver/Cursor");

            inputDeviceManager = field(xserverClass, "inputDeviceManager", "Lcom/winhub/hernandez/xserver/InputDeviceManager;");
            getPointWindow = method(inputDeviceManagerClass, "getPointWindow", "()Lcom/winhub/hernandez/xserver/Window;");

            windowManager = field(xserverClass, "windowManager", "Lcom/winhub/hernandez/xserver/WindowManager;");
            rootWindow = field(windowManagerClass, "rootWindow", "Lcom/winhub/hernandez/xserver/Window;");

            windowGetHeight = method(windowClass, "getHeight", "()S");
            windowGetWidth = method(windowClass, "getWidth", "()S");
            windowGetX = method(windowClass, "getX", "()S");
            windowGetY = method(windowClass, "getY", "()S");
            windowIsInputOutput = method(windowClass, "isInputOutput", "()Z");
            windowAttributesIsEnabled = method(windowAttributesClass, "isEnabled", "()Z");
            windowGetContent = method(windowClass, "getContent", "()Lcom/winhub/hernandez/xserver/Drawable;");
            windowID = field(windowClass, "id", "I");
            windowAttributes = field(windowClass, "attributes", "Lcom/winhub/hernandez/xserver/WindowAttributes;");

            drawableID = field(drawableClass, "id", "I");
            drawableWidth = field(drawableClass, "width", "S");
            drawableHeight = field(drawableClass, "height", "S");
            drawableGetBackingAHB = method(drawableClass, "getBackingAHB", "()J");

            cursorIsVisible = method(cursorClass, "isVisible", "()Z");
            cursorID = field(cursorClass, "id", "I");
            cursorHotspotX = field(cursorClass, "hotSpotX", "I");
            cursorHotspotY = field(cursorClass, "hotSpotY", "I");
            cursorImage = field(cursorClass, "cursorImage", "Lcom/winhub/hernandez/xserver/Drawable;");

            if (env->ExceptionCheck()) { env->ExceptionClear(); ok = false; }
            ready = ok;
            return ok;
        }
};
