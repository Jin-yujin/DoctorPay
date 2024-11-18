package com.project.doctorpay.ui.mypage

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.project.doctorpay.R

class TermsAndPolicyFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_terms_and_policy, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTermsAndPolicy: TextView = view.findViewById(R.id.tvTermsAndPolicy)
        val termsAndPolicyText = """
            <b>1. 서비스 제공 목적</b><br>
            본 앱은 비급여 항목 및 항목별 진료비에 대한 정보를 카테고리별, 지역별로 제공하며, 사용자에게 유익한 의료 정보를 제공하기 위한 목적을 가지고 있습니다.<br><br>

            <b>2. 정보 제공의 한계</b><br>
            비급여 진료 항목과 비용 정보는 관련 의료기관 또는 제 3자로부터 제공된 자료를 기반으로 하며, 실시간으로 변동될 수 있습니다. 해당 정보는 참고용으로 제공되며, 실제 진료비용은 의료기관에서 확인해야 합니다. 본 앱은 정보의 정확성, 최신성, 완전성에 대해 보증하지 않습니다.<br><br>

            <b>3. 책임의 한계</b><br>
            본 앱에서 제공하는 비급여 항목 및 진료비 정보에 기반한 개인의 결정 또는 행동에 대해 법적 책임을 지지 않습니다. 의료 서비스에 관한 구체적인 결정은 반드시 해당 의료기관과의 상담 후 이루어져야 하며, 본 앱은 어떠한 의료 서비스에 대해서도 직접적인 관여를 하지 않습니다.<br><br>

            <b>4. 개인정보 처리 방침</b><br>
            본 앱은 개인정보 보호법을 준수하며, 사용자로부터 수집된 개인정보는 서비스 제공 목적 이외의 용도로 사용되지 않습니다. 수집된 개인정보는 다음과 같은 경우에만 사용됩니다.<br>
            • 사용자 맞춤형 서비스 제공<br>
            • 앱 기능 개선 및 사용자 편의성 증대<br>
            • 법적 요구에 따른 대응<br><br>

            <b>5. 사용자 책임</b><br>
            사용자는 앱에서 제공하는 정보를 이용하기 전에 반드시 자신의 상황에 맞는 정보인지 확인해야 합니다. 본 앱에서 제공하는 모든 정보는 단순 참고 자료로 활용해야 하며, 의료기관과의 상담 없이 해당 정보를 진료 결정에 사용해서는 안 됩니다.<br><br>

            <b>6. 서비스 변경 및 중단</b><br>
            본 앱은 사용자에게 사전 공지 없이 서비스의 일시적 또는 영구적인 변경, 중단을 할 수 있습니다. 또한 본 앱이 제공하는 정보는 사전 예고 없이 변경될 수 있습니다.<br><br>

            <b>7. 약관의 개정</b><br>
            본 약관은 언제든지 개정될 수 있으며, 개정된 약관은 앱을 통해 공지됩니다. 사용자는 약관 개정 공지 후에도 앱을 계속 사용할 경우 개정된 약관에 동의한 것으로 간주됩니다.<br>
        """.trimIndent()

        // <b> </b>: bold체
        // <br>: 띄어쓰기

        tvTermsAndPolicy.text = Html.fromHtml(termsAndPolicyText, Html.FROM_HTML_MODE_COMPACT)
    }
}