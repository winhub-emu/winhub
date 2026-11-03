#include "view_transformation.hpp"

void ViewTransformation::update(int outerWidth, int outerHeight, int innerWidth, int innerHeight) {
    aspect = std::min((float)outerWidth / innerWidth, (float)outerHeight / innerHeight);
    viewWidth = (int)std::ceil(innerWidth * aspect);
    viewHeight = (int)std::ceil(innerHeight * aspect);
    viewOffsetX = (int)((outerWidth - innerWidth * aspect) * 0.5f);
    viewOffsetY = (int)((outerHeight - innerHeight * aspect) * 0.5f);
    sceneScaleX = (innerWidth * aspect) / outerWidth;
    sceneScaleY = (innerHeight * aspect) / outerHeight;
    sceneOffsetX = (innerWidth - innerWidth * sceneScaleX) * 0.5f;
    sceneOffsetY = (innerHeight - innerHeight * sceneScaleY) * 0.5f;
}