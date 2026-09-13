package com.routine.calendar

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Ev(
    val key: String,
    val title: String,
    val start: String,
    val end: String,
    val type: String,
)

val dayNames = mapOf(
    "1" to "Lun", "2" to "Mar", "3" to "Mer",
    "4" to "Gio", "5" to "Ven", "6" to "Sab", "0" to "Dom",
)
val dayOrder = listOf("1", "2", "3", "4", "5", "6", "0")

fun timeKey(t: String) = t.replace(":", "_")
fun toMin(t: String): Int {
    val p = t.split(":")
    return p[0].toInt() * 60 + p[1].toInt()
}

/** Lunedì della settimana con offset (0 = corrente). */
fun getMonday(offset: Int): Calendar {
    val c = Calendar.getInstance()
    val dow = c.get(Calendar.DAY_OF_WEEK) // 1=Dom … 7=Sab
    val diffToMon = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
    c.add(Calendar.DAY_OF_MONTH, diffToMon + offset * 7)
    return c
}

fun weekIdOf(monday: Calendar): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(monday.time)

/** Chiave giorno di oggi: "1".."6","0". */
fun todayKey(): String {
    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return if (dow == Calendar.SUNDAY) "0" else (dow - 1).toString()
}

/** Data del giorno dk nella settimana del lunedì dato. */
fun dateOf(monday: Calendar, dk: String): Calendar {
    val c = monday.clone() as Calendar
    c.add(Calendar.DAY_OF_MONTH, dayOrder.indexOf(dk))
    return c
}

fun weekLabel(monday: Calendar): String {
    val f = SimpleDateFormat("d MMM", Locale.ITALIAN)
    val sun = monday.clone() as Calendar
    sun.add(Calendar.DAY_OF_MONTH, 6)
    return "${f.format(monday.time)} – ${f.format(sun.time)} ${monday.get(Calendar.YEAR)}"
}

fun parseWeek(snap: DataSnapshot): Map<String, List<Ev>> {
    val out = mutableMapOf<String, List<Ev>>()
    for (dk in dayOrder) {
        val list = snap.child(dk).children.mapNotNull { e ->
            val t = e.child("title").getValue(String::class.java) ?: return@mapNotNull null
            val s = e.child("start").getValue(String::class.java) ?: return@mapNotNull null
            val en = e.child("end").getValue(String::class.java) ?: s
            val ty = e.child("type").getValue(String::class.java) ?: "routine"
            Ev(e.key ?: timeKey(s), t, s, en, ty)
        }.sortedBy { toMin(it.start) }
        out[dk] = list
    }
    return out
}

object WeekRepo {
    private fun weekRef(uid: String, weekId: String) =
        FirebaseDatabase.getInstance().getReference("users/$uid/weeks/$weekId")

    fun listen(uid: String, weekId: String, onData: (Map<String, List<Ev>>, Boolean) -> Unit): ValueEventListener {
        val ref = weekRef(uid, weekId)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (!s.exists()) {
                    ref.setValue(RoutineDefaults.asFirebaseMap())
                    return
                }
                onData(parseWeek(s), true)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        return l
    }

    fun unlisten(uid: String, weekId: String, l: ValueEventListener) {
        weekRef(uid, weekId).removeEventListener(l)
    }

    fun reset(uid: String, weekId: String) {
        weekRef(uid, weekId).setValue(RoutineDefaults.asFirebaseMap())
    }

    fun save(
        uid: String, weekId: String,
        oldDay: String?, oldKey: String?,
        day: String, ev: Ev,
    ) {
        val ref = weekRef(uid, weekId)
        if (oldDay != null && oldKey != null && (oldDay != day || oldKey != timeKey(ev.start))) {
            ref.child("$oldDay/$oldKey").removeValue()
        }
        val payload = mapOf("title" to ev.title, "start" to ev.start, "end" to ev.end, "type" to ev.type)
        ref.child("$day/${timeKey(ev.start)}").setValue(payload)
    }

    fun delete(uid: String, weekId: String, day: String, key: String) {
        weekRef(uid, weekId).child("$day/$key").removeValue()
    }
}
