package com.project.doctorpay.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.project.doctorpay.R

class MapViewFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var naverMap: NaverMap

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mapview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }

    override fun onMapReady(map: NaverMap) {
        naverMap = map

        // 초기 카메라 위치 설정 (예: 서울시청)
        val initialPosition = LatLng(37.5666102, 126.9783881)
        naverMap.moveCamera(CameraUpdate.scrollTo(initialPosition))

        // 주변 병원 정보 표시 (예시)
        addHospitalMarkers()
    }

    private fun addHospitalMarkers() {
        // 이 부분에서 실제 병원 데이터를 가져와 마커로 표시해야 합니다.
        // 아래는 예시 데이터입니다.
        val hospitals = listOf(
            LatLng(37.5665, 126.9780) to "A병원",
            LatLng(37.5660, 126.9770) to "B병원",
            LatLng(37.5670, 126.9790) to "C병원"
        )

        hospitals.forEach { (location, name) ->
            Marker().apply {
                position = location
                map = naverMap
                captionText = name
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}