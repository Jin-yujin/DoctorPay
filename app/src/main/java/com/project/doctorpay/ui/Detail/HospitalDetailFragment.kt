package com.project.doctorpay.ui.Detail

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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentHospitalDetailBinding
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.ui.favorite.FavoriteFragment
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.calendar.Appointment
import com.project.doctorpay.ui.hospitalList.HospitalListFragment
import com.project.doctorpay.ui.reviews.Review
import com.project.doctorpay.ui.reviews.ReviewFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HospitalDetailFragment : Fragment() {

    private var _binding: FragmentHospitalDetailBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var hospital: HospitalInfo
    private var isFromMap: Boolean = false
    private var category: String = ""


    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }

    private var listener: HospitalDetailListener? = null

    private var shouldShowToolbar = true
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
            isFromMap = it.getBoolean(ARG_IS_FROM_MAP, false)
            category = it.getString(ARG_CATEGORY, "")
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

            // 운영 시간 정보 표시
            hospital.timeInfo?.let { timeInfo ->
                tvHospitalHoliday.text = when {
                    timeInfo.isClosed -> "휴진"
                    else -> "영업중"
                }

                tvNightCare.text = when {
                    timeInfo.isEmergencyNight -> "야간진료: 가능"
                    else -> "야간진료: 불가"
                }
            } ?: run {
                tvHospitalHoliday.text = "운영시간 정보 없음"
                tvNightCare.text = "야간진료 정보 없음"
            }

            // 진료과목 표시
            val departmentsText = hospital.departments.joinToString(", ")
            tvHospitalDepartment.text = if (departmentsText.isNotEmpty()) {
                departmentsText
            } else {
                "진료과목 정보 없음"
            }

            // 리뷰 정보 로드
            loadReviewPreviews()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val nonPaymentItems = viewModel.fetchNonPaymentDetails(
                    viewId = HospitalViewModel.DETAIL_VIEW,
                    ykiho = hospital.ykiho
                )
                loadNonCoveredItems()
            } catch (e: Exception) {
                Log.e("HospitalDetailFragment", "Error fetching non-payment details", e)
                Toast.makeText(context, "비급여 항목 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        setupObservers()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getError(HospitalViewModel.DETAIL_VIEW).collect { error ->
                error?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            btnMoreNonCoveredItems.setOnClickListener { showAllNonCoveredItems() }

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
                userId = auth.currentUser?.uid ?: "",
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH),
                day = calendar.get(Calendar.DAY_OF_MONTH),
                time = appointmentTime,
                hospitalName = hospital.name,
                notes = notes,
                timestamp = Date()
            )

            // MainActivity를 통해 CalendarFragment로 일정 정보 전달하는 방식 변경
            // Firestore에 직접 저장
            db.collection("appointments")
                .add(appointment.toMap())
                .addOnSuccessListener {
                    Toast.makeText(context, "일정이 추가되었습니다", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Log.e("HospitalDetailFragment", "Error adding appointment", e)
                    Toast.makeText(context, "일정 추가 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                }
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

    private fun loadNonCoveredItems() {
        if (!isViewCreated || !isAdded || _binding == null) return

        val ykiho = hospital.ykiho
        if (ykiho.isBlank()) {
            Log.e("NonPaymentAPI", "ykiho is blank")
            binding.layoutNonCoveredItems.removeAllViews()
            addEmptyNonCoveredView()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("NonPaymentAPI", "Fetching non-payment items for ykiho: $ykiho")

                val nonPaymentItems = viewModel.fetchNonPaymentDetails(
                    viewId = HospitalViewModel.DETAIL_VIEW,
                    ykiho = ykiho
                )

                Log.d("NonPaymentAPI", "Fetched ${nonPaymentItems.size} items")

                if (nonPaymentItems.isEmpty()) {
                    Log.d("NonPaymentAPI", "No items found")
                    binding.layoutNonCoveredItems.removeAllViews()
                    addEmptyNonCoveredView()
                    return@launch
                }

                binding.layoutNonCoveredItems.removeAllViews()
                nonPaymentItems.take(3).forEachIndexed { index, item ->
                    Log.d("NonPaymentAPI", "Adding item $index: ${item.npayKorNm}")
                    addNonCoveredItemPreview(item)
                }

                binding.btnMoreNonCoveredItems.isVisible = nonPaymentItems.size > 3

            } catch (e: Exception) {
                Log.e("NonPaymentAPI", "Error loading non-covered items", e)
                binding.layoutNonCoveredItems.removeAllViews()
                addEmptyNonCoveredView()
            }
        }
    }

    private fun addNonCoveredItemPreview(item: NonPaymentItem) {
        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_non_covered_full, binding.layoutNonCoveredItems, false)

        itemView.apply {
            findViewById<TextView>(R.id.tvItemName).apply {
                text = item.npayKorNm ?: "항목명 없음"
                Log.d("NonPaymentAPI", "Setting item name: ${item.npayKorNm}")
            }

            findViewById<TextView>(R.id.tvItemPrice).apply {
                text = if (!item.curAmt.isNullOrEmpty()) {
                    "${item.curAmt}원"
                } else {
                    "가격 정보 없음"
                }
                Log.d("NonPaymentAPI", "Setting item price: ${item.curAmt}")
            }

            findViewById<TextView>(R.id.tvItemCode).apply {
                text = "코드: ${item.itemCd ?: "없음"}"
                isVisible = !item.itemCd.isNullOrEmpty()
                Log.d("NonPaymentAPI", "Setting item code: ${item.itemCd}")
            }

            findViewById<TextView>(R.id.tvSpecialNote).apply {
                text = item.spcmfyCatn
                isVisible = !item.spcmfyCatn.isNullOrEmpty()
                Log.d("NonPaymentAPI", "Setting special note: ${item.spcmfyCatn}")
            }
        }

        binding.layoutNonCoveredItems.addView(itemView)
    }

    private fun addEmptyNonCoveredView() {
        val emptyView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_non_covered_full, binding.layoutNonCoveredItems, false)

        emptyView.apply {
            findViewById<TextView>(R.id.tvItemName).text = "비급여 항목이 없습니다"
            findViewById<TextView>(R.id.tvItemPrice).visibility = View.GONE
            findViewById<TextView>(R.id.tvItemCode).visibility = View.GONE
            findViewById<TextView>(R.id.tvSpecialNote).visibility = View.GONE
        }

        binding.layoutNonCoveredItems.addView(emptyView)
    }

    // 비급여 상세 목록으로 이동
    private fun showAllNonCoveredItems() {
        val nonCoveredFragment = NonCoveredItemsFragment.newInstance(
            hospitalId = hospital.ykiho,
            hospitalName = hospital.name
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, nonCoveredFragment)
            .addToBackStack(null)
            .commit()
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
                putStringArrayList("departments", ArrayList(hospital.departments))
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

    fun setHospitalInfo(hospital: HospitalInfo) {
        this.hospital = hospital
    }


    companion object {
        const val ARG_HOSPITAL_ID = "hospital_id"
        const val ARG_IS_FROM_MAP = "is_from_map"
        const val ARG_CATEGORY = "category"

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