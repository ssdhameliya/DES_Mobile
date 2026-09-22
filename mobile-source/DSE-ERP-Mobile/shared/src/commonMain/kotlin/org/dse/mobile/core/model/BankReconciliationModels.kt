package org.dse.mobile.core.model
import kotlinx.serialization.Serializable

@Serializable data class BankBatch(val id:Long=0,val bankName:String="",val bankAccount:String="",val accountHolder:String="",val statementFrom:String="",val statementTo:String="",val currency:String="INR",val transactionCount:Int=0,val totalDebit:Double=0.0,val totalCredit:Double=0.0,val reconciledCount:Int=0,val reconciliationPercent:Double=0.0,val status:String="",val sourceFileName:String="",val importedAt:String="")
@Serializable data class BankBatchPage(val rows:List<BankBatch> = emptyList(),val page:Int=0,val size:Int=50,val totalRows:Long=0,val totalPages:Int=0)
@Serializable data class BankAllocation(val allocationId:Long?=null,val targetType:String="",val targetId:Int?=null,val documentNo:String="",val allocatedAmount:Double=0.0,val roundingAdjustment:Double=0.0,val paymentRecordId:Int?=null,val financeEntryId:Int?=null)
@Serializable data class BankTransaction(val id:Long=0,val importId:Long=0,val sourceRowNumber:Int?=null,val transactionTimestamp:String="",val transactionDate:String="",val valueDate:String="",val description:String="",val reference:String="",val debit:Double=0.0,val credit:Double=0.0,val balance:Double=0.0,val status:String="",val suggestedMatchType:String?=null,val suggestedMatchId:Int?=null,val suggestedConfidence:Double?=null,val matchLink:String?=null,val financeEntryId:Int?=null,val linkedTargetType:String?=null,val linkedTargetId:Int?=null,val linkedDocumentNo:String?=null,val linkedAllocations:List<BankAllocation> = emptyList(),val rowVersion:Long=0)
@Serializable data class BankMetrics(val total:Int=0,val unmatched:Int=0,val suggested:Int=0,val matched:Int=0,val expenses:Int=0,val ignored:Int=0,val review:Int=0,val totalCredits:Double=0.0,val totalDebits:Double=0.0,val reconciled:Int=0,val reconciledPercent:Double=0.0,val batchStatus:String="")
@Serializable data class BankTransactionPage(val rows:List<BankTransaction> = emptyList(),val metrics:BankMetrics?=null,val page:Int=0,val size:Int=50,val totalRows:Long=0,val totalPages:Int=0)
@Serializable data class BankAllocationRequest(val targetType:String,val targetId:Int,val amount:Double)
@Serializable data class BankMatchRequest(val user:String,val allocations:List<BankAllocationRequest>)
@Serializable data class BankExpenseRequest(val category:String,val accountName:String,val paymentMode:String,val notes:String,val billPath:String="",val user:String)
@Serializable data class BankEntryRequest(val accountName:String,val paymentMode:String,val notes:String,val user:String)
@Serializable data class BankIgnoreRequest(val note:String,val user:String)
@Serializable data class BankNoteRequest(val note:String,val user:String,val rowVersion:Long=0)
@Serializable data class BankOperationResult(val success:Boolean=false,val message:String="",val status:String="",val financeEntryId:Int?=null)
@Serializable data class BankBatchDeleteResult(val success:Boolean=false,val message:String="",val deletedBatchId:Long=0,val deletedTransactions:Int=0,val reversedTransactions:Int=0)
