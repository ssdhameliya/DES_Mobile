package org.dse.mobile.core.model

import kotlinx.serialization.Serializable

// Register filter contracts mirror the server 10.0.26 query parameters.
data class SalesFilter(
    val q:String="", val invoice:String="", val customer:String="", val from:String="", val to:String="",
    val paymentStatus:String="", val due:String="", val mail:String="", val whatsapp:String="",
    val invoiceType:String="", val documentStatus:String="", val returnStatus:String="", val minAmount:Double?=null, val maxAmount:Double?=null,
)
data class PurchaseFilter(
    val q:String="", val supplier:String="", val from:String="", val to:String="",
    val paymentStatus:String="", val due:String="", val mail:String="", val documentStatus:String="", val returnStatus:String="",
)
data class FinanceFilter(val q:String="", val mode:String="", val period:String="", val type:String="")
data class ReturnFilter(val q:String="", val party:String="", val status:String="", val from:String="", val to:String="")
data class BankBatchFilter(val q:String="", val account:String="", val status:String="", val fromDate:String="", val toDate:String="")
data class BankTransactionFilter(val q:String="", val status:String="", val direction:String="ALL", val fromDate:String="", val toDate:String="")

@Serializable data class NextCodeResponse(val code:String="")
@Serializable data class ExistsResponse(val exists:Boolean=false)
@Serializable data class ReferenceFormatsResponse(val formats:Map<String,String> = emptyMap())
@Serializable data class RenameCategoryRequest(val oldName:String="",val newName:String="")

@Serializable data class StockHistoryRow(val date:String="",val type:String="",val quantity:Double=0.0,val reason:String="",val reference:String="",val user:String="")
@Serializable data class StockAdjustmentRequest(val itemCode:String="",val type:String="",val quantity:Double=0.0,val reason:String="",val referenceNo:String="",val createdBy:String="Mobile")

@Serializable data class ReminderStatusRequest(val status:String="OPEN",val snoozedUntil:String?=null)
@Serializable data class NotificationCreate(val title:String="",val message:String="",val severity:String="INFO",val category:String="GENERAL",val targetFxml:String?=null,val referenceNo:String?=null,val moduleKey:String?=null,val recordId:Long?=null,val actionCode:String?=null)
@Serializable data class CountResponse(val count:Long=0)
@Serializable data class ShellCounts(val notifications:Int=0,val email:Int=0,val whatsapp:Int=0,val reminders:Int=0)

@Serializable data class AttachmentMeta(val id:Long=0,val documentType:String="",val documentId:Int=0,val fileName:String="",val createdBy:String="",val createdAt:String="")

@Serializable data class BusinessEmailRequest(val recipient:String="",val subject:String="",val body:String="",val attachmentName:String?=null,val attachmentBase64:String?=null)
@Serializable data class BusinessEmailResult(val success:Boolean=false,val message:String="")
@Serializable data class CommunicationRow(val id:Int=0,val entityType:String="",val entityId:Int=0,val documentLabel:String="",val channel:String="",val recipient:String="",val subject:String="",val status:String="",val errorMessage:String="",val createdBy:String="",val createdAt:String="")
@Serializable data class ActivityRow(val id:Long=0,val entityType:String="",val entityId:Int=0,val action:String="",val detail:String="",val createdBy:String="",val createdAt:String="")
@Serializable data class CommunicationRequest(val entityType:String="",val entityId:Int=0,val channel:String="",val recipient:String="",val subject:String="",val status:String="SENT",val errorMessage:String="",val createdBy:String="Mobile")
@Serializable data class TextResponse(val value:String="")
@Serializable data class SavedView(val name:String="",val data:String="")
@Serializable data class SavedViewSave(val userId:Int?=null,val screen:String="",val name:String="",val data:String="")
@Serializable data class ResolvedRecord(val found:Boolean=false,val moduleKey:String="",val recordId:Long?=null,val reference:String="",val targetFxml:String="")

@Serializable data class ProfileUpdate(val fullName:String="",val email:String="",val department:String="",val branch:String="",val rowVersion:Long=0)
@Serializable data class ChangePasswordRequest(val userId:Int=0,val currentPassword:String="",val password:String="")

