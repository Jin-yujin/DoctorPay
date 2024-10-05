package com.project.doctorpay.ui.favorite

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.DB.HospitalInfo
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentFavoriteBinding
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.hospitalList.HospitalDetailFragment

class FavoriteFragment : Fragment() {
    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HospitalAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadFavoriteHospitals()

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadFavoriteHospitals()
        }
    }

    private fun setupRecyclerView() {
        adapter = HospitalAdapter(emptyList()) { hospital ->
            navigateToHospitalDetail(hospital)
        }
        binding.favoriteRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.favoriteRecyclerView.adapter = adapter
    }

    private fun loadFavoriteHospitals() {
        // TODO: Replace with actual data loading logic for favorite hospitals
        val dummyFavorites = listOf(
            HospitalInfo(
                LatLng(37.5665, 126.9780),
                "즐겨찾기 병원 A",
                "서울시 중구 A로 123",
                "내과, 외과",
                "09:00 - 18:00",
                "02-1234-5678",
                "영업중",
                4.5
            ),
            HospitalInfo(
                LatLng(37.5660, 126.9770),
                "즐겨찾기 병원 B",
                "서울시 중구 B로 456",
                "소아과, 피부과",
                "10:00 - 19:00",
                "02-2345-6789",
                "영업중",
                4.2
            )
        )

        adapter.updateHospitals(dummyFavorites)
        binding.swipeRefreshLayout.isRefreshing = false
    }

    private fun navigateToHospitalDetail(hospital: HospitalInfo) {
        val detailFragment = HospitalDetailFragment.newInstance(
            hospitalName = hospital.name,
            hospitalAddress = hospital.address,
            hospitalDepartment = hospital.department,
            hospitalTime = hospital.time,
            hospitalPhoneNumber = hospital.phoneNumber,
            isFromMap = false,
            categoryId = -1 // Assuming no specific category for favorites
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}