package fr.globoudou.brucecompanion.core

/**
 * Génère les styles QGIS (QML) stockés dans `layer_styles`. Best-effort : si une
 * version de QField ignore ce QML, la couche reste lisible sans style.
 *
 * - `access_points` : symboles catégorisés par sécurité (champ `secu`).
 * - `observations` : petits points translucides, discrets, en fond.
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

    fun accessPoints(): String {
        val cats = StringBuilder()
        val syms = StringBuilder()
        categories.forEachIndexed { i, (value, label, color) ->
            cats.append(
                """<category symbol="$i" value="$value" label="$label" render="true" type="string"/>"""
            )
            syms.append(markerSymbol(i.toString(), color, size = "2.6", outline = "255,255,255,255"))
        }
        return qgisWrap(
            """<renderer-v2 forceraster="0" type="categorizedSymbol" attr="secu" symbollevels="0" enableorderby="0" referencescale="-1">
<categories>
$cats
</categories>
<symbols>
$syms
</symbols>
</renderer-v2>"""
        )
    }

    fun observations(): String {
        val sym = markerSymbol("0", "120,130,140,110", size = "1.2", outline = "120,130,140,60")
        return qgisWrap(
            """<renderer-v2 forceraster="0" type="singleSymbol" symbollevels="0" enableorderby="0" referencescale="-1">
<symbols>
$sym
</symbols>
</renderer-v2>"""
        )
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

    private fun qgisWrap(body: String): String =
        """<!DOCTYPE qgis PUBLIC 'http://mrcc.com/qgis.dtd' 'SYSTEM'>
<qgis version="3.34.0" styleCategories="Symbology">
$body
<layerGeometryType>0</layerGeometryType>
</qgis>"""
}
