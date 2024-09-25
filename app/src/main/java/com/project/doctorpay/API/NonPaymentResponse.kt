package com.project.doctorpay.api

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(name = "response", strict = false)
data class NonPaymentResponse(
    @field:Element(name = "header")
    var header: Header? = null,

    @field:Element(name = "body")
    var body: Body? = null
)

@Root(name = "header", strict = false)
data class Header(
    @field:Element(name = "resultCode")
    var resultCode: String? = null,

    @field:Element(name = "resultMsg")
    var resultMsg: String? = null
)

@Root(name = "body", strict = false)
data class Body(
    @field:ElementList(name = "items", inline = true)
    var items: List<Item>? = null,

    @field:Element(name = "numOfRows")
    var numOfRows: Int = 0,

    @field:Element(name = "pageNo")
    var pageNo: Int = 0,

    @field:Element(name = "totalCount")
    var totalCount: Int = 0
)

@Root(name = "item", strict = false)
data class Item(
    @field:Element(name = "yadmNm")
    var hospitalName: String? = null,

    @field:Element(name = "clCdNm")
    var hospitalType: String? = null,

    @field:Element(name = "sidoCdNm")
    var cityName: String? = null,

    @field:Element(name = "sgguCdNm")
    var districtName: String? = null,

    @field:Element(name = "npayKorNm")
    var itemName: String? = null,

    @field:Element(name = "minPrc")
    var minPrice: Int = 0,

    @field:Element(name = "maxPrc")
    var maxPrice: Int = 0
)