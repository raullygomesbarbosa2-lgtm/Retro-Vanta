package com.retrovanta;

import org.junit.Test;
import static org.junit.Assert.*;

/** Generated raw-cartridge tests exercise the implemented slice, not commercial games. */
public final class GbaCoreSmokeTest {
    private static byte[] cartridge(){
        byte[] rom=new byte[0x400];
        // Reset branch from 0x08000000 to a tiny Thumb program after the 192-byte header.
        put16(rom,0,0xe05e);
        byte[] title="RV GBA TEST".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(title,0,rom,0xa0,title.length);
        rom[0xb2]=(byte)0x96;
        // LDR r0,[pc,#12] -> DISPCNT; enable mode 3. Then write red pixel 0 and loop.
        int[] code={0x4803,0x0000,0x2103,0x8001,0x4802,0x211f,0x8001,0xe7fe};
        for(int i=0;i<code.length;i++)put16(rom,0xc0+i*2,code[i]);
        put32(rom,0xd0,0x04000000); // IO
        put32(rom,0xd4,0x06000000); // VRAM
        int sum=0;for(int i=0xa0;i<=0xbc;i++)sum=(sum-(rom[i]&255))&255;
        rom[0xbd]=(byte)((sum-0x19)&255);
        return rom;
    }
    private static void put16(byte[] a,int p,int x){a[p]=(byte)x;a[p+1]=(byte)(x>>>8);}
    private static void put32(byte[] a,int p,int x){put16(a,p,x);put16(a,p+2,x>>>16);}

    @Test public void executesThumbAndRendersMode3PixelFromGeneratedRawRom(){
        GbaCore core=new GbaCore();core.load(cartridge());
        assertEquals("RV GBA TEST",core.getCartridgeTitle());
        assertEquals(240,core.videoWidth());assertEquals(160,core.videoHeight());
        int[] frame=core.frame();assertEquals(240*160,frame.length);
        assertEquals(0xffff0000,frame[0]);assertEquals(0xff000000,frame[1]);
    }
    @Test public void mapsOnScreenControlsToActiveLowGbaKeys(){
        GbaCore core=new GbaCore();core.load(cartridge());
        assertEquals(0x3ff,core.readKeyInputForTest());
        core.setButtons(GbaCore.BUTTON_A|GbaCore.BUTTON_RIGHT|GbaCore.BUTTON_START);
        assertEquals(0x3ff&~(1|8|16),core.readKeyInputForTest());
    }
    @Test public void rejectsMalformedSizeFixedHeaderAndChecksum(){
        GbaCore core=new GbaCore();
        try{core.load(new byte[0xbf]);fail("short image rejected");}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("192-byte"));}
        byte[] bad=cartridge();bad[0xb2]=0;
        try{core.load(bad);fail("bad fixed header byte rejected");}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("0xB2"));}
        bad=cartridge();bad[0xbd]^=1;
        try{core.load(bad);fail("bad header checksum rejected");}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("checksum"));}
    }
}
