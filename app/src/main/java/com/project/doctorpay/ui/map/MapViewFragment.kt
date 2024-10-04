package com.project.doctorpay.ui.map

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.project.doctorpay.DB.HospitalInfo
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentMapviewBinding
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.hospitalList.HospitalDetailFragment

class MapViewFragment : Fragment(), OnMapReadyCallback, HospitalDetailFragment.HospitalDetailListener {

    private var _binding: FragmentMapviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var naverMap: NaverMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private val hospitals = mutableListOf<HospitalInfo>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onBackFromHospitalDetail() {
        showHospitalList()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        setupBottomSheet()
        setupRecyclerView()
        showHospitalList()
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.saveFlags = BottomSheetBehavior.SAVE_ALL
        bottomSheetBehavior.isFitToContents = false
        bottomSheetBehavior.halfExpandedRatio = 0.5f

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updateExpandButtonIcon(newState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        binding.expandButton.setOnClickListener { toggleBottomSheetState() }
    }

    private fun setupRecyclerView() {
        binding.hospitalRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.hospitalRecyclerView.adapter = HospitalAdapter(hospitals) { hospital ->
            showHospitalDetail(hospital)
        }
    }


    private fun toggleBottomSheetState() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            BottomSheetBehavior.STATE_HALF_EXPANDED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            else -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun updateExpandButtonIcon(state: Int) {
        binding.expandButton.setImageResource(
            when (state) {
                BottomSheetBehavior.STATE_EXPANDED -> android.R.drawable.arrow_down_float
                BottomSheetBehavior.STATE_HALF_EXPANDED -> android.R.drawable.arrow_up_float
                else -> android.R.drawable.arrow_up_float
            }
        )
    }


    override fun onMapReady(map: NaverMap) {
        naverMap = map
        val initialPosition = LatLng(37.5666102, 126.9783881)
        naverMap.moveCamera(CameraUpdate.scrollTo(initialPosition))
        addHospitalMarkers()
    }

    private fun addHospitalMarkers() {
        val dummyData = listOf(
            HospitalInfo(
                LatLng(37.5665, 126.9780),
                "A병원",
                "서울시 중구 A로 123",
                "내과, 외과",
                "09:00 - 18:00",
                "02-1234-5678",
                "영업중",
                2.5
            ),
            HospitalInfo(
                LatLng(37.5660, 126.9770),
                "B병원",
                "서울시 중구 B로 456",
                "소아과, 피부과",
                "10:00 - 19:00",
                "02-2345-6789",
                "영업중",
                3.5
            ),
            HospitalInfo(
                LatLng(37.5670, 126.9790),
                "C병원",
                "서울시 중구 C로 789",
                "정형외과, 신경과",
                "08:30 - 17:30",
                "02-3456-7890",
                "영업 마감",
                4.2
            )
        )

        hospitals.addAll(dummyData)

        dummyData.forEachIndexed { index, hospital ->
            Marker().apply {
                position = hospital.location
                map = naverMap
                captionText = hospital.name
                tag = index
                setOnClickListener {
                    showHospitalDetail(hospitals[it.tag as Int])
                    true
                }
            }
        }

        (binding.hospitalRecyclerView.adapter as HospitalAdapter).notifyDataSetChanged()
    }

    private fun showHospitalDetail(hospital: HospitalInfo) {
        val hospitalDetailFragment = HospitalDetailFragment.newInstance(
            hospital.name,
            hospital.address,
            hospital.department,
            hospital.time,
            hospital.phoneNumber,
            isFromMap = true
        )

        hospitalDetailFragment.setHospitalDetailListener(this)

        childFragmentManager.beginTransaction()
            .replace(R.id.hospitalDetailContainer, hospitalDetailFragment).addToBackStack(null)
            .commit()

        binding.hospitalRecyclerView.visibility = View.GONE
        binding.hospitalDetailContainer.visibility = View.VISIBLE

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun showHospitalList() {
        binding.hospitalRecyclerView.visibility = View.VISIBLE
        binding.hospitalDetailContainer.visibility = View.GONE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Lifecycle methods for MapView
    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }
}