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

## Uso
1. "Scansiona volantino" → scegli la foto del volantino dalla galleria.
2. Attendi (OCR + chiamata LLM). Le pizze vengono salvate localmente.
3. Scrivi un ingrediente nel campo filtro (es. "funghi") per vedere solo le pizze che lo contengono.
4. "Svuota" cancella il database.

## Note / possibili migliorie
- **Foto al volo**: ora usa il picker della galleria. Per scattare direttamente serve CameraX
  (o `ActivityResultContracts.TakePicture`).
- **Normalizzazione ingredienti**: il prompt chiede già minuscolo/singolare, ma per filtri
  perfetti puoi aggiungere un dizionario di sinonimi lato app.
- **Versioni**: AGP/Kotlin/Compose sono a versioni stabili collaudate; Android Studio
  potrebbe proporti aggiornamenti.
- **Costo**: un volantino è qualche centinaio di token → frazioni di centesimo a scansione.
