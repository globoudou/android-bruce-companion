package fr.globoudou.brucecompanion.data

import android.content.Context

/**
 * Réglages persistants (identifiants et hôte mémorisés d'une fois à l'autre).
 * Stockage simple en SharedPreferences : usage personnel, pas de secret de
 * grande valeur (ce sont les identifiants par défaut de la WebUI de Bruce).
 */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bruce", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString("host", "172.0.0.1") ?: "172.0.0.1"
        set(v) = prefs.edit().putString("host", v).apply()

    var user: String
        get() = prefs.getString("user", "admin") ?: "admin"
        set(v) = prefs.edit().putString("user", v).apply()

    var password: String
        get() = prefs.getString("pwd", "bruce") ?: "bruce"
        set(v) = prefs.edit().putString("pwd", v).apply()

    var apSsid: String
        get() = prefs.getString("ap_ssid", "BruceNet") ?: "BruceNet"
        set(v) = prefs.edit().putString("ap_ssid", v).apply()

    var apPass: String
        get() = prefs.getString("ap_pass", "brucenet") ?: "brucenet"
        set(v) = prefs.edit().putString("ap_pass", v).apply()

    /** Dossier wardriving sur la carte SD du Bruce. */
    var folder: String
        get() = prefs.getString("folder", "/BruceWardriving") ?: "/BruceWardriving"
        set(v) = prefs.edit().putString("folder", v).apply()

    fun saveConnection(host: String, user: String, pwd: String) {
        prefs.edit()
            .putString("host", host)
            .putString("user", user)
            .putString("pwd", pwd)
            .apply()
    }
}
