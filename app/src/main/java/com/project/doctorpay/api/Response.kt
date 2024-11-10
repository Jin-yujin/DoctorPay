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
    var header: Header? = null,

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
data class DgsbjtInfoHeader(
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
data class DgsbjtInfoBody(
    @field:Element(name = "items", required = false)
    var items: DgsbjtInfoItems? = null,

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
data class DgsbjtInfoItems(
    @field:ElementList(inline = true, entry = "item", required = false)
    var itemList: List<DgsbjtInfoItem>? = null
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
    val spcmfyCatn: String?,
    val npayKorNm: String?,
    val curAmt: String?
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



@Root(name = "item", strict = false)
data class DgsbjtInfoItem(
    @field:Element(name = "dgsbjtCd", required = false)
    var dgsbjtCd: String? = null,

    @field:Element(name = "dgsbjtCdNm", required = false)
    var dgsbjtCdNm: String? = null,

    @field:Element(name = "dgsbjtPrSdrCnt", required = false)
    var dgsbjtPrSdrCnt: String? = null,

    @field:Element(name = "cdiagDrCnt", required = false)
    var cdiagDrCnt: String? = null
)

@Root(name = "response", strict = false)
data class HospitalDetailResponse(
    @field:Element(name = "header", required = false)
    var header: Header? = null,

    @field:Element(name = "body", required = false)
    var body: HospitalDetailBody? = null
)

@Root(strict = false)
data class HospitalDetailBody(
    @field:Element(name = "items", required = false)
    var items: HospitalDetailItems? = null,

    @field:Element(name = "numOfRows", required = false)
    var numOfRows: Int = 0,

    @field:Element(name = "pageNo", required = false)
    var pageNo: Int = 0,

    @field:Element(name = "totalCount", required = false)
    var totalCount: Int = 0
)

@Root(strict = false)
data class HospitalDetailItems(
    @field:Element(name = "item", required = false)
    var item: HospitalDetailItem? = null
)

@Root(strict = false)
data class HospitalDetailItem(
    @field:Element(name = "trmtMonStart", required = false) var trmtMonStart: String? = null,
    @field:Element(name = "trmtMonEnd", required = false) var trmtMonEnd: String? = null,
    @field:Element(name = "trmtTueStart", required = false) var trmtTueStart: String? = null,
    @field:Element(name = "trmtTueEnd", required = false) var trmtTueEnd: String? = null,
    @field:Element(name = "trmtWedStart", required = false) var trmtWedStart: String? = null,
    @field:Element(name = "trmtWedEnd", required = false) var trmtWedEnd: String? = null,
    @field:Element(name = "trmtThuStart", required = false) var trmtThuStart: String? = null,
    @field:Element(name = "trmtThuEnd", required = false) var trmtThuEnd: String? = null,
    @field:Element(name = "trmtFriStart", required = false) var trmtFriStart: String? = null,
    @field:Element(name = "trmtFriEnd", required = false) var trmtFriEnd: String? = null,
    @field:Element(name = "trmtSatStart", required = false) var trmtSatStart: String? = null,
    @field:Element(name = "trmtSatEnd", required = false) var trmtSatEnd: String? = null,
    @field:Element(name = "trmtSunStart", required = false) var trmtSunStart: String? = null,
    @field:Element(name = "trmtSunEnd", required = false) var trmtSunEnd: String? = null,
    @field:Element(name = "lunchWeek", required = false) var lunchWeek: String? = null,
    @field:Element(name = "lunchSat", required = false) var lunchSat: String? = null,
    @field:Element(name = "rcvWeek", required = false) var rcvWeek: String? = null,
    @field:Element(name = "rcvSat", required = false) var rcvSat: String? = null,
    @field:Element(name = "noTrmtSun", required = false) var noTrmtSun: String? = null,
    @field:Element(name = "noTrmtHoli", required = false) var noTrmtHoli: String? = null,
    @field:Element(name = "emyDayYn", required = false) var emyDayYn: String? = null,
    @field:Element(name = "emyNgtYn", required = false) var emyNgtYn: String? = null,
    @field:Element(name = "emyDayTelNo1", required = false) var emyDayTelNo1: String? = null,
    @field:Element(name = "emyNgtTelNo1", required = false) var emyNgtTelNo1: String? = null
)
