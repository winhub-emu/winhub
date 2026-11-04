#pragma once

#include <cmath>
#include <algorithm>

class ViewTransformation {
    public:
        int viewOffsetX;
        int viewOffsetY;
        int viewWidth;
        int viewHeight;
        float aspect;
        float sceneScaleX;
        float sceneScaleY;
        float sceneOffsetX;
        float sceneOffsetY;
        
        void update(int outerWidth, int outerHeight, int innerWidth, int innerHeight);
};