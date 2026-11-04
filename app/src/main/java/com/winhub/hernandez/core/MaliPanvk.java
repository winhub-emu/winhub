package com.winhub.hernandez.core;

/** Arm Mali detection shared by the Mali-only options (PanVK driver sources, BCn layer, HUD counters). */
public final class MaliPanvk {
    public static final int VENDOR_ARM = 0x13B5;

    private static volatile Integer vendorId;

    private MaliPanvk() {}

    /** True on an Arm Mali GPU. The probe runs once per process. */
    public static boolean isMaliGpu() {
        Integer v = vendorId;
        if (v == null) {
            try {
                v = GPUInformation.getVendorID(null, null);
            } catch (Throwable t) {
                return false; // probe unavailable: not Mali, and not cached so a later call retries
            }
            vendorId = v;
        }
        return v == VENDOR_ARM;
    }
}
