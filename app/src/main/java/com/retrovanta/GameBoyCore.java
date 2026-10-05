package com.retrovanta;

import java.util.Arrays;

/** Small, original DMG interpreter prototype for the Retro Vanta project. */
final class GameBoyCore {
    static final int WIDTH = 160, HEIGHT = 144;
    private static final int[] SHADES = {0xffe5f3d1, 0xffa8c686, 0xff527a54, 0xff243b3b};
    private byte[] rom = new byte[0];
    private final byte[] vram = new byte[0x2000], wram = new byte[0x2000];
    private final byte[] eram = new byte[0x8000], oam = new byte[0xa0], io = new byte[0x80];
    private final int[] pixels = new int[WIDTH * HEIGHT];
    private int a, f, b, c, d, e, h, l, sp, pc;
    private int ie, romBank = 1, ramBank, mbcMode, ramEnabled, pressed, cyclesInFrame, mapperType;
    private boolean ime, halted;
    private String cartridgeTitle = "Game Boy";

    void load(byte[] image) {
        if (image == null || image.length < 0x150) throw new IllegalArgumentException("Arquivo pequeno ou inválido para uma ROM de Game Boy.");
        if (image.length > 8 * 1024 * 1024) throw new IllegalArgumentException("A ROM passa do limite inicial de 8 MB.");
        int cartridge = image[0x147] & 255;
        if (cartridge != 0x00 && cartridge != 0x01 && cartridge != 0x02 && cartridge != 0x03)
            throw new IllegalArgumentException(String.format("Controle de cartucho 0x%02X ainda não é compatível nesta versão.", cartridge));
        if ((image[0x143] & 255) == 0xc0)
            throw new IllegalArgumentException("Este jogo exige Game Boy Color; o núcleo colorido ainda não está pronto.");
        mapperType = cartridge;
        cartridgeTitle = readTitle(image);
        rom = Arrays.copyOf(image, image.length);
        Arrays.fill(vram, (byte) 0); Arrays.fill(wram, (byte) 0);
        Arrays.fill(eram, (byte) 0xff); Arrays.fill(oam, (byte) 0); Arrays.fill(io, (byte) 0);
        a=0x01; f=0xb0; b=0x00; c=0x13; d=0x00; e=0xd8; h=0x01; l=0x4d;
        sp=0xfffe; pc=0x0100; ie=0; romBank=1; ramBank=0; mbcMode=0; ramEnabled=0; ime=false; halted=false;
        io[0x00]=(byte)0xcf; io[0x40]=(byte)0x91; io[0x47]=(byte)0xfc;
        io[0x48]=(byte)0xff; io[0x49]=(byte)0xff; io[0x0f]=(byte)0xe1;
    }

    String getCartridgeTitle() { return cartridgeTitle; }

    private String readTitle(byte[] image) {
        StringBuilder title = new StringBuilder();
        for (int i=0x134; i<0x144; i++) {
            int ch=image[i]&255;
            if (ch==0) break;
            if (ch>=32 && ch<=126) title.append((char)ch);
        }
        String value=title.toString().trim();
        return value.isEmpty() ? "Game Boy (sem título no cabeçalho)" : value;
    }

    void setButtons(int bits) { pressed = bits & 0xff; }

    int[] frame() {
        int spent = 0, guard = 0;
        while (spent < 70224 && guard++ < 50000) spent += step();
        cyclesInFrame += spent;
        drawBackground();
        return pixels;
    }

