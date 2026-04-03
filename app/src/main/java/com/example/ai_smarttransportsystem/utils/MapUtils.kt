package com.example.ai_smarttransportsystem.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import kotlin.math.*

object MapUtils {

    private const val TAG = "MapUtils"
    private const val MAPS_BASE_URL = "https://maps.googleapis.com/maps/api/"

    // Directions API snaps destination to nearest driveable road.
    // If that snap is further than this from the tapped point → no driveable road nearby.
    private const val MAX_SNAP_METERS = 40.0

    // Central Faisalabad reference point (Clock Tower) — always bus-accessible
    private val REFERENCE = LatLng(31.4187, 73.0791)

    // ─── Only these geocoding result types represent a named road a bus can use ─
    // "route" = a named road/street that appears on the map as a driveable road
    // Everything else (premise, street_address, establishment, park, etc.) is rejected
    private val BUS_ROAD_TYPES = setOf("route")

    // ─── These types are explicitly NOT bus-accessible ────────────────────────
    private val REJECTED_TYPES = setOf(
        "premise",              // house plot / building
        "street_address",       // specific door address (narrow lane / gali)
        "establishment",        // shop, office, business
        "point_of_interest",    // POI
        "park",                 // park / garden
        "natural_feature",      // field, river, etc.
        "subpremise"            // apartment / flat inside a building
    )

    // ═════════════════════════════════════════════════════════════════════════
    // Retrofit interfaces
    // ═════════════════════════════════════════════════════════════════════════

    interface DirectionsApi {
        @GET("directions/json")
        fun getDirections(
            @Query("origin") origin: String,
            @Query("destination") destination: String,
            @Query("mode") mode: String,
            @Query("key") key: String
        ): Call<DirectionsResponse>
    }

    interface GeocodingApi {
        @GET("geocode/json")
        fun reverseGeocode(
            @Query("latlng") latlng: String,
            @Query("key") key: String
        ): Call<GeocodingResponse>
    }

    // ─── Directions response models ───────────────────────────────────────────

    data class DirectionsResponse(
        val status: String,
        val routes: List<DirectionsRoute>?,
        val error_message: String?
    )

    data class DirectionsRoute(val legs: List<DirectionsLeg>?)

    data class DirectionsLeg(
        val end_location: DirectionsLocation?,
        val distance: ValueText?,
        val duration: ValueText?
    )

    data class DirectionsLocation(val lat: Double, val lng: Double)
    data class ValueText(val text: String?, val value: Int?)

    // ─── Geocoding response models ────────────────────────────────────────────

    data class GeocodingResponse(
        val status: String,
        val results: List<GeocodingResult>?
    )

    data class GeocodingResult(
        val types: List<String>,
        val formatted_address: String?
    )

