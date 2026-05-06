package com.projeto2.pianoplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MusicLibrary {
    public static class Song {
        public String id;
        public String title;
        public String author;
        public String uri;
        public int notes;
        public long durationMs;
    }

    private static final String PREF = "piano";
    private static final String KEY = "songs_json";
    private static final String SELECTED = "selected_song_id";

    public static List<Song> list(Context c) {
        ArrayList<Song> out = new ArrayList<>();
        try {
            SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(p.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Song s = new Song();
                s.id = o.optString("id");
                s.title = o.optString("title", "MIDI");
                s.author = o.optString("author", "Online Sequencer");
                s.uri = o.optString("uri");
                s.notes = o.optInt("notes");
                s.durationMs = o.optLong("durationMs");
                out.add(s);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void add(Context c, Song s) {
        try {
            List<Song> songs = list(c);
            songs.add(0, s);
            save(c, songs);
            select(c, s.id);
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("midi_uri", s.uri).apply();
        } catch (Exception ignored) {}
    }

    public static void delete(Context c, String id) {
        List<Song> songs = list(c);
        ArrayList<Song> keep = new ArrayList<>();
        Song removed = null;
        for (Song s : songs) {
            if (s.id.equals(id)) removed = s; else keep.add(s);
        }
        if (removed != null) {
            try {
                Uri u = Uri.parse(removed.uri);
                if ("file".equals(u.getScheme())) new File(u.getPath()).delete();
            } catch (Exception ignored) {}
        }
        save(c, keep);
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (id.equals(p.getString(SELECTED, ""))) {
            p.edit().remove(SELECTED).remove("midi_uri").apply();
        }
    }

    public static void select(Context c, String id) {
        for (Song s : list(c)) {
            if (s.id.equals(id)) {
                c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                        .putString(SELECTED, id)
                        .putString("midi_uri", s.uri)
                        .apply();
                return;
            }
        }
    }

    public static Song selected(Context c) {
        String id = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(SELECTED, "");
        for (Song s : list(c)) if (s.id.equals(id)) return s;
        List<Song> all = list(c);
        return all.isEmpty() ? null : all.get(0);
    }

    private static void save(Context c, List<Song> songs) {
        try {
            JSONArray arr = new JSONArray();
            for (Song s : songs) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("title", s.title);
                o.put("author", s.author);
                o.put("uri", s.uri);
                o.put("notes", s.notes);
                o.put("durationMs", s.durationMs);
                arr.put(o);
            }
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static String duration(long ms) {
        long total = Math.max(0, ms / 1000);
        return (total / 60) + ":" + String.format("%02d", total % 60);
    }
}