    private int step() {
        if (rom.length == 0 || halted) return 4;
        int op = fetch();
        int x = op >>> 6, y = (op >>> 3) & 7, z = op & 7, p = y >>> 1, q = y & 1;
        if (x == 0) {
            switch (z) {
                case 0:
                    if (y == 0) return 4;
                    if (y == 1) { int addr=fetch16(); write(addr, sp & 255); write((addr+1)&65535, sp>>>8); return 20; }
                    if (y == 2) { fetch(); halted=true; return 4; }
                    if (y == 3) { pc=(pc+(byte)fetch())&65535; return 12; }
                    int off=(byte)fetch(); if (condition(y-4)) { pc=(pc+off)&65535; return 12; } return 8;
                case 1:
                    if (q==0) { setPair(p, fetch16()); return 12; }
                    setHL(addHL(pair(p))); return 8;
                case 2: {
                    int addr = p==0 ? pair(0) : p==1 ? pair(1) : hl();
                    if (q==0) write(addr,a); else a=read(addr);
                    if (p==2) setHL((hl()+1)&65535); if (p==3) setHL((hl()-1)&65535);
                    return 8;
                }
                case 3: setPair(p, (pair(p) + (q==0 ? 1 : -1)) & 65535); return 8;
                case 4: { int v=inc8(reg(y)); putReg(y,v); return y==6 ? 12 : 4; }
                case 5: { int v=dec8(reg(y)); putReg(y,v); return y==6 ? 12 : 4; }
                case 6: putReg(y,fetch()); return y==6 ? 12 : 8;
                case 7: misc(y); return 4;
            }
        } else if (x == 1) {
            if (y==6 && z==6) { halted=true; return 4; }
            putReg(y,reg(z)); return (y==6 || z==6) ? 8 : 4;
        } else if (x == 2) {
            alu(y,reg(z)); return z==6 ? 8 : 4;
        } else {
            switch (z) {
                case 0:
                    if (y<4) { if(condition(y)) { pc=pop(); return 20; } return 8; }
                    if (y==4) { write(0xff00|fetch(),a); return 12; }
                    if (y==5) { addSP((byte)fetch()); return 16; }
                    if (y==6) { a=read(0xff00|fetch()); return 12; }
                    setHL(addSP((byte)fetch())); return 12;
                case 1:
                    if (q==0) { setPair2(p,pop()); return 12; }
                    if (p==0) { pc=pop(); return 16; }
                    if (p==1) { pc=pop(); ime=true; return 16; }
                    if (p==2) { pc=hl(); return 4; }
                    sp=hl(); return 8;
                case 2:
                    if (y<4) { int addr=fetch16(); if(condition(y)) { pc=addr; return 16; } return 12; }
                    if (y==4) { write(0xff00|c,a); return 8; }
                    if (y==5) { write(fetch16(),a); return 16; }
                    if (y==6) { a=read(0xff00|c); return 8; }
                    a=read(fetch16()); return 16;
                case 3:
                    if (y==0) { pc=fetch16(); return 16; }
                    if (y==1) return cb(fetch());
                    if (y==6) { ime=false; return 4; }
                    if (y==7) { ime=true; return 4; }
                    return 4;
                case 4:
                    if (y<4) { int addr=fetch16(); if(condition(y)) { push(pc); pc=addr; return 24; } return 12; }
                    return 4;
                case 5:
                    if (q==0) { push(pair2(p)); return 16; }
                    if (p==0) { int addr=fetch16(); push(pc); pc=addr; return 24; }
                    return 4;
                case 6: alu(y,fetch()); return 8;
                case 7: push(pc); pc=y*8; return 16;
            }
        }
        return 4;
    }

    private int cb(int op) {
        int x=op>>>6, y=(op>>>3)&7, z=op&7, v=reg(z);
        if(x==0) {
            int carry=0, out=v;
            switch(y) {
                case 0: carry=v>>>7; out=((v<<1)|carry)&255; break;
                case 1: carry=v&1; out=(v>>>1)|(carry<<7); break;
                case 2: { int old=(f>>>4)&1; carry=v>>>7; out=((v<<1)|old)&255; break; }
                case 3: { int old=(f>>>4)&1; carry=v&1; out=(v>>>1)|(old<<7); break; }
                case 4: carry=v>>>7; out=(v<<1)&255; break;
                case 5: carry=v&1; out=(v>>>1)| (v&0x80); break;
                case 6: out=((v<<4)|(v>>>4))&255; break;
                case 7: carry=v&1; out=v>>>1; break;
            }
            putReg(z,out); f=(out==0?0x80:0)|(carry<<4); return z==6?16:8;
        }
        if(x==1) { f=(f&0x10)|0x20|((v&(1<<y))==0?0x80:0); return z==6?12:8; }
        if(x==2) putReg(z,v&~(1<<y)); else putReg(z,v|(1<<y));
        return z==6?16:8;
    }

