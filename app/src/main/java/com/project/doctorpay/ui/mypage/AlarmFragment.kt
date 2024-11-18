package com.project.doctorpay.ui.mypage


import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentAlarmSettingBinding

class AlarmFragment: Fragment() {

    private var _binding: FragmentAlarmSettingBinding? = null
    // Firebase DB Service 객체 주입
//    private lateinit var switchAllNotification: SwitchCompat
    private lateinit var switchRemindNotification: SwitchCompat
//    private lateinit var switchParticipationNotification: SwitchCompat




    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_alarm_setting, container, false)

        switchRemindNotification = view.findViewById(R.id.switchReserveNotification)

        context?.let { nonNullContext ->
            switchRemindNotification.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(nonNullContext, R.color.signature_color4))
            switchRemindNotification.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(nonNullContext, R.color.gray))
        }


        // 저장된 설정 불러오기
//        loadNotificationSettings()

        // Switch 리스너 설정
//        switchAllNotification.setOnCheckedChangeListener { _, isChecked ->
//            saveNotificationSetting("all_notification_enabled", isChecked)
//            updateOtherSwitches(isChecked)
//            switchChatNotification.isChecked = isChecked
//            switchParticipationNotification.isChecked = isChecked
//            updateSwitchColor(switchAllNotification, isChecked)
//        }

        switchRemindNotification.setOnCheckedChangeListener { _, isChecked ->
//            saveNotificationSetting("chat_notification_enabled", isChecked)
            updateSwitchColor(switchRemindNotification, isChecked)
        }

//        switchParticipationNotification.setOnCheckedChangeListener { _, isChecked ->
//            saveNotificationSetting("participation_notification_enabled", isChecked)
//            updateSwitchColor(switchParticipationNotification, isChecked)
//        }
        // 뒤로가기 버튼 처리
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Instead of replacing the fragment, just pop the back stack
                requireActivity().supportFragmentManager.popBackStack()
            }
        })

        return view

    }

    private fun updateSwitchColor(switch: SwitchCompat, isChecked: Boolean) {
        context?.let { nonNullContext ->
            if (isChecked) {
                switch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(nonNullContext, R.color.signature_color4))
                switch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(nonNullContext, R.color.signature_color1))
            } else {
                switch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(nonNullContext, R.color.signature_color4))
                switch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(nonNullContext, R.color.gray))
            }
        }
    }

//    private fun loadNotificationSettings() {
//        val sharedPreferences = requireActivity().getSharedPreferences("NotificationPreferences", Context.MODE_PRIVATE)
//        val allEnabled = sharedPreferences.getBoolean("all_notification_enabled", true)
//        switchAllNotification.isChecked = allEnabled
//        switchChatNotification.isChecked = sharedPreferences.getBoolean("chat_notification_enabled", true)
//        switchParticipationNotification.isChecked = sharedPreferences.getBoolean("participation_notification_enabled", true)
//
//        switchChatNotification.isEnabled = allEnabled
//        switchParticipationNotification.isEnabled = allEnabled
//    }

//    private fun saveNotificationSetting(key: String, value: Boolean) {
//        val sharedPreferences = requireActivity().getSharedPreferences("NotificationPreferences", Context.MODE_PRIVATE)
//        sharedPreferences.edit().putBoolean(key, value).apply()
//    }
//
//    private fun updateOtherSwitches(allEnabled: Boolean) {
//        switchChatNotification.isEnabled = allEnabled
//        switchParticipationNotification.isEnabled = allEnabled
//        if (allEnabled) {
//            switchChatNotification.isChecked = true
//            switchParticipationNotification.isChecked = true
//            saveNotificationSetting("chat_notification_enabled", true)
//            saveNotificationSetting("participation_notification_enabled", true)
//        } else {
//            switchChatNotification.isChecked = false
//            switchParticipationNotification.isChecked = false
//            saveNotificationSetting("chat_notification_enabled", false)
//            saveNotificationSetting("participation_notification_enabled", false)
//        }
//    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}