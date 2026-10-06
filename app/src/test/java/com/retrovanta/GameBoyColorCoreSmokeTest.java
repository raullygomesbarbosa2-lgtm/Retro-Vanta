package com.retrovanta;

import org.junit.Test;
import static org.junit.Assert.*;

/** Generated CGB ROM smoke tests; these are not commercial-game compatibility tests. */
public final class GameBoyColorCoreSmokeTest {
    private static byte[] cartridge(int type, int banks, int... program) {
        byte[] rom = new byte[0x4000 * banks];
        byte[] title = "RV CGB TEST".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(title, 0, rom, 0x134, title.length);
        rom[0x143] = (byte) 0xc0; // CGB-only cartridge
        rom[0x147] = (byte) type;
        rom[0x148] = (byte) (banks == 2 ? 0 : banks == 4 ? 1 : 2);
        rom[0x149] = 0; // no external RAM
        for (int i = 0; i < program.length; i++) rom[0x100 + i] = (byte) program[i];
        return rom;
    }

    @Test public void runsGeneratedCgbProgramAndUsesColorPaletteMemory() {
        GameBoyColorCore core = new GameBoyColorCore();
        core.load(cartridge(0, 2,
                0x3e, 0x80,       // BG palette index 0, auto-increment
                0xe0, 0x68,
                0x3e, 0x1f,       // palette color 0 = red (15-bit BGR)
                0xe0, 0x69,
                0x3e, 0x00,
                0xe0, 0x69,
                0x18, 0xfe));     // stable loop
        assertEquals("RV CGB TEST", core.getCartridgeTitle());
        assertEquals(160, core.videoWidth());
        assertEquals(144, core.videoHeight());
        int[] frame = core.frame();
        assertEquals(160 * 144, frame.length);
        assertEquals(0xffff0000, frame[0]);
        assertEquals(0xffff0000, frame[frame.length - 1]);
    }

    @Test public void switchesMbc5RomAndIndependentVramAndWramBanks() {
        byte[] rom = cartridge(0x19, 4,
                0x3e, 0x02, 0xea, 0x00, 0x20, // MBC5: select ROM bank 2
                0xfa, 0x00, 0x40,             // read banked ROM byte
                0xea, 0x00, 0xc0,             // store at WRAM bank 0
                0x3e, 0x01, 0xe0, 0x4f,       // select VRAM bank 1
                0x3e, 0x55, 0xea, 0x00, 0x80,
                0x3e, 0x00, 0xe0, 0x4f,       // bank 0 remains independent
                0xfa, 0x00, 0x80, 0xea, 0x01, 0xc0,
                0x3e, 0x01, 0xe0, 0x4f,       // select bank 1 and verify its stored byte
                0xfa, 0x00, 0x80, 0xea, 0x02, 0xc0,
                0x3e, 0x03, 0xe0, 0x70,       // WRAM bank 3
                0x3e, 0x66, 0xea, 0x00, 0xd0,
                0x18, 0xfe);
        rom[0x8000] = 0x42; // bank 2 starts at ROM offset 0x8000
        GameBoyColorCore core = new GameBoyColorCore();
        core.load(rom);
        core.frame();
        assertEquals(0x42, core.readWramForTest(0));
        assertEquals(0x00, core.readWramForTest(1));
        assertEquals(0x55, core.readWramForTest(2));
        assertEquals(0x66, core.readWramForTest(3 * 0x1000));
    }

    @Test public void rejectsImagesWithoutCgbFlagAndUnsupportedController() {
        GameBoyColorCore core = new GameBoyColorCore();
        byte[] noCgb = cartridge(0, 2, 0x18, 0xfe);
        noCgb[0x143] = 0;
        try { core.load(noCgb); fail("Must reject a cartridge without its CGB flag"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Game Boy Color")); }
        byte[] unsupported = cartridge(0x05, 2, 0x18, 0xfe);
        try { core.load(unsupported); fail("Must reject unsupported cartridge controller"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Controle de cartucho")); }
    }
}
