package com.projeto2.pianoplayer;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MidiFile {
    public static class NoteEvent implements Comparable<NoteEvent> {
        public final long timeMs;
        public final int note;
        public NoteEvent(long timeMs, int note) { this.timeMs = timeMs; this.note = note; }
        public int compareTo(NoteEvent o) { return Long.compare(timeMs, o.timeMs); }
    }
    public static List<NoteEvent> load(Context c, Uri uri) throws Exception {
        byte[] data;
        try (InputStream in = c.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            data = out.toByteArray();
        }
        Parser p = new Parser(data);
        return p.parse();
    }
    private static class Parser {
        byte[] d; int pos; int division = 480; int tempo = 500000; List<NoteEvent> out = new ArrayList<>();
        Parser(byte[] data) { d = data; }
        List<NoteEvent> parse() throws Exception {
            if (!"MThd".equals(text(4))) throw new IllegalArgumentException("Invalid MIDI file");
            int headerLen = i32(); i16(); int tracks = i16(); division = i16(); pos += Math.max(0, headerLen - 6);
            for (int k = 0; k < tracks && pos < d.length; k++) track();
            Collections.sort(out); return out;
        }
        void track() throws Exception {
            if (!"MTrk".equals(text(4))) return;
            int end = Math.min(d.length, pos + i32()); long ticks = 0; int running = 0;
            while (pos < end) {
                ticks += var(); int status = u8();
                if (status < 128) { pos--; status = running; } else running = status;
                if (status == 255) { int type = u8(); int len = var(); if (type == 81 && len == 3) tempo = (u8() << 16) | (u8() << 8) | u8(); else pos += len; }
                else if (status == 240 || status == 247) pos += var();
                else { int cmd = status & 240; int a = u8(); int b = (cmd == 192 || cmd == 208) ? 0 : u8(); if (cmd == 144 && b > 0) out.add(new NoteEvent((ticks * tempo) / division / 1000L, a)); }
            }
            pos = end;
        }
        int u8() { return d[pos++] & 255; }
        int i16() { return (u8() << 8) | u8(); }
        int i32() { return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8(); }
        int var() { int v = 0, b; do { b = u8(); v = (v << 7) | (b & 127); } while ((b & 128) != 0); return v; }
        String text(int n) { String s = new String(d, pos, n); pos += n; return s; }
    }
}
