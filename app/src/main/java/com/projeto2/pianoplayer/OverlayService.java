package com.projeto2.pianoplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {
    private WindowManager wm;
    private View controls;
    private View captureView;
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean playing = false;
    private boolean paused = false;
    private int nextIndex = 0;
    private long playStart;
    private long pausedAt;
    private List<MidiFile.NoteEvent> notes = new ArrayList<>();
    private final String[] keys = {"Q","E","R","T","Y","U","P","1","2","3","4","5","6","7","8","9","0"};
    private int calibrating = -1;

    public IBinder onBind(Intent intent) { return null; }

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
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(10, 10, 10, 10);
        box.setBackgroundColor(0xDD111111);

        Button play = btn("Tocar/Pausar");
        Button stop = btn("Parar");
        Button calib = btn("Calibrar");
        Button close = btn("Fechar");
        box.addView(play); box.addView(stop); box.addView(calib); box.addView(close);

        play.setOnClickListener(v -> togglePlay());
        stop.setOnClickListener(v -> stopPlayback());
        calib.setOnClickListener(v -> startCalibration());
        close.setOnClickListener(v -> stopSelf());

        controls = box;
        WindowManager.LayoutParams lp = params(-2, -2);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = 70;
        wm.addView(controls, lp);
    }

    private Button btn(String s) { Button b = new Button(this); b.setText(s); b.setTextSize(11); return b; }

    private WindowManager.LayoutParams params(int w, int h) {
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        return new WindowManager.LayoutParams(w, h, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
    }

    private void togglePlay() {
        if (playing && !paused) { paused = true; pausedAt = System.currentTimeMillis(); toast("Pausado"); return; }
        if (playing) { paused = false; playStart += System.currentTimeMillis() - pausedAt; scheduleNext(); toast("Continuando"); return; }
        String saved = prefs.getString("midi_uri", null);
        if (saved == null) { toast("Importe um MIDI primeiro"); return; }
        try {
            notes = MidiFile.load(this, Uri.parse(saved));
            nextIndex = 0; playing = true; paused = false; playStart = System.currentTimeMillis(); scheduleNext();
            toast("Tocando " + notes.size() + " notas");
        } catch (Exception e) { toast("Erro ao ler MIDI: " + e.getMessage()); }
    }

    private void stopPlayback() { playing = false; paused = false; nextIndex = 0; handler.removeCallbacksAndMessages(null); toast("Parado"); }

    private void scheduleNext() {
        if (!playing || paused || nextIndex >= notes.size()) { if (nextIndex >= notes.size()) stopPlayback(); return; }
        MidiFile.NoteEvent ev = notes.get(nextIndex);
        long delay = Math.max(0, ev.timeMs - (System.currentTimeMillis() - playStart));
        handler.postDelayed(() -> { playNote(notes.get(nextIndex).note); nextIndex++; scheduleNext(); }, delay);
    }

    private void playNote(int midiNote) {
        int idx = Math.floorMod(midiNote - 60, keys.length);
        String k = keys[idx];
        float x = prefs.getFloat("key_" + k + "_x", -1);
        float y = prefs.getFloat("key_" + k + "_y", -1);
        if (x < 0 || y < 0 || PianoAccessibilityService.instance == null) return;
        PianoAccessibilityService.instance.tap(x, y);
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
        super.onDestroy();
    }
}
