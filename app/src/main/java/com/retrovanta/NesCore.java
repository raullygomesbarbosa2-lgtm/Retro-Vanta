package com.retrovanta;

import java.util.Arrays;

/**
 * Original, self-contained NES core prototype. Supports iNES 1.0 mapper 0 (NROM)
 * and mapper 2 (UxROM), official NMOS 6502 instructions, controller port 1, and
 * a software-rendered 256x240 picture. Audio/APU emulation is intentionally omitted.
 * The PPU renderer and timing are functional approximations, not cycle-accurate.
 */
final class NesCore implements EmulatorCore {
    static final int WIDTH = 256, HEIGHT = 240;
    // NES controller bits: A, B, Select, Start, Up, Down, Left, Right.
    static final int BUTTON_A=1, BUTTON_B=2, BUTTON_SELECT=4, BUTTON_START=8,
            BUTTON_UP=16, BUTTON_DOWN=32, BUTTON_LEFT=64, BUTTON_RIGHT=128;
    private static final int[] COLORS = {
            0xff626262,0xff001fb2,0xff2404c8,0xff5200b2,0xff730076,0xff800024,0xff730b00,0xff522800,
            0xff244400,0xff005700,0xff005c00,0xff005324,0xff003c76,0xff000000,0xff000000,0xff000000,
            0xffababab,0xff0d57ff,0xff4b30ff,0xff8a13ff,0xffbc08d6,0xffd21269,0xffc72e00,0xff9d5400,
            0xff607b00,0xff209800,0xff00a300,0xff009942,0xff007db4,0xff000000,0xff000000,0xff000000,
            0xffffffff,0xff53aeff,0xff9085ff,0xffd365ff,0xffff57ff,0xffff5dcf,0xffff7757,0xfffa9e00,
            0xffbdc700,0xff7ae700,0xff43f611,0xff26ef7e,0xff2cd5f6,0xff4e4e4e,0xff000000,0xff000000,
            0xffffffff,0xffb6e1ff,0xffced1ff,0xffe9c3ff,0xffffbcff,0xffffbdf4,0xffffc6c3,0xffffd59a,
            0xffe9e681,0xffcef481,0xffb6f5a4,0xffa9f2ce,0xffa9eafa,0xffb8b8b8,0xff000000,0xff000000
    };
    private static final int IMP=0, ACC=1, ZP=2, ZPX=3, ZPY=4, ABS=5, ABSX=6, ABSY=7,
            IND=8, INDX=9, INDY=10, REL=11, IMM=12;
    private static final int ORA=1, AND=2, EOR=3, ADC=4, STA=5, LDA=6, CMP=7, SBC=8,
            ASL=9, ROL=10, LSR=11, ROR=12, STX=13, LDX=14, DEC=15, INC=16, STY=17, LDY=18,
            BIT=19, CPX=20, CPY=21, BRK=22, JSR=23, RTI=24, RTS=25, JMP=26, PHP=27, PLP=28,
            PHA=29, PLA=30, BPL=31, BMI=32, BVC=33, BVS=34, BCC=35, BCS=36, BNE=37, BEQ=38,
            CLC=39, SEC=40, CLI=41, SEI=42, CLV=43, CLD=44, SED=45, TAX=46, TXA=47, DEX=48,
            INX=49, TAY=50, TYA=51, DEY=52, INY=53, TXS=54, TSX=55, NOP=56;
    private static final int[] OP=new int[256], MODE=new int[256], CYCLES=new int[256];
    static {
        Arrays.fill(OP,NOP); Arrays.fill(MODE,IMP); Arrays.fill(CYCLES,2);
        instruction(0x00,BRK,IMP,7); instruction(0x20,JSR,ABS,6); instruction(0x40,RTI,IMP,6); instruction(0x60,RTS,IMP,6);
        instruction(0x4c,JMP,ABS,3); instruction(0x6c,JMP,IND,5);
        instruction(0x08,PHP,IMP,3); instruction(0x28,PLP,IMP,4); instruction(0x48,PHA,IMP,3); instruction(0x68,PLA,IMP,4);
        int[] flags={0x18,0x38,0x58,0x78,0xb8,0xd8,0xf8}; int[] flagOps={CLC,SEC,CLI,SEI,CLV,CLD,SED};
        for(int i=0;i<flags.length;i++) instruction(flags[i],flagOps[i],IMP,2);
        int[] implied={0xaa,0x8a,0xca,0xe8,0xa8,0x98,0x88,0xc8,0x9a,0xba,0xea};
        int[] impliedOps={TAX,TXA,DEX,INX,TAY,TYA,DEY,INY,TXS,TSX,NOP};
        for(int i=0;i<implied.length;i++) instruction(implied[i],impliedOps[i],IMP,2);
        int[] branches={0x10,0x30,0x50,0x70,0x90,0xb0,0xd0,0xf0};
        int[] branchOps={BPL,BMI,BVC,BVS,BCC,BCS,BNE,BEQ};
        for(int i=0;i<branches.length;i++) instruction(branches[i],branchOps[i],REL,2);
        matrix(ORA, new int[]{0x01,0x05,0x09,0x0d,0x11,0x15,0x19,0x1d},new int[]{INDX,ZP,IMM,ABS,INDY,ZPX,ABSY,ABSX},new int[]{6,3,2,4,5,4,4,4});
        matrix(AND, new int[]{0x21,0x25,0x29,0x2d,0x31,0x35,0x39,0x3d},new int[]{INDX,ZP,IMM,ABS,INDY,ZPX,ABSY,ABSX},new int[]{6,3,2,4,5,4,4,4});
        matrix(EOR, new int[]{0x41,0x45,0x49,0x4d,0x51,0x55,0x59,0x5d},new int[]{INDX,ZP,IMM,ABS,INDY,ZPX,ABSY,ABSX},new int[]{6,3,2,4,5,4,4,4});
        matrix(ADC, new int[]{0x61,0x65,0x69,0x6d,0x71,0x75,0x79,0x7d},new int[]{INDX,ZP,IMM,ABS,INDY,ZPX,ABSY,ABSX},new int[]{6,3,2,4,5,4,4,4});
        matrix(STA, new int[]{0x81,0x85,0,0x8d,0x91,0x95,0x99,0x9d},new int[]{INDX,ZP,IMP,ABS,INDY,ZPX,ABSY,ABSX},new int[]{6,3,2,4,6,4,5,5});
        matrix(LDA, new int[]{0xa1,0xa5,0xa9,0xad,0xb1,0xb5,0xb9,0xbd},new int[]{INDX,ZP,IMM,ABS,INDY,ZPX,ABSY,ABSX},new int[]{6,3,2,4,5,4,4,4});
        matrix(CMP, new int[]{0xc1,0xc5,0xc9,0xcd,0xd1,0xd5,0xd9,0xdd},new int[]{INDX,ZP,IMM,ABS,INDY,ZPX,ABSY,ABSX},new int[]{6,3,2,4,5,4,4,4});
        matrix(SBC, new int[]{0xe1,0xe5,0xe9,0xed,0xf1,0xf5,0xf9,0xfd},new int[]{INDX,ZP,IMM,ABS,INDY,ZPX,ABSY,ABSX},new int[]{6,3,2,4,5,4,4,4});
        matrix(STX,new int[]{0,0x86,0,0x8e,0,0x96,0,0},new int[]{0,ZP,0,ABS,0,ZPY,0,0},new int[]{0,3,0,4,0,4,0,0});
        matrix(LDX,new int[]{0,0xa6,0xa2,0xae,0,0xb6,0xbe,0},new int[]{0,ZP,IMM,ABS,0,ZPY,ABSY,0},new int[]{0,3,2,4,0,4,4,0});
        matrix(STY,new int[]{0,0x84,0,0x8c,0,0x94,0,0},new int[]{0,ZP,0,ABS,0,ZPX,0,0},new int[]{0,3,0,4,0,4,0,0});
        matrix(LDY,new int[]{0,0xa4,0xa0,0xac,0,0xb4,0,0xbc},new int[]{0,ZP,IMM,ABS,0,ZPX,0,ABSX},new int[]{0,3,2,4,0,4,0,4});
        matrix(CPX,new int[]{0,0xe4,0xe0,0xec,0,0,0,0},new int[]{0,ZP,IMM,ABS,0,0,0,0},new int[]{0,3,2,4,0,0,0,0});
        matrix(CPY,new int[]{0,0xc4,0xc0,0xcc,0,0,0,0},new int[]{0,ZP,IMM,ABS,0,0,0,0},new int[]{0,3,2,4,0,0,0,0});
        matrix(BIT,new int[]{0,0x24,0,0x2c,0,0,0,0},new int[]{0,ZP,0,ABS,0,0,0,0},new int[]{0,3,0,4,0,0,0,0});
        rw(ASL,new int[]{0x0a,0x06,0,0x0e,0,0x16,0,0x1e},new int[]{ACC,ZP,0,ABS,0,ZPX,0,ABSX},new int[]{2,5,0,6,0,6,0,7});
        rw(ROL,new int[]{0x2a,0x26,0,0x2e,0,0x36,0,0x3e},new int[]{ACC,ZP,0,ABS,0,ZPX,0,ABSX},new int[]{2,5,0,6,0,6,0,7});
        rw(LSR,new int[]{0x4a,0x46,0,0x4e,0,0x56,0,0x5e},new int[]{ACC,ZP,0,ABS,0,ZPX,0,ABSX},new int[]{2,5,0,6,0,6,0,7});
        rw(ROR,new int[]{0x6a,0x66,0,0x6e,0,0x76,0,0x7e},new int[]{ACC,ZP,0,ABS,0,ZPX,0,ABSX},new int[]{2,5,0,6,0,6,0,7});
        rw(DEC,new int[]{0,0xc6,0,0xce,0,0xd6,0,0xde},new int[]{0,ZP,0,ABS,0,ZPX,0,ABSX},new int[]{0,5,0,6,0,6,0,7});
        rw(INC,new int[]{0,0xe6,0,0xee,0,0xf6,0,0xfe},new int[]{0,ZP,0,ABS,0,ZPX,0,ABSX},new int[]{0,5,0,6,0,6,0,7});
    }
    private static void instruction(int code,int op,int mode,int cycles){OP[code]=op;MODE[code]=mode;CYCLES[code]=cycles;}
    private static void matrix(int op,int[] codes,int[] modes,int[] cycles){for(int i=0;i<codes.length;i++)if(codes[i]!=0)instruction(codes[i],op,modes[i],cycles[i]);}
    private static void rw(int op,int[] codes,int[] modes,int[] cycles){matrix(op,codes,modes,cycles);}

