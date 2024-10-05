package com.project.doctorpay.ui.hospitalList

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentHospitalDetailBinding
import com.project.doctorpay.ui.favorite.FavoriteFragment
import com.project.doctorpay.ui.map.MapViewFragment

class HospitalDetailFragment : Fragment() {


    private var _binding: FragmentHospitalDetailBinding? = null
    private val binding get() = _binding!!
    private var isFromMap: Boolean = false
    private var categoryId: Int = -1

    private lateinit var hospitalName: String
    private lateinit var hospitalAddress: String
    private lateinit var hospitalPhone: String

    private var shouldShowToolbar = true

    fun hideToolbar() {
        shouldShowToolbar = false
    }

    // Add this line to declare the listener property
    private var listener: HospitalDetailListener? = null

    interface HospitalDetailListener {
        fun onBackFromHospitalDetail()
    }

    fun setHospitalDetailListener(listener: HospitalDetailListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHospitalDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!shouldShowToolbar) {
            binding.appBarLayout.visibility = View.GONE
        }

        arguments?.let {
            hospitalName = it.getString("hospitalName", "")
            hospitalAddress = it.getString("hospitalAddress", "")
            val hospitalDepartment = it.getString("hospitalDepartment", "")
            val hospitalTime = it.getString("hospitalTime", "")
            hospitalPhone = it.getString("hospitalPhoneNumber", "")
            isFromMap = it.getBoolean(ARG_IS_FROM_MAP, false)
            categoryId = it.getInt(ARG_CATEGORY_ID, -1)

            // Set up views with hospital information
            binding.tvHospitalName.text = hospitalName
            binding.tvHospitalType.text = hospitalDepartment
            binding.tvHospitalAddress.text = hospitalAddress
            binding.tvHospitalPhone.text = hospitalPhone
            binding.ratingBar.rating = 4.5f // 예시 평점, 실제로는 arguments에서 받아와야 함

            binding.tvHospitalHours.text = hospitalTime
            binding.tvHospitalHoliday.text = "휴일: 일요일, 공휴일"
            binding.tvNightCare.text = "야간진료: 가능"
            binding.tvFemaleDoctors.text = "여의사 진료: 가능"
        }


        // 뒤로가기 버튼
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        binding.btnBack.setOnClickListener {
            navigateBack()
        }


        // Set up other views and click listeners
        setupClickListeners()
        // Load reviews and non-covered items
        loadReviewPreviews()
        loadNonCoveredItems()
    }

    private fun setupClickListeners() {

        binding.btnStart.setOnClickListener { openMapWithDirections("출발") }
        binding.btnDestination.setOnClickListener { openMapWithDirections("도착") }
        binding.btnSave.setOnClickListener { /* TODO: Implement save functionality */ }
        binding.btnCall.setOnClickListener { dialPhoneNumber(hospitalPhone) }
        binding.btnShare.setOnClickListener { shareHospitalInfo() }
        binding.tvHospitalPhone.setOnClickListener { dialPhoneNumber(hospitalPhone) }

        binding.btnAppointment.setOnClickListener {
            // TODO: Implement appointment functionality
        }

        binding.btnMoreReviews.setOnClickListener {
            val intent = Intent(
                requireContext(),
                com.project.doctorpay.ui.reviews.ReviewsFragment::class.java
            ).apply {
                putExtra("HOSPITAL_NAME", hospitalName)
            }
            startActivity(intent)
        }

        binding.btnMoreNonCoveredItems.setOnClickListener {
            // TODO: Implement non-covered items list functionality
        }
    }

    private fun openMapWithDirections(mode: String) {
        val encodedAddress = Uri.encode(hospitalAddress)
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
            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(browserIntent)
        }
    }

    private fun loadReviewPreviews() {
        // TODO: Load review previews from API or database
        // For now, we'll add some dummy data
        addReviewPreview("김OO", "친절하고 좋았어요", 5f)
        addReviewPreview("이OO", "대기 시간이 좀 길었어요", 3f)
    }

    private fun loadNonCoveredItems() {
        // TODO: Load non-covered items from API or database
        // For now, we'll add some dummy data
        addNonCoveredItem("MRI 검사", "500,000원")
        addNonCoveredItem("치과 임플란트", "1,500,000원")
    }

    private fun dialPhoneNumber(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        startActivity(intent)
    }

    private fun shareHospitalInfo() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, hospitalName)
            putExtra(Intent.EXTRA_TEXT, "$hospitalName\n$hospitalAddress\n$hospitalPhone")
        }
        startActivity(Intent.createChooser(shareIntent, "공유하기"))
    }

    private fun addReviewPreview(name: String, content: String, rating: Float) {
        val reviewView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_review_preview, binding.layoutReviews, false)
        reviewView.findViewById<TextView>(R.id.tvReviewerName).text = name
        reviewView.findViewById<TextView>(R.id.tvReviewContent).text = content
        reviewView.findViewById<RatingBar>(R.id.rbReviewRating).rating = rating
        binding.layoutReviews.addView(reviewView)
    }

    private fun addNonCoveredItem(itemName: String, price: String) {
        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_non_covered, binding.layoutNonCoveredItems, false)
        itemView.findViewById<TextView>(R.id.tvItemName).text = itemName
        itemView.findViewById<TextView>(R.id.tvItemPrice).text = price
        binding.layoutNonCoveredItems.addView(itemView)
    }

    private fun navigateBack() {
        if (isFromMap) {
            listener?.onBackFromHospitalDetail()
        } else {
            val parentFragment = parentFragment
            if (parentFragment is HospitalListFragment) {
                parentFragmentManager.popBackStack()
            } else if (parentFragment is FavoriteFragment) {
                listener?.onBackFromHospitalDetail()
                parentFragmentManager.popBackStack()
            } else {
                if (categoryId != -1) {
                    val hospitalListFragment = HospitalListFragment.newInstance(categoryId)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, hospitalListFragment)
                        .commit()
                } else {
                    // categoryId가 없는 경우 처리
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
        private const val ARG_HOSPITAL_NAME = "hospitalName"
        private const val ARG_HOSPITAL_ADDRESS = "hospitalAddress"
        private const val ARG_HOSPITAL_DEPARTMENT = "hospitalDepartment"
        private const val ARG_HOSPITAL_TIME = "hospitalTime"
        private const val ARG_HOSPITAL_PHONE = "hospitalPhoneNumber"
        private const val ARG_IS_FROM_MAP = "isFromMap"
        private const val ARG_CATEGORY_ID = "category_id"


        fun newInstance(
            hospitalName: String,
            hospitalAddress: String,
            hospitalDepartment: String,
            hospitalTime: String,
            hospitalPhoneNumber: String,
            isFromMap: Boolean,
            categoryId: Int
        ) = HospitalDetailFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_HOSPITAL_NAME, hospitalName)
                putString(ARG_HOSPITAL_ADDRESS, hospitalAddress)
                putString(ARG_HOSPITAL_DEPARTMENT, hospitalDepartment)
                putString(ARG_HOSPITAL_TIME, hospitalTime)
                putString(ARG_HOSPITAL_PHONE, hospitalPhoneNumber)
                putBoolean(ARG_IS_FROM_MAP, isFromMap)
                putInt(ARG_CATEGORY_ID, categoryId)
            }
        }
    }
}