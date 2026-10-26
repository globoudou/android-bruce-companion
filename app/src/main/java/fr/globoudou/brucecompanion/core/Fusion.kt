package fr.globoudou.brucecompanion.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fusion « intelligente » des observations en émetteurs estimés, un par MAC.
 *
 * Idée : une borne est vue depuis plusieurs points. Plutôt que de garder un
 * seul point (souvent le premier contact, en limite de portée), on estime sa
 * position par un barycentre pondéré par la puissance du signal (le signal
 * fort pèse beaucoup plus que le signal faible) et par la précision GPS.
 *
 * On détecte aussi les émetteurs « mobiles » (partage de connexion d'un
 * téléphone, Wi-Fi de bus, BLE d'un objet porté) : si les observations d'un
 * même MAC sont dispersées au-delà d'un seuil, la position n'a pas de sens, on
 * marque l'émetteur mobile et on l'exclut de l'estimation ponctuelle.
 */
object Fusion {

    /** Au-delà de cette dispersion (m), l'émetteur est considéré mobile. */
    const val MOBILE_SPREAD_M = 300.0

    /** Un émetteur vu une seule fois ne peut pas être jugé mobile. */
    private const val MIN_OBS_FOR_MOBILE = 2

    data class Emitter(
        val mac: String,
        val ssid: String,
        val auth: String,
        val type: RadioType,
        val mfgrId: String,
        val lat: Double,
        val lon: Double,
        val bestRssi: Int,
        val obsCount: Int,
        /** Rayon d'incertitude estimé (m) : dispersion des observations autour du barycentre. */
        val radiusM: Double,
        val mobile: Boolean,
        val firstSeen: String,
        val lastSeen: String,
    )

    data class Result(
        val emitters: List<Emitter>,
        val observations: List<Observation>,
    ) {
        val wifiCount get() = emitters.count { it.type == RadioType.WIFI }
        val bleCount get() = emitters.count { it.type == RadioType.BLE }
        val mobileCount get() = emitters.count { it.mobile }
    }

    fun fuse(observations: List<Observation>): Result {
        val byMac = observations.groupBy { it.mac }
        val emitters = byMac.map { (mac, obs) -> fuseOne(mac, obs) }
            .sortedWith(compareBy({ it.type }, { -it.bestRssi }))
        return Result(emitters, observations)
    }

    private fun fuseOne(mac: String, obs: List<Observation>): Emitter {
        // Poids = puissance linéaire (10^(rssi/10)) pondérée par la précision GPS.
        // Un point à -50 dBm pèse ~1000x un point à -80 dBm.
        var sumW = 0.0
        var sumLat = 0.0
        var sumLon = 0.0
        for (o in obs) {
            val w = weight(o)
            sumW += w
            sumLat += w * o.lat
            sumLon += w * o.lon
        }
        val lat = if (sumW > 0) sumLat / sumW else obs.first().lat
        val lon = if (sumW > 0) sumLon / sumW else obs.first().lon

        // Dispersion : distance pondérée moyenne au barycentre (RMS pondéré).
        var sumWd2 = 0.0
        var maxSpread = 0.0
        for (o in obs) {
            val d = haversine(lat, lon, o.lat, o.lon)
            maxSpread = max(maxSpread, d)
            val w = weight(o)
            sumWd2 += w * d * d
        }
        val radius = if (sumW > 0) sqrt(sumWd2 / sumW) else 0.0

        val mobile = obs.size >= MIN_OBS_FOR_MOBILE && maxSpread > MOBILE_SPREAD_M

        // SSID : on prend le premier non vide (le nom ne change pas d'un passage à l'autre).
        val ssid = obs.firstOrNull { it.ssid.isNotEmpty() }?.ssid ?: ""
        val strongest = obs.maxByOrNull { it.rssi }!!
        val seen = obs.map { it.firstSeen }.filter { it.isNotEmpty() }.sorted()

        // Pour un mobile, la position barycentre n'a pas de sens : on garde le
        // point de signal le plus fort, faute de mieux, mais on le signale.
        val (outLat, outLon) = if (mobile) strongest.lat to strongest.lon else lat to lon

        return Emitter(
            mac = mac,
            ssid = ssid,
            auth = strongest.auth,
            type = obs.first().type,
            mfgrId = obs.firstOrNull { it.mfgrId.isNotEmpty() }?.mfgrId ?: "",
            lat = outLat,
            lon = outLon,
            bestRssi = strongest.rssi,
            obsCount = obs.size,
            radiusM = if (mobile) maxSpread else radius,
            mobile = mobile,
            firstSeen = seen.firstOrNull() ?: "",
            lastSeen = seen.lastOrNull() ?: "",
        )
    }

    private fun weight(o: Observation): Double {
        val power = 10.0.pow(o.rssi / 10.0)          // mW relatif
        val acc = if (o.accuracy > 0) o.accuracy else 5.0
        return power / acc
    }

    /** Distance en mètres entre deux points WGS84. */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
