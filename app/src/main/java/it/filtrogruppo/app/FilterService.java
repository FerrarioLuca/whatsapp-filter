package it.filtrogruppo.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Il "layer" tra WhatsApp e le notifiche: legge le notifiche del gruppo scelto,
 * toglie quelle della persona filtrata e ripubblica il resto.
 */
public class FilterService extends NotificationListenerService {
    static final String TAG = "FiltroGruppo";
    static final String CH_ALERT = "gruppo_con_suono";
    static final String CH_SILENT = "gruppo_silenzioso";
    static final int NOTIF_ID = 1001;
    static final String EXTRA_TARGET = "it.filtrogruppo.app.extra.TARGET";
    static final String EXTRA_INPUTS = "it.filtrogruppo.app.extra.INPUTS";

    private static final String[] WHATSAPP = {"com.whatsapp", "com.whatsapp.w4b"};
    private static final int MAX_HISTORY = 40;
    private static final int MAX_SEEN = 600;
    private static final int MAX_ACTIONS = 3;

    // Stato condiviso con ActionReceiver (stesso processo). Accesso sempre sotto il lock della classe.
    private static final List<Msg> history = new ArrayList<>();
    private static final LinkedHashSet<String> seen = new LinkedHashSet<>();
    private static final LinkedHashSet<String> dismissed = new LinkedHashSet<>();
    private static String historyTitle = "";
    private static PendingIntent lastContentIntent;
    private static Notification.Action[] lastActions;
    private static Icon lastLargeIcon;
    private static String lastPackage = WHATSAPP[0];
    private static String ourChannel;

    /** La notifica del filtro è stata chiusa: i suoi messaggi non vanno riproposti. */
    static synchronized void clearHistory() {
        for (Msg m : history) dismissed.add(m.key);
        GroupFilter.trimSeen(dismissed, MAX_SEEN);
        history.clear();
        ourChannel = null;
    }

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel alert = new NotificationChannel(CH_ALERT,
                c.getString(R.string.ch_alert_name), NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription(c.getString(R.string.ch_alert_desc));
        NotificationChannel silent = new NotificationChannel(CH_SILENT,
                c.getString(R.string.ch_silent_name), NotificationManager.IMPORTANCE_LOW);
        silent.setDescription(c.getString(R.string.ch_silent_desc));
        nm.createNotificationChannel(alert);
        nm.createNotificationChannel(silent);
    }

    @Override
    public void onListenerConnected() {
        ensureChannels(this);
    }

