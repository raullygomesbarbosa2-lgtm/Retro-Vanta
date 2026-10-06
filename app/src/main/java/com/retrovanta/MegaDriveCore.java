package com.retrovanta;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Original, deliberately bounded Mega Drive/Genesis implementation. It accepts
 * ordinary, headered-at-0x100, unbanked cartridges up to 4 MiB and emulates a
 * useful 68000 subset, basic VDP plane-A tiles and a 3-button pad. It is not a
 * cycle-accurate or general commercial-game emulator; see the opcode/device
 * handling below for the intentionally small profile.
 */
final class MegaDriveCore implements EmulatorCore {
    static final int WIDTH=320, HEIGHT=224;
    static final int BUTTON_A=1, BUTTON_B=2, BUTTON_C=4, BUTTON_START=8,
            BUTTON_UP=16, BUTTON_DOWN=32, BUTTON_LEFT=64, BUTTON_RIGHT=128;
    private static final int ROM_LIMIT=4*1024*1024;
    private static final int[] COLORS=new int[64];
    static {
        for(int c=0;c<64;c++) {
            int r=(c&7)*36, g=((c>>>3)&7)*36, b=((c>>>6)&3)*85;
            COLORS[c]=0xff000000|(r<<16)|(g<<8)|b;
        }
    }
    private byte[] rom=new byte[0];
    private final byte[] ram=new byte[0x10000], vram=new byte[0x10000], cram=new byte[0x80], vsram=new byte[0x80];
    private final int[] regs=new int[24], pixels=new int[WIDTH*HEIGHT];
    private int[] d=new int[8], a=new int[8];
    private int pc,sr,cycles,buttons, halted;
    private int vdpAddress,vdpCode,vdpFirst,vdpLatch,vdpStatus,frameCount;
    private String title="Sega Mega Drive";

    @Override public void load(byte[] image) {
        if(image==null || image.length<0x200) throw new IllegalArgumentException("Invalid or undersized Mega Drive ROM.");
        if(image.length>ROM_LIMIT) throw new IllegalArgumentException("Cartridge exceeds the supported 4 MiB unbanked profile.");
        if(image[0x100]!='S'||image[0x101]!='E'||image[0x102]!='G'||image[0x103]!='A')
            throw new IllegalArgumentException("Missing standard SEGA cartridge header at 0x100.");
        rom=Arrays.copyOf(image,image.length);
        Arrays.fill(ram,(byte)0); Arrays.fill(vram,(byte)0); Arrays.fill(cram,(byte)0); Arrays.fill(vsram,(byte)0); Arrays.fill(regs,0);
        Arrays.fill(d,0); Arrays.fill(a,0);
        a[7]=read32Raw(0); pc=read32Raw(4)&0xffffff; sr=0x2700;
        cycles=buttons=halted=frameCount=vdpAddress=vdpCode=vdpFirst=vdpLatch=vdpStatus=0;
        title=headerText(0x150,0x180); if(title.isEmpty()) title=headerText(0x120,0x150);
        if(title.isEmpty())title="Sega Mega Drive";
    }
    @Override public int videoWidth(){return WIDTH;}
    @Override public int videoHeight(){return HEIGHT;}
    @Override public String getCartridgeTitle(){return title;}
    @Override public void setButtons(int bits){buttons=bits&0xff;}

    @Override public int[] frame(){
        if(rom.length==0){Arrays.fill(pixels,0xff000000);return pixels;}
        int end=cycles+127000, guard=0;
        while(cycles<end && guard++<40000) cycles+=step();
        frameCount++; render(); return pixels;
    }

