package com.retrovanta;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private static final int MAX_ROM_BYTES = 8 * 1024 * 1024;
    private EmulatorView emulatorView;
    private TextView status;

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
        title.setText("RETRO VANTA  /  DMG");
        title.setTextColor(Color.rgb(65, 221, 255));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button open = new Button(this);
        open.setText("Abrir ROM de Game Boy");
        open.setTextColor(Color.WHITE);
        open.setTextSize(13);
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(Color.rgb(13, 33, 71));
        buttonBg.setCornerRadius(dp(12));
        buttonBg.setStroke(dp(2), Color.rgb(36, 214, 255));
        open.setBackground(buttonBg);
        open.setPadding(dp(14), 0, dp(14), 0);
        open.setOnClickListener(v -> openRom());
        bar.addView(open, new LinearLayout.LayoutParams(-2, dp(46)));
        status = new TextView(this);
        status.setText("ROMs .gb · núcleo experimental");
        status.setTextColor(Color.rgb(179, 204, 240));
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12), 0, 0, 0);
        bar.addView(status, new LinearLayout.LayoutParams(0, dp(46), 1));
        root.addView(bar);

        emulatorView = new EmulatorView(this);
        root.addView(emulatorView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

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
            if (in == null) throw new IllegalArgumentException("Não consegui ler esse arquivo.");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > MAX_ROM_BYTES) throw new IllegalArgumentException("Arquivo acima de 8 MB; esta versão só aceita ROMs de Game Boy até esse tamanho.");
                out.write(buffer, 0, n);
            }
            byte[] rom = out.toByteArray();
            emulatorView.loadRom(rom);
            status.setText("Carregado: " + emulatorView.getRomTitle());
            status.setTextColor(Color.rgb(81, 255, 191));
        } catch (Exception e) {
            String message = e.getMessage();
            status.setText(message == null ? "Arquivo inválido ou ainda não compatível." : message);
            status.setTextColor(Color.rgb(255, 103, 144));
        }
    }
}
