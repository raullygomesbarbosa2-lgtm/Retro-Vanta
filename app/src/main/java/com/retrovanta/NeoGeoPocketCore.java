package com.retrovanta;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Stand-alone, deliberately bounded monochrome NGP bring-up core.
 *
 * <p>This is not a TLCS-900H implementation. The CPU is a small, documented
 * diagnostic bytecode interpreter used to exercise header loading, memory,
 * input and the monochrome tile renderer with generated test cartridges. It
 * must not be represented as running ordinary commercial NGP programs.</p>
 */
final class NeoGeoPocketCore implements EmulatorCore {
    static final int WIDTH = 160, HEIGHT = 152;
    static final int ROM_BASE = 0x200000;
    private static final int MAX_ROM = 8 * 1024 * 1024;
    private static final int[] SHADES = {0xfff8f8f0, 0xffb8c8a0, 0xff687858, 0xff182820};

    // Tiny test/bring-up instruction set. All immediates are little-endian.
    // 00 NOP; 10/11/12 load A/B/C,imm8; 20 store A,[addr16]; 21 load A,[addr16];
    // 30 jump addr24; 31 jump-if-A-zero addr24; 40 add A,imm8; FF halt.
    private byte[] rom = new byte[0];
    private final byte[] ram = new byte[0x10000];
    private final int[] pixels = new int[WIDTH * HEIGHT];
    private int pc, a, b, c, pressed, systemCode;
    private boolean halted;
    private String title = "Neo Geo Pocket";

    @Override public void load(byte[] image) {
        if (image == null || image.length < 0x40) throw new IllegalArgumentException("Arquivo pequeno ou inválido para um cartucho Neo Geo Pocket.");
        if (image.length > MAX_ROM) throw new IllegalArgumentException("A ROM passa do limite inicial de 8 MB.");
        int code = image[0x23] & 0xff;
        if (code != 0x00 && code != 0x10) throw new IllegalArgumentException(String.format("Código de sistema NGP/NGPC desconhecido: 0x%02X.", code));
        systemCode = code;
        title = readTitle(image);
        rom = Arrays.copyOf(image, image.length);
        Arrays.fill(ram, (byte) 0);
        Arrays.fill(pixels, SHADES[0]);
        long entry = (image[0x1c] & 255L) | ((image[0x1d] & 255L) << 8)
                | ((image[0x1e] & 255L) << 16) | ((image[0x1f] & 255L) << 24);
        // Zero and out-of-ROM vectors use the canonical first byte after header,
        // useful for homebrew/test images without a populated start vector.
        if (entry == 0 || entry < ROM_BASE || entry - ROM_BASE >= rom.length) entry = ROM_BASE + 0x40;
        pc = (int) (entry - ROM_BASE);
        a = b = c = pressed = 0;
        halted = false;
    }

    private static String readTitle(byte[] image) {
        StringBuilder out = new StringBuilder();
        for (int i = 0x24; i < 0x30; i++) {
            int ch = image[i] & 255;
            if (ch == 0) break;
            if (ch >= 32 && ch <= 126) out.append((char) ch);
        }
        String value = out.toString().trim();
        return value.isEmpty() ? "Neo Geo Pocket (sem título no cabeçalho)" : value;
    }

    @Override public String getCartridgeTitle() { return title; }
    @Override public int videoWidth() { return WIDTH; }
    @Override public int videoHeight() { return HEIGHT; }

    /** Button bitfield is exposed at I/O address B000; bits 0..7 are caller-defined. */
    @Override public void setButtons(int bits) { pressed = bits & 0xff; }

    @Override public int[] frame() {
        if (rom.length == 0) return pixels;
        // Frame pacing is intentionally bounded; diagnostics cannot execute forever.
        for (int i = 0; i < 20_000 && !halted; i++) step();
        drawBackground();
        return pixels;
    }

    private int fetch8() {
        if (pc < 0 || pc >= rom.length) { halted = true; return 0xff; }
        return rom[pc++] & 255;
    }
    private int fetch16() { int lo = fetch8(); return lo | (fetch8() << 8); }
    private int fetch24() { int lo = fetch8(); return lo | (fetch8() << 8) | (fetch8() << 16); }

    private void step() {
        int op = fetch8();
        switch (op) {
            case 0x00: break;
            case 0x10: a = fetch8(); break;
            case 0x11: b = fetch8(); break;
            case 0x12: c = fetch8(); break;
            case 0x20: write(fetch16(), a); break;
            case 0x21: a = read(fetch16()); break;
            case 0x30: pc = checkedPc(fetch24()); break;
            case 0x31: { int dest = fetch24(); if (a == 0) pc = checkedPc(dest); break; }
            case 0x40: a = (a + fetch8()) & 255; break;
            case 0xff: halted = true; break;
            default: halted = true; break; // unknown instruction traps, never runs unbounded
        }
    }

    private int checkedPc(int address) {
        if (address < ROM_BASE || address - ROM_BASE >= rom.length) { halted = true; return 0; }
        return address - ROM_BASE;
    }
    private int read(int address) {
        address &= 0xffff;
        if (address == 0xb000) return pressed;
        if (address >= 0x8000 && address <= 0x9fff) return ram[address] & 255;
        return ram[address] & 255;
    }
    private void write(int address, int value) {
        address &= 0xffff;
        // 8000-8fff tile patterns (32 bytes/tile, 2bpp); 9000-93ff tilemap.
        if ((address >= 0x8000 && address <= 0x8fff) || (address >= 0x9000 && address <= 0x93ff))
            ram[address] = (byte) value;
    }

    private void drawBackground() {
        // A 32x32 tile map begins at 9000; 8x8, 2bpp tiles begin at 8000.
        // Pattern bits are planar: first 8 bytes low plane, next 8 high plane;
        // subsequent 16 bytes are accepted as a second tile bank, indexed by ID.
        for (int y = 0; y < HEIGHT; y++) {
            int ty = y >>> 3, py = y & 7;
            for (int x = 0; x < WIDTH; x++) {
                int tx = x >>> 3, px = x & 7;
                int mapIndex = 0x9000 + ty * 32 + tx;
                int tile = ram[mapIndex] & 255;
                int base = 0x8000 + tile * 16;
                int bit = 7 - px;
                int low = (ram[base + py] >>> bit) & 1;
                int high = (ram[base + 8 + py] >>> bit) & 1;
                pixels[y * WIDTH + x] = SHADES[low | (high << 1)];
            }
        }
    }

    // Package-private test hooks intentionally limited to observable bring-up state.
    int readMemoryForTest(int address) { return read(address); }
    int programCounterForTest() { return pc; }
    int accumulatorForTest() { return a; }
}
