package com.retrovanta;

import java.util.Arrays;

/**
 * Original, compact Sega Master System core. It targets standard 8 KiB Sega-mapper
 * cartridges (and small unbanked images), a practical Z80 instruction subset,
 * the common SMS tile/sprite VDP modes, and two-button controller input.
 * Timing, interrupts, sound and several Z80/VDP corner cases are approximations.
 */
final class MasterSystemCore implements EmulatorCore {
    static final int WIDTH = 256, HEIGHT = 192;
    // Pad 1 bit mask convention used by setButtons(): directions 0x10..0x80; A/B 0x01/0x02.
    static final int BUTTON_A=1, BUTTON_B=2, BUTTON_UP=16, BUTTON_DOWN=32,
            BUTTON_LEFT=64, BUTTON_RIGHT=128;
    private static final int[] PALETTE = new int[64];
    static {
        for (int i=0;i<64;i++) {
            int b=(i&3)*85, r=((i>>>2)&3)*85, g=((i>>>4)&3)*85;
            PALETTE[i]=0xff000000|(r<<16)|(g<<8)|b;
        }
    }
    private byte[] rom=new byte[0];
    private final byte[] ram=new byte[0x2000], vram=new byte[0x4000], cram=new byte[0x40], regs=new byte[16], oam=new byte[0x100];
    private final int[] pixels=new int[WIDTH*HEIGHT];
    private int a,f,b,c,d,e,h,l,sp,pc,iff1,halted,cycles,buttons;
    private int[] banks={0,1,2};
    private int mapperControl, vdpAddress, vdpCode, vdpLatch, vdpFirst, vdpStatus;
    private int lineCounter, frameNumber;
    private String title="Sega Master System";

    @Override public void load(byte[] image) {
        if(image==null || image.length<0x400) throw new IllegalArgumentException("Arquivo pequeno ou inválido para uma ROM de Master System.");
        if(image.length>8*1024*1024+512) throw new IllegalArgumentException("A ROM excede o limite inicial de 8 MB.");
        int offset=(image.length%0x4000==512)?512:0;
        rom=Arrays.copyOfRange(image,offset,image.length);
        Arrays.fill(ram,(byte)0); Arrays.fill(vram,(byte)0); Arrays.fill(cram,(byte)0); Arrays.fill(regs,(byte)0); Arrays.fill(oam,(byte)0);
        banks[0]=0;banks[1]=1;banks[2]=2;mapperControl=0;vdpAddress=vdpCode=vdpLatch=vdpFirst=vdpStatus=lineCounter=frameNumber=0;
        // SMS reset starts at address zero. Register defaults keep the display off until programmed.
        a=f=b=c=d=e=h=l=0; sp=0xdff0; pc=0; iff1=halted=cycles=buttons=0;
        title="Sega Master System";
    }
    @Override public int videoWidth(){return WIDTH;}
    @Override public int videoHeight(){return HEIGHT;}
    @Override public String getCartridgeTitle(){return title;}
    @Override public void setButtons(int bits){buttons=bits&0xf3;}

    @Override public int[] frame(){
        if(rom.length==0){Arrays.fill(pixels,0xff000000);return pixels;}
        int target=cycles+59736, guard=0;
        while(cycles<target && guard++<40000) cycles+=step();
        frameNumber++; render(); return pixels;
    }

