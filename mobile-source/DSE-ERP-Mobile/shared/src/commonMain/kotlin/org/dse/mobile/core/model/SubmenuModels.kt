package org.dse.mobile.core.model
import kotlinx.serialization.Serializable

@Serializable data class QuotationRecord(val id:Int=0,val customerId:Int=0,val no:String="",val date:String="",val customer:String="",val valid:String?=null,val status:String?=null,val followUp:String?=null,val converted:String?=null,val salesperson:String?=null,val createdBy:String?=null,val amount:Double=0.0,val phone:String?=null,val email:String?=null,val gstin:String?=null,val source:String?=null,val remarks:String?=null,val discount:Double=0.0,val attachment:String?=null,val rowVersion:Long=0)
@Serializable data class QuotationLine(val code:String="",val description:String="",val quantity:Double=1.0,val rate:Double=0.0,val gst:Double=0.0,val discount:Double=0.0,val total:Double=0.0,val category:String="",val hsn:String="",val unit:String="")
@Serializable data class QuotationSaveRequest(val id:Int?=null,val date:String="",val valid:String="",val customerId:Int=0,val subtotal:Double=0.0,val discountAmount:Double=0.0,val gstAmount:Double=0.0,val total:Double=0.0,val remarks:String?=null,val followUp:String?=null,val salesperson:String?=null,val source:String?=null,val createdBy:String?=null,val lines:List<QuotationLine> = emptyList(),val rowVersion:Long=0)
@Serializable data class QuotationMetricPoint(val label:String="",val value:Double=0.0)
@Serializable data class QuotationMetrics(val totalValue:Double=0.0,val totalCount:Long=0,val pendingValue:Double=0.0,val pendingCount:Long=0,val acceptedValue:Double=0.0,val acceptedCount:Long=0,val expiredValue:Double=0.0,val expiredCount:Long=0,val conversionRate:Double=0.0,val average:Double=0.0,val trend:List<QuotationMetricPoint> = emptyList(),val statuses:List<QuotationMetricPoint> = emptyList())
@Serializable data class QuotationPage(val rows:List<QuotationRecord> = emptyList(),val page:Int=0,val size:Int=25,val totalRows:Long=0,val totalPages:Int=0,val filteredAmount:Double=0.0,val metrics:QuotationMetrics?=null,val customers:List<String> = emptyList(),val salespersons:List<String> = emptyList())
@Serializable data class QuotationFollowUp(val date:String="",val notes:String="",val rowVersion:Long=0)
@Serializable data class QuoteText(val value:String="")
@Serializable data class QuoteOk(val success:Boolean=false,val message:String="")

@Serializable data class ReturnSummary(val no:String="",val date:String="",val invoice:String="",val party:String="",val total:Double=0.0,val refund:Double=0.0,val reason:String?=null,val status:String?=null,val refundStatus:String?=null,val email:String?=null)
@Serializable data class ReturnPage(val rows:List<ReturnSummary> = emptyList(),val page:Int=0,val size:Int=25,val totalRows:Long=0,val totalPages:Int=0,val metrics:ReturnMetrics?=null,val parties:List<String> = emptyList())
@Serializable data class ReturnableLine(val sourceLineId:Long=0,val code:String="",val description:String="",val quantity:Double=0.0,val rate:Double=0.0,val discountPercent:Double=0.0,val taxPercent:Double=0.0,val lineTotal:Double=0.0,val returnedQuantity:Double=0.0,val returnedAmount:Double=0.0)
@Serializable data class ReturnCreateLine(val code:String="",val sourceLineId:Long?=null,val quantity:Double=1.0,val amount:Double=0.0,val reason:String?=null)
@Serializable data class ReturnCreateRequest(val type:String="",val invoiceNo:String="",val partyId:Int=0,val returnDate:String="",val lines:List<ReturnCreateLine> = emptyList())
@Serializable data class ReturnCreated(val returnNo:String="")
@Serializable data class ReturnUpdateRequest(val field:String="",val value:String="",val expectedRowVersion:Long=0)
@Serializable data class ReturnOk(val success:Boolean=false,val message:String="")