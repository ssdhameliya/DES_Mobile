package org.dse.mobile.core.api

import org.dse.mobile.core.model.*

interface ExistingErpApi {
    suspend fun health(): ApiResult<HealthResponse>
    suspend fun runtimeHealth(): ApiResult<RuntimeHealthResponse>
    suspend fun login(identity:String,password:String): ApiResult<LoginResponse>
    suspend fun completeMfa(challengeId:String,otp:String): ApiResult<LoginResponse>
    suspend fun resendMfa(challengeId:String): ApiResult<MfaChallengeResponse>
    suspend fun requestPasswordReset(identity:String): ApiResult<ChallengeResponse>
    suspend fun completePasswordReset(challengeId:String,otp:String,totp:String,password:String): ApiResult<OperationResponse>
    suspend fun registrationRoles(): ApiResult<List<RoleOption>>
    suspend fun registrationCaptcha(): ApiResult<CaptchaResponse>
    suspend fun requestRegistration(request:RegistrationOtpRequest): ApiResult<ChallengeResponse>
    suspend fun verifyRegistrationEmail(request:RegistrationEmailVerifyRequest): ApiResult<RegistrationMfaSetupResponse>
    suspend fun completeRegistrationMfa(registrationId:Long,otp:String): ApiResult<OperationResponse>
    suspend fun register(request:RegisterRequest): ApiResult<OperationResponse>
    suspend fun logout(): ApiResult<OperationResponse>
    suspend fun extendSession(): ApiResult<SessionExtendResponse>
    suspend fun effectivePermissions(): ApiResult<List<EffectivePermission>>
    suspend fun currentProfile(): ApiResult<UserProfile>
    suspend fun updateProfile(request:ProfileUpdate): ApiResult<UserProfile>
    suspend fun changePassword(request:ChangePasswordRequest): ApiResult<OperationResponse>

    suspend fun salesPage(page:Int=0,size:Int=25,query:String=""): ApiResult<SalesPage>
    suspend fun salesPage(page:Int,size:Int,filter:SalesFilter): ApiResult<SalesPage>
    suspend fun saleByInvoice(invoiceNo:String): ApiResult<SaleRecord>
    suspend fun nextSaleNumber(): ApiResult<NextNumber>
    suspend fun createSale(record:SaleRecord): ApiResult<SaleRecord>
    suspend fun updateSale(record:SaleRecord): ApiResult<SaleRecord>
    suspend fun deleteSale(invoiceNo:String): ApiResult<OperationResponse>
    suspend fun saleAction(invoiceNo:String,action:String,reason:String=""): ApiResult<OperationResponse>
    suspend fun duplicateSale(id:Int,user:String="Mobile"): ApiResult<TextResponse>
    suspend fun markDocumentWhatsapp(type:String,id:Int): ApiResult<OperationResponse>
    suspend fun markDocumentEmail(type:String,id:Int): ApiResult<OperationResponse>
    suspend fun sendBusinessEmail(request:BusinessEmailRequest): ApiResult<BusinessEmailResult>
    suspend fun resendBusinessEmail(request:BusinessEmailRequest): ApiResult<BusinessEmailResult>
    suspend fun emailSettings(): ApiResult<EmailSettings>
    suspend fun saveEmailSettings(request:EmailSettings): ApiResult<EmailSettings>
    suspend fun testEmail(recipient:String): ApiResult<BusinessEmailResult>
    suspend fun canonicalDocument(type:String,number:String,format:String="PDF"): ApiResult<ByteArray>

    suspend fun purchasesPage(page:Int=0,size:Int=25,query:String=""): ApiResult<PurchasePage>
    suspend fun purchasesPage(page:Int,size:Int,filter:PurchaseFilter): ApiResult<PurchasePage>
    suspend fun purchaseByInvoice(invoiceNo:String): ApiResult<PurchaseRecord>
    suspend fun nextPurchaseNumber(): ApiResult<NextNumber>
    suspend fun createPurchase(record:PurchaseRecord): ApiResult<PurchaseRecord>
    suspend fun updatePurchase(record:PurchaseRecord): ApiResult<PurchaseRecord>
    suspend fun deletePurchase(invoiceNo:String): ApiResult<OperationResponse>
    suspend fun purchaseAction(invoiceNo:String,action:String,reason:String=""): ApiResult<OperationResponse>

