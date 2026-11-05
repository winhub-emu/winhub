/*
 * Ported from Pipetto-crypto/winlator (branch winlator_bionic),
 * app/src/main/java/com/winlator/cmod/xserver/errors/GLXError.java.
 * Original work Copyright (c) 2023 BrunoSX and contributors; MIT License.
 * Adapted for com.winhub.hernandez: package rename; error base +3 -> +4 (Randr owns +0..+3 here).
 * See THIRD-PARTY-LICENSES.md.
 */
package com.winhub.hernandez.xserver.errors;

public abstract class GLXError {
    public static byte BASE_ERROR_CODE = Byte.MIN_VALUE + 4; // +4: clear of Randr's 4 error codes (also at MIN_VALUE here)
}
