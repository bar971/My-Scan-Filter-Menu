package com.example.pizzascanner.llm

import com.example.pizzascanner.data.Pizza
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val MODEL = "claude-haiku-4-5"
private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
private const val MAX_TENTATIVI = 3
private const val TIMEOUT_MS = 90_000L

private const val SYSTEM_PROMPT = """Sei un estrattore di menu. Ricevi il testo grezzo (e disordinato) di una o più foto dello stesso menu, prodotto da OCR.
Restituisci ESCLUSIVAMENTE un oggetto JSON, senza preamboli, senza markdown, senza testo extra.
Formato: {"fonte":"...","telefoni":["...","..."],"piatti":[{"nome":"...","ingredienti":["...","..."]}]}
Regole su "fonte" e "telefoni":
- "fonte" è il nome del locale (ristorante/pizzeria/trattoria) come stampato sul menu. Cerca il nome del locale anche fuori dal logo, es. vicino a indirizzo o telefono. Se assente o illeggibile, usa esattamente "fonte sconosciuta".
- "telefoni" è la lista dei numeri di telefono del locale come stampati (fisso, cellulare, ecc.). Se assenti o illeggibili, usa lista vuota [].
Regole sui piatti:
- Riporta SOLO gli ingredienti effettivamente scritti nel testo, in modo fedele.
- NON dedurre, NON inventare e NON aggiungere ingredienti non presenti, nemmeno se il piatto è un classico noto.
- Se per un piatto gli ingredienti non sono presenti nel testo, metti come unico elemento: "⚠️ ingredienti non presenti".
- Se gli ingredienti ci sono ma non sono leggibili (OCR incerto), metti come unico elemento: "⚠️ ingrediente non letto".
- Se solo alcuni ingredienti sono illeggibili, riporta quelli leggibili e aggiungi in coda "⚠️ ingrediente non letto".
- Scrivi gli ingredienti in minuscolo.
- Ignora prezzi, indirizzi e testo non pertinente ai piatti (tranne nome locale e telefono).
- Se non riconosci alcun piatto, restituisci {"fonte":"...","telefoni":[],"piatti":[]}."""

@Serializable private data class Msg(val role: String, val content: String)
@Serializable private data class Req(
    val model: String,
    val max_tokens: Int,
    val system: String,
    val messages: List<Msg>
)
@Serializable private data class ContentBlock(val type: String, val text: String? = null)
@Serializable private data class Resp(
    val content: List<ContentBlock> = emptyList(),
    val stop_reason: String? = null
)
@Serializable private data class PizzaDto(val nome: String, val ingredienti: List<String> = emptyList())
@Serializable private data class MenuDto(
    val fonte: String = "fonte sconosciuta",
    val telefoni: List<String> = emptyList(),
    val piatti: List<PizzaDto> = emptyList()
)

/** Esito della lettura: i piatti estratti + se la risposta è stata troncata. */
data class EsitoLettura(val piatti: List<Pizza>, val troncato: Boolean)

class LlmService(private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MS
            connectTimeoutMillis = 15_000L
        }
    }

    /**
     * Manda il testo OCR a Claude e ottiene i piatti strutturati.
     * Riprova fino a MAX_TENTATIVI in caso di errore di rete o JSON non valido.
     * Il troncamento (max_tokens) NON viene ritentato ma recuperato sul posto.
     */
    suspend fun estraiPizze(testoOcr: String): EsitoLettura {
        var ultima: Exception? = null
        repeat(MAX_TENTATIVI) { tentativo ->
            try {
                return eseguiUnaVolta(testoOcr)
            } catch (e: Exception) {
                ultima = e
                if (tentativo < MAX_TENTATIVI - 1) delay(600L * (tentativo + 1))
            }
        }
        throw ultima ?: IllegalStateException("Errore sconosciuto")
    }

    private suspend fun eseguiUnaVolta(testoOcr: String): EsitoLettura {
        val resp: String = client.post(ENDPOINT) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(
                Req(
                    model = MODEL,
                    max_tokens = 8000,
                    system = SYSTEM_PROMPT,
                    messages = listOf(Msg("user", testoOcr))
                )
            )
        }.bodyAsText()

        val parsed = json.decodeFromString<Resp>(resp)
        val troncato = parsed.stop_reason == "max_tokens"
        val raw = parsed.content.firstOrNull { it.type == "text" }?.text.orEmpty()

        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        if (!cleaned.startsWith("{")) {
            throw IllegalStateException("Risposta inattesa dall'API: ${resp.take(300)}")
        }

        val menu = try {
            json.decodeFromString<MenuDto>(cleaned)
        } catch (e: Exception) {
            // Recupero solo se troncato; altrimenti rilancio (e scatterà il retry).
            if (troncato) {
                val recuperato = salvaParziale(cleaned) ?: throw e
                json.decodeFromString<MenuDto>(recuperato)
            } else throw e
        }

        val piatti = menu.piatti.map {
            Pizza(nome = it.nome, ingredienti = it.ingredienti, fonte = menu.fonte, telefoni = menu.telefoni)
        }
        return EsitoLettura(piatti, troncato)
    }

    /** Recupera un JSON troncato tagliando fino all'ultimo piatto completo. */
    private fun salvaParziale(cleaned: String): String? {
        val ultimoCompleto = cleaned.lastIndexOf("},")
        if (ultimoCompleto < 0) return null
        return cleaned.substring(0, ultimoCompleto + 1) + "]}"
    }
}