    suspend fun financePage(page:Int=0,size:Int=25,query:String=""): ApiResult<FinancePage>
    suspend fun financePage(page:Int,size:Int,filter:FinanceFilter): ApiResult<FinancePage>
    suspend fun financeById(id:Int): ApiResult<FinanceRecord>
    suspend fun financeMetrics(): ApiResult<FinanceMetrics>
    suspend fun nextFinanceNumber(): ApiResult<NextNumber>
    suspend fun createFinance(record:FinanceRecord): ApiResult<FinanceRecord>
    suspend fun updateFinance(record:FinanceRecord): ApiResult<FinanceRecord>
    suspend fun deleteFinance(id:Int,rowVersion:Long): ApiResult<OperationResponse>
    suspend fun stockHistory(itemCode:String): ApiResult<List<StockHistoryRow>>
    suspend fun adjustStock(request:StockAdjustmentRequest): ApiResult<OperationResponse>

    suspend fun quotationsPage(page:Int=0,size:Int=25,query:String=""): ApiResult<QuotationPage>
    suspend fun quotationsPage(page:Int,size:Int,query:String,number:String,customer:String,status:String,from:String,to:String,valid:String,salesperson:String,minAmount:String,maxAmount:String,followUp:String,source:String): ApiResult<QuotationPage>
    suspend fun quotationSources(): ApiResult<List<String>>
    suspend fun quotationById(id:Int): ApiResult<QuotationRecord>
    suspend fun quotationLines(id:Int): ApiResult<List<QuotationLine>>
    suspend fun createQuotation(request:QuotationSaveRequest): ApiResult<QuotationRecord>
    suspend fun updateQuotation(id:Int,request:QuotationSaveRequest): ApiResult<QuotationRecord>
    suspend fun deleteQuotation(id:Int): ApiResult<QuoteOk>
    suspend fun quotationAction(id:Int,action:String,user:String="Mobile"): ApiResult<QuoteText>
    suspend fun quotationNotes(id:Int,value:String): ApiResult<QuoteOk>
    suspend fun quotationSent(id:Int,channel:String): ApiResult<QuoteOk>
    suspend fun quotationFollowUp(id:Int,date:String,notes:String): ApiResult<QuoteOk>

    suspend fun returnsPage(type:String,page:Int=0,size:Int=25,query:String=""): ApiResult<ReturnPage>
    suspend fun returnsPage(type:String,page:Int,size:Int,filter:ReturnFilter): ApiResult<ReturnPage>
    suspend fun returnDetails(no:String): ApiResult<ReturnDetails>
    suspend fun returnPartyEmail(no:String): ApiResult<TextResponse>
    suspend fun returnedQuantities(type:String,invoiceNo:String): ApiResult<Map<String,Double>>
    suspend fun returnSettlements(type:String): ApiResult<List<ReturnSettlement>>
    suspend fun createReturn(request:ReturnCreateRequest): ApiResult<ReturnCreated>
    suspend fun updateReturn(no:String,field:String,value:String): ApiResult<ReturnOk>
    suspend fun approveReturn(no:String): ApiResult<ReturnOk>
    suspend fun rejectReturn(no:String,reason:String): ApiResult<ReturnOk>
    suspend fun returnRefunds(no:String): ApiResult<List<ReturnRefundRow>>
    suspend fun recordReturnRefund(no:String,request:ReturnRefundCreateRequest): ApiResult<ReturnRefundCreated>
    suspend fun cancelReturn(no:String,sales:Boolean): ApiResult<ReturnOk>
    suspend fun deleteReturn(no:String,sales:Boolean): ApiResult<ReturnOk>

