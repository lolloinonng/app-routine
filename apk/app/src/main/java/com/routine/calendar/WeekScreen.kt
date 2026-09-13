package com.routine.calendar

import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

val typeColors = mapOf(
    "routine" to Color(0xFF4285F4),
    "free" to Color(0xFF34A853),
    "trade" to Color(0xFFB7791F),
    "sport" to Color(0xFFEA4335),
)

// Altezza di un'ora in timeline: come il sito (60px) ma leggera da disegnare
private val HOUR_H: Dp = 60.dp
private val MIN_H: Dp = 28.dp

private fun uiPrefs(ctx: Context) = ctx.getSharedPreferences("routine_ui", Context.MODE_PRIVATE)

/** Programma sveglie di sistema per gli eventi di OGGI: cambio evento + preavviso 5'. */
fun scheduleTodayEvents(ctx: Context, data: Map<String, List<Ev>>) {
    val list = data[todayKey()] ?: return
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()
    val arr = JSONArray()
    for (ev in list) {
        val p = ev.start.split(":")
        if (p.size != 2) continue
        cal.set(Calendar.HOUR_OF_DAY, p[0].toInt())
        cal.set(Calendar.MINUTE, p[1].toInt())
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val tStart = cal.timeInMillis
        if (tStart > now) {
            arr.put(
                JSONObject().put("title", "▶ " + ev.title)
                    .put("body", "Iniziato alle ${ev.start} • fine ${ev.end}")
                    .put("at", tStart).put("tag", "ev-" + ev.start),
            )
        }
        val tLead = tStart - 5 * 60 * 1000
        if (tLead > now) {
            arr.put(
                JSONObject().put("title", "⏰ Tra 5 minuti: " + ev.title)
                    .put("body", "Inizio alle ${ev.start}")
                    .put("at", tLead).put("tag", "pre-" + ev.start),
            )
        }
    }
    EventScheduler.schedule(ctx, arr.toString())
}

data class EditTarget(val day: String, val ev: Ev?)

/** Evento con minuti assoluti (00:00 di fine = 1440). */
private data class Placed(val ev: Ev, val sMin: Int, val eMin: Int) {
    val dur: Int get() = eMin - sMin
}

private fun placeEvents(evs: List<Ev>): List<Placed> = evs.map { ev ->
    var s = toMin(ev.start)
    var e = if (ev.end == "00:00") 1440 else toMin(ev.end)
    if (e <= s) e = s + 30
    Placed(ev, s, e)
}

