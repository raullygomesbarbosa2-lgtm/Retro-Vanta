package com.retrovanta;

import java.util.Arrays;

/**
 * Independent, deliberately incomplete SNES LoROM prototype. It implements a
 * small emulation-mode 65C816 subset and a static Mode 0 BG1 tile plane; it is
 * not a general-purpose or game-compatible SNES emulator.
 */
final class SnesCore {
    static final int WIDTH=256, HEIGHT=224;
    static final int BUTTON_B=1, BUTTON_Y=2, BUTTON_SELECT=4, BUTTON_START=8,
            BUTTON_UP=16, BUTTON_DOWN=32, BUTTON_LEFT=64, BUTTON_RIGHT=128,
            BUTTON_A=256, BUTTON_X=512, BUTTON_L=1024, BUTTON_R=2048;
    private static final int FRAME_CYCLES=29781, ROM_LIMIT=8*1024*1024;
    private byte[] rom;
    private final byte[] wram=new byte[0x20000], cgram=new byte[512], vram=new byte[0x10000];
    private final int[] pixels=new int[WIDTH*HEIGHT];
    private final int[] ppu=new int[0x40];
    private int pc,a,x,y,s,d,db,p;
    private final boolean emulation=true;
    private boolean stopped;
    private int pressed,joypadWord,cgramAddress,cgramLatch,vramAddress;
    private boolean cgramSecond;
    private int displayBrightness=15,bg1Hofs,bg1Vofs;
    private boolean hofsSecond,vofsSecond;
    private boolean forcedBlank=true;
    private String title="SNES LoROM prototype";

    public void load(byte[] image) {
        if(image==null||image.length<0x8000)throw new IllegalArgumentException("Cartucho SNES pequeno demais: é necessário ao menos um banco LoROM de 32 KiB.");
        if(image.length>ROM_LIMIT)throw new IllegalArgumentException("ROM SNES acima do limite inicial de 8 MiB.");
        if((image.length&0x7fff)!=0)throw new IllegalArgumentException("Este protótipo aceita somente ROMs LoROM em bancos completos de 32 KiB.");
        int mode=image[0x7fd5]&255;
        if((mode&0x2f)!=0x20)throw new IllegalArgumentException(String.format("Mapeamento SNES 0x%02X não compatível: somente LoROM padrão, sem chips especiais.",mode));
        int declaredSize=image[0x7fd7]&255;
        if(declaredSize<5||declaredSize>13)throw new IllegalArgumentException("Tamanho declarado no cabeçalho LoROM fora do perfil inicial (32 KiB–8 MiB).");
        int vector=(image[0x7ffc]&255)|((image[0x7ffd]&255)<<8);
        if(vector<0x8000)throw new IllegalArgumentException("Vetor de reset LoROM inválido: precisa apontar para a região ROM $8000–$FFFF.");
        rom=Arrays.copyOf(image,image.length);title=readTitle(image);
        Arrays.fill(wram,(byte)0);Arrays.fill(cgram,(byte)0);Arrays.fill(vram,(byte)0);Arrays.fill(ppu,0);Arrays.fill(pixels,0xff000000);
        a=x=y=d=db=0;s=0x01ff;p=0x34;stopped=false;
        pressed=joypadWord=cgramAddress=cgramLatch=vramAddress=0;cgramSecond=false;
        displayBrightness=15;forcedBlank=true;bg1Hofs=bg1Vofs=0;hofsSecond=vofsSecond=false;pc=vector;
    }
    private String readTitle(byte[] image){StringBuilder out=new StringBuilder();for(int i=0x7fc0;i<0x7fd5;i++){int ch=image[i]&255;if(ch>=32&&ch<=126)out.append((char)ch);}String v=out.toString().trim();return v.isEmpty()?"SNES LoROM · subset":v;}
    public int videoWidth(){return WIDTH;} public int videoHeight(){return HEIGHT;} public String getCartridgeTitle(){return title;}
    public void setButtons(int bits){pressed=bits&0x0fff;}
    public int[] frame(){
        if(rom==null){Arrays.fill(pixels,0xff000000);return pixels;}
        joypadWord=pressed&0xffff;
        int consumed=0,guard=0;
        while(consumed<FRAME_CYCLES&&!stopped&&guard++<50000)consumed+=step();
        renderMode0Bg1();return pixels;
    }

