package com.wayars.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LatLng(val lat: Double, val lon: Double)

/**
 * Honest road-distance estimation via free, keyless services:
 *  - Nominatim (OpenStreetMap) for address -> coordinates
 *  - OSRM (Open Source Routing Machine) for coordinates -> real road km
 *
 * IMPORTANT SCOPE NOTE: this is a ready-to-use utility, not something wired
 * into the automatic scanning pipeline. Reliably picking "this text is the
 * pickup address" and "this text is the drop-off address" out of an
 * arbitrary Bolt/Uber/Wolt/FreeNow screen via generic node-scanning is not
 * realistic — those apps don't expose a public API, and address text is
 * visually indistinguishable from any other line of text without per-app,
 * per-screen tuning that breaks on the next UI update. Use this from
 * anywhere you DO have two known address strings (e.g. a future feature
 * where the driver types them in manually) rather than expecting it to
 * silently correct every parsed order's distance.
 *
 * Both providers are free but rate-limited and request a real User-Agent —
 * do not remove the header below or Nominatim will start blocking requests.
 */
object GeoDistanceEstimator {

    private const val USER_AGENT = "WayArs/1.0 (driver assistant app)"

    suspend fun geocode(address: String): LatLng? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(address, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONArray(body)
            if (results.length() == 0) return@runCatching null
            val first = results.getJSONObject(0)
            LatLng(first.getString("lat").toDouble(), first.getString("lon").toDouble())
        }.getOrNull()
    }

    /** Real road distance in km between two points, via OSRM's public demo server. */
    suspend fun roadDistanceKm(from: LatLng, to: LatLng): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://router.project-osrm.org/route/v1/driving/" +
                    "${from.lon},${from.lat};${to.lon},${to.lat}?overview=false"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(body)
            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) return@runCatching null
            val meters = routes.getJSONObject(0).getDouble("distance")
            meters / 1000.0
        }.getOrNull()
    }

    /** Convenience: address A + address B -> real road km, or null if either step fails. */
    suspend fun estimateRoadDistanceKm(addressFrom: String, addressTo: String): Double? {
        val from = geocode(addressFrom) ?: return null
        val to = geocode(addressTo) ?: return null
        return roadDistanceKm(from, to)
    }
}
