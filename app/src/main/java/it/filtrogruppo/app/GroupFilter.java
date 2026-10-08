package it.filtrogruppo.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Decide cosa fare con una notifica del gruppo da filtrare.
 * Logica pura: FilterService esegue la decisione.
 */
final class GroupFilter {

    static final class Decision {
        /** Rimuovere la notifica originale di WhatsApp. */
        boolean cancelOriginal;
        /** Pubblicare/aggiornare la notifica del filtro. */
        boolean postOurs;
        /** La pubblicazione deve suonare/vibrare. */
        boolean alert;
        /** Messaggi da aggiungere alla notifica del filtro. */
        final List<Msg> addToHistory = new ArrayList<>();
        /** Messaggi della persona filtrata mai visti prima (per il contatore). */
        int newlyBlocked;
    }

    private GroupFilter() {}

    /**
     * @param msgs        messaggi contenuti nella notifica di WhatsApp
     * @param persons     persone da filtrare
     * @param alertMode   il gruppo è silenziato su WhatsApp: tocca al filtro suonare
     * @param historyKeys chiavi dei messaggi già nella notifica del filtro (vuoto = non attiva)
     * @param dismissed   chiavi dei messaggi già mostrati dal filtro e poi chiusi (tocco, swipe, risposta)
     * @param canPost     il filtro può pubblicare notifiche proprie
     * @param seen        chiavi dei messaggi già visti (viene aggiornato)
     */
    static Decision decide(List<Msg> msgs, List<String> persons, boolean alertMode,
                           Set<String> historyKeys, Set<String> dismissed, boolean canPost, Set<String> seen) {
        Decision d = new Decision();
        List<Msg> self = new ArrayList<>();
        List<Msg> blocked = new ArrayList<>();
        List<Msg> allowed = new ArrayList<>();
        for (Msg m : msgs) {
            if (m.sender == null) self.add(m);
            else if (SenderMatcher.senderMatches(m.sender, persons)) blocked.add(m);
            else allowed.add(m);
        }
        // Nessun mittente riconosciuto: non tocco niente.
        if (blocked.isEmpty() && allowed.isEmpty()) return d;

        boolean anyNewAllowed = false;
        for (Msg m : allowed) if (!seen.contains(m.key)) { anyNewAllowed = true; break; }
        for (Msg m : blocked) if (!seen.contains(m.key)) d.newlyBlocked++;
        for (Msg m : msgs) seen.add(m.key);

        if (!canPost) {
            // Il filtro non può mostrare notifiche: rimuovo solo quelle fatte interamente dalla persona filtrata.
            d.cancelOriginal = !blocked.isEmpty() && allowed.isEmpty();
            return d;
        }

        boolean oursActive = !historyKeys.isEmpty();
        // WhatsApp ha già suonato e non c'è niente da nascondere: lascio la notifica originale.
        if (!alertMode && blocked.isEmpty() && !oursActive) return d;

        List<Msg> toShow = new ArrayList<>();
        boolean othersInToShow = false;
        for (Msg m : self) {
            if (!historyKeys.contains(m.key) && !dismissed.contains(m.key)) toShow.add(m);
        }
        for (Msg m : allowed) {
            if (!historyKeys.contains(m.key) && !dismissed.contains(m.key)) {
                toShow.add(m);
                othersInToShow = true;
            }
        }
        Collections.sort(toShow, Msg.BY_TIME);

        d.cancelOriginal = true;
        // Una notifica del filtro nuova ha senso solo se c'è almeno un messaggio di qualcun altro.
        boolean worthPosting = oursActive ? !toShow.isEmpty() : othersInToShow;
        if (worthPosting) {
            d.postOurs = true;
            d.addToHistory.addAll(toShow);
            d.alert = alertMode && anyNewAllowed;
        }
        return d;
    }

    /** Tiene l'insieme dei messaggi visti entro una dimensione massima (scarta i più vecchi). */
    static void trimSeen(Set<String> seen, int max) {
        int excess = seen.size() - max;
        Iterator<String> it = seen.iterator();
        while (excess-- > 0 && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
