package com.projeto2.pianoplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final int PICK_MIDI = 10;
    private TextView midiStatus;
    private LinearLayout libraryBox;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("piano", MODE_PRIVATE);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
    }

    protected void onResume() { super.onResume(); refreshStatus(); refreshLibrary(); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(36, 36, 36, 36);
        scroll.addView(root);

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

        EditText search = new EditText(this);
        search.setHint("Pesquisar MIDI no Online Sequencer");
        search.setSingleLine(true);
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));

        Button searchBtn = new Button(this);
        searchBtn.setText("Pesquisar MIDI/MID");
        searchBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, SearchMidiActivity.class);
            i.putExtra("q", search.getText().toString());
            startActivity(i);
        });
        root.addView(searchBtn, new LinearLayout.LayoutParams(-1, -2));

        Button overlayBtn = new Button(this);
        overlayBtn.setText("Abrir janela flutuante");
        overlayBtn.setOnClickListener(v -> openOverlay());
        root.addView(overlayBtn, new LinearLayout.LayoutParams(-1, -2));

        Button accessBtn = new Button(this);
        accessBtn.setText("Ativar acessibilidade");
        accessBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessBtn, new LinearLayout.LayoutParams(-1, -2));

        TextView libTitle = new TextView(this);
        libTitle.setText("\nBiblioteca de músicas baixadas");
        libTitle.setTextSize(20);
        libTitle.setGravity(Gravity.CENTER);
        root.addView(libTitle, new LinearLayout.LayoutParams(-1, -2));

        libraryBox = new LinearLayout(this);
        libraryBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(libraryBox, new LinearLayout.LayoutParams(-1, -2));

        TextView help = new TextView(this);
        help.setText("Toque em uma música para selecionar. Toque e segure para excluir com confirmação.");
        help.setGravity(Gravity.CENTER);
        help.setPadding(0, 24, 0, 0);
        root.addView(help, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
        refreshStatus();
        refreshLibrary();
    }

    private void refreshStatus() {
        MusicLibrary.Song s = MusicLibrary.selected(this);
        String uri = prefs.getString("midi_uri", null);
        if (s != null) midiStatus.setText("Selecionada: " + s.title);
        else midiStatus.setText(uri == null ? "Nenhum MIDI importado" : "MIDI importado e salvo");
    }

    private void refreshLibrary() {
        if (libraryBox == null) return;
        libraryBox.removeAllViews();
        List<MusicLibrary.Song> songs = MusicLibrary.list(this);
        if (songs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nenhuma música baixada ainda.");
            empty.setGravity(Gravity.CENTER);
            libraryBox.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (MusicLibrary.Song s : songs) {
            TextView item = new TextView(this);
            item.setText(s.title + "\nAutor: " + s.author + " | Notas: " + s.notes + " | Duração: " + MusicLibrary.duration(s.durationMs));
            item.setTextSize(16);
            item.setPadding(18, 18, 18, 18);
            item.setBackgroundColor(0xFFEFEFEF);
            item.setOnClickListener(v -> { MusicLibrary.select(this, s.id); refreshStatus(); Toast.makeText(this, "Música selecionada", Toast.LENGTH_SHORT).show(); });
            item.setOnLongClickListener(v -> { confirmDelete(s); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 8, 0, 8);
            libraryBox.addView(item, lp);
        }
    }

    private void confirmDelete(MusicLibrary.Song s) {
        new AlertDialog.Builder(this)
                .setTitle("Deletar música?")
                .setMessage("Deseja deletar \"" + s.title + "\" da biblioteca?")
                .setPositiveButton("Deletar", (d, w) -> { MusicLibrary.delete(this, s.id); refreshStatus(); refreshLibrary(); })
                .setNegativeButton("Cancelar", null)
                .show();
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
