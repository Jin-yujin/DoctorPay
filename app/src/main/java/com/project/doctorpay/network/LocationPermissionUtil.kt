package com.project.doctorpay.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object LocationPermissionUtil {
    private const val LOCATION_PERMISSION_REQUEST_CODE = 1001

    fun checkLocationPermission(fragment: Fragment, onPermissionGranted: () -> Unit) {
        when {
            ContextCompat.checkSelfPermission(
                fragment.requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                onPermissionGranted()
            }
            fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionRationale(fragment, onPermissionGranted)
            }
            else -> {
                requestLocationPermission(fragment, onPermissionGranted)
            }
        }
    }

    private fun showLocationPermissionRationale(fragment: Fragment, onPermissionGranted: () -> Unit) {
        // 권한 요청 설명 다이얼로그 표시
        // 사용자가 권한 요청에 동의하면 requestLocationPermission 호출
    }

    private fun requestLocationPermission(fragment: Fragment, onPermissionGranted: () -> Unit) {
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                    onPermissionGranted()
                }
                else -> {
                    // 권한이 거부됨
                }
            }
        }.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}