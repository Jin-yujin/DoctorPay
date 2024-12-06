package com.project.doctorpay.comp

import android.app.Activity
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment

class BackPressHandler(private val activity: Activity) {
    var backPressedTime: Long = 0
    val finishIntervalTime: Long = 2000

    fun onBackPressed(finishCallback: () -> Unit) {
        if (System.currentTimeMillis() > backPressedTime + finishIntervalTime) {
            backPressedTime = System.currentTimeMillis()
            showGuide()
        } else {
            finishCallback()
        }
    }

    private fun showGuide() {
        Toast.makeText(activity, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
    }
}

// Extension function for Fragment
fun Fragment.handleBackPress(onBackPressed: () -> Unit) {
    val backPressHandler = BackPressHandler(requireActivity())
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                backPressHandler.onBackPressed {
                    onBackPressed()
                }
            }
        }
    )
}