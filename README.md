# Filtro Gruppo

App Android che si mette tra WhatsApp e le notifiche: nel gruppo che scegli nasconde le notifiche di una o più persone e lascia passare quelle degli altri. La chat su WhatsApp resta intatta, il filtro agisce solo sulle notifiche.

Funziona con WhatsApp e WhatsApp Business, Android 9 o superiore. Nessuna dipendenza esterna, nessun accesso a internet.

## 1. Ottenere l'APK senza Android Studio (GitHub)

1. Su github.com crea un repository nuovo, meglio privato (es. `filtro-gruppo`).
2. Nella pagina del repository scegli **uploading an existing file** e trascina dentro tutto il contenuto della cartella `FiltroGruppo` già decompressa, compresa la cartella `.github`. Poi **Commit changes**.
   - Se la cartella `.github` non viene caricata (alcuni sistemi nascondono le cartelle che iniziano col punto): apri la scheda **Actions** → **set up a workflow yourself**, incolla il contenuto di `.github/workflows/build.yml` e conferma.
3. Scheda **Actions** → la build **Compila APK** parte da sola (altrimenti **Run workflow**). Dopo qualche minuto l'APK compare nella sezione **Releases** del repository come `FiltroGruppo.apk`, scaricabile direttamente dal telefono. È anche tra gli **Artifacts** dell'esecuzione, come zip.

In alternativa, con Android Studio: **Open** sulla cartella, attendi la sincronizzazione, poi **Run** con il telefono collegato.

## 2. Installazione

1. Copia `app-debug.apk` sul telefono e aprilo. Android chiederà di consentire l'installazione di app sconosciute per l'app da cui lo apri (file manager o browser).
2. Play Protect può avvisare che l'app non è verificata: è normale per un'app compilata da te, scegli di installarla comunque.

## 3. Configurazione

1. Apri **Filtro Gruppo** → **Attiva accesso alle notifiche** → attiva *Filtro Gruppo WhatsApp*.
   - Da Android 13 in poi, per le app installate da APK l'interruttore può risultare bloccato con il messaggio «Impostazione con limitazioni». In quel caso: **Apri Info app** → menu **⋮** in alto a destra → **Consenti impostazioni con limitazioni**, poi riprova.
2. **Consenti notifiche del filtro** (serve a mostrare i messaggi degli altri).
3. Scegli il gruppo: appena arriva un messaggio in un gruppo, il nome compare in *Gruppi visti di recente*, toccalo. Dal messaggio successivo compaiono anche i *Mittenti visti in questo gruppo*: tocca la persona da filtrare. Puoi anche scrivere tutto a mano, una persona per riga, esattamente come appare nelle notifiche (nome in rubrica, `~nome` o numero).
4. **Modalità silenziosa (consigliata).** Senza questa modalità il suono di WhatsApp parte prima che il filtro possa intervenire: la notifica sparisce, ma il telefono ha già suonato.
   - Su WhatsApp apri il gruppo → tocca il nome → **Notifiche** → attiva le **notifiche personalizzate** e togli suono, vibrazione e notifiche ad alta priorità. Le voci possono cambiare nome tra versioni di WhatsApp; in alternativa puoi rendere silenzioso il canale di quel gruppo da **Apri le notifiche di WhatsApp**.
   - **Non** usare «Silenzia» di WhatsApp sul gruppo: così WhatsApp non manda proprio le notifiche e il filtro non vede niente.
   - Torna nell'app e attiva **Il gruppo è silenzioso su WhatsApp: fai suonare il filtro**. Il suono si cambia da **Suono delle notifiche del filtro**.

## Come si comporta

- **Messaggio della persona filtrata:** la notifica di WhatsApp viene rimossa. In modalità silenziosa il telefono non suona.
- **Messaggi degli altri:** compaiono nella notifica del filtro, con nome del gruppo, mittenti e foto del gruppo. Toccandola si apre la chat. Le azioni di WhatsApp (Rispondi, Segna come letto) vengono copiate e inoltrate a WhatsApp.
- **Notifiche miste** (lei + altri nella stessa notifica): restano solo i messaggi degli altri.
- In fondo all'app vedi quanti messaggi sono stati filtrati e cosa è successo con l'ultima notifica del gruppo, utile per capire se il nome della persona è scritto giusto.

## Limiti

- Il riconoscimento si basa sui nomi come appaiono nelle notifiche: se rinomini il gruppo o il contatto, aggiorna l'app.
- Se leggi il gruppo aprendo WhatsApp direttamente, la notifica del filtro resta finché non la tocchi o la scorri via.
- Rispondi e Segna come letto dalla notifica del filtro passano per WhatsApp: se sul tuo telefono non funzionano, tocca la notifica e rispondi dalla chat.
- Su alcuni telefoni (Xiaomi, Huawei, alcuni Samsung) conviene escludere l'app dall'ottimizzazione della batteria. Se il filtro smette di funzionare, disattiva e riattiva l'accesso alle notifiche.

## Codice

- `SenderMatcher.java`, `GroupFilter.java`: riconoscimento di gruppo e mittenti, decisione su cosa nascondere. Logica pura, senza dipendenze Android.
- `FilterService.java`: il layer vero e proprio (`NotificationListenerService`): legge le notifiche di WhatsApp, rimuove quelle da nascondere e ripubblica il resto.
- `ActionReceiver.java`: inoltra a WhatsApp le azioni toccate sulla notifica del filtro.
- `MainActivity.java`: schermata delle impostazioni.