    private void misc(int op) {
        switch(op) {
            case 0: { int bit=a>>>7; a=((a<<1)|bit)&255; f=bit<<4; break; }
            case 1: { int bit=a&1; a=(a>>>1)|(bit<<7); f=bit<<4; break; }
            case 2: { int old=(f>>>4)&1, bit=a>>>7; a=((a<<1)|old)&255; f=bit<<4; break; }
            case 3: { int old=(f>>>4)&1, bit=a&1; a=(a>>>1)|(old<<7); f=bit<<4; break; }
            case 4: { int v=a; int adj=0; boolean carry=(f&0x10)!=0;
                if((f&0x40)==0) { if((f&0x20)!=0 || (v&15)>9) adj|=6; if(carry || v>0x99) { adj|=0x60; carry=true; } a=(v+adj)&255; }
                else { if((f&0x20)!=0) adj|=6; if(carry) adj|=0x60; a=(v-adj)&255; }
                f=(f&0x40)|(a==0?0x80:0)|(carry?0x10:0); break; }
            case 5: a^=255; f=(f&0x90)|0x60; break;
            case 6: f=(f&0x80)|0x10; break;
            case 7: f=(f&0x80)|((f&0x10)==0?0x10:0); break;
        }
    }

    private void alu(int op,int v) {
        int carry=(f>>>4)&1, result;
        switch(op) {
            case 0: result=a+v; f=((result&255)==0?0x80:0)|(((a&15)+(v&15)>15)?0x20:0)|(result>255?0x10:0); a=result&255; break;
            case 1: result=a+v+carry; f=((result&255)==0?0x80:0)|(((a&15)+(v&15)+carry>15)?0x20:0)|(result>255?0x10:0); a=result&255; break;
            case 2: result=a-v; f=0x40|((result&255)==0?0x80:0)|((a&15)<(v&15)?0x20:0)|(a<v?0x10:0); a=result&255; break;
            case 3: result=a-v-carry; f=0x40|((result&255)==0?0x80:0)|((a&15)<((v&15)+carry)?0x20:0)|(a<v+carry?0x10:0); a=result&255; break;
            case 4: a&=v; f=(a==0?0x80:0)|0x20; break;
            case 5: a^=v; f=a==0?0x80:0; break;
            case 6: a|=v; f=a==0?0x80:0; break;
            case 7: result=a-v; f=0x40|((result&255)==0?0x80:0)|((a&15)<(v&15)?0x20:0)|(a<v?0x10:0); break;
        }
    }

    private int inc8(int v) { int n=(v+1)&255; f=(f&0x10)|(n==0?0x80:0)|((v&15)==15?0x20:0); return n; }
    private int dec8(int v) { int n=(v-1)&255; f=(f&0x10)|0x40|(n==0?0x80:0)|((v&15)==0?0x20:0); return n; }
    private int addHL(int v) { int old=hl(), sum=old+v; f=(f&0x80)|(((old&0xfff)+(v&0xfff)>0xfff)?0x20:0)|(sum>0xffff?0x10:0); return sum&0xffff; }
    private int addSP(int value) { int old=sp, signed=(byte)value; int r=(old+signed)&65535; f=(((old&15)+(value&15)>15)?0x20:0)|(((old&255)+(value&255)>255)?0x10:0); sp=r; return r; }
    private boolean condition(int y) { switch(y&3) { case 0:return (f&0x80)==0; case 1:return (f&0x80)!=0; case 2:return (f&0x10)==0; default:return (f&0x10)!=0; } }
    private int reg(int r) { switch(r) { case 0:return b; case 1:return c; case 2:return d; case 3:return e; case 4:return h; case 5:return l; case 6:return read(hl()); default:return a; } }
    private void putReg(int r,int v) { v&=255; switch(r) { case 0:b=v;break; case 1:c=v;break; case 2:d=v;break; case 3:e=v;break; case 4:h=v;break; case 5:l=v;break; case 6:write(hl(),v);break; default:a=v;break; } }
    private int pair(int p) { switch(p) { case 0:return (b<<8)|c; case 1:return (d<<8)|e; case 2:return hl(); default:return sp; } }
    private void setPair(int p,int v) { v&=65535; switch(p) { case 0:b=v>>>8;c=v&255;break; case 1:d=v>>>8;e=v&255;break; case 2:setHL(v);break; default:sp=v;break; } }
    private int pair2(int p) { return p==3 ? ((a<<8)|f) : pair(p); }
    private void setPair2(int p,int v) { if(p==3) { a=(v>>>8)&255; f=v&0xf0; } else setPair(p,v); }
    private int hl() { return (h<<8)|l; }
    private void setHL(int v) { h=(v>>>8)&255;l=v&255; }
    private int fetch() { int v=read(pc); pc=(pc+1)&65535; return v; }
    private int fetch16() { int lo=fetch(); return lo|(fetch()<<8); }
    private void push(int v) { sp=(sp-1)&65535;write(sp,(v>>>8)&255);sp=(sp-1)&65535;write(sp,v&255); }
    private int pop() { int lo=read(sp);sp=(sp+1)&65535;int hi=read(sp);sp=(sp+1)&65535;return lo|(hi<<8); }

