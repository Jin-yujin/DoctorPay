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
import com.project.doctorpay.db.OperationState
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
import java.util.concurrent.TimeoutException
import kotlin.math.pow

class HospitalViewModel(
    private val healthInsuranceApi: HealthInsuranceApi,
    private val repository: HospitalRepository
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
    private val dgsbjtInfoCache = mutableMapOf<String, List<String>>()
    private val cacheTimestamps = mutableMapOf<String, Long>()

    private val viewStates = mutableMapOf<String, ViewState>()

    companion object {
        const val MAP_VIEW = "MAP_VIEW"
        const val LIST_VIEW = "LIST_VIEW"
        const val DETAIL_VIEW = "DETAIL_VIEW"
        const val FAVORITE_VIEW = "FAVORITE_VIEW"
        const val HOME_VIEW = "HOME_VIEW"
        const val DEFAULT_RADIUS = 3000
        private const val CACHE_DURATION = 30 * 60 * 1000 // 30분
        private const val RETRY_COUNT = 2
        private const val BASE_DELAY = 500L
        private const val TIMEOUT_DURATION = 15000L
        const val PAGE_SIZE = 50 // 기본 페이지 사이즈 추가

        // 화면별 페이지 사이즈 정의
        private val VIEW_PAGE_SIZES = mapOf(
            MAP_VIEW to 30,
            LIST_VIEW to 50,
            DETAIL_VIEW to 1,
            FAVORITE_VIEW to 50,
            HOME_VIEW to 50
        )
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
        times: Int = 2,
        initialDelay: Long = 1000,
        maxDelay: Long = 3000,
        factor: Double = 1.5,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        var lastException: Exception? = null

        repeat(times) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt == times - 1) throw e

                Log.w("RetryOperation", "Attempt ${attempt + 1} failed: ${e.message}")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }

        throw lastException ?: IllegalStateException("Retry failed")
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
            viewStates[HOME_VIEW]?.isLoading?.value = true
            viewStates[LIST_VIEW]?.isLoading?.value = true
            viewStates[FAVORITE_VIEW]?.isLoading?.value = true  // FAVORITE_VIEW도 추가

            try {
                // 1. 글로벌 캐시 체크
                if (globalCache == null ||
                    System.currentTimeMillis() - globalCacheTime > CACHE_DURATION ||
                    forceRefresh
                ) {
                    // 캐시가 없거나 만료되었거나 강제 새로고침인 경우
                    viewState.isLoading.value = true
                    val result = fetchNearbyHospitals(
                        viewId = viewId,
                        latitude = latitude,
                        longitude = longitude,
                        updateGlobalCache = true
                    )
                    // 결과를 캐시 및 hospitals에 저장
                    if (viewId == FAVORITE_VIEW) {
                        viewState.cachedHospitals = result
                    }
                } else {
                    // 2. 캐시된 데이터 사용
                    val categoryKey = category?.name ?: "ALL"
                    val cachedData = viewState.categoryCache[categoryKey]

                    if (cachedData != null && !forceRefresh) {
                        // 카테고리별 캐시가 있는 경우
                        viewState.hospitals.value = cachedData
                    } else {
                        // 카테고리별 필터링 수행
                        val filteredData = filterHospitalsByCategory(globalCache!!, category)
                        viewState.categoryCache[categoryKey] = filteredData
                        viewState.hospitals.value = filteredData
                    }
                }
            } finally {
                viewStates[HOME_VIEW]?.isLoading?.value = false
                viewStates[LIST_VIEW]?.isLoading?.value = false
                viewStates[FAVORITE_VIEW]?.isLoading?.value = false
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

    // MapView를 위한 최적화된 데이터 처리
    private suspend fun processHospitalResponseForMap(
        response: Response<HospitalInfoResponse>,
        latitude: Double,
        longitude: Double
    ): List<HospitalInfo> {
        val body = response.body()
        if (body == null) {
            throw IOException("서버 응답이 비어있습니다")
        }

        val newHospitals = body.body?.items?.itemList?.take(30) ?: emptyList() // MapView용으로 30개로 제한
        val nonPaymentResponse = retryWithExponentialBackoff { fetchNonPaymentInfo() }
        val nonPaymentItems = if (nonPaymentResponse.isSuccessful) {
            nonPaymentResponse.body()?.body?.items?.itemList?.take(30) ?: emptyList()
        } else {
            emptyList()
        }

        return withContext(Dispatchers.IO) {
            combineHospitalData(newHospitals, nonPaymentItems)
        }
    }

    private suspend fun processHospitalResponseForList(
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
            nonPaymentResponse.body()?.body?.items?.itemList ?: emptyList()
        } else {
            emptyList()
        }

        return withContext(Dispatchers.IO) {
            combineHospitalData(newHospitals, nonPaymentItems)
        }
    }

    // 병원 데이터 결합 최적화
    private suspend fun combineHospitalData(
        hospitalInfoItems: List<HospitalInfoItem>?,
        nonPaymentItems: List<NonPaymentItem>?
    ): List<HospitalInfo> = withContext(Dispatchers.Default) {
        val nonPaymentMap = nonPaymentItems
            ?.groupBy { it.yadmNm ?: "" }
            ?.filterKeys { it.isNotEmpty() }
            ?: emptyMap()

        hospitalInfoItems?.chunked(5)?.flatMap { chunk ->
            chunk.mapNotNull { hospitalInfo ->
                supervisorScope {
                    try {
                        if (!isValidHospitalInfo(hospitalInfo)) return@supervisorScope null

                        val ykiho = hospitalInfo.ykiho
                        if (ykiho == null || ykiho.isBlank()) {
                            return@supervisorScope createHospitalInfo(
                                hospitalInfo = hospitalInfo,
                                nonPaymentMap = nonPaymentMap,
                                timeInfo = getDefaultTimeInfo(),
                                dgsbjtCodes = emptyList()
                            )
                        }

                        // API 호출 병렬 처리
                        val deferredDgsbjtCodes = async(Dispatchers.IO) {
                            try {
                                withTimeout(5000) {
                                    fetchDgsbjtInfo(ykiho)
                                }
                            } catch (e: Exception) {
                                Log.w("HospitalViewModel", "Error fetching dgsbjtInfo for $ykiho", e)
                                emptyList<String>()
                            }
                        }

                        val deferredTimeInfo = async(Dispatchers.IO) {
                            try {
                                withTimeout(5000) {
                                    fetchHospitalTimeInfo(ykiho)
                                }
                            } catch (e: Exception) {
                                Log.w("HospitalViewModel", "Error fetching timeInfo for $ykiho", e)
                                getDefaultTimeInfo()
                            }
                        }

                        // 병렬로 실행된 작업들의 결과 수집
                        val (dgsbjtCodes, timeInfo) = awaitAll(
                            deferredDgsbjtCodes,
                            deferredTimeInfo
                        )

                        createHospitalInfo(
                            hospitalInfo = hospitalInfo,
                            nonPaymentMap = nonPaymentMap,
                            timeInfo = timeInfo as HospitalTimeInfo,
                            dgsbjtCodes = dgsbjtCodes as List<String>
                        )

                    } catch (e: Exception) {
                        Log.e("HospitalViewModel", "Error processing hospital", e)
                        createHospitalInfo(
                            hospitalInfo = hospitalInfo,
                            nonPaymentMap = nonPaymentMap,
                            timeInfo = getDefaultTimeInfo(),
                            dgsbjtCodes = emptyList()
                        )
                    }
                }
            }
        } ?: emptyList()
    }

    suspend fun getNonPaymentInfo(viewId: String = LIST_VIEW): List<NonPaymentItem> {
        return try {
            val pageSize = VIEW_PAGE_SIZES[viewId] ?: PAGE_SIZE

            val response = retryWithExponentialBackoff {
                healthInsuranceApi.getNonPaymentInfo(
                    serviceKey = NetworkModule.getServiceKey(),
                    pageNo = 1,
                    numOfRows = pageSize
                )
            }
            if (response.isSuccessful) {
                response.body()?.body?.items?.itemList ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("HospitalViewModel", "Error fetching non-payment info", e)
            emptyList()
        }
    }

    // 비급여 정보만 가져오는 메서드 추가
    // HospitalViewModel에서
    suspend fun fetchNonPaymentItemsOnly(ykiho: String): List<NonPaymentItem> {
        return withContext(Dispatchers.IO) {
            try {
                val response = healthInsuranceApi.getNonPaymentItemHospDtlList(
                    serviceKey = NetworkModule.getServiceKey(),
                    ykiho = ykiho,
                    pageNo = 1,
                    numOfRows = 100
                )

                if (response.isSuccessful) {
                    response.body()?.body?.items?.itemList ?: emptyList()
                } else {
                    Log.e("NonPaymentAPI", "Failed to fetch non-payment items: ${response.code()}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("NonPaymentAPI", "Error fetching non-payment items", e)
                emptyList()
            }
        }
    }

    suspend fun fetchNonPaymentDetails(viewId: String, ykiho: String): List<NonPaymentItem> {
        return fetchNonPaymentItemsOnly(ykiho)  // 단순히 비급여 정보만 가져오도록 변경
    }

    private suspend fun processHospitalResponse(
        response: Response<HospitalInfoResponse>,
        latitude: Double,
        longitude: Double,
        viewId: String = LIST_VIEW
    ): List<HospitalInfo> {
        return when (viewId) {
            MAP_VIEW -> processHospitalResponseForMap(response, latitude, longitude)
            else -> processHospitalResponseForList(response, latitude, longitude)
        }
    }

    data class NonPaymentLoadResult(
        val items: List<NonPaymentItem>,
        val hasMore: Boolean
    )

    suspend fun fetchNonPaymentItemsOnly(
        ykiho: String,
        pageNo: Int = 1,
        pageSize: Int = 100
    ): NonPaymentLoadResult {
        return try {
            Log.d("NonPaymentAPI", "Fetching non-payment items for ykiho: $ykiho, page: $pageNo")

            val response = healthInsuranceApi.getNonPaymentItemHospDtlList(
                serviceKey = NetworkModule.getServiceKey(),
                ykiho = ykiho,
                pageNo = pageNo,
                numOfRows = pageSize
            )

            if (response.isSuccessful) {
                val body = response.body()?.body
                val items = body?.items?.itemList ?: emptyList()
                // totalCount를 안전하게 처리
                val totalCount = try {
                    when (val total = body?.totalCount) {
                        is Number -> total.toInt()
                        else -> 0
                    }
                } catch (e: Exception) {
                    Log.e("NonPaymentAPI", "Error parsing totalCount", e)
                    0
                }

                val hasMore = (pageNo * pageSize) < totalCount

                Log.d("NonPaymentAPI", "Fetched ${items.size} items, total: $totalCount")
                NonPaymentLoadResult(items, hasMore)
            } else {
                Log.e("NonPaymentAPI", "Failed to fetch non-payment items: ${response.code()}")
                NonPaymentLoadResult(emptyList(), false)
            }
        } catch (e: Exception) {
            Log.e("NonPaymentAPI", "Error fetching non-payment items", e)
            NonPaymentLoadResult(emptyList(), false)
        }
    }

    fun fetchNearbyHospitals(
        viewId: String,
        latitude: Double,
        longitude: Double,
        radius: Int = DEFAULT_RADIUS,
        forceRefresh: Boolean = false,
        updateGlobalCache: Boolean = false
    ): List<HospitalInfo> {
        var result = emptyList<HospitalInfo>()

        // 화면별 페이지 사이즈 적용
        val pageSize = VIEW_PAGE_SIZES[viewId] ?: 100

        viewModelScope.launch {
            val viewState = getViewState(viewId)
            viewState.error.value = null

            viewStates[HOME_VIEW]?.isLoading?.value = true
            viewStates[LIST_VIEW]?.isLoading?.value = true
            if (viewId == FAVORITE_VIEW) {
                viewStates[FAVORITE_VIEW]?.isLoading?.value = true
            }

            try {
                val response = retryWithExponentialBackoff {
                    healthInsuranceApi.getHospitalInfo(
                        serviceKey = NetworkModule.getServiceKey(),
                        pageNo = viewState.currentPage,
                        numOfRows = pageSize,
                        yPos = formatCoordinate(latitude),
                        xPos = formatCoordinate(longitude),
                        radius = radius
                    )
                }

                if (response.isSuccessful) {
                    // 여기서 result 변수에 할당
                    result = processHospitalResponse(response, latitude, longitude)
                    // HOME_VIEW와 LIST_VIEW 모두에 데이터 저장
                    viewStates[HOME_VIEW]?.hospitals?.value = result
                    viewStates[LIST_VIEW]?.hospitals?.value = result

//                    viewState.isLoading.value = true

                    // isDataLoaded 플래그도 둘 다 true로 설정
                    viewStates[HOME_VIEW]?.isDataLoaded = true
                    viewStates[LIST_VIEW]?.isDataLoaded = true

                    if (updateGlobalCache) {
                        globalCache = result
                        globalCacheTime = System.currentTimeMillis()
                    }
                    viewState.hospitals.value = result

                    // filteredHospitals 업데이트 - MapView를 위한 최적화
                    if (viewId == MAP_VIEW) {
                        val filtered = result.take(pageSize) // MapView용 제한된 수의 병원만
                        filterHospitalsWithin5km(viewId, latitude, longitude, filtered)
                    } else {
                        filterHospitalsWithin5km(viewId, latitude, longitude, result)
                    }

                    viewState.isDataLoaded = true
                    viewState.lastLocation = Pair(latitude, longitude)

                    if (viewId == FAVORITE_VIEW) {
                        // 즐겨찾기 뷰의 경우 캐시 유지
                        viewState.cachedHospitals = result
                    }
                } else {
                    throw HttpException(response)
                }
            } catch (e: Exception) {
                handleError(viewId, e)
            } finally {
                if (viewId == FAVORITE_VIEW) {
                    viewStates[FAVORITE_VIEW]?.isLoading?.value = false
                }
//                viewState.isLoading.value = false
                viewStates[HOME_VIEW]?.isLoading?.value = false
                viewStates[LIST_VIEW]?.isLoading?.value = false
            }
        }

        return result
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

    private suspend fun fetchDgsbjtInfo(ykiho: String): List<String> {
        // 캐시된 데이터 확인
        val cachedData = dgsbjtInfoCache[ykiho]
        val cacheTimestamp = cacheTimestamps[ykiho] ?: 0L

        if (cachedData != null &&
            System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION) {
            return cachedData
        }

        return try {
            withTimeout(5000) {
                val response = retryWithExponentialBackoff(
                    times = 2,
                    initialDelay = 1000,
                    maxDelay = 3000
                ) {
                    healthInsuranceApi.getDgsbjtInfo(
                        serviceKey = NetworkModule.getServiceKey(),
                        ykiho = ykiho.trim(),
                        pageNo = 1,
                        numOfRows = 30
                    )
                }

                if (response.isSuccessful) {
                    response.body()?.body?.items?.itemList
                        ?.mapNotNull { it.dgsbjtCd }
                        ?.also { codes ->
                            dgsbjtInfoCache[ykiho] = codes
                            cacheTimestamps[ykiho] = System.currentTimeMillis()
                        } ?: emptyList()
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.w("HospitalViewModel", "Error fetching dgsbjtInfo for $ykiho", e)
            emptyList()
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

    // createHospitalInfo 함수 수정
    private fun createHospitalInfo(
        hospitalInfo: HospitalInfoItem,
        nonPaymentMap: Map<String, List<NonPaymentItem>>,
        timeInfo: HospitalTimeInfo?,
        dgsbjtCodes: List<String>  // 진료과목 코드 파라미터 추가
    ): HospitalInfo {
        val latitude = hospitalInfo.YPos?.toDoubleOrNull() ?: 0.0
        val longitude = hospitalInfo.XPos?.toDoubleOrNull() ?: 0.0

        // 비급여 항목 처리
        val nonPaymentItems = nonPaymentMap[hospitalInfo.yadmNm ?: ""] ?: emptyList()

        // 진료과목 추론 (이제 API에서 받아온 진료과목 코드 사용)
        val departments = inferDepartments(
            hospitalName = hospitalInfo.yadmNm ?: "",
            nonPaymentItems = nonPaymentItems,
            departmentCodes = dgsbjtCodes  // API에서 받아온 코드 사용
        )

        // 진료과목 카테고리 추출
        val departmentCategories = departments.mapNotNull { department ->
            DepartmentCategory.values().find { it.categoryName == department }?.name
        }.distinct()

        // 운영 상태 확인
        val operationState = timeInfo?.getCurrentState() ?: OperationState.UNKNOWN
        val stateText = operationState.toDisplayText()

        return HospitalInfo(
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
            nonPaymentItems = nonPaymentItems,
            clCdNm = hospitalInfo.clCdNm ?: "",
            ykiho = hospitalInfo.ykiho ?: "",
            timeInfo = timeInfo
        )
    }

    private fun isValidHospitalInfo(hospitalInfo: HospitalInfoItem): Boolean {
        return hospitalInfo.YPos?.toDoubleOrNull() != null &&
                hospitalInfo.XPos?.toDoubleOrNull() != null &&
                hospitalInfo.yadmNm?.isNotBlank() == true
    }

    private fun determineOperationState(timeInfo: HospitalTimeInfo): String {
        return when {
            timeInfo == null -> "운영시간 정보없음"
            timeInfo.isClosed -> "영업마감"
            timeInfo.isEmergencyDay || timeInfo.isEmergencyNight -> "응급실 운영중"
            else -> {
                val nowTime = LocalTime.now()
                val currentDay = LocalDate.now().dayOfWeek
                when (currentDay) {
                    DayOfWeek.SUNDAY -> timeInfo.sundayTime
                    DayOfWeek.SATURDAY -> timeInfo.saturdayTime
                    else -> timeInfo.weekdayTime
                }?.let { timeRange ->
                    if (timeRange.start == null || timeRange.end == null) {
                        "운영시간 정보없음"
                    } else if (nowTime.isAfter(timeRange.start) && nowTime.isBefore(timeRange.end)) {
                        if (isLunchTime(timeInfo, currentDay, nowTime)) "점심시간" else "영업중"
                    } else {
                        "영업마감"
                    }
                } ?: "운영시간 정보없음"
            }
        }
    }

    private fun isLunchTime(timeInfo: HospitalTimeInfo, currentDay: DayOfWeek, nowTime: LocalTime): Boolean {
        val lunchTimeRange = if (currentDay == DayOfWeek.SATURDAY) {
            timeInfo.saturdayLunchTime
        } else {
            timeInfo.lunchTime
        }

        return lunchTimeRange?.let { lunch ->
            lunch.start?.let { start ->
                lunch.end?.let { end ->
                    nowTime.isAfter(start) && nowTime.isBefore(end)
                }
            }
        } ?: false
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
            is TimeoutCancellationException -> {
                Log.w("HospitalViewModel", "Timeout occurred, continuing with default values")
                null // 타임아웃은 에러 메시지를 표시하지 않음
            }
            is SocketTimeoutException -> "서버 응답 시간이 초과되었습니다."
            is UnknownHostException -> "인터넷 연결을 확인해주세요."
            is HttpException -> {
                when (e.code()) {
                    429 -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
                    in 500..599 -> "서버 오류가 발생했습니다."
                    else -> "네트워크 오류: ${e.code()}"
                }
            }
            else -> {
                Log.e("HospitalViewModel", "Unexpected error", e)
                "일시적인 오류가 발생했습니다."
            }
        }
        errorMessage?.let { viewState.error.value = it }
    }

    // 운영시간 정보 조회 최적화
    suspend fun fetchHospitalTimeInfo(ykiho: String): HospitalTimeInfo {
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(15000) {
                    val response = retryWithExponentialBackoff(
                        times = 3,
                        initialDelay = 2000,
                        maxDelay = 5000
                    ) {
                        healthInsuranceApi.getDtlInfo(
                            serviceKey = NetworkModule.getServiceKey(),
                            ykiho = ykiho
                        )
                    }

                    if (response.isSuccessful) {
                        val detailItem = response.body()?.body?.items?.item
                        convertToHospitalTimeInfo(detailItem)
                    } else {
                        Log.w("TimeInfo", "Failed to fetch time info: ${response.code()}")
                        getDefaultTimeInfo()
                    }
                }
            } catch (e: Exception) {
                Log.e("TimeInfo", "Error fetching time info", e)
                when (e) {
                    is TimeoutCancellationException -> {
                        Log.w("TimeInfo", "Timeout occurred, using default time info")
                        getDefaultTimeInfo()
                    }
                    else -> getDefaultTimeInfo()
                }
            }
        }
    }

    private fun convertToHospitalTimeInfo(item: HospitalDetailItem?): HospitalTimeInfo {
        if (item == null) {
            Log.d("TimeInfo", "Detail item is null, using default time info")
            return getDefaultTimeInfo()
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
                    parseTime(item.trmtMonStart) ?: LocalTime.of(9, 0),
                    parseTime(item.trmtMonEnd) ?: LocalTime.of(18, 0)
                ),
                saturdayTime = TimeRange(
                    parseTime(item.trmtSatStart) ?: LocalTime.of(9, 0),
                    parseTime(item.trmtSatEnd) ?: LocalTime.of(13, 0)
                ),
                sundayTime = if (item.noTrmtSun == "Y") null else TimeRange(
                    parseTime(item.trmtSunStart),
                    parseTime(item.trmtSunEnd)
                ),
                lunchTime = parseLunchTime(item.lunchWeek),
                saturdayLunchTime = parseLunchTime(item.lunchSat),
                isEmergencyDay = item.emyDayYn == "Y",
                isEmergencyNight = item.emyNgtYn == "Y",
                emergencyDayContact = item.emyDayTelNo1,
                emergencyNightContact = item.emyNgtTelNo1,
                isClosed = false
            )
        } catch (e: Exception) {
            Log.e("TimeInfo", "Error converting hospital time info, using default", e)
            getDefaultTimeInfo()
        }
    }

    private fun parseLunchTime(lunchTimeStr: String?): TimeRange? {
        if (lunchTimeStr.isNullOrBlank() ||
            lunchTimeStr == "없음" ||
            lunchTimeStr == "점심시간 없음") {
            return null
        }

        Log.d("TimeInfo", "Parsing lunch time: $lunchTimeStr")

        try {
            // 1. 오후/오전 포맷 처리
            val amPmPattern = """오[전후]\s*(\d{1,2})시\s*(\d{0,2})분?\s*[~\-]\s*오[전후]\s*(\d{1,2})시\s*(\d{0,2})분?""".toRegex()
            amPmPattern.find(lunchTimeStr)?.let { match ->
                val (startHour, startMin, endHour, endMin) = match.destructured
                return TimeRange(
                    LocalTime.of(
                        if (lunchTimeStr.contains("오후") && startHour.toInt() != 12) startHour.toInt() + 12 else startHour.toInt(),
                        if (startMin.isEmpty()) 0 else startMin.toInt()
                    ),
                    LocalTime.of(
                        if (lunchTimeStr.contains("오후") && endHour.toInt() != 12) endHour.toInt() + 12 else endHour.toInt(),
                        if (endMin.isEmpty()) 0 else endMin.toInt()
                    )
                )
            }

            // 2. 한글 시분 형식
            val koreanPattern = """(\d{1,2})시\s*(\d{0,2})분?\s*[~\-]\s*(\d{1,2})시\s*(\d{0,2})분?""".toRegex()
            koreanPattern.find(lunchTimeStr)?.let { match ->
                val (startHour, startMin, endHour, endMin) = match.destructured
                return TimeRange(
                    LocalTime.of(startHour.toInt(), if (startMin.isEmpty()) 0 else startMin.toInt()),
                    LocalTime.of(endHour.toInt(), if (endMin.isEmpty()) 0 else endMin.toInt())
                )
            }

            // 3. 콜론 형식
            val colonPattern = """(\d{1,2})[:：](\d{2})\s*[~\-]\s*(\d{1,2})[:：](\d{2})""".toRegex()
            colonPattern.find(lunchTimeStr)?.let { match ->
                val (startHour, startMin, endHour, endMin) = match.destructured
                return TimeRange(
                    LocalTime.of(startHour.toInt(), startMin.toInt()),
                    LocalTime.of(endHour.toInt(), endMin.toInt())
                )
            }

            // 4. 숫자만 있는 형식
            val numericPattern = """(\d{1,2})[;：]?(\d{0,2})[~\-](\d{1,2})[;：]?(\d{0,2})""".toRegex()
            numericPattern.find(lunchTimeStr.replace("""[^0-9;:~\-]""".toRegex(), ""))?.let { match ->
                val (startHour, startMin, endHour, endMin) = match.destructured
                return TimeRange(
                    LocalTime.of(
                        startHour.toInt(),
                        if (startMin.isEmpty()) 0 else startMin.toInt()
                    ),
                    LocalTime.of(
                        endHour.toInt(),
                        if (endMin.isEmpty()) 0 else endMin.toInt()
                    )
                )
            }

            // 기본 점심시간 리턴
            Log.d("TimeInfo", "Using default lunch time for format: $lunchTimeStr")
            return TimeRange(LocalTime.of(12, 30), LocalTime.of(13, 30))
        } catch (e: Exception) {
            Log.e("TimeInfo", "Error parsing lunch time: $lunchTimeStr", e)
            return TimeRange(LocalTime.of(12, 30), LocalTime.of(13, 30))
        }
    }

    private fun parseTime(timeStr: String?): LocalTime? {
        if (timeStr.isNullOrBlank()) {
            Log.d("TimeInfo", "Time string is null or blank: $timeStr")
            return null
        }

        return try {
            val cleanTime = timeStr.trim().replace("""[^0-9]""".toRegex(), "")
            if (cleanTime.length != 4) {
                Log.e("TimeInfo", "Invalid time format: $timeStr")
                return null
            }

            val hour = cleanTime.substring(0, 2).toInt()
            val minute = cleanTime.substring(2, 4).toInt()

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

    suspend fun searchNonPaymentItems(
        query: String,
        latitude: Double,
        longitude: Double
    ): List<NonPaymentItem> {
        return try {
            withContext(Dispatchers.IO) {
                retryWithExponentialBackoff(
                    times = 3,
                    initialDelay = 1000,
                    maxDelay = 5000,
                    factor = 2.0
                ) {
                    val nonPaymentItems = mutableListOf<NonPaymentItem>()

                    // 1. 검색어로 직접 비급여 항목 검색
                    val directSearchResponse = healthInsuranceApi.getNonPaymentInfo(
                        serviceKey = NetworkModule.getServiceKey(),
                        pageNo = 1,
                        numOfRows = 100,
                        itemNm = query
                    )

                    if (directSearchResponse.isSuccessful) {
                        directSearchResponse.body()?.body?.items?.itemList?.let {
                            nonPaymentItems.addAll(it)
                        }
                    }

                    // 2. 주변 병원 검색 (타임아웃 설정 추가)
                    withTimeout(10000) {
                        val hospitalResponse = healthInsuranceApi.getHospitalInfo(
                            serviceKey = NetworkModule.getServiceKey(),
                            pageNo = 1,
                            numOfRows = 50, // 병원 수 제한하여 타임아웃 방지
                            xPos = longitude.toString(),
                            yPos = latitude.toString(),
                            radius = 10000
                        )

                        // 3. 각 병원별 비급여 항목 조회
                        if (hospitalResponse.isSuccessful) {
                            supervisorScope {
                                hospitalResponse.body()?.body?.items?.itemList
                                    ?.take(10) // 동시 요청 수 제한
                                    ?.map { hospital ->
                                        async {
                                            hospital.ykiho?.let { ykiho ->
                                                try {
                                                    val hospitalItemsResponse = withTimeout(5000) {
                                                        healthInsuranceApi.getNonPaymentItemHospDtlList(
                                                            serviceKey = NetworkModule.getServiceKey(),
                                                            ykiho = ykiho,
                                                            pageNo = 1,
                                                            numOfRows = 100
                                                        )
                                                    }

                                                    if (hospitalItemsResponse.isSuccessful) {
                                                        hospitalItemsResponse.body()?.body?.items?.itemList
                                                            ?.filter { item ->
                                                                item.npayKorNm?.contains(query, ignoreCase = true) == true ||
                                                                        item.itemNm?.contains(query, ignoreCase = true) == true
                                                            }
                                                            ?.map { item ->
                                                                item.copy(
                                                                    ykiho = ykiho,
                                                                    yadmNm = hospital.yadmNm,
                                                                    latitude = hospital.YPos,
                                                                    longitude = hospital.XPos
                                                                )
                                                            }
                                                    } else null
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            }
                                        }
                                    }?.awaitAll()
                                    ?.filterNotNull()
                                    ?.flatten()
                                    ?.let { items ->
                                        nonPaymentItems.addAll(items)
                                    }
                            }
                        }
                    }

                    nonPaymentItems
                        .distinctBy { Triple(it.ykiho, it.itemCd, it.npayKorNm) }
                        .filter { item ->
                            !item.npayKorNm.isNullOrBlank() && !item.yadmNm.isNullOrBlank()
                        }
                        .sortedBy { it.yadmNm }
                }
            }
        } catch (e: Exception) {
            Log.e("HospitalViewModel", "Error searching non-payment items", e)
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewStates.clear()
    }
}