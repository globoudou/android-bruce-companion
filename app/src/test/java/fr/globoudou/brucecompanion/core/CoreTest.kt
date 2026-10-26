package fr.globoudou.brucecompanion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTest {

    private val sample = """
WigleWifi-1.6,appRelease=v1.0,model=M5Stack GPS Unit,release=v1.0,device=ESP32,display=SPI TFT,board=ESP32,brand=Bruce
MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,RCOIs,MfgrId,Type
AA:BB:CC:DD:EE:FF,"Livebox-1234",[WPA2_PSK],2026-10-26 11:00:00,6,2437,-80,48.1000,-1.6800,30,5,,,WIFI
AA:BB:CC:DD:EE:FF,"Livebox-1234",[WPA2_PSK],2026-10-26 11:00:10,6,2437,-55,48.1002,-1.6802,30,5,,,WIFI
11:22:33:44:55:66,"Café, du coin",[OPEN],2026-10-26 11:01:00,1,2412,-70,48.2000,-1.5000,20,8,,,WIFI
00:00:00:00:00:00,"bad",[OPEN],2026-10-26 11:02:00,1,2412,-70,0.000000,0.000000,0,0,,,WIFI
DE:AD:BE:EF:00:01,"",Misc [BLE],2026-10-26 11:03:00,0,,-67,48.3000,-1.4000,10,49,,004C,BLE
""".trimIndent()

    @Test fun parsesValidRowsAndSkipsJunk() {
        val obs = Wigle.parse(sample)
        // 5 data rows, dont une à 0/0 ignorée → 4 observations.
        assertEquals(4, obs.size)
        val ble = obs.first { it.type == RadioType.BLE }
        assertEquals("004C", ble.mfgrId)
    }

    @Test fun ssidWithCommaStaysIntact() {
        val obs = Wigle.parse(sample)
        assertTrue(obs.any { it.ssid == "Café, du coin" })
    }

    @Test fun fusionGroupsByMacAndWeightsTowardStrongSignal() {
        val obs = Wigle.parse(sample)
        val res = Fusion.fuse(obs)
        // 3 MAC distincts valides (2 WiFi + 1 BLE).
        assertEquals(3, res.emitters.size)
        val livebox = res.emitters.first { it.mac == "AA:BB:CC:DD:EE:FF" }
        assertEquals(2, livebox.obsCount)
        assertEquals(-55, livebox.bestRssi)
        // Barycentre pondéré : tiré vers le point à -55 dBm (48.1002) plutôt
        // qu'au milieu (48.1001).
        assertTrue(livebox.lat > 48.10015, "lat fusionnée=${livebox.lat}")
    }

    @Test fun detectsMobileWhenObservationsAreFarApart() {
        val obs = listOf(
            obs("FF:FF:FF:FF:FF:FF", -60, 48.10, -1.60),
            obs("FF:FF:FF:FF:FF:FF", -60, 48.20, -1.50), // ~13 km plus loin
        )
        val res = Fusion.fuse(obs)
        assertTrue(res.emitters.single().mobile)
    }

    @Test fun singleObservationIsNeverMobile() {
        val res = Fusion.fuse(listOf(obs("AB:CD:EF:01:02:03", -60, 48.1, -1.6)))
        assertTrue(!res.emitters.single().mobile)
    }

    @Test fun haversineIsAboutRight() {
        // 0.01° de latitude ≈ 1.11 km.
        val d = Fusion.haversine(48.0, -1.6, 48.01, -1.6)
        assertTrue(d in 1100.0..1120.0, "d=$d")
    }

    private fun obs(mac: String, rssi: Int, lat: Double, lon: Double) = Observation(
        mac = mac, ssid = "x", auth = "WPA2_PSK", firstSeen = "2026-10-26 11:00:00",
        channel = 6, frequency = 2437, rssi = rssi, lat = lat, lon = lon,
        altitude = 0.0, accuracy = 5.0, mfgrId = "", type = RadioType.WIFI,
    )
}
