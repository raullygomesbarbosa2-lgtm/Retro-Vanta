package com.retrovanta;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Original, deliberately small GBA hardware slice: raw cartridge loading, a useful
 * Thumb/ARM7TDMI subset, basic memory-mapped input, and bitmap video modes 3/4/5.
 * Execution begins in Thumb state at the cartridge entry; ARM state and BIOS boot are
 * not implemented. This is not a complete GBA emulator.
 */
final class GbaCore implements EmulatorCore {
    static final int WIDTH = 240, HEIGHT = 160;
    // Controller values match the current on-screen controls: Right, Left, Up, Down,
    // A/X, B/Y, Select, Start. The GBA's separate L/R keys are not exposed by that UI.
    static final int BUTTON_RIGHT=1, BUTTON_LEFT=2, BUTTON_UP=4, BUTTON_DOWN=8,
            BUTTON_A=16, BUTTON_B=32, BUTTON_SELECT=64, BUTTON_START=128;
    private static final int MAX_ROM = 32 * 1024 * 1024;
    private byte[] rom = new byte[0];
    private final byte[] ewram=new byte[0x40000], iwram=new byte[0x8000], io=new byte[0x400];
    private final byte[] palette=new byte[0x400], vram=new byte[0x18000], oam=new byte[0x400];
    private final int[] pixels=new int[WIDTH*HEIGHT];
    private int[] bankedRom;
    private int r0,r1,r2,r3,r4,r5,r6,r7,sp,lr,pc;
    private int n,z,c,v,pressed,dispcnt;
    private String title="Game Boy Advance";

    @Override public void load(byte[] image) {
        if(image==null || image.length<0xc0) throw new IllegalArgumentException("GBA raw ROM is shorter than its 192-byte header.");
        if(image.length>MAX_ROM) throw new IllegalArgumentException("GBA ROM exceeds the supported 32 MiB cartridge limit.");
        if((image.length&3)!=0) throw new IllegalArgumentException("GBA ROM size must be a multiple of 4 bytes.");
        if((image[0xb2]&255)!=0x96) throw new IllegalArgumentException("Invalid GBA header fixed byte at 0xB2.");
        int checksum=0; for(int i=0xa0;i<=0xbc;i++) checksum=(checksum-(image[i]&255))&255;
        checksum=(checksum-0x19)&255;
        if((image[0xbd]&255)!=checksum) throw new IllegalArgumentException("Invalid GBA header checksum.");
        StringBuilder name=new StringBuilder();
        for(int i=0xa0;i<0xac;i++){int ch=image[i]&255;if(ch>=32&&ch<=126)name.append((char)ch);}
        title=name.toString().trim(); if(title.isEmpty()) title="GBA (untitled cartridge)";
        rom=Arrays.copyOf(image,image.length);
        Arrays.fill(ewram,(byte)0);Arrays.fill(iwram,(byte)0);Arrays.fill(io,(byte)0);
        Arrays.fill(palette,(byte)0);Arrays.fill(vram,(byte)0);Arrays.fill(oam,(byte)0);
        r0=r1=r2=r3=r4=r5=r6=r7=0; sp=0x03007f00;lr=0;pc=0x08000000;
        n=z=c=v=0;pressed=0;dispcnt=0;io[0x130]=io[0x131]=(byte)0xff;
    }
    @Override public String getCartridgeTitle(){return title;}
    @Override public int videoWidth(){return WIDTH;}
    @Override public int videoHeight(){return HEIGHT;}
    @Override public void setButtons(int bits){pressed=bits&0xff;}

    @Override public int[] frame(){
        if(rom.length!=0){int cycles=0,guard=0;while(cycles<280896&&guard++<100000){cycles+=stepThumb();}}
        render(); return pixels;
    }