    private byte[] prg, chr;
    private final byte[] prgRam=new byte[0x2000], chrRam=new byte[0x2000], nametable=new byte[0x1000], palette=new byte[32], oam=new byte[256];
    private final int[] pixels=new int[WIDTH*HEIGHT];
    private final boolean[] backgroundOpaque=new boolean[WIDTH*HEIGHT];
    private int mapper, bankSelect, mirroring, a,x,y,sp=0xfd,p=0x24,pc,cycles,pressed,controllerShift,strobe;
    private int ppuCtrl,ppuMask,ppuStatus,ppuOamAddr,ppuV,ppuT,ppuFineX,ppuWriteToggle,scrollX,scrollY;
    private int ppuReadBuffer, frameCounter, ppuCycles;
    private boolean nmiPending;
    private String title="NES";

    @Override public void load(byte[] image) {
        if(image==null || image.length<16) throw new IllegalArgumentException("Arquivo não contém um cabeçalho iNES válido.");
        if(image[0]!='N'||image[1]!='E'||image[2]!='S'||(image[3]&255)!=0x1a) throw new IllegalArgumentException("Cabeçalho iNES inválido.");
        int f6=image[6]&255,f7=image[7]&255;
        if((f7&0x0c)==0x08) throw new IllegalArgumentException("ROM NES 2.0 ainda não é compatível; use iNES 1.0.");
        if((f7&0x0c)!=0) throw new IllegalArgumentException("Formato de cabeçalho iNES desconhecido.");
        int requested=((f6>>>4)&15)|(f7&0xf0);
        if(requested!=0 && requested!=2) throw new IllegalArgumentException("Mapper NES "+requested+" não compatível (somente 0 e 2).");
        int prgBanks=image[4]&255, chrBanks=image[5]&255;
        if(prgBanks<1 || prgBanks>32) throw new IllegalArgumentException("Tamanho PRG iNES inválido ou acima do limite de 512 KiB.");
        if(requested==0 && prgBanks!=1 && prgBanks!=2) throw new IllegalArgumentException("NROM exige 16 ou 32 KiB de PRG ROM.");
        if(requested==2 && prgBanks<2) throw new IllegalArgumentException("UxROM exige ao menos dois bancos PRG.");
        if(chrBanks>1) throw new IllegalArgumentException("Mapper 0/2 neste núcleo aceita até 8 KiB de CHR ROM (ou CHR RAM).");
        int offset=16+((f6&4)!=0?512:0), prgLength=prgBanks*0x4000, chrLength=chrBanks*0x2000;
        if((long)offset+prgLength+chrLength>image.length) throw new IllegalArgumentException("ROM truncada: os bancos declarados no cabeçalho não estão completos.");
        mapper=requested; mirroring=(f6&8)!=0?2:(f6&1); prg=Arrays.copyOfRange(image,offset,offset+prgLength);
        chr=chrBanks==0?null:Arrays.copyOfRange(image,offset+prgLength,offset+prgLength+chrLength);
        title=readTitle(image); bankSelect=0;
        Arrays.fill(prgRam,(byte)0); Arrays.fill(chrRam,(byte)0); Arrays.fill(nametable,(byte)0); Arrays.fill(palette,(byte)0); Arrays.fill(oam,(byte)0);
        if((f6&4)!=0)System.arraycopy(image,16,prgRam,0x1000,512);
        a=x=y=0;sp=0xfd;p=0x24;cycles=0;pressed=controllerShift=strobe=0;
        ppuCtrl=ppuMask=ppuStatus=ppuOamAddr=ppuV=ppuT=ppuFineX=ppuWriteToggle=scrollX=scrollY=ppuReadBuffer=frameCounter=ppuCycles=0;nmiPending=false;
        pc=read16(0xfffc);
    }
    private String readTitle(byte[] image){
        if(image.length<16) return "NES";
        StringBuilder s=new StringBuilder();
        // iNES v1 has no standardized title. Show a deterministic mapper label instead.
        return "NES · Mapper "+((((image[6]&255)>>>4)&15)|((image[7]&0xf0)));
    }
    @Override public int videoWidth(){return WIDTH;}
    @Override public int videoHeight(){return HEIGHT;}
    @Override public String getCartridgeTitle(){return title;}
    @Override public void setButtons(int bits){pressed=bits&255;if(strobe!=0)controllerShift=pressed;}
    @Override public int[] frame(){
        if(prg==null){Arrays.fill(pixels,0xff000000);return pixels;}
        int target=frameCounter+29781, guard=0;
        while(cycles<target && guard++<100000) cycles+=step();
        frameCounter=target; render(); return pixels;
    }

