#pragma once

#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>

#include "drawable.hpp"
#include "renderer_jni.hpp"

struct Cursor {
    int id;
    std::unique_ptr<struct Drawable> image;
    int hotspotX;
    int hotspotY;
    bool visible;
    jobject cursorObj;
};

struct Pointer {
    int posX;
    int posY;
};

struct CursorLock {
    std::mutex mutex;
   
    std::unique_lock<std::mutex> lock() {
        return std::unique_lock<std::mutex>(mutex);
    }
};

class CursorManager {
    private:
        std::unordered_map<int, std::unique_ptr<struct Cursor>> cursors;
        std::unique_ptr<struct Cursor> rootCursor;
        
    public: 
        Pointer pointer{0, 0};
        CursorLock cursorLock;
        
        void setRootCursor(std::unique_ptr<struct Cursor> cursor);
        Cursor *getRootCursor();
        void addCursor(int id, std::unique_ptr<struct Cursor> cursor);
        void removeCursor(JNIEnv *env, Cursor *cursor);
        Cursor *getCursor(int id);
        void clear(JNIEnv *env);
        std::vector<Cursor *> getAllCursors();
};