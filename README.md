# Pizza Scanner

App Android (Kotlin + Jetpack Compose) che legge un volantino pizzeria da foto,
ne struttura le pizze con un LLM e ti lascia filtrare per ingrediente, offline.

Flusso: **foto → OCR on-device (ML Kit) → Claude Haiku 4.5 (JSON) → SQLite (Room) → filtro locale**.

## Prerequisiti
- JDK 17
- Android SDK (via Android Studio o command-line tools)
- Una chiave API Anthropic

## Configurazione chiave
Copia `local.properties.example` in `local.properties` e inserisci la tua chiave:
```
ANTHROPIC_API_KEY=sk-ant-...
```
La chiave finisce in `BuildConfig.ANTHROPIC_API_KEY` (non nel codice sorgente).

## ⚠️ Gradle wrapper mancante
Questo zip NON contiene il binario `gradle/wrapper/gradle-wrapper.jar` né gli script
`gradlew`/`gradlew.bat` (è un binario, non generabile da qui). Genera il wrapper una
volta sola, in uno dei due modi:

**A) Android Studio (consigliato):** `File > Open` sulla cartella. Al primo sync genera
wrapper e scarica tutto da solo.

**B) Riga di comando** (serve Gradle installato, es. `brew install gradle` o SDKMAN):
```
cd PizzaScanner
gradle wrapper --gradle-version 8.9
```

## Build (VS Code / terminale)
Dopo aver generato il wrapper:
```
./gradlew assembleDebug          # produce app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # installa su device/emulatore collegato
```
In VS Code installa le estensioni *Kotlin* e *Gradle for Java* per editing e lancio dei task;
la compilazione resta affidata a `gradlew`.

## Guida utente

### Permessi richiesti
L'app chiede un solo permesso di sistema, **Internet** (per contattare Claude). Foto e
fotocamera passano dai picker di sistema di Android (galleria e scatto foto), quindi
non serve concedere permessi runtime aggiuntivi.

### Leggere un menu
1. Nella schermata principale tocca **"Leggi menu"**.
2. Scegli la sorgente:
   - **Sfoglia dalla galleria**: seleziona una o più foto del menu già presenti sul telefono.
   - **Scatta foto al menu**: apre la fotocamera; dopo ogni scatto puoi scegliere
     "Scatta un'altra" (per fotografare più pagine dello stesso menu) o "Procedi".
3. Attendi mentre l'app mostra "Leggo il menu…" (OCR on-device + chiamata a Claude Haiku 4.5).
4. Il locale letto compare nell'elenco della home, con nome, numero di piatti e telefoni
   (se presenti sul menu). Se il nome del locale non è leggibile, viene salvato come
   "fonte sconosciuta" (numerata se ne aggiungi più di uno).

### Cercare e filtrare
- Nel campo **"Cerca locale"** in home, digita parte del nome del locale per filtrare l'elenco.
- Apri un locale (tap sulla card) per vedere i suoi piatti.
- Nel campo **"Filtra ingrediente"** del dettaglio:
  - `funghi` → mostra solo i piatti che contengono "funghi".
  - `-funghi` (prefisso `-`) → mostra solo i piatti che **non** contengono "funghi".

### Aggiornare o eliminare un locale
Tieni premuto (long-press) su un locale nella home per aprire il menu azioni:
- **Aggiorna menu (rileggi)**: rifotografa/riseleziona le pagine del menu. Se il nome
  letto corrisponde al locale, i piatti vengono sostituiti; se risulta diverso, l'app
  non tocca il locale originale e crea invece un nuovo locale, avvisandoti.
- **Elimina locale**: cancella il locale e tutti i suoi piatti (richiede conferma).

Nel dettaglio di un locale puoi anche toccare il nome in alto per rinominarlo.

### Tema
Dal menu **⋮** in alto a destra puoi scegliere tra "Caldo" (default), "Adatta al sistema"
e "Scuro freddo". Dallo stesso menu, **"Svuota tutto"** cancella l'intero database
(tutti i locali e i piatti, richiede conferma).

### Errori più comuni
- **"Nessun testo rilevato"**: l'OCR non ha trovato testo nella foto (immagine sfocata,
  troppo scura o non è un menu). Riprova con una foto più nitida e ben illuminata.
- **"Nessun piatto letto"**: il testo è stato letto ma Claude non vi ha riconosciuto piatti.
  Verifica che la foto inquadri davvero l'elenco dei piatti.
- **"Errore: ..."** generico: problema di rete o risposta inattesa dall'API (es. chiave
  API mancante/non valida in `local.properties`, connessione assente). L'app ritenta
  automaticamente fino a 3 volte prima di mostrare l'errore.
- **Avviso "Menu molto lungo"**: se il menu ha moltissimi piatti, la risposta di Claude
  può essere troncata; alcuni piatti finali potrebbero mancare. Se serve, rileggi il
  menu suddividendolo in foto separate.

## Note / possibili migliorie
- **Normalizzazione ingredienti**: il prompt chiede già minuscolo/singolare, ma per filtri
  perfetti puoi aggiungere un dizionario di sinonimi lato app.
- **Versioni**: AGP/Kotlin/Compose sono a versioni stabili collaudate; Android Studio
  potrebbe proporti aggiornamenti.
- **Costo**: un volantino è qualche centinaio di token → frazioni di centesimo a scansione.