    private int step(){
        if(nmiPending){nmiPending=false;push16(pc);push((p&~0x10)|0x20);p|=4;pc=read16(0xfffa);tickPpu(7);return 7;}
        int opcode=fetch(); int op=OP[opcode], mode=MODE[opcode], base=CYCLES[opcode];
        int address=0, crossed=0;
        if(mode!=IMP && mode!=ACC) { int[] result=address(mode); address=result[0];crossed=result[1]; }
        int value=(mode==IMP||mode==ACC||mode==REL)?0:mode==IMM?address:read(address);
        switch(op){
            case ORA:a=(a|value)&255;setNZ(a);break; case AND:a=(a&value)&255;setNZ(a);break; case EOR:a=(a^value)&255;setNZ(a);break;
            case ADC:adc(value);break; case SBC:adc(value^255);break;
            case LDA:a=value;setNZ(a);break; case LDX:x=value;setNZ(x);break;case LDY:y=value;setNZ(y);break;
            case STA:write(address,a);break;case STX:write(address,x);break;case STY:write(address,y);break;
            case CMP:compare(a,value);break;case CPX:compare(x,value);break;case CPY:compare(y,value);break;
            case BIT:p=(p&~0xc2)|(value&0xc0)|((a&value)==0?2:0);break;
            case ASL:case LSR:case ROL:case ROR:case INC:case DEC:{int v=mode==ACC?a:value;int out;
                switch(op){case ASL:p=(p&~1)|(v>>>7);out=(v<<1)&255;break;case LSR:p=(p&~1)|(v&1);out=v>>>1;break;
                    case ROL:{int c=p&1;p=(p&~1)|(v>>>7);out=((v<<1)|c)&255;break;}case ROR:{int c=p&1;p=(p&~1)|(v&1);out=(v>>>1)|(c<<7);break;}
                    case INC:out=(v+1)&255;break;default:out=(v-1)&255;break;}
                if(mode==ACC)a=out;else write(address,out);setNZ(out);break;}
            case BRK:pc=(pc+1)&65535;push16(pc);push(p|0x30);p|=4;pc=read16(0xfffe);break;
            case JSR:push16((pc-1)&65535);pc=address;break;case JMP:pc=mode==IND?read16Bug(address):address;break;
            case RTS:pc=(pop16()+1)&65535;break;case RTI:p=(pop()&0xef)|0x20;pc=pop16();break;
            case PHP:push(p|0x30);break;case PLP:p=(pop()&0xef)|0x20;break;case PHA:push(a);break;case PLA:a=pop();setNZ(a);break;
            case BPL:case BMI:case BVC:case BVS:case BCC:case BCS:case BNE:case BEQ:
                if(branchCondition(op)){int before=pc;pc=(pc+(byte)address)&65535;base++;if((before&0xff00)!=(pc&0xff00))base++;}break;
            case CLC:p&=~1;break;case SEC:p|=1;break;case CLI:p&=~4;break;case SEI:p|=4;break;case CLV:p&=~0x40;break;case CLD:p&=~8;break;case SED:p|=8;break;
            case TAX:x=a;setNZ(x);break;case TXA:a=x;setNZ(a);break;case DEX:x=(x-1)&255;setNZ(x);break;case INX:x=(x+1)&255;setNZ(x);break;
            case TAY:y=a;setNZ(y);break;case TYA:a=y;setNZ(a);break;case DEY:y=(y-1)&255;setNZ(y);break;case INY:y=(y+1)&255;setNZ(y);break;
            case TXS:sp=x;break;case TSX:x=sp;setNZ(x);break;default:break;
        }
        if(crossed!=0 && (op==ORA||op==AND||op==EOR||op==ADC||op==LDA||op==LDX||op==LDY||op==CMP||op==SBC))base++;
        int spent=Math.max(2,base);tickPpu(spent);return spent;
    }
    private int[] address(int mode){int addr=0,cross=0,lo,hi,base;
        switch(mode){case IMM:addr=fetch();break;case ZP:addr=fetch();break;case ZPX:addr=(fetch()+x)&255;break;case ZPY:addr=(fetch()+y)&255;break;
            case ABS:addr=fetch16();break;case ABSX:base=fetch16();addr=(base+x)&65535;cross=(base&0xff00)!=(addr&0xff00)?1:0;break;
            case ABSY:base=fetch16();addr=(base+y)&65535;cross=(base&0xff00)!=(addr&0xff00)?1:0;break;
            case IND:addr=fetch16();break;case INDX:addr=(fetch()+x)&255;lo=read(addr);hi=read((addr+1)&255);addr=lo|(hi<<8);break;
            case INDY:base=fetch();lo=read(base);hi=read((base+1)&255);base=lo|(hi<<8);addr=(base+y)&65535;cross=(base&0xff00)!=(addr&0xff00)?1:0;break;
            case REL:addr=fetch();break;default:break;}
        return new int[]{addr,cross};
    }
    private boolean branchCondition(int op){switch(op){case BPL:return(p&0x80)==0;case BMI:return(p&0x80)!=0;case BVC:return(p&0x40)==0;case BVS:return(p&0x40)!=0;case BCC:return(p&1)==0;case BCS:return(p&1)!=0;case BNE:return(p&2)==0;default:return(p&2)!=0;}}
    private void adc(int v){int sum=a+v+(p&1),out=sum&255;p=(p&~0xc3)|(sum>255?1:0)|((~(a^v)&(a^out)&0x80)!=0?0x40:0)|(out==0?2:0)|(out&0x80);a=out;}
    private void compare(int r,int v){int d=(r-v)&255;p=(p&~0x83)|(r>=v?1:0)|(d==0?2:0)|(d&0x80);}
    private void setNZ(int v){p=(p&~0x82)|((v&255)==0?2:0)|(v&0x80);}
    private int fetch(){int v=read(pc);pc=(pc+1)&65535;return v;}
    private int fetch16(){int lo=fetch();return lo|(fetch()<<8);}
    private int read16(int addr){return read(addr&65535)|(read((addr+1)&65535)<<8);}
    private int read16Bug(int addr){return read(addr)|(read((addr&0xff00)|((addr+1)&255))<<8);}
    private void push(int v){write(0x100|sp,v);sp=(sp-1)&255;}
    private int pop(){sp=(sp+1)&255;return read(0x100|sp);}
    private void push16(int v){push(v>>>8);push(v&255);}
    private int pop16(){int lo=pop();return lo|(pop()<<8);}
    private int read(int addr){addr&=65535;
        if(addr<0x2000)return prgRam[addr&0x7ff]&255;
        if(addr<0x4000)return ppuReadRegister(addr&7);
        if(addr==0x4016){int bit=strobe!=0?(pressed&1):(controllerShift&1);if(strobe==0)controllerShift=(controllerShift>>>1)|0x80;return 0x40|bit;}
        if(addr>=0x6000&&addr<0x8000)return prgRam[addr-0x6000]&255;
        if(addr>=0x8000)return prgRead(addr);
        return 0;
    }
    private void write(int addr,int value){addr&=65535;value&=255;
        if(addr<0x2000){prgRam[addr&0x7ff]=(byte)value;return;}
        if(addr<0x4000){ppuWriteRegister(addr&7,value);return;}
        if(addr==0x4014){int base=value<<8;for(int i=0;i<256;i++)oam[(ppuOamAddr+i)&255]=(byte)read(base+i);cycles+=513;tickPpu(513);return;}
        if(addr==0x4016){int next=value&1;if(strobe!=0&&next==0)controllerShift=pressed;strobe=next;if(strobe!=0)controllerShift=pressed;return;}
        if(addr>=0x6000&&addr<0x8000){prgRam[addr-0x6000]=(byte)value;return;}
        if(addr>=0x8000&&mapper==2)bankSelect=value%(prg.length/0x4000);
    }
    private int prgRead(int addr){int ix;if(mapper==0)ix=(addr-0x8000)%(prg.length);else if(addr<0xc000)ix=bankSelect*0x4000+(addr-0x8000);else ix=prg.length-0x4000+(addr-0xc000);return prg[ix]&255;}
    private int ppuReadRegister(int reg){switch(reg){case 2:int v=(ppuStatus&255);ppuStatus&=~0x80;ppuWriteToggle=0;return v;
        case 4:return oam[ppuOamAddr]&255;case 7:{int addr=ppuV&0x3fff,value=ppuReadMemory(addr);int out=addr>=0x3f00?value:ppuReadBuffer;ppuReadBuffer=ppuReadMemory((addr+0x1000)&0x3fff);ppuV=(ppuV+((ppuCtrl&4)!=0?32:1))&0x7fff;return out;}default:return 0;}}
    private void ppuWriteRegister(int reg,int value){switch(reg){case 0:ppuCtrl=value;ppuT=(ppuT&0xf3ff)|((value&3)<<10);break;case 1:ppuMask=value;break;case 3:ppuOamAddr=value;break;case 4:oam[ppuOamAddr++&255]=(byte)value;break;
        case 5:if(ppuWriteToggle==0){scrollX=value;ppuFineX=value&7;ppuT=(ppuT&0x7fe0)|(value>>>3);ppuWriteToggle=1;}else{scrollY=value;ppuT=(ppuT&0x0c1f)|((value&0xf8)<<2)|((value&7)<<12);ppuWriteToggle=0;}break;
        case 6:if(ppuWriteToggle==0){ppuT=(ppuT&0xff)|((value&0x3f)<<8);ppuWriteToggle=1;}else{ppuT=(ppuT&0x7f00)|value;ppuV=ppuT;ppuWriteToggle=0;}break;
        case 7:ppuWriteMemory(ppuV&0x3fff,value);ppuV=(ppuV+((ppuCtrl&4)!=0?32:1))&0x7fff;break;}}
    private int ppuReadMemory(int addr){addr&=0x3fff;if(addr<0x2000){if(chr==null)return chrRam[addr]&255;return chr[addr%chr.length]&255;}if(addr<0x3f00){int n=addr-0x2000;return nametable[nametableOffset(n>>>10,n&0x3ff)]&255;}return palette[paletteIndex(addr)]&0x3f;}
    private void ppuWriteMemory(int addr,int value){addr&=0x3fff;if(addr<0x2000){if(chr==null)chrRam[addr]=(byte)value;return;}if(addr<0x3f00){int n=addr-0x2000;nametable[nametableOffset(n>>>10,n&0x3ff)]=(byte)value;return;}palette[paletteIndex(addr)]=(byte)(value&0x3f);}
    private int paletteIndex(int addr){int i=(addr-0x3f00)&31;if(i==0x10||i==0x14||i==0x18||i==0x1c)i-=16;return i;}
    private int nametableOffset(int table,int offset){
        int physical=mirroring==2?table:mirroring==0?(table&1):(table>>>1);
        return physical*0x400+offset;
    }

