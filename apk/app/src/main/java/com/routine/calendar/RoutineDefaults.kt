package com.routine.calendar

// Stessa routine predefinita del sito (index.html) — unica fonte condivisa via Firebase.
object RoutineDefaults {

    private data class D(val title: String, val start: String, val end: String, val type: String)

    private fun morning(schoolEnd: String, backEnd: String): List<D> = listOf(
        D("Sveglia / Preparazione + Colazione", "06:00", "06:55", "routine"),
        D("Pullman per scuola", "06:55", "08:00", "routine"),
        D("Scuola", "08:00", schoolEnd, "routine"),
        D("Navetta + 036 Padova / Rientro a casa", schoolEnd, backEnd, "routine"),
    )

    private fun night(): List<D> = listOf(
        D("Tempo libero", "20:30", "21:30", "free"),
        D("Preparazione per andare a letto", "21:30", "22:00", "routine"),
        D("A letto", "22:00", "00:00", "routine"),
    )

    private val monWed = morning("14:00", "15:00") + listOf(
        D("Pranzo", "15:00", "15:30", "routine"),
        D("Trading: apertura / check (solo se opero)", "15:30", "16:00", "trade"),
        D("Studio per la scuola", "16:00", "18:00", "free"),
        D("Allenamento (1h)", "18:00", "19:00", "sport"),
        D("Tempo libero", "19:00", "20:00", "free"),
        D("Cena", "20:00", "20:30", "routine"),
    ) + night()

    private val tue = morning("14:00", "15:00") + listOf(
        D("Pranzo", "15:00", "15:30", "routine"),
        D("Trading: apertura / check (solo se opero)", "15:30", "16:00", "trade"),
        D("Studio per la scuola", "16:00", "18:00", "free"),
        D("Studio trading", "18:00", "19:00", "trade"),
        D("Tempo libero", "19:00", "20:00", "free"),
        D("Cena", "20:00", "20:30", "routine"),
    ) + night()

    private val thu = morning("13:00", "14:00") + listOf(
        D("Pranzo", "14:00", "14:30", "routine"),
        D("Trading: apertura / check (solo se opero)", "14:30", "15:00", "trade"),
        D("Studio per la scuola", "15:00", "17:00", "free"),
        D("Studio trading", "17:00", "18:00", "trade"),
        D("Tempo libero", "18:00", "20:00", "free"),
        D("Cena", "20:00", "20:30", "routine"),
    ) + night()

    private val fri = morning("13:00", "14:00") + listOf(
        D("Pranzo", "14:00", "14:30", "routine"),
        D("Trading: apertura / check (solo se opero)", "14:30", "15:00", "trade"),
        D("Studio per la scuola", "15:00", "17:00", "free"),
        D("Allenamento (1h)", "17:00", "18:00", "sport"),
        D("Tempo libero", "18:00", "20:00", "free"),
        D("Cena", "20:00", "20:30", "routine"),
    ) + night()

    private val sat = morning("12:00", "13:00") + listOf(
        D("Pranzo", "13:00", "13:30", "routine"),
        D("Studio per la scuola", "13:30", "15:00", "free"),
        D("Studio trading / Review settimana (facoltativo)", "15:00", "15:30", "trade"),
        D("Tempo libero", "15:30", "20:30", "free"),
        D("Cena", "20:30", "21:30", "routine"),
        D("Tempo libero", "21:30", "00:00", "free"),
        D("Preparazione per andare a letto / A letto", "00:00", "01:00", "routine"),
    )

    private val sun = listOf(
        D("Sveglia Comoda", "10:00", "10:30", "routine"),
        D("Tempo libero", "10:30", "13:00", "free"),
        D("Pranzo in Famiglia", "13:00", "14:00", "routine"),
        D("Tempo libero", "14:00", "19:00", "free"),
        D("Occhiata ai mercati (facoltativo)", "19:00", "20:30", "trade"),
        D("Cena & Relax", "20:30", "21:30", "routine"),
        D("Tempo libero", "21:30", "00:00", "free"),
        D("Preparazione per andare a letto / A letto", "00:00", "01:00", "routine"),
    )

    private val days = mapOf(
        "1" to monWed, "2" to tue, "3" to monWed,
        "4" to thu, "5" to fri, "6" to sat, "0" to sun,
    )

    /** Forma Firebase: weeks/{weekId}/{day}/{start_con_underscore} = {title,start,end,type} */
    fun asFirebaseMap(): Map<String, Map<String, Map<String, String>>> =
        days.mapValues { (_, list) ->
            list.associate { d ->
                d.start.replace(":", "_") to mapOf(
                    "title" to d.title, "start" to d.start,
                    "end" to d.end, "type" to d.type,
                )
            }
        }
}
