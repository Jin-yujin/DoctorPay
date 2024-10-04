package com.project.doctorpay.ui.hospitalList

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
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
            // TODO: 필터 적용 로직 구현
            loadHospitalList(isChecked)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHospitalList(binding.checkFilter.isChecked)
        }

        // Add click listener to list items
        binding.mListView.setOnItemClickListener { _, _, position, _ ->
            val hospital = adapter.getItem(position)
            hospital?.let {
                val intent = Intent(requireContext(), HospitalDetailActivity::class.java).apply {
                    putExtra("HOSPITAL_NAME", it.name)
                    putExtra("HOSPITAL_TIME", it.time)
                    putExtra("HOSPITAL_DOCTOR", it.doctor)
                    putExtra("HOSPITAL_CAPACITY", it.capacity)
                    // Add these lines if they're available in your Hospital data class
                    // putExtra("HOSPITAL_ADDRESS", it.address)
                    // putExtra("HOSPITAL_PHONE", it.phone)
                    // putExtra("HOSPITAL_RATING", it.rating)
                }
                startActivity(intent)
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
            Hospital("서울대학교병원", "09:00 - 18:00", "김의사", "3/5"),
            Hospital("연세세브란스병원", "10:00 - 19:00", "이의사", "2/4"),
            Hospital("가톨릭대학교 서울성모병원", "08:30 - 17:30", "박의사", "4/6")
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

    data class Hospital(
        val name: String,
        val time: String,
        val doctor: String,
        val capacity: String
    )

    inner class HospitalListAdapter(context: Context, hospitals: List<Hospital>) :
        ArrayAdapter<Hospital>(context, 0, hospitals) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val binding = if (convertView == null) {
                CompListItemBinding.inflate(LayoutInflater.from(context), parent, false)
            } else {
                CompListItemBinding.bind(convertView)
            }

            val hospital = getItem(position)!!

            binding.tvName.text = hospital.name
            binding.tvTimeInfo.text = hospital.time
            binding.tvWriter.text = hospital.doctor
            binding.tvPartner.text = hospital.capacity

            return binding.root
        }
    }
}