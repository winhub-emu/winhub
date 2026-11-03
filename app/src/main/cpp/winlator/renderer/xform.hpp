#pragma once

#include <cmath>

namespace XForm {
    void set(float *xform, float n11, float n12, float n21, float n22, float dx, float dy);
    void set(float *xform, float tx, float ty, float sx, float sy);
    void identity(float *xform);
    void makeTransform(float *xform, float tx, float ty, float sx, float sy, float angle);
    void multiply(float *result, float *ta, float *tb);
}