    private int step(){
        if(halted!=0)return 4;
        int op=fetch16();
        if(op==0x4e71)return 4; // NOP
        if(op==0x4e72){sr=fetch16();halted=1;return 4;} // STOP
        if(op==0x4e75){pc=pop32()&0xffffff;return 16;} // RTS
        if((op&0xfff8)==0x4e50){int r=op&7;push32(a[r]);a[r]=a[7];a[7]+= (short)fetch16();return 16;} // LINK
        if((op&0xfff8)==0x4e58){int r=op&7;a[7]=a[r];a[r]=pop32();return 12;} // UNLK
        if(op==0x4e70)return 4; // RESET (device reset is intentionally minimal)
        if((op&0xffc0)==0x4ec0){pc=eaAddress((op>>>3)&7,op&7)&0xffffff;return 10;} // JMP
        if((op&0xffc0)==0x4e80){int target=eaAddress((op>>>3)&7,op&7);push32(pc);pc=target&0xffffff;return 18;} // JSR
        if((op&0xf1c0)==0x41c0){int dr=(op>>>9)&7;a[dr]=eaAddress((op>>>3)&7,op&7);return 8;} // LEA
        if((op&0xf100)==0x7000){int r=(op>>>9)&7;d[r]=(byte)op;setLogic(d[r],4);return 4;} // MOVEQ
        if((op&0xf000)==0x6000)return branch(op); // Bcc/BSR
        if((op&0xf000)==0x1000||(op&0xf000)==0x2000||(op&0xf000)==0x3000)return move(op);
        if((op&0xf100)==0x5000)return quick(op);
        if((op&0xff00)==0x4200||(op&0xff00)==0x4a00)return unary(op);
        if((op&0xff00)==0x0000||(op&0xff00)==0x0200||(op&0xff00)==0x0400||(op&0xff00)==0x0600||(op&0xff00)==0x0a00||(op&0xff00)==0x0c00)return immediate(op);
        if((op&0xf000)==0x8000||(op&0xf000)==0x9000||(op&0xf000)==0xb000||(op&0xf000)==0xc000||(op&0xf000)==0xd000)return arithmetic(op);
        if((op&0xfff8)==0x4e40){int vec=32+(op&7);push32(pc);push16(sr);pc=read32(vec*4)&0xffffff;return 34;} // TRAP
        return 4; // unsupported instruction: deterministic short no-op for bounded smoke/profile behavior
    }

    private int branch(int op){
        int cond=(op>>>8)&15, disp=(byte)op; if(disp==0)disp=(short)fetch16();
        if(cond==1){push32(pc);pc=(pc+disp)&0xffffff;return 18;}
        if(testCond(cond)){pc=(pc+disp)&0xffffff;return 10;}return 8;
    }
    private int move(int op){
        int top=op>>>12,size=top==1?1:top==3?2:4;
        int sm=(op>>>3)&7,srce=op&7,dm=(op>>>6)&7,dst=(op>>>9)&7;
        int val=readEa(sm,srce,size); writeEa(dm,dst,size,val);
        if(dm!=1)setLogic(val,size);
        return 8;
    }
    private int quick(int op){
        int sizeCode=(op>>>6)&3;if(sizeCode==3)return 4;int size=sizeCode==0?1:sizeCode==1?2:4;
        int amount=(op>>>9)&7;if(amount==0)amount=8;int mode=(op>>>3)&7,r=op&7;
        if(mode==1){a[r]=(op&0x100)!=0?a[r]-amount:a[r]+amount;return 8;}
        int old=readEa(mode,r,size), value=(op&0x100)!=0?old-amount:old+amount;
        writeEa(mode,r,size,value);setAddFlags(old,amount,value,size,(op&0x100)!=0);return 8;
    }
    private int unary(int op){
        int sizeCode=(op>>>6)&3;if(sizeCode==3)return 4;int size=sizeCode==0?1:sizeCode==1?2:4;
        int mode=(op>>>3)&7,r=op&7,v=readEa(mode,r,size);
        if((op&0xff00)==0x4a00){setLogic(v,size);return 8;}
        writeEa(mode,r,size,0);setLogic(0,size);return 8;
    }
    private int immediate(int op){
        int family=op&0xff00, sc=(op>>>6)&3;if(sc==3)return 4;int size=sc==0?1:sc==1?2:4;
        int imm=size==4?fetch32():size==2?fetch16():(byte)fetch16();int mode=(op>>>3)&7,r=op&7,old=readEa(mode,r,size),out;
        if(family==0x0c00){out=old-imm;setAddFlags(old,imm,out,size,true);return 8;}
        if(family==0x0000)out=old|imm;else if(family==0x0200)out=old&imm;else if(family==0x0400)out=old-imm;else if(family==0x0600)out=old+imm;else if(family==0x0a00)out=old^imm;else return 4;
        writeEa(mode,r,size,out);
        if(family==0x0400)setAddFlags(old,imm,out,size,true);else if(family==0x0600)setAddFlags(old,imm,out,size,false);else setLogic(out,size);
        return 8;
    }
    private int arithmetic(int op){
        int top=op>>>12, dr=(op>>>9)&7, mode=(op>>>3)&7,r=op&7,dir=(op>>>8)&1, sc=(op>>>6)&3;
        if(top==0xb000){ // CMP/EOR common register and EA forms
            int size=sc==0?1:sc==1?2:sc==2?4:0;if(size==0)return 4;
            if(dir==0){int src=readEa(mode,r,size);setAddFlags(d[dr],src,d[dr]-src,size,true);}else{int old=readEa(mode,r,size),v=d[dr];writeEa(mode,r,size,old^v);setLogic(old^v,size);}return 8;
        }
        if(top==0xc000 && (op&0xf1f0)==0xc100){int sr=(op>>>9)&7;int t=d[dr];d[dr]=d[dr]&d[sr];d[sr]=t;return 6;}
        int size=sc==0?1:sc==1?2:sc==2?4:0;if(size==0)return 4;
        int val=readEa(mode,r,size), old=d[dr], result;
        if(top==0x8000){result=dir==0?old|val:val|old; if(dir==0)d[dr]=result;else writeEa(mode,r,size,result);setLogic(result,size);}
        else if(top==0xc000){result=dir==0?old&val:val&old;if(dir==0)d[dr]=result;else writeEa(mode,r,size,result);setLogic(result,size);}
        else if(top==0x9000||top==0xd000){boolean sub=top==9;result=dir==0?(sub?old-val:old+val):(sub?val-old:val+old);if(dir==0)d[dr]=result;else writeEa(mode,r,size,result);setAddFlags(dir==0?old:val,dir==0?val:old,result,size,sub);}
        return 8;
    }

