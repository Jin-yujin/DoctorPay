package com.project.doctorpay.location

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.project.doctorpay.databinding.FragmentLocationSettingBinding
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.comp.KakaoSearchService
import kotlinx.coroutines.launch

class LocationSettingFragment : Fragment() {
    private var _binding: FragmentLocationSettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchAdapter: LocationSearchAdapter
    private lateinit var kakaoSearchService: KakaoSearchService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        kakaoSearchService = KakaoSearchService(requireContext())
        setupToolbar()
        setupSearchView()
        setupRecyclerView()
        setupCurrentLocationButton()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupSearchView() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()
                if (!query.isNullOrBlank() && query.length >= 2) {
                    lifecycleScope.launch {
                        try {
                            val results = kakaoSearchService.searchPlaces(query)
                            searchAdapter.submitList(results)
                        } catch (e: Exception) {
                            Log.e("LocationSetting", "Search error", e)
                        }
                    }
                } else {
                    searchAdapter.submitList(emptyList())
                }
            }
        })
    }

    private fun setupRecyclerView() {
        searchAdapter = LocationSearchAdapter { location ->
            setFragmentResult("location_selection", bundleOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "address" to location.address
            ))
            parentFragmentManager.popBackStack()
        }

        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupCurrentLocationButton() {
        binding.currentLocationButton.setOnClickListener {
            LocationPermissionUtil.checkLocationPermission(this) {
                getCurrentLocation()
            }
        }
    }

    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    lifecycleScope.launch {
                        val address = kakaoSearchService.getAddressFromLocation(
                            latitude = it.latitude,
                            longitude = it.longitude
                        )
                        setFragmentResult("location_selection", bundleOf(
                            "latitude" to it.latitude,
                            "longitude" to it.longitude,
                            "address" to address
                        ))
                        parentFragmentManager.popBackStack()
                    }
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}