    private void render(){int universal=palette[0]&0x3f;Arrays.fill(pixels,COLORS[universal]);int baseTable=ppuCtrl&3;
        for(int py=0;py<HEIGHT;py++)for(int px=0;px<WIDTH;px++){
            int sx=(px+scrollX)&511,sy=(py+scrollY)&511,table=baseTable+((sx>>>8)&1)+(((sy>>>8)&1)<<1);table&=3;
            int tx=(sx&255)>>>3,ty=(sy&255)>>>3, ntIndex=nametableOffset(table,ty*32+tx);
            int tile=nametable[ntIndex]&255, attr=nametable[nametableOffset(table,0x3c0+(ty>>>2)*8+(tx>>>2))]&255;
            int shift=((ty&2)<<1)|(tx&2), pal=(attr>>>shift)&3, row=sy&7, col=sx&7;
            int patternBase=(ppuCtrl&0x10)!=0?0x1000:0;int bit=7-col;
            int lo=ppuReadMemory(patternBase+tile*16+row),hi=ppuReadMemory(patternBase+tile*16+row+8);
            int color=((lo>>>bit)&1)|(((hi>>>bit)&1)<<1);if((ppuMask&8)==0||(px<8&&(ppuMask&2)==0))color=0;int palIndex=color==0?0:pal*4+color;
            int pixel=py*WIDTH+px;pixels[pixel]=COLORS[palette[palIndex]&0x3f];backgroundOpaque[pixel]=color!=0;
        }
        if((ppuMask&0x10)!=0)renderSprites();
    }
    private void tickPpu(int cpuCycles){
        int clocks=cpuCycles*3;
        while(clocks-->0){
            ppuCycles++;if(ppuCycles>=341*262)ppuCycles=0;
            if(ppuCycles==341*241+1){ppuStatus|=0x80;if((ppuCtrl&0x80)!=0)nmiPending=true;}
            else if(ppuCycles==341*261+1)ppuStatus&=~0xe0;
        }
    }
    private void renderSprites(){int spriteHeight=(ppuCtrl&0x20)!=0?16:8;
        for(int i=63;i>=0;i--){int sy=(oam[i*4]&255)-1,sx=oam[i*4+3]&255,tile=oam[i*4+1]&255,attr=oam[i*4+2]&255;
            for(int dy=0;dy<spriteHeight;dy++){int yy=sy+dy;if(yy<0||yy>=HEIGHT)continue;int row=(attr&0x80)!=0?spriteHeight-1-dy:dy,bank=(ppuCtrl&8)!=0?0x1000:0;
                int tileNum=tile;
                if(spriteHeight==16){bank=(tile&1)*0x1000;tileNum&=0xfe;if(row>=8){tileNum++;row-=8;}}
                for(int dx=0;dx<8;dx++){int xx=sx+dx;if(xx<0||xx>=WIDTH)continue;int col=(attr&0x40)!=0?dx:7-dx,lo=ppuReadMemory(bank+tileNum*16+row),hi=ppuReadMemory(bank+tileNum*16+row+8),c=((lo>>>col)&1)|(((hi>>>col)&1)<<1);if(c==0)continue;
                    int index=yy*WIDTH+xx;if((attr&0x20)!=0&&backgroundOpaque[index])continue;
                    pixels[index]=COLORS[palette[16+((attr&3)*4)+c]&63];}}
        }
    }
}
