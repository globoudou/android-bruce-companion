package fr.globoudou.brucecompanion.core

/**
 * Lecture des fichiers CSV « WigleWifi » produits par le wardriving de Bruce.
 *
 * Format (voir firmware Bruce, modules/gps/wardriving.cpp) :
 *   - ligne 1 : métadonnées `WigleWifi-1.x,appRelease=...`
 *   - ligne 2 : en-tête de colonnes
 *   - lignes suivantes : une observation par ligne
 *
 * Colonnes :
 *   MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,
 *   CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,RCOIs,MfgrId,Type
 */

enum class RadioType { WIFI, BLE, OTHER }

/** Une observation brute : un contact avec un émetteur à un instant et un point GPS donnés. */
data class Observation(
    val mac: String,
    val ssid: String,
    val auth: String,
    val firstSeen: String,
    val channel: Int,
    val frequency: Int,
    val rssi: Int,
    val lat: Double,
    val lon: Double,
    val altitude: Double,
    val accuracy: Double,
    val mfgrId: String,
    val type: RadioType,
)

object Wigle {

    /** Nombre de colonnes attendu dans l'en-tête Bruce. */
    private const val COLS = 14

    /**
     * Analyse le contenu d'un CSV Wigle. Les lignes invalides (coordonnées 0/0,
     * MAC vide, champs manquants) sont ignorées silencieusement : un relevé
     * terrain contient toujours quelques points sans fix GPS.
     */
    fun parse(content: String): List<Observation> {
        val out = ArrayList<Observation>()
        val lines = content.split('\n')
        var headerSeen = false
        for (raw in lines) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            if (!headerSeen) {
                // On saute tout jusqu'à la ligne d'en-tête de colonnes incluse.
                if (line.startsWith("WigleWifi")) continue
                if (line.startsWith("MAC,")) { headerSeen = true; continue }
                // Fichier sans ligne meta : la première ligne « MAC, » fait office d'en-tête.
                if (line.startsWith("MAC")) { headerSeen = true; continue }
            }
            val f = splitCsv(line)
            if (f.size < COLS) continue
            val mac = f[0].trim().uppercase()
            if (mac.isEmpty() || mac == "00:00:00:00:00:00") continue
            val lat = f[7].toDoubleOrNull() ?: continue
            val lon = f[8].toDoubleOrNull() ?: continue
            if (lat == 0.0 && lon == 0.0) continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val type = when (f[13].trim().uppercase()) {
                "WIFI" -> RadioType.WIFI
                "BLE" -> RadioType.BLE
                else -> RadioType.OTHER
            }
            out.add(
                Observation(
                    mac = mac,
                    ssid = f[1].trim().trim('"'),
                    auth = f[2].trim().trim('[', ']'),
                    firstSeen = f[3].trim(),
                    channel = f[4].toIntOrNull() ?: 0,
                    frequency = f[5].toIntOrNull() ?: 0,
                    rssi = f[6].toIntOrNull() ?: -127,
                    lat = lat,
                    lon = lon,
                    altitude = f[9].toDoubleOrNull() ?: 0.0,
                    accuracy = f[10].toDoubleOrNull() ?: 0.0,
                    mfgrId = f[12].trim(),
                    type = type,
                )
            )
        }
        return out
    }

    /**
     * Découpe une ligne CSV en respectant les guillemets (un SSID peut contenir
     * une virgule). Pas d'échappement par doublement : Bruce ne produit que des
     * champs simples entre guillemets.
     */
    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> { inQuotes = !inQuotes; sb.append(c) }
                c == ',' && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
        }
        out.add(sb.toString())
        return out
    }
}
