package org.dse.mobile.core.model

import kotlinx.serialization.Serializable

// Desktop/server 10.0.16 parity contracts. Business rules remain server-owned.
@Serializable data class AuditChangeRow(val id:Long=0,val fieldName:String="",val oldValue:String="",val newValue:String="")
@Serializable data class AuditEventRow(
    val id:Long=0,val entityType:String="",val entityId:Long=0,val referenceNo:String="",val action:String="",val category:String="",
    val detail:String="",val createdBy:String="",val createdAt:String="",val source:String="",val legacySource:String="",
    val changes:List<AuditChangeRow> = emptyList(),
)
@Serializable data class AuditGlobalPage(
    val rows:List<AuditEventRow> = emptyList(),val total:Long=0,val page:Int=0,val size:Int=50,val totalPages:Int=0,
    val businessChanges:Long=0,val financialEvents:Long=0,val documentEvents:Long=0,val communications:Long=0,
)

data class AuditFilter(
    val module:String="",val action:String="",val user:String="",val reference:String="",val query:String="",
)

@Serializable data class Party360Party(
    val id:Int=0,val code:String="",val name:String="",val contactPerson:String="",val phone:String="",val email:String="",
    val gstin:String="",val address:String="",val openingBalance:Double=0.0,val active:Boolean=true,val rowVersion:Long=0,
)
@Serializable data class Party360QuotationRow(val id:Int=0,val no:String="",val date:String="",val valid:String="",val salesperson:String="",val amount:Double=0.0,val status:String="",val followUp:String="")
@Serializable data class Party360InvoiceRow(val id:Int=0,val invoiceNo:String="",val invoiceDate:String="",val totalAmount:Double=0.0,val paidAmount:Double=0.0,val outstanding:Double=0.0,val paymentStatus:String="",val documentStatus:String="")
@Serializable data class Party360PaymentRow(val id:Int=0,val paymentDate:String="",val referenceNo:String="",val paymentMode:String="",val amount:Double=0.0,val invoiceNo:String="",val notes:String="")
@Serializable data class Party360ContactRow(
    val id:Long=0,val partyId:Int=0,val name:String="",val designation:String="",val department:String="",val mobile:String="",val email:String="",
    val primary:Boolean=false,val notes:String="",val rowVersion:Long=0,val createdBy:String="",val createdAt:String="",val updatedBy:String="",val updatedAt:String="",
)
@Serializable data class Party360ContactSave(val id:Long?=null,val name:String="",val designation:String="",val department:String="",val mobile:String="",val email:String="",val primary:Boolean=false,val notes:String="",val rowVersion:Long=0)
@Serializable data class Party360NoteRow(val id:Long=0,val partyId:Int=0,val note:String="",val createdBy:String="",val createdAt:String="",val updatedBy:String="",val updatedAt:String="",val rowVersion:Long=0)
@Serializable data class Party360NoteSave(val id:Long?=null,val note:String="",val rowVersion:Long=0)
@Serializable data class Customer360Summary(
    val customer:Party360Party=Party360Party(),val outstandingReceivable:Double=0.0,val openQuotationValue:Double=0.0,val openQuotationCount:Long=0,
    val totalSales:Double=0.0,val lastPaymentAmount:Double=0.0,val lastPaymentDate:String="",val recentQuotations:List<Party360QuotationRow> = emptyList(),
    val recentInvoices:List<Party360InvoiceRow> = emptyList(),
)
@Serializable data class Supplier360Summary(
    val supplier:Party360Party=Party360Party(),val outstandingPayable:Double=0.0,val purchaseCount:Long=0,val totalPurchases:Double=0.0,
    val lastPaymentAmount:Double=0.0,val lastPaymentDate:String="",val recentPurchases:List<Party360InvoiceRow> = emptyList(),val recentPayments:List<Party360PaymentRow> = emptyList(),
)

@Serializable data class SettingsBatch(val values:Map<String,String> = emptyMap())
@Serializable data class EmailSettings(val email:String="",val appPassword:String="",val host:String="",val port:Int?=587,val passwordConfigured:Boolean=false)
@Serializable data class EmailTestRequest(val recipient:String="")
@Serializable data class NotificationPreferences(val enabled:Boolean=true,val toasts:Boolean=true,val categories:Map<String,Boolean> = emptyMap())

@Serializable data class StoragePolicy(
    val logRetentionDays:Int=0,val reportRetentionDays:Int=0,val exportRetentionDays:Int=0,val diagnosticRetentionDays:Int=0,
    val importResultRetentionDays:Int=0,val tempRetentionDays:Int=0,val compressLogs:Boolean=false,
)
@Serializable data class StorageStatus(
    val workspace:String="",val documentsBytes:Long=0,val attachmentsBytes:Long=0,val reportsBytes:Long=0,val exportsBytes:Long=0,
    val logsBytes:Long=0,val backupsBytes:Long=0,val tempBytes:Long=0,val totalManagedBytes:Long=0,val lastCleanupAt:String="",val lastCleanupSummary:String="",
    val policy:StoragePolicy=StoragePolicy(),
)
@Serializable data class StorageCleanupResult(val dryRun:Boolean=false,val filesDeleted:Int=0,val filesCompressed:Int=0,val bytesReclaimed:Long=0,val completedAt:String="",val summary:String="")

@Serializable data class SetupStatus(val required:Boolean=false,val userCount:Long=0,val adminCount:Long=0)
@Serializable data class SetupBootstrapRequest(
    val companyName:String="",val phone:String="",val companyEmail:String="",val gstin:String="",val address:String="",
    val adminName:String="",val adminUsername:String="",val adminEmail:String="",val adminPassword:String="",
)
@Serializable data class SetupBootstrapResponse(val success:Boolean=false,val message:String="")

@Serializable data class UpdateAssetView(val id:Long=0,val name:String="",val size:Long=0,val downloadUrl:String="",val contentType:String="")
@Serializable data class UpdateReleaseView(val tagName:String="",val name:String="",val body:String="",val publishedAt:String="",val prerelease:Boolean=false,val assets:List<UpdateAssetView> = emptyList(),val htmlUrl:String="")

@Serializable data class ServerResourceMeta(val key:String="",val fileName:String="",val contentType:String="",val checksum:String="",val updatedAt:String="",val size:Long=0)

@Serializable data class AdminPermissionSet(val rowVersion:Long=0,val permissions:List<AdminPermission> = emptyList())
@Serializable data class RegistrationRoleDto(val code:String="",val displayName:String="")
@Serializable data class RegistrationRoleSaveRequest(val role:String="")
@Serializable data class RegistrationRequestRow(
    val id:Long=0,val username:String="",val fullName:String="",val email:String="",val requestedRole:String="",val emailVerified:Boolean=false,
    val mfaVerified:Boolean=false,val status:String="",val requestedAt:String="",val reviewedBy:String="",val reviewedAt:String="",val rejectionReason:String="",val rowVersion:Long=0,
)
@Serializable data class RegistrationDecisionRequest(val role:String="",val reason:String="",val rowVersion:Long=0)


@Serializable data class RegisterExportRequest(val register:String="",val format:String="XLSX",val filters:Map<String,String> = emptyMap())
