package com.project.doctorpay.ui.Detail

import NonPaymentItem
import RecentHospitalRepository
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.PorterDuff
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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
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
import com.project.doctorpay.db.FavoriteRepository
import com.project.doctorpay.db.OperationState
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.calendar.Appointment
import com.project.doctorpay.ui.hospitalList.HospitalListFragment
import com.project.doctorpay.ui.reviews.Review
import com.project.doctorpay.ui.reviews.ReviewFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HospitalDetailFragment : Fragment() {
    private var _binding: FragmentHospitalDetailBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val favoriteRepository = FavoriteRepository.getInstance()
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHospitalDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 최근 본 병원에 추가
        val recentHospitalRepository = RecentHospitalRepository(requireContext())
        recentHospitalRepository.addRecentHospital(hospital)

        isViewCreated = true
        updateUI(hospital)  // 기존 hospital 정보로 UI 업데이트
        setupClickListeners()
        setupBackPressHandler()
        loadNonCoveredItems() // 비급여 정보만 로드
        setupFavoriteButton()
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
                val currentState = timeInfo.getCurrentState()
                val currentDayOfWeek = java.time.LocalDate.now().dayOfWeek

                val operationHoursText = buildString {
                    append(when (currentState) {
                        OperationState.OPEN -> "영업중\n"
                        OperationState.LUNCH_BREAK -> {
                            timeInfo.lunchTime?.let { lunch ->
                                "점심시간 (${formatTime(lunch.start)} - ${formatTime(lunch.end)})\n"
                            } ?: "점심시간\n"
                        }

                        OperationState.CLOSED -> "영업종료\n"
                        OperationState.EMERGENCY -> {
                            when {
                                timeInfo.isEmergencyDay && timeInfo.isEmergencyNight -> "24시간 응급실 운영\n"
                                timeInfo.isEmergencyDay -> "주간 응급실 운영\n"
                                timeInfo.isEmergencyNight -> "야간 응급실 운영\n"
                                else -> ""
                            }
                        }

                        OperationState.UNKNOWN -> "운영시간 정보 없음"
                    })

                    if (currentState != OperationState.UNKNOWN) {
                        append("■ 평일: ")
                        timeInfo.weekdayTime?.let { time ->
                            append("${formatTime(time.start)} - ${formatTime(time.end)}")
                            timeInfo.lunchTime?.let { lunch ->
                                append(" (점심시간 ${formatTime(lunch.start)} - ${formatTime(lunch.end)})")
                            }
                        } ?: append("시간정보 없음")
                        append("\n")

                        append("■ 토요일: ")
                        timeInfo.saturdayTime?.let { time ->
                            append("${formatTime(time.start)} - ${formatTime(time.end)}")
                            timeInfo.saturdayLunchTime?.let { lunch ->
                                append(" (점심시간 ${formatTime(lunch.start)} - ${formatTime(lunch.end)})")
                            }
                        } ?: append("휴진")
                        append("\n")

                        append("■ 일요일: ")
                        timeInfo.sundayTime?.let { time ->
                            append("${formatTime(time.start)} - ${formatTime(time.end)}")
                        } ?: append("휴진")
                        append("\n")

                        if (timeInfo.isEmergencyDay || timeInfo.isEmergencyNight) {
                            append("■ 응급실: ")
                            when {
                                timeInfo.isEmergencyDay && timeInfo.isEmergencyNight -> append("24시간 운영")
                                timeInfo.isEmergencyDay -> append("주간 운영")
                                timeInfo.isEmergencyNight -> append("야간 운영")
                            }
                            timeInfo.emergencyDayContact?.let { contact ->
                                append(" (연락처: $contact)")
                            }
                        }
                    }
                }

                tvHospitalHoliday.text = operationHoursText

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

    // 시간 포맷팅을 위한 도우미 함수
    private fun formatTime(time: java.time.LocalTime?): String {
        return time?.let {
            String.format("%02d:%02d", it.hour, it.minute)
        } ?: "-"
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

    private fun setupFavoriteButton() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val isFavorite = favoriteRepository.isFavorite(hospital.ykiho)
                updateFavoriteButtonState(isFavorite)
            } catch (e: Exception) {
                Log.e("HospitalDetailFragment", "Error checking favorite status", e)
            }
        }
    }

    private fun updateFavoriteButtonState(isFavorite: Boolean) {
        val drawable = if (isFavorite) {
            android.R.drawable.btn_star_big_on
        } else {
            android.R.drawable.btn_star_big_off
        }

        binding.btnSave.setImageResource(drawable)

        // Only apply color filter if favorite
        if (isFavorite) {
            binding.btnSave.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.favoriteColor),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.btnSave.clearColorFilter()
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
                        addReviewPreview(
                            binding,
                            nickname,
                            review.content,
                            review.rating,
                            review.id,
                            review.timestamp
                        )
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HospitalDetailFragment", "Error getting user info", e)
                    handleFirebaseCallback { binding ->
                        addReviewPreview(
                            binding,
                            "익명",
                            review.content,
                            review.rating,
                            review.id,
                            review.timestamp
                        )
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
            btnStart.setOnClickListener { openMapWithDirections() }
            btnSave.setOnClickListener { toggleFavorite() }
            btnCall.setOnClickListener { dialPhoneNumber(hospital.phoneNumber) }
            btnShare.setOnClickListener { shareHospitalInfo() }
            tvHospitalPhone.setOnClickListener { dialPhoneNumber(hospital.phoneNumber) }
            btnAppointment.setOnClickListener { showAppointmentDialog() }
            btnMoreReviews.setOnClickListener { navigateToReviewsFragment() }
            btnBack.setOnClickListener { navigateBack() }

            // 비급여 항목 더보기 버튼 클릭 리스너 통합
            binding.btnMoreNonCoveredItems.setOnClickListener {
                showAllNonCoveredItems()
            }
        }
    }

    // HospitalDetailFragment에 추가
    fun showMainButtons() {
        binding.apply {
            btnSave.visibility = View.VISIBLE
            btnShare.visibility = View.VISIBLE
            btnAppointment.visibility = View.VISIBLE
        }
    }

    fun hideMainButtons() {
        binding.apply {
            btnSave.visibility = View.GONE
            btnShare.visibility = View.GONE
            btnAppointment.visibility = View.GONE
        }
    }

    private fun toggleFavorite() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentState = favoriteRepository.isFavorite(hospital.ykiho)
                if (currentState) {
                    favoriteRepository.removeFavorite(hospital.ykiho)
                    showToast("즐겨찾기가 해제되었습니다")
                } else {
                    favoriteRepository.addFavorite(hospital)
                    showToast("즐겨찾기에 추가되었습니다")
                }
                updateFavoriteButtonState(!currentState)
            } catch (e: Exception) {
                when (e) {
                    is IllegalStateException -> showToast("로그인이 필요합니다")
                    else -> {
                        Log.e("HospitalDetailFragment", "Error toggling favorite", e)
                        showToast("오류가 발생했습니다")
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun showAppointmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_appointment, null)
        val hospitalNameTextView = dialogView.findViewById<TextView>(R.id.hospitalNameTextView)
        val hospitalNameInputLayout =
            dialogView.findViewById<TextInputLayout>(R.id.hospitalNameInputLayout)
        val dateEditText = dialogView.findViewById<EditText>(R.id.dateEditText)
        val timeEditText = dialogView.findViewById<EditText>(R.id.timeEditText)
        val notesEditText = dialogView.findViewById<EditText>(R.id.notesEditText)

        hospitalNameTextView.text = hospital.name
        hospitalNameTextView.visibility = View.VISIBLE
        hospitalNameInputLayout.visibility = View.GONE

        val calendar = Calendar.getInstance()

        dateEditText.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    dateEditText.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
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

    private fun openMapWithDirections() {
        val encodedAddress = Uri.encode(hospital.address)
        val encodedName = Uri.encode(hospital.name)

        val uri =Uri.parse("nmap://route/public?slat=&slng=&sname=현재위치&dlat=${hospital.latitude}&dlng=${hospital.longitude}&dname=$encodedName&appname=${context?.packageName}")

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
            putExtra(
                Intent.EXTRA_TEXT,
                "${hospital.name}\n${hospital.address}\n${hospital.phoneNumber}"
            )
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

    // HospitalDetailFragment에서
    private fun loadNonCoveredItems() {
        if (!isViewCreated || !isAdded || _binding == null) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // withContext를 사용하여 IO 작업 수행
                val items = withContext(Dispatchers.IO) {
                    viewModel.fetchNonPaymentItemsOnly(hospital.ykiho)
                }

                // UI 업데이트는 Main 스레드에서
                withContext(Dispatchers.Main) {
                    if (items.isEmpty()) {
                        addEmptyNonCoveredView("비급여 항목이 없습니다")
                        binding.btnMoreNonCoveredItems?.isVisible = false
                    } else {
                        binding.layoutNonCoveredItems.removeAllViews()
                        items.take(3).forEach { item ->
                            addNonCoveredItemPreview(item)
                        }
                        binding.btnMoreNonCoveredItems?.isVisible = items.size > 3
                    }
                }
            } catch (e: Exception) {
                Log.e("NonPaymentAPI", "Error loading non-covered items", e)
                withContext(Dispatchers.Main) {
                    addEmptyNonCoveredView("데이터를 불러오는데 실패했습니다")
                    binding.btnMoreNonCoveredItems?.isVisible = false
                }
            }
        }
    }


    private fun addNonCoveredItemPreview(item: NonPaymentItem) {
        try {
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_non_covered_preview, binding.layoutNonCoveredItems, false)

            // null 체크를 포함한 안전한 findViewById 사용
            itemView?.let { view ->
                // 항목명
                view.findViewById<TextView>(R.id.tvItemName)?.apply {
                    text = item.npayKorNm ?: "항목명 없음"
                    visibility = View.VISIBLE
                }

                // 가격
                view.findViewById<TextView>(R.id.tvItemPrice)?.apply {
                    text = item.curAmt?.let { amt ->
                        try {
                            String.format("%,d원", amt.toInt())
                        } catch (e: NumberFormatException) {
                            "금액 정보 없음"
                        }
                    } ?: "금액 정보 없음"
                    visibility = View.VISIBLE
                }

                // 코드
                view.findViewById<TextView>(R.id.tvItemCode)?.apply {
                    text = "코드: ${item.itemCd ?: "없음"}"
                    visibility = if (item.itemCd.isNullOrEmpty()) View.GONE else View.VISIBLE
                }

                // 특이사항
                view.findViewById<TextView>(R.id.tvSpecialNote)?.apply {
                    text = item.spcmfyCatn
                    visibility = if (item.spcmfyCatn.isNullOrEmpty()) View.GONE else View.VISIBLE
                }

                // 날짜 정보
                view.findViewById<TextView>(R.id.tvDate)?.apply {
                    text = item.adtFrDd?.let { date ->
                        try {
                            "기준일자: ${date.substring(0, 4)}.${date.substring(4, 6)}.${
                                date.substring(
                                    6,
                                    8
                                )
                            }"
                        } catch (e: Exception) {
                            "기준일자: $date"
                        }
                    } ?: "날짜 정보 없음"
                    visibility = View.VISIBLE
                }

                binding.layoutNonCoveredItems.addView(view)
            }
        } catch (e: Exception) {
            Log.e("NonPaymentAPI", "Error adding non-covered item preview", e)
        }
    }

    private fun addEmptyNonCoveredView(message: String) {
        try {
            val emptyView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_non_covered_preview, binding.layoutNonCoveredItems, false)

            emptyView?.let { view ->
                // 메시지 표시
                view.findViewById<TextView>(R.id.tvItemName)?.apply {
                    text = message
                    visibility = View.VISIBLE
                }

                // 다른 뷰들은 숨김
                view.findViewById<TextView>(R.id.tvItemPrice)?.visibility = View.GONE
                view.findViewById<TextView>(R.id.tvItemCode)?.visibility = View.GONE
                view.findViewById<TextView>(R.id.tvSpecialNote)?.visibility = View.GONE
                view.findViewById<TextView>(R.id.tvDate)?.visibility = View.GONE

                binding.layoutNonCoveredItems.addView(view)
            }
        } catch (e: Exception) {
            Log.e("NonPaymentAPI", "Error adding empty view", e)
        }
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
        hideContent()  // 기존 내용 숨기기

        // toolbar도 숨기기
        binding.appBarLayout.visibility = View.GONE

        val nonCoveredFragment = NonCoveredItemsFragment.newInstance(
            hospitalId = hospital.ykiho,
            hospitalName = hospital.name
        )

        childFragmentManager.beginTransaction()
            .replace(R.id.hospitalDetailContainer, nonCoveredFragment)
            .addToBackStack(null)
            .commit()
    }

    // 뷰 미리보기
    private fun addReviewPreview(
        binding: FragmentHospitalDetailBinding,
        name: String,
        content: String,
        rating: Float,
        reviewId: String,
        timestamp: Long
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
                ).format(Date(timestamp))
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
        itemView.findViewById<TextView>(R.id.tvItemPrice).text =
            itemCd // Changed 'price' to 'itemCd'
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



    interface ContentVisibilityListener {
        fun showContent()
        fun hideContent()
    }

    fun showContent() {
        binding.nestedScrollView.visibility = View.VISIBLE
        binding.hospitalDetailContainer.visibility = View.GONE
        binding.appBarLayout.visibility = View.VISIBLE  // toolbar 다시 표시
        showMainButtons()
    }

    fun hideContent() {
        binding.nestedScrollView.visibility = View.GONE
        binding.hospitalDetailContainer.visibility = View.VISIBLE
        hideMainButtons()
    }
}