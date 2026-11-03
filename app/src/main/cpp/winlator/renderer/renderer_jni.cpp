#include "egl.hpp"
#include <cstdint>
#include <cstring>

#define STB_IMAGE_STATIC
#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#define LOG_TAG "Renderer"
#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {

JNICache cache;
JNIXServer xserver;
WindowManager windowManager;
CursorManager cursorManager;
EGLRenderer renderer;

// Fills the native drawable from the Java Drawable. The AHB is only present when the Java side backed the
// drawable with a GPUImage; otherwise the texture stays empty instead of reading memory we do not own.
void bindDrawable(JNIEnv *env, Drawable *drawable, jobject drawableObj) {
    drawable->id = env->GetIntField(drawableObj, cache.drawableID);
    drawable->width = env->GetShortField(drawableObj, cache.drawableWidth);
    drawable->height = env->GetShortField(drawableObj, cache.drawableHeight);
    drawable->data = nullptr;
    drawable->format = HAL_PIXEL_FORMAT_BGRA_8888;
    drawable->setAhb((AHardwareBuffer *)env->CallLongMethod(drawableObj, cache.drawableGetBackingAHB));
}

std::unique_ptr<Drawable> createDrawable(JNIEnv *env, jobject drawableObj, bool directContent) {
    auto drawable = std::make_unique<Drawable>();
    bindDrawable(env, drawable.get(), drawableObj);
    drawable->isDirectContent = directContent;
    drawable->drawableObj = env->NewGlobalRef(drawableObj);
    return drawable;
}

bool loadRootCursor(JNIEnv *env, jobject context) {
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getAssets = env->GetMethodID(contextClass, "getAssets", "()Landroid/content/res/AssetManager;");
    jobject assetManagerObject = env->CallObjectMethod(context, getAssets);
    env->DeleteLocalRef(contextClass);
    if (assetManagerObject == nullptr) return false;

    AAssetManager *assetManager = AAssetManager_fromJava(env, assetManagerObject);
    AAsset *cursorAsset = AAssetManager_open(assetManager, "cursor.png", AASSET_MODE_BUFFER);
    env->DeleteLocalRef(assetManagerObject);
    if (cursorAsset == nullptr) {
        printf("cursor.png asset is missing");
        return false;
    }

    off_t len = AAsset_getLength(cursorAsset);
    const unsigned char *data = static_cast<const unsigned char *>(AAsset_getBuffer(cursorAsset));

    int w = 0, h = 0, c = 0;
    unsigned char *cursorData = data != nullptr ? stbi_load_from_memory(data, (int)len, &w, &h, &c, 4) : nullptr;
    AAsset_close(cursorAsset);
    if (cursorData == nullptr) {
        printf("Failed to decode cursor.png");
        return false;
    }

    AHardwareBuffer_Desc desc{};
    desc.width = w;
    desc.height = h;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.layers = 1;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN | AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;

    AHardwareBuffer *buffer = nullptr;
    if (AHardwareBuffer_allocate(&desc, &buffer) != 0 || buffer == nullptr) {
        printf("Failed to allocate the root cursor buffer");
        stbi_image_free(cursorData);
        return false;
    }

    AHardwareBuffer_Desc outDesc{};
    AHardwareBuffer_describe(buffer, &outDesc);

    uint8_t *dst = nullptr;
    if (AHardwareBuffer_lock(buffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, nullptr, reinterpret_cast<void **>(&dst)) != 0 || dst == nullptr) {
        printf("Failed to lock the root cursor buffer");
        AHardwareBuffer_release(buffer);
        stbi_image_free(cursorData);
        return false;
    }

    for (int y = 0; y < h; ++y)
        memcpy(dst + (size_t)y * outDesc.stride * 4, cursorData + (size_t)y * w * 4, (size_t)w * 4);

    AHardwareBuffer_unlock(buffer, nullptr);
    stbi_image_free(cursorData);

    auto cursorDrawable = std::make_unique<Drawable>();
    cursorDrawable->id = -1;
    cursorDrawable->width = w;
    cursorDrawable->height = h;
    cursorDrawable->adoptAhb(buffer);

    auto rootCursor = std::make_unique<Cursor>();
    rootCursor->id = cursorDrawable->id;
    rootCursor->image = std::move(cursorDrawable);
    rootCursor->hotspotX = 0;
    rootCursor->hotspotY = 0;
    rootCursor->visible = true;
    rootCursor->cursorObj = nullptr;

    cursorManager.setRootCursor(std::move(rootCursor));
    return true;
}

