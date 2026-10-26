package fr.globoudou.brucecompanion.core

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Écriture (et relecture) d'un GeoPackage OGC directement via SQLite — un
 * GeoPackage est une base SQLite avec des tables normalisées. Cela évite
 * d'embarquer GDAL (plusieurs dizaines de Mo) pour produire un fichier que
 * QField/QGIS ouvrent nativement.
 *
 * Deux couches de points sont produites :
 *   - `observations` : tous les contacts bruts (rejouables, pour recalculer).
 *   - `access_points` : un émetteur estimé par MAC (position fusionnée).
 *
 * Réf. : OGC GeoPackage 1.2 — tables gpkg_spatial_ref_sys / gpkg_contents /
 * gpkg_geometry_columns, en-tête GeoPackageBinary + WKB point.
 */
object GeoPackage {

    private const val SRS_ID = 4326
    private const val OBS = "observations"
    private const val AP = "access_points"

    // 'GPKG' en big-endian, valeur du PRAGMA application_id d'un GeoPackage.
    private const val APPLICATION_ID = 0x47504B47
    private const val USER_VERSION = 10200 // 1.2

    /** Bilan d'une écriture, pour l'écran de résultat. */
    data class Stats(
        val observations: Int,
        val emitters: Int,
        val wifi: Int,
        val ble: Int,
        val mobile: Int,
    )

