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
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

public class SearchMidiActivity extends Activity {
    private static final String ALLOWED_HOST = "onlinesequencer.net";
    private static final int MAX_DOWNLOAD_BYTES = 5 * 1024 * 1024;

    private WebView web;
    private EditText search;
    private String pendingBlobTitle = "online_sequencer";
    private String pendingBlobAuthor = "Online Sequencer";
    private String pendingDataUrl;
    private String pendingTitle;
    private String pendingAuthor;

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
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        web.addJavascriptInterface(new MidiBridge(), "MidiBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowedUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAllowedUrl(url);
            }

            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isAllowedUrl(url)) injectMidiCapture();
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.setDownloadListener(downloadListener);

        go.setOnClickListener(v -> doSearch());
        String q = getIntent().getStringExtra("q");
        if (q != null) { search.setText(q); doSearch(); }
        else web.loadUrl("https://onlinesequencer.net/sequences");
    }

    private boolean isAllowedUrl(String raw) {
        try {
            Uri u = Uri.parse(raw);
            String host = u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) && (ALLOWED_HOST.equalsIgnoreCase(host) || (host != null && host.endsWith("." + ALLOWED_HOST)));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void doSearch() {
        try {
            String q = search.getText().toString().trim();
            String url = "https://onlinesequencer.net/sequences?search=" + URLEncoder.encode(q, "UTF-8");
            web.loadUrl(url);
        } catch (Exception e) { toast("Erro na pesquisa"); }
    }

    private void injectMidiCapture() {
        String js = "(function(){" +
                "if(location.hostname.indexOf('onlinesequencer.net')<0)return;" +
                "if(window.__pianoMidiHooked)return;window.__pianoMidiHooked=true;window.__wantMidiDownload=false;" +
                "function title(){return document.title||document.querySelector('h1')?.innerText||'online_sequencer';}" +
                "function sendBlob(blob){try{if(blob.size>5242880){MidiBridge.error('MIDI muito grande');return;}var r=new FileReader();r.onloadend=function(){MidiBridge.offerMidi(r.result,title(),'Online Sequencer');};r.onerror=function(){MidiBridge.error('Falha ao ler MIDI gerado');};r.readAsDataURL(blob);}catch(e){MidiBridge.error(String(e));}}" +
                "var oldCreate=URL.createObjectURL;URL.createObjectURL=function(obj){try{if(window.__wantMidiDownload&&obj instanceof Blob){sendBlob(obj);window.__wantMidiDownload=false;}}catch(e){}return oldCreate.apply(URL,arguments);};" +
                "document.addEventListener('click',function(ev){var a=ev.target.closest&&ev.target.closest('a,button');var txt=(a&&(a.innerText||a.textContent)||'').toLowerCase();if(txt.indexOf('download midi')>=0||txt.indexOf('midi')>=0){window.__wantMidiDownload=true;setTimeout(function(){window.__wantMidiDownload=false;},5000);}},true);" +
                "})();";
        web.evaluateJavascript(js, null);
    }

    private final DownloadListener downloadListener = (url, userAgent, contentDisposition, mimetype, contentLength) -> {
        if (!isAllowedUrl(web.getUrl())) { toast("Página não permitida"); return; }
        if (url != null && url.startsWith("blob:")) {
            downloadBlobMidi(url);
            return;
        }
        String safeMime = mimetype == null ? "" : mimetype.toLowerCase();
        String safeUrl = url == null ? "" : url.toLowerCase();
        if (!safeUrl.contains(".mid") && !safeUrl.contains(".midi") && !safeMime.contains("midi")) {
            if (isAllowedUrl(url)) web.loadUrl(url);
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
        String js = "(function(){" +
                "if(location.hostname.indexOf('onlinesequencer.net')<0)return;" +
                "try{" +
                "var x=new XMLHttpRequest();" +
                "x.open('GET','" + blobUrl.replace("'", "\\'") + "',true);" +
                "x.responseType='blob';" +
                "x.onload=function(){if(x.status===0||x.status===200){if(x.response.size>5242880){MidiBridge.error('MIDI muito grande');return;}var r=new FileReader();r.onloadend=function(){MidiBridge.offerMidi(r.result,document.title||'online_sequencer','Online Sequencer');};r.onerror=function(){MidiBridge.error('Falha ao ler blob MIDI');};r.readAsDataURL(x.response);}};" +
                "x.onerror=function(){};" +
                "x.send();" +
                "}catch(e){}" +
                "})();";
        web.evaluateJavascript(js, null);
    }

    public class MidiBridge {
        @JavascriptInterface
        public void offerMidi(String dataUrl, String title, String author) {
            if (!isAllowedUrl(web.getUrl())) return;
            pendingDataUrl = dataUrl;
            pendingTitle = title;
            pendingAuthor = author;
            runOnUiThread(() -> new AlertDialog.Builder(SearchMidiActivity.this)
                    .setTitle("Adicionar MIDI")
                    .setMessage("Baixar esta música e adicionar à biblioteca?")
                    .setPositiveButton("Adicionar", (d, w) -> savePendingMidi())
                    .setNegativeButton("Cancelar", null)
                    .show());
        }

        @JavascriptInterface
        public void error(String message) {
            runOnUiThread(() -> toast("Erro ao baixar MIDI: " + message));
        }
    }

    private void savePendingMidi() {
        try {
            if (pendingDataUrl == null) { toast("Nenhum MIDI capturado"); return; }
            int comma = pendingDataUrl.indexOf(',');
            String base64 = comma >= 0 ? pendingDataUrl.substring(comma + 1) : pendingDataUrl;
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            validateMidiBytes(bytes);
            String safeTitle = cleanTitle(pendingTitle == null ? pendingBlobTitle : pendingTitle);
            saveBytesToLibrary(bytes, safeTitle + ".mid", safeTitle, pendingAuthor == null ? pendingBlobAuthor : pendingAuthor);
            pendingDataUrl = null;
            toast("Música adicionada à biblioteca");
        } catch (Exception e) {
            toast("Erro ao salvar MIDI: " + e.getMessage());
        }
    }

    private void downloadMidi(String url) {
        if (!isAllowedUrl(url)) { toast("Download bloqueado"); return; }
        new Thread(() -> {
            try {
                URL u = new URL(url);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                c.connect();
                String name = guessName(url);
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                int total = 0;
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_DOWNLOAD_BYTES) throw new IllegalArgumentException("MIDI muito grande");
                        data.write(buf, 0, n);
                    }
                }
                byte[] bytes = data.toByteArray();
                validateMidiBytes(bytes);
                saveBytesToLibrary(bytes, name, name.replace(".midi", "").replace(".mid", ""), "Online Sequencer");
                runOnUiThread(() -> toast("Música adicionada à biblioteca"));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Erro ao baixar MIDI: " + e.getMessage()));
            }
        }).start();
    }

    private void validateMidiBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("MIDI vazio");
        if (bytes.length > MAX_DOWNLOAD_BYTES) throw new IllegalArgumentException("MIDI muito grande");
        if (!MidiFile.looksLikeMidi(bytes)) throw new IllegalArgumentException("Arquivo não parece ser MIDI válido");
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
