package com.retrovanta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class EmulatorView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final GameBoyCore core = new GameBoyCore();
    private Bitmap frameBitmap;
    private boolean loaded;
    private int held;

    EmulatorView(Context context) {
        super(context);
        setFocusable(true);
        postInvalidateOnAnimation();
    }

    void loadRom(byte[] bytes) {
        core.load(bytes);
        loaded = true;
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w=getWidth(), h=getHeight();
        canvas.drawColor(Color.rgb(18, 24, 34));
        if (loaded) {
            int[] pixels=core.frame();
            if(frameBitmap==null) frameBitmap=Bitmap.createBitmap(GameBoyCore.WIDTH,GameBoyCore.HEIGHT,Bitmap.Config.ARGB_8888);
            frameBitmap.setPixels(pixels,0,GameBoyCore.WIDTH,0,0,GameBoyCore.WIDTH,GameBoyCore.HEIGHT);
            float maxW=w*0.66f, maxH=h*0.51f, scale=Math.min(maxW/GameBoyCore.WIDTH,maxH/GameBoyCore.HEIGHT);
            float dw=GameBoyCore.WIDTH*scale, dh=GameBoyCore.HEIGHT*scale;
            float left=(w-dw)/2f, top=h*0.04f;
            paint.setColor(Color.rgb(125,255,200));
            canvas.drawRoundRect(new RectF(left-7,top-7,left+dw+7,top+dh+7),10,10,paint);
            canvas.drawBitmap(frameBitmap,null,new RectF(left,top,left+dw,top+dh),paint);
        } else {
            paint.setColor(Color.rgb(125,255,200)); paint.setTextSize(22);
            canvas.drawText("RETRO VANTA",w*0.36f,h*0.33f,paint);
            paint.setColor(Color.LTGRAY); paint.setTextSize(15);
            canvas.drawText("Núcleo DMG próprio · escolha uma ROM para iniciar",w*0.20f,h*0.42f,paint);
        }
        drawControls(canvas,w,h);
        postInvalidateOnAnimation();
    }

    private void drawControls(Canvas canvas,int w,int h) {
        float cy=h*0.82f, r=Math.min(h*0.085f,w*0.035f);
        paint.setColor(0x5529d5a0);
        canvas.drawRoundRect(new RectF(w*0.06f,cy-r*2.25f,w*0.06f+r*2.7f,cy+r*2.25f),r,r,paint);
        paint.setColor(0xff7dffc8);
        canvas.drawCircle(w*0.06f+r*0.5f,cy,r*0.62f,paint);
        canvas.drawCircle(w*0.06f+r*2.05f,cy,r*0.62f,paint);
        canvas.drawCircle(w*0.06f+r*1.28f,cy-r*1.35f,r*0.62f,paint);
        canvas.drawCircle(w*0.06f+r*1.28f,cy+r*1.35f,r*0.62f,paint);
        float ax=w*0.86f, bx=w*0.94f;
        paint.setColor(0x778751d0); canvas.drawCircle(ax,cy,r*0.9f,paint); canvas.drawCircle(bx,cy-r*0.25f,r*0.9f,paint);
        paint.setColor(Color.WHITE); paint.setTextSize(12);
        canvas.drawText("B",ax-4,cy+4,paint); canvas.drawText("A",bx-4,cy-r*0.25f+4,paint);
        paint.setColor(0xffd8e2ec); paint.setTextSize(11);
        canvas.drawText("SELECT",w*0.46f,cy+5,paint); canvas.drawText("START",w*0.54f,cy+5,paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction()!=MotionEvent.ACTION_DOWN && event.getAction()!=MotionEvent.ACTION_MOVE && event.getAction()!=MotionEvent.ACTION_UP && event.getAction()!=MotionEvent.ACTION_CANCEL) return true;
        if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL) { held=0; core.setButtons(0); invalidate(); return true; }
        float x=event.getX(), y=event.getY(), w=getWidth(), h=getHeight(), cy=h*0.82f;
        int bits=0;
        if(y>h*0.62f && x<w*0.32f) {
            float cx=w*0.06f+Math.min(h*0.085f,w*0.035f)*1.28f;
            float r=Math.min(h*0.085f,w*0.035f)*1.4f;
            if(Math.abs(x-cx)>Math.abs(y-cy)) bits=x>cx?1:2;
            else bits=y<cy?4:8;
        } else if(y>h*0.62f && x>w*0.78f) {
            bits=x>w*0.90f?16:32;
        } else if(y>h*0.62f) {
            bits=x<w*0.50f?64:128;
        }
        held=bits; core.setButtons(held); invalidate(); return true;
    }
}