    private int readEa(int mode,int r,int size){
        if(mode==0)return sized(d[r],size);
        if(mode==1)return sized(a[r],size);
        if(mode==7){if(r==0)return sized((short)fetch16(),size);if(r==1)return fetch32();if(r==2){int addr=(pc+(short)fetch16())&0xffffff;return readSized(addr,size);}if(r==4)return size==4?fetch32():size==2?fetch16():(byte)fetch16();}
        int step=size==1&&r==7?2:size,addr;
        if(mode==3)addr=a[r]&0xffffff;else if(mode==4)addr=(a[r]-step)&0xffffff;else addr=eaAddress(mode,r);
        int v=readSized(addr,size);if(mode==3)a[r]+=step;else if(mode==4)a[r]-=step;return v;
    }
    private void writeEa(int mode,int r,int size,int value){
        value=sized(value,size);if(mode==0){d[r]=merge(d[r],value,size);return;}if(mode==1){a[r]=size==2?(short)value:value;return;}
        if(mode==7&&r==0){writeSized((short)fetch16(),size,value);return;}if(mode==7&&r==1){writeSized(fetch32(),size,value);return;}
        int step=size==1&&r==7?2:size,addr;
        if(mode==3)addr=a[r]&0xffffff;else if(mode==4){a[r]-=step;addr=a[r]&0xffffff;}else addr=eaAddress(mode,r);
        writeSized(addr,size,value);if(mode==3)a[r]+=step;
    }
    private int eaAddress(int mode,int r){
        switch(mode){case 2:return a[r]&0xffffff;case 3:case 4:return a[r]&0xffffff;case 5:return(a[r]+(short)fetch16())&0xffffff;case 7:if(r==0)return(short)fetch16()&0xffffff;if(r==1)return fetch32()&0xffffff;if(r==2){int base=pc;return(base+(short)fetch16())&0xffffff;}default:return 0;}
    }
    private int readSized(int addr,int size){if(size==1)return read8(addr);if(size==2)return read16(addr);return read32(addr);}
    private void writeSized(int addr,int size,int v){if(size==1)write8(addr,v);else if(size==2)write16(addr,v);else write32(addr,v);}
    private int read8(int addr){addr&=0xffffff;if(addr<rom.length)return rom[addr]&255;if(addr>=0xff0000)return ram[addr&0xffff]&255;if(addr>=0xa10000&&addr<=0xa1001f)return controllerRead(addr);if(addr>=0xc00000&&addr<0xc00020)return vdpRead(addr);return 0xff;}
    private int read16(int addr){addr&=0xffffff;if(addr==0xc00000||addr==0xc00004||addr==0xc00008||addr==0xc0000c)return vdpRead(addr);if(addr>=0xa10000&&addr<0xa10020){return(read8(addr)<<8)|read8(addr+1);}return(read8(addr)<<8)|read8(addr+1);}
    private int read32(int addr){return(read16(addr)<<16)|read16(addr+2);}
    private void write8(int addr,int v){addr&=0xffffff;v&=255;if(addr>=0xff0000){ram[addr&0xffff]=(byte)v;return;}if(addr>=0xa10000&&addr<=0xa1001f)return;if(addr>=0xc00000&&addr<0xc00020){vdpWrite(addr,v);return;}}
    private void write16(int addr,int v){addr&=0xffffff;if(addr>=0xff0000){ram[addr&0xffff]=(byte)(v>>>8);ram[(addr+1)&0xffff]=(byte)v;return;}if(addr>=0xa10000&&addr<0xa10020)return;if(addr>=0xc00000&&addr<0xc00020){vdpWrite(addr,v);return;}write8(addr,v>>>8);write8(addr+1,v);}
    private void write32(int addr,int v){write16(addr,v>>>16);write16(addr+2,v);}
    private int controllerRead(int addr){if((addr&0x1f)==3||(addr&0x1f)==5){int v=0xff;if((buttons&BUTTON_UP)!=0)v&=~1;if((buttons&BUTTON_DOWN)!=0)v&=~2;if((buttons&BUTTON_LEFT)!=0)v&=~4;if((buttons&BUTTON_RIGHT)!=0)v&=~8;if((buttons&BUTTON_B)!=0)v&=~0x10;if((buttons&BUTTON_C)!=0)v&=~0x20;if((buttons&BUTTON_A)!=0)v&=~0x40;if((buttons&BUTTON_START)!=0)v&=~0x80;return v;}return 0xff;}
    private int vdpRead(int addr){if((addr&0x1c)==4){vdpFirst=0;return vdpStatus;}if((addr&0x1c)==0){int val=((vram[vdpAddress&0xffff]&255)<<8)|(vram[(vdpAddress+1)&0xffff]&255);vdpAddress=(vdpAddress+2)&0xffff;vdpFirst=0;return val;}return 0;}
    private void vdpWrite(int addr,int value){
        if((addr&0x1c)==4){
            value&=0xffff;
            if((value&0xc000)==0x8000){int reg=(value>>>8)&31;if(reg<regs.length)regs[reg]=value&255;vdpFirst=0;return;}
            if(vdpFirst==0){vdpLatch=value;vdpFirst=1;}else{int cmd=value;vdpCode=(cmd>>>14)&3;vdpAddress=((cmd&3)<<14)|vdpLatch;vdpFirst=0;}
            return;
        }
        if((addr&0x1c)==0){vdpFirst=0;if(vdpCode==1)writeVram(vdpAddress,value);else if(vdpCode==3)writeCram(vdpAddress,value);else if(vdpCode==2)writeVsram(vdpAddress,value);}
    }
    private void writeVram(int addr,int val){vram[addr&0xffff]=(byte)(val>>>8);vram[(addr+1)&0xffff]=(byte)val;vdpAddress=(addr+2)&0xffff;}
    private void writeCram(int addr,int val){int ix=(addr>>>1)&0x3f;cram[ix*2]=(byte)(val>>>8);cram[ix*2+1]=(byte)val;vdpAddress=(addr+2)&0xffff;}
    private void writeVsram(int addr,int val){int ix=(addr>>>1)&0x3f;vsram[ix*2]=(byte)(val>>>8);vsram[ix*2+1]=(byte)val;vdpAddress=(addr+2)&0xffff;}
    private int cramColor(int index){int raw=((cram[(index&63)*2]&255)<<8)|(cram[(index&63)*2+1]&255);int r=((raw>>>1)&7)*36,g=((raw>>>5)&7)*36,b=((raw>>>9)&7)*36;return 0xff000000|(r<<16)|(g<<8)|b;}

