package com.project.doctorpay.comp

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.animation.doOnEnd
import com.project.doctorpay.databinding.FragmentMapviewBinding

class LoadingManager(private val binding: FragmentMapviewBinding) {

    private var currentDots = 0
    private val maxDots = 3
    private var dotAnimator: ObjectAnimator? = null

    fun showLoading() {
        binding.loadingIndicator.root.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
        startDotAnimation()
    }

    fun hideLoading() {
        binding.loadingIndicator.root.animate()
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.loadingIndicator.root.visibility = View.GONE
                stopDotAnimation()
            }
            .start()
    }

    private fun startDotAnimation() {
        stopDotAnimation()
        updateLoadingText()

        dotAnimator = ObjectAnimator.ofFloat(binding.loadingIndicator.dotProgress, "rotation", 0f, 360f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopDotAnimation() {
        dotAnimator?.cancel()
        dotAnimator = null
    }

    private fun updateLoadingText() {
        val dots = ".".repeat(currentDots + 1)
        binding.loadingIndicator.loadingText.text = "주변 병원을 검색하고 있습니다$dots"
        currentDots = (currentDots + 1) % maxDots
    }
}