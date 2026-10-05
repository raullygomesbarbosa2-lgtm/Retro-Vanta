package com.retrovanta;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class MainActivity extends Activity {
    private static final int OPEN_ROM = 41;
    private EmulatorView emulatorView;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 16, 26));
        root.setPadding(12, 4, 12, 4);

        TextView title = new TextView(this);
        title.setText("RETRO VANTA  ·  DMG / PROTÓTIPO ORIGINAL");
        title.setTextColor(Color.rgb(125, 255, 200));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, 34));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button open = new Button(this);
        open.setText("ABRIR ROM GAME BOY");
        open.setOnClickListener(v -> openRom());
        bar.addView(open, new LinearLayout.LayoutParams(-2, 48));
        status = new TextView(this);
        status.setText("Escolha uma ROM .gb ou .gbc · protótipo experimental");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(12, 0, 0, 0);
        bar.addView(status, new LinearLayout.LayoutParams(0, 48, 1));
        root.addView(bar);

        emulatorView = new EmulatorView(this);
        root.addView(emulatorView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void openRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, OPEN_ROM);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != OPEN_ROM || result != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Arquivo indisponível");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            byte[] rom = out.toByteArray();
            emulatorView.loadRom(rom);
            status.setText("ROM carregada: " + rom.length + " bytes · compatibilidade inicial");
        } catch (Exception e) {
            status.setText("Não consegui abrir esse arquivo.");
        }
    }
}