    suspend fun payments(type:String,id:Int): ApiResult<List<PaymentRow>>
    suspend fun recordPayment(request:PaymentRequest): ApiResult<PaymentCreated>
    suspend fun updatePayment(paymentId:Int,request:PaymentUpdateRequest): ApiResult<OperationResponse>

    suspend fun bankBatches(page:Int=0,size:Int=50,query:String=""): ApiResult<BankBatchPage>
    suspend fun bankBatches(page:Int,size:Int,filter:BankBatchFilter): ApiResult<BankBatchPage>
    suspend fun bankTransactions(batchId:Long,page:Int=0,size:Int=50,query:String=""): ApiResult<BankTransactionPage>
    suspend fun bankTransactions(batchId:Long,page:Int,size:Int,filter:BankTransactionFilter): ApiResult<BankTransactionPage>
    suspend fun bankCandidates(transactionId:Long): ApiResult<List<BankCandidate>>
    suspend fun bankSuggest(transactionId:Long): ApiResult<List<BankCandidate>>
    suspend fun bankMatch(transactionId:Long,user:String,allocations:List<BankAllocationRequest>,note:String=""): ApiResult<BankOperationResult>
    suspend fun bankExpense(transactionId:Long,user:String,category:String,accountName:String,paymentMode:String,notes:String): ApiResult<BankOperationResult>
    suspend fun bankEntry(transactionId:Long,user:String,accountName:String,paymentMode:String,notes:String): ApiResult<BankOperationResult>
    suspend fun bankIgnore(transactionId:Long,user:String,note:String): ApiResult<BankOperationResult>
    suspend fun bankReview(transactionId:Long,user:String,note:String): ApiResult<BankOperationResult>
    suspend fun bankNote(transactionId:Long,user:String,note:String): ApiResult<BankOperationResult>
    suspend fun bankReverse(transactionId:Long,user:String): ApiResult<BankOperationResult>
    suspend fun bankBulkExpense(request:BankBulkExpenseRequest): ApiResult<BankBulkResult>
    suspend fun bankBulkEntry(request:BankBulkEntryRequest): ApiResult<BankBulkResult>
    suspend fun deleteBankBatch(batchId:Long,user:String): ApiResult<BankBatchDeleteResult>
    suspend fun bankAudit(transactionId:Long): ApiResult<List<BankAudit>>
    suspend fun bankSource(batchId:Long): ApiResult<BankSource>

    suspend fun searchParties(type:String,q:String="",limit:Int=40): ApiResult<List<MasterParty>>
    suspend fun listParties(type:String): ApiResult<List<MasterParty>>
    suspend fun nextPartyCode(type:String): ApiResult<NextCodeResponse>
    suspend fun searchItems(q:String="",limit:Int=40): ApiResult<List<MasterItem>>
    suspend fun listItems(): ApiResult<List<MasterItem>>
    suspend fun nextItemCode(): ApiResult<NextCodeResponse>
    suspend fun salesEntryBootstrap(): ApiResult<SalesEntryBootstrap>
    suspend fun lookupValuesByCode(code:String): ApiResult<List<String>>
    suspend fun lookupsByCode(code:String): ApiResult<List<LookupImportDto>>
    suspend fun lookupCategories(): ApiResult<List<CategoryImportDto>>
    suspend fun referenceFormats(): ApiResult<ReferenceFormatsResponse>
    suspend fun createParty(party:MasterParty): ApiResult<MasterParty>
    suspend fun updateParty(party:MasterParty): ApiResult<MasterParty>
    suspend fun deleteParty(id:Int,rowVersion:Long): ApiResult<OperationResponse>
    suspend fun createItem(item:MasterItem): ApiResult<MasterItem>
    suspend fun updateItem(item:MasterItem): ApiResult<MasterItem>
    suspend fun deleteItem(code:String,rowVersion:Long): ApiResult<OperationResponse>
    suspend fun createLookup(lookup:LookupImportDto): ApiResult<LookupImportDto>
    suspend fun updateLookup(lookup:LookupImportDto): ApiResult<LookupImportDto>
    suspend fun setLookupActive(id:Int,active:Boolean,rowVersion:Long): ApiResult<LookupImportDto>
    suspend fun deleteLookup(id:Int,rowVersion:Long): ApiResult<OperationResponse>
    suspend fun nextLookupCode(type:String): ApiResult<NextCodeResponse>
    suspend fun upsertCategory(category:CategoryUpsertRequest): ApiResult<CategoryImportDto>
    suspend fun addCategory(name:String): ApiResult<CategoryImportDto>
    suspend fun renameCategory(oldName:String,newName:String,rowVersion:Long): ApiResult<CategoryImportDto>
    suspend fun setCategoryActive(name:String,active:Boolean,rowVersion:Long): ApiResult<CategoryImportDto>
    suspend fun deleteCategory(name:String,rowVersion:Long): ApiResult<OperationResponse>