    /** Crée un GeoPackage neuf contenant les observations et les émetteurs fusionnés. */
    fun writeNew(file: File, observations: List<Observation>): Stats {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA application_id = $APPLICATION_ID")
            db.execSQL("PRAGMA user_version = $USER_VERSION")
            createCoreTables(db)
            createFeatureTables(db)
            val res = fillData(db, observations)
            writeStyles(db)
            return statsOf(res)
        } finally {
            db.close()
        }
    }

    /**
     * Ajoute des observations à un GeoPackage existant produit par cette app :
     * les anciennes observations sont relues, fusionnées avec les nouvelles, et
     * les deux couches recalculées. La position des bornes s'affine ainsi au fil
     * des sessions. Renvoie le bilan après fusion.
     */
    fun appendTo(file: File, newObs: List<Observation>): Stats {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val existing = readObservations(db)
            val all = existing + newObs
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM $OBS")
                db.execSQL("DELETE FROM $AP")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return statsOf(fillData(db, all))
        } finally {
            db.close()
        }
    }

    private fun statsOf(res: Fusion.Result) = Stats(
        observations = res.observations.size,
        emitters = res.emitters.size,
        wifi = res.wifiCount,
        ble = res.bleCount,
        mobile = res.mobileCount,
    )

    /** Vrai si le fichier est un GeoPackage déjà produit par cette app (couches attendues). */
    fun isCompatible(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            db.use {
                val c = it.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN (?, ?)",
                    arrayOf(OBS, AP)
                )
                c.use { cur -> cur.count == 2 }
            }
        } catch (_: Exception) {
            false
        }
    }

    // ---- Schéma GeoPackage ------------------------------------------------

    private fun createCoreTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE gpkg_spatial_ref_sys (
                srs_name TEXT NOT NULL, srs_id INTEGER NOT NULL PRIMARY KEY,
                organization TEXT NOT NULL, organization_coordsys_id INTEGER NOT NULL,
                definition TEXT NOT NULL, description TEXT)"""
        )
        db.execSQL(
            "INSERT INTO gpkg_spatial_ref_sys VALUES " +
                "('Undefined cartesian SRS',-1,'NONE',-1,'undefined',NULL)," +
                "('Undefined geographic SRS',0,'NONE',0,'undefined',NULL)," +
                "('WGS 84 geodetic',4326,'EPSG',4326," +
                "'GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563," +
                "AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]]," +
                "PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]]," +
                "UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9122\"]]," +
                "AUTHORITY[\"EPSG\",\"4326\"]]','WGS 84')"
        )
        db.execSQL(
            """CREATE TABLE gpkg_contents (
                table_name TEXT NOT NULL PRIMARY KEY, data_type TEXT NOT NULL,
                identifier TEXT UNIQUE, description TEXT DEFAULT '',
                last_change DATETIME NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
                min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE,
                srs_id INTEGER,
                CONSTRAINT fk_gc_r_srs_id FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))"""
        )
        db.execSQL(
            """CREATE TABLE gpkg_geometry_columns (
                table_name TEXT NOT NULL, column_name TEXT NOT NULL,
                geometry_type_name TEXT NOT NULL, srs_id INTEGER NOT NULL,
                z TINYINT NOT NULL, m TINYINT NOT NULL,
                CONSTRAINT pk_geom_cols PRIMARY KEY (table_name, column_name),
                CONSTRAINT uk_gc_table_name UNIQUE (table_name),
                CONSTRAINT fk_gc_tn FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name),
                CONSTRAINT fk_gc_srs FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys (srs_id))"""
        )
    }

    private fun createFeatureTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $OBS (
                fid INTEGER PRIMARY KEY AUTOINCREMENT, geom BLOB,
                mac TEXT, ssid TEXT, auth TEXT, firstseen TEXT,
                channel INTEGER, frequency INTEGER, rssi INTEGER,
                lat REAL, lon REAL, altitude REAL, accuracy REAL,
                mfgrid TEXT, type TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE $AP (
                fid INTEGER PRIMARY KEY AUTOINCREMENT, geom BLOB,
                mac TEXT, ssid TEXT, auth TEXT, type TEXT, mfgrid TEXT,
                best_rssi INTEGER, obs_count INTEGER, radius_m REAL,
                mobile INTEGER, secu TEXT, first_seen TEXT, last_seen TEXT,
                lat REAL, lon REAL)"""
        )
        for (t in listOf(OBS, AP)) {
            val label = if (t == OBS) "Observations brutes" else "Émetteurs (position estimée)"
            db.execSQL(
                "INSERT INTO gpkg_contents (table_name,data_type,identifier,description,srs_id) " +
                    "VALUES (?, 'features', ?, ?, $SRS_ID)",
                arrayOf(t, t, label)
            )
            db.execSQL(
                "INSERT INTO gpkg_geometry_columns VALUES (?, 'geom', 'POINT', $SRS_ID, 0, 0)",
                arrayOf(t)
            )
        }
    }

    // ---- Remplissage ------------------------------------------------------

    private fun fillData(db: SQLiteDatabase, observations: List<Observation>): Fusion.Result {
        val fused = Fusion.fuse(observations)
        db.beginTransaction()
        try {
            val obsStmt = db.compileStatement(
                "INSERT INTO $OBS (geom,mac,ssid,auth,firstseen,channel,frequency,rssi," +
                    "lat,lon,altitude,accuracy,mfgrid,type) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            )
            for (o in observations) {
                obsStmt.clearBindings()
                obsStmt.bindBlob(1, geomPoint(o.lon, o.lat))
                obsStmt.bindString(2, o.mac)
                obsStmt.bindString(3, o.ssid)
                obsStmt.bindString(4, o.auth)
                obsStmt.bindString(5, o.firstSeen)
                obsStmt.bindLong(6, o.channel.toLong())
                obsStmt.bindLong(7, o.frequency.toLong())
                obsStmt.bindLong(8, o.rssi.toLong())
                obsStmt.bindDouble(9, o.lat)
                obsStmt.bindDouble(10, o.lon)
                obsStmt.bindDouble(11, o.altitude)
                obsStmt.bindDouble(12, o.accuracy)
                obsStmt.bindString(13, o.mfgrId)
                obsStmt.bindString(14, o.type.name)
                obsStmt.executeInsert()
            }

            val apStmt = db.compileStatement(
                "INSERT INTO $AP (geom,mac,ssid,auth,type,mfgrid,best_rssi,obs_count,radius_m," +
                    "mobile,secu,first_seen,last_seen,lat,lon) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            )
            for (e in fused.emitters) {
                apStmt.clearBindings()
                apStmt.bindBlob(1, geomPoint(e.lon, e.lat))
                apStmt.bindString(2, e.mac)
                apStmt.bindString(3, e.ssid)
                apStmt.bindString(4, e.auth)
                apStmt.bindString(5, e.type.name)
                apStmt.bindString(6, e.mfgrId)
                apStmt.bindLong(7, e.bestRssi.toLong())
                apStmt.bindLong(8, e.obsCount.toLong())
                apStmt.bindDouble(9, e.radiusM)
                apStmt.bindLong(10, if (e.mobile) 1 else 0)
                apStmt.bindString(11, secuClass(e))
                apStmt.bindString(12, e.firstSeen)
                apStmt.bindString(13, e.lastSeen)
                apStmt.bindDouble(14, e.lat)
                apStmt.bindDouble(15, e.lon)
                apStmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        updateExtent(db, OBS, observations.map { it.lat to it.lon })
        updateExtent(db, AP, fused.emitters.map { it.lat to it.lon })
        return fused
    }

    private fun readObservations(db: SQLiteDatabase): List<Observation> {
        val out = ArrayList<Observation>()
        val c = db.rawQuery(
            "SELECT mac,ssid,auth,firstseen,channel,frequency,rssi,lat,lon," +
                "altitude,accuracy,mfgrid,type FROM $OBS", null
        )
        c.use {
            while (it.moveToNext()) {
                out.add(
                    Observation(
                        mac = it.getString(0) ?: continue,
                        ssid = it.getString(1) ?: "",
                        auth = it.getString(2) ?: "",
                        firstSeen = it.getString(3) ?: "",
                        channel = it.getInt(4),
                        frequency = it.getInt(5),
                        rssi = it.getInt(6),
                        lat = it.getDouble(7),
                        lon = it.getDouble(8),
                        altitude = it.getDouble(9),
                        accuracy = it.getDouble(10),
                        mfgrId = it.getString(11) ?: "",
                        type = runCatching { RadioType.valueOf(it.getString(12) ?: "OTHER") }
                            .getOrDefault(RadioType.OTHER),
                    )
                )
            }
        }
        return out
    }

    private fun updateExtent(db: SQLiteDatabase, table: String, pts: List<Pair<Double, Double>>) {
        if (pts.isEmpty()) return
        val minX = pts.minOf { it.second }
        val maxX = pts.maxOf { it.second }
        val minY = pts.minOf { it.first }
        val maxY = pts.maxOf { it.first }
        db.execSQL(
            "UPDATE gpkg_contents SET min_x=?, min_y=?, max_x=?, max_y=?, " +
                "last_change=strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE table_name=?",
            arrayOf(minX, minY, maxX, maxY, table)
        )
    }

    // ---- Géométrie GeoPackageBinary (point WKB) ---------------------------

    private fun geomPoint(lon: Double, lat: Double): ByteArray {
        // En-tête (8 o) : magic "GP", version 0, flags 0x01 (little-endian, sans
        // enveloppe), srs_id ; puis WKB point little-endian.
        val buf = ByteBuffer.allocate(8 + 21).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('G'.code.toByte())
        buf.put('P'.code.toByte())
        buf.put(0.toByte())      // version
        buf.put(0x01.toByte())   // flags : little-endian, pas d'enveloppe
        buf.putInt(SRS_ID)
        buf.put(0x01.toByte())   // WKB : little-endian
        buf.putInt(1)            // type géométrie : Point
        buf.putDouble(lon)    // X
        buf.putDouble(lat)    // Y
        return buf.array()
    }

    // ---- Styles QGIS/QField (best-effort, « à valider en test ») ----------

    private fun secuClass(e: Fusion.Emitter): String {
        if (e.type == RadioType.BLE) return "ble"
        val a = e.auth.uppercase()
        return when {
            a.contains("WPA3") -> "wpa3"
            a.contains("WPA2") -> "wpa2"
            a.contains("WPA") -> "wpa"
            a.contains("WEP") -> "wep"
            a.contains("OPEN") -> "open"
            else -> "other"
        }
    }

    /**
     * Table `layer_styles` (spécifique QGIS) : QField applique le style par défaut
     * qu'elle contient. Si le QML est ignoré par une version, la couche s'affiche
     * simplement sans style — les données restent intactes.
     */
    private fun writeStyles(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE layer_styles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                f_table_catalog TEXT, f_table_schema TEXT, f_table_name TEXT,
                f_geometry_column TEXT, styleName TEXT, styleQML TEXT, styleSLD TEXT,
                useAsDefault BOOLEAN, description TEXT, owner TEXT, ui TEXT,
                update_time DATETIME DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')))"""
        )
        insertStyle(db, AP, "Sécurité", QmlStyles.accessPoints())
        insertStyle(db, OBS, "Observations", QmlStyles.observations())
    }

    private fun insertStyle(db: SQLiteDatabase, table: String, name: String, qml: String) {
        db.execSQL(
            "INSERT INTO layer_styles (f_table_catalog,f_table_schema,f_table_name," +
                "f_geometry_column,styleName,styleQML,styleSLD,useAsDefault,description,owner,ui) " +
                "VALUES ('','', ?, 'geom', ?, ?, '', 1, '', '', NULL)",
            arrayOf(table, name, qml)
        )
    }
}
