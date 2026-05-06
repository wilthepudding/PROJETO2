package com.projeto2.pianoplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

public class SearchMidiActivity extends Activity {
    private WebView web;
    private EditText search;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Pesquisar MIDI/MID no Online Sequencer");
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        android.widget.Button go = new android.widget.Button(this);
        go.setText("Pesquisar MIDI/MID");
        root.addView(go, new LinearLayout.LayoutParams(-1, -2));
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setDownloadListener(downloadListener);

        go.setOnClickListener(v -> doSearch());
        String q = getIntent().getStringExtra("q");
        if (q != null) { search.setText(q); doSearch(); }
        else web.loadUrl("https://onlinesequencer.net/sequences");
    }

    private void doSearch() {
        try {
            String q = search.getText().toString().trim();
            String url = "https://onlinesequencer.net/sequences?search=" + URLEncoder.encode(q, "UTF-8");
            web.loadUrl(url);
        } catch (Exception e) { toast("Erro na pesquisa"); }
    }

    private final DownloadListener downloadListener = (url, userAgent, contentDisposition, mimetype, contentLength) -> {
        if (!url.toLowerCase().contains(".mid") && !mimetype.toLowerCase().contains("midi")) {
            web.loadUrl(url);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Adicionar MIDI")
                .setMessage("Baixar esta música e adicionar à biblioteca?")
                .setPositiveButton("Adicionar", (d, w) -> downloadMidi(url))
                .setNegativeButton("Cancelar", null)
                .show();
    };

    private void downloadMidi(String url) {
        new Thread(() -> {
            try {
                URL u = new URL(url);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                c.connect();
                String name = guessName(url);
                File dir = new File(getFilesDir(), "midis");
                dir.mkdirs();
                File file = new File(dir, System.currentTimeMillis() + "_" + name);
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                Uri uri = Uri.fromFile(file);
                List<MidiFile.NoteEvent> events = MidiFile.load(this, uri);
                MusicLibrary.Song song = new MusicLibrary.Song();
                song.id = String.valueOf(System.currentTimeMillis());
                song.title = name.replace(".midi", "").replace(".mid", "");
                song.author = "Online Sequencer";
                song.uri = uri.toString();
                song.notes = events.size();
                song.durationMs = events.isEmpty() ? 0 : events.get(events.size() - 1).timeMs;
                MusicLibrary.add(this, song);
                runOnUiThread(() -> toast("Música adicionada à biblioteca"));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Erro ao baixar MIDI: " + e.getMessage()));
            }
        }).start();
    }

    private String guessName(String url) {
        try {
            String p = Uri.parse(url).getLastPathSegment();
            if (p != null && p.length() > 0) return p.endsWith(".mid") || p.endsWith(".midi") ? p : p + ".mid";
        } catch (Exception ignored) {}
        return "online_sequencer.mid";
    }

    private void toast(String s) { Toast t = Toast.makeText(this, s, Toast.LENGTH_LONG); t.setGravity(Gravity.BOTTOM, 0, 80); t.show(); }
}
