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
    private boolean loaded, stickActive;
    private int held;
    private float stickDx, stickDy;

    EmulatorView(Context context) {
        super(context);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        postInvalidateOnAnimation();
    }

    void loadRom(byte[] bytes) {
        core.load(bytes);
        loaded = true;
        postInvalidateOnAnimation();
    }

    String getRomTitle() { return core.getCartridgeTitle(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w=getWidth(), h=getHeight();
        canvas.drawColor(Color.rgb(5, 10, 32));
        paint.setColor(0x182b67ff);
        canvas.drawCircle(w*0.50f,h*0.33f,Math.min(w,h)*0.48f,paint);
        if (loaded) {
            int[] pixels=core.frame();
            if(frameBitmap==null) frameBitmap=Bitmap.createBitmap(GameBoyCore.WIDTH,GameBoyCore.HEIGHT,Bitmap.Config.ARGB_8888);
            frameBitmap.setPixels(pixels,0,GameBoyCore.WIDTH,0,0,GameBoyCore.WIDTH,GameBoyCore.HEIGHT);
            float maxW=w*0.66f, maxH=h*0.49f, scale=Math.min(maxW/GameBoyCore.WIDTH,maxH/GameBoyCore.HEIGHT);
            float dw=GameBoyCore.WIDTH*scale, dh=GameBoyCore.HEIGHT*scale;
            float left=(w-dw)/2f, top=h*0.025f;
            paint.setColor(Color.rgb(33, 225, 255)); paint.setShadowLayer(16,0,0,0xff1adfff);
            canvas.drawRoundRect(new RectF(left-7,top-7,left+dw+7,top+dh+7),12,12,paint);
            paint.clearShadowLayer();
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(frameBitmap,null,new RectF(left,top,left+dw,top+dh),paint);
        } else {
            paint.setColor(Color.rgb(48, 226, 255)); paint.setShadowLayer(12,0,0,0xff1adfff); paint.setTextSize(23);
            canvas.drawText("RETRO VANTA",w*0.39f,h*0.23f,paint); paint.clearShadowLayer();
            paint.setColor(Color.rgb(190, 210, 242)); paint.setTextSize(14);
            canvas.drawText("NÚCLEO DMG ORIGINAL · SELECIONE UMA ROM",w*0.24f,h*0.33f,paint);
        }
        drawController(canvas,w,h);
        postInvalidateOnAnimation();
    }

    private float stickRadius(int w,int h) { return Math.min(w*0.072f,h*0.155f); }
    private float stickCenterX(int w) { return w*0.19f; }
    private float controlCenterY(int h) { return h*0.80f; }
    private float buttonRadius(int w,int h) { return Math.min(w*0.040f,h*0.105f); }
    private float buttonAX(int w) { return w*0.91f; }
    private float buttonBX(int w) { return w*0.80f; }

    private void drawController(Canvas canvas,int w,int h) {
        float cy=controlCenterY(h), sr=stickRadius(w,h), br=buttonRadius(w,h), sx=stickCenterX(w);
        paint.setColor(0x2424dfff); canvas.drawCircle(sx,cy,sr*1.45f,paint);
        paint.setColor(0xff103059); paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(12,0,0,0xff27dcff); canvas.drawCircle(sx,cy,sr,paint); paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(2,sr*0.07f)); paint.setColor(0xff50ddff);
        canvas.drawCircle(sx,cy,sr*0.96f,paint); paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<4;i++) {
            double a=Math.PI*0.5*i;
            paint.setColor(0xffb5f6ff);
            canvas.drawCircle(sx+(float)Math.cos(a)*sr*0.69f,cy+(float)Math.sin(a)*sr*0.69f,sr*0.065f,paint);
        }
        float knobX=sx+stickDx, knobY=cy+stickDy;
        paint.setColor(0xfff5ffff); paint.setShadowLayer(12,0,0,0xffffffff); canvas.drawCircle(knobX,knobY,sr*0.40f,paint); paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(2,sr*0.08f)); paint.setColor(0xff62e9ff); canvas.drawCircle(knobX,knobY,sr*0.40f,paint); paint.setStyle(Paint.Style.FILL);

        drawActionButton(canvas,buttonBX(w),cy,br,0xff42ffb1,"B");
        drawActionButton(canvas,buttonAX(w),cy,br,0xffff4fba,"A");
        drawPill(canvas,w*0.455f,cy+br*1.32f,br*0.72f,br*0.26f,"SELECT");
        drawPill(canvas,w*0.565f,cy+br*1.32f,br*0.72f,br*0.26f,"START");
    }

    private void drawActionButton(Canvas canvas,float x,float y,float r,int color,String label) {
        paint.setColor(color & 0x33ffffff); canvas.drawCircle(x,y,r*1.40f,paint);
        paint.setColor(color); paint.setShadowLayer(14,0,0,color); canvas.drawCircle(x,y,r,paint); paint.clearShadowLayer();
        paint.setColor(0xff07132d); canvas.drawCircle(x,y,r*0.76f,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(2,r*0.09f)); paint.setColor(color); canvas.drawCircle(x,y,r*0.76f,paint); paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE); paint.setTextSize(r*0.72f); paint.setFakeBoldText(true);
        canvas.drawText(label,x-paint.measureText(label)/2f,y+r*0.24f,paint); paint.setFakeBoldText(false);
    }

    private void drawPill(Canvas canvas,float x,float y,float halfWidth,float halfHeight,String label) {
        paint.setColor(0xff102b54); paint.setShadowLayer(7,0,0,0xff357dff);
        canvas.drawRoundRect(new RectF(x-halfWidth,y-halfHeight,x+halfWidth,y+halfHeight),halfHeight,halfHeight,paint); paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(0xff74aaff);
        canvas.drawRoundRect(new RectF(x-halfWidth,y-halfHeight,x+halfWidth,y+halfHeight),halfHeight,halfHeight,paint); paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE); paint.setTextSize(Math.max(9,halfHeight*0.9f)); paint.setFakeBoldText(true);
        canvas.drawText(label,x-paint.measureText(label)/2f,y+halfHeight*0.35f,paint); paint.setFakeBoldText(false);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) {
            held=0; stickActive=false; stickDx=0; stickDy=0; core.setButtons(0); invalidate(); return true;
        }
        if(action!=MotionEvent.ACTION_DOWN && action!=MotionEvent.ACTION_POINTER_DOWN && action!=MotionEvent.ACTION_MOVE && action!=MotionEvent.ACTION_POINTER_UP) return true;
        int w=getWidth(),h=getHeight(),count=event.getPointerCount(),bits=0;
        int lifted=action==MotionEvent.ACTION_POINTER_UP?event.getActionIndex():-1;
        boolean activeStick=false;
        float newDx=0,newDy=0,sx=stickCenterX(w),cy=controlCenterY(h),sr=stickRadius(w,h),br=buttonRadius(w,h);
        for(int i=0;i<count;i++) {
            if(i==lifted) continue;
            float x=event.getX(i), y=event.getY(i);
            float dx=x-sx,dy=y-cy;
            if(y>h*0.59f && x<w*0.38f && dx*dx+dy*dy<(sr*1.55f)*(sr*1.55f)) {
                activeStick=true;
                float length=(float)Math.sqrt(dx*dx+dy*dy), limit=sr*0.78f;
                if(length>limit && length>0) { dx=dx*limit/length; dy=dy*limit/length; }
                newDx=dx;newDy=dy;
                if(Math.abs(dx)>sr*0.23f) bits|=dx>0?1:2;
                if(Math.abs(dy)>sr*0.23f) bits|=dy>0?8:4;
                continue;
            }
            if(y>h*0.61f && near(x,y,buttonAX(w),cy,br*1.35f)) bits|=16;
            else if(y>h*0.61f && near(x,y,buttonBX(w),cy,br*1.35f)) bits|=32;
            else if(y>h*0.61f && near(x,y,w*0.455f,cy+br*1.32f,br*0.9f)) bits|=64;
            else if(y>h*0.61f && near(x,y,w*0.565f,cy+br*1.32f,br*0.9f)) bits|=128;
        }
        stickActive=activeStick;
        if(activeStick) { stickDx=newDx;stickDy=newDy; } else { stickDx=0;stickDy=0; }
        held=bits; core.setButtons(held); invalidate(); return true;
    }

    private boolean near(float x,float y,float cx,float cy,float radius) {
        float dx=x-cx,dy=y-cy; return dx*dx+dy*dy<=radius*radius;
    }
}