    private int stepThumb(){
        int op=read16(pc);pc=(pc+2)&0x0fffffff;
        // Format 1: immediate/register shifts.
        if((op&0xe000)==0x0000 && (op&0x1800)!=0x1800){
            int kind=(op>>>11)&3,amount=(op>>>6)&31,rs=(op>>>3)&7,rd=op&7,val=reg(rs),out;
            if(kind==0){if(amount!=0)c=(val>>>(32-amount)&1);out=val<<amount;}
            else if(kind==1){if(amount==0)amount=32;c=(val>>>(amount-1))&1;out=amount==32?0:val>>>amount;}
            else {if(amount==0)amount=32;c=(val>>>(amount-1))&1;out=amount==32?(val<0?-1:0):val>>amount;}
            putReg(rd,out);nz(out);return 1;
        }
        // Add/subtract register or 3-bit immediate.
        if((op&0xf800)==0x1800){boolean imm=(op&0x0400)!=0,sub=(op&0x0200)!=0;int rs=(op>>>3)&7,rd=op&7;
            int a=reg(rs),b=imm?((op>>>6)&7):reg((op>>>6)&7);putReg(rd,sub?subFlags(a,b):addFlags(a,b));return 1;}
        // MOV/CMP/ADD/SUB immediate.
        if((op&0xe000)==0x2000){int kind=(op>>>11)&3,rd=(op>>>8)&7,imm=op&255;
            if(kind==0){putReg(rd,imm);nz(imm);}else if(kind==1)subFlags(reg(rd),imm);else if(kind==2)putReg(rd,addFlags(reg(rd),imm));else putReg(rd,subFlags(reg(rd),imm));return 1;}
        // ALU register operations.
        if((op&0xfc00)==0x4000){int kind=(op>>>6)&15,rs=(op>>>3)&7,rd=op&7,a=reg(rd),b=reg(rs),out=a;
            switch(kind){case 0:out=a&b;putReg(rd,out);nz(out);break;case 1:out=a^b;putReg(rd,out);nz(out);break;
                case 2: c=((a>>>(32-(b&255)))&1);out=a<<(b&255);putReg(rd,out);nz(out);break;
                case 3: c=((a>>>(Math.max(1,b&255)-1))&1);out=a>>>(b&255);putReg(rd,out);nz(out);break;
                case 4:c=((a>>>(Math.max(1,b&255)-1))&1);out=a>>(b&255);putReg(rd,out);nz(out);break;
                case 5:out=addFlags(a,b+c);putReg(rd,out);break;case 6:out=subFlags(a,b+(1-c));putReg(rd,out);break;
                case 7:out=Integer.rotateRight(a,b&31);putReg(rd,out);nz(out);break;case 8:nz(a&b);break;
                case 9:out=subFlags(0,b);putReg(rd,out);break;case 10:subFlags(a,b);break;case 11:addFlags(a,b);break;
                case 12:out=a|b;putReg(rd,out);nz(out);break;case 13:out=a*b;putReg(rd,out);nz(out);break;
                case 14:out=a&~b;putReg(rd,out);nz(out);break;case 15:out=~b;putReg(rd,out);nz(out);break;}
            return 1;}
        // High-register ADD/CMP/MOV and BX.
        if((op&0xfc00)==0x4400){int kind=(op>>>8)&3,rd=(op&7)|((op>>>4)&8),rs=((op>>>3)&15),a=regAny(rd),b=regAny(rs);
            if(kind==0)setAny(rd,a+b);else if(kind==1)subFlags(a,b);else if(kind==2)setAny(rd,b);else{pc=b&~1;}return 1;}
        // PC-relative literal load.
        if((op&0xf800)==0x4800){int rd=(op>>>8)&7,addr=((pc+2)&~3)+((op&255)<<2);putReg(rd,read32(addr));return 2;}
        // Register-offset STR/STRH/STRB/LDRSB/LDR/LDRH/LDRB/LDRSH.
        if((op&0xf000)==0x5000){int kind=(op>>>9)&7,ro=(op>>>6)&7,rb=(op>>>3)&7,rd=op&7,addr=reg(rb)+reg(ro);
            switch(kind){case 0:write32(addr,reg(rd));break;case 1:write16(addr,reg(rd));break;case 2:write8(addr,reg(rd));break;case 3:putReg(rd,(byte)read8(addr));break;
                case 4:putReg(rd,read32(addr));break;case 5:putReg(rd,read16(addr));break;case 6:putReg(rd,read8(addr));break;case 7:putReg(rd,(short)read16(addr));break;}return 2;}
        // Immediate byte/word, halfword, and SP-relative transfer.
        if((op&0xe000)==0x6000){boolean byteOp=(op&0x1000)!=0,load=(op&0x0800)!=0;int imm=(op>>>6)&31,rb=(op>>>3)&7,rd=op&7,addr=reg(rb)+(byteOp?imm:imm<<2);
            if(load)putReg(rd,byteOp?read8(addr):read32(addr));else if(byteOp)write8(addr,reg(rd));else write32(addr,reg(rd));return 2;}
        if((op&0xf000)==0x8000){boolean load=(op&0x0800)!=0;int addr=reg((op>>>3)&7)+(((op>>>6)&31)<<1),rd=op&7;if(load)putReg(rd,read16(addr));else write16(addr,reg(rd));return 2;}
        if((op&0xf000)==0x9000){boolean load=(op&0x0800)!=0;int rd=(op>>>8)&7,addr=sp+((op&255)<<2);if(load)putReg(rd,read32(addr));else write32(addr,reg(rd));return 2;}
        // Address generation and SP adjustment.
        if((op&0xf000)==0xa000){int rd=(op>>>8)&7,base=(op&0x0800)!=0?sp:((pc+2)&~3);putReg(rd,base+((op&255)<<2));return 1;}
        if((op&0xff00)==0xb000){int delta=(op&0x7f)<<2;sp=(op&0x80)!=0?sp-delta:sp+delta;return 1;}
        // PUSH/POP (LR/PC extension).
        if((op&0xf600)==0xb400){boolean pop=(op&0x0800)!=0,extra=(op&0x0100)!=0;int mask=op&255;
            if(!pop){if(extra){sp-=4;write32(sp,lr);}for(int i=7;i>=0;i--)if((mask&(1<<i))!=0){sp-=4;write32(sp,reg(i));}}
            else{for(int i=0;i<8;i++)if((mask&(1<<i))!=0){putReg(i,read32(sp));sp+=4;}if(extra){int val=read32(sp);sp+=4;if(pop)pc=val&~1;else lr=val;}}return 3;}
        // LDMIA/STMIA.
        if((op&0xf000)==0xc000){boolean load=(op&0x0800)!=0;int rb=(op>>>8)&7,mask=op&255,addr=reg(rb);for(int i=0;i<8;i++)if((mask&(1<<i))!=0){if(load)putReg(i,read32(addr));else write32(addr,reg(i));addr+=4;}if(!load||mask==0)putReg(rb,addr);return 2+Integer.bitCount(mask);}
        // Conditional and unconditional branches, plus two-halfword BL.
        if((op&0xf000)==0xd000){int cond=(op>>>8)&15;if(cond==15)return 3; if(cond==14)return 3; if(condition(cond))pc+=2+((byte)(op&255))*2;return 1;}
        if((op&0xf800)==0xe000){pc+=2+(((op&0x7ff)<<21)>>20);return 2;}
        if((op&0xf800)==0xf000){lr=pc+2+(((op&0x7ff)<<21)>>9);return 1;}
        if((op&0xf800)==0xf800){int target=lr+((op&0x7ff)<<1);lr=pc|1;pc=target;return 3;}
        return 1; // Unsupported/undefined Thumb encodings behave as harmless NOPs in this prototype.
    }

