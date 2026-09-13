package com.routine.calendar

import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun WeekScreen(uid: String, email: String?, onLogout: () -> Unit) {
    val ctx = LocalContext.current
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
            val df = SimpleDateFormat("d", Locale.ITALIAN)
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

        // Eventi del giorno — lista verticale, scroll col dito
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val evs = data[selectedDay].orEmpty()
            if (evs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessun evento — tocca + per aggiungerne uno")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    items(evs, key = { it.key }) { ev ->
                        val c = typeColors[ev.type] ?: Color.Gray
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { editTarget = EditTarget(selectedDay, ev) },
                        ) {
                            Row(Modifier.fillMaxWidth()) {
                                Box(Modifier.width(6.dp).height(64.dp).background(c))
                                Column(Modifier.padding(10.dp)) {
                                    Text("${ev.start} – ${ev.end}", style = MaterialTheme.typography.labelMedium)
                                    Text(ev.title, style = MaterialTheme.typography.titleSmall)
                                    Text(ev.type, style = MaterialTheme.typography.bodySmall)
                                }
                            }
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