    private int step(){
        if(halted!=0)return 4;
        int op=fetch();
        // IX/IY and ED instructions are recognized as prefixes but intentionally not emulated.
        while(op==0xdd||op==0xfd){op=fetch(); if(op==0xed||op==0xdd||op==0xfd){op=fetch();}}
        if(op==0xed){fetch();return 8;}
        if(op==0xcb)return cb(fetch());
        int x=op>>>6,y=(op>>>3)&7,z=op&7,p=y>>>1,q=y&1;
        if(x==0){
            switch(z){
                case 0: switch(y){case 0:return 4;case 1:{int addr=fetch16();write(addr,sp&255);write(addr+1,sp>>>8);return 20;}case 2:fetch();return 8;case 3:pc=(pc+(byte)fetch())&65535;return 12;default:{int off=(byte)fetch();if(condition(y-4)){pc=(pc+off)&65535;return 12;}return 7;}}
                case 1: if(q==0){setPair(p,fetch16());return 10;}addHL(pair(p));return 11;
                case 2:{int addr=p==0?pair(0):p==1?pair(1):p==2?hl():0; if(q==0)write(addr,a);else a=read(addr);if(p==2)setHL((hl()+1)&65535);if(p==3)setHL((hl()-1)&65535);return 7;}
                case 3:setPair(p,(pair(p)+(q==0?1:-1))&65535);return 6;
                case 4:{int v=inc(reg(y));putReg(y,v);return y==6?11:4;}
                case 5:{int v=dec(reg(y));putReg(y,v);return y==6?11:4;}
                case 6:putReg(y,fetch());return y==6?10:7;
                default:misc(y);return 4;
            }
        }
        if(x==1){if(y==6&&z==6){halted=1;return 4;}putReg(y,reg(z));return (y==6||z==6)?7:4;}
        if(x==2){alu(y,reg(z));return z==6?7:4;}
        switch(z){
            case 0:if(y<4){if(condition(y)){pc=pop16();return 11;}return 5;}if(y==4){write(0xff00|fetch(),a);return 11;}if(y==5){addSp((byte)fetch());return 16;}if(y==6){a=read(0xff00|fetch());return 11;}setHL(addSp((byte)fetch()));return 12;
            case 1:if(q==0){setPair2(p,pop16());return 10;}if(p==0){pc=pop16();return 10;}if(p==1){pc=pop16();iff1=1;return 10;}if(p==2){pc=hl();return 4;}sp=hl();return 6;
            case 2:if(y<4){int addr=fetch16();if(condition(y))pc=addr;return 10;}if(y==4){write(0xff00|c,a);return 7;}if(y==5){write(fetch16(),a);return 13;}if(y==6){a=read(0xff00|c);return 7;}a=read(fetch16());return 13;
            case 3:if(y==0){pc=fetch16();return 10;}if(y==1)return cb(fetch());if(y==2){int port=fetch();out(port,a);return 11;}if(y==3){int port=fetch();a=in(port);return 11;}return 4;
            case 4:if(y<4){int addr=fetch16();if(condition(y)){push16(pc);pc=addr;return 17;}return 10;}return 4;
            case 5:if(q==0){push16(pair2(p));return 11;}if(p==0){int addr=fetch16();push16(pc);pc=addr;return 17;}return 4;
            case 6:alu(y,fetch());return 7;
            default:push16(pc);pc=y*8;return 11;
        }
    }
    private int cb(int op){int x=op>>>6,y=(op>>>3)&7,z=op&7,v=reg(z),out=v,carry;
        if(x==0){carry=f&1;switch(y){case 0:carry=v>>>7;out=((v<<1)|(v>>>7))&255;break;case 1:carry=v&1;out=(v>>>1)|(carry<<7);break;case 2:{int c=f&1;carry=v>>>7;out=((v<<1)|c)&255;break;}case 3:{int c=f&1;carry=v&1;out=(v>>>1)|(c<<7);break;}case 4:carry=v>>>7;out=(v<<1)&255;break;case 5:carry=v&1;out=(v>>>1)|(v&0x80);break;case 6:out=((v<<4)|(v>>>4))&255;break;case 7:carry=v&1;out=v>>>1;break;}putReg(z,out);f=(out==0?0x40:0)|(out&0x80)|(carry?1:0);return z==6?15:8;}
        if(x==1){f=(f&1)|0x10|((v&(1<<y))==0?0x40:0)|(y==7&&(v&0x80)!=0?0x80:0);return z==6?12:8;}
        putReg(z,x==2?v&~(1<<y):v|(1<<y));return z==6?15:8;
    }
    private void misc(int n){switch(n){case 0:{int old=a;a=(a+1)&255;f=(f&1)|(a==0?0x40:0)|(a&0x80)|(((old&15)==15)?0x10:0)|((old==0x7f)?4:0);break;}case 1:{int old=a;a=(a-1)&255;f=(f&1)|2|(a==0?0x40:0)|(a&0x80)|((old&15)==0?0x10:0)|(old==0x80?4:0);break;}case 2:{int c=a>>>7;a=((a<<1)|c)&255;f=(f&0xc4)|c;break;}case 3:{int c=a&1;a=(a>>>1)|(c<<7);f=(f&0xc4)|c;break;}case 4:{int c=f&1,top=a>>>7;a=((a<<1)|c)&255;f=(f&0xc4)|top;break;}case 5:{int c=f&1,low=a&1;a=(a>>>1)|(c<<7);f=(f&0xc4)|low;break;}case 6:f=(f&0xc4)|0x10;break;case 7:f=(f&0xc4)|(f&1);break;}}
    private void alu(int op,int v){v&=255;int r,carry=f&1;switch(op){case 0:case 1:r=a+v+carry;f=flags8(r&255)|((r>255)?1:0)|(((~(a^v)&(a^r)&0x80)!=0)?4:0);a=r&255;break;case 2:r=a-v-carry;f=flags8(r&255)|2|((r<0)?1:0)|((((a^v)&(a^r)&0x80)!=0)?4:0);a=r&255;break;case 3:a&=v;f=flags8(a)|0x10;break;case 4:a^=v;f=flags8(a);break;case 5:a|=v;f=flags8(a);break;case 6:r=a-v;f=flags8(r&255)|2|(a>=v?1:0)|((((a^v)&(a^r)&0x80)!=0)?4:0);break;case 7:r=a+v;f=flags8(r&255)|(r>255?1:0)|(((~(a^v)&(a^r)&0x80)!=0)?4:0);a=r&255;break;}}
    private int flags8(int v){return (v&0xa8)|(v==0?0x40:0)|(((Integer.bitCount(v&255)&1)==0)?4:0);}
    private int inc(int v){int o=(v+1)&255;f=(f&1)|(o&0xa8)|(o==0?0x40:0)|((v&15)==15?0x10:0)|(v==0x7f?4:0);return o;}
    private int dec(int v){int o=(v-1)&255;f=(f&1)|2|(o&0xa8)|(o==0?0x40:0)|((v&15)==0?0x10:0)|(v==0x80?4:0);return o;}
    private int addSp(int off){int u=off&255,r=(sp+off)&65535;f=((sp^u^r)&0x10)|(((sp^u^r)&0x100)>>>8);return r;}
    private void addHL(int v){int old=hl(),r=(old+v)&65535;f=(f&0xc4)|(((old&0xfff)+(v&0xfff)>0xfff)?0x10:0)|(old+v>65535?1:0)|(r>>>8&0x80);setHL(r);}
    private boolean condition(int n){switch(n){case 0:return(f&0x40)==0;case 1:return(f&0x40)!=0;case 2:return(f&1)==0;case 3:return(f&1)!=0;case 4:return(f&4)==0;case 5:return(f&4)!=0;case 6:return(f&0x80)==0;default:return(f&0x80)!=0;}}
    private int reg(int r){switch(r){case 0:return b;case 1:return c;case 2:return d;case 3:return e;case 4:return h;case 5:return l;case 6:return read(hl());default:return a;}}
    private void putReg(int r,int v){v&=255;switch(r){case 0:b=v;break;case 1:c=v;break;case 2:d=v;break;case 3:e=v;break;case 4:h=v;break;case 5:l=v;break;case 6:write(hl(),v);break;default:a=v;}}
    private int pair(int p){switch(p){case 0:return(b<<8)|c;case 1:return(d<<8)|e;case 2:return hl();default:return sp;}}
    private int pair2(int p){return p==3?(a<<8)|f:pair(p);}
    private void setPair(int p,int v){switch(p){case 0:b=v>>>8&255;c=v&255;break;case 1:d=v>>>8&255;e=v&255;break;case 2:setHL(v);break;default:sp=v&65535;}}
    private void setPair2(int p,int v){if(p==3){a=v>>>8&255;f=v&255;}else setPair(p,v);}
    private int hl(){return(h<<8)|l;} private void setHL(int v){h=v>>>8&255;l=v&255;}
    private int fetch(){int v=read(pc);pc=(pc+1)&65535;return v;}
    private int fetch16(){int lo=fetch();return lo|(fetch()<<8);}
    private int read16(int addr){return read(addr)|(read(addr+1)<<8);}
    private void push16(int v){sp=(sp-1)&65535;write(sp,v>>>8);sp=(sp-1)&65535;write(sp,v);}
    private int pop16(){int lo=read(sp);sp=(sp+1)&65535;int hi=read(sp);sp=(sp+1)&65535;return lo|(hi<<8);}

