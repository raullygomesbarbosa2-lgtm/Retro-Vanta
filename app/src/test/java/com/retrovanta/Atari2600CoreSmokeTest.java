package com.retrovanta;

import org.junit.Test;
import static org.junit.Assert.*;

public final class Atari2600CoreSmokeTest {
    @Test public void rawTwoKiBCartridgeRunsAndProducesFrame() {
        Atari2600Core core=new Atari2600Core();
        core.load(tinyCartridge(2048));
        assertEquals(160,core.videoWidth());
        assertEquals(192,core.videoHeight());
        assertTrue(core.getCartridgeTitle().contains("2 KiB"));
        int[] frame=core.frame();
        assertEquals(160*192,frame.length);
        assertNotEquals(0xff000000,frame[0]);
        core.setButtons(Atari2600Core.BUTTON_UP|Atari2600Core.BUTTON_FIRE);
    }

    @Test public void rawFourKiBCartridgeLoads() {
        Atari2600Core core=new Atari2600Core();
        core.load(tinyCartridge(4096));
        assertTrue(core.getCartridgeTitle().contains("4 KiB"));
        assertEquals(160*192,core.frame().length);
    }

    @Test public void rejectsOtherSizesAndHeaders() {
        try { new Atari2600Core().load(new byte[2048+16]); fail("Unexpected container/header accepted"); }
        catch(IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("2 KiB or 4 KiB")); }
    }

    private static byte[] tinyCartridge(int size) {
        byte[] rom=new byte[size];
        // LDA #$4e; STA COLUBK; JMP $f005. The 2K/4K vectors are at the end.
        int[] code={0xa9,0x4e,0x85,0x09,0x4c,0x05,0xf0};
        for(int i=0;i<code.length;i++)rom[i]=(byte)code[i];
        int vector=size-4;
        rom[vector]=(byte)0x00; rom[vector+1]=(byte)0xf0;
        rom[vector+2]=(byte)0x00; rom[vector+3]=(byte)0xf0;
        return rom;
    }
}