    private void render(){
        int backdrop=regs[7]&0x3f;Arrays.fill(pixels,cramColor(backdrop));if((regs[1]&0x40)==0)return;
        int base=(regs[2]&0x38)<<10, planeWidth=planeDimension(regs[16]&3), planeHeight=planeDimension((regs[16]>>>4)&3);
        if(planeWidth==0)planeWidth=32;if(planeHeight==0)planeHeight=32;
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
            int tx=(x>>>3)%planeWidth,ty=(y>>>3)%planeHeight,at=(base+((ty*planeWidth+tx)*2))&0xffff;
            int word=((vram[at]&255)<<8)|(vram[(at+1)&0xffff]&255),tile=word&0x7ff;
            int px=x&7,py=y&7;if((word&0x800)!=0)px=7-px;if((word&0x1000)!=0)py=7-py;
            int p=(tile*32+py*4+(px>>>1))&0xffff,b=vram[p]&255,color=(px&1)==0?(b>>>4):(b&15);
            if(color==0)continue;int palette=(word>>>13)&3,ci=palette*16+color;
            pixels[y*WIDTH+x]=cramColor(ci);
        }
    }
    private int planeDimension(int code){switch(code){case 0:return 32;case 1:return 64;case 2:return 128;default:return 32;}}

    private int fetch16(){int v=read16(pc);pc=(pc+2)&0xffffff;return v;}
    private int fetch32(){int hi=fetch16();return(hi<<16)|fetch16();}
    private int pop32(){int v=read32(a[7]);a[7]=(a[7]+4)&0xffffff;return v;}
    private void push32(int v){a[7]=(a[7]-4)&0xffffff;write32(a[7],v);}
    private void push16(int v){a[7]=(a[7]-2)&0xffffff;write16(a[7],v);}
    private int read32Raw(int at){if(at<0||at+3>=rom.length)return 0;return((rom[at]&255)<<24)|((rom[at+1]&255)<<16)|((rom[at+2]&255)<<8)|(rom[at+3]&255);}
    private String headerText(int from,int to){int n=Math.min(to,rom.length);String s=new String(rom,from,Math.max(0,n-from),StandardCharsets.US_ASCII).replaceAll("[^\\x20-\\x7e]"," ").trim();return s.replaceAll("\\s+"," ");}
    private int sized(int v,int size){return size==1?(byte)v:size==2?(short)v:v;}
    private int merge(int old,int v,int size){if(size==1)return(old&0xffffff00)|(v&255);if(size==2)return(old&0xffff0000)|(v&0xffff);return v;}
    private void setLogic(int v,int size){int x=sized(v,size),sign=size==1?0x80:size==2?0x8000:0x80000000;sr=(sr&~0x0f)|(x==0?4:0)|(x<0?8:0);}
    private void setAddFlags(int left,int right,int result,int size,boolean sub){int mask=size==1?255:size==2?65535:-1,sign=size==1?128:size==2?32768:0x80000000;int l=left&mask,r=right&mask,o=result&mask;int flags=(o==0?4:0)|((o&sign)!=0?8:0);boolean carry=sub?l<r:((long)l+(long)r)>(size==1?255L:size==2?65535L:0xffffffffL);boolean overflow=sub?(((l^r)&(l^o)&sign)!=0):(((~(l^r))&(l^o)&sign)!=0);if(carry)flags|=1;if(overflow)flags|=2;sr=(sr&~0x0f)|flags;}
    private boolean testCond(int c){boolean z=(sr&4)!=0,n=(sr&8)!=0,v=(sr&2)!=0,carry=(sr&1)!=0;switch(c){case 0:return true;case 1:return false;case 2:return!carry&&!z;case 3:return carry||z;case 4:return!carry;case 5:return carry;case 6:return!z;case 7:return z;case 8:return!v;case 9:return v;case 10:return!n;case 11:return n;case 12:return n==v;case 13:return n!=v;case 14:return!z&&n==v;default:return z||n!=v;}}
}