    suspend fun importBankStatement(request:BankImportRequest): ApiResult<BankImportResult>
    suspend fun importPurchaseRecon(request:PurchaseReconImportRequest): ApiResult<PurchaseReconImportResult>

    suspend fun purchaseReconPage(page:Int=0,size:Int=25,q:String="",status:String=""): ApiResult<PurchaseReconPage>
    suspend fun purchaseReconRecord(id:Int): ApiResult<PurchaseReconRecord>
    suspend fun savePurchaseRecon(request:PurchaseReconSave): ApiResult<PurchaseReconRecord>
    suspend fun updatePurchaseRecon(id:Int,request:PurchaseReconSave): ApiResult<PurchaseReconRecord>
    suspend fun deletePurchaseRecon(id:Int): ApiResult<OperationResponse>
    suspend fun purchaseReconSuppliers(q:String="",limit:Int=40): ApiResult<List<PurchaseReconSupplier>>
    suspend fun createPurchaseReconSupplier(request:PurchaseReconSupplierSave): ApiResult<PurchaseReconSupplier>
    suspend fun updatePurchaseReconSupplier(id:Int,request:PurchaseReconSupplierSave): ApiResult<PurchaseReconSupplier>
    suspend fun deletePurchaseReconSupplier(id:Int): ApiResult<OperationResponse>
    suspend fun purchaseReconBankLinks(id:Int): ApiResult<List<PurchaseReconBankLink>>
    suspend fun purchaseReconMetrics(): ApiResult<PurchaseReconMetrics>

    suspend fun insightDashboard(period:String="This Month"): ApiResult<InsightDashboardBundle>
    suspend fun shellCounts(): ApiResult<ShellCounts>
    suspend fun reportFilters(): ApiResult<ReportFilters>
    suspend fun reminders(): ApiResult<List<ReminderRecord>>
    suspend fun createReminder(reminder:ReminderRecord): ApiResult<ReminderRecord>
    suspend fun updateReminder(id:Long,reminder:ReminderRecord): ApiResult<ReminderRecord>
    suspend fun setReminderStatus(id:Long,status:String,snoozedUntil:String?=null): ApiResult<OperationResponse>
    suspend fun deleteReminder(id:Long): ApiResult<OperationResponse>
    suspend fun notifications(limit:Int=50): ApiResult<List<InsightNotification>>
    suspend fun createNotification(request:NotificationCreate): ApiResult<InsightNotification>
    suspend fun markNotificationRead(id:Long): ApiResult<OperationResponse>
    suspend fun markNotificationUnread(id:Long): ApiResult<OperationResponse>
    suspend fun markAllNotificationsRead(): ApiResult<OperationResponse>
    suspend fun deleteNotification(id:Long): ApiResult<OperationResponse>
    suspend fun clearNotifications(): ApiResult<OperationResponse>
    suspend fun notificationPreferences(): ApiResult<NotificationPreferences>
    suspend fun saveNotificationPreferences(request:NotificationPreferences): ApiResult<NotificationPreferences>
    suspend fun reports(from:String,to:String,reportType:String="All Reports",party:String="",item:String="",salesperson:String=""): ApiResult<ReportBundle>
    suspend fun globalSearch(q:String): ApiResult<List<GlobalSearchRow>>
    suspend fun resolveRecord(moduleKey:String,reference:String): ApiResult<ResolvedRecord>

