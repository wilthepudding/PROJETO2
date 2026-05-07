package com.projeto2.pianoplayer;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MidiFile {
    private static final int MAX_MIDI_BYTES = 5 * 1024 * 1024;

    public static class NoteEvent implements Comparable<NoteEvent> {
        public final long timeMs;
        public final int note;
        public NoteEvent(long timeMs, int note) { this.timeMs = timeMs; this.note = note; }
        public int compareTo(NoteEvent o) { return Long.compare(timeMs, o.timeMs); }
    }

    public static List<NoteEvent> load(Context c, Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("MIDI não informado");
        byte[] data;
        InputStream raw = "file".equals(uri.getScheme()) ? new FileInputStream(uri.getPath()) : c.getContentResolver().openInputStream(uri);
        if (raw == null) throw new IllegalArgumentException("Não foi possível abrir o MIDI");
        try (InputStream in = raw; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_MIDI_BYTES) throw new IllegalArgumentException("MIDI muito grande");
                out.write(buf, 0, n);
            }
            data = out.toByteArray();
        }
        if (data.length < 14) throw new IllegalArgumentException("MIDI inválido");
        Parser p = new Parser(data);
        return p.parse();
    }

    public static boolean looksLikeMidi(byte[] data) {
        return data != null && data.length >= 4 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd';
    }

    private static class Parser {
        byte[] d;
        int pos;
        int division = 480;
        int tempo = 500000;
        List<NoteEvent> out = new ArrayList<>();

        Parser(byte[] data) { d = data; }

        List<NoteEvent> parse() throws Exception {
            if (!"MThd".equals(text(4))) throw new IllegalArgumentException("Arquivo MIDI inválido");
            int headerLen = i32();
            if (headerLen < 6) throw new IllegalArgumentException("Cabeçalho MIDI inválido");
            i16();
            int tracks = i16();
            division = i16();
            if (division <= 0 || (division & 0x8000) != 0) throw new IllegalArgumentException("Divisão MIDI não suportada");
            skip(headerLen - 6);
            for (int k = 0; k < tracks && pos < d.length; k++) track();
            Collections.sort(out);
            return out;
        }

        void track() throws Exception {
            if (remaining() < 8) return;
            if (!"MTrk".equals(text(4))) return;
            int len = i32();
            if (len < 0) throw new IllegalArgumentException("Faixa MIDI inválida");
            int end = Math.min(d.length, pos + len);
            long ticks = 0;
            long us = 0;
            int running = 0;
            while (pos < end) {
                long delta = var();
                us += (delta * tempo) / division;
                ticks += delta;
                int status = u8();
                if (status < 128) {
                    if (running == 0) throw new IllegalArgumentException("Running status MIDI inválido");
                    pos--;
                    status = running;
                } else {
                    running = status;
                }

                if (status == 255) {
                    int type = u8();
                    int metaLen = var();
                    require(metaLen);
                    if (type == 81 && metaLen == 3) {
                        tempo = (u8() << 16) | (u8() << 8) | u8();
                    } else {
                        skip(metaLen);
                    }
                } else if (status == 240 || status == 247) {
                    skip(var());
                } else {
                    int cmd = status & 240;
                    int a = u8();
                    int b = (cmd == 192 || cmd == 208) ? 0 : u8();
                    if (cmd == 144 && b > 0) out.add(new NoteEvent(us / 1000L, a));
                }
            }
            pos = end;
        }

        int remaining() { return d.length - pos; }
        void require(int n) {
            if (n < 0 || pos + n > d.length) throw new IllegalArgumentException("MIDI truncado ou corrompido");
        }
        void skip(int n) { if (n > 0) { require(n); pos += n; } }
        int u8() { require(1); return d[pos++] & 255; }
        int i16() { return (u8() << 8) | u8(); }
        int i32() { return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8(); }
        int var() {
            int v = 0;
            int b;
            int count = 0;
            do {
                if (++count > 4) throw new IllegalArgumentException("Valor MIDI variável inválido");
                b = u8();
                v = (v << 7) | (b & 127);
            } while ((b & 128) != 0);
            return v;
        }
        String text(int n) { require(n); String s = new String(d, pos, n); pos += n; return s; }
    }
}
