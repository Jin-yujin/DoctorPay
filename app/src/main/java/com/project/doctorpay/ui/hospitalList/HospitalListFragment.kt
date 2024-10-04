package com.project.doctorpay.ui.hospitalList


import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.DB.HospitalInfo
import com.project.doctorpay.R
import com.project.doctorpay.databinding.ViewHospitalListBinding
import com.project.doctorpay.databinding.CompListItemBinding


class HospitalListFragment : Fragment() {
    private var _binding: ViewHospitalListBinding? = null
    private val binding get() = _binding!!

    private var categoryId: Int = -1
    private lateinit var adapter: HospitalListAdapter

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

        setupListView()
        loadHospitalList()

        binding.checkFilter.setOnCheckedChangeListener { _, isChecked ->
            loadHospitalList(isChecked)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHospitalList(binding.checkFilter.isChecked)
        }

        binding.mListView.setOnItemClickListener { _, _, position, _ ->
            val hospital = adapter.getItem(position)
            hospital?.let {
                val detailFragment = HospitalDetailFragment.newInstance(
                    hospitalName = it.name,
                    hospitalAddress = it.address,
                    hospitalDepartment = it.department,
                    hospitalTime = it.time,
                    hospitalPhoneNumber = it.phoneNumber,
                    isFromMap = false
                )

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, detailFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun setupListView() {
        adapter = HospitalListAdapter(requireContext(), mutableListOf())
        binding.mListView.adapter = adapter
    }

    private fun loadHospitalList(onlyAvailable: Boolean = false) {
        // TODO: 실제 데이터 로딩 로직으로 대체
        val dummyData = listOf(
            HospitalInfo(
                LatLng(37.5665, 126.9780),
                "A병원",
                "서울시 중구 A로 123",
                "내과, 외과",
                "09:00 - 18:00",
                "02-1234-5678",
                "영업중", 2.5

            ),
            HospitalInfo(
                LatLng(37.5660, 126.9770),
                "B병원",
                "서울시 중구 B로 456",
                "소아과, 피부과",
                "10:00 - 19:00",
                "02-2345-6789",
                "영업중", 3.5
            ),
            HospitalInfo(
                LatLng(37.5670, 126.9790),
                "C병원",
                "서울시 중구 C로 789",
                "정형외과, 신경과",
                "08:30 - 17:30",
                "02-3456-7890",
                "영업 마감", 4.2
            )
        )

        adapter.clear()
        adapter.addAll(dummyData)
        adapter.notifyDataSetChanged()

        binding.swipeRefreshLayout.isRefreshing = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    inner class HospitalListAdapter(context: Context, hospitals: List<HospitalInfo>) :
        ArrayAdapter<HospitalInfo>(context, 0, hospitals) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val binding = if (convertView == null) {
                CompListItemBinding.inflate(LayoutInflater.from(context), parent, false)
            } else {
                CompListItemBinding.bind(convertView)
            }

            val hospital = getItem(position)!!

            binding.itemHospitalName.text = hospital.name
            binding.itemHospitalAddress.text = hospital.address
            binding.itemHospitalNum.text = hospital.phoneNumber
            binding.tvState.text = hospital.state
            binding.itemHospitalDistance.text = "000m"
            return binding.root
        }
    }
}