    private int step(){
        int op=fetch8(), m8=(p&0x20)!=0?1:0, x8=(p&0x10)!=0?1:0;
        switch(op){
            case 0xea:return 2;
            case 0x18:p&=~1;return 2; case 0x38:p|=1;return 2;
            case 0x58:p&=~4;return 2; case 0x78:p|=4;return 2;
            case 0xb8:p&=~0x40;return 2; // CLV
            case 0xd8:p&=~8;return 2; // CLD
            case 0xf8:p|=8;return 2; // SED (decimal arithmetic is not currently decimal-correct)
            case 0xc2:p&=~fetch8();if(emulation)p|=0x30;return 3;
            case 0xe2:p|=fetch8();if(emulation)p|=0x30;return 3;
            case 0xa9:setA(loadImmediate(m8!=0),m8!=0);setNZ(a,m8!=0);return 2+m8;
            case 0xa2:x=loadImmediate(x8!=0);setNZ(x,x8!=0);return 2+x8;
            case 0xa0:y=loadImmediate(x8!=0);setNZ(y,x8!=0);return 2+x8;
            case 0xa5:setA(readMemory(dpAddress(fetch8())),m8!=0);setNZ(a,m8!=0);return 3;
            case 0xb5:setA(readMemory(dpAddress((fetch8()+x)&255)),m8!=0);setNZ(a,m8!=0);return 4;
            case 0xad:setA(readMemory(absAddress(fetch16())),m8!=0);setNZ(a,m8!=0);return 4;
            case 0xbd:setA(readMemory(absAddress((fetch16()+x)&0xffff)),m8!=0);setNZ(a,m8!=0);return 4;
            case 0xb9:setA(readMemory(absAddress((fetch16()+y)&0xffff)),m8!=0);setNZ(a,m8!=0);return 4;
            case 0xaf:setA(readMemory(fetch24()),m8!=0);setNZ(a,m8!=0);return 5;
            case 0xa7:{int dp=fetch8(),ptr=readMemory(dpAddress(dp))|(readMemory(dpAddress((dp+1)&255))<<8);setA(readMemory(((db&255)<<16)|ptr),m8!=0);setNZ(a,m8!=0);return 6;}
            case 0x85:writeMemory(dpAddress(fetch8()),a);return 3;
            case 0x95:writeMemory(dpAddress((fetch8()+x)&255),a);return 4;
            case 0x8d:writeMemory(absAddress(fetch16()),a);return 4;
            case 0x9d:writeMemory(absAddress((fetch16()+x)&0xffff),a);return 5;
            case 0x99:writeMemory(absAddress((fetch16()+y)&0xffff),a);return 5;
            case 0x8f:writeMemory(fetch24(),a);return 5;
            case 0x64:writeMemory(dpAddress(fetch8()),0);return 3;
            case 0x74:writeMemory(dpAddress((fetch8()+x)&255),0);return 4;
            case 0x9c:writeMemory(absAddress(fetch16()),0);return 4;
            case 0x9e:writeMemory(absAddress((fetch16()+x)&0xffff),0);return 5;
            case 0x69:adc(loadImmediate(m8!=0),m8!=0);return 2+m8;
            case 0xe9:adc(loadImmediate(m8!=0)^widthMask(m8!=0),m8!=0);return 2+m8;
            case 0x29:a&=loadImmediate(m8!=0);setNZ(a,m8!=0);return 2+m8;
            case 0x09:a|=loadImmediate(m8!=0);setNZ(a,m8!=0);return 2+m8;
            case 0x49:a^=loadImmediate(m8!=0);setNZ(a,m8!=0);return 2+m8;
            case 0x25:a&=readMemory(dpAddress(fetch8()));setNZ(a,m8!=0);return 3;
            case 0x05:a|=readMemory(dpAddress(fetch8()));setNZ(a,m8!=0);return 3;
            case 0x45:a^=readMemory(dpAddress(fetch8()));setNZ(a,m8!=0);return 3;
            case 0xc9:compare(a,loadImmediate(m8!=0),m8!=0);return 2+m8;
            case 0xe0:compare(x,loadImmediate(x8!=0),x8!=0);return 2+x8;
            case 0xc0:compare(y,loadImmediate(x8!=0),x8!=0);return 2+x8;
            case 0xc5:compare(a,readMemory(dpAddress(fetch8())),m8!=0);return 3;
            case 0x24:{int v=readMemory(dpAddress(fetch8()));p=(p&~0x82)|((a&v)==0?2:0)|(v&0x80);return 3;}
            case 0x89:{int v=loadImmediate(m8!=0);p=(p&~0x82)|((a&v)==0?2:0)|(v&(m8!=0?0x80:0x8000));return 2+m8;}
            case 0xe6:modifyDp(fetch8(),1);return 5; case 0xc6:modifyDp(fetch8(),-1);return 5;
            case 0xee:modifyAbs(fetch16(),1);return 6; case 0xce:modifyAbs(fetch16(),-1);return 6;
            case 0x4c:pc=fetch16();return 3;
            case 0x5c:pc=fetch24()&0xffff;return 4; // long jump restricted to current bank
            case 0x20:{int target=fetch16();push16((pc-1)&0xffff);pc=target;return 6;}
            case 0x60:pc=(pop16()+1)&0xffff;return 6;
            case 0x80:branch(true);return 3;
            case 0x82:pc=(pc+(short)fetch16())&0xffff;return 4;
            case 0xd0:branch((p&2)==0);return 2; case 0xf0:branch((p&2)!=0);return 2;
            case 0x90:branch((p&1)==0);return 2; case 0xb0:branch((p&1)!=0);return 2;
            case 0x10:branch((p&0x80)==0);return 2; case 0x30:branch((p&0x80)!=0);return 2;
            case 0x50:branch((p&0x40)==0);return 2; case 0x70:branch((p&0x40)!=0);return 2;
            case 0xaa:x=a&widthMask(x8!=0);setNZ(x,x8!=0);return 2;
            case 0x8a:setA(x,m8!=0);setNZ(a,m8!=0);return 2;
            case 0xa8:y=a&widthMask(x8!=0);setNZ(y,x8!=0);return 2;
            case 0x98:setA(y,m8!=0);setNZ(a,m8!=0);return 2;
            case 0xba:x=s&widthMask(x8!=0);setNZ(x,x8!=0);return 2;
            case 0x9a:s=emulation?0x100|(x&255):x&0xffff;return 2;
            case 0x8b:db=a&255;return 3; case 0xab:db=pop8();setNZ(db,true);return 4;
            case 0xe8:x=(x+1)&widthMask(x8!=0);setNZ(x,x8!=0);return 2;
            case 0xca:x=(x-1)&widthMask(x8!=0);setNZ(x,x8!=0);return 2;
            case 0xc8:y=(y+1)&widthMask(x8!=0);setNZ(y,x8!=0);return 2;
            case 0x88:y=(y-1)&widthMask(x8!=0);setNZ(y,x8!=0);return 2;
            case 0x1a:setA((a+1)&widthMask(m8!=0),m8!=0);setNZ(a,m8!=0);return 2;
            case 0x3a:setA((a-1)&widthMask(m8!=0),m8!=0);setNZ(a,m8!=0);return 2;
            case 0x48:push8(a);return 3;case 0x68:setA(pop8(),m8!=0);setNZ(a,m8!=0);return 4;
            case 0xda:push8(x);return 3;case 0xfa:x=pop8();setNZ(x,x8!=0);return 4;
            case 0x5a:push8(y);return 3;case 0x7a:y=pop8();setNZ(y,x8!=0);return 4;
            case 0x00:stopped=true;return 7; // BRK is a stop, not interrupt dispatch
            default:throw new IllegalStateException(String.format("Opcode 65C816 $%02X fora do subconjunto em $%04X.",op,(pc-1)&0xffff));
        }
    }
    private void branch(boolean take){int off=(byte)fetch8();if(take)pc=(pc+off)&0xffff;}
    private int dpAddress(int value){return (d+(value&255))&0xffff;}
    private int absAddress(int value){return ((db&255)<<16)|(value&0xffff);}
    private int loadImmediate(boolean eight){int v=fetch8();if(!eight)v|=fetch8()<<8;return v;}
    private int widthMask(boolean eight){return eight?0xff:0xffff;}
    private void setA(int value,boolean eight){a=eight?((a&0xff00)|(value&0xff)):(value&0xffff);}
    private void setNZ(int value,boolean eight){int v=value&widthMask(eight);p=(p&~0x82)|(v==0?2:0)|(eight?(v&0x80):((v>>>8)&0x80));}
    private void compare(int lhs,int rhs,boolean eight){int mask=widthMask(eight),diff=(lhs-rhs)&mask;p=(p&~3)|((lhs&mask)>=(rhs&mask)?1:0)|(diff==0?2:0);}
    private void adc(int value,boolean eight){int mask=widthMask(eight),sign=eight?0x80:0x8000,old=a&mask,v=value&mask,sum=old+v+(p&1);int result=sum&mask;p=(p&~0x43)|((sum>mask)?1:0)|((~(old^v)&(old^result)&sign)!=0?0x40:0);a=(a&~mask)|result;setNZ(a,eight);}
    private void modifyDp(int dp,int delta){int v=(readMemory(dpAddress(dp))+delta)&255;writeMemory(dpAddress(dp),v);setNZ(v,true);}
    private void modifyAbs(int address,int delta){int adr=absAddress(address),v=(readMemory(adr)+delta)&255;writeMemory(adr,v);setNZ(v,true);}
    private void push8(int v){writeMemory(s&0xffff,v);s=0x100|((s-1)&255);}
    private int pop8(){s=0x100|((s+1)&255);return readMemory(s&0xffff);}
    private void push16(int v){push8((v>>>8)&255);push8(v&255);}
    private int pop16(){int lo=pop8();return lo|(pop8()<<8);}
    private int fetch8(){int value=readMemory(((db&255)<<16)|(pc&0xffff));pc=(pc+1)&0xffff;return value;}
    private int fetch16(){int lo=fetch8();return lo|(fetch8()<<8);}
    private int fetch24(){int lo=fetch16();return lo|(fetch8()<<16);}

