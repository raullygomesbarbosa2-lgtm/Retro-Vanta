package com.retrovanta;

import org.junit.Test;
import static org.junit.Assert.*;

/** Smoke checks use generated homebrew bytes; no commercial ROM is required. */
public final class MasterSystemCoreSmokeTest {
    @Test public void generatedHomebrewProgramsVdpAndProducesPixels() {
        MasterSystemCore core=new MasterSystemCore();
        byte[] image=cartridge(new int[]{
                0x3e,0x0e,0xd3,0xbf, 0x3e,0x82,0xd3,0xbf, // name table base register 2
                0x3e,0x40,0xd3,0xbf, 0x3e,0x81,0xd3,0xbf, // display enabled, register 1
                0x3e,0x00,0xd3,0xbf, 0x3e,0xc0,0xd3,0xbf, // CRAM address 0
                0x3e,0x0c,0xd3,0xbe,                         // backdrop red
                0x3e,0x01,0xd3,0xbf, 0x3e,0xc0,0xd3,0xbf, // CRAM address 1
                0x3e,0x30,0xd3,0xbe,                         // palette color 1 green
                0x3e,0x00,0xd3,0xbf, 0x3e,0x40,0xd3,0xbf, // VRAM address 0, write
                0x3e,0xff,0xd3,0xbe, 0xaf,0xd3,0xbe,0xd3,0xbe,0xd3,0xbe, // tile row, color 1
                0x18,0xfe                                  // stable idle loop after VDP setup
        });
        core.load(image);
        assertEquals(256,core.videoWidth()); assertEquals(192,core.videoHeight());
        int[] pixels=core.frame();
        assertEquals(256*192,pixels.length);
        assertEquals(0xff00ff00,pixels[0]);
        assertEquals(0xffff0000,pixels[256]);
        core.setButtons(MasterSystemCore.BUTTON_A|MasterSystemCore.BUTTON_RIGHT);
    }

    @Test public void loadsSegaMapperCartridgeWithGeneratedProgram() {
        byte[] image=new byte[0x10000]; // four 16 KiB ROM blocks
        image[0]=(byte)0xc3; image[1]=0; image[2]=0; // JP 0000, valid idle loop
        MasterSystemCore core=new MasterSystemCore();
        core.load(image);
        assertEquals(256*192,core.frame().length);
        assertEquals("Sega Master System",core.getCartridgeTitle());
    }

    @Test public void rejectsEmptyImage() {
        try { new MasterSystemCore().load(new byte[0]); fail("Expected invalid cartridge rejection"); }
        catch(IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("ROM")); }
    }

    private byte[] cartridge(int[] program) {
        byte[] image=new byte[0x4000];
        for(int i=0;i<program.length;i++)image[i]=(byte)program[i];
        return image;
    }
}
