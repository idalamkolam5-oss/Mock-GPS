package com.ardy.fakegps

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.ardy.fakegps.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init OSMDroid configuration
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupListeners()
        checkPermissions()
        updateUiState()
    }

    private fun setupMap() {
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)

        val startPoint = GeoPoint(-7.4726, 110.2198) // Magelang Kota
        val mapController = binding.mapView.controller
        mapController.setZoom(15.0)
        mapController.setCenter(startPoint)
    }

    private fun setupListeners() {
        // Ambil titik tengah layar peta
        binding.btnPickCenter.setOnClickListener {
            val center = binding.mapView.mapCenter
            binding.etLat.setText(String.format(java.util.Locale.US, "%.6f", center.latitude))
            binding.etLng.setText(String.format(java.util.Locale.US, "%.6f", center.longitude))
            Toast.makeText(this, "Titik tengah peta dipilih", Toast.LENGTH_SHORT).show()
        }

        // Quick Presets
        binding.chipMgl.setOnClickListener {
            setCoordinates(-7.4726, 110.2198)
        }
        binding.chipJneMgl.setOnClickListener {
            setCoordinates(-7.4812, 110.2245)
        }
        binding.chipYogya.setOnClickListener {
            setCoordinates(-7.7956, 110.3695)
        }
        binding.chipSmg.setOnClickListener {
            setCoordinates(-6.9932, 110.4203)
        }

        // Tombol Start / Stop Mock GPS
        binding.btnToggleMock.setOnClickListener {
            if (MockLocationService.isRunning) {
                stopMockService()
            } else {
                startMockService()
            }
        }
    }

    private fun setCoordinates(lat: Double, lng: Double) {
        binding.etLat.setText(String.format(java.util.Locale.US, "%.6f", lat))
        binding.etLng.setText(String.format(java.util.Locale.US, "%.6f", lng))
        binding.mapView.controller.animateTo(GeoPoint(lat, lng))
    }

    private fun startMockService() {
        if (!isMockLocationEnabled()) {
            showMockLocationDialog()
            return
        }

        val latStr = binding.etLat.text.toString().trim()
        val lngStr = binding.etLng.text.toString().trim()

        val lat = latStr.toDoubleOrNull()
        val lng = lngStr.toDoubleOrNull()

        if (lat == null || lng == null) {
            Toast.makeText(this, "Koordinat latitude/longitude tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LAT, lat)
            putExtra(MockLocationService.EXTRA_LNG, lng)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        binding.mapView.controller.animateTo(GeoPoint(lat, lng))
        updateUiState()
        Toast.makeText(this, "Mock GPS aktif di: $lat, $lng", Toast.LENGTH_SHORT).show()
    }

    private fun stopMockService() {
        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        startService(intent)
        updateUiState()
        Toast.makeText(this, "Mock GPS dinonaktifkan", Toast.LENGTH_SHORT).show()
    }

    private fun updateUiState() {
        binding.root.postDelayed({
            if (MockLocationService.isRunning) {
                binding.btnToggleMock.text = getString(R.string.stop_mock)
                binding.btnToggleMock.setBackgroundColor(Color.parseColor("#757575")) // Abu-abu
                binding.tvStatus.text = "Status: AKTIF (%.5f, %.5f)".format(
                    MockLocationService.currentLat,
                    MockLocationService.currentLng
                )
                binding.tvStatus.setTextColor(Color.parseColor("#2E7D32")) // Hijau
            } else {
                binding.btnToggleMock.text = getString(R.string.start_mock)
                binding.btnToggleMock.setBackgroundColor(Color.parseColor("#D32F2F")) // Merah JNE
                binding.tvStatus.text = "Status: Tidak Aktif"
                binding.tvStatus.setTextColor(Color.parseColor("#000000"))
            }
        }, 200)
    }

    private fun isMockLocationEnabled(): Boolean {
        val opsManager = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            opsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            opsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showMockLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Izin Mock Location Diperlukan")
            .setMessage("Aplikasi ini belum dipilih sebagai 'Aplikasi Lokasi Tiruan' di Opsi Pengembang.\n\nSilakan buka Opsi Pengembang -> Pilih aplikasi lokasi tiruan -> Pilih Mock GPS JNE.")
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun checkPermissions() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        updateUiState()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
