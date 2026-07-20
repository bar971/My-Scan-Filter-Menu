package com.example.pizzascanner.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pizzascanner.BuildConfig
import com.example.pizzascanner.data.AppDatabase
import com.example.pizzascanner.data.Pizza
import com.example.pizzascanner.llm.LlmService
import com.example.pizzascanner.ocr.OcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context

/** Un locale aggregato per la home (raggruppamento per fonte). */
data class Locale(val fonte: String, val telefoni: List<String>, val numPiatti: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    // Tema: persistito in SharedPreferences, default CALDO.
    private val prefs = app.getSharedPreferences("impostazioni", Context.MODE_PRIVATE)
    private val _temaMode = MutableStateFlow(
        runCatching { TemaMode.valueOf(prefs.getString("tema", TemaMode.CALDO.name)!!) }
            .getOrDefault(TemaMode.CALDO)
    )
    val temaMode: StateFlow<TemaMode> = _temaMode
    fun setTema(mode: TemaMode) {
        _temaMode.value = mode
        prefs.edit().putString("tema", mode.name).apply()
    }
    private val dao = AppDatabase.get(app).pizzaDao()
    private val llm = LlmService(BuildConfig.ANTHROPIC_API_KEY)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filtroDettaglio = MutableStateFlow("")
    val filtroDettaglio: StateFlow<String> = _filtroDettaglio

    private val _fonteSelezionata = MutableStateFlow<String?>(null)
    val fonteSelezionata: StateFlow<String?> = _fonteSelezionata

    private val _stato = MutableStateFlow<Stato>(Stato.Pronto)
    val stato: StateFlow<Stato> = _stato

    // Messaggio informativo una tantum (es. aggiornamento che ha creato un nuovo locale).
    private val _avviso = MutableStateFlow<String?>(null)
    val avviso: StateFlow<String?> = _avviso
    fun chiudiAvviso() { _avviso.value = null }

    // HOME: elenco locali, filtrato per NOME locale soltanto.
    val locali: StateFlow<List<Locale>> =
        combine(dao.getAll(), _query) { lista, q ->
            val query = q.trim().lowercase()
            lista.groupBy { it.fonte }
                .filter { (fonte, _) ->
                    query.isEmpty() || fonte.lowercase().contains(query)
                }
                .map { (fonte, piatti) ->
                    Locale(
                        fonte = fonte,
                        telefoni = piatti.firstOrNull { it.telefoni.isNotEmpty() }?.telefoni ?: emptyList(),
                        numPiatti = piatti.size
                    )
                }
                .sortedBy { it.fonte.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // DETTAGLIO: piatti del locale aperto, filtrati per ingrediente.
    // Prefisso "-" = esclusione (mostra i piatti SENZA quell'ingrediente).
    val piattiLocale: StateFlow<List<Pizza>> =
        combine(dao.getAll(), _fonteSelezionata, _filtroDettaglio) { lista, fonte, filtro ->
            if (fonte == null) emptyList()
            else {
                val raw = filtro.trim().lowercase()
                val escludi = raw.startsWith("-")
                val termine = raw.removePrefix("-").trim()
                lista.filter { it.fonte == fonte }
                    .filter {
                        if (termine.isEmpty()) true
                        else {
                            val presente = it.ingredienti.any { ing -> ing.lowercase().contains(termine) }
                            if (escludi) !presente else presente
                        }
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(value: String) { _query.value = value }
    fun setFiltroDettaglio(value: String) { _filtroDettaglio.value = value }

    fun apri(fonte: String) { _filtroDettaglio.value = ""; _fonteSelezionata.value = fonte }
    fun torna() { _fonteSelezionata.value = null }

    /** Lettura di un NUOVO menu: crea/aggiunge un locale (numerato se sconosciuto). */
    fun processa(uris: List<Uri>) {
        viewModelScope.launch {
            _stato.value = Stato.Elaboro
            try {
                val testo = ocrConcat(uris)
                if (testo.isBlank()) { _stato.value = Stato.Errore("Nessun testo rilevato"); return@launch }

                val esito = llm.estraiPizze(testo)
                val nuove = esito.piatti
                if (nuove.isEmpty()) { _stato.value = Stato.Errore("Nessun piatto letto"); return@launch }

                val fonteLetta = nuove.first().fonte
                val fonteFinale = nomeNuovoLocale(fonteLetta)
                val rimappate = if (fonteFinale != fonteLetta) nuove.map { it.copy(fonte = fonteFinale) } else nuove

                val firme = dao.getAllOnce().map { firma(it) }.toMutableSet()
                val daInserire = rimappate.filter { firme.add(firma(it)) }
                dao.insertAll(daInserire)
                _stato.value = Stato.Pronto
                if (esito.troncato) _avviso.value =
                    "Menu molto lungo: la lettura potrebbe essere incompleta (alcuni piatti finali potrebbero mancare). Se serve, rileggilo a foto separate."
            } catch (e: Exception) {
                _stato.value = Stato.Errore(e.message ?: "Errore sconosciuto")
            }
        }
    }

    /**
     * Aggiorna un locale ESISTENTE rileggendo le foto.
     * - Se il nome riletto COINCIDE con quello del locale long-premuto → sostituisce i piatti.
     * - Se NON coincide → NON tocca il locale long-premuto e crea un NUOVO locale
     *   (numerato se sconosciuto), avvisando l'utente. Protegge dal long-press sbagliato.
     */
    fun aggiorna(fonte: String, uris: List<Uri>) {
        viewModelScope.launch {
            _stato.value = Stato.Elaboro
            try {
                val testo = ocrConcat(uris)
                if (testo.isBlank()) { _stato.value = Stato.Errore("Nessun testo rilevato"); return@launch }

                val esito = llm.estraiPizze(testo)
                val lette = esito.piatti
                if (lette.isEmpty()) { _stato.value = Stato.Errore("Nessun piatto letto: locale non modificato"); return@launch }

                val fonteLetta = lette.first().fonte
                val combacia = fonteLetta.trim().lowercase() == fonte.trim().lowercase()

                if (combacia) {
                    val rimappate = lette.map { it.copy(fonte = fonte) }
                    val firme = mutableSetOf<String>()
                    val daInserire = rimappate.filter { firme.add(firma(it)) }
                    dao.deleteFonte(fonte)
                    dao.insertAll(daInserire)
                    _stato.value = Stato.Pronto
                    if (esito.troncato) _avviso.value =
                        "Menu molto lungo: la lettura potrebbe essere incompleta (alcuni piatti finali potrebbero mancare)."
                } else {
                    val fonteFinale = nomeNuovoLocale(fonteLetta)
                    val rimappate = if (fonteFinale != fonteLetta) lette.map { it.copy(fonte = fonteFinale) } else lette
                    val firme = dao.getAllOnce().map { firma(it) }.toMutableSet()
                    val daInserire = rimappate.filter { firme.add(firma(it)) }
                    dao.insertAll(daInserire)
                    _stato.value = Stato.Pronto
                    _avviso.value = "Il menu letto non corrisponde a «$fonte». " +
                            "Ho creato un nuovo locale «$fonteFinale» invece di aggiornarlo."
                }
            } catch (e: Exception) {
                _stato.value = Stato.Errore(e.message ?: "Errore sconosciuto")
            }
        }
    }

    fun eliminaFonte(fonte: String) {
        viewModelScope.launch {
            dao.deleteFonte(fonte)
            if (_fonteSelezionata.value == fonte) _fonteSelezionata.value = null
        }
    }

    fun rinominaFonte(vecchia: String, nuova: String) {
        val pulita = nuova.trim()
        if (pulita.isEmpty() || pulita == vecchia) return
        viewModelScope.launch {
            dao.rinominaFonte(vecchia, pulita)
            if (_fonteSelezionata.value == vecchia) _fonteSelezionata.value = pulita
        }
    }

    fun svuota() {
        viewModelScope.launch { dao.clear() }
    }

    /** Se la fonte è "fonte sconosciuta", la numera in modo univoco (1, 2, 3...). */
    private suspend fun nomeNuovoLocale(fonteLetta: String): String {
        if (fonteLetta.trim().lowercase() != "fonte sconosciuta") return fonteLetta
        val regex = Regex("""fonte sconosciuta (\d+)""", RegexOption.IGNORE_CASE)
        val maxN = dao.getAllOnce()
            .mapNotNull { regex.matchEntire(it.fonte.trim())?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "fonte sconosciuta ${maxN + 1}"
    }

    private suspend fun ocrConcat(uris: List<Uri>): String {
        val blocchi = mutableListOf<String>()
        for (uri in uris) {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uri) }
            val t = OcrService.extract(bitmap)
            if (t.isNotBlank()) blocchi.add(t)
        }
        return blocchi.joinToString("\n\n--- PAGINA SUCCESSIVA ---\n\n")
    }

    private fun firma(p: Pizza): String =
        p.fonte.trim().lowercase() + "|" +
                p.nome.trim().lowercase() + "|" +
                p.ingredienti.map { it.trim().lowercase() }.sorted().joinToString(",")

    private fun decodeBitmap(uri: Uri): Bitmap {
        val cr = getApplication<Application>().contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(cr, uri)
            ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(cr, uri)
        }
    }

    sealed interface Stato {
        data object Pronto : Stato
        data object Elaboro : Stato
        data class Errore(val msg: String) : Stato
    }
}