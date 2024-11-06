package com.project.doctorpay.api

import DgsbjtInfoItem
import DgsbjtInfoResponse
import HospitalDetailItem
import HospitalInfoItem
import HospitalInfoResponse
import NonPaymentItem
import NonPaymentResponse
import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.db.DepartmentCategory
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.db.HospitalTimeInfo
import com.project.doctorpay.db.TimeRange
import com.project.doctorpay.db.inferDepartments
import com.project.doctorpay.network.NetworkModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.simpleframework.xml.core.ElementException
import retrofit2.HttpException
import retrofit2.Response
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.pow

class HospitalViewModel(
    private val healthInsuranceApi: HealthInsuranceApi
) : ViewModel() {

    // 상태 관리를 위한 StateFlow
    private val _hospitals = MutableStateFlow<List<HospitalInfo>>(emptyList())
    val hospitals: StateFlow<List<HospitalInfo>> = _hospitals

    private val _filteredHospitals = MutableStateFlow<List<HospitalInfo>>(emptyList())
    val filteredHospitals: StateFlow<List<HospitalInfo>> = _filteredHospitals

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // 페이징 관련 변수
    private var currentPage = 1
    private val pageSize = 50
    private var isLastPage = false

    companion object {
        const val DEFAULT_RADIUS = 5000  // 5km
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY = 1000L
        private const val REQUEST_TIMEOUT = 30000L // 30초

        // 캐시 관련 상수
        private var cachedHospitals: List<HospitalInfo>? = null
        private var lastFetchTime: Long = 0
        private const val CACHE_DURATION = 30 * 60 * 1000 // 30분
    }

    // 마지막으로 요청한 위치 저장
    private var lastLocation: Pair<Double, Double>? = null

    private fun formatCoordinate(value: Double): String {
        return String.format("%.8f", value)
    }

    private suspend fun <T> retryWithExponentialBackoff(
        times: Int = MAX_RETRIES,
        initialDelay: Long = BASE_DELAY,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null

        repeat(times) { attempt ->
            try {
                return withTimeout(REQUEST_TIMEOUT) {
                    block()
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt == times - 1) throw e

                val backoffDelay = initialDelay * 2.0.pow(attempt.toDouble()).toLong()
                    .coerceAtMost(10000) // 최대 10초
                Log.d("RetryOperation", "Retrying operation after error: ${e.message}, attempt ${attempt + 1} of $times after ${backoffDelay}ms delay")
                delay(backoffDelay)
            }
        }

        throw lastException ?: IOException("Failed after $times attempts")
    }

    fun fetchNearbyHospitals(latitude: Double, longitude: Double, radius: Int = DEFAULT_RADIUS, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // 캐시 확인
            if (!forceRefresh &&
                cachedHospitals != null &&
                (System.currentTimeMillis() - lastFetchTime) < CACHE_DURATION &&
                lastLocation?.first == latitude &&
                lastLocation?.second == longitude
            ) {
                Log.d("CacheDebug", """
                캐시 사용됨:
                - 캐시된 병원 수: ${cachedHospitals?.size}
                - 마지막 업데이트: ${formatTimeAgo(lastFetchTime)}
                - 캐시된 위치: ${lastLocation?.first}, ${lastLocation?.second}
                - 요청 위치: $latitude, $longitude
            """.trimIndent())

                _hospitals.value = cachedHospitals!!
                filterHospitalsWithin5km(latitude, longitude, cachedHospitals!!)
                return@launch
            }

            _isLoading.value = true
            _error.value = null

            try {
                Log.d("HospitalViewModel", "Fetching hospitals with lat=$latitude, lon=$longitude, radius=$radius")

                val response = retryWithExponentialBackoff {
                    healthInsuranceApi.getHospitalInfo(
                        serviceKey = NetworkModule.getServiceKey(),
                        pageNo = currentPage,
                        numOfRows = pageSize,
                        xPos = formatCoordinate(longitude),
                        yPos = formatCoordinate(latitude),
                        radius = radius
                    )
                }

                Log.d("HospitalViewModel", "API Response Code: ${response.code()}")

                when {
                    response.isSuccessful -> {
                        val body = response.body()
                        Log.d("HospitalViewModel", "Response Body: $body")

                        if (body == null) {
                            _error.value = "서버 응답이 비어있습니다"
                            return@launch
                        }

                        val newHospitals = body.body?.items?.itemList ?: emptyList()
                        Log.d("HospitalViewModel", "Received ${newHospitals.size} hospitals from API")

                        // NonPayment 정보 가져오기
                        val nonPaymentResponse = retryWithExponentialBackoff { fetchNonPaymentInfo() }
                        val nonPaymentItems = if (nonPaymentResponse.isSuccessful) {
                            nonPaymentResponse.body()?.body?.items ?: emptyList()
                        } else {
                            emptyList()
                        }

                        val combinedHospitals = combineHospitalData(newHospitals, nonPaymentItems)
                        Log.d("HospitalViewModel", "Combined hospitals: ${combinedHospitals.size}")

                        // 병렬로 진료과목 정보 가져오기
                        val updatedHospitals = withContext(Dispatchers.IO) {
                            combinedHospitals.map { hospital ->
                                async {
                                    try {
                                        val dgsbjtResponse = retryWithExponentialBackoff {
                                            fetchDgsbjtInfo(hospital.ykiho)
                                        }
                                        if (dgsbjtResponse.isSuccessful) {
                                            updateHospitalWithDgsbjtInfo(hospital, dgsbjtResponse.body()?.body?.items?.itemList)
                                        } else {
                                            hospital
                                        }
                                    } catch (e: Exception) {
                                        Log.e("HospitalViewModel", "Error fetching dgsbjt info for ${hospital.name}", e)
                                        hospital
                                    }
                                }
                            }.awaitAll()
                        }

                        // 캐시 업데이트
                        cachedHospitals = updatedHospitals
                        lastFetchTime = System.currentTimeMillis()
                        lastLocation = Pair(latitude, longitude)

                        if (currentPage == 1) {
                            _hospitals.value = updatedHospitals
                        } else {
                            _hospitals.value = _hospitals.value + updatedHospitals
                        }

                        // 5km 이내 병원만 필터링
                        filterHospitalsWithin5km(latitude, longitude, _hospitals.value)

                        isLastPage = newHospitals.size < pageSize
                        currentPage++
                    }
                    response.code() == 429 -> {
                        Log.d("HospitalViewModel", "Rate limit exceeded, retrying after delay...")
                        delay(BASE_DELAY)
                        fetchNearbyHospitals(latitude, longitude, DEFAULT_RADIUS)
                    }
                    else -> {
                        val errorBody = response.errorBody()?.string()
                        _error.value = "서버 오류: ${response.code()} - ${response.message()}"
                        Log.e("HospitalViewModel", "API Error: $errorBody")
                    }
                }

            } catch (e: Exception) {
                Log.e("HospitalViewModel", "Error fetching hospitals", e)
                handleError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 시간 포맷팅 함수 추가
    private fun formatTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 1000 -> "방금 전"
            diff < 60 * 1000 -> "${diff / 1000}초 전"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}분 전"
            else -> "${diff / (60 * 60 * 1000)}시간 전"
        }
    }
    suspend fun fetchNonPaymentDetails(ykiho: String): List<NonPaymentItem> {
        return retryWithExponentialBackoff {
            val response = healthInsuranceApi.getNonPaymentItemHospDtlList(
                serviceKey = NetworkModule.getServiceKey(),
                ykiho = ykiho
            )
            if (response.isSuccessful) {
                response.body()?.body?.items ?: emptyList()
            } else emptyList()
        }
    }

    fun resetPagination() {
        currentPage = 1
        isLastPage = false
        _hospitals.value = emptyList()
    }

    private fun filterHospitalsWithin5km(latitude: Double, longitude: Double, hospitals: List<HospitalInfo>) {
        val filteredAndSortedList = hospitals
            .map { hospital ->
                // 각 병원의 거리 계산
                val distance = calculateDistance(
                    latitude, longitude,
                    hospital.latitude, hospital.longitude
                )
                Pair(hospital, distance)
            }
            .filter { (_, distance) ->
                distance <= 5000 // 5km 이내 필터링
            }
            .sortedBy { (_, distance) -> distance } // 거리순 정렬
            .map { (hospital, _) -> hospital }

        _filteredHospitals.value = filteredAndSortedList
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    suspend fun fetchHospitalInfo(sidoCd: String, sgguCd: String): Response<HospitalInfoResponse> {
        return retryWithExponentialBackoff {
            val response = healthInsuranceApi.getHospitalInfo(
                serviceKey = NetworkModule.getServiceKey(),
                pageNo = 1,
                numOfRows = pageSize,
                sidoCd = sidoCd,
                sgguCd = sgguCd,
                xPos = "0",
                yPos = "0",
                radius = 0
            )
            Log.d("API_RESPONSE", "Raw Hospital Info Response: ${response.body()}")
            response
        }
    }

    private suspend fun fetchNonPaymentInfo(): Response<NonPaymentResponse> {
        return retryWithExponentialBackoff {
            healthInsuranceApi.getNonPaymentInfo(
                serviceKey = NetworkModule.getServiceKey(),
                pageNo = 1,
                numOfRows = pageSize
            )
        }
    }

    private suspend fun fetchDgsbjtInfo(ykiho: String): Response<DgsbjtInfoResponse> {
        return retryWithExponentialBackoff {
            healthInsuranceApi.getDgsbjtInfo(
                serviceKey = NetworkModule.getServiceKey(),
                ykiho = ykiho.trim(),
                pageNo = 1,
                numOfRows = pageSize
            )
        }
    }



    private fun updateHospitalWithDgsbjtInfo(hospital: HospitalInfo, dgsbjtItems: List<DgsbjtInfoItem>?): HospitalInfo {
        val dgsbjtCodes = dgsbjtItems?.mapNotNull { it.dgsbjtCd } ?: emptyList()
        val updatedDepartments = inferDepartments(hospital.name, hospital.nonPaymentItems, dgsbjtCodes)
        val updatedDepartmentCategories = getDepartmentCategories(updatedDepartments)
        return hospital.copy(
            departments = updatedDepartments,
            departmentCategories = updatedDepartmentCategories
        )
    }

    fun filterHospitalsByCategory(hospitals: List<HospitalInfo>, category: DepartmentCategory?): List<HospitalInfo> {
        val filteredHospitals = if (category == null) {
            hospitals
        } else {
            hospitals.filter { hospital ->
                hospital.departmentCategories.contains(category.name)
            }
        }

        Log.d("HospitalViewModel", "Filtering for category: ${category?.name}, Results: ${filteredHospitals.size}")

        filteredHospitals.forEach { hospital ->
            Log.d("HospitalViewModel", "Filtered Hospital: ${hospital.name}, Categories: ${hospital.departmentCategories.joinToString()}")
        }

        return filteredHospitals
    }
    private suspend fun combineHospitalData(
        hospitalInfoItems: List<HospitalInfoItem>?,
        nonPaymentItems: List<NonPaymentItem>?
    ): List<HospitalInfo> {
        Log.d("HospitalViewModel", "Combining data - Hospitals: ${hospitalInfoItems?.size}, NonPayment: ${nonPaymentItems?.size}")

        val nonPaymentMap = nonPaymentItems?.groupBy { it.yadmNm } ?: emptyMap()

        return withContext(Dispatchers.IO) {
            hospitalInfoItems?.mapNotNull { hospitalInfo ->
                try {
                    // 1. 기본 유효성 검사
                    val nonPaymentItemsForHospital = nonPaymentMap[hospitalInfo.yadmNm] ?: emptyList()
                    val latitude = hospitalInfo.YPos?.toDoubleOrNull() ?: return@mapNotNull null
                    val longitude = hospitalInfo.XPos?.toDoubleOrNull() ?: return@mapNotNull null

                    // 좌표가 유효한 경우만 처리
                    if (latitude == 0.0 && longitude == 0.0) {
                        return@mapNotNull null
                    }

                    // 2. 진료과목 추론
                    val departments = inferDepartments(
                        hospitalInfo.yadmNm ?: "",
                        nonPaymentItemsForHospital,
                        hospitalInfo.dgsbjtCd?.split(",") ?: emptyList()
                    )

                    // 3. 진료과목 카테고리 설정
                    val departmentCategories = getDepartmentCategories(departments)

                    // 4. 운영시간 정보 가져오기
                    val timeInfo = withContext(Dispatchers.IO) {
                        try {
                            Log.d("TimeInfo", "Attempting to fetch time info for hospital: ${hospitalInfo.yadmNm}")
                            val ykiho = hospitalInfo.ykiho
                            if (ykiho == null) {
                                Log.d("TimeInfo", "ykiho is null for hospital: ${hospitalInfo.yadmNm}")
                                null
                            } else {
                                val response = retryWithExponentialBackoff {
                                    healthInsuranceApi.getDtlInfo(
                                        serviceKey = NetworkModule.getServiceKey(),
                                        ykiho = ykiho
                                    )
                                }

                                Log.d("TimeInfo", """
                                        Time Info API Response:
                                        - Hospital: ${hospitalInfo.yadmNm}
                                        - ykiho: $ykiho
                                        - Response code: ${response.code()}
                                        - Response body: ${response.body()}
                                        - Error body: ${response.errorBody()?.string()}
                                    """.trimIndent())

                                if (response.isSuccessful) {
                                    val detailItem = response.body()?.body?.items?.item
                                    convertToHospitalTimeInfo(detailItem)
                                } else {
                                    Log.e("TimeInfo", "API call failed with code: ${response.code()}")
                                    when (response.code()) {
                                        500 -> Log.e("TimeInfo", "Server error for ykiho: $ykiho")
                                        404 -> Log.e("TimeInfo", "API endpoint not found")
                                        else -> Log.e("TimeInfo", "Unknown error: ${response.errorBody()?.string()}")
                                    }
                                    getDefaultTimeInfo()  // 기본 운영시간 정보 반환
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TimeInfo", "Error fetching time info for ${hospitalInfo.ykiho}", e)
                            e.printStackTrace()
                            getDefaultTimeInfo()  // 에러 발생 시 기본 운영시간 정보 반환
                        }
                    }

                    // Debug logging for timeInfo
                    timeInfo?.let {
                        Log.d("TimeInfo", """
                        Time info for ${hospitalInfo.yadmNm}:
                        - Week time: ${it.weekdayTime}
                        - Saturday time: ${it.saturdayTime}
                        - Sunday time: ${it.sundayTime}
                        - Lunch time: ${it.lunchTime}
                        - Emergency: ${it.isEmergencyDay}, ${it.isEmergencyNight}
                    """.trimIndent())
                    } ?: Log.d("TimeInfo", "Using default time info for ${hospitalInfo.yadmNm}")

                    // 5. 운영 상태 결정
                    val operationState = when {
                        timeInfo == null -> OperationState.UNKNOWN
                        timeInfo.isClosed -> OperationState.CLOSED
                        timeInfo.isEmergencyDay || timeInfo.isEmergencyNight -> OperationState.EMERGENCY
                        else -> {
                            val nowTime = LocalTime.now()
                            val currentDay = LocalDate.now().dayOfWeek
                            when (currentDay) {
                                DayOfWeek.SUNDAY -> timeInfo.sundayTime
                                DayOfWeek.SATURDAY -> timeInfo.saturdayTime
                                else -> timeInfo.weekdayTime
                            }?.let { timeRange ->
                                if (timeRange.start == null || timeRange.end == null) {
                                    OperationState.UNKNOWN
                                } else if (nowTime.isAfter(timeRange.start) && nowTime.isBefore(timeRange.end)) {
                                    // 점심시간 체크
                                    val lunchTimeRange = if (currentDay == DayOfWeek.SATURDAY) {
                                        timeInfo.saturdayLunchTime
                                    } else {
                                        timeInfo.lunchTime
                                    }

                                    if (lunchTimeRange?.let { lunch ->
                                            lunch.start?.let { start ->
                                                lunch.end?.let { end ->
                                                    nowTime.isAfter(start) && nowTime.isBefore(end)
                                                }
                                            }
                                        } == true) {
                                        OperationState.LUNCH_BREAK
                                    } else {
                                        OperationState.OPEN
                                    }
                                } else {
                                    OperationState.CLOSED
                                }
                            } ?: OperationState.UNKNOWN
                        }
                    }

                    // 6. 상태 텍스트 설정
                    val stateText = when (operationState) {
                        OperationState.OPEN -> "영업중"
                        OperationState.CLOSED -> "영업마감"
                        OperationState.LUNCH_BREAK -> "점심시간"
                        OperationState.EMERGENCY -> "응급실 운영중"
                        OperationState.UNKNOWN -> "운영시간 정보없음"
                    }

                    // 7. HospitalInfo 객체 생성
                    HospitalInfo(
                        location = LatLng(latitude, longitude),
                        name = hospitalInfo.yadmNm ?: "",
                        address = hospitalInfo.addr ?: "",
                        departments = departments,
                        departmentCategories = departmentCategories,
                        phoneNumber = hospitalInfo.telno ?: "",
                        state = stateText,
                        rating = 0.0,
                        latitude = latitude,
                        longitude = longitude,
                        nonPaymentItems = nonPaymentItemsForHospital,
                        clCdNm = hospitalInfo.clCdNm ?: "",
                        ykiho = hospitalInfo.ykiho ?: "",
                        timeInfo = timeInfo
                    ).also {
                        Log.d("HospitalViewModel", "Created hospital: ${it.name} at (${it.latitude}, ${it.longitude}) with state: ${it.state}")
                    }

                } catch (e: Exception) {
                    Log.e("HospitalViewModel", "Error creating hospital from item: ${hospitalInfo.yadmNm}", e)
                    null
                }
            } ?: emptyList()
        }
    }

    private fun getDefaultTimeInfo(): HospitalTimeInfo {
        return HospitalTimeInfo(
            weekdayTime = TimeRange(
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)
            ),
            saturdayTime = TimeRange(
                LocalTime.of(9, 0),
                LocalTime.of(13, 0)
            ),
            sundayTime = null,
            lunchTime = TimeRange(
                LocalTime.of(12, 0),
                LocalTime.of(13, 0)
            ),
            saturdayLunchTime = null,
            isEmergencyDay = false,
            isEmergencyNight = false,
            emergencyDayContact = null,
            emergencyNightContact = null,
            isClosed = false
        )
    }

    // 병원 운영 상태를 나타내는 열거형
    enum class OperationState {
        OPEN,           // 영업 중
        CLOSED,         // 영업 종료
        LUNCH_BREAK,    // 점심시간
        EMERGENCY,      // 응급실 운영 중
        UNKNOWN         // 상태 알 수 없음
    }
    fun searchHospitals(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 캐시된 데이터가 있으면 그 중에서 검색
                val searchBase = cachedHospitals ?: _hospitals.value

                val filteredHospitals = searchBase.filter { hospital ->
                    hospital.name.contains(query, ignoreCase = true) ||
                            hospital.departments.any { it.contains(query, ignoreCase = true) } ||
                            hospital.address.contains(query, ignoreCase = true)
                }

                _hospitals.value = filteredHospitals

            } catch (e: Exception) {
                _error.value = "검색 중 오류가 발생했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 검색 초기화 (원래 데이터로 복구)
    fun resetSearch() {
        viewModelScope.launch {
            cachedHospitals?.let {
                _hospitals.value = it
            }
        }
    }

    private fun handleError(e: Exception) {
        Log.e("HospitalViewModel", "데이터 불러오기 오류", e)
        val errorMessage = when (e) {
            is TimeoutCancellationException -> "요청 시간이 초과되었습니다. 다시 시도해주세요."
            is SocketTimeoutException -> "서버 응답 시간이 초과되었습니다. 다시 시도해주세요."
            is UnknownHostException -> "인터넷 연결을 확인해주세요."
            is HttpException -> {
                when (e.code()) {
                    429 -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
                    in 500..599 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                    else -> "네트워크 오류: ${e.code()}"
                }
            }
            is ElementException -> "데이터 형식 오류: ${e.message}"
            is EOFException -> "서버 응답이 불완전합니다. 다시 시도해주세요."
            is IOException -> "네트워크 오류가 발생했습니다: ${e.message}"
            else -> "알 수 없는 오류가 발생했습니다: ${e.message}"
        }

        Log.e("HospitalViewModel", errorMessage)
        _error.value = errorMessage
    }

    private fun getDepartmentCategories(departments: List<String>): List<String> {
        return departments.map { dept ->
            DepartmentCategory.values().find { it.categoryName == dept }?.name ?: DepartmentCategory.OTHER_SPECIALTIES.name
        }.distinct()
    }

    fun clearCache() {
        cachedHospitals = null
        lastFetchTime = 0
        lastLocation = null
    }

    override fun onCleared() {
        super.onCleared()
        // 캐시는 유지하고 다른 리소스만 정리
    }

    // 특정 지역의 병원만 보여주고 싶다
    fun fetchHospitalsByRegion(sidoCd: String, sgguCd: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val hospitalInfoResponse = retryWithExponentialBackoff {
                    fetchHospitalInfo(sidoCd, sgguCd)
                }

                if (hospitalInfoResponse.isSuccessful) {
                    val hospitals = hospitalInfoResponse.body()?.body?.items?.itemList ?: emptyList()

                    // NonPayment 정보 가져오기
                    val nonPaymentResponse = retryWithExponentialBackoff { fetchNonPaymentInfo() }
                    val nonPaymentItems = if (nonPaymentResponse.isSuccessful) {
                        nonPaymentResponse.body()?.body?.items ?: emptyList()
                    } else {
                        emptyList()
                    }

                    val combinedHospitals = combineHospitalData(hospitals, nonPaymentItems)

                    // 진료과목 정보 가져오기 (병렬 처리)
                    val updatedHospitals = withContext(Dispatchers.IO) {
                        combinedHospitals.map { hospital ->
                            async {
                                try {
                                    val dgsbjtResponse = retryWithExponentialBackoff {
                                        fetchDgsbjtInfo(hospital.ykiho)
                                    }
                                    if (dgsbjtResponse.isSuccessful) {
                                        updateHospitalWithDgsbjtInfo(hospital, dgsbjtResponse.body()?.body?.items?.itemList)
                                    } else {
                                        hospital
                                    }
                                } catch (e: Exception) {
                                    Log.e("HospitalViewModel", "Error fetching dgsbjt info: ${e.message}")
                                    hospital
                                }
                            }
                        }.awaitAll()
                    }

                    _hospitals.value = updatedHospitals
                } else {
                    _error.value = "데이터를 불러오는데 실패했습니다."
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchHospitalTimeInfo(ykiho: String): HospitalTimeInfo? {
        return try {
            val response = retryWithExponentialBackoff {
                healthInsuranceApi.getDtlInfo(
                    serviceKey = NetworkModule.getServiceKey(),
                    ykiho = ykiho
                )
            }

            Log.d("TimeInfo", "Time Info API Response for $ykiho: ${response.body()}")

            if (response.isSuccessful) {
                val detailItem = response.body()?.body?.items?.item
                Log.d("TimeInfo", "Detail Item for $ykiho: $detailItem")
                convertToHospitalTimeInfo(detailItem)
            } else {
                Log.e("TimeInfo", "Failed to fetch time info for $ykiho: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e("TimeInfo", "Error fetching hospital time info for $ykiho", e)
            null
        }
    }

    private fun convertToHospitalTimeInfo(item: HospitalDetailItem?): HospitalTimeInfo? {
        if (item == null) {
            Log.d("TimeInfo", "Detail item is null")
            return null
        }

        Log.d("TimeInfo", """
        Converting DetailItem:
        - Mon: ${item.trmtMonStart} - ${item.trmtMonEnd}
        - Sat: ${item.trmtSatStart} - ${item.trmtSatEnd}
        - Sun: ${item.trmtSunStart} - ${item.trmtSunEnd}
        - Lunch Week: ${item.lunchWeek}
        - Lunch Sat: ${item.lunchSat}
        - Emergency: ${item.emyDayYn}, ${item.emyNgtYn}
    """.trimIndent())

        return try {
            HospitalTimeInfo(
                weekdayTime = TimeRange(
                    parseTime(item.trmtMonStart),
                    parseTime(item.trmtMonEnd)
                ),
                saturdayTime = TimeRange(
                    parseTime(item.trmtSatStart),
                    parseTime(item.trmtSatEnd)
                ),
                sundayTime = TimeRange(
                    parseTime(item.trmtSunStart),
                    parseTime(item.trmtSunEnd)
                ),
                lunchTime = parseLunchTime(item.lunchWeek),
                saturdayLunchTime = parseLunchTime(item.lunchSat),
                isEmergencyDay = item.emyDayYn == "Y",
                isEmergencyNight = item.emyNgtYn == "Y",
                emergencyDayContact = item.emyDayTelNo1,
                emergencyNightContact = item.emyNgtTelNo1,
                isClosed = item.noTrmtSun == "Y" || item.noTrmtHoli == "Y"
            ).also {
                Log.d("TimeInfo", "Converted TimeInfo: $it")
            }
        } catch (e: Exception) {
            Log.e("TimeInfo", "Error converting hospital time info", e)
            null
        }
    }

    private fun parseTime(timeStr: String?): LocalTime? {
        if (timeStr.isNullOrBlank()) {
            Log.d("TimeInfo", "Time string is null or blank: $timeStr")
            return null
        }
        return try {
            // 시간 형식이 "0900"과 같은 형태인지 확인
            if (timeStr.length != 4) {
                Log.e("TimeInfo", "Invalid time format: $timeStr")
                return null
            }

            val hour = timeStr.substring(0, 2).toInt()
            val minute = timeStr.substring(2, 4).toInt()

            if (hour !in 0..23 || minute !in 0..59) {
                Log.e("TimeInfo", "Invalid hour/minute: $hour:$minute")
                return null
            }

            LocalTime.of(hour, minute).also {
                Log.d("TimeInfo", "Parsed time $timeStr to $it")
            }
        } catch (e: Exception) {
            Log.e("TimeInfo", "Error parsing time: $timeStr", e)
            null
        }
    }

    private fun parseLunchTime(lunchTimeStr: String?): TimeRange? {
        if (lunchTimeStr.isNullOrBlank()) {
            Log.d("TimeInfo", "Lunch time string is null or blank")
            return null
        }

        Log.d("TimeInfo", "Parsing lunch time: $lunchTimeStr")

        // 예: "1200-1330" 형식 파싱
        val times = lunchTimeStr.split("-")
        return if (times.size == 2) {
            TimeRange(
                parseTime(times[0].trim()),
                parseTime(times[1].trim())
            ).also {
                Log.d("TimeInfo", "Parsed lunch time range: $it")
            }
        } else {
            Log.e("TimeInfo", "Invalid lunch time format: $lunchTimeStr")
            null
        }
    }
}