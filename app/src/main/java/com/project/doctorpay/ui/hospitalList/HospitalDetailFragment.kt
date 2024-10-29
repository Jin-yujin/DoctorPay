package com.project.doctorpay.ui.hospitalList

import NonPaymentItem
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentHospitalDetailBinding
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.ui.favorite.FavoriteFragment
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.calendar.Appointment
import com.project.doctorpay.ui.reviews.Review
import com.project.doctorpay.ui.reviews.ReviewFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HospitalDetailFragment : Fragment() {

    private var _binding: FragmentHospitalDetailBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. Access only between onCreateView and onDestroyView")

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }

    private var isFromMap: Boolean = false
    private var category: String = ""
    private lateinit var hospital: HospitalInfo

    private var shouldShowToolbar = true
    private var listener: HospitalDetailListener? = null

    private var isViewCreated = false

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
        isViewCreated = true
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

            // 진료과목 표시 방식 변경
            val departmentsText = hospital.departments.joinToString(", ")
            tvHospitalDepartment.text = if (departmentsText.isNotEmpty()) {
                departmentsText
            } else {
                "진료과목 정보 없음"
            }

            loadReviewPreviews()

//
//            // 진료과목 카테고리 표시 (선택적)
//            val categoriesText = hospital.departmentCategories.joinToString(", ")
//            tvHospitalCategories.text = if (categoriesText.isNotEmpty()) {
//                "카테고리: $categoriesText"
//            } else {
//                "카테고리 정보 없음"
//            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val nonPaymentItems = viewModel.fetchNonPaymentDetails(hospital.ykiho)
            loadNonCoveredItems(nonPaymentItems)
            if (nonPaymentItems.isEmpty()) {
                Toast.makeText(context, "No non-payment items found", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Non-payment items loaded", Toast.LENGTH_SHORT).show()
            }
        }

        loadNonCoveredItems(hospital.nonPaymentItems)
    }

    // 사용자 정보 로드 및 중복 체크 처리
    private fun loadUserInfo(review: Review, binding: FragmentHospitalDetailBinding) {
        if (!isViewCreated || !isAdded || _binding == null) return

        try {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(review.userId)
                .get()
                .addOnSuccessListener { userDoc ->
                    // 중복 체크를 위한 태그 사용(이미 해당 리뷰가 표시되어 있는지 확인)
                    val existingReview = binding.layoutReviews.findViewWithTag<View>(review.id)
                    if (existingReview != null) return@addOnSuccessListener

                    handleFirebaseCallback { binding ->
                        val nickname = userDoc.getString("nickname") ?: "익명"
                        // 리뷰 ID를 태그로 사용하여 중복 방지
                        addReviewPreview(binding, nickname, review.content, review.rating, review.id)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HospitalDetailFragment", "Error getting user info", e)
                    handleFirebaseCallback { binding ->
                        addReviewPreview(binding, "익명", review.content, review.rating, review.id)
                    }
                }
        } catch (e: Exception) {
            Log.e("HospitalDetailFragment", "Error in loadUserInfo", e)
        }
    }

    // 안전한 UI 업데이트를 위한 유틸리티 메서드
    private fun handleFirebaseCallback(action: (FragmentHospitalDetailBinding) -> Unit) {
        if (!isViewCreated || !isAdded || _binding == null) return

        try {
            // UI 업데이트는 메인 스레드에서 실행
            activity?.runOnUiThread {
                if (!isViewCreated || !isAdded || _binding == null) return@runOnUiThread
                action(binding)
            }
        } catch (e: Exception) {
            Log.e("HospitalDetailFragment", "Error handling Firebase callback", e)
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            btnStart.setOnClickListener { openMapWithDirections("출발") }
            btnDestination.setOnClickListener { openMapWithDirections("도착") }
            btnSave.setOnClickListener { /* TODO: Implement save functionality */ }
            btnCall.setOnClickListener { dialPhoneNumber(hospital.phoneNumber) }
            btnShare.setOnClickListener { shareHospitalInfo() }
            tvHospitalPhone.setOnClickListener { dialPhoneNumber(hospital.phoneNumber) }
            btnAppointment.setOnClickListener { showAppointmentDialog() }
            btnMoreReviews.setOnClickListener { navigateToReviewsFragment() }
            btnMoreNonCoveredItems.setOnClickListener { /* TODO: Implement non-covered items list functionality */ }
            btnBack.setOnClickListener { navigateBack() }
        }
    }

    private fun showAppointmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_appointment, null)
        val hospitalNameTextView = dialogView.findViewById<TextView>(R.id.hospitalNameTextView)
        val hospitalNameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.hospitalNameInputLayout)
        val dateEditText = dialogView.findViewById<EditText>(R.id.dateEditText)
        val timeEditText = dialogView.findViewById<EditText>(R.id.timeEditText)
        val notesEditText = dialogView.findViewById<EditText>(R.id.notesEditText)

        hospitalNameTextView.text = hospital.name
        hospitalNameTextView.visibility = View.VISIBLE
        hospitalNameInputLayout.visibility = View.GONE

        val calendar = Calendar.getInstance()

        dateEditText.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, day ->
                calendar.set(year, month, day)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                dateEditText.setText(dateFormat.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        timeEditText.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timeEditText.setText(timeFormat.format(calendar.time))
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.AppointmentDialogTheme)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.addButton).setOnClickListener {
            val appointmentDate = dateEditText.text.toString()
            val appointmentTime = timeEditText.text.toString()
            val notes = notesEditText.text.toString()

            if (appointmentDate.isBlank() || appointmentTime.isBlank()) {
                Toast.makeText(context, "날짜와 시간을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val appointment = Appointment(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
                appointmentTime,
                hospital.name,
                notes
            )

            // MainActivity를 통해 CalendarFragment로 일정 정보 전달
            (activity as? MainActivity)?.addAppointmentToCalendar(appointment)
            Toast.makeText(context, "일정이 추가되었습니다", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
        val encodedName = Uri.encode(hospital.name)

        val uri = when (mode) {
            "출발" -> Uri.parse("nmap://route/public?slat=&slng=&sname=현재위치&dlat=${hospital.latitude}&dlng=${hospital.longitude}&dname=$encodedName&appname=${context?.packageName}")
            "도착" -> Uri.parse("nmap://route/public?dlat=${hospital.latitude}&dlng=${hospital.longitude}&dname=$encodedName&appname=${context?.packageName}")
            else -> Uri.parse("nmap://search?query=$encodedAddress&appname=${context?.packageName}")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val packageManager = context?.packageManager
        val naverMapPackageName = "com.nhn.android.nmap"

        if (packageManager?.getLaunchIntentForPackage(naverMapPackageName) != null) {
            intent.setPackage(naverMapPackageName)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            val naverMapWebUri = Uri.parse("https://map.naver.com/v5/search/${encodedAddress}")
            startActivity(Intent(Intent.ACTION_VIEW, naverMapWebUri))
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
        // Fragment가 유효한 상태인지 확인
        if (!isViewCreated || !isAdded || _binding == null) return

        val db = FirebaseFirestore.getInstance()
        try {
            db.collection("reviews")
                .whereEqualTo("hospitalId", hospital.ykiho)
                .get()
                .addOnSuccessListener { documents ->
                    // Firebase 콜백에서 UI 업데이트를 위한 안전한 처리
                    handleFirebaseCallback { binding ->
                        if (documents.isEmpty) {
                            // 리뷰가 없을 때의 처리
                            binding.layoutReviews.removeAllViews()
                            val emptyView = LayoutInflater.from(requireContext())
                                .inflate(R.layout.view_empty_review, binding.layoutReviews, false)
                            binding.layoutReviews.addView(emptyView)
                            binding.tvReviewRating.text = "0.0"
                            binding.ratingBar.rating = 0f
                            return@handleFirebaseCallback
                        }

                        // 리뷰가 있을 때의 처리
                        val reviews = documents.toObjects(Review::class.java)
                        val avgRating = reviews.map { it.rating }.average().toFloat()
                        binding.tvReviewRating.text = String.format("%.1f", avgRating)
                        binding.ratingBar.rating = avgRating

                        // 리뷰 미리보기 표시 (최신 2개만)
                        binding.layoutReviews.removeAllViews()
                        reviews.sortedByDescending { it.timestamp }
                            .take(2)
                            .forEach { review ->
                                loadUserInfo(review, binding)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HospitalDetailFragment", "Error loading review previews", e)
                }
        } catch (e: Exception) {
            Log.e("HospitalDetailFragment", "Error in loadReviewPreviews", e)
        }
    }

    private fun loadNonCoveredItems(items: List<NonPaymentItem>) {
        binding.layoutNonCoveredItems.removeAllViews()
        items.forEach { item ->
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_non_covered, binding.layoutNonCoveredItems, false)

            itemView.findViewById<TextView>(R.id.tvItemName).text = item.npayKorNm ?: "Unknown Item"
            itemView.findViewById<TextView>(R.id.tvItemPrice).text = "${item.curAmt}원"  // Use curAmt for price

            binding.layoutNonCoveredItems.addView(itemView)
        }
    }

    // 뷰 미리보기 추가 메서드
    private fun addReviewPreview(
        binding: FragmentHospitalDetailBinding,
        name: String,
        content: String,
        rating: Float,
        reviewId: String  // 리뷰 ID
    ) {
        if (!isViewCreated || !isAdded) return

        try {
            val reviewView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_review_preview, binding.layoutReviews, false)

            // 리뷰 뷰에 ID를 태그로 설정하여 중복 체크 가능하게 함
            reviewView.tag = reviewId

            reviewView.apply {
                findViewById<TextView>(R.id.tvReviewerName).text = name
                findViewById<TextView>(R.id.tvReviewContent).text = content
                findViewById<RatingBar>(R.id.rbReviewRating).rating = rating
                findViewById<TextView>(R.id.tvReviewDate).text = SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).format(Date())
            }

            binding.layoutReviews.addView(reviewView)
        } catch (e: Exception) {
            Log.e("HospitalDetailFragment", "Error in addReviewPreview", e)
        }
    }

    private fun addNonCoveredItem(itemName: String, itemCd: String) {
        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_non_covered, binding.layoutNonCoveredItems, false)
        itemView.findViewById<TextView>(R.id.tvItemName).text = itemName
        itemView.findViewById<TextView>(R.id.tvItemPrice).text = itemCd // Changed 'price' to 'itemCd'
        binding.layoutNonCoveredItems.addView(itemView)
    }

    private fun navigateToReviewsFragment() {
        // ReviewFragment로 전환하면서 hospitalId 전달
        val reviewFragment = ReviewFragment().apply {
            arguments = Bundle().apply {
                putString("hospitalId", hospital.ykiho)
                putString("hospitalName", hospital.name)
            }
        }

        // Fragment 전환
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, reviewFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun navigateBack() {
        when {
            isFromMap -> {
                listener?.onBackFromHospitalDetail()
            }
            parentFragment is HospitalListFragment -> {
                parentFragmentManager.popBackStack()
            }
            parentFragment is FavoriteFragment -> {
                listener?.onBackFromHospitalDetail()
                parentFragmentManager.popBackStack()
            }
            else -> {
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewCreated = false
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