package com.projeto2.pianoplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
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
    private String pendingBlobTitle = "online_sequencer";
    private String pendingBlobAuthor = "Online Sequencer";

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
        web.addJavascriptInterface(new MidiBridge(), "MidiBridge");
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
        if (url != null && url.startsWith("blob:")) {
            new AlertDialog.Builder(this)
                    .setTitle("Adicionar MIDI")
                    .setMessage("Baixar esta música e adicionar à biblioteca?")
                    .setPositiveButton("Adicionar", (d, w) -> downloadBlobMidi(url))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        String safeMime = mimetype == null ? "" : mimetype.toLowerCase();
        String safeUrl = url == null ? "" : url.toLowerCase();
        if (!safeUrl.contains(".mid") && !safeUrl.contains(".midi") && !safeMime.contains("midi")) {
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

    private void downloadBlobMidi(String blobUrl) {
        pendingBlobTitle = cleanTitle(web.getTitle());
        pendingBlobAuthor = "Online Sequencer";
        String js = "(async function(){" +
                "try{" +
                "const r=await fetch('" + blobUrl.replace("'", "\\'") + "');" +
                "const b=await r.blob();" +
                "const reader=new FileReader();" +
                "reader.onloadend=function(){MidiBridge.saveMidi(reader.result, document.title || 'online_sequencer', 'Online Sequencer');};" +
                "reader.onerror=function(){MidiBridge.error('Falha ao ler blob MIDI');};" +
                "reader.readAsDataURL(b);" +
                "}catch(e){MidiBridge.error(String(e));}" +
                "})();";
        web.evaluateJavascript(js, null);
    }

    public class MidiBridge {
        @JavascriptInterface
        public void saveMidi(String dataUrl, String title, String author) {
            try {
                int comma = dataUrl.indexOf(',');
                String base64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String safeTitle = cleanTitle(title == null ? pendingBlobTitle : title);
                saveBytesToLibrary(bytes, safeTitle + ".mid", safeTitle, author == null ? pendingBlobAuthor : author);
                runOnUiThread(() -> toast("Música adicionada à biblioteca"));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Erro ao salvar MIDI: " + e.getMessage()));
            }
        }

        @JavascriptInterface
        public void error(String message) {
            runOnUiThread(() -> toast("Erro ao baixar MIDI: " + message));
        }
    }

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
                addFileToLibrary(file, name, name.replace(".midi", "").replace(".mid", ""), "Online Sequencer");
                runOnUiThread(() -> toast("Música adicionada à biblioteca"));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Erro ao baixar MIDI: " + e.getMessage()));
            }
        }).start();
    }

    private void saveBytesToLibrary(byte[] bytes, String fileName, String title, String author) throws Exception {
        File dir = new File(getFilesDir(), "midis");
        dir.mkdirs();
        File file = new File(dir, System.currentTimeMillis() + "_" + safeFileName(fileName));
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
        addFileToLibrary(file, file.getName(), title, author);
    }

    private void addFileToLibrary(File file, String fileName, String title, String author) throws Exception {
        Uri uri = Uri.fromFile(file);
        List<MidiFile.NoteEvent> events = MidiFile.load(this, uri);
        MusicLibrary.Song song = new MusicLibrary.Song();
        song.id = String.valueOf(System.currentTimeMillis());
        song.title = cleanTitle(title == null ? fileName : title);
        song.author = author == null || author.trim().isEmpty() ? "Online Sequencer" : author;
        song.uri = uri.toString();
        song.notes = events.size();
        song.durationMs = events.isEmpty() ? 0 : events.get(events.size() - 1).timeMs;
        MusicLibrary.add(this, song);
    }

    private String guessName(String url) {
        try {
            String p = Uri.parse(url).getLastPathSegment();
            if (p != null && p.length() > 0) return p.endsWith(".mid") || p.endsWith(".midi") ? p : p + ".mid";
        } catch (Exception ignored) {}
        return "online_sequencer.mid";
    }

    private String cleanTitle(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "online_sequencer";
        String s = raw.replace("Online Sequencer", "").replace("-", " ").replace("|", " ").trim();
        if (s.isEmpty()) s = raw.trim();
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private String safeFileName(String raw) {
        String s = raw == null ? "online_sequencer.mid" : raw.replaceAll("[^a-zA-Z0-9._-]", "_");
        return s.endsWith(".mid") || s.endsWith(".midi") ? s : s + ".mid";
    }

    private void toast(String s) { Toast t = Toast.makeText(this, s, Toast.LENGTH_LONG); t.setGravity(Gravity.BOTTOM, 0, 80); t.show(); }
}