    private int reg(int i){switch(i){case 0:return r0;case 1:return r1;case 2:return r2;case 3:return r3;case 4:return r4;case 5:return r5;case 6:return r6;default:return r7;}}
    private void putReg(int i,int x){switch(i){case 0:r0=x;break;case 1:r1=x;break;case 2:r2=x;break;case 3:r3=x;break;case 4:r4=x;break;case 5:r5=x;break;case 6:r6=x;break;default:r7=x;}}
    private int regAny(int i){switch(i){case 13:return sp;case 14:return lr;case 15:return pc+2;default:return reg(i);}}
    private void setAny(int i,int x){switch(i){case 13:sp=x;break;case 14:lr=x;break;case 15:pc=x&~1;break;default:putReg(i,x);}}
    private void nz(int x){n=x<0?1:0;z=x==0?1:0;}
    private int addFlags(int a,int b){long q=(a&0xffffffffL)+(b&0xffffffffL);int out=(int)q;c=(q>>>32)!=0?1:0;v=((~(a^b)&(a^out))>>>31)&1;nz(out);return out;}
    private int subFlags(int a,int b){long q=(a&0xffffffffL)-(b&0xffffffffL);int out=(int)q;c=(a&0xffffffffL)>=(b&0xffffffffL)?1:0;v=(((a^b)&(a^out))>>>31)&1;nz(out);return out;}
    private boolean condition(int q){switch(q){case 0:return z!=0;case 1:return z==0;case 2:return c!=0;case 3:return c==0;case 4:return n!=0;case 5:return n==0;case 6:return v!=0;case 7:return v==0;case 8:return c!=0&&z==0;case 9:return c==0||z!=0;case 10:return n==v;case 11:return n!=v;case 12:return z==0&&n==v;case 13:return z!=0||n!=v;default:return false;}}

