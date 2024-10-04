package com.project.doctorpay

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, "56d42cbadaff4473519f8adf8e6317e8")
    }
}