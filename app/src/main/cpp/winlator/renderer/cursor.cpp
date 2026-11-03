#include "cursor.hpp"

void CursorManager::addCursor(int id, std::unique_ptr<struct Cursor> cursor) {
    auto lock = cursorLock.lock();
    this->cursors[id] = std::move(cursor);
}

void CursorManager::removeCursor(JNIEnv *env, Cursor *cursor) {
    if (cursor->cursorObj != nullptr) env->DeleteGlobalRef(cursor->cursorObj);
    if (cursor->image && cursor->image->drawableObj != nullptr) env->DeleteGlobalRef(cursor->image->drawableObj);
    
    {
        auto lock = cursorLock.lock();
        this->cursors.erase(cursor->id);
    }
}

Cursor* CursorManager::getCursor(int id) {
    auto lock = cursorLock.lock();
    auto it = this->cursors.find(id);
    return it == this->cursors.end() ? nullptr : it->second.get();
}

void CursorManager::setRootCursor(std::unique_ptr<struct Cursor> cursor) {
    this->rootCursor = std::move(cursor);
}

Cursor* CursorManager::getRootCursor() {
    return this->rootCursor.get();
}

void CursorManager::clear(JNIEnv *env) {
    std::unordered_map<int, std::unique_ptr<struct Cursor>> old;
    {
        auto lock = cursorLock.lock();
        old.swap(this->cursors);
    }
    for (auto &entry : old) {
        if (entry.second->cursorObj != nullptr) env->DeleteGlobalRef(entry.second->cursorObj);
        if (entry.second->image && entry.second->image->drawableObj != nullptr) env->DeleteGlobalRef(entry.second->image->drawableObj);
    }
    rootCursor.reset();
}

std::vector<Cursor *> CursorManager::getAllCursors() {
    std::vector<Cursor *> all;
    auto lock = cursorLock.lock();
    for (auto &entry : cursors) all.push_back(entry.second.get());
    if (rootCursor) all.push_back(rootCursor.get());
    return all;
}