    // ═════════════════════════════════════════════════════════════════════════
    // Public entry point
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Validates whether [originalLatLng] is a bus-accessible stop using two checks:
     *
     * Step 1 — Directions API (driving mode):
     *   • Confirms a driving route exists to this point.
     *   • Gets the road-snapped endpoint Google chose for this destination.
     *   • Rejects if that endpoint is > 40 m from the tapped point
     *     (meaning no driveable road is nearby).
     *
     * Step 2 — Geocoding API (reverse geocode of snapped endpoint):
     *   • Checks the actual road type at the snapped point.
     *   • Only "route" (a named driveable road) is accepted.
     *   • "premise", "street_address", "establishment", "park", etc. are rejected.
     *
     * Callback:
     *   (snappedLatLng, true)  → confirmed bus stop on a named driveable road
     *   (snappedLatLng, false) → route exists but road is a house/lane/park/etc.
     *   (null, false)          → no driveable route to this location at all
     */
    fun validateAndSnapLocation(
        context: Context,
        apiKey: String,
        originalLatLng: LatLng,
        callback: (LatLng?, Boolean) -> Unit
    ) {
        if (apiKey.isEmpty()) {
            Log.e(TAG, "API key is empty")
            callback(null, false)
            return
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(MAPS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        step1Directions(retrofit, apiKey, originalLatLng) { snappedLatLng ->
            if (snappedLatLng == null) {
                // No driveable route found
                callback(null, false)
            } else {
                step2Geocoding(retrofit, apiKey, snappedLatLng, originalLatLng) { isBusRoad ->
                    callback(snappedLatLng, isBusRoad)
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Step 1 — Directions API
    // ═════════════════════════════════════════════════════════════════════════

    private fun step1Directions(
        retrofit: Retrofit,
        apiKey: String,
        originalLatLng: LatLng,
        callback: (LatLng?) -> Unit          // null = no valid route
    ) {
        val api = retrofit.create(DirectionsApi::class.java)
        val origin = "${REFERENCE.latitude},${REFERENCE.longitude}"
        val dest   = "${originalLatLng.latitude},${originalLatLng.longitude}"

        Log.d(TAG, "Step1 → Directions to $dest")

        api.getDirections(origin, dest, "driving", apiKey)
            .enqueue(object : Callback<DirectionsResponse> {

                override fun onResponse(
                    call: Call<DirectionsResponse>,
                    response: Response<DirectionsResponse>
                ) {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Directions HTTP ${response.code()}")
                        callback(null)
                        return
                    }

                    val body   = response.body()
                    val status = body?.status
                    Log.d(TAG, "Directions status: $status")

                    when (status) {
                        "OK" -> {
                            val endLoc = body?.routes
                                ?.firstOrNull()
                                ?.legs?.firstOrNull()
                                ?.end_location

                            if (endLoc == null) {
                                Log.w(TAG, "Directions OK but no end_location")
                                callback(null)
                                return
                            }

                            val snapped  = LatLng(endLoc.lat, endLoc.lng)
                            val distance = calculateDistance(originalLatLng, snapped)
                            Log.i(TAG, "Directions snap: ${"%.1f".format(distance)}m from tap")

                            if (distance > MAX_SNAP_METERS) {
                                Log.w(TAG, "Snap too far (${distance.toInt()}m > ${MAX_SNAP_METERS.toInt()}m) — no driveable road near tap")
                                callback(null)
                            } else {
                                callback(snapped)
                            }
                        }

                        "ZERO_RESULTS", "NOT_FOUND" -> {
                            Log.w(TAG, "Directions: no driving route ($status)")
                            callback(null)
                        }

                        "REQUEST_DENIED" -> {
                            Log.e(TAG, "Directions REQUEST_DENIED — enable Directions API on your Roads key in Cloud Console")
                            callback(null)
                        }

                        else -> {
                            Log.w(TAG, "Directions status=$status | ${body?.error_message}")
                            callback(null)
                        }
                    }
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    Log.e(TAG, "Directions network failure: ${t.message}")
                    callback(null)
                }
            })
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Step 2 — Geocoding API (road type check)
    // ═════════════════════════════════════════════════════════════════════════

    private fun step2Geocoding(
        retrofit: Retrofit,
        apiKey: String,
        snappedLatLng: LatLng,
        originalLatLng: LatLng,
        callback: (Boolean) -> Unit          // true = bus-accessible road
    ) {
        val api    = retrofit.create(GeocodingApi::class.java)
        val latlng = "${snappedLatLng.latitude},${snappedLatLng.longitude}"

        Log.d(TAG, "Step2 → Geocoding $latlng")

        api.reverseGeocode(latlng, apiKey)
            .enqueue(object : Callback<GeocodingResponse> {

                override fun onResponse(
                    call: Call<GeocodingResponse>,
                    response: Response<GeocodingResponse>
                ) {
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Geocoding HTTP ${response.code()} — rejecting (safe default)")
                        callback(false)
                        return
                    }

                    val body   = response.body()
                    val status = body?.status
                    Log.d(TAG, "Geocoding status: $status")

                    when (status) {
                        "OK" -> {
                            val results = body?.results ?: emptyList()

                            // Walk through ALL results (Google returns most-specific first).
                            // Accept if ANY result is a "route" type.
                            // Reject if the top result is explicitly a house/lane/POI
                            // with NO route result anywhere in the list.

                            val hasRoute = results.any { r ->
                                r.types.any { it in BUS_ROAD_TYPES }
                            }

                            val topTypes   = results.firstOrNull()?.types ?: emptyList()
                            val topAddress = results.firstOrNull()?.formatted_address ?: "unknown"

                            if (hasRoute) {
                                val routeAddress = results
                                    .first { r -> r.types.any { it in BUS_ROAD_TYPES } }
                                    .formatted_address
                                Log.i(TAG, "✓ Bus road confirmed: $routeAddress | top types: $topTypes")
                                callback(true)
                            } else {
                                // No route found — check if it's explicitly a rejected type
                                val isExplicitlyRejected = topTypes.any { it in REJECTED_TYPES }
                                Log.w(TAG, "✗ Not a bus road | types=$topTypes | address=$topAddress | explicitReject=$isExplicitlyRejected")
                                callback(false)
                            }
                        }

                        "ZERO_RESULTS" -> {
                            // Unnamed path / track / open field — reject
                            Log.w(TAG, "Geocoding ZERO_RESULTS — unnamed location, rejecting")
                            callback(false)
                        }

                        "REQUEST_DENIED" -> {
                            Log.e(TAG, "Geocoding REQUEST_DENIED — enable Geocoding API on your Roads key in Cloud Console")
                            callback(false)
                        }

                        else -> {
                            Log.w(TAG, "Geocoding status=$status — rejecting (safe default)")
                            callback(false)
                        }
                    }
                }

                override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
                    Log.e(TAG, "Geocoding network failure: ${t.message}")
                    callback(false)
                }
            })
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Utility
    // ═════════════════════════════════════════════════════════════════════════

    fun calculateDistance(point1: LatLng, point2: LatLng): Double {
        val R     = 6371000.0
        val dLat  = Math.toRadians(point2.latitude  - point1.latitude)
        val dLng  = Math.toRadians(point2.longitude - point1.longitude)
        val a     = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(point1.latitude)) *
                cos(Math.toRadians(point2.latitude)) *
                sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
