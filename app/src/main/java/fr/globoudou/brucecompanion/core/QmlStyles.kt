package fr.globoudou.brucecompanion.core

/**
 * Génère les styles QGIS (QML) stockés dans `layer_styles`. Best-effort : si une
 * version de QField ignore ce QML, la couche reste lisible sans style.
 *
 * Contenu appliqué (styleCategories = AllStyleCategories) :
 *  - symbologie (points catégorisés par sécurité pour access_points) ;
 *  - expression d'affichage : ce qui s'affiche dans la liste « Entités » ;
 *  - alias de champs en français (formulaire d'attributs) ;
 *  - infobulle (map tip) au toucher d'un point.
 */
object QmlStyles {

    /** (valeur du champ secu, libellé, couleur RGBA). */
    private val categories = listOf(
        Triple("open", "Ouvert", "239,68,68,255"),
        Triple("wep", "WEP", "249,115,22,255"),
        Triple("wpa", "WPA", "234,179,8,255"),
        Triple("wpa2", "WPA2", "59,130,246,255"),
        Triple("wpa3", "WPA3", "34,197,94,255"),
        Triple("ble", "BLE", "168,85,247,255"),
        Triple("other", "Autre", "148,163,184,255"),
    )

    private val apAliases = listOf(
        "mac" to "BSSID", "ssid" to "SSID", "auth" to "Chiffrement",
        "type" to "Type radio", "mfgrid" to "Fabricant (ID)",
        "best_rssi" to "RSSI max (dBm)", "obs_count" to "Nb de mesures",
        "radius_m" to "Incertitude (m)", "mobile" to "Mobile",
        "secu" to "Classe sécurité", "first_seen" to "Première vue",
        "last_seen" to "Dernière vue", "lat" to "Latitude", "lon" to "Longitude",
    )

    private val obsAliases = listOf(
        "mac" to "BSSID", "ssid" to "SSID", "auth" to "Chiffrement",
        "firstseen" to "Horodatage", "channel" to "Canal",
        "frequency" to "Fréquence (MHz)", "rssi" to "RSSI (dBm)",
        "lat" to "Latitude", "lon" to "Longitude", "altitude" to "Altitude (m)",
        "accuracy" to "Précision GPS (m)", "mfgrid" to "Fabricant (ID)",
        "type" to "Type radio",
    )

    fun accessPoints(): String {
        val cats = StringBuilder()
        val syms = StringBuilder()
        categories.forEachIndexed { i, (value, label, color) ->
            cats.append(
                """<category symbol="$i" value="$value" label="$label" render="true" type="string"/>"""
            )
            syms.append(markerSymbol(i.toString(), color, size = "2.6", outline = "255,255,255,255"))
        }
        val renderer =
            """<renderer-v2 forceraster="0" type="categorizedSymbol" attr="secu" symbollevels="0" enableorderby="0" referencescale="-1">
<categories>
$cats
</categories>
<symbols>
$syms
</symbols>
</renderer-v2>"""
        // Liste « Entités » : SSID (ou masqué) · chiffrement · RSSI.
        val preview =
            "concat(coalesce(nullif(\"ssid\",''),'(SSID masqué)'),' · ',\"auth\",' · ',\"best_rssi\",' dBm')"
        val mapTip = esc(
            "<b>[% coalesce(nullif(\"ssid\",''),'(SSID masqué)') %]</b><br>" +
                "BSSID : [% \"mac\" %]<br>" +
                "Chiffrement : [% \"auth\" %]<br>" +
                "RSSI max : [% \"best_rssi\" %] dBm — [% \"obs_count\" %] mesure(s)<br>" +
                "Incertitude : [% round(\"radius_m\") %] m[% if(\"mobile\"=1,' — MOBILE','') %]"
        )
        return qgisWrap(renderer, preview, mapTip, apAliases)
    }

    fun observations(): String {
        val renderer =
            """<renderer-v2 forceraster="0" type="singleSymbol" symbollevels="0" enableorderby="0" referencescale="-1">
<symbols>
${markerSymbol("0", "120,130,140,110", size = "1.2", outline = "120,130,140,60")}
</symbols>
</renderer-v2>"""
        val preview = "concat(\"mac\",' · ',\"rssi\",' dBm')"
        val mapTip = esc(
            "<b>[% \"mac\" %]</b><br>" +
                "[% coalesce(nullif(\"ssid\",''),'(SSID masqué)') %]<br>" +
                "[% \"rssi\" %] dBm — canal [% \"channel\" %]<br>" +
                "[% \"firstseen\" %]"
        )
        return qgisWrap(renderer, preview, mapTip, obsAliases)
    }

    private fun markerSymbol(name: String, colorRgba: String, size: String, outline: String): String =
        """<symbol name="$name" type="marker" alpha="1" clip_to_extent="1" force_rhr="0" frame_rate="10">
<layer class="SimpleMarker" enabled="1" locked="0" pass="0">
<Option type="Map">
<Option name="color" type="QString" value="$colorRgba"/>
<Option name="name" type="QString" value="circle"/>
<Option name="outline_color" type="QString" value="$outline"/>
<Option name="outline_style" type="QString" value="solid"/>
<Option name="outline_width" type="QString" value="0.3"/>
<Option name="outline_width_unit" type="QString" value="MM"/>
<Option name="size" type="QString" value="$size"/>
<Option name="size_unit" type="QString" value="MM"/>
<Option name="angle" type="QString" value="0"/>
<Option name="offset" type="QString" value="0,0"/>
<Option name="offset_unit" type="QString" value="MM"/>
<Option name="scale_method" type="QString" value="diameter"/>
<Option name="horizontal_anchor_point" type="QString" value="1"/>
<Option name="vertical_anchor_point" type="QString" value="1"/>
<Option name="joinstyle" type="QString" value="bevel"/>
</Option>
</layer>
</symbol>"""

    private fun aliasesXml(aliases: List<Pair<String, String>>): String {
        val sb = StringBuilder("<aliases>")
        aliases.forEachIndexed { i, (field, name) ->
            sb.append("""<alias field="$field" name="${escAttr(name)}" index="$i"/>""")
        }
        sb.append("</aliases>")
        return sb.toString()
    }

    private fun qgisWrap(
        renderer: String,
        previewExpression: String,
        mapTip: String,
        aliases: List<Pair<String, String>>,
    ): String =
        """<!DOCTYPE qgis PUBLIC 'http://mrcc.com/qgis.dtd' 'SYSTEM'>
<qgis version="3.34.0" styleCategories="AllStyleCategories">
$renderer
<previewExpression>${escText(previewExpression)}</previewExpression>
${aliasesXml(aliases)}
<mapTip>$mapTip</mapTip>
<layerGeometryType>0</layerGeometryType>
</qgis>"""

    /** Échappe le HTML d'un map tip pour qu'il survive comme texte dans le XML. */
    private fun esc(html: String): String = escText(html)

    private fun escText(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escAttr(s: String): String =
        escText(s).replace("\"", "&quot;")
}
