import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(name = "response", strict = false)
data class NonPaymentResponse(
    @field:Element(name = "header") var header: Header? = null,
    @field:Element(name = "body") var body: NonPaymentBody? = null
)

@Root(name = "response", strict = false)
data class HospitalInfoResponse(
    @field:Element(name = "header") var header: Header? = null,
    @field:Element(name = "body") var body: HospitalInfoBody? = null
)

data class Header(
    @field:Element(name = "resultCode") var resultCode: String? = null,
    @field:Element(name = "resultMsg") var resultMsg: String? = null
)

data class NonPaymentBody(
    @field:ElementList(inline = true, name = "items") var items: List<NonPaymentItem>? = null,
    @field:Element(name = "numOfRows") var numOfRows: Int = 0,
    @field:Element(name = "pageNo") var pageNo: Int = 0,
    @field:Element(name = "totalCount") var totalCount: Int = 0
)

data class HospitalInfoBody(
    @field:ElementList(inline = true, name = "items") var items: List<HospitalInfoItem>? = null,
    @field:Element(name = "numOfRows") var numOfRows: Int = 0,
    @field:Element(name = "pageNo") var pageNo: Int = 0,
    @field:Element(name = "totalCount") var totalCount: Int = 0
)

data class NonPaymentItem(
    @field:Element(name = "yadmNm") val yadmNm: String?,  // 요양기관명
    @field:Element(name = "clCd") val clCd: String?,    // 종별코드
    @field:Element(name = "clCdNm") val clCdNm: String?,  // 종별코드명
    @field:Element(name = "adrSido") val adrSido: String?, // 주소(시도)
    @field:Element(name = "adrSgg") val adrSgg: String?,  // 주소(시군구)
    @field:Element(name = "itemCd") val itemCd: String?,  // 진료코드
    @field:Element(name = "itemNm") val itemNm: String?,  // 진료명
    @field:Element(name = "cntrImpAmtMin") val cntrImpAmtMin: String?, // 최소 금액
    @field:Element(name = "cntrImpAmtMax") val cntrImpAmtMax: String?, // 최대 금액
    @field:Element(name = "spcmfyCatn") val spcmfyCatn: String?     // 특이사항
)

data class HospitalInfoItem(
    @field:Element(name = "yadmNm") var yadmNm: String? = null,
    @field:Element(name = "addr") var addr: String? = null,
    @field:Element(name = "telno") var telno: String? = null,
    @field:Element(name = "XPos") var XPos: String? = null,
    @field:Element(name = "YPos") var YPos: String? = null,
    @field:Element(name = "clCdNm") var clCdNm: String? = null,
    @field:Element(name = "estbDd") var estbDd: String? = null,
    @field:Element(name = "drTotCnt") var drTotCnt: Int = 0,
    @field:Element(name = "sidoCdNm", required = false) var sidoCdNm: String? = null,
    @field:Element(name = "sgguCdNm", required = false) var sgguCdNm: String? = null,
    @field:Element(name = "emdongNm", required = false) var emdongNm: String? = null,
    @field:Element(name = "dgsbjtCd", required = false) var dgsbjtCd: String? = null
)