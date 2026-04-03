package com.example.ai_smarttransportsystem

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.ai_smarttransportsystem.utils.MapUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MapStopSelectionActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var progressLoading: ProgressBar
    private lateinit var mMap: GoogleMap
    private var selectedMarker: Marker? = null

    companion object {
        private const val TAG = "MapSelection"
        val FAISALABAD_BOUNDS = LatLngBounds(
            LatLng(31.30, 73.00),   // SW corner
            LatLng(31.55, 73.20)    // NE corner
        )
    }

    private lateinit var startAutocomplete: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_stop_selection)

        progressLoading = findViewById(R.id.progress_loading)

        // Places SDK uses the Android-restricted MAPS key
        val mapsKey = BuildConfig.MAPS_API_KEY
        if (mapsKey.isNotEmpty() && !Places.isInitialized()) {
            Places.initialize(applicationContext, mapsKey)
        }

        startAutocomplete = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            when (result.resultCode) {
                RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(result.data!!)
                    handleSelectedPlace(place)
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status = Autocomplete.getStatusFromIntent(result.data!!)
                    Log.e(TAG, "Autocomplete error: ${status.statusMessage}")
                    Toast.makeText(this, "Search error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // ── Confirm button ────────────────────────────────────────────────────
        findViewById<FloatingActionButton>(R.id.btn_confirm_location).setOnClickListener {
            val marker = selectedMarker ?: run {
                Toast.makeText(this, "Please select a location first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressLoading.visibility = View.VISIBLE
            val tappedPos = marker.position
            Toast.makeText(this, "Checking bus accessibility…", Toast.LENGTH_SHORT).show()

            // Directions API + Geocoding validation — uses unrestricted ROADS_API_KEY
            MapUtils.validateAndSnapLocation(
                context = this,
                apiKey  = BuildConfig.ROADS_API_KEY,
                originalLatLng = tappedPos
            ) { snappedPos, isValid ->

                progressLoading.visibility = View.GONE
                val rootView = findViewById<View>(R.id.main)

                when {
                    // ✓ Valid — named driveable road confirmed
                    isValid && snappedPos != null -> {
                        // Snap marker to the road-aligned endpoint
                        selectedMarker?.remove()
                        selectedMarker = mMap.addMarker(
                            MarkerOptions()
                                .position(snappedPos)
                                .title("Bus Stop")
                                .snippet("Confirmed bus-accessible")
                                .icon(BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_GREEN))
                        )
                        setResult(RESULT_OK, Intent().apply {
                            putExtra("LAT", snappedPos.latitude)
                            putExtra("LNG", snappedPos.longitude)
                        })
                        finish()
                    }

                    // ✗ Route found but road is a house / lane / park — not bus-accessible
                    !isValid && snappedPos != null -> {
                        selectedMarker?.remove()
                        selectedMarker = mMap.addMarker(
                            MarkerOptions()
                                .position(snappedPos)
                                .title("Not bus-accessible")
                                .snippet("Narrow lane, house, or non-road area")
                                .icon(BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_RED))
                        )
                        Snackbar.make(
                            rootView,
                            "This location is on a house, lane, or non-bus road. Please select a main road.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }

                    // ✗ No driveable road found near this point at all
                    else -> {
                        Snackbar.make(
                            rootView,
                            "No driveable road found nearby. Please select a point on a main road.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        // ── Search bar ────────────────────────────────────────────────────────
        findViewById<EditText>(R.id.et_search_place).setOnClickListener {
            val fields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS
            )
            val intent = Autocomplete
                .IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .setLocationBias(RectangularBounds.newInstance(FAISALABAD_BOUNDS))
                .setCountry("PK")
                .setTypesFilter(listOf("address"))
                .build(this)
            startAutocomplete.launch(intent)
        }
    }

    // ── Places autocomplete result ─────────────────────────────────────────────

    private fun handleSelectedPlace(place: Place) {
        val latLng = place.latLng ?: run {
            Toast.makeText(this, "Could not get location for this place", Toast.LENGTH_SHORT).show()
            return
        }
        if (!FAISALABAD_BOUNDS.contains(latLng)) {
            Log.w(TAG, "Place outside bounds: $latLng")
            Toast.makeText(this, "Location is outside Faisalabad area", Toast.LENGTH_SHORT).show()
            return
        }
        selectedMarker?.remove()
        selectedMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(place.name ?: "Selected")
                .snippet(place.address)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        Toast.makeText(this, "Selected: ${place.address}", Toast.LENGTH_SHORT).show()
    }

    // ── Map ready ─────────────────────────────────────────────────────────────

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setLatLngBoundsForCameraTarget(FAISALABAD_BOUNDS)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(31.4187, 73.0791), 12f)
        )

        mMap.setOnMapClickListener { latLng ->
            if (!FAISALABAD_BOUNDS.contains(latLng)) {
                Toast.makeText(this, "Location outside Faisalabad area", Toast.LENGTH_SHORT).show()
                return@setOnMapClickListener
            }
            Log.i(TAG, "Tap: ${latLng.latitude}, ${latLng.longitude}")
            selectedMarker?.remove()
            selectedMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Selected Stop")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
        }
    }
}