@Composable
fun WeekScreen(uid: String, email: String?, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var weekOffset by remember { mutableStateOf(0) }
    val monday = remember(weekOffset) { getMonday(weekOffset) }
    val weekId = remember(monday) { weekIdOf(monday) }
    var data by remember { mutableStateOf<Map<String, List<Ev>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var selectedDay by remember { mutableStateOf(todayKey()) }
    var notifyOn by remember { mutableStateOf(uiPrefs(ctx).getBoolean("notify_on", false)) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var showReset by remember { mutableStateOf(false) }

    DisposableEffect(uid, weekId) {
        loading = true
        val l: ValueEventListener = WeekRepo.listen(uid, weekId) { d, _ ->
            data = d
            loading = false
            if (uiPrefs(ctx).getBoolean("notify_on", false)) scheduleTodayEvents(ctx, d)
        }
        onDispose { WeekRepo.unlisten(uid, weekId, l) }
    }

    fun setNotify(on: Boolean) {
        notifyOn = on
        uiPrefs(ctx).edit().putBoolean("notify_on", on).apply()
        if (on) {
            scheduleTodayEvents(ctx, data)
            Toast.makeText(ctx, "Notifiche attive: avvisi a ogni cambio evento", Toast.LENGTH_SHORT).show()
        } else {
            EventScheduler.cancelAll(ctx)
            Toast.makeText(ctx, "Notifiche disattivate", Toast.LENGTH_SHORT).show()
        }
    }

    // Posizioni calcolate una sola volta per giorno (niente ricalcoli a ogni scroll)
    val placed = remember(data, selectedDay) { placeEvents(data[selectedDay].orEmpty()) }

    val scroll = rememberScrollState()
    val y8px = remember(density) { with(density) { (HOUR_H * 8).toPx() }.toInt() }
    // Come il sito: all'apertura punta alle 8 (solo settimana corrente)
    LaunchedEffect(weekId, selectedDay) {
        scroll.scrollTo(if (weekOffset == 0) y8px else 0)
    }

    Column(Modifier.fillMaxSize()) {
        // Barra titolo + settimana
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Routine", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { weekOffset-- }) { Icon(Icons.Filled.ChevronLeft, "Prec") }
            IconButton(onClick = { weekOffset = 0 }) { Text("Oggi", style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = { weekOffset++ }) { Icon(Icons.Filled.ChevronRight, "Succ") }
        }
        Text(weekLabel(monday), modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        if (email != null) Text(email, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)

        // Azioni
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { setNotify(!notifyOn) }) {
                Icon(if (notifyOn) Icons.Filled.Notifications else Icons.Filled.NotificationsOff, "Notifiche")
            }
            IconButton(onClick = { showReset = true }) { Icon(Icons.Filled.Refresh, "Reset") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { editTarget = EditTarget(selectedDay, null) }) { Icon(Icons.Filled.Add, "Nuovo") }
            IconButton(onClick = onLogout) { Icon(Icons.Filled.Logout, "Esci") }
        }

        // Giorni
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val df = remember { SimpleDateFormat("d", Locale.ITALIAN) }
            for (dk in dayOrder) {
                val sel = dk == selectedDay
                val isToday = dk == todayKey() && weekOffset == 0
                TextButton(onClick = { selectedDay = dk }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            dayNames[dk] ?: dk,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            df.format(dateOf(monday, dk).time) + if (isToday) " •" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Timeline proporzionale: altezza evento = durata (come il sito)
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (placed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nessun evento — tocca + per aggiungerne uno")
            }
        } else {
            val totalH = HOUR_H * 24
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Row(Modifier.fillMaxWidth().height(totalH)) {
                    TimeGutter()
                    Box(Modifier.weight(1f).height(totalH)) {
                        HourGrid()
                        for (p in placed) {
                            TimelineEvent(
                                p = p,
                                onClick = { editTarget = EditTarget(selectedDay, p.ev) },
                            )
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { t ->
        EventDialog(
            day = t.day,
            ev = t.ev,
            onDismiss = { editTarget = null },
            onSave = { day, title, start, end, type ->
                WeekRepo.save(uid, weekId, t.ev?.let { t.day }, t.ev?.key, day, Ev("", title, start, end, type))
                editTarget = null
            },
            onDelete = {
                t.ev?.let { WeekRepo.delete(uid, weekId, t.day, it.key) }
                editTarget = null
            },
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Ripristina routine?") },
            text = { Text("Tutti gli eventi di questa settimana verranno sovrascritti con la routine predefinita.") },
            confirmButton = {
                Button(onClick = {
                    WeekRepo.reset(uid, weekId)
                    showReset = false
                    Toast.makeText(ctx, "Routine ripristinata", Toast.LENGTH_SHORT).show()
                }) { Text("Ripristina") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Annulla") } },
        )
    }
}

/** Colonna ore 00–23. */
@Composable
private fun TimeGutter() {
    Column(Modifier.width(48.dp)) {
        for (h in 0..23) {
            Box(Modifier.height(HOUR_H), contentAlignment = Alignment.TopEnd) {
                Text(
                    String.format("%02d:00", h),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp, top = 2.dp),
                )
            }
        }
    }
}

/** Griglia ore/mezz'ore disegnata in un colpo solo (1 composable, zero lag). */
@Composable
private fun HourGrid() {
    val line = MaterialTheme.colorScheme.outlineVariant
    val half = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    val hPx = remember(density) { with(density) { HOUR_H.toPx() } }
    Canvas(Modifier.fillMaxSize()) {
        for (h in 0..24) {
            drawLine(line, Offset(0f, h * hPx), Offset(size.width, h * hPx), strokeWidth = 1f)
            if (h < 24) {
                drawLine(half, Offset(0f, h * hPx + hPx / 2), Offset(size.width, h * hPx + hPx / 2), strokeWidth = 1f)
            }
        }
    }
}

/** Blocco evento alto in proporzione alla durata. Sotto i 45' diventa riga singola. */
@Composable
private fun TimelineEvent(p: Placed, onClick: () -> Unit) {
    val c = typeColors[p.ev.type] ?: Color.Gray
    val top = HOUR_H * (p.sMin / 60f)
    val h = (HOUR_H * (p.dur / 60f)).coerceAtLeast(MIN_H)
    val compact = p.dur < 45
    Card(
        Modifier.fillMaxWidth()
            .padding(horizontal = 4.dp)
            .offset(y = top)
            .height(h)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(5.dp).height(h).background(c))
            Column(
                Modifier.padding(horizontal = 8.dp, vertical = if (compact) 2.dp else 5.dp),
                verticalArrangement = if (compact) Arrangement.Center else Arrangement.Top,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (compact) {
                    // Riga singola come sul sito: orario + titolo troncato
                    Text(
                        "${p.ev.start}–${p.ev.end} • ${p.ev.title}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text("${p.ev.start}–${p.ev.end}", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text(p.ev.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (p.dur >= 60) Text(p.ev.type, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun EventDialog(
    day: String,
    ev: Ev?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    var dDay by remember { mutableStateOf(day) }
    var title by remember { mutableStateOf(ev?.title ?: "") }
    var start by remember { mutableStateOf(ev?.start ?: "09:00") }
    var end by remember { mutableStateOf(ev?.end ?: "10:00") }
    var type by remember { mutableStateOf(ev?.type ?: "routine") }
    var error by remember { mutableStateOf("") }

    fun pickTime(init: String, onPick: (String) -> Unit) {
        val p = init.split(":")
        TimePickerDialog(ctx, { _, h, m -> onPick(String.format("%02d:%02d", h, m)) }, p[0].toInt(), p[1].toInt(), true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ev == null) "Nuovo evento" else "Modifica evento") },
        text = {
            Column {
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                Text("Giorno", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (dk in dayOrder) {
                        TextButton(onClick = { dDay = dk }) {
                            Text(
                                dayNames[dk] ?: dk,
                                color = if (dDay == dk) MaterialTheme.colorScheme.primary else Color.Gray,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    OutlinedButton(onClick = { pickTime(start) { start = it } }, modifier = Modifier.weight(1f)) { Text(start) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { pickTime(end) { end = it } }, modifier = Modifier.weight(1f)) { Text(end) }
                }
                Spacer(Modifier.height(8.dp))
                TextField(value = title, onValueChange = { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(4.dp))
                Text("Tipo", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (t in listOf("routine", "free", "trade", "sport")) {
                        TextButton(onClick = { type = t }) {
                            Text(t, color = if (type == t) (typeColors[t] ?: Color.Black) else Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank()) { error = "Metti un titolo"; return@Button }
                if (start == end) { error = "Inizio e fine non possono coincidere"; return@Button }
                onSave(dDay, title.trim(), start, end, type)
            }) { Text("Salva") }
        },
        dismissButton = {
            Row {
                if (ev != null) {
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Elimina", tint = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}
