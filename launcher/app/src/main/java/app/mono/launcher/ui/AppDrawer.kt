package app.mono.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mono.launcher.data.AppEntry
import app.mono.launcher.data.normalizeForSearch
import kotlinx.coroutines.delay

/**
 * App-Liste mit Suchfeld.
 * - Live-Filter beim Tippen.
 * - Auto-Öffnen: Passt genau EINE App, wird sie nach [autoOpenDelayMs] gestartet.
 *   Tippt man in der Zeit weiter, wird der Timer abgebrochen (LaunchedEffect startet neu).
 *   autoOpenDelayMs < 0 schaltet das aus.
 */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    autoOpenDelayMs: Int,
    placeholder: String = "Apps suchen",
    onLaunch: (AppEntry) -> Unit,
    onLongPress: ((AppEntry) -> Unit)? = null,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val launch by rememberUpdatedState(onLaunch)

    BackHandler { onClose() }

    // Tastatur automatisch öffnen.
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    val matches = remember(query, apps) { filterApps(apps, query) }

    // Auto-Öffnen nur bei eindeutigem App-Treffer und nicht-leerer Eingabe.
    LaunchedEffect(query, matches) {
        if (autoOpenDelayMs >= 0 && query.isNotBlank() && matches.size == 1) {
            delay(autoOpenDelayMs.toLong())
            launch(matches.first())
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 32.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            // "Los" auf der Tastatur startet den obersten Treffer.
            keyboardActions = KeyboardActions(onGo = { matches.firstOrNull()?.let(launch) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Column {
                    Box {
                        if (query.isEmpty()) T(placeholder, size = 22.sp, alpha = 0.5f)
                        inner()
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White))
                }
            },
        )

        // Liste im unteren Bereich: kurze Listen kleben unten (nah an Daumen und Tastatur).
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        ) {
            items(matches, key = { it.key }) { app ->
                T(
                    app.label, size = 24.sp, maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .tap(onLongClick = onLongPress?.let { { it(app) } }) { launch(app) },
                )
            }
        }
    }
}

/** Treffer: Wortanfang-Treffer zuerst, dann sonstige Teiltreffer, jeweils in Listenreihenfolge (alphabetisch). */
fun filterApps(apps: List<AppEntry>, query: String): List<AppEntry> = filterByKey(apps, query) { it.searchKey }

fun <T> filterByKey(items: List<T>, query: String, key: (T) -> String): List<T> {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return items
    val starts = ArrayList<T>()
    val contains = ArrayList<T>()
    for (item in items) {
        val k = key(item)
        when {
            k.startsWith(q) || k.split(' ', '-', '.').any { it.startsWith(q) } -> starts += item
            k.contains(q) -> contains += item
        }
    }
    return starts + contains
}