    suspend fun savedViews(screen:String,userId:Int?=null): ApiResult<List<SavedView>>
    suspend fun saveView(request:SavedViewSave): ApiResult<OperationResponse>
    suspend fun communications(): ApiResult<List<CommunicationRow>>
    suspend fun activity(type:String,id:Int): ApiResult<List<ActivityRow>>
    suspend fun logCommunication(request:CommunicationRequest): ApiResult<OperationResponse>

    suspend fun recordAudit(type:String,id:Long): ApiResult<List<AuditEventRow>>
    suspend fun globalAudit(page:Int=0,size:Int=50,filter:AuditFilter=AuditFilter()): ApiResult<AuditGlobalPage>

    suspend fun customer360Summary(id:Int): ApiResult<Customer360Summary>
    suspend fun customer360Contacts(id:Int): ApiResult<List<Party360ContactRow>>
    suspend fun saveCustomer360Contact(id:Int,request:Party360ContactSave): ApiResult<Party360ContactRow>
    suspend fun deleteCustomer360Contact(id:Int,contactId:Long,rowVersion:Long): ApiResult<OperationResponse>
    suspend fun customer360Quotations(id:Int): ApiResult<List<Party360QuotationRow>>
    suspend fun customer360Invoices(id:Int): ApiResult<List<Party360InvoiceRow>>
    suspend fun customer360Payments(id:Int): ApiResult<List<Party360PaymentRow>>
    suspend fun customer360Notes(id:Int): ApiResult<List<Party360NoteRow>>
    suspend fun saveCustomer360Note(id:Int,request:Party360NoteSave): ApiResult<Party360NoteRow>
    suspend fun deleteCustomer360Note(id:Int,noteId:Long,rowVersion:Long): ApiResult<OperationResponse>

    suspend fun supplier360Summary(id:Int): ApiResult<Supplier360Summary>
    suspend fun supplier360Purchases(id:Int): ApiResult<List<Party360InvoiceRow>>
    suspend fun supplier360Payments(id:Int): ApiResult<List<Party360PaymentRow>>
    suspend fun supplier360Contacts(id:Int): ApiResult<List<Party360ContactRow>>
    suspend fun saveSupplier360Contact(id:Int,request:Party360ContactSave): ApiResult<Party360ContactRow>
    suspend fun deleteSupplier360Contact(id:Int,contactId:Long,rowVersion:Long): ApiResult<OperationResponse>
    suspend fun supplier360Notes(id:Int): ApiResult<List<Party360NoteRow>>
    suspend fun saveSupplier360Note(id:Int,request:Party360NoteSave): ApiResult<Party360NoteRow>
    suspend fun deleteSupplier360Note(id:Int,noteId:Long,rowVersion:Long): ApiResult<OperationResponse>