    private int readMemory(int address){
        address&=0xffffff;int bank=address>>>16,off=address&0xffff;
        if(bank==0x7e||bank==0x7f)return wram[((bank-0x7e)<<16)|off]&255;
        if((bank<=0x3f||(bank>=0x80&&bank<=0xbf))&&off<0x2000)return wram[off]&255;
        if((bank&0x7f)<=0x3f&&off>=0x2100&&off<=0x213f)return readIo(off);
        if(bank==0&&off==0x4218)return joypadWord&255;if(bank==0&&off==0x4219)return (joypadWord>>>8)&255;
        if((bank&0x7f)<=0x7d&&off>=0x8000)return rom[((bank&0x7f)*0x8000+(off&0x7fff))%rom.length]&255;
        return 0;
    }
    private int readIo(int off){if(off==0x2139||off==0x213a){int val=vram[(vramAddress*2+(off==0x213a?1:0))&0xffff]&255;if(off==0x213a)incrementVram();return val;}if(off==0x213b){int v=cgram[cgramAddress&511]&255;if(cgramSecond){v=(cgram[(cgramAddress+1)&511]&127);cgramAddress=(cgramAddress+2)&511;}cgramSecond=!cgramSecond;return v;}return ppu[off&63]&255;}
    private void writeMemory(int address,int value){
        address&=0xffffff;value&=255;int bank=address>>>16,off=address&0xffff;
        if(bank==0x7e||bank==0x7f){wram[((bank-0x7e)<<16)|off]=(byte)value;return;}
        if((bank<=0x3f||(bank>=0x80&&bank<=0xbf))&&off<0x2000){wram[off]=(byte)value;return;}
        if((bank&0x7f)<=0x3f&&off>=0x2100&&off<=0x213f){writeIo(off,value);return;}
        if(bank!=0)return;
        if(off==0x4016)return;
    }
    private void writeIo(int off,int value){
        ppu[off&63]=value;
        if(off==0x2100){displayBrightness=value&15;forcedBlank=(value&0x80)!=0;return;}
        if(off==0x2121){cgramAddress=(value&255)*2;cgramSecond=false;return;}
        if(off==0x2122){if(!cgramSecond){cgramLatch=value;cgramSecond=true;}else{cgram[cgramAddress&511]=(byte)cgramLatch;cgram[(cgramAddress+1)&511]=(byte)(value&0x7f);cgramAddress=(cgramAddress+2)&511;cgramSecond=false;}return;}
        if(off==0x2116){vramAddress=(vramAddress&0xff00)|value;return;}
        if(off==0x2117){vramAddress=(vramAddress&255)|(value<<8);vramAddress&=0x7fff;return;}
        if(off==0x2118){vram[(vramAddress*2)&0xffff]=(byte)value;if((ppu[0x15]&0x80)==0)incrementVram();return;}
        if(off==0x2119){vram[(vramAddress*2+1)&0xffff]=(byte)value;if((ppu[0x15]&0x80)!=0)incrementVram();return;}
        if(off==0x210d||off==0x210e){boolean horizontal=off==0x210d;int old=horizontal?bg1Hofs:bg1Vofs;boolean second=horizontal?hofsSecond:vofsSecond;int next=second?((old&255)|((value&3)<<8)):((old&0x300)|(value&255));if(horizontal){bg1Hofs=next&0x3ff;hofsSecond=!hofsSecond;}else{bg1Vofs=next&0x3ff;vofsSecond=!vofsSecond;}}
    }
    private void incrementVram(){vramAddress=(vramAddress+((ppu[0x15]&3)==1?32:1))&0x7fff;}
    private void renderMode0Bg1(){
        int brightness=forcedBlank?0:displayBrightness;
        if(brightness==0){Arrays.fill(pixels,0xff000000);return;}
        int mapBase=(ppu[0x07]&0xfc)<<9, charBase=(ppu[0x0b]&0x0f)<<13;
        int hofs=bg1Hofs, vofs=bg1Vofs;
        boolean enabled=(ppu[0x2c]&1)!=0&&(ppu[0x05]&7)==0;
        if(!enabled){fillBackdrop(brightness);return;}
        for(int sy=0;sy<HEIGHT;sy++)for(int sx=0;sx<WIDTH;sx++){
            int wx=(sx+hofs)&255,wy=(sy+vofs)&255;
            int mapIndex=mapBase+(((wy>>>3)*32+(wx>>>3))*2);
            int entry=(vram[mapIndex&0xffff]&255)|((vram[(mapIndex+1)&0xffff]&255)<<8);
            int tile=entry&0x3ff,palette=(entry>>>10)&7;
            int row=wy&7,col=wx&7;if((entry&0x8000)!=0)row=7-row;if((entry&0x4000)!=0)col=7-col;
            int base=charBase+tile*32;
            int bit=7-col,lo=vram[(base+row*2)&0xffff]&255,hi=vram[(base+row*2+1)&0xffff]&255;
            int p2=vram[(base+16+row*2)&0xffff]&255,p3=vram[(base+16+row*2+1)&0xffff]&255;
            int color=((lo>>>bit)&1)|(((hi>>>bit)&1)<<1)|(((p2>>>bit)&1)<<2)|(((p3>>>bit)&1)<<3);
            int argb=color==0?backdrop(brightness):rgb((cgram[(palette*32+color*2)&511]&255)|((cgram[(palette*32+color*2+1)&511]&127)<<8),brightness);
            pixels[sy*WIDTH+sx]=argb;
        }
    }
    private void fillBackdrop(int brightness){Arrays.fill(pixels,backdrop(brightness));}
    private int backdrop(int brightness){return rgb((cgram[0]&255)|((cgram[1]&127)<<8),brightness);}
    private int rgb(int raw,int brightness){int r=((raw&31)*255/31)*brightness/15,g=(((raw>>>5)&31)*255/31)*brightness/15,b=(((raw>>>10)&31)*255/31)*brightness/15;return 0xff000000|(r<<16)|(g<<8)|b;}
    int readWramForTest(int address){return wram[address&0x1ffff]&255;}
}
