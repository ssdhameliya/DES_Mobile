package org.dse.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable data class LookupValuesResponse(val values:List<String> = emptyList())
@Serializable data class SalesEntryBootstrap(val paymentTerms:List<String> = emptyList(),val chargeTypes:List<String> = emptyList(),val gstTypes:List<String> = emptyList(),val transporters:List<LookupImportDto> = emptyList(),val customers:List<MasterParty> = emptyList())

@Serializable data class PaymentRow(val id:Int=0,val date:String="",val reference:String="",val mode:String="",val amount:Double=0.0,val notes:String="",val receivedFrom:String="",val attachment:String="",val paymentType:String="",val rowVersion:Long=0)
@Serializable data class PaymentRequest(val documentType:String="",val documentId:Int=0,val date:String="",val amount:Double=0.0,val mode:String="",val reference:String="",val notes:String="",val receivedFrom:String="",val paymentType:String="PARTIAL",val attachment:String?=null,val createdBy:String="Mobile")
@Serializable data class PaymentCreated(val id:Int=0)
@Serializable data class PaymentUpdateRequest(val date:String="",val amount:Double=0.0,val mode:String="",val reference:String="",val notes:String="",val receivedFrom:String="",val expectedRowVersion:Long=0)

@Serializable data class ReturnDetailLine(val name:String="",val code:String="",val quantity:Double=0.0,val unit:String="",val rate:Double=0.0,val tax:Double=0.0,val amount:Double=0.0,val reason:String="")
@Serializable data class ReturnDetails(val no:String="",val date:String="",val invoice:String="",val party:String="",val type:String="",val paymentTerms:String="",val currency:String="",val createdAt:String="",val updatedAt:String="",val attachment:String="",val notes:String="",val total:Double=0.0,val refund:Double=0.0,val status:String="",val refundStatus:String="",val rowVersion:Long=0,val lines:List<ReturnDetailLine> = emptyList())
@Serializable data class ReturnSettlement(val invoiceNo:String="",val status:String="",val pendingAmount:Double=0.0,val approvedReturnAmount:Double=0.0,val settledAmount:Double=0.0,val dueDate:String="")
@Serializable data class ReturnRefundCreateRequest(val date:String="",val amount:Double=0.0,val mode:String="",val reference:String="",val bankAccount:String="",val refundedParty:String="",val notes:String="",val refundType:String="PARTIAL",val createdBy:String="Mobile")
@Serializable data class ReturnRefundRow(val id:Int=0,val date:String="",val reference:String="",val mode:String="",val bankAccount:String="",val amount:Double=0.0,val refundedParty:String="",val status:String="",val notes:String="",val attachment:String="",val refundType:String="")
@Serializable data class ReturnRefundCreated(val id:Int=0)
@Serializable data class ReturnMetrics(val total:Double=0.0,val count:Long=0,val monthAmount:Double=0.0,val monthCount:Long=0,val approvedAmount:Double=0.0,val refundAmount:Double=0.0,val average:Double=0.0)

@Serializable data class BankCandidate(val type:String="",val id:Int=0,val documentNo:String="",val partyName:String="",val documentDate:String="",val totalAmount:Double=0.0,val paidAmount:Double=0.0,val outstanding:Double=0.0,val confidence:Double=0.0)
@Serializable data class BankBulkExpenseRequest(val transactionIds:List<Long> = emptyList(),val category:String="",val accountName:String="",val paymentMode:String="",val notes:String="",val billPath:String="",val user:String="")
@Serializable data class BankBulkEntryRequest(val transactionIds:List<Long> = emptyList(),val accountName:String="",val paymentMode:String="",val notes:String="",val user:String="")
@Serializable data class BankBulkResult(val success:Boolean=false,val message:String="",val status:String="",val processed:Int=0,val financeEntryIds:List<Int> = emptyList())

@Serializable data class InsightDashboardSnapshot(val period:String="",val products:Long=0,val customers:Long=0,val invoices:Long=0,val purchases:Long=0,val lowStock:Long=0,val salesValue:Double=0.0,val purchaseValue:Double=0.0,val receivables:Double=0.0,val payables:Double=0.0,val openReceivables:Long=0,val openPayables:Long=0,val cash:Double=0.0,val openReminders:Long=0,val overdueReminders:Long=0)
@Serializable data class InsightActivity(val type:String="",val number:String="",val party:String="",val date:String="",val amount:Double=0.0)
@Serializable data class InsightNotification(val id:Long=0,val title:String="",val message:String="",val severity:String="",val category:String="",val read:Boolean=false,val targetFxml:String?=null,val referenceNo:String?=null,val moduleKey:String?=null,val recordId:Long?=null,val actionCode:String?=null,val createdAt:Long=0)
@Serializable data class InsightDashboardBundle(val snapshot:InsightDashboardSnapshot=InsightDashboardSnapshot(),val recent:List<InsightActivity> = emptyList(),val topCustomers:List<String> = emptyList(),val ageing:List<String> = emptyList(),val activities:List<InsightNotification> = emptyList())
@Serializable data class ReminderRecord(val id:Long?=null,val title:String="",val referenceNo:String="",val dueDate:String="",val priority:String="",val notes:String="",val status:String="OPEN",val createdBy:String="",val snoozedUntil:String?=null)
@Serializable data class ReportFilters(val parties:List<String> = emptyList(),val items:List<String> = emptyList(),val salespeople:List<String> = emptyList())
@Serializable data class ReportPoint(val label:String="",val value:Double=0.0)
@Serializable data class ReportRow(val number:String="",val date:String="",val party:String="",val amount:Double=0.0,val status:String="")
@Serializable data class ReportBundle(val sales:Double=0.0,val purchase:Double=0.0,val profit:Double=0.0,val receivables:Double=0.0,val stock:Double=0.0,val low:Long=0,val customers:Long=0,val customerPoints:List<ReportPoint> = emptyList(),val itemPoints:List<ReportPoint> = emptyList(),val salesRows:List<ReportRow> = emptyList(),val purchaseRows:List<ReportRow> = emptyList(),val salesPaid:Double=0.0,val payables:Double=0.0,val purchasesPaid:Double=0.0,val items:Long=0,val out:Long=0,val salesCount:Long=0,val purchaseCount:Long=0,val averageSale:Double=0.0)
@Serializable data class GlobalSearchRow(val module:String="",val moduleKey:String="",val recordId:Long?=null,val reference:String="",val description:String="",val detail:String="",val targetFxml:String="",val permission:String="")
