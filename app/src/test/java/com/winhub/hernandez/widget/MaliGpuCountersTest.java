package com.winhub.hernandez.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The model gate that keeps job-manager Mali GPUs (reboot risk) away from /dev/mali0. */
public class MaliGpuCountersTest {

    @Test
    public void csfGenerationNames_pass() {
        assertTrue(MaliGpuCounters.isCsfMaliName("Mali-G615 MC6"));
        assertTrue(MaliGpuCounters.isCsfMaliName("Mali-G710 MC10"));
        assertTrue(MaliGpuCounters.isCsfMaliName("Mali-G310"));
        assertTrue(MaliGpuCounters.isCsfMaliName("ARM Mali-G720 Immortalis MC12"));
        assertTrue(MaliGpuCounters.isCsfMaliName("Immortalis-G925"));
    }

    @Test
    public void jobManagerNames_blocked() {
        assertFalse(MaliGpuCounters.isCsfMaliName("Mali-G57 MC2"));
        assertFalse(MaliGpuCounters.isCsfMaliName("Mali-G76 MC4"));
        assertFalse(MaliGpuCounters.isCsfMaliName("Mali-G78 MP20"));
        assertFalse(MaliGpuCounters.isCsfMaliName("Mali-G52 r1"));
        assertFalse(MaliGpuCounters.isCsfMaliName("Mali-T880"));
    }

    @Test
    public void otherGpus_blocked() {
        assertFalse(MaliGpuCounters.isCsfMaliName("Adreno (TM) 750"));
        assertFalse(MaliGpuCounters.isCsfMaliName("Samsung Xclipse 940"));
        assertFalse(MaliGpuCounters.isCsfMaliName(null));
    }
}
