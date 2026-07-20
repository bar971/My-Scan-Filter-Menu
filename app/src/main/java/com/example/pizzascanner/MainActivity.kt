package com.example.pizzascanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pizzascanner.ui.AppTheme
import com.example.pizzascanner.ui.MainViewModel
import com.example.pizzascanner.ui.TemaMode
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val tema by vm.temaMode.collectAsStateWithLifecycle()
            AppTheme(tema) {
                Surface(Modifier.fillMaxSize()) { App(vm) }
            }
        }
    }
}

private fun componiNumero(context: Context, numero: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero")))
}

/** Crea un URI in cache per ricevere uno scatto della fotocamera di sistema. */
private fun creaUriFoto(context: Context): Uri {
    val dir = File(context.cacheDir, "scatti").apply { mkdirs() }
    val file = File(dir, "menu_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun App(vm: MainViewModel) {
    val fonteSel by vm.fonteSelezionata.collectAsStateWithLifecycle()
    if (fonteSel == null) HomeScreen(vm) else DettaglioScreen(vm, fonteSel!!)
}

@Composable
private fun TemaOpzione(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val locali by vm.locali.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val stato by vm.stato.collectAsStateWithLifecycle()
    val avviso by vm.avviso.collectAsStateWithLifecycle()
    val tema by vm.temaMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var menuAperto by remember { mutableStateOf(false) }
    var confermaSvuota by remember { mutableStateOf(false) }
    var dialogTema by remember { mutableStateOf(false) }
    var azioniPer by remember { mutableStateOf<String?>(null) }
    var fonteDaAggiornare by remember { mutableStateOf<String?>(null) }
    var fonteDaEliminare by remember { mutableStateOf<String?>(null) }

    // Scelta sorgente (galleria/fotocamera) e flusso scatti multipli.
    var sceltaSorgente by remember { mutableStateOf(false) }
    var chiediAltra by remember { mutableStateOf(false) }
    val scatti = remember { mutableStateListOf<Uri>() }
    var uriScattoCorrente by remember { mutableStateOf<Uri?>(null) }
    // Dove vanno gli scatti al termine: null = nuovo menu, valorizzato = aggiorna quel locale.
    var aggiornaFonteConScatti by remember { mutableStateOf<String?>(null) }

    fun avviaLettura(uris: List<Uri>) {
        val f = aggiornaFonteConScatti
        if (f != null) vm.aggiorna(f, uris) else vm.processa(uris)
        aggiornaFonteConScatti = null
    }

    // Lettura da GALLERIA (nuovo menu).
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> if (uris.isNotEmpty()) vm.processa(uris) }

    // Rilettura da GALLERIA per AGGIORNARE un locale.
    val updatePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        val f = fonteDaAggiornare
        if (uris.isNotEmpty() && f != null) vm.aggiorna(f, uris)
        fonteDaAggiornare = null
    }

    // Scatto FOTOCAMERA di sistema (una foto per volta).
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok && uriScattoCorrente != null) scatti.add(uriScattoCorrente!!)
        if (scatti.isNotEmpty()) chiediAltra = true
    }

    fun scattaUnaFoto() {
        val uri = creaUriFoto(context)
        uriScattoCorrente = uri
        cameraLauncher.launch(uri)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("My Scan&Filter Menu") },
            actions = {
                Box {
                    IconButton(onClick = { menuAperto = true }) {
                        Text("⋮", style = MaterialTheme.typography.titleLarge)
                    }
                    DropdownMenu(expanded = menuAperto, onDismissRequest = { menuAperto = false }) {
                        DropdownMenuItem(
                            text = { Text("Tema") },
                            onClick = { menuAperto = false; dialogTema = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Svuota tutto") },
                            onClick = { menuAperto = false; confermaSvuota = true }
                        )
                    }
                }
            }
        )

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Button(onClick = {
                aggiornaFonteConScatti = null
                sceltaSorgente = true
            }) { Text("Leggi menu") }

            when (val s = stato) {
                is MainViewModel.Stato.Elaboro ->
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Leggo il menu…")
                    }
                is MainViewModel.Stato.Errore ->
                    Text("Errore: ${s.msg}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                else -> {}
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { vm.setQuery(it) },
                label = { Text("Cerca locale") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            if (locali.isEmpty()) {
                Text(if (query.isBlank()) "Leggi un menu per iniziare." else "Nessun risultato.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(locali, key = { it.fonte }) { loc ->
                        ElevatedCard(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = { vm.apri(loc.fonte) },
                                onLongClick = { azioniPer = loc.fonte }
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(loc.fonte, fontWeight = FontWeight.SemiBold)
                                Text("${loc.numPiatti} piatti",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline)
                                loc.telefoni.forEach { num ->
                                    Text(num,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { componiNumero(context, num) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Scelta sorgente: Galleria o Fotocamera.
    if (sceltaSorgente) {
        AlertDialog(
            onDismissRequest = { sceltaSorgente = false },
            title = { Text("Leggi menu") },
            text = {
                Column {
                    TextButton(onClick = {
                        sceltaSorgente = false
                        val agg = aggiornaFonteConScatti
                        if (agg != null) { fonteDaAggiornare = agg; aggiornaFonteConScatti = null
                            updatePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }) { Text("Sfoglia dalla galleria") }
                    TextButton(onClick = {
                        sceltaSorgente = false
                        scatti.clear()
                        scattaUnaFoto()
                    }) { Text("Scatta foto al menu") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sceltaSorgente = false }) { Text("Annulla") }
            }
        )
    }

    // Dopo ogni scatto: altra foto o procedi?
    if (chiediAltra) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Foto acquisite: ${scatti.size}") },
            text = { Text("Vuoi scattare un'altra pagina del menu o procedere con la lettura?") },
            confirmButton = {
                TextButton(onClick = {
                    chiediAltra = false
                    scattaUnaFoto()
                }) { Text("Scatta un'altra") }
            },
            dismissButton = {
                TextButton(onClick = {
                    chiediAltra = false
                    val daLeggere = scatti.toList()
                    scatti.clear()
                    if (daLeggere.isNotEmpty()) avviaLettura(daLeggere)
                }) { Text("Procedi") }
            }
        )
    }

    // Conferma svuota tutto.
    if (confermaSvuota) {
        AlertDialog(
            onDismissRequest = { confermaSvuota = false },
            title = { Text("Svuota tutto") },
            text = { Text("Eliminare tutti i locali e i piatti salvati? L'operazione non è reversibile.") },
            confirmButton = {
                TextButton(onClick = { vm.svuota(); confermaSvuota = false }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confermaSvuota = false }) { Text("Annulla") }
            }
        )
    }

    // Scelta tema.
    if (dialogTema) {
        AlertDialog(
            onDismissRequest = { dialogTema = false },
            title = { Text("Tema") },
            text = {
                Column {
                    TemaOpzione("Caldo", tema == TemaMode.CALDO) { vm.setTema(TemaMode.CALDO) }
                    TemaOpzione("Adatta al sistema", tema == TemaMode.SISTEMA) { vm.setTema(TemaMode.SISTEMA) }
                    TemaOpzione("Scuro freddo", tema == TemaMode.SCURO_FREDDO) { vm.setTema(TemaMode.SCURO_FREDDO) }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogTema = false }) { Text("Chiudi") }
            }
        )
    }

    // Azioni su un locale (long-press): aggiorna oppure elimina.
    if (azioniPer != null) {
        val f = azioniPer!!
        AlertDialog(
            onDismissRequest = { azioniPer = null },
            title = { Text(f) },
            text = {
                Column {
                    TextButton(onClick = {
                        azioniPer = null
                        aggiornaFonteConScatti = f
                        sceltaSorgente = true
                    }) { Text("Aggiorna menu (rileggi)") }
                    TextButton(onClick = {
                        fonteDaEliminare = f
                        azioniPer = null
                    }) { Text("Elimina locale") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { azioniPer = null }) { Text("Annulla") }
            }
        )
    }

    // Conferma eliminazione del singolo locale.
    if (fonteDaEliminare != null) {
        val f = fonteDaEliminare!!
        AlertDialog(
            onDismissRequest = { fonteDaEliminare = null },
            title = { Text("Elimina locale") },
            text = { Text("Eliminare «$f» e tutti i suoi piatti? L'operazione non è reversibile.") },
            confirmButton = {
                TextButton(onClick = { vm.eliminaFonte(f); fonteDaEliminare = null }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { fonteDaEliminare = null }) { Text("Annulla") }
            }
        )
    }

    // Avviso informativo.
    if (avviso != null) {
        AlertDialog(
            onDismissRequest = { vm.chiudiAvviso() },
            title = { Text("Attenzione") },
            text = { Text(avviso!!) },
            confirmButton = {
                TextButton(onClick = { vm.chiudiAvviso() }) { Text("Ho capito") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DettaglioScreen(vm: MainViewModel, fonte: String) {
    val piatti by vm.piattiLocale.collectAsStateWithLifecycle()
    val filtro by vm.filtroDettaglio.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editFonte by remember { mutableStateOf(false) }

    BackHandler { vm.torna() }

    val telefoni = piatti.firstOrNull { it.telefoni.isNotEmpty() }?.telefoni ?: emptyList()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = { vm.torna() }, contentPadding = PaddingValues(0.dp)) { Text("← Locali") }

        Text(fonte,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { editFonte = true })

        telefoni.forEach { num ->
            Text(num,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { componiNumero(context, num) })
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = filtro,
            onValueChange = { vm.setFiltroDettaglio(it) },
            label = { Text("Filtra ingrediente (- per escludere)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (piatti.isEmpty()) {
            Text(if (filtro.isBlank()) "Nessun piatto." else "Nessun piatto con questo ingrediente.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(piatti, key = { it.id }) { p ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(p.nome, fontWeight = FontWeight.SemiBold)
                            Text(p.ingredienti.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (editFonte) {
        var testo by remember { mutableStateOf(fonte) }
        AlertDialog(
            onDismissRequest = { editFonte = false },
            title = { Text("Modifica fonte") },
            text = {
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    singleLine = true,
                    label = { Text("Nome locale") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rinominaFonte(fonte, testo)
                    editFonte = false
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { editFonte = false }) { Text("Annulla") }
            }
        )
    }
}