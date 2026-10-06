package com.retrovanta;

import org.junit.Test;
import static org.junit.Assert.*;

/** Generated header/diagnostic-bytecode smoke tests; not commercial-ROM compatibility tests. */
public final class NeoGeoPocketCoreSmokeTest {
    private static byte[] cartridge(int systemCode, int... program) {
        byte[] rom = new byte[0x2000];
        byte[] license = "COPYRIGHT BY SNK CORPORATION".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(license, 0, rom, 0, Math.min(28, license.length));
        rom[0x1c] = 0x40; rom[0x1d] = 0; rom[0x1e] = 0x20; rom[0x1f] = 0; // 0x200040
        rom[0x20] = 0x34; rom[0x21] = 0x12; rom[0x22] = 1;
        rom[0x23] = (byte) systemCode;
        byte[] title = "RV NGP TEST ".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, rom, 0x24, 12);
        for (int i = 0; i < program.length; i++) rom[0x40 + i] = (byte) program[i];
        return rom;
    }

    private static void storeA(int[] code, int address, int value) {
        code[0] = 0x10; code[1] = value;
        code[2] = 0x20; code[3] = address & 255; code[4] = (address >>> 8) & 255;
    }

    @Test public void parsesHeaderExecutesSubsetAndRendersMonochromeTilePlane() {
        // Set tile #1's low bitplane rows, map tile #1 at the top-left, then halt.
        int[] code = new int[5 * 9 + 1];
        int p = 0;
        for (int row = 0; row < 8; row++) {
            code[p++] = 0x10; code[p++] = 0xff;
            code[p++] = 0x20; code[p++] = (0x8010 + row) & 255; code[p++] = (0x8010 + row) >>> 8;
        }
        code[p++] = 0x10; code[p++] = 1;
        code[p++] = 0x20; code[p++] = 0x00; code[p++] = 0x90;
        code[p++] = 0xff;
        NeoGeoPocketCore core = new NeoGeoPocketCore();
        core.load(cartridge(0, code));
        assertEquals("RV NGP TEST", core.getCartridgeTitle());
        assertEquals(160, core.videoWidth());
        assertEquals(152, core.videoHeight());
        int[] frame = core.frame();
        assertEquals(160 * 152, frame.length);
        assertEquals(0xffb8c8a0, frame[0]); // shade 1 from set low plane
        assertEquals(0xfff8f8f0, frame[8]); // adjacent unmapped tile uses blank tile 0
    }

    @Test public void acceptsColorSystemHeaderButUsesOnlyMonochromeRenderingAndButtonRegister() {
        NeoGeoPocketCore core = new NeoGeoPocketCore();
        core.load(cartridge(0x10, 0x21, 0x00, 0xb0, 0xff)); // A <- buttons; stop
        core.setButtons(0x25);
        core.frame();
        assertEquals(0x25, core.accumulatorForTest());
        assertEquals(0x25, core.readMemoryForTest(0xb000));
        assertEquals("RV NGP TEST", core.getCartridgeTitle());
    }

    @Test public void rejectsUnknownSystemCodesAndTooShortHeaders() {
        NeoGeoPocketCore core = new NeoGeoPocketCore();
        try { core.load(new byte[0x3f]); fail("short header should fail"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("pequeno")); }
        try { core.load(cartridge(0x42, 0xff)); fail("unknown system code should fail"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("sistema")); }
    }
}
