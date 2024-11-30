package com.project.doctorpay.location

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object LocationPermissionUtil {
    private const val LOCATION_PERMISSION_REQUEST_CODE = 1001

    fun checkLocationPermission(fragment: Fragment, onPermissionGranted: () -> Unit) {
        when {
            hasLocationPermission(fragment) -> {
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

    private fun hasLocationPermission(fragment: Fragment): Boolean {
        return ContextCompat.checkSelfPermission(
            fragment.requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    fragment.requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showLocationPermissionRationale(fragment: Fragment, onPermissionGranted: () -> Unit) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("위치 권한 필요")
            .setMessage("주변 병원을 찾기 위해 위치 권한이 필요합니다. 권한을 허용해주세요.")
            .setPositiveButton("권한 허용") { dialog, _ ->
                dialog.dismiss()
                requestLocationPermission(fragment, onPermissionGranted)
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun requestLocationPermission(fragment: Fragment, onPermissionGranted: () -> Unit) {
        val permissionLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                onPermissionGranted()
            } else {
                // 권한이 거부된 경우 사용자에게 설정으로 이동하도록 안내
                showGoToSettingsDialog(fragment)
            }
        }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showGoToSettingsDialog(fragment: Fragment) {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("위치 권한 필요")
            .setMessage("위치 권한이 거부되었습니다. 설정에서 권한을 허용해주세요.")
            .setPositiveButton("설정으로 이동") { dialog, _ ->
                dialog.dismiss()
                // 앱 설정 화면으로 이동하는 인텐트 실행
                openAppSettings(fragment)
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun openAppSettings(fragment: Fragment) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${fragment.requireContext().packageName}")
        )
        fragment.startActivity(intent)
    }
}