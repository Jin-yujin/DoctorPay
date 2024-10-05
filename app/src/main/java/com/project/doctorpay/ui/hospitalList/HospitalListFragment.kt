package com.project.doctorpay.ui.hospitalList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.DB.HospitalInfo
import com.project.doctorpay.R
import com.project.doctorpay.databinding.ViewHospitalListBinding

class HospitalListFragment : Fragment() {
    private var _binding: ViewHospitalListBinding? = null
    private val binding get() = _binding!!

    private var categoryId: Int = -1
    private lateinit var adapter: HospitalAdapter

    companion object {
        private const val ARG_CATEGORY_ID = "category_id"

        fun newInstance(categoryId: Int): HospitalListFragment {
            val fragment = HospitalListFragment()
            val args = Bundle()
            args.putInt(ARG_CATEGORY_ID, categoryId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            categoryId = it.getInt(ARG_CATEGORY_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ViewHospitalListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadHospitalList()

        binding.checkFilter.setOnCheckedChangeListener { _, isChecked ->
            loadHospitalList(isChecked)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHospitalList(binding.checkFilter.isChecked)
        }
    }

    private fun setupRecyclerView() {
        adapter = HospitalAdapter(emptyList()) { hospital ->
            navigateToHospitalDetail(hospital)
        }
        binding.mListView.layoutManager = LinearLayoutManager(requireContext())
        binding.mListView.adapter = adapter
    }

    private fun loadHospitalList(onlyAvailable: Boolean = false) {
        // TODO: Replace with actual data loading logic
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

        val filteredData = if (onlyAvailable) {
            dummyData.filter { it.state == "영업중" }
        } else {
            dummyData
        }

        adapter.updateHospitals(filteredData)

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
            categoryId = categoryId
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