// Every texture is owned by the render thread's GL context, so teardown has to run there.
void destroyWindowOnRenderThread(int id) {
    JNIEnv *env = cache.getEnv();
    auto window = windowManager.getWindow(id);
    if (!window) return;

    std::vector<Window *> subtree{window};
    for (size_t i = 0; i < subtree.size(); ++i)
        for (auto child : subtree[i]->children) subtree.push_back(child);

    for (auto w : subtree) {
        if (w->inputOutput && w->drawable && w->drawable->glTexture != nullptr)
            renderer.destroyTexture(w->drawable->glTexture.get());
        for (auto &content : w->directContents)
            if (content.second->glTexture != nullptr) renderer.destroyTexture(content.second->glTexture.get());
    }

    windowManager.deleteWindow(env, window);
    renderer.updateScene();
}

void releaseSession(JNIEnv *env) {
    windowManager.clear(env);
    cursorManager.clear(env);
    if (xserver.windowManager != nullptr) env->DeleteGlobalRef(xserver.windowManager);
    if (xserver.inputDeviceManager != nullptr) env->DeleteGlobalRef(xserver.inputDeviceManager);
    if (xserver.xserver != nullptr) env->DeleteGlobalRef(xserver.xserver);
    xserver.windowManager = nullptr;
    xserver.inputDeviceManager = nullptr;
    xserver.xserver = nullptr;
}

}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeInit(JNIEnv *env, jobject thiz, jobject context, jobject xServer, jint textureFilter) {
    if (!cache.init(env)) {
        printf("Failed to resolve the Java classes the EGL renderer needs");
        return JNI_FALSE;
    }

    // A previous session may have left state behind in this process.
    renderer.stop();
    renderer.join();
    releaseSession(env);

    jobject windowManagerObj = env->GetObjectField(xServer, cache.windowManager);
    jobject inputDeviceManagerObj = env->GetObjectField(xServer, cache.inputDeviceManager);
    jobject rootWindowObj = windowManagerObj != nullptr ? env->GetObjectField(windowManagerObj, cache.rootWindow) : nullptr;
    if (windowManagerObj == nullptr || inputDeviceManagerObj == nullptr || rootWindowObj == nullptr) {
        printf("X server is missing its window manager");
        return JNI_FALSE;
    }

    auto rootWindow = std::make_unique<Window>();
    rootWindow->id = env->GetIntField(rootWindowObj, cache.windowID);
    rootWindow->width = env->CallShortMethod(rootWindowObj, cache.windowGetWidth);
    rootWindow->height = env->CallShortMethod(rootWindowObj, cache.windowGetHeight);
    rootWindow->x = env->CallShortMethod(rootWindowObj, cache.windowGetX);
    rootWindow->y = env->CallShortMethod(rootWindowObj, cache.windowGetY);
    rootWindow->z_order = INT32_MIN;
    rootWindow->className = "";

    jobject drawableObj = env->CallObjectMethod(rootWindowObj, cache.windowGetContent);
    if (drawableObj != nullptr) {
        rootWindow->drawable = createDrawable(env, drawableObj, false);
        env->DeleteLocalRef(drawableObj);
    }

    rootWindow->cursor = nullptr;
    rootWindow->parent = nullptr;
    rootWindow->mapped = true;
    rootWindow->enabled = true;
    rootWindow->inputOutput = true;
    rootWindow->externalContent = nullptr;
    rootWindow->surface = nullptr;

    jobject attributes = env->GetObjectField(rootWindowObj, cache.windowAttributes);
    rootWindow->attributes = env->NewGlobalRef(attributes);
    rootWindow->windowObj = env->NewGlobalRef(rootWindowObj);

    env->DeleteLocalRef(rootWindowObj);
    env->DeleteLocalRef(attributes);

    // Read before the move: argument evaluation order is unspecified.
    int rootId = rootWindow->id;
    windowManager.setRootWindow(rootWindow.get());
    windowManager.addWindow(rootId, std::move(rootWindow));

    // Without cursor.png the renderer still works, it just has no fallback pointer image.
    loadRootCursor(env, context);

    xserver.windowManager = env->NewGlobalRef(windowManagerObj);
    xserver.inputDeviceManager = env->NewGlobalRef(inputDeviceManagerObj);
    xserver.xserver = env->NewGlobalRef(xServer);

    env->DeleteLocalRef(windowManagerObj);
    env->DeleteLocalRef(inputDeviceManagerObj);

    renderer.windowManager = &windowManager;
    renderer.cursorManager = &cursorManager;
    renderer.cache = &cache;
    renderer.xServer = &xserver;
    renderer.setTextureFilter(textureFilter);
    renderer.start();
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeCreateWindow(JNIEnv *env, jobject thiz, jobject windowObj, jint parentId) {
    auto window = std::make_unique<Window>();
    window->id = env->GetIntField(windowObj, cache.windowID);
    window->width = env->CallShortMethod(windowObj, cache.windowGetWidth);
    window->height = env->CallShortMethod(windowObj, cache.windowGetHeight);
    window->x = env->CallShortMethod(windowObj, cache.windowGetX);
    window->y = env->CallShortMethod(windowObj, cache.windowGetY);
    window->z_order = 100;
    window->className = "";

    bool isInputOutput = env->CallBooleanMethod(windowObj, cache.windowIsInputOutput);
    window->inputOutput = isInputOutput;
    window->drawable = nullptr;

    if (isInputOutput) {
        jobject drawableObj = env->CallObjectMethod(windowObj, cache.windowGetContent);
        if (drawableObj != nullptr) {
            window->drawable = createDrawable(env, drawableObj, false);
            env->DeleteLocalRef(drawableObj);
        }
    }

    window->cursor = nullptr;
    window->mapped = false;
    window->parent = nullptr;
    window->compositeRedirected = false;
    window->externalContent = nullptr;
    window->surface = nullptr;
    window->enabled = true;

    jobject attributes = env->GetObjectField(windowObj, cache.windowAttributes);
    window->attributes = env->NewGlobalRef(attributes);
    window->windowObj = env->NewGlobalRef(windowObj);
    env->DeleteLocalRef(attributes);

    // A content-less window has nothing to sample, so it must never be treated as drawable.
    if (window->inputOutput && !window->drawable) window->inputOutput = false;

    if (parentId > -1) {
        auto parent = windowManager.getWindow(parentId);
        window->parent = parent;
        if (parent) parent->children.push_back(window.get());
    }

    int windowId = window->id;
    windowManager.addWindow(windowId, std::move(window));
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeMapWindow(JNIEnv *env, jobject thiz, jint id) {
    auto window = windowManager.getWindow(id);
    if (!window) return;

    window->mapped = true;
    renderer.queueEvent([]{ renderer.updateScene(); });
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeUnmapWindow(JNIEnv *env, jobject thiz, jint id) {
    auto window = windowManager.getWindow(id);
    if (!window) return;

    window->mapped = false;
    renderer.queueEvent([]{ renderer.updateScene(); });
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeDestroyWindow(JNIEnv *env, jobject thiz, jint id) {
    // Looked up again on the render thread: the window may already be gone when the event runs.
    renderer.queueEvent([id] { destroyWindowOnRenderThread(id); });
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeCreateCursor(JNIEnv *env, jobject thiz, jobject cursorObj, jlong ahbPtr) {
    jobject drawableObj = env->GetObjectField(cursorObj, cache.cursorImage);
    if (drawableObj == nullptr || ahbPtr == 0) {
        if (drawableObj != nullptr) env->DeleteLocalRef(drawableObj);
        return;
    }

    // The cursor pixels live in a buffer of their own (kept in sync from Java), not in the Drawable.
    auto image = std::make_unique<Drawable>();
    image->id = env->GetIntField(drawableObj, cache.drawableID);
    image->width = env->GetShortField(drawableObj, cache.drawableWidth);
    image->height = env->GetShortField(drawableObj, cache.drawableHeight);
    image->format = HAL_PIXEL_FORMAT_BGRA_8888;
    image->setAhb((AHardwareBuffer *)ahbPtr);
    env->DeleteLocalRef(drawableObj);

    auto cursor = std::make_unique<Cursor>();
    cursor->id = env->GetIntField(cursorObj, cache.cursorID);
    cursor->image = std::move(image);
    cursor->hotspotX = env->GetIntField(cursorObj, cache.cursorHotspotX);
    cursor->hotspotY = env->GetIntField(cursorObj, cache.cursorHotspotY);
    cursor->visible = env->CallBooleanMethod(cursorObj, cache.cursorIsVisible);
    cursor->cursorObj = env->NewGlobalRef(cursorObj);

    int cursorId = cursor->id;
    cursorManager.addCursor(cursorId, std::move(cursor));
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeFreeCursor(JNIEnv *env, jobject thiz, jint id) {
    renderer.queueEvent([id] {
        auto cursor = cursorManager.getCursor(id);
        if (!cursor) return;

        JNIEnv *env = cache.getEnv();
        if (cursor->image->glTexture != nullptr)
            renderer.destroyTexture(cursor->image->glTexture.get());

        // Windows keep raw pointers to their cursor, so none may outlive it.
        {
            auto lock = windowManager.windowLock.lock();
            for (auto &entry : windowManager.getWindowTree())
                if (entry.second->cursor == cursor) entry.second->cursor = nullptr;
        }

        cursorManager.removeCursor(env, cursor);
    });
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeBindCursor(JNIEnv *env, jobject thiz, jint windowId, jint cursorId, jboolean visible) {
    auto window = windowManager.getWindow(windowId);
    if (!window) return;
    auto cursor = cursorManager.getCursor(cursorId);
    if (!cursor) return;

    cursor->visible = visible;

    window->cursor = cursor;
    for (auto &child : window->children)
        child->cursor = cursor;

    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativePointerMove(JNIEnv *env, jobject thiz, jint posX, jint posY) {
    cursorManager.pointer.posX = posX;
    cursorManager.pointer.posY = posY;
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeChangeWindowZOrder(JNIEnv *env, jobject thiz, jint stackMode, jint id, jint siblingId) {
    auto window = windowManager.getWindow(id);
    auto sibling = windowManager.getWindow(siblingId);

    if (!window) return;

    windowManager.changeZOrder(stackMode, window, sibling);

    renderer.queueEvent([]{ renderer.updateScene(); });
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeUpdateWindowGeometry(JNIEnv *env, jobject thiz, jint id, jint width, jint height, jint x, jint y, jboolean resized) {
    auto window = windowManager.getWindow(id);
    if (!window) return;

    window->width = width;
    window->height = height;
    window->x = x;
    window->y = y;

    if (resized && window->inputOutput && window->drawable) {
        jobject drawableObj = env->CallObjectMethod(window->windowObj, cache.windowGetContent);
        if (drawableObj != nullptr) {
            jobject newDrawableObj = env->NewGlobalRef(drawableObj);
            AHardwareBuffer *newAhb = (AHardwareBuffer *)env->CallLongMethod(drawableObj, cache.drawableGetBackingAHB);
            if (newAhb != nullptr) AHardwareBuffer_acquire(newAhb);
            env->DeleteLocalRef(drawableObj);

            // The render thread owns the drawable's buffer and texture, so the swap happens there.
            renderer.queueEvent([id, width, height, newDrawableObj, newAhb] {
                JNIEnv *env = cache.getEnv();
                auto w = windowManager.getWindow(id);
                if (!w || !w->drawable) {
                    env->DeleteGlobalRef(newDrawableObj);
                    if (newAhb != nullptr) AHardwareBuffer_release(newAhb);
                    return;
                }

                if (w->drawable->drawableObj != nullptr) env->DeleteGlobalRef(w->drawable->drawableObj);
                w->drawable->drawableObj = newDrawableObj;
                w->drawable->data = nullptr;
                w->drawable->width = width;
                w->drawable->height = height;
                w->drawable->adoptAhb(newAhb);
                if (w->drawable->glTexture != nullptr) w->drawable->glTexture->sizeChanged = true;
            });
        }
    }

    if (resized) {
        renderer.queueEvent([]{ renderer.updateScene(); });
    } else {
        renderer.queueEvent([id]{
            auto w = windowManager.getWindow(id);
            if (w) renderer.updateWindowPosition(w);
        });
        renderer.queueEvent([]{ renderer.updateScene(); });
    }
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeUpdateWindowContent(JNIEnv *env, jobject thiz, jint id) {
    auto window = windowManager.getWindow(id);
    if (!window) return;

    window->hasContent = true;
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeReparentWindow(JNIEnv *env, jobject thiz, jint id, jint parentId, jshort x, jshort y) {
    auto window = windowManager.getWindow(id);
    auto parent = windowManager.getWindow(parentId);

    if (!window || !parent) return;

    window->x = x;
    window->y = y;

    windowManager.reparentWindow(window, parent);

    renderer.queueEvent([id]{
        auto w = windowManager.getWindow(id);
        if (w) renderer.updateWindowPosition(w);
    });
    renderer.queueEvent([]{ renderer.updateScene(); });
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeToggleFullscreen(JNIEnv *env, jobject thiz) {
    renderer.toggleFullscreen = true;
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeSetTextureFilter(JNIEnv *env, jobject thiz, jint textureFilter) {
    renderer.setTextureFilter(textureFilter);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeSetCursorVisible(JNIEnv *env, jobject thiz, jboolean visible) {
    renderer.cursorVisible = visible;
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeSetScreenOffsetYRelativeToCursor(JNIEnv *env, jobject thiz, jboolean cond) {
    renderer.screenOffsetYRelativeToCursor = cond;
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeSetMagnifierZoom(JNIEnv *env, jobject thiz, jfloat magnifierZoom) {
    renderer.magnifierZoom = magnifierZoom;
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeSetUnviewableWMClass(JNIEnv *env, jobject thiz, jstring unviewableWMName) {
    if (unviewableWMName == nullptr) {
        windowManager.setUnviewableWMClass("");
        return;
    }
    const char *chars = env->GetStringUTFChars(unviewableWMName, nullptr);
    if (chars == nullptr) return;
    std::string str(chars);
    env->ReleaseStringUTFChars(unviewableWMName, chars);
    windowManager.setUnviewableWMClass(str);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeSetWindowClassName(JNIEnv *env, jobject thiz, jint id, jstring className) {
    auto window = windowManager.getWindow(id);
    if (!window || className == nullptr) return;

    const char *chars = env->GetStringUTFChars(className, nullptr);
    if (chars == nullptr) return;
    std::string str(chars);
    env->ReleaseStringUTFChars(className, chars);
    window->className = str;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeCreateSurface(JNIEnv *env, jobject thiz, jobject surface) {
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) return;
    renderer.createSurface(window);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeDestroySurface(JNIEnv *env, jobject thiz) {
    renderer.destroySurface();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeChangeSurface(JNIEnv *env, jobject thiz, jint width, jint height) {
    renderer.changeSurface(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativePause(JNIEnv *env, jobject thiz) {
    renderer.pause();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeResume(JNIEnv *env, jobject thiz) {
    renderer.resume();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeStop(JNIEnv *env, jobject thiz) {
    renderer.stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeAddDirectContent(JNIEnv *env, jobject thiz, jint windowId, jobject drawableObj) {
    auto window = windowManager.getWindow(windowId);
    if (!window) return;

    auto drawable = createDrawable(env, drawableObj, true);
    // Content with no buffer behind it would only ever render empty.
    if (drawable->ahb == nullptr) {
        env->DeleteGlobalRef(drawable->drawableObj);
        return;
    }

    // The Java drawable is as wide as the buffer's row stride; the picture itself is the buffer's real size.
    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(drawable->ahb, &desc);
    drawable->width = desc.width;
    drawable->height = desc.height;

    if (window->parent) window->parent->surface = window;
    window->externalContent = nullptr;
    window->directContents[drawable->id] = std::move(drawable);
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeUpdateDirectContent(JNIEnv *env, jobject thiz, jint windowId, jint drawableId) {
    auto window = windowManager.getWindow(windowId);
    if (!window) return;

    auto it = window->directContents.find(drawableId);
    if (it == window->directContents.end()) return;

    window->externalContent = it->second.get();
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeRemoveDirectContent(JNIEnv *env, jobject thiz, jint windowId, jint drawableId) {
    // The render thread may be sampling this content right now, so it is retired there.
    renderer.queueEvent([windowId, drawableId] {
        auto window = windowManager.getWindow(windowId);
        if (!window) return;

        auto it = window->directContents.find(drawableId);
        if (it == window->directContents.end()) return;

        JNIEnv *env = cache.getEnv();
        auto content = it->second.get();
        if (window->externalContent == content) window->externalContent = nullptr;
        if (content->glTexture != nullptr) renderer.destroyTexture(content->glTexture.get());
        if (content->drawableObj != nullptr) env->DeleteGlobalRef(content->drawableObj);
        window->directContents.erase(it);
    });
    renderer.requestRenderer();
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeSetCompositeRedirected(JNIEnv *env, jobject thiz, jint windowId, jboolean redirected) {
    auto window = windowManager.getWindow(windowId);
    if (!window) return;

    window->compositeRedirected = redirected;
}

extern "C" JNIEXPORT void JNICALL
Java_com_winhub_hernandez_renderer_egl_EglRenderer_nativeCompositeRedirect(JNIEnv *env, jobject thiz, jint srcDrawableId, jint dstDrawableId, jshort dstX, jshort dstY) {
    auto srcWindow = windowManager.getWindow(srcDrawableId);
    if (!srcWindow) return;

    auto dstWindow = windowManager.getWindow(dstDrawableId);
    if (!dstWindow) return;

    if (!srcWindow->isAncestorOf(dstWindow)) {
        bool positionChanged = false;
        bool zOrderChanged = false;

        auto sibling = srcWindow->getWindowSibling(dstWindow);
        if (!sibling) return;

        int posX = dstWindow->x + dstX;
        int posY = dstWindow->y + dstY;

        if (sibling->x != posX || sibling->y != posY) {
            sibling->x = posX;
            sibling->y = posY;
            positionChanged = true;
        }

        if (sibling->z_order <= dstWindow->z_order) {
            windowManager.changeZOrder(1, sibling, dstWindow);
            zOrderChanged = true;
        }

        if (positionChanged || zOrderChanged) {
            int siblingId = sibling->id;
            renderer.queueEvent([siblingId]{
                auto w = windowManager.getWindow(siblingId);
                if (w) renderer.updateWindowPosition(w);
            });
            renderer.queueEvent([]{ renderer.updateScene(); });
            renderer.requestRenderer();
        }
    }
}
