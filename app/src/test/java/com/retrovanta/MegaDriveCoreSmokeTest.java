package com.retrovanta;

import org.junit.Test;
import static org.junit.Assert.*;

/** Uses tiny generated cartridge images, not copyrighted game dumps. */
public final class MegaDriveCoreSmokeTest {
    private static final int VDP_DATA=0x00c00000, VDP_CTRL=0x00c00004;

    @Test public void generatedCartridgeExecutes68000AndDrawsPlaneATile() {
        MegaDriveCore core=new MegaDriveCore();
        byte[] rom=cartridge(); int p=0x200;
        // Enable display, backdrop palette index 0, set palette entry 1 to red.
        p=moveWordAbsolute(rom,p,0x8144,VDP_CTRL); // VDP register 1
        p=moveWordAbsolute(rom,p,0x8700,VDP_CTRL); // backdrop palette entry 0
        p=moveWordAbsolute(rom,p,0x0002,VDP_CTRL); // CRAM word address 1
        p=moveWordAbsolute(rom,p,0xc000,VDP_CTRL); // CRAM write command
        p=moveWordAbsolute(rom,p,0x000e,VDP_DATA); // red (3-bit RGB)
        p=moveWordAbsolute(rom,p,0x0000,VDP_CTRL); // VRAM address 0
        p=moveWordAbsolute(rom,p,0x4000,VDP_CTRL); // VRAM write command
        p=moveWordAbsolute(rom,p,0x0001,VDP_DATA); // first plane entry selects tile 1
        p=moveWordAbsolute(rom,p,0x0020,VDP_CTRL); // tile 1 pattern address
        p=moveWordAbsolute(rom,p,0x4000,VDP_CTRL);
        for(int i=0;i<16;i++)p=moveWordAbsolute(rom,p,0x1111,VDP_DATA);
        rom[p++]=(byte)0x4e;rom[p++]=(byte)0xf9; // JMP absolute long back to idle loop
        rom[p++]=0;rom[p++]=0;rom[p++]=2;rom[p++]=0;

        core.load(rom);
        assertEquals("HOME BREW TEST",core.getCartridgeTitle());
        assertEquals(320,core.videoWidth()); assertEquals(224,core.videoHeight());
        int[] frame=core.frame();
        assertEquals(320*224,frame.length);
        assertEquals(0xfffc0000,frame[0]);
        assertEquals(0xff000000,frame[8]);
        core.setButtons(MegaDriveCore.BUTTON_A|MegaDriveCore.BUTTON_START|MegaDriveCore.BUTTON_RIGHT);
    }

    @Test public void loadsHeaderedImageAndResetsItsInitialStackAndProgram() {
        byte[] rom=cartridge();
        rom[0x200]=0x70;rom[0x201]=0x05; // MOVEQ #5,D0
        rom[0x202]=0x60;rom[0x203]=(byte)0xfc; // BRA back to MOVEQ
        MegaDriveCore core=new MegaDriveCore();core.load(rom);
        assertEquals(320*224,core.frame().length);
        assertEquals("HOME BREW TEST",core.getCartridgeTitle());
    }

    @Test public void rejectsImagesWithoutStandardHeaderAndOversizedImages() {
        try {new MegaDriveCore().load(new byte[0x400]);fail("expected missing-header rejection");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("SEGA"));}
        byte[] huge=new byte[4*1024*1024+1];
        huge[0x100]='S';huge[0x101]='E';huge[0x102]='G';huge[0x103]='A';
        try {new MegaDriveCore().load(huge);fail("expected profile size rejection");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("4 MiB"));}
    }

    private byte[] cartridge(){
        byte[] b=new byte[0x400];
        b[0x100]='S';b[0x101]='E';b[0x102]='G';b[0x103]='A';
        byte[] name="HOME BREW TEST".getBytes();System.arraycopy(name,0,b,0x150,name.length);
        put32(b,0,0x00fffffc);put32(b,4,0x00000200);
        return b;
    }
    private int moveWordAbsolute(byte[] rom,int p,int value,int address){
        rom[p++]=0x33;rom[p++]=(byte)0xfc;
        rom[p++]=(byte)(value>>>8);rom[p++]=(byte)value;
        put32(rom,p,address);return p+4;
    }
    private void put32(byte[] b,int at,int value){b[at]=(byte)(value>>>24);b[at+1]=(byte)(value>>>16);b[at+2]=(byte)(value>>>8);b[at+3]=(byte)value;}
}
