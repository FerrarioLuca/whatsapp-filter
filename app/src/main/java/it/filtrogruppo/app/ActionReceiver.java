package it.filtrogruppo.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

/**
 * Riceve i tocchi sulle azioni della notifica del filtro (Rispondi, Segna come letto)
 * e li inoltra a WhatsApp, poi chiude la notifica del filtro.
 */
public class ActionReceiver extends BroadcastReceiver {
    static final String ACTION_FORWARD = "it.filtrogruppo.app.FORWARD";

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context context, Intent intent) {
        PendingIntent target = intent.getParcelableExtra(FilterService.EXTRA_TARGET);
        Parcelable[] inputs = intent.getParcelableArrayExtra(FilterService.EXTRA_INPUTS);

        Intent fill = new Intent();
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (inputs != null && inputs.length > 0 && results != null) {
            RemoteInput[] remoteInputs = new RemoteInput[inputs.length];
            for (int i = 0; i < inputs.length; i++) remoteInputs[i] = (RemoteInput) inputs[i];
            RemoteInput.addResultsToIntent(remoteInputs, fill, results);
        }

        if (target != null) {
            try {
                target.send(context, 0, fill);
            } catch (PendingIntent.CanceledException e) {
                Log.w(FilterService.TAG, "Azione di WhatsApp non più valida", e);
            }
        }

        FilterService.clearHistory();
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(FilterService.NOTIF_ID);
    }
}
