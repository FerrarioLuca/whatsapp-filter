package it.filtrogruppo.app;

import java.util.Comparator;

/** Un messaggio letto da una notifica di WhatsApp. sender == null significa "io". */
final class Msg {
    static final Comparator<Msg> BY_TIME = (a, b) -> Long.compare(a.time, b.time);

    final String sender;
    final CharSequence text;
    final long time;
    /** Identità stabile del messaggio, per non contarlo o mostrarlo due volte. */
    final String key;

    Msg(String sender, CharSequence text, long time, long keyTime) {
        this.sender = sender;
        this.text = text == null ? "" : text;
        this.time = time;
        this.key = (sender == null ? "" : sender) + '\u0001' + keyTime + '\u0001' + this.text;
    }
}