@Serializable data class AdminUser(val id:Int=0,val username:String="",val fullName:String?=null,val email:String?=null,val role:String="",val department:String?=null,val accessLevel:String?=null,val branch:String?=null,val active:Boolean=true,val locked:Boolean=false,val mfaEnabled:Boolean=false,val lastLogin:String?=null,val rowVersion:Long=0)
@Serializable data class AdminUserSaveRequest(val id:Int?=null,val username:String="",val password:String="",val fullName:String="",val email:String="",val role:String="",val department:String="",val accessLevel:String="",val branch:String="",val active:Boolean=true,val locked:Boolean=false,val mfaEnabled:Boolean=false,val rowVersion:Long=0)
@Serializable data class AdminRole(val id:Int=0,val code:String="",val displayName:String="",val description:String?=null,val active:Boolean=true,val userCount:Long=0)
@Serializable data class AdminRoleSaveRequest(val id:Int?=null,val name:String="",val description:String="",val active:Boolean=true)
@Serializable data class AdminPermission(val id:Long=0,val module:String="",val action:String="",val description:String="",val allowed:Boolean=false)
@Serializable data class AdminPermissionSave(val id:Long=0,val allowed:Boolean=false)
@Serializable data class AdminPermissionSaveRequest(val role:String="",val permissions:List<AdminPermissionSave> = emptyList(),val rowVersion:Long=0)
@Serializable data class PasswordResetRequest(val password:String="")
@Serializable data class LockRequest(val locked:Boolean=false)
@Serializable data class AdminMfaState(val required:Boolean=false,val status:String="",val message:String="")

@Serializable data class PurchaseReconSupplier(val id:Int?=null,val reference:String="",val legalName:String="",val gstin:String="",val pan:String="",val contactPerson:String="",val phone:String="",val email:String="",val notes:String="",val status:String="ACTIVE",val source:String="",val reconCount:Long=0,val createdAt:String="",val updatedAt:String="",val rowVersion:Long=0)
@Serializable data class PurchaseReconSupplierSave(val id:Int?=null,val legalName:String="",val gstin:String="",val pan:String="",val contactPerson:String="",val phone:String="",val email:String="",val notes:String="",val status:String="ACTIVE",val rowVersion:Long=0)
@Serializable data class PurchaseReconBankLink(val allocationId:Long?=null,val statementTransactionId:Long?=null,val bankTransactionDate:String="",val bankReference:String="",val allocatedAmount:Double=0.0,val financeEntryId:Int?=null,val financeVoucherNo:String="",val createdAt:String="")
@Serializable data class PurchaseReconRecord(val id:Int?=null,val reference:String="",val supplierId:Int?=null,val supplierReference:String="",val supplierName:String="",val supplierGstin:String="",val supplierInvoiceNo:String="",val invoiceDate:String="",val financialYear:String="",val taxableValue:Double=0.0,val cgst:Double=0.0,val sgst:Double=0.0,val igst:Double=0.0,val otherAdjustment:Double=0.0,val invoiceValue:Double=0.0,val linkedAmount:Double=0.0,val balance:Double=0.0,val taxDifference:Double=0.0,val taxReviewRequired:Boolean=false,val status:String="",val source:String="",val importBatchId:Long?=null,val sourceSheet:String="",val sourceRow:Int?=null,val notes:String="",val createdAt:String="",val updatedAt:String="",val bankLinks:List<PurchaseReconBankLink> = emptyList(),val rowVersion:Long=0)
@Serializable data class PurchaseReconSave(val id:Int?=null,val supplierId:Int?=null,val supplierInvoiceNo:String="",val invoiceDate:String="",val taxableValue:Double=0.0,val cgst:Double=0.0,val sgst:Double=0.0,val igst:Double=0.0,val otherAdjustment:Double=0.0,val invoiceValue:Double=0.0,val notes:String="",val rowVersion:Long=0)
@Serializable data class PurchaseReconMetrics(val total:Long=0,val open:Long=0,val partial:Long=0,val reconciled:Long=0,val review:Long=0,val invoiceValue:Double=0.0,val linkedValue:Double=0.0,val outstandingValue:Double=0.0)
@Serializable data class PurchaseReconPage(val rows:List<PurchaseReconRecord> = emptyList(),val page:Int=0,val size:Int=25,val totalRows:Long=0,val totalPages:Int=0,val metrics:PurchaseReconMetrics=PurchaseReconMetrics())

@Serializable data class BankSource(val fileName:String="",val fingerprint:String="",val csvContent:String="")
@Serializable data class BankAudit(val id:Long?=null,val eventType:String="",val detail:String="",val previousStatus:String="",val newStatus:String="",val performedBy:String="",val createdAt:String="")

@Serializable data class CompatibilityInfo(
    val serverBaseline:String="10.0.25",
    val apiContract:String="server-10.0.25-compatible-v5",
    val minimumMobile:String="1.2.3",
    val latestMobile:String="1.2.24",
)
