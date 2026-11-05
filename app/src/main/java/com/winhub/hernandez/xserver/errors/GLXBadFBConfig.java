/*
 * Ported from Pipetto-crypto/winlator (branch winlator_bionic),
 * app/src/main/java/com/winlator/cmod/xserver/errors/GLXBadFBConfig.java.
 * Original work Copyright (c) 2023 BrunoSX and contributors; MIT License.
 * Adapted for com.winhub.hernandez: package rename.
 * See THIRD-PARTY-LICENSES.md.
 */
package com.winhub.hernandez.xserver.errors;

public class GLXBadFBConfig extends XRequestError {
    public GLXBadFBConfig(int id) {
        super(GLXError.BASE_ERROR_CODE + 9, id);
    }
}
