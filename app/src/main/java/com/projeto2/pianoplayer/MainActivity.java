package com.projeto2.pianoplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int PICK_MIDI = 10;
    private TextView midiStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("piano", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(36, 36, 36, 36);

        TextView title = new TextView(this);
        title.setText("Piano MIDI Overlay");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        midiStatus = new TextView(this);
        midiStatus.setGravity(Gravity.CENTER);
        midiStatus.setPadding(0, 24, 0, 24);
        root.addView(midiStatus, new LinearLayout.LayoutParams(-1, -2));

        Button importBtn = new Button(this);
        importBtn.setText("Importar MIDI/MID");
        importBtn.setOnClickListener(v -> openMidiPicker());
        root.addView(importBtn, new LinearLayout.LayoutParams(-1, -2));

        Button overlayBtn = new Button(this);
        overlayBtn.setText("Abrir janela flutuante");
        overlayBtn.setOnClickListener(v -> openOverlay());
        root.addView(overlayBtn, new LinearLayout.LayoutParams(-1, -2));

        Button accessBtn = new Button(this);
        accessBtn.setText("Ativar acessibilidade");
        accessBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessBtn, new LinearLayout.LayoutParams(-1, -2));

        TextView help = new TextView(this);
        help.setText("Use: 1) importe um arquivo .mid/.midi; 2) permita sobreposição; 3) ative a acessibilidade do app; 4) abra a janela flutuante e calibre as teclas.");
        help.setGravity(Gravity.CENTER);
        help.setPadding(0, 24, 0, 0);
        root.addView(help, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        refreshStatus();

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
    }

    private void refreshStatus() {
        String uri = prefs.getString("midi_uri", null);
        midiStatus.setText(uri == null ? "Nenhum MIDI importado" : "MIDI importado e salvo");
    }

    private void openMidiPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/midi", "audio/mid", "audio/x-midi", "application/octet-stream"});
        startActivityForResult(i, PICK_MIDI);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MIDI && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
            prefs.edit().putString("midi_uri", uri.toString()).apply();
            Toast.makeText(this, "MIDI salvo", Toast.LENGTH_SHORT).show();
            refreshStatus();
        }
    }

    private void openOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(i);
            Toast.makeText(this, "Ative a permissão de janela flutuante", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, OverlayService.class));
        else startService(new Intent(this, OverlayService.class));
    }
}