    private int read8(int address){int a=address&0x0fffffff;
        if(a>=0x08000000&&a<0x0e000000)return rom.length==0?0xff:rom[(a-0x08000000)%rom.length]&255;
        if(a>=0x02000000&&a<0x03000000)return ewram[(a-0x02000000)&0x3ffff]&255;
        if(a>=0x03000000&&a<0x04000000)return iwram[(a-0x03000000)&0x7fff]&255;
        if(a>=0x04000000&&a<0x04000400){int o=a-0x04000000;if(o==0x130)return keyInput()&255;if(o==0x131)return(keyInput()>>>8)&3;return io[o]&255;}
        if(a>=0x05000000&&a<0x05000400)return palette[(a-0x05000000)&0x3ff]&255;
        if(a>=0x06000000&&a<0x06018000)return vram[vramIndex(a-0x06000000)]&255;
        if(a>=0x07000000&&a<0x07000400)return oam[(a-0x07000000)&0x3ff]&255;return 0;
    }
    private int keyInput(){int keys=0x3ff;if((pressed&BUTTON_A)!=0)keys&=~1;if((pressed&BUTTON_B)!=0)keys&=~2;if((pressed&BUTTON_SELECT)!=0)keys&=~4;if((pressed&BUTTON_START)!=0)keys&=~8;if((pressed&BUTTON_RIGHT)!=0)keys&=~16;if((pressed&BUTTON_LEFT)!=0)keys&=~32;if((pressed&BUTTON_UP)!=0)keys&=~64;if((pressed&BUTTON_DOWN)!=0)keys&=~128;return keys;}
    int readKeyInputForTest(){return keyInput();}
    int read16(int a){return read8(a)|(read8(a+1)<<8);}
    private int read32(int a){return read16(a)|(read16(a+2)<<16);}
    private void write8(int address,int value){int a=address&0x0fffffff,value8=value&255;
        if(a>=0x02000000&&a<0x03000000){ewram[(a-0x02000000)&0x3ffff]=(byte)value8;return;}
        if(a>=0x03000000&&a<0x04000000){iwram[(a-0x03000000)&0x7fff]=(byte)value8;return;}
        if(a>=0x04000000&&a<0x04000400){int o=a-0x04000000;io[o]=(byte)value8;if(o<2)dispcnt=io[0]&255|(io[1]&255)<<8;return;}
        if(a>=0x05000000&&a<0x05000400){palette[(a-0x05000000)&0x3ff]=(byte)value8;return;}
        if(a>=0x06000000&&a<0x06018000){vram[vramIndex(a-0x06000000)]=(byte)value8;return;}
        if(a>=0x07000000&&a<0x07000400)oam[(a-0x07000000)&0x3ff]=(byte)value8;
    }
    private void write16(int a,int x){write8(a,x);write8(a+1,x>>>8);}
    private void write32(int a,int x){write16(a,x);write16(a+2,x>>>16);}
    private int vramIndex(int offset){int x=offset&0x1ffff;return x>=0x18000?x-0x8000:x;}
    private int color(int value){int r=value&31,g=(value>>>5)&31,b=(value>>>10)&31;return 0xff000000|((r*255/31)<<16)|((g*255/31)<<8)|(b*255/31);}
    private int paletteColor(int index){return color((palette[(index*2)&0x3ff]&255)|((palette[(index*2+1)&0x3ff]&255)<<8));}
    private void render(){int mode=dispcnt&7,page=(dispcnt&0x10)!=0?0xa000:0;boolean blank=(dispcnt&0x80)!=0;
        if(blank){Arrays.fill(pixels,0xff000000);return;}
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int rgb=0xff000000;
            if(mode==3){int p=(y*WIDTH+x)*2;rgb=color((vram[p]&255)|((vram[p+1]&255)<<8));}
            else if(mode==4){int index=vram[page+y*WIDTH+x]&255;rgb=paletteColor(index);}
            else if(mode==5){int p=page+(y*160+x)*2;if(x<160&&y<128)rgb=color((vram[p]&255)|((vram[p+1]&255)<<8));}
            pixels[y*WIDTH+x]=rgb;
        }
    }
}
