#include "xform.hpp"

void XForm::set(float *xform, float n11, float n12, float n21, float n22, float dx, float dy) {
    xform[0] = n11; 
    xform[1] = n12;
    xform[2] = n21;
    xform[3] = n22;
    xform[4] =  dx; 
    xform[5] =  dy;
}

void XForm::set(float *xform, float tx, float ty, float sx, float sy) {
    xform[0] = sx; 
    xform[1] =  0;
    xform[2] =  0; 
    xform[3] = sy;
    xform[4] = tx; 
    xform[5] = ty;
}

void XForm::identity(float *xform) {
    set(xform, 1, 0, 0, 1, 0, 0);
}

void XForm::makeTransform(float *xform, float tx, float ty, float sx, float sy, float angle) {
    float c = std::cos(angle);
    float s = std::sin(angle);

    xform[0] = sx * c;
    xform[1] = sy * s;
    xform[2] = sx * -s;
    xform[3] = sy * c;
    xform[4] = tx;
    xform[5] = ty;
}

void XForm::multiply(float *result, float *ta, float *tb) {
    float a0 = ta[0], a3 = ta[3], 
          a1 = ta[1], a4 = ta[4],
          a2 = ta[2], a5 = ta[5],
          b0 = tb[0], b3 = tb[3],
          b1 = tb[1], b4 = tb[4],
          b2 = tb[2], b5 = tb[5];
          
    result[0] = a0 * b0 + a1 * b2;
    result[1] = a0 * b1 + a1 * b3;
    result[2] = a2 * b0 + a3 * b2;
    result[3] = a2 * b1 + a3 * b3;
    result[4] = a4 * b0 + a5 * b2 + b4;
    result[5] = a4 * b1 + a5 * b3 + b5;
}
