/*
 * Ported from Pipetto-crypto/winlator (branch winlator_bionic, commit 60c291b9),
 * app/src/main/java/com/winlator/cmod/xserver/errors/GLXBadProfileARB.java.
 * Original work Copyright (c) 2023 BrunoSX and contributors; MIT License.
 * Adapted for com.winhub.hernandez: package rename.
 * See THIRD-PARTY-LICENSES.md.
 */
package com.winhub.hernandez.xserver.errors;

public class GLXBadProfileARB extends XRequestError {
    public GLXBadProfileARB(int id) {
        super(GLXError.BASE_ERROR_CODE + 13, id);
    }
}
