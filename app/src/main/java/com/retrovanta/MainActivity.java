package com.retrovanta;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MainActivity extends Activity {
    private static final int OPEN_ROM = 41;
    private static final int MAX_ROM_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SOURCE_BYTES = 16 * 1024 * 1024;
    private EmulatorView emulatorView;
    private TextView status;
    private Button open;

    private static final String[] SYSTEMS = {
        "Game Boy (DMG) — DISPONÍVEL",
        "Game Boy Color — em desenvolvimento",
        "Game Boy Advance — em desenvolvimento",
        "Super Nintendo / SNES — em desenvolvimento",
        "Mega Drive / Genesis — em desenvolvimento",
        "Master System / Mega System — em desenvolvimento",
        "Atari 2600 / 7800 / Lynx — em desenvolvimento",
        "PC Engine — em desenvolvimento",
        "Neo Geo Pocket / Color — em desenvolvimento",
        "Arcade FBNeo / MAME — em desenvolvimento"
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setNavigationBarColor(Color.rgb(5, 10, 32));
        getWindow().setStatusBarColor(Color.rgb(5, 10, 32));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(5, 10, 32));
        root.setPadding(dp(14), dp(4), dp(14), dp(4));

        TextView title = new TextView(this);
        title.setText("RETRO VANTA  /  NÚCLEOS");
        title.setTextColor(Color.rgb(65, 221, 255));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(29)));

        Spinner systemMenu = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SYSTEMS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        systemMenu.setAdapter(adapter);
        GradientDrawable menuBg=new GradientDrawable(); menuBg.setColor(Color.rgb(12,27,57)); menuBg.setCornerRadius(dp(10)); menuBg.setStroke(dp(1),Color.rgb(43,118,213));
        systemMenu.setBackground(menuBg);
        root.addView(systemMenu,new LinearLayout.LayoutParams(-1,dp(42)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        open = new Button(this);
        open.setText("Selecionar ROM / ZIP");
        open.setTextColor(Color.WHITE); open.setTextSize(13);
        GradientDrawable buttonBg = new GradientDrawable(); buttonBg.setColor(Color.rgb(13,33,71)); buttonBg.setCornerRadius(dp(12)); buttonBg.setStroke(dp(2),Color.rgb(36,214,255));
        open.setBackground(buttonBg); open.setPadding(dp(12),0,dp(12),0); open.setOnClickListener(v -> openRom());
        bar.addView(open,new LinearLayout.LayoutParams(-2,dp(44)));
        status = new TextView(this);
        status.setText("Game Boy original · arquivo .gb ou .zip"); status.setTextColor(Color.rgb(179,204,240)); status.setTextSize(12);
        status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(dp(10),0,0,0);
        bar.addView(status,new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(bar);

        emulatorView = new EmulatorView(this);
        root.addView(emulatorView,new LinearLayout.LayoutParams(-1,0,1));
        systemMenu.setSelection(0);
        systemMenu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean supported=position==0;
                open.setEnabled(supported); open.setAlpha(supported?1f:0.45f);
                if(!supported) { status.setText("Este núcleo ainda não foi implementado; o único ativo é Game Boy DMG."); status.setTextColor(Color.rgb(255,190,95)); }
                else { status.setText("Game Boy original · aceita .gb e ZIP com ROM .gb"); status.setTextColor(Color.rgb(179,204,240)); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        setContentView(root);
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void openRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        startActivityForResult(intent,OPEN_ROM);
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=OPEN_ROM||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(InputStream in=getContentResolver().openInputStream(data.getData())) {
            if(in==null) throw new IOException("Não consegui ler esse arquivo.");
            byte[] source=readLimited(in,MAX_SOURCE_BYTES);
            if(isZip(source)) loadFromZip(source); else loadOneRom(source);
        } catch(Exception e) {
            String message=e.getMessage(); status.setText(message==null?"Arquivo inválido ou incompatível.":message);
            status.setTextColor(Color.rgb(255,103,144));
        }
    }

    private byte[] readLimited(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
        while((n=in.read(buffer))!=-1) { if(out.size()+n>limit)throw new IOException("Arquivo grande demais para esta versão."); out.write(buffer,0,n); }
        return out.toByteArray();
    }

    private boolean isZip(byte[] bytes) {
        return bytes.length>=4 && bytes[0]=='P' && bytes[1]=='K' && ((bytes[2]==3&&bytes[3]==4)||(bytes[2]==5&&bytes[3]==6)||(bytes[2]==7&&bytes[3]==8));
    }

    private void loadFromZip(byte[] archive) throws IOException {
        String lastError=null;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName().toLowerCase(Locale.ROOT);
                if(entry.isDirectory()||(!name.endsWith(".gb")&&!name.endsWith(".gbc")))continue;
                try { loadOneRom(readLimited(zip,MAX_ROM_BYTES)); return; }
                catch(IllegalArgumentException ex) { lastError=ex.getMessage(); }
            }
        }
        if(lastError!=null)throw new IOException(lastError);
        throw new IOException("O ZIP não contém uma ROM .gb ou .gbc.");
    }

    private void loadOneRom(byte[] rom) {
        if(rom.length>MAX_ROM_BYTES)throw new IllegalArgumentException("A ROM passa do limite de 8 MB.");
        emulatorView.loadRom(rom);
        status.setText("Executando: "+emulatorView.getRomTitle()); status.setTextColor(Color.rgb(81,255,191));
    }
}