    private int read(int addr) {
        addr &= 65535;
        if(addr<0x4000) return addr<rom.length ? rom[addr]&255 : 0xff;
        if(addr<0x8000) { int bank=romBank; int ix=bank*0x4000+(addr-0x4000); return ix<rom.length?rom[ix]&255:0xff; }
        if(addr<0xa000) return vram[addr-0x8000]&255;
        if(addr<0xc000) { if(ramEnabled==0)return 0xff; int ix=(ramBank*0x2000)+(addr-0xa000);return ix<eram.length?eram[ix]&255:0xff; }
        if(addr<0xe000) return wram[addr-0xc000]&255;
        if(addr<0xfe00) return wram[addr-0xe000]&255;
        if(addr<0xfea0) return oam[addr-0xfe00]&255;
        if(addr<0xff00) return 0xff;
        if(addr==0xff00) { int select=io[0]&0x30, low=15; if((select&0x10)==0)low &= (~pressed)&15; if((select&0x20)==0)low &= (~(pressed>>>4))&15; return 0xc0|select|low; }
        if(addr==0xffff)return ie;
        return io[addr-0xff00]&255;
    }

    private void write(int addr,int value) {
        addr &= 65535; value &= 255;
        if(addr<0x2000) { if(rom.length>0x147 && (rom[0x147]&255)>=1 && (rom[0x147]&255)<=3) ramEnabled=(value&15)==10?1:0; return; }
        if(addr<0x4000) { romBank=(romBank&0x60)|(value&31); if((romBank&31)==0)romBank++; return; }
        if(addr<0x6000) { if(mbcMode==0)romBank=(romBank&31)|((value&3)<<5); else ramBank=value&3; return; }
        if(addr<0x8000) { mbcMode=value&1; return; }
        if(addr<0xa000) { vram[addr-0x8000]=(byte)value; return; }
        if(addr<0xc000) { if(ramEnabled!=0) { int ix=ramBank*0x2000+addr-0xa000;if(ix<eram.length)eram[ix]=(byte)value; } return; }
        if(addr<0xe000) { wram[addr-0xc000]=(byte)value; return; }
        if(addr<0xfe00) { wram[addr-0xe000]=(byte)value; return; }
        if(addr<0xfea0) { oam[addr-0xfe00]=(byte)value; return; }
        if(addr<0xff00)return;
        if(addr==0xffff) { ie=value;return; }
        if(addr==0xff04) { io[4]=0;return; }
        if(addr==0xff46) { io[0x46]=(byte)value;int base=value<<8;for(int i=0;i<160;i++)oam[i]=(byte)read((base+i)&65535);return; }
        if(addr==0xff44) { io[0x44]=0;return; }
        io[addr-0xff00]=(byte)value;
    }

    private void drawBackground() {
        int lcdc=io[0x40]&255;
        if((lcdc&0x80)==0) { Arrays.fill(pixels,SHADES[0]);return; }
        int mapBase=(lcdc&8)!=0?0x1c00:0x1800;
        int palette=io[0x47]&255;
        for(int y=0;y<HEIGHT;y++) for(int x=0;x<WIDTH;x++) {
            int tileX=(x>>>3)&31, tileY=(y>>>3)&31;
            int tile=vram[mapBase+tileY*32+tileX]&255;
            int dataAddr;
            if((lcdc&0x10)!=0) dataAddr=tile*16;
            else dataAddr=0x1000+(byte)tile*16;
            int row=y&7, bit=7-(x&7);
            int lo=vram[(dataAddr+row*2)&0x1fff]&255, hi=vram[(dataAddr+row*2+1)&0x1fff]&255;
            int color=((lo>>>bit)&1)|(((hi>>>bit)&1)<<1);
            int shade=(palette >>> (color*2))&3;
            pixels[y*WIDTH+x]=SHADES[shade];
        }
    }
}
