package fr.globoudou.brucecompanion.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Liaison Wi-Fi vers le Bruce.
 *
 * Problème central : le point d'accès de Bruce n'a pas d'accès Internet. Sans
 * précaution, Android route le trafic de l'app sur la 4G et ne trouve jamais
 * l'ESP. On lie donc explicitement le processus au réseau du Bruce
 * (`bindProcessToNetwork`) pour que toutes les requêtes HTTP y passent.
 *
 * Deux modes :
 *  - [connectToAp] : l'app demande la connexion à l'AP (dialogue système),
 *    puis se lie à ce réseau. API 29+.
 *  - [bindToCurrentWifi] : l'utilisateur a déjà rejoint le réseau (AP ou son
 *    LAN) ; on se lie au Wi-Fi courant.
 */
class WifiLink(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var callback: ConnectivityManager.NetworkCallback? = null

    /**
     * Demande la connexion à l'AP `ssid`/`passphrase` et lie le processus à ce
     * réseau. Renvoie vrai si la liaison a réussi. À libérer avec [release].
     */
    suspend fun connectToAp(ssid: String, passphrase: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .apply { if (passphrase.isNotEmpty()) setWpa2Passphrase(passphrase) }
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            release()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val bound = cm.bindProcessToNetwork(network)
                    if (cont.isActive) cont.resume(bound)
                }

                override fun onUnavailable() {
                    if (cont.isActive) cont.resume(false)
                }
            }
            callback = cb
            try {
                cm.requestNetwork(request, cb)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false)
            }
            cont.invokeOnCancellation { release() }
        }

    /** Lie le processus au réseau Wi-Fi courant (déjà rejoint par l'utilisateur). */
    fun bindToCurrentWifi(): Boolean {
        val network = cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return false
        return cm.bindProcessToNetwork(network)
    }

    /** Détache le processus et libère la demande réseau. */
    fun release() {
        runCatching { cm.bindProcessToNetwork(null) }
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }
}
