package com.retrovanta;

import java.util.Arrays;

/**
 * Small original Atari 2600 (6507/TIA/RIOT) interpreter. Accepts raw 2 KiB and
 * 4 KiB fixed-bank cartridges. This is a functional, deliberately partial
 * implementation: CPU timing and TIA graphics are scanline approximations.
 */
final class Atari2600Core implements EmulatorCore {
    static final int WIDTH=160, HEIGHT=192;
    // Shared virtual-controller bits: up, down, left, right, fire.
    static final int BUTTON_UP=1, BUTTON_DOWN=2, BUTTON_LEFT=4, BUTTON_RIGHT=8, BUTTON_FIRE=16;
    private static final int[] COLORS=new int[128];
    static {
        // A compact generated NTSC-like palette, indexed by the TIA's 7-bit color value.
        for(int hue=0;hue<16;hue++) for(int lum=0;lum<8;lum++) {
            double h=(hue*22.5-30.0)*Math.PI/180.0, s=(hue==0?0.0:0.72), l=0.12+lum*0.105;
            double c=(1-Math.abs(2*l-1))*s, x=c*(1-Math.abs((h/(Math.PI/3))%2-1)), m=l-c/2;
            double r=0,g=0,b=0; double deg=(hue*22.5+330)%360;
            if(deg<60){r=c;g=x;} else if(deg<120){r=x;g=c;} else if(deg<180){g=c;b=x;}
            else if(deg<240){g=x;b=c;} else if(deg<300){r=x;b=c;} else {r=c;b=x;}
            COLORS[hue*8+lum]=0xff000000|((int)((r+m)*255)<<16)|((int)((g+m)*255)<<8)|(int)((b+m)*255);
        }
    }
    private final byte[] ram=new byte[128];
    private final int[] pixels=new int[WIDTH*HEIGHT];
    private final byte[] rom=new byte[4096];
    private final int[] tia=new int[64];
    private int a,x,y,sp,p,pc,pressed,cycles,scanline,hclock;
    private int timer,timerDivider,timerPrescale=1;
    private int player0X=40,player1X=112,missile0X=40,missile1X=112,ballX=80;
    private String title="Atari 2600 · 2K/4K";

    @Override public void load(byte[] image) {
        if(image==null || (image.length!=2048 && image.length!=4096))
            throw new IllegalArgumentException("Atari 2600 core accepts raw 2 KiB or 4 KiB cartridges only.");
        Arrays.fill(rom,(byte)0xff); System.arraycopy(image,0,rom,0,image.length);
        Arrays.fill(ram,(byte)0); Arrays.fill(tia,0); Arrays.fill(pixels,0xff000000);
        a=x=y=0;sp=0xfd;p=0x24;pressed=cycles=scanline=hclock=0;
        timer=0;timerDivider=0;timerPrescale=1;player0X=40;player1X=112;missile0X=40;missile1X=112;ballX=80;
        pc=read16(0xfffc); title="Atari 2600 · "+(image.length/1024)+" KiB raw";
    }
    @Override public int videoWidth(){return WIDTH;}
    @Override public int videoHeight(){return HEIGHT;}
    @Override public String getCartridgeTitle(){return title;}
    @Override public void setButtons(int bits){pressed=bits&31;}
    @Override public int[] frame(){
        if(pc==0 && rom[0]!=0) { Arrays.fill(pixels,0xff000000); return pixels; }
        int end=cycles+262*76, guard=0;
        while(cycles<end && guard++<30000) step();
        return pixels;
    }

