package com.projeto2.pianoplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {
    private WindowManager wm;
    private View controls;
    private View captureView;
    private View songView;
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean playing = false;
    private boolean paused = false;
    private int nextIndex = 0;
    private long songBaseMs;
    private long realBaseMs;
    private long pausedAt;
    private float speed = 1.0f;
    private List<MidiFile.NoteEvent> notes = new ArrayList<>();
    private final String[] keys = {"Q","E","R","T","Y","U","P","1","2","3","4","5","6","7","8","9","0"};
    private int calibrating = -1;

    public IBinder onBind(android.content.Intent intent) { return null; }

    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("piano", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground(2, makeNotification());
        showControls();
    }

    private Notification makeNotification() {
        String id = "piano_overlay";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(id, "Piano MIDI Overlay", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, id) : new Notification.Builder(this);
        return b.setContentTitle("Piano MIDI Overlay").setContentText("Janela flutuante ativa").setSmallIcon(android.R.drawable.ic_media_play).build();
    }

    private void showControls() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(5, 5, 5, 5);
        box.setBackgroundColor(0xCC111111);
        box.setGravity(Gravity.CENTER);

        Button play = btn("Tocar");
        Button stop = btn("Parar");
        Button songs = btn("Música");
        Button speedBtn = btn("Vel 1x");
        Button calib = btn("Calibrar");
        Button close = btn("Fechar");
        box.addView(play); box.addView(stop); box.addView(songs); box.addView(speedBtn); box.addView(calib); box.addView(close);

        play.setOnClickListener(v -> togglePlay());
        stop.setOnClickListener(v -> stopPlayback());
        songs.setOnClickListener(v -> toggleSongPicker());
        speedBtn.setOnClickListener(v -> {
            if (speed == 1.0f) speed = 0.75f; else if (speed == 0.75f) speed = 0.5f; else speed = 1.0f;
            speedBtn.setText(speed == 1.0f ? "Vel 1x" : (speed == 0.75f ? "Vel .75" : "Vel .5"));
            if (playing && !paused) { songBaseMs = currentSongMs(); realBaseMs = SystemClock.uptimeMillis(); }
            toast("Velocidade: " + speedBtn.getText());
        });
        calib.setOnClickListener(v -> startCalibration());
        close.setOnClickListener(v -> stopSelf());

        controls = box;
        WindowManager.LayoutParams lp = params(dp(86), -2);
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.x = dp(6);
        lp.y = 0;
        wm.addView(controls, lp);
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(9);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(2, 2, 2, 2);
        b.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(34));
        lp.setMargins(0, 2, 0, 2);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private WindowManager.LayoutParams params(int w, int h) {
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        return new WindowManager.LayoutParams(w, h, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
    }

    private void toggleSongPicker() {
        if (songView != null) { wm.removeView(songView); songView = null; return; }
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18, 18, 18, 18);
        box.setBackgroundColor(0xEE222222);
        scroll.addView(box);
        TextView title = new TextView(this);
        title.setText("Selecionar música");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        box.addView(title);
        List<MusicLibrary.Song> songs = MusicLibrary.list(this);
        if (songs.isEmpty()) {
            TextView empty = overlayText("Nenhuma música baixada");
            box.addView(empty);
        } else {
            for (MusicLibrary.Song s : songs) {
                TextView item = overlayText(s.title + "\n" + s.author + " | " + s.notes + " notas | " + MusicLibrary.duration(s.durationMs));
                item.setPadding(10, 14, 10, 14);
                item.setOnClickListener(v -> {
                    stopPlayback();
                    MusicLibrary.select(this, s.id);
                    toast("Selecionada: " + s.title);
                    if (songView != null) { wm.removeView(songView); songView = null; }
                });
                box.addView(item);
            }
        }
        songView = scroll;
        WindowManager.LayoutParams lp = params((int)(getResources().getDisplayMetrics().widthPixels * 0.70f), (int)(getResources().getDisplayMetrics().heightPixels * 0.45f));
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.x = dp(96);
        lp.y = 0;
        wm.addView(songView, lp);
    }

    private TextView overlayText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        return t;
    }

    private void togglePlay() {
        if (playing && !paused) { paused = true; pausedAt = SystemClock.uptimeMillis(); handler.removeCallbacksAndMessages(null); toast("Pausado"); return; }
        if (playing) { paused = false; realBaseMs += SystemClock.uptimeMillis() - pausedAt; scheduleNextBatch(); toast("Continuando"); return; }
        MusicLibrary.Song selected = MusicLibrary.selected(this);
        String saved = selected != null ? selected.uri : prefs.getString("midi_uri", null);
        if (saved == null) { toast("Importe ou baixe um MIDI primeiro"); return; }
        if (PianoAccessibilityService.instance == null) { toast("Ative a acessibilidade do app primeiro"); return; }
        try {
            notes = MidiFile.load(this, Uri.parse(saved));
            notes = simplifyNotes(notes);
            nextIndex = 0; playing = true; paused = false; songBaseMs = 0; realBaseMs = SystemClock.uptimeMillis();
            scheduleNextBatch();
            toast("Tocando " + notes.size() + " notas");
        } catch (Exception e) { toast("Erro ao ler MIDI: " + e.getMessage()); }
    }

    private void stopPlayback() { playing = false; paused = false; nextIndex = 0; handler.removeCallbacksAndMessages(null); toast("Parado"); }

    private long currentSongMs() {
        return songBaseMs + (long)((SystemClock.uptimeMillis() - realBaseMs) * speed);
    }

    private void scheduleNextBatch() {
        if (!playing || paused) return;
        if (nextIndex >= notes.size()) { stopPlayback(); return; }
        long nowSong = currentSongMs();
        long nextTime = notes.get(nextIndex).timeMs;
        long delay = Math.max(0, (long)((nextTime - nowSong) / speed));
        handler.postDelayed(() -> {
            if (!playing || paused) return;
            playDueNotes(currentSongMs() + 12);
            scheduleNextBatch();
        }, delay);
    }

    private void playDueNotes(long targetSongMs) {
        ArrayList<String> batchKeys = new ArrayList<>();
        long batchStart = nextIndex < notes.size() ? notes.get(nextIndex).timeMs : targetSongMs;
        while (nextIndex < notes.size()) {
            MidiFile.NoteEvent ev = notes.get(nextIndex);
            if (ev.timeMs > targetSongMs || ev.timeMs - batchStart > 18) break;
            String k = keyForMidi(ev.note);
            if (!batchKeys.contains(k)) batchKeys.add(k);
            nextIndex++;
        }
        playKeys(batchKeys);
    }

    private List<MidiFile.NoteEvent> simplifyNotes(List<MidiFile.NoteEvent> input) {
        ArrayList<MidiFile.NoteEvent> out = new ArrayList<>();
        String lastKey = "";
        long lastTime = -9999;
        for (MidiFile.NoteEvent ev : input) {
            String k = keyForMidi(ev.note);
            if (k.equals(lastKey) && ev.timeMs - lastTime < 45) continue;
            out.add(ev);
            lastKey = k;
            lastTime = ev.timeMs;
        }
        return out;
    }

    private String keyForMidi(int midiNote) {
        int idx = Math.floorMod(midiNote - 60, keys.length);
        return keys[idx];
    }

    private void playKeys(List<String> batchKeys) {
        if (batchKeys == null || batchKeys.isEmpty()) return;
        if (PianoAccessibilityService.instance == null) { stopPlayback(); toast("Acessibilidade desativada"); return; }
        ArrayList<Float> xs = new ArrayList<>();
        ArrayList<Float> ys = new ArrayList<>();
        for (String k : batchKeys) {
            float x = prefs.getFloat("key_" + k + "_x", -1);
            float y = prefs.getFloat("key_" + k + "_y", -1);
            if (x >= 0 && y >= 0) { xs.add(x); ys.add(y); }
        }
        if (xs.isEmpty()) return;
        float[] xa = new float[xs.size()];
        float[] ya = new float[ys.size()];
        for (int i = 0; i < xs.size(); i++) { xa[i] = xs.get(i); ya[i] = ys.get(i); }
        PianoAccessibilityService.instance.multiTap(xa, ya, 34);
    }

    private void startCalibration() {
        calibrating = 0;
        addCaptureLayer();
        toast("Clique no meio das teclas - " + keys[calibrating]);
    }

    private void addCaptureLayer() {
        if (captureView != null) return;
        TextView v = new TextView(this);
        v.setTextColor(Color.WHITE);
        v.setBackgroundColor(0x44000000);
        v.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        v.setPadding(10, 90, 10, 10);
        v.setText("Clique no meio das teclas - " + keys[calibrating]);
        v.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && calibrating >= 0) {
                String k = keys[calibrating];
                prefs.edit().putFloat("key_" + k + "_x", event.getRawX()).putFloat("key_" + k + "_y", event.getRawY()).apply();
                calibrating++;
                if (calibrating >= keys.length) { finishCalibration(); }
                else ((TextView) view).setText("Clique no meio das teclas - " + keys[calibrating]);
                return true;
            }
            return true;
        });
        captureView = v;
        wm.addView(captureView, params(-1, -1));
    }

    private void finishCalibration() {
        calibrating = -1;
        if (captureView != null) { wm.removeView(captureView); captureView = null; }
        toast("Calibração salva automaticamente");
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    public void onDestroy() {
        stopPlayback();
        if (controls != null) wm.removeView(controls);
        if (captureView != null) wm.removeView(captureView);
        if (songView != null) wm.removeView(songView);
        super.onDestroy();
    }
}
