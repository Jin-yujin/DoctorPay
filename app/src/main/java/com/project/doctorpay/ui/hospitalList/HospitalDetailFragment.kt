package com.project.doctorpay.ui.hospitalList

import NonPaymentItem
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentHospitalDetailBinding
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.ui.favorite.FavoriteFragment
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.network.NetworkModule.healthInsuranceApi
import kotlinx.coroutines.launch

class HospitalDetailFragment : Fragment() {

    private var _binding: FragmentHospitalDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(healthInsuranceApi)
    }

    private var isFromMap: Boolean = false
    private var category: String = ""
    private lateinit var hospital: HospitalInfo

    private var shouldShowToolbar = true
    private var listener: HospitalDetailListener? = null

    interface HospitalDetailListener {
        fun onBackFromHospitalDetail()
    }

    fun hideToolbar() {
        shouldShowToolbar = false
    }

    fun setHospitalDetailListener(listener: HospitalDetailListener) {
        this.listener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            hospital = it.getParcelable("hospital_info") ?: throw IllegalArgumentException("Hospital info must be provided")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHospitalDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUI(hospital)
        setupClickListeners()
        setupBackPressHandler()
    }

    private fun updateUI(hospital: HospitalInfo) {
        binding.apply {
            tvHospitalName.text = hospital.name
            tvHospitalType.text = hospital.clCdNm
            tvHospitalAddress.text = hospital.address
            tvHospitalPhone.text = hospital.phoneNumber
            ratingBar.rating = hospital.rating.toFloat()
            tvHospitalHours.text = hospital.time.ifEmpty { "영업 시간 정보 없음" }
            tvHospitalHoliday.text = "휴일: 정보 없음" // 실제 데이터가 있다면 그것을 사용
            tvNightCare.text = "야간진료: 정보 없음" // 실제 데이터가 있다면 그것을 사용
            tvFemaleDoctors.text = "여의사 진료: 정보 없음" // 실제 데이터가 있다면 그것을 사용
            tvHospitalDepartment.text = hospital.department
        }

        loadNonCoveredItems(hospital.nonPaymentItems)
        loadReviewPreviews() // 리뷰 데이터가 있다면 이 메서드를 구현하여 실제 리뷰를 표시
    }

    private fun setupClickListeners() {
        binding.apply {
            btnStart.setOnClickListener { openMapWithDirections("출발") }
            btnDestination.setOnClickListener { openMapWithDirections("도착") }
            btnSave.setOnClickListener { /* TODO: Implement save functionality */ }
            btnCall.setOnClickListener { dialPhoneNumber(hospital.phoneNumber) }
            btnShare.setOnClickListener { shareHospitalInfo() }
            tvHospitalPhone.setOnClickListener { dialPhoneNumber(hospital.phoneNumber) }
            btnAppointment.setOnClickListener { /* TODO: Implement appointment functionality */ }
            btnMoreReviews.setOnClickListener { navigateToReviewsFragment() }
            btnMoreNonCoveredItems.setOnClickListener { /* TODO: Implement non-covered items list functionality */ }
            btnBack.setOnClickListener { navigateBack() }
        }
    }

    private fun setupBackPressHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun openMapWithDirections(mode: String) {
        val encodedAddress = Uri.encode(hospital.address)
        val uri = when (mode) {
            "출발" -> Uri.parse("https://maps.google.com/maps?saddr=$encodedAddress&daddr=")
            "도착" -> Uri.parse("https://maps.google.com/maps?daddr=$encodedAddress")
            else -> Uri.parse("https://maps.google.com/maps?q=$encodedAddress")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun dialPhoneNumber(phoneNumber: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
    }

    private fun shareHospitalInfo() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, hospital.name)
            putExtra(Intent.EXTRA_TEXT, "${hospital.name}\n${hospital.address}\n${hospital.phoneNumber}")
        }
        startActivity(Intent.createChooser(shareIntent, "공유하기"))
    }

    private fun loadReviewPreviews() {
        // TODO: Load actual review previews from API or database
        addReviewPreview("김OO", "친절하고 좋았어요", 5f)
        addReviewPreview("이OO", "대기 시간이 좀 길었어요", 3f)
    }

    private fun loadNonCoveredItems(items: List<NonPaymentItem>) {
        binding.layoutNonCoveredItems.removeAllViews()
        items.take(2).forEach { item ->
            addNonCoveredItem(item)
        }
    }

    private fun addNonCoveredItem(item: NonPaymentItem) {
        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_non_covered, binding.layoutNonCoveredItems, false)

        itemView.findViewById<TextView>(R.id.tvItemName).text = item.itemNm ?: "Unknown Item"
        val priceRange = when {
            item.cntrImpAmtMin != null && item.cntrImpAmtMax != null ->
                "${item.cntrImpAmtMin}원 ~ ${item.cntrImpAmtMax}원"
            item.cntrImpAmtMin != null -> "최소 ${item.cntrImpAmtMin}원"
            item.cntrImpAmtMax != null -> "최대 ${item.cntrImpAmtMax}원"
            else -> "가격 정보 없음"
        }
        itemView.findViewById<TextView>(R.id.tvItemPrice).text = priceRange

        binding.layoutNonCoveredItems.addView(itemView)
    }


    private fun addReviewPreview(name: String, content: String, rating: Float) {
        val reviewView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_review_preview, binding.layoutReviews, false)
        reviewView.findViewById<TextView>(R.id.tvReviewerName).text = name
        reviewView.findViewById<TextView>(R.id.tvReviewContent).text = content
        reviewView.findViewById<RatingBar>(R.id.rbReviewRating).rating = rating
        binding.layoutReviews.addView(reviewView)
    }

    private fun addNonCoveredItem(itemName: String, itemCd: String) {
        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_non_covered, binding.layoutNonCoveredItems, false)
        itemView.findViewById<TextView>(R.id.tvItemName).text = itemName
        itemView.findViewById<TextView>(R.id.tvItemPrice).text = itemCd // Changed 'price' to 'itemCd'
        binding.layoutNonCoveredItems.addView(itemView)
    }

    private fun navigateToReviewsFragment() {
        // TODO: Implement navigation to ReviewsFragment
    }

    private fun navigateBack() {
        when {
            isFromMap -> listener?.onBackFromHospitalDetail()
            parentFragment is HospitalListFragment -> parentFragmentManager.popBackStack()
            parentFragment is FavoriteFragment -> {
                listener?.onBackFromHospitalDetail()
                parentFragmentManager.popBackStack()
            }
            else -> {
                if (category.isNotEmpty()) {
                    val hospitalListFragment = HospitalListFragment.newInstance()
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, hospitalListFragment)
                        .commit()
                } else {
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_HOSPITAL_ID = "hospital_id"
        private const val ARG_IS_FROM_MAP = "is_from_map"
        private const val ARG_CATEGORY = "category"

        fun newInstance(hospitalId: String, isFromMap: Boolean, category: String) =
            HospitalDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_HOSPITAL_ID, hospitalId)
                    putBoolean(ARG_IS_FROM_MAP, isFromMap)
                    putString(ARG_CATEGORY, category)
                }
            }
    }
}