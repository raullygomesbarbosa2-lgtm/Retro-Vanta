package com.retrovanta;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SnesCoreSmokeTest {
    private byte[] cartridge(int[] program) {
        byte[] rom=new byte[0x8000];
        byte[] name="RV HOME TEST".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(name,0,rom,0x7fc0,name.length);
        rom[0x7fd5]=0x20; // LoROM, no enhancement chip
        rom[0x7fd7]=5;    // 32 KiB
        for(int i=0;i<program.length;i++)rom[i]=(byte)program[i];
        rom[0x7ffc]=0x00;rom[0x7ffd]=(byte)0x80; // reset vector $8000
        return rom;
    }

    @Test public void generatedLoRomExecutesCpuAndRendersBackdropPalette() {
        SnesCore core=new SnesCore();
        core.load(cartridge(new int[]{
                0xa9,0x0f,             // LDA #$0F: release forced blank, full brightness
                0x8d,0x00,0x21,        // STA $2100
                0xa9,0x00,             // LDA #$00: CGRAM color 0
                0x8d,0x21,0x21,        // STA $2121
                0xa9,0x1f,             // red, SNES 15-bit color
                0x8d,0x22,0x21,        // CGRAM low byte
                0xa9,0x00,
                0x8d,0x22,0x21,        // CGRAM high byte
                0x80,0xfe              // BRA to itself
        }));
        assertEquals("RV HOME TEST",core.getCartridgeTitle());
        int[] frame=core.frame();
        assertEquals(256*224,frame.length);
        assertEquals(0xffff0000,frame[0]);
        assertEquals(0xffff0000,frame[frame.length-1]);
    }

    @Test public void autoJoypadInputCanBeReadByCartridgeCpu() {
        SnesCore core=new SnesCore();
        core.load(cartridge(new int[]{
                0xad,0x18,0x42,             // LDA $4218 (auto-joypad low byte)
                0x8f,0x00,0x00,0x7e,        // STA $7E:0000
                0xad,0x19,0x42,             // LDA $4219 (auto-joypad high byte)
                0x8f,0x01,0x00,0x7e,        // STA $7E:0001
                0x80,0xf0                  // BRA to the input poll sequence
        }));
        core.setButtons(SnesCore.BUTTON_A|SnesCore.BUTTON_START);
        core.frame();
        assertEquals(SnesCore.BUTTON_START,core.readWramForTest(0));
        assertEquals(SnesCore.BUTTON_A>>>8,core.readWramForTest(1));
        core.setButtons(SnesCore.BUTTON_LEFT);
        core.frame();
        assertEquals(SnesCore.BUTTON_LEFT,core.readWramForTest(0));
        assertEquals(0,core.readWramForTest(1));
    }

    @Test public void emulationModeDirectPageArithmeticAndWramAccess() {
        SnesCore core=new SnesCore();
        core.load(cartridge(new int[]{
                0xa9,0x11,       // LDA #$11
                0x85,0x00,       // STA $00 (direct page)
                0xe6,0x00,       // INC $00
                0xa5,0x00,       // LDA $00
                0x8f,0x00,0x00,0x7e, // STA $7E:0000
                0x80,0xfe        // BRA to itself
        }));
        core.frame();
        assertEquals(0x12,core.readWramForTest(0));
    }

    @Test public void generatedCartridgeUploadsAndScrollsMode0FourBitTiles() {
        java.util.ArrayList<Integer> code=new java.util.ArrayList<>();
        writeIo(code,0x2100,0x0f); // full brightness, leave forced blank
        writeIo(code,0x2105,0x00); // Mode 0
        writeIo(code,0x2107,0x04); // BG1 map at VRAM byte $0800, 32x32
        writeIo(code,0x210b,0x00); // BG1 character base at VRAM $0000
        writeIo(code,0x212c,0x01); // enable BG1 on main screen
        writeIo(code,0x2121,0x01); // CGRAM color 1
        writeIo(code,0x2122,0x1f);writeIo(code,0x2122,0x00); // palette 0: red
        writeIo(code,0x2121,0x11); // palette 1, color 1
        writeIo(code,0x2122,0x00);writeIo(code,0x2122,0x7c); // blue
        writeIo(code,0x2115,0x80); // increment VRAM word address after high byte
        writeIo(code,0x2116,0x00);writeIo(code,0x2117,0x00);
        // Tile 0 remains transparent. Tile 1: plane 0 sets row 0, yielding
        // palette color index 1 across its first row.
        for(int i=0;i<16;i++)emit(code,0xa9,0,0x8d,0x18,0x21,0xa9,0,0x8d,0x19,0x21); // tile 0
        for(int i=0;i<16;i++){
            int plane0=(i==0)?0xff:0;
            emit(code,0xa9,plane0,0x8d,0x18,0x21,0xa9,0,0x8d,0x19,0x21);
        }
        writeIo(code,0x2116,0x00);writeIo(code,0x2117,0x04); // map starts at word $0400
        writeIo(code,0x2118,0x01);writeIo(code,0x2119,0x04); // tile 1, palette 1
        writeIo(code,0x210d,0x08);writeIo(code,0x210d,0x00); // horizontal scroll 8 px
        emit(code,0x80,0xfe);
        int[] program=new int[code.size()];for(int i=0;i<program.length;i++)program[i]=code.get(i);
        SnesCore core=new SnesCore();core.load(cartridge(program));int[] frame=core.frame();
        assertEquals("scroll exposes transparent next tile at left",0xff000000,frame[0]);
        assertEquals("tilemap palette attribute selects CGRAM palette 1",0xff0000ff,frame[255]);
        assertEquals("tile row below the uploaded row remains transparent",0xff000000,frame[256]);
    }

    private static void writeIo(java.util.List<Integer> code,int address,int value){emit(code,0xa9,value,0x8d,address&255,(address>>>8)&255);}
    private static void emit(java.util.List<Integer> code,int... bytes){for(int b:bytes)code.add(b&255);}

    @Test public void refusesCartridgesOutsideExplicitLoRomProfile() {
        SnesCore core=new SnesCore();
        byte[] bad=cartridge(new int[]{0x80,0xfe});
        bad[0x7fd5]=0x22; // enhancement/chip mapping, not standard LoROM
        try { core.load(bad); fail("Unsupported map mode must be rejected"); }
        catch(IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("LoROM padrão")); }
    }

    @Test public void refusesTruncatedAndInvalidResetVectorImages() {
        SnesCore core=new SnesCore();
        try { core.load(new byte[0x7fff]); fail("Short image must be rejected"); }
        catch(IllegalArgumentException expected) { /* expected */ }
        byte[] bad=cartridge(new int[]{0x80,0xfe}); bad[0x7ffc]=0;bad[0x7ffd]=0x20;
        try { core.load(bad); fail("Invalid reset vector must be rejected"); }
        catch(IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Vetor de reset")); }
    }
}