    private int step(){
        int op=fetch(), used=2, addr, v, old, result;
        switch(op){
            // Loads and stores
            case 0xa9:a=fetch();nz(a);used=2;break; case 0xa5:addr=zp();a=read(addr);nz(a);used=3;break;
            case 0xb5:addr=zpx();a=read(addr);nz(a);used=4;break;case 0xad:addr=abs();a=read(addr);nz(a);used=4;break;
            case 0xbd:addr=absx();a=read(addr);nz(a);used=4;break;case 0xb9:addr=absy();a=read(addr);nz(a);used=4;break;
            case 0xa1:addr=indx();a=read(addr);nz(a);used=6;break;case 0xb1:addr=indy();a=read(addr);nz(a);used=5;break;
            case 0xa2:x=fetch();nz(x);used=2;break;case 0xa6:x=read(zp());nz(x);used=3;break;case 0xb6:x=read(zpy());nz(x);used=4;break;case 0xae:x=read(abs());nz(x);used=4;break;case 0xbe:x=read(absy());nz(x);used=4;break;
            case 0xa0:y=fetch();nz(y);used=2;break;case 0xa4:y=read(zp());nz(y);used=3;break;case 0xb4:y=read(zpx());nz(y);used=4;break;case 0xac:y=read(abs());nz(y);used=4;break;case 0xbc:y=read(absx());nz(y);used=4;break;
            case 0x85:write(zp(),a);used=3;break;case 0x95:write(zpx(),a);used=4;break;case 0x8d:write(abs(),a);used=4;break;case 0x9d:write(absx(),a);used=5;break;case 0x99:write(absy(),a);used=5;break;case 0x81:write(indx(),a);used=6;break;case 0x91:write(indy(),a);used=6;break;
            case 0x86:write(zp(),x);used=3;break;case 0x96:write(zpy(),x);used=4;break;case 0x8e:write(abs(),x);used=4;break;
            case 0x84:write(zp(),y);used=3;break;case 0x94:write(zpx(),y);used=4;break;case 0x8c:write(abs(),y);used=4;break;
            // AND / OR / XOR / ADC / SBC, immediate and common memory modes
            case 0x29:a&=fetch();nz(a);used=2;break;case 0x25:a&=read(zp());nz(a);used=3;break;case 0x2d:a&=read(abs());nz(a);used=4;break;case 0x35:a&=read(zpx());nz(a);used=4;break;case 0x3d:a&=read(absx());nz(a);used=4;break;case 0x39:a&=read(absy());nz(a);used=4;break;case 0x21:a&=read(indx());nz(a);used=6;break;case 0x31:a&=read(indy());nz(a);used=5;break;
            case 0x09:a|=fetch();nz(a);used=2;break;case 0x05:a|=read(zp());nz(a);used=3;break;case 0x0d:a|=read(abs());nz(a);used=4;break;case 0x15:a|=read(zpx());nz(a);used=4;break;case 0x1d:a|=read(absx());nz(a);used=4;break;case 0x19:a|=read(absy());nz(a);used=4;break;case 0x01:a|=read(indx());nz(a);used=6;break;case 0x11:a|=read(indy());nz(a);used=5;break;
            case 0x49:a^=fetch();nz(a);used=2;break;case 0x45:a^=read(zp());nz(a);used=3;break;case 0x4d:a^=read(abs());nz(a);used=4;break;case 0x55:a^=read(zpx());nz(a);used=4;break;case 0x5d:a^=read(absx());nz(a);used=4;break;case 0x59:a^=read(absy());nz(a);used=4;break;case 0x41:a^=read(indx());nz(a);used=6;break;case 0x51:a^=read(indy());nz(a);used=5;break;
            case 0x69:adc(fetch());used=2;break;case 0x65:adc(read(zp()));used=3;break;case 0x6d:adc(read(abs()));used=4;break;case 0x75:adc(read(zpx()));used=4;break;case 0x7d:adc(read(absx()));used=4;break;case 0x79:adc(read(absy()));used=4;break;case 0x61:adc(read(indx()));used=6;break;case 0x71:adc(read(indy()));used=5;break;
            case 0xe9:case 0xeb:sbc(fetch());used=2;break;case 0xe5:sbc(read(zp()));used=3;break;case 0xed:sbc(read(abs()));used=4;break;case 0xf5:sbc(read(zpx()));used=4;break;case 0xfd:sbc(read(absx()));used=4;break;case 0xf9:sbc(read(absy()));used=4;break;case 0xe1:sbc(read(indx()));used=6;break;case 0xf1:sbc(read(indy()));used=5;break;
            // Comparisons
            case 0xc9:compare(a,fetch());used=2;break;case 0xc5:compare(a,read(zp()));used=3;break;case 0xcd:compare(a,read(abs()));used=4;break;case 0xd5:compare(a,read(zpx()));used=4;break;case 0xdd:compare(a,read(absx()));used=4;break;case 0xd9:compare(a,read(absy()));used=4;break;case 0xc1:compare(a,read(indx()));used=6;break;case 0xd1:compare(a,read(indy()));used=5;break;
            case 0xe0:compare(x,fetch());used=2;break;case 0xe4:compare(x,read(zp()));used=3;break;case 0xec:compare(x,read(abs()));used=4;break;case 0xc0:compare(y,fetch());used=2;break;case 0xc4:compare(y,read(zp()));used=3;break;case 0xcc:compare(y,read(abs()));used=4;break;
            // Inc/dec, shifts and rotates (accumulator plus common memory forms)
            case 0xe8:x=(x+1)&255;nz(x);break;case 0xca:x=(x-1)&255;nz(x);break;case 0xc8:y=(y+1)&255;nz(y);break;case 0x88:y=(y-1)&255;nz(y);break;
            case 0xe6:addr=zp();write(addr,(read(addr)+1)&255);nz(read(addr));used=5;break;case 0xf6:addr=zpx();write(addr,(read(addr)+1)&255);nz(read(addr));used=6;break;case 0xee:addr=abs();write(addr,(read(addr)+1)&255);nz(read(addr));used=6;break;case 0xfe:addr=absx();write(addr,(read(addr)+1)&255);nz(read(addr));used=7;break;
            case 0xc6:addr=zp();write(addr,(read(addr)-1)&255);nz(read(addr));used=5;break;case 0xd6:addr=zpx();write(addr,(read(addr)-1)&255);nz(read(addr));used=6;break;case 0xce:addr=abs();write(addr,(read(addr)-1)&255);nz(read(addr));used=6;break;case 0xde:addr=absx();write(addr,(read(addr)-1)&255);nz(read(addr));used=7;break;
            case 0x0a:a=shift(a,0);break;case 0x06:addr=zp();write(addr,shift(read(addr),0));used=5;break;case 0x16:addr=zpx();write(addr,shift(read(addr),0));used=6;break;case 0x0e:addr=abs();write(addr,shift(read(addr),0));used=6;break;case 0x1e:addr=absx();write(addr,shift(read(addr),0));used=7;break;
            case 0x4a:a=shift(a,1);break;case 0x46:addr=zp();write(addr,shift(read(addr),1));used=5;break;case 0x56:addr=zpx();write(addr,shift(read(addr),1));used=6;break;case 0x4e:addr=abs();write(addr,shift(read(addr),1));used=6;break;case 0x5e:addr=absx();write(addr,shift(read(addr),1));used=7;break;
            case 0x2a:a=shift(a,2);break;case 0x26:addr=zp();write(addr,shift(read(addr),2));used=5;break;case 0x36:addr=zpx();write(addr,shift(read(addr),2));used=6;break;case 0x2e:addr=abs();write(addr,shift(read(addr),2));used=6;break;case 0x3e:addr=absx();write(addr,shift(read(addr),2));used=7;break;
            case 0x6a:a=shift(a,3);break;case 0x66:addr=zp();write(addr,shift(read(addr),3));used=5;break;case 0x76:addr=zpx();write(addr,shift(read(addr),3));used=6;break;case 0x6e:addr=abs();write(addr,shift(read(addr),3));used=6;break;case 0x7e:addr=absx();write(addr,shift(read(addr),3));used=7;break;
            // Control flow, branches, stack and flags
            case 0x4c:pc=abs();used=3;break;case 0x6c:addr=abs();pc=read(addr)|(read((addr&0xff00)|((addr+1)&255))<<8);used=5;break;
            case 0x20:addr=abs();push((pc-1)>>>8);push((pc-1)&255);pc=addr;used=6;break;case 0x60:pc=((pop()|(pop()<<8))+1)&65535;used=6;break;
            case 0x00:fetch();push(pc>>>8);push(pc&255);push(p|0x30);p|=4;pc=read16(0xfffe);used=7;break;
            case 0x48:push(a);used=3;break;case 0x68:a=pop();nz(a);used=4;break;case 0x08:push(p|0x30);used=3;break;case 0x28:p=(pop()&0xef)|0x20;used=4;break;
            case 0x10:used=branch((p&0x80)==0);break;case 0x30:used=branch((p&0x80)!=0);break;case 0x50:used=branch((p&0x40)==0);break;case 0x70:used=branch((p&0x40)!=0);break;case 0x90:used=branch((p&1)==0);break;case 0xb0:used=branch((p&1)!=0);break;case 0xd0:used=branch((p&2)==0);break;case 0xf0:used=branch((p&2)!=0);break;
            case 0x18:p&=~1;break;case 0x38:p|=1;break;case 0x58:p&=~4;break;case 0x78:p|=4;break;case 0xb8:p&=~0x40;break;case 0xd8:p&=~8;break;case 0xf8:p|=8;break;
            case 0xaa:x=a;nz(x);break;case 0x8a:a=x;nz(a);break;case 0xa8:y=a;nz(y);break;case 0x98:a=y;nz(a);break;case 0xba:x=sp;nz(x);break;case 0x9a:sp=x;break;case 0xea:break;
            case 0x24:v=read(zp());p=(p&~0xc2)|(v&0xc0)|((a&v)==0?2:0);used=3;break;case 0x2c:v=read(abs());p=(p&~0xc2)|(v&0xc0)|((a&v)==0?2:0);used=4;break;
            default: // Unsupported/illegal instructions act as a two-cycle NOP to keep imperfect carts contained.
                used=2;break;
        }
        tick(used);return used;
    }
    private int branch(boolean yes){int off=(byte)fetch();if(!yes)return 2;int old=pc;pc=(pc+off)&65535;return ((old&0xff00)!=(pc&0xff00))?4:3;}
    private void tick(int n){for(int c=0;c<n;c++){cycles++;timerDivider++;if(timerDivider>=timerPrescale){timerDivider=0;timer=(timer-1)&255;}for(int z=0;z<3;z++){
        if(scanline>=40&&scanline<232&&hclock>=68){int px=hclock-68;if(px<WIDTH)pixels[(scanline-40)*WIDTH+px]=colorAt(px,scanline-40);}
        hclock++;if(hclock>=228){hclock=0;scanline++;if(scanline>=262)scanline=0;}
    }}}
    private int colorAt(int px,int py){int bg=tia[0x09]&0xfe,pf=tia[0x08]&0xfe,p0=tia[0x06]&0xfe,p1=tia[0x07]&0xfe;
        if((tia[0x01]&2)!=0)return bg;
        boolean play=playfield(px);boolean one=player(px,player0X,tia[0x1b],tia[0x04],tia[0x0b]);boolean two=player(px,player1X,tia[0x1c],tia[0x05],tia[0x0c]);
        boolean ball=(px>=ballX&&px<ballX+((tia[0x0a]&0x30)==0x30?4:2)&&tia[0x1f]!=0);
        boolean m0=px==missile0X&&(tia[0x1d]&2)!=0,m1=px==missile1X&&(tia[0x1e]&2)!=0;
        boolean priority=(tia[0x0a]&4)!=0;
        if(priority){if(play||ball)return pf;if(one||m0)return p0;if(two||m1)return p1;}
        else {if(one||m0)return p0;if(two||m1)return p1;if(play||ball)return pf;}
        return bg;
    }
    private boolean playfield(int px){int cell=px/4;boolean right=cell>=20;int bitCell=right?cell-20:cell;boolean reflect=(tia[0x0a]&1)!=0&&right;if(reflect)bitCell=19-bitCell;int bit;
        if(bitCell<4)bit=((tia[0x0d]>>>(4+bitCell))&1);else if(bitCell<12)bit=((tia[0x0e]>>>(11-bitCell))&1);else bit=((tia[0x0f]>>>(bitCell-12))&1);
        return bit!=0;
    }
    private boolean player(int px,int pos,int graphic,int size,int reflect){int mode=(size>>>4)&7,scale=1,reps=1,gap=0;switch(mode){case 1:scale=2;break;case 2:reps=2;gap=8;break;case 3:reps=3;gap=8;break;case 4:scale=2;reps=2;gap=8;break;case 5:scale=4;break;case 6:scale=2;reps=3;gap=8;break;case 7:scale=4;reps=2;gap=8;break;}
        int dx=(px-pos+160)%160;for(int copy=0;copy<reps;copy++){int start=copy*(8*scale+gap);if(dx>=start&&dx<start+8*scale){int bit=(dx-start)/scale;if((reflect&8)!=0)bit=7-bit;return ((graphic>>>(7-bit))&1)!=0;}}return false;}
    private int fetch(){int v=read(pc);pc=(pc+1)&65535;return v;} private int abs(){int lo=fetch();return lo|(fetch()<<8);} private int absx(){return(abs()+x)&65535;} private int absy(){return(abs()+y)&65535;}
    private int zp(){return fetch()&255;} private int zpx(){return(fetch()+x)&255;} private int zpy(){return(fetch()+y)&255;}
    private int indx(){int q=(fetch()+x)&255;return read(q)|(read((q+1)&255)<<8);} private int indy(){int q=fetch()&255;return((read(q)|(read((q+1)&255)<<8))+y)&65535;}
    private int read16(int q){return read(q)|(read((q+1)&65535)<<8);} private int read(int address){int q=address&0x1fff;
        if(q>=0x1000)return rom[(q-0x1000)&(rom.length==2048?0x7ff:0xfff)]&255;
        if((q&0x1280)==0x0080)return ram[q&0x7f]&255;
        if((q&0x1280)==0x0280){int r=q&0x1f;if(r==0)return (pressed&BUTTON_RIGHT)!=0?0:0x80;if(r==1)return(pressed&BUTTON_LEFT)!=0?0:0x80;if(r==2)return(pressed&BUTTON_DOWN)!=0?0:0x80;if(r==3)return(pressed&BUTTON_UP)!=0?0:0x80;if(r==0x0c)return(pressed&BUTTON_FIRE)!=0?0:0x80;if(r==0x04)return timer;return 0;}
        int r=q&0x3f;if(r==0x0c)return(pressed&BUTTON_FIRE)!=0?0:0x80;return 0;
    }
    private void write(int address,int value){int q=address&0x1fff;value&=255;if(q>=0x1000)return;
        if((q&0x1280)==0x0080){ram[q&0x7f]=(byte)value;return;}
        if((q&0x1280)==0x0280){int r=q&0x1f;if(r==0x14){timer=value;timerDivider=0;}else if(r>=0x15&&r<=0x17){int[] pres={1,8,64,1024};timerPrescale=pres[r-0x15];timer=value;timerDivider=0;}return;}
        int r=q&0x3f;tia[r]=value;
        switch(r){case 0x10:player0X=(hclock/3-68+160)%160;break;case 0x11:player1X=(hclock/3-68+160)%160;break;case 0x12:missile0X=(hclock/3-68+160)%160;break;case 0x13:missile1X=(hclock/3-68+160)%160;break;case 0x14:ballX=(hclock/3-68+160)%160;break;case 0x2a:hclock=0;scanline=(scanline+1)%262;break;case 0x2b:Arrays.fill(tia,0);break;default:break;}
    }
    private void push(int v){write(0x100|sp,v);sp=(sp-1)&255;} private int pop(){sp=(sp+1)&255;return read(0x100|sp);}
    private void nz(int v){p=(p&~0x82)|((v&255)==0?2:0)|(v&0x80);} private void compare(int r,int v){int d=(r-v)&255;p=(p&~0x83)|(r>=v?1:0)|(d==0?2:0)|(d&0x80);}
    private void adc(int v){int s=a+v+(p&1),o=s&255;p=(p&~0xc3)|(s>255?1:0)|((~(a^v)&(a^o)&0x80)!=0?0x40:0)|(o==0?2:0)|(o&0x80);a=o;}
    private void sbc(int v){adc(v^255);}
    private int shift(int v,int op){int carry, out;switch(op){case 0:carry=v>>>7;out=(v<<1)&255;break;case 1:carry=v&1;out=v>>>1;break;case 2:carry=v>>>7;out=((v<<1)|(p&1))&255;break;default:carry=v&1;out=(v>>>1)|((p&1)<<7);break;}p=(p&~1)|carry;nz(out);return out;}
}
