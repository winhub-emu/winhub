/*
 * Ported from Pipetto-crypto/winlator (branch winlator_bionic),
 * app/src/main/java/com/winlator/cmod/xserver/errors/GLXBadWindow.java.
 * Original work Copyright (c) 2023 BrunoSX and contributors; MIT License.
 * Adapted for com.winhub.hernandez: package rename.
 * See THIRD-PARTY-LICENSES.md.
 */
package com.winhub.hernandez.xserver.errors;

public class GLXBadWindow extends XRequestError {
    public GLXBadWindow(int id) {
        super(GLXError.BASE_ERROR_CODE + 12, id);
    }
}
