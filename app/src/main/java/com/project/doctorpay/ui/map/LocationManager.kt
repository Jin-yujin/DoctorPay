package com.project.doctorpay.ui.map

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.NaverMap
import com.project.doctorpay.api.HospitalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocationManager(
    private val fragment: Fragment,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val naverMap: NaverMap,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val viewModel: HospitalViewModel,
    private val onLocationUpdate: (LatLng) -> Unit,
    private val onLocationError: (String) -> Unit
) {
    private var locationCallback: LocationCallback? = null
    private var isInitialLocationSet = false

    companion object {
        private const val DEFAULT_LAT = 37.5666805  // 서울 중심부
        private const val DEFAULT_LNG = 127.0784147
        private const val LOCATION_UPDATE_INTERVAL = 10000L
        private const val LOCATION_FASTEST_INTERVAL = 5000L
        private const val LOCATION_MAX_WAIT_TIME = 15000L
    }

    private val requestPermissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                startLocationTracking()
            }
            else -> {
                showLocationPermissionRationale()
            }
        }
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("위치 권한 필요")
            .setMessage("주변 병원 확인을 위해 위치 권한이 필요합니다.")
            .setPositiveButton("권한 요청") { _, _ ->
                requestLocationPermissions()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
                loadDefaultLocation()
            }
            .create()
            .show()
    }

    fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    // 위치 권한 체크
    fun hasLocationPermission(): Boolean {
        return fragment.context?.let { context ->
            (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED)
        } ?: false
    }

    // 위치 추적 시작
    fun startLocationTracking() {
        if (!hasLocationPermission()) {
            onLocationError("위치 권한이 없습니다")
            loadDefaultLocation()
            return
        }

        try {
            // 지도 설정
            naverMap.locationTrackingMode = LocationTrackingMode.Follow

            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = LOCATION_UPDATE_INTERVAL
                fastestInterval = LOCATION_FASTEST_INTERVAL
                maxWaitTime = LOCATION_MAX_WAIT_TIME
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (!hasLocationPermission()) return

                    try {
                        locationResult.lastLocation?.let { location ->
                            val newLocation = LatLng(location.latitude, location.longitude)
                            loadDataWithLocation(newLocation)
                        }
                    } catch (e: SecurityException) {
                        Log.e("LocationManager", "Security exception during location update", e)
                        loadDefaultLocation()
                    }
                }
            }

            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    Looper.getMainLooper()
                ).addOnFailureListener { e ->
                    Log.e("LocationManager", "Failed to request location updates", e)
                    loadDefaultLocation()
                }
            } catch (e: SecurityException) {
                Log.e("LocationManager", "Security exception requesting location updates", e)
                loadDefaultLocation()
            }

        } catch (e: Exception) {
            Log.e("LocationManager", "Error enabling location tracking", e)
            loadDefaultLocation()
        }
    }

    // 데이터 로드
    private fun loadDataWithLocation(location: LatLng) {
        lifecycleScope.launch {
            try {
                if (!hasLocationPermission()) {
                    Log.d("LocationManager", "No location permission when loading data")
                    return@launch
                }

                // 위치 업데이트 콜백 실행
                onLocationUpdate(location)

                if (!isInitialLocationSet) {
                    isInitialLocationSet = true
                    moveToLocation(location)
                }

                // 데이터 로드는 IO 스레드에서 실행
                withContext(Dispatchers.IO) {
                    viewModel.fetchNearbyHospitals(
                        viewId = HospitalViewModel.MAP_VIEW,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radius = 3000,
                        forceRefresh = true
                    )
                }
            } catch (e: Exception) {
                Log.e("LocationManager", "Error loading data with location", e)
                onLocationError("데이터를 불러오는 중 오류가 발생했습니다")
            }
        }
    }

    // 위치로 이동
    fun moveToLocation(location: LatLng) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                naverMap.moveCamera(CameraUpdate.scrollTo(location))
            } catch (e: Exception) {
                Log.e("LocationManager", "Error moving camera to location", e)
            }
        }
    }

    // 기본 위치(서울 중심부) 로드
    fun loadDefaultLocation() {
        val defaultLocation = LatLng(DEFAULT_LAT, DEFAULT_LNG)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                loadDataWithLocation(defaultLocation)
            } catch (e: Exception) {
                Log.e("LocationManager", "Error loading default location", e)
                onLocationError("기본 위치를 불러오는데 실패했습니다")
            }
        }
    }

    // 거리 계산
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    // 좌표 유효성 검사
    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude != 0.0 && longitude != 0.0 &&
                latitude >= 33.0 && latitude <= 43.0 && // 한국 위도 범위
                longitude >= 124.0 && longitude <= 132.0 // 한국 경도 범위
    }

    // 위치 업데이트 중지
    fun stopLocationUpdates() {
        try {
            locationCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }
            locationCallback = null
        } catch (e: Exception) {
            Log.e("LocationManager", "Error stopping location updates", e)
        }
    }
}