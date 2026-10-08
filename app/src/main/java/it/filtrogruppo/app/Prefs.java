package it.filtrogruppo.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Impostazioni e piccoli dati salvati (gruppi e mittenti visti, contatore). */
final class Prefs {
    static final String FILE = "filtro_gruppo";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_GROUP = "group";
    static final String KEY_PERSONS = "persons";
    static final String KEY_ALERT_MODE = "alert_mode";
    static final String KEY_FILTERED = "filtered_count";
    static final String KEY_RECENT_GROUPS = "recent_groups";
    static final String KEY_LAST_EVENT = "last_event";
    static final String KEY_ASKED_POST = "asked_post_permission";

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static List<String> list(SharedPreferences sp, String key) {
        return lines(sp.getString(key, ""));
    }

    static List<String> persons(SharedPreferences sp) {
        return list(sp, KEY_PERSONS);
    }

    static String sendersKey(String group) {
        return "senders_" + SenderMatcher.norm(group);
    }

    /** Mette un valore in cima a una lista "recenti", senza duplicati. */
    static void pushRecent(SharedPreferences sp, String key, String value, int max) {
        if (value == null || value.trim().isEmpty()) return;
        List<String> cur = list(sp, key);
        if (!cur.isEmpty() && cur.get(0).equals(value)) return;
        cur.remove(value);
        cur.add(0, value);
        while (cur.size() > max) cur.remove(cur.size() - 1);
        sp.edit().putString(key, String.join("\n", cur)).apply();
    }

    static void addFiltered(SharedPreferences sp, int n) {
        if (n <= 0) return;
        sp.edit().putInt(KEY_FILTERED, sp.getInt(KEY_FILTERED, 0) + n).apply();
    }
}
