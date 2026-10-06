package com.retrovanta;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NesCoreSmokeTest {
    @Test public void nromExecutesCpuAndRendersFrame() {
        NesCore core = new NesCore();
        byte[] rom = new byte[16 + 0x4000];
        rom[0]='N'; rom[1]='E'; rom[2]='S'; rom[3]=0x1a;
        rom[4]=1; // NROM-128, CHR RAM
        int[] code = {0xa9,0x3f,0x8d,0x06,0x20,0xa9,0x00,0x8d,0x06,0x20,
                0xa9,0x01,0x8d,0x07,0x20,0x4c,0x00,0x80};
        for (int i=0;i<code.length;i++) rom[16+i]=(byte)code[i];
        rom[16+0x3ffc]=0x00; rom[16+0x3ffd]=(byte)0x80;
        core.load(rom);
        assertEquals(256,core.videoWidth());
        assertEquals(240,core.videoHeight());
        assertTrue(core.getCartridgeTitle().contains("Mapper 0"));
        int[] frame=core.frame();
        assertEquals(256*240,frame.length);
        assertEquals(0xff001fb2,frame[0]);
        core.setButtons(NesCore.BUTTON_A|NesCore.BUTTON_RIGHT);
    }

    @Test public void uxromLoadsAndRenders() {
        NesCore core = new NesCore();
        byte[] rom=new byte[16+0x8000];
        rom[0]='N';rom[1]='E';rom[2]='S';rom[3]=0x1a;rom[4]=2;rom[6]=0x20;
        int fixed=16+0x4000;rom[fixed]=0x4c;rom[fixed+1]=0x00;rom[fixed+2]=(byte)0xc0;
        rom[fixed+0x3ffc]=0x00;rom[fixed+0x3ffd]=(byte)0xc0;
        core.load(rom);
        assertTrue(core.getCartridgeTitle().contains("Mapper 2"));
        assertEquals(256*240,core.frame().length);
    }

    @Test public void rejectsUnsupportedMapper() {
        NesCore core = new NesCore();
        byte[] rom=new byte[16+0x4000];
        rom[0]='N';rom[1]='E';rom[2]='S';rom[3]=0x1a;rom[4]=1;rom[6]=0x10;
        try { core.load(rom); fail("Unsupported mapper was accepted"); }
        catch(IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Mapper")); }
    }
}