    private int read(int addr){addr&=65535;
        if(addr<0xc000){if(rom.length<=0x8000)return rom[addr%rom.length]&255;int slot=addr>>>14;int bank=banks[slot]%Math.max(1,(rom.length+0x3fff)/0x4000);int ix=bank*0x4000+(addr&0x3fff);return ix<rom.length?rom[ix]&255:0xff;}
        if(addr<0xe000)return ram[addr&0x1fff]&255;
        if(addr>=0xfffc){if(addr==0xfffc)return mapperControl;return read(addr-0x2000);}
        return ram[addr&0x1fff]&255;
    }
    private void write(int addr,int value){addr&=65535;value&=255;
        if(addr>=0xfffc){if(addr==0xfffc)mapperControl=value;else banks[addr-0xfffd]=value;return;}
        if(addr>=0xc000){ram[addr&0x1fff]=(byte)value;return;}
    }
    private int in(int port){port&=255;if(port==0xbe){int addr=vdpAddress++&0x3fff;return vdpCode==3?cram[addr&0x3f]&255:vram[addr]&255;}if(port==0xbf){vdpFirst=0;return vdpStatus&255;}if(port==0xdc)return pad1();if(port==0xdd)return 0xff;return 0xff;}
    private int pad1(){int v=0xff;if((buttons&BUTTON_UP)!=0)v&=~1;if((buttons&BUTTON_DOWN)!=0)v&=~2;if((buttons&BUTTON_LEFT)!=0)v&=~4;if((buttons&BUTTON_RIGHT)!=0)v&=~8;if((buttons&BUTTON_A)!=0)v&=~0x10;if((buttons&BUTTON_B)!=0)v&=~0x20;return v;}
    private void out(int port,int value){port&=255;value&=255;if(port==0xbe){int addr=vdpAddress++&0x3fff;if(vdpCode==3)cram[addr&0x3f]=(byte)(value&0x3f);else vram[addr]=(byte)value;return;}if(port==0xbf){if(vdpFirst==0){vdpLatch=value;vdpFirst=1;}else{vdpFirst=0;vdpCode=value>>>6;vdpAddress=(((value&0x3f)<<8)|vdpLatch)&0x3fff;if(vdpCode==2){int r=value&15;if(r<regs.length)regs[r]=(byte)vdpLatch;}else if(vdpCode==3)cram[vdpAddress&0x3f]=(byte)(vdpLatch&0x3f);}return;}if(port==0x7e){lineCounter=value;return;}}

