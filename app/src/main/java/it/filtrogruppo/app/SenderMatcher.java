package it.filtrogruppo.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Confronto tra nomi di gruppo e mittenti come compaiono nelle notifiche di WhatsApp.
 * Logica pura, senza dipendenze Android, così è testabile.
 */
final class SenderMatcher {

    /** Caratteri invisibili che WhatsApp inserisce attorno ai nomi (marcatori di direzione, zero-width). */
    private static final Pattern INVISIBLE =
            Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]");
    /** Suffisso tipo "(3 messaggi)" o "(2 new messages)". Non tocca "(2024)". */
    private static final Pattern COUNT_SUFFIX =
            Pattern.compile("\\s*\\(\\d+\\s+\\p{L}[^()]*\\)\\s*$");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern PHONE_LIKE = Pattern.compile("^[+0-9()\\-. ]+$");
    private static final Pattern LEADING_TILDE = Pattern.compile("^~\\s*");

    private SenderMatcher() {}

    /** Toglie caratteri invisibili, normalizza Unicode e spazi. Mantiene maiuscole/minuscole. */
    static String clean(CharSequence cs) {
        if (cs == null) return "";
        String s = Normalizer.normalize(cs.toString(), Normalizer.Form.NFKC);
        s = INVISIBLE.matcher(s).replaceAll("");
        s = SPACES.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /** Forma per i confronti: pulita e minuscola. */
    static String norm(CharSequence cs) {
        return clean(cs).toLowerCase(Locale.ROOT);
    }

    /** Nome "pulito" del gruppo a partire dal titolo della notifica. */
    static String groupNameFrom(CharSequence title) {
        String t = clean(title);
        int at = t.indexOf(" @ ");
        if (at >= 0) t = t.substring(at + 3);
        t = COUNT_SUFFIX.matcher(t).replaceFirst("");
        return t.trim();
    }

    /** True se il titolo della notifica corrisponde al gruppo configurato. */
    static boolean titleMatches(CharSequence title, String group) {
        String g = norm(group);
        if (g.isEmpty() || title == null) return false;
        for (String candidate : titleCandidates(clean(title))) {
            if (norm(candidate).equals(g)) return true;
        }
        return false;
    }

    private static List<String> titleCandidates(String t) {
        List<String> out = new ArrayList<>();
        out.add(t);
        out.add(COUNT_SUFFIX.matcher(t).replaceFirst(""));
        // Formato "Mittente @ Gruppo": provo ogni porzione dopo " @ ".
        int idx = t.indexOf(" @ ");
        while (idx >= 0) {
            String rest = t.substring(idx + 3);
            out.add(rest);
            out.add(COUNT_SUFFIX.matcher(rest).replaceFirst(""));
            idx = t.indexOf(" @ ", idx + 3);
        }
        // Formato "Gruppo: Mittente".
        int colon = t.indexOf(": ");
        if (colon > 0) out.add(t.substring(0, colon));
        return out;
    }

    /** Mittente ricavato da un titolo "Mittente @ Gruppo", altrimenti null. */
    static String senderFromTitle(CharSequence title) {
        String t = clean(title);
        int at = t.indexOf(" @ ");
        if (at <= 0) return null;
        return t.substring(0, at).trim();
    }

    /** Divide una riga "Mittente: testo". Restituisce {mittente, testo} oppure null. */
    static String[] splitSenderLine(CharSequence line) {
        if (line == null) return null;
        String s = line.toString();
        int i = s.indexOf(": ");
        if (i <= 0 || i > 80) return null;
        String sender = clean(s.substring(0, i));
        if (sender.isEmpty()) return null;
        return new String[]{sender, s.substring(i + 2)};
    }

    /** True se il mittente corrisponde a una delle persone da filtrare. */
    static boolean senderMatches(CharSequence sender, Collection<String> entries) {
        if (sender == null || entries == null) return false;
        List<String> variants = senderVariants(sender);
        if (variants.isEmpty()) return false;
        for (String entry : entries) {
            String ne = normName(entry);
            if (ne.isEmpty()) continue;
            boolean entryIsPhone = isPhoneLike(entry);
            for (String v : variants) {
                if (v.equals(ne)) return true;
                if (entryIsPhone && isPhoneLike(v) && samePhone(digits(entry), digits(v))) return true;
            }
        }
        return false;
    }

    private static List<String> senderVariants(CharSequence sender) {
        List<String> out = new ArrayList<>();
        String base = normName(sender);
        if (!base.isEmpty()) out.add(base);
        String n = norm(sender);
        if (n.contains("~")) {
            for (String part : n.split("~")) {
                String p = normName(part);
                if (!p.isEmpty() && !out.contains(p)) out.add(p);
            }
        }
        return out;
    }

    private static String normName(CharSequence cs) {
        return LEADING_TILDE.matcher(norm(cs)).replaceFirst("").trim();
    }

    private static boolean isPhoneLike(CharSequence cs) {
        String s = normName(cs);
        return !s.isEmpty() && PHONE_LIKE.matcher(s).matches() && digits(s).length() >= 7;
    }

    private static String digits(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        String s = cs.toString();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    /** Confronta le ultime cifre, così "+39 333 1234567" e "3331234567" coincidono. */
    private static boolean samePhone(String a, String b) {
        int k = Math.min(9, Math.min(a.length(), b.length()));
        if (k < 7) return false;
        return a.substring(a.length() - k).equals(b.substring(b.length() - k));
    }
}
