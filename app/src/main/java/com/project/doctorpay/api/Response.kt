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
    @field:Element(name = "header", required = false)
    var header: Header? = null,

    @field:Element(name = "body", required = false)
    var body: HospitalInfoBody? = null
)

@Root(strict = false)
data class Header(
    @field:Element(name = "resultCode", required = false)
    var resultCode: String? = null,

    @field:Element(name = "resultMsg", required = false)
    var resultMsg: String? = null
)

@Root(strict = false)
data class NonPaymentBody(
    @field:ElementList(inline = true, entry = "item", required = false)
    var items: List<NonPaymentItem>? = null,

    @field:Element(name = "numOfRows", required = false)
    var numOfRows: Int = 0,

    @field:Element(name = "pageNo", required = false)
    var pageNo: Int = 0,

    @field:Element(name = "totalCount", required = false)
    var totalCount: Int = 0
)

@Root(strict = false)
data class HospitalInfoBody(
    @field:Element(name = "items", required = false)
    var items: HospitalInfoItems? = null,

    @field:Element(name = "numOfRows", required = false)
    var numOfRows: Int = 0,

    @field:Element(name = "pageNo", required = false)
    var pageNo: Int = 0,

    @field:Element(name = "totalCount", required = false)
    var totalCount: Int = 0
)

@Root(strict = false)
data class HospitalInfoItems(
    @field:ElementList(inline = true, entry = "item", required = false)
    var itemList: List<HospitalInfoItem>? = null
)

@Root(strict = false)
data class NonPaymentItem(
    @field:Element(name = "yadmNm", required = false) val yadmNm: String? = null,
    @field:Element(name = "clCd", required = false) val clCd: String? = null,
    @field:Element(name = "clCdNm", required = false) val clCdNm: String? = null,
    @field:Element(name = "adrSido", required = false) val adrSido: String? = null,
    @field:Element(name = "adrSgg", required = false) val adrSgg: String? = null,
    @field:Element(name = "itemCd", required = false) val itemCd: String? = null,
    @field:Element(name = "itemNm", required = false) val itemNm: String? = null,
    @field:Element(name = "cntrImpAmtMin", required = false) val cntrImpAmtMin: String? = null,
    @field:Element(name = "cntrImpAmtMax", required = false) val cntrImpAmtMax: String? = null,
    @field:Element(name = "spcmfyCatn", required = false) val spcmfyCatn: String? = null
)

@Root(strict = false)
data class HospitalInfoItem(
    @field:Element(name = "yadmNm", required = false) var yadmNm: String? = null,
    @field:Element(name = "addr", required = false) var addr: String? = null,
    @field:Element(name = "telno", required = false) var telno: String? = null,
    @field:Element(name = "XPos", required = false) var XPos: String? = null,
    @field:Element(name = "YPos", required = false) var YPos: String? = null,
    @field:Element(name = "clCdNm", required = false) var clCdNm: String? = null,
    @field:Element(name = "estbDd", required = false) var estbDd: String? = null,
    @field:Element(name = "drTotCnt", required = false) var drTotCnt: Int = 0,
    @field:Element(name = "sidoCdNm", required = false) var sidoCdNm: String? = null,
    @field:Element(name = "sgguCdNm", required = false) var sgguCdNm: String? = null,
    @field:Element(name = "emdongNm", required = false) var emdongNm: String? = null,
    @field:Element(name = "dgsbjtCd", required = false) var dgsbjtCd: String? = null,
    @field:Element(name = "clCd", required = false) var clCd: String? = null
)