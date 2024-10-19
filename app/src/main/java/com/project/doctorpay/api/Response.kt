import android.os.Parcelable
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import kotlinx.parcelize.Parcelize
import retrofit2.http.Query

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

@Root(name = "response", strict = false)
data class DgsbjtInfoResponse(
    @field:Element(name = "header", required = false)
    var header: DgsbjtInfoHeader? = null,

    @field:Element(name = "body", required = false)
    var body: DgsbjtInfoBody? = null
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
data class DgsbjtInfoHeader(
    @field:Element(name = "resultCode", required = false)
    var resultCode: String? = null,

    @field:Element(name = "resultMsg", required = false)
    var resultMsg: String? = null
)

@Root(strict = false)
data class DgsbjtInfoBody(
    @field:ElementList(inline = true, entry = "item", required = false)
    var items: List<DgsbjtInfoItem>? = null,

    @field:Element(name = "numOfRows", required = false)
    var numOfRows: Int = 0,

    @field:Element(name = "pageNo", required = false)
    var pageNo: Int = 0,

    @field:Element(name = "totalCount", required = false)
    var totalCount: Int = 0
)


@Parcelize
data class NonPaymentItem(
    val yadmNm: String?,
    val clCd: String?,
    val clCdNm: String?,
    val adrSido: String?,
    val adrSgg: String?,
    val itemCd: String?,
    val itemNm: String?,
    val cntrImpAmtMin: String?,
    val cntrImpAmtMax: String?,
    val spcmfyCatn: String?
) : Parcelable

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
    @field:Element(name = "clCd", required = false) var clCd: String? = null,
    @field:Element(name = "ykiho", required = false) var ykiho: String? = null
)


@Parcelize
data class DgsbjtInfoItem(
    val dgsbjtCd: String?,
    val dgsbjtCdNm: String?,
    val dgsbjtPrSdrCnt: String?,
    val cdiagDrCnt: String?,
    val ykiho: String?
) : Parcelable