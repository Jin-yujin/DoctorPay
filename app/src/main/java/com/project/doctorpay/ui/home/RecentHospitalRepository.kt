import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.doctorpay.db.HospitalInfo

class RecentHospitalRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "RecentHospitals"
        private const val KEY_RECENT_HOSPITALS = "recent_hospitals"
        private const val MAX_RECENT_HOSPITALS = 5
    }

    fun addRecentHospital(hospital: HospitalInfo) {
        val currentList = getRecentHospitals().toMutableList()

        // 이미 있는 병원이면 제거 (최신으로 업데이트하기 위해)
        currentList.removeIf { it.ykiho == hospital.ykiho }

        // 새 병원을 리스트 맨 앞에 추가
        currentList.add(0, hospital)

        // 최대 5개까지만 유지
        if (currentList.size > MAX_RECENT_HOSPITALS) {
            currentList.removeAt(currentList.lastIndex)
        }

        // 저장
        saveRecentHospitals(currentList)
    }

    fun getRecentHospitals(): List<HospitalInfo> {
        val json = prefs.getString(KEY_RECENT_HOSPITALS, null) ?: return emptyList()
        val type = object : TypeToken<List<HospitalInfo>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRecentHospitals(hospitals: List<HospitalInfo>) {
        val json = gson.toJson(hospitals)
        prefs.edit().putString(KEY_RECENT_HOSPITALS, json).apply()
    }

    fun clearRecentHospitals() {
        prefs.edit().remove(KEY_RECENT_HOSPITALS).apply()
    }
}