    @Override
    public void onListenerDisconnected() {
        requestRebind(new ComponentName(this, FilterService.class));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(sbn);
        } catch (Throwable t) {
            // In caso di errore non tocco la notifica di WhatsApp: meglio vederla che perderla.
            Log.e(TAG, "Errore nel filtro", t);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && getPackageName().equals(sbn.getPackageName()) && sbn.getId() == NOTIF_ID) {
            clearHistory();
        }
    }

    private static boolean isWhatsApp(String pkg) {
        for (String p : WHATSAPP) if (p.equals(pkg)) return true;
        return false;
    }

    private void handle(StatusBarNotification sbn) {
        if (sbn == null || !isWhatsApp(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if ((n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0) return;
        if (Notification.CATEGORY_CALL.equals(n.category)) return;

        Bundle ex = n.extras;
        CharSequence convTitle = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
        boolean isGroup = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) || convTitle != null;
        List<Msg> msgs = extractMessages(ex, title, sbn.getPostTime());
        String display = SenderMatcher.groupNameFrom(convTitle != null ? convTitle : title);

        SharedPreferences sp = Prefs.sp(this);
        // Memorizzo gruppi e mittenti visti, così nell'app si possono scegliere con un tocco.
        if (isGroup && !display.isEmpty()) {
            Prefs.pushRecent(sp, Prefs.KEY_RECENT_GROUPS, display, 12);
            String key = Prefs.sendersKey(display);
            for (Msg m : msgs) if (m.sender != null) Prefs.pushRecent(sp, key, m.sender, 25);
        }

        if (!sp.getBoolean(Prefs.KEY_ENABLED, true)) return;
        String group = sp.getString(Prefs.KEY_GROUP, "");
        List<String> persons = Prefs.persons(sp);
        if (group == null || group.trim().isEmpty() || persons.isEmpty()) return;
        if (!SenderMatcher.titleMatches(convTitle, group) && !SenderMatcher.titleMatches(title, group)) return;
        if (msgs.isEmpty()) return;

        boolean alertMode = sp.getBoolean(Prefs.KEY_ALERT_MODE, false);
        ensureChannels(this);
        boolean canPost = canPost();

        GroupFilter.Decision d;
        boolean wasActive;
        synchronized (FilterService.class) {
            Set<String> historyKeys = new HashSet<>();
            for (Msg m : history) historyKeys.add(m.key);
            wasActive = !history.isEmpty();
            d = GroupFilter.decide(msgs, persons, alertMode, historyKeys, dismissed, canPost, seen);
            GroupFilter.trimSeen(seen, MAX_SEEN);
            if (d.postOurs) {
                history.addAll(d.addToHistory);
                Collections.sort(history, Msg.BY_TIME);
                while (history.size() > MAX_HISTORY) history.remove(0);
                historyTitle = display.isEmpty() ? group.trim() : display;
                if (n.contentIntent != null) lastContentIntent = n.contentIntent;
                if (n.actions != null) lastActions = n.actions;
                Icon large = n.getLargeIcon();
                if (large != null) lastLargeIcon = large;
                lastPackage = sbn.getPackageName();
            }
        }

        boolean posted = !d.postOurs || postOurs(d.alert, wasActive);
        if (d.cancelOriginal && posted) {
            try {
                cancelNotification(sbn.getKey());
            } catch (RuntimeException e) {
                Log.w(TAG, "Impossibile rimuovere la notifica originale", e);
            }
        }

        Prefs.addFiltered(sp, d.newlyBlocked);
        int blockedHere = 0;
        for (Msg m : msgs) if (m.sender != null && SenderMatcher.senderMatches(m.sender, persons)) blockedHere++;
        String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        sp.edit().putString(Prefs.KEY_LAST_EVENT,
                getString(R.string.last_event_fmt, when, msgs.size(), blockedHere)).apply();
    }

    @SuppressWarnings("deprecation")
    private static List<Msg> extractMessages(Bundle ex, CharSequence title, long postTime) {
        List<Msg> out = new ArrayList<>();
        Parcelable[] arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (arr != null && arr.length > 0) {
            for (Parcelable p : arr) {
                if (!(p instanceof Bundle)) continue;
                Bundle b = (Bundle) p;
                CharSequence text = b.getCharSequence("text");
                long time = b.getLong("time", postTime);
                CharSequence sender = null;
                Parcelable person = b.getParcelable("sender_person");
                if (person instanceof Person) sender = ((Person) person).getName();
                if (sender == null) sender = b.getCharSequence("sender");
                String s = sender == null ? null : SenderMatcher.clean(sender);
                if (s != null && s.isEmpty()) s = null;
                out.add(new Msg(s, text, time, time));
            }
            return out;
        }

        // Ripiego per notifiche senza MessagingStyle: righe "Mittente: testo".
        CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines == null || lines.length == 0) {
            CharSequence t = ex.getCharSequence(Notification.EXTRA_TEXT);
            lines = t == null ? new CharSequence[0] : new CharSequence[]{t};
        }
        String titleSender = SenderMatcher.senderFromTitle(title);
        for (CharSequence line : lines) {
            if (line == null) continue;
            String[] split = SenderMatcher.splitSenderLine(line);
            if (split != null) {
                out.add(new Msg(split[0], split[1], postTime, 0));
            } else if (titleSender != null) {
                out.add(new Msg(titleSender, line, postTime, 0));
            } else {
                // Mittente non riconoscibile: non intervengo su questa notifica.
                return new ArrayList<>();
            }
        }
        return out;
    }

    private boolean canPost() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return false;
        for (String id : new String[]{CH_ALERT, CH_SILENT}) {
            NotificationChannel c = nm.getNotificationChannel(id);
            if (c == null || c.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        }
        return true;
    }

    private boolean postOurs(boolean alert, boolean wasActive) {
        List<Msg> snapshot;
        String title;
        PendingIntent content;
        Notification.Action[] actions;
        Icon large;
        String pkg;
        String channelBefore;
        synchronized (FilterService.class) {
            snapshot = new ArrayList<>(history);
            title = historyTitle;
            content = lastContentIntent;
            actions = lastActions;
            large = lastLargeIcon;
            pkg = lastPackage;
            channelBefore = ourChannel;
        }
        if (snapshot.isEmpty()) return true;

        String channel;
        boolean onlyOnce;
        if (alert) {
            channel = CH_ALERT;
            onlyOnce = false;
        } else if (wasActive && channelBefore != null) {
            channel = channelBefore;   // aggiornamento silenzioso della notifica già visibile
            onlyOnce = true;
        } else {
            channel = CH_SILENT;
            onlyOnce = true;
        }

        try {
            Person me = new Person.Builder().setName(getString(R.string.me)).build();
            Notification.MessagingStyle style = new Notification.MessagingStyle(me);
            style.setConversationTitle(title);
            style.setGroupConversation(true);
            Map<String, Person> people = new HashMap<>();
            int fromOthers = 0;
            for (Msg m : snapshot) {
                Person p = null;
                if (m.sender != null) {
                    fromOthers++;
                    p = people.get(m.sender);
                    if (p == null) {
                        p = new Person.Builder().setName(m.sender).setKey(m.sender).build();
                        people.put(m.sender, p);
                    }
                }
                style.addMessage(new Notification.MessagingStyle.Message(m.text, m.time, p));
            }

            Notification.Builder b = new Notification.Builder(this, channel)
                    .setSmallIcon(R.drawable.ic_stat_filter)
                    .setStyle(style)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(onlyOnce)
                    .setShowWhen(true)
                    .setWhen(snapshot.get(snapshot.size() - 1).time)
                    .setNumber(fromOthers)
                    .setColor(getColor(R.color.accent));
            if (large != null) b.setLargeIcon(large);
            PendingIntent tap = content != null ? content : launchIntent(pkg);
            if (tap != null) b.setContentIntent(tap);
            if (actions != null) {
                int added = 0;
                for (int i = 0; i < actions.length && added < MAX_ACTIONS; i++) {
                    Notification.Action a = actions[i];
                    if (a == null || a.actionIntent == null) continue;
                    b.addAction(wrapAction(a, i));
                    added++;
                }
            }

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(NOTIF_ID, b.build());
            synchronized (FilterService.class) {
                ourChannel = channel;
            }
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Impossibile pubblicare la notifica del filtro", e);
            return false;
        }
    }

    /** Copia un'azione di WhatsApp (Rispondi, Segna come letto) facendola passare da ActionReceiver. */
    private Notification.Action wrapAction(Notification.Action a, int idx) {
        RemoteInput[] inputs = a.getRemoteInputs();
        boolean hasInput = inputs != null && inputs.length > 0;
        Intent i = new Intent(this, ActionReceiver.class)
                .setAction(ActionReceiver.ACTION_FORWARD + "." + idx + (hasInput ? ".reply" : ""))
                .putExtra(EXTRA_TARGET, a.actionIntent);
        if (hasInput) i.putExtra(EXTRA_INPUTS, inputs);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (hasInput) {
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        } else {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(this, idx, i, flags);
        Notification.Action.Builder ab = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat_filter), a.title, pi);
        if (hasInput) for (RemoteInput r : inputs) ab.addRemoteInput(r);
        ab.setAllowGeneratedReplies(a.getAllowGeneratedReplies());
        ab.setSemanticAction(a.getSemanticAction());
        return ab.build();
    }

    private PendingIntent launchIntent(String pkg) {
        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) return null;
        return PendingIntent.getActivity(this, 0, li,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
