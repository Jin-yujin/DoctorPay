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
import androidx.constraintlayout.motion.utils.ViewState
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
import kotlinx.coroutines.flow.asStateFlow
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



    // ViewState 정의
    data class ViewState(
        val hospitals: MutableStateFlow<List<HospitalInfo>> = MutableStateFlow(emptyList()),
        val filteredHospitals: MutableStateFlow<List<HospitalInfo>> = MutableStateFlow(emptyList()),
        val isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false),
        val error: MutableStateFlow<String?> = MutableStateFlow(null),
        var currentPage: Int = 1,
        var isLastPage: Boolean = false,
        var lastLocation: Pair<Double, Double>? = null,
        var cachedHospitals: List<HospitalInfo>? = null,
        var categoryCache: MutableMap<String, List<HospitalInfo>> = mutableMapOf(),
        var lastFetchTime: Long = 0,
        var isDataLoaded: Boolean = false
    )

    // 전역 캐시 추가
    private var globalCache: List<HospitalInfo>? = null
    private var globalCacheTime: Long = 0
    private val CACHE_DURATION = 30 * 60 * 1000 // 30분


    private val viewStates = mutableMapOf<String, ViewState>()

    companion object {
        const val MAP_VIEW = "MAP_VIEW"
        const val LIST_VIEW = "LIST_VIEW"
        const val DETAIL_VIEW = "DETAIL_VIEW"
        const val FAVORITE_VIEW = "FAVORITE_VIEW"
        const val HOME_VIEW = "HOME_VIEW"
        const val DEFAULT_RADIUS = 5000
        private const val CACHE_DURATION = 30 * 60 * 1000 // 30분
        private const val MAX_RETRIES = 3
        private const val BASE_DELAY = 1000L
        private const val REQUEST_TIMEOUT = 30000L
        private const val PAGE_SIZE = 100
    }



    // 페이징 관련 변수
    private var currentPage = 1
    private val pageSize = 100
    private var isLastPage = false

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

    // Fragment별 ViewState 가져오기
    fun getViewState(viewId: String): ViewState {
        return viewStates.getOrPut(viewId) { ViewState() }
    }

    // StateFlow getter 메서드들
    fun getHospitals(viewId: String): StateFlow<List<HospitalInfo>> =
        getViewState(viewId).hospitals.asStateFlow()

    fun getFilteredHospitals(viewId: String): StateFlow<List<HospitalInfo>> =
        getViewState(viewId).filteredHospitals.asStateFlow()

    fun getIsLoading(viewId: String): StateFlow<Boolean> =
        getViewState(viewId).isLoading.asStateFlow()

    fun getError(viewId: String): StateFlow<String?> =
        getViewState(viewId).error.asStateFlow()


    // 카테고리별 데이터 가져오기
    fun getHospitalsByCategory(
        viewId: String,
        category: DepartmentCategory?,
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            val viewState = getViewState(viewId)

            // 1. 글로벌 캐시 체크
            if (globalCache == null ||
                System.currentTimeMillis() - globalCacheTime > CACHE_DURATION ||
                forceRefresh
            ) {
                // 캐시가 없거나 만료되었거나 강제 새로고침인 경우
                viewState.isLoading.value = true
                fetchNearbyHospitals(
                    viewId = viewId,
                    latitude = latitude,
                    longitude = longitude,
                    updateGlobalCache = true
                )
            } else {
                // 2. 캐시된 데이터 사용
                val categoryKey = category?.name ?: "ALL"
                val cachedData = viewState.categoryCache[categoryKey]

                if (cachedData != null && !forceRefresh) {
                    // 카테고리별 캐시가 있는 경우
                    viewState.hospitals.value = cachedData
                    viewState.isLoading.value = false
                } else {
                    // 카테고리별 필터링 수행
                    val filteredData = filterHospitalsByCategory(globalCache!!, category)
                    viewState.categoryCache[categoryKey] = filteredData
                    viewState.hospitals.value = filteredData
                    viewState.isLoading.value = false
                }
            }
        }
    }

    // 캐시 초기화
    fun clearCache(viewId: String) {
        val viewState = getViewState(viewId)
        viewState.categoryCache.clear()
        globalCache = null
        globalCacheTime = 0
        viewState.isDataLoaded = false
    }

    // 페이지네이션 리셋
    fun resetPagination(viewId: String) {
        val viewState = getViewState(viewId)
        viewState.currentPage = 1
        viewState.isLastPage = false
        viewState.hospitals.value = emptyList()
    }

    private suspend fun processHospitalResponse(
        response: Response<HospitalInfoResponse>,
        latitude: Double,
        longitude: Double
    ): List<HospitalInfo> {
        val body = response.body()
        if (body == null) {
            throw IOException("서버 응답이 비어있습니다")
        }

        val newHospitals = body.body?.items?.itemList ?: emptyList()
        val nonPaymentResponse = retryWithExponentialBackoff { fetchNonPaymentInfo() }
        val nonPaymentItems = if (nonPaymentResponse.isSuccessful) {
            nonPaymentResponse.body()?.body?.items ?: emptyList()
        } else {
            emptyList()
        }

        return withContext(Dispatchers.IO) {
            combineHospitalData(newHospitals, nonPaymentItems)
        }
    }


    fun fetchNearbyHospitals(
        viewId: String,
        latitude: Double,
        longitude: Double,
        radius: Int = DEFAULT_RADIUS,
        forceRefresh: Boolean = false,
        updateGlobalCache: Boolean = false
    ) {
        viewModelScope.launch {
            val viewState = getViewState(viewId)

            // 캐시 체크
            if (!forceRefresh &&
                globalCache != null &&
                System.currentTimeMillis() - globalCacheTime < CACHE_DURATION
            ) {
                viewState.hospitals.value = globalCache!!
                viewState.isDataLoaded = true
                return@launch
            }

            viewState.isLoading.value = true
            viewState.error.value = null

            try {
                val response = retryWithExponentialBackoff {
                    healthInsuranceApi.getHospitalInfo(
                        serviceKey = NetworkModule.getServiceKey(),
                        pageNo = viewState.currentPage,
                        numOfRows = PAGE_SIZE,
                        yPos = formatCoordinate(latitude),
                        xPos = formatCoordinate(longitude),
                        radius = radius
                    )
                }

                if (response.isSuccessful) {
                    val hospitals = processHospitalResponse(response, latitude, longitude)
                    if (updateGlobalCache) {
                        globalCache = hospitals
                        globalCacheTime = System.currentTimeMillis()
                    }
                    viewState.hospitals.value = hospitals
                    viewState.isDataLoaded = true
                    viewState.lastLocation = Pair(latitude, longitude)
                } else {
                    throw HttpException(response)
                }
            } catch (e: Exception) {
                handleError(viewId, e)
            } finally {
                viewState.isLoading.value = false
            }
        }
    }

    private fun filterHospitalsWithin5km(viewId: String, latitude: Double, longitude: Double, hospitals: List<HospitalInfo>) {
        val viewState = getViewState(viewId)
        val filteredAndSortedList = hospitals
            .map { hospital ->
                val distance = calculateDistance(
                    latitude, longitude,
                    hospital.latitude, hospital.longitude
                )
                Pair(hospital, distance)
            }
            .filter { (_, distance) -> distance <= 5000 }
            .sortedBy { (_, distance) -> distance }
            .map { (hospital, _) -> hospital }

        viewState.filteredHospitals.value = filteredAndSortedList
    }

    private suspend fun handleHospitalResponse(
        viewId: String,
        response: Response<HospitalInfoResponse>,
        latitude: Double,
        longitude: Double
    ) {
        val viewState = getViewState(viewId)
        viewState.isLoading.value = true
        viewState.isDataLoaded = true

        try {
            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body == null) {
                        viewState.error.value = "서버 응답이 비어있습니다"
                        return
                    }

                    val newHospitals = body.body?.items?.itemList ?: emptyList()
                    val nonPaymentResponse = retryWithExponentialBackoff { fetchNonPaymentInfo() }
                    val nonPaymentItems = if (nonPaymentResponse.isSuccessful) {
                        nonPaymentResponse.body()?.body?.items ?: emptyList()
                    } else {
                        emptyList()
                    }

                    val combinedHospitals = combineHospitalData(newHospitals, nonPaymentItems)

                    // 병렬 처리 최적화
                    val updatedHospitals = withContext(Dispatchers.IO) {
                        combinedHospitals.chunked(4).flatMap { chunk ->
                            chunk.map { hospital ->
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
                                        Log.e("HospitalViewModel", "Error fetching dgsbjt info", e)
                                        hospital
                                    }
                                }
                            }.awaitAll()
                        }
                    }

                    viewState.cachedHospitals = updatedHospitals
                    viewState.lastFetchTime = System.currentTimeMillis()
                    viewState.lastLocation = Pair(latitude, longitude)

                    if (viewState.currentPage == 1) {
                        viewState.hospitals.value = updatedHospitals
                    } else {
                        viewState.hospitals.value = viewState.hospitals.value + updatedHospitals
                    }

                    filterHospitalsWithin5km(viewId, latitude, longitude, viewState.hospitals.value)
                    viewState.isLastPage = newHospitals.size < 50
                    viewState.currentPage++
                }
                response.code() == 429 -> {
                    delay(BASE_DELAY)
                    fetchNearbyHospitals(viewId, latitude, longitude, DEFAULT_RADIUS)
                }
                else -> {
                    viewState.error.value = "서버 오류: ${response.code()} - ${response.message()}"
                }
            }
        } catch (e: Exception) {
            handleError(viewId, e)
        } finally {
            viewState.isLoading.value = false
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

    // 비급여 항목 조회 메서드 수정
    suspend fun fetchNonPaymentDetails(viewId: String, ykiho: String): List<NonPaymentItem> {
        val viewState = getViewState(viewId)
        viewState.isLoading.value = true

        try {
            val response = retryWithExponentialBackoff {
                healthInsuranceApi.getNonPaymentItemHospDtlList(
                    serviceKey = NetworkModule.getServiceKey(),
                    ykiho = ykiho
                )
            }
            return if (response.isSuccessful) {
                response.body()?.body?.items ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            viewState.error.value = "비급여 항목을 불러오는데 실패했습니다: ${e.message}"
            return emptyList()
        } finally {
            viewState.isLoading.value = false
        }
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

    // 검색 기능 업데이트
    fun searchHospitals(viewId: String, query: String) {
        viewModelScope.launch {
            val viewState = getViewState(viewId)
            viewState.isLoading.value = true

            try {
                val searchBase = viewState.cachedHospitals ?: viewState.hospitals.value
                val filteredHospitals = searchBase.filter { hospital ->
                    hospital.name.contains(query, ignoreCase = true) ||
                            hospital.departments.any { it.contains(query, ignoreCase = true) } ||
                            hospital.address.contains(query, ignoreCase = true)
                }
                viewState.hospitals.value = filteredHospitals
            } catch (e: Exception) {
                viewState.error.value = "검색 중 오류가 발생했습니다: ${e.message}"
            } finally {
                viewState.isLoading.value = false
            }
        }
    }

    // 검색 초기화
    fun resetSearch(viewId: String) {
        val viewState = getViewState(viewId)
        viewModelScope.launch {
            viewState.cachedHospitals?.let {
                viewState.hospitals.value = it
            }
        }
    }

    private fun getDepartmentCategories(departments: List<String>): List<String> {
        return departments.map { dept ->
            DepartmentCategory.values().find { it.categoryName == dept }?.name ?: DepartmentCategory.OTHER_SPECIALTIES.name
        }.distinct()
    }


    private fun handleError(viewId: String, e: Exception) {
        val viewState = getViewState(viewId)
        val errorMessage = when (e) {
            is TimeoutCancellationException -> "요청 시간이 초과되었습니다."
            is SocketTimeoutException -> "서버 응답 시간이 초과되었습니다."
            is UnknownHostException -> "인터넷 연결을 확인해주세요."
            is HttpException -> {
                when (e.code()) {
                    429 -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
                    in 500..599 -> "서버 오류가 발생했습니다."
                    else -> "네트워크 오류: ${e.code()}"
                }
            }
            else -> "알 수 없는 오류가 발생했습니다: ${e.message}"
        }
        viewState.error.value = errorMessage
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


    override fun onCleared() {
        super.onCleared()
        viewStates.clear()
    }
}