    suspend fun setting(key:String,defaultValue:String=""): ApiResult<TextResponse>
    suspend fun saveSetting(key:String,value:String): ApiResult<OperationResponse>
    suspend fun saveSettings(values:Map<String,String>): ApiResult<OperationResponse>
    suspend fun storageStatus(): ApiResult<StorageStatus>
    suspend fun storageCleanup(dryRun:Boolean=true): ApiResult<StorageCleanupResult>
    suspend fun setupStatus(): ApiResult<SetupStatus>
    suspend fun setupBootstrap(request:SetupBootstrapRequest): ApiResult<SetupBootstrapResponse>
    suspend fun latestUpdate(includePrerelease:Boolean=false): ApiResult<UpdateReleaseView>
    suspend fun updateReleases(includePrerelease:Boolean=false,limit:Int=20): ApiResult<List<UpdateReleaseView>>
    suspend fun serverResources(type:String): ApiResult<List<ServerResourceMeta>>
    suspend fun serverResourceFile(type:String,key:String): ApiResult<ByteArray>
    suspend fun putServerResource(type:String,key:String,filename:String,contentType:String,data:ByteArray,expectedChecksum:String=""): ApiResult<ServerResourceMeta>
    suspend fun deleteServerResource(type:String,key:String): ApiResult<OperationResponse>
    suspend fun documentAttachments(type:String,id:Int): ApiResult<List<AttachmentMeta>>
    suspend fun addDocumentAttachment(type:String,id:Int,filename:String,data:ByteArray): ApiResult<AttachmentMeta>
    suspend fun documentAttachmentFile(type:String,id:Int,attachmentId:Long): ApiResult<ByteArray>
    suspend fun deleteDocumentAttachment(type:String,id:Int,attachmentId:Long): ApiResult<OperationResponse>
    suspend fun uploadReturnAttachment(no:String,filename:String,data:ByteArray): ApiResult<TextResponse>
    suspend fun returnAttachmentFile(no:String): ApiResult<ByteArray>
    suspend fun deleteReturnAttachment(no:String): ApiResult<OperationResponse>
    suspend fun uploadPaymentAttachment(paymentId:Int,filename:String,data:ByteArray): ApiResult<TextResponse>
    suspend fun paymentAttachmentFile(paymentId:Int): ApiResult<ByteArray>
    suspend fun deletePaymentAttachment(paymentId:Int): ApiResult<OperationResponse>
    suspend fun uploadRefundAttachment(refundId:Int,filename:String,data:ByteArray): ApiResult<TextResponse>
    suspend fun refundAttachmentFile(refundId:Int): ApiResult<ByteArray>
    suspend fun deleteRefundAttachment(refundId:Int): ApiResult<OperationResponse>

    suspend fun adminUsers(): ApiResult<List<AdminUser>>
    suspend fun saveAdminUser(request:AdminUserSaveRequest): ApiResult<AdminUser>
    suspend fun updateAdminUser(id:Int,request:AdminUserSaveRequest): ApiResult<AdminUser>
    suspend fun deleteAdminUser(id:Int): ApiResult<OperationResponse>
    suspend fun setAdminUserLocked(id:Int,locked:Boolean): ApiResult<OperationResponse>
    suspend fun resetAdminPassword(id:Int,password:String): ApiResult<OperationResponse>
    suspend fun adminRoles(): ApiResult<List<AdminRole>>
    suspend fun saveAdminRole(request:AdminRoleSaveRequest): ApiResult<AdminRole>
    suspend fun updateAdminRole(id:Int,request:AdminRoleSaveRequest): ApiResult<AdminRole>
    suspend fun deleteAdminRole(id:Int): ApiResult<OperationResponse>
    suspend fun adminPermissions(role:String): ApiResult<List<AdminPermission>>
    suspend fun adminPermissionSet(role:String): ApiResult<AdminPermissionSet>
    suspend fun saveAdminPermissions(request:AdminPermissionSaveRequest): ApiResult<OperationResponse>
    suspend fun registrationRole(): ApiResult<RegistrationRoleDto>
    suspend fun saveRegistrationRole(role:String): ApiResult<RegistrationRoleDto>
    suspend fun registrationRequests(status:String="PENDING_ADMIN_APPROVAL"): ApiResult<List<RegistrationRequestRow>>
    suspend fun approveRegistration(id:Long,request:RegistrationDecisionRequest): ApiResult<OperationResponse>
    suspend fun rejectRegistration(id:Long,request:RegistrationDecisionRequest): ApiResult<OperationResponse>
}

interface MobileApiV1 {
    suspend fun dashboard(): ApiResult<DashboardSnapshot>
    suspend fun sync(sinceCursor:Long): ApiResult<SyncPage>
    suspend fun shipping(page:Int=0,size:Int=25): ApiResult<List<ShippingRecord>>
}