    private void render(){
        int bg=(regs[7]&15);Arrays.fill(pixels,PALETTE[cram[bg]&63]);
        if((regs[1]&0x40)==0)return;
        int nameBase=(regs[2]&0x0e)<<10;
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
            int col=x>>>3,row=y>>>3,entry=nameBase+((row*32+col)*2);entry&=0x3fff;
            int word=(vram[entry]&255)|((vram[(entry+1)&0x3fff]&255)<<8),tile=word&0x1ff;
            int px=x&7,py=y&7;if((word&0x200)!=0)px=7-px;if((word&0x400)!=0)py=7-py;
            int bit=7-px,base=(tile*32+py*4)&0x3fff;
            int color=((vram[base]>>>bit)&1)|(((vram[(base+1)&0x3fff]>>>bit)&1)<<1)|(((vram[(base+2)&0x3fff]>>>bit)&1)<<2)|(((vram[(base+3)&0x3fff]>>>bit)&1)<<3);
            boolean high=(word&0x800)!=0;int pal=(high?32:0)+color;
            if(color==0&&!high)continue;
            pixels[y*WIDTH+x]=PALETTE[cram[pal]&63];
        }
        renderSprites(nameBase);
    }
    private void renderSprites(int nameBase){
        int table=(regs[5]&0x7e)<<7, pattern=(regs[6]&4)!=0?0x2000:0, size=(regs[1]&2)!=0?16:8, zoom=(regs[1]&1)!=0?2:1, count=0;
        for(int i=0;i<64;i++){
            int sy=(oam[i]&255);if(sy==208)break;if(sy>=240)sy-=256;sy++;
            if(sy>=HEIGHT||sy+size*zoom<=0)continue;
            if(++count>8)continue;
            int tile=oam[table+i]&255;if(size==16)tile&=0xfe;
            for(int row=0;row<size;row++)for(int col=0;col<8;col++){
                int t=tile+(row>>>3),r=row&7,base=(pattern+t*32+r*4)&0x3fff,bit=7-col;
                int color=((vram[base]>>>bit)&1)|(((vram[(base+1)&0x3fff]>>>bit)&1)<<1)|(((vram[(base+2)&0x3fff]>>>bit)&1)<<2)|(((vram[(base+3)&0x3fff]>>>bit)&1)<<3);
                if(color==0)continue;
                for(int zx=0;zx<zoom;zx++)for(int zy=0;zy<zoom;zy++){int x=(oam[0x80+i]&255)+col*zoom+zx,y=sy+row*zoom+zy;if(x>=WIDTH||y<0||y>=HEIGHT)continue;pixels[y*WIDTH+x]=PALETTE[cram[32+color]&63];}
            }
        }
    }
}
