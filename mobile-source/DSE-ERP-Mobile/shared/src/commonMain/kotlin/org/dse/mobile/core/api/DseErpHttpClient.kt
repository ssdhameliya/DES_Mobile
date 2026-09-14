package org.dse.mobile.core.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import org.dse.mobile.core.model.*
import org.dse.mobile.core.offline.OfflineRepository
import org.dse.mobile.core.security.MobileSecurityPolicy

class DseErpHttpClient(
    baseUrl:String,
    private val sessions:SessionStore=InMemorySessionStore(),
    engine:HttpClientEngine=platformHttpClientEngine(),
):ExistingErpApi,MobileApiV1 {
    private val root=baseUrl.trim().trimEnd('/')
    private val endpointProblem=MobileSecurityPolicy.endpointProblem(root)
    private val json=Json{ignoreUnknownKeys=true;explicitNulls=false;encodeDefaults=true;coerceInputValues=true}
    private val client=HttpClient(engine){
        expectSuccess=false
        install(ContentNegotiation){json(json)}
        install(HttpTimeout){connectTimeoutMillis=10_000;requestTimeoutMillis=30_000;socketTimeoutMillis=30_000}
    }

    override suspend fun health()=get<HealthResponse>(ExistingErpRoutes.HEALTH,false)
    override suspend fun runtimeHealth()=get<RuntimeHealthResponse>(ExistingErpRoutes.RUNTIME_HEALTH,false)
    override suspend fun login(identity:String,password:String):ApiResult<LoginResponse>{
        val r=post<LoginResponse,LoginRequest>(ExistingErpRoutes.LOGIN,LoginRequest(identity,password),false)
        if(r is ApiResult.Success&&r.value.success&&!r.value.mfaRequired)sessions.saveAccessToken(r.value.accessToken)
        return r
    }
    override suspend fun completeMfa(challengeId:String,otp:String):ApiResult<LoginResponse>{
        val r=post<LoginResponse,MfaCompleteRequest>(ExistingErpRoutes.MFA_COMPLETE,MfaCompleteRequest(challengeId,otp),false)
        if(r is ApiResult.Success&&r.value.success)sessions.saveAccessToken(r.value.accessToken)
        return r
    }
    override suspend fun resendMfa(challengeId:String)=post<MfaChallengeResponse,MfaResendRequest>(ExistingErpRoutes.MFA_RESEND,MfaResendRequest(challengeId),false)
    override suspend fun requestPasswordReset(identity:String)=post<ChallengeResponse,PasswordResetOtpRequest>(ExistingErpRoutes.PASSWORD_RESET_REQUEST,PasswordResetOtpRequest(identity.trim()),false)
    override suspend fun completePasswordReset(challengeId:String,otp:String,totp:String,password:String)=post<OperationResponse,PasswordResetCompleteRequest>(ExistingErpRoutes.PASSWORD_RESET_COMPLETE,PasswordResetCompleteRequest(challengeId,otp.trim(),totp.trim(),password),false)
    override suspend fun registrationRoles()=get<List<RoleOption>>(ExistingErpRoutes.REGISTRATION_ROLES,false)
    override suspend fun registrationCaptcha()=get<CaptchaResponse>(ExistingErpRoutes.REGISTRATION_CAPTCHA,false)
    override suspend fun requestRegistration(request:RegistrationOtpRequest)=post<ChallengeResponse,RegistrationOtpRequest>(ExistingErpRoutes.REGISTRATION_REQUEST,request,false)
    override suspend fun verifyRegistrationEmail(request:RegistrationEmailVerifyRequest)=post<RegistrationMfaSetupResponse,RegistrationEmailVerifyRequest>(ExistingErpRoutes.REGISTRATION_EMAIL_VERIFY,request,false)
    override suspend fun completeRegistrationMfa(registrationId:Long,otp:String)=post<OperationResponse,RegistrationMfaCompleteRequest>(ExistingErpRoutes.REGISTRATION_MFA_COMPLETE,RegistrationMfaCompleteRequest(registrationId,otp.trim()),false)
    override suspend fun register(request:RegisterRequest)=post<OperationResponse,RegisterRequest>(ExistingErpRoutes.REGISTER,request,false)
    override suspend fun extendSession():ApiResult<SessionExtendResponse>{
        val r=postEmpty<SessionExtendResponse>(ExistingErpRoutes.SESSION_EXTEND)
        if(r is ApiResult.Success&&r.value.success)sessions.saveAccessToken(r.value.accessToken)
        return r
    }
    override suspend fun logout():ApiResult<OperationResponse>{
        val r=postEmpty<OperationResponse>(ExistingErpRoutes.LOGOUT)
        sessions.clear()
        return r
    }
    override suspend fun effectivePermissions()=get<List<EffectivePermission>>(ExistingErpRoutes.EFFECTIVE_PERMISSIONS)
    override suspend fun currentProfile()=get<UserProfile>(ExistingErpRoutes.PROFILE)
    override suspend fun updateProfile(request:ProfileUpdate)=put<UserProfile,ProfileUpdate>(ExistingErpRoutes.PROFILE,request)
    override suspend fun changePassword(request:ChangePasswordRequest)=post<OperationResponse,ChangePasswordRequest>(ExistingErpRoutes.CHANGE_PASSWORD,request)

    override suspend fun salesPage(page:Int,size:Int,query:String)=salesPage(page,size,SalesFilter(q=query))
    override suspend fun salesPage(page:Int,size:Int,filter:SalesFilter)=cachedGet<SalesPage>(
        OfflineRepository.cacheKey("sales",page,size,filter.toString()),ExistingErpRoutes.SALES_PAGE
    ){
        parameter("page",page);parameter("size",size);parameter("q",filter.q);parameter("invoice",filter.invoice);parameter("customer",filter.customer)
        parameter("from",filter.from);parameter("to",filter.to);parameter("paymentStatus",filter.paymentStatus);parameter("due",filter.due)
        parameter("mail",filter.mail);parameter("whatsapp",filter.whatsapp);parameter("invoiceType",filter.invoiceType);parameter("documentStatus",filter.documentStatus)
        filter.minAmount?.let{parameter("minAmount",it)};filter.maxAmount?.let{parameter("maxAmount",it)}
    }
    override suspend fun saleByInvoice(invoiceNo:String)=cachedGet<SaleRecord>("cache.sale.${safeKey(invoiceNo)}",ExistingErpRoutes.SALE_BY_INVOICE){parameter("invoiceNo",invoiceNo)}
    override suspend fun nextSaleNumber()=get<NextNumber>(ExistingErpRoutes.SALES_NEXT)
    override suspend fun createSale(record:SaleRecord)=post<SaleRecord,SaleRecord>(ExistingErpRoutes.SALES,record)
    override suspend fun updateSale(record:SaleRecord)=put<SaleRecord,SaleRecord>(ExistingErpRoutes.SALES,record)
    override suspend fun deleteSale(invoiceNo:String)=delete<OperationResponse>(ExistingErpRoutes.SALES){parameter("invoiceNo",invoiceNo)}
    override suspend fun saleAction(invoiceNo:String,action:String,reason:String):ApiResult<OperationResponse>{
        val path=when(action.uppercase()){
            "CANCEL"->ExistingErpRoutes.SALES_CANCEL
            "APPROVE"->ExistingErpRoutes.SALES_APPROVE
            "REJECT"->ExistingErpRoutes.SALES_REJECT
            else->return ApiResult.NotImplemented("Unknown Sale action")
        }
        return postEmpty(path){parameter("invoiceNo",invoiceNo);if(action.equals("REJECT",true)&&reason.isNotBlank())parameter("reason",reason)}
    }
    override suspend fun duplicateSale(id:Int,user:String)=postEmpty<TextResponse>("${ExistingErpRoutes.SUPPORT}/sales/$id/duplicate"){parameter("user",user)}
    override suspend fun markDocumentWhatsapp(type:String,id:Int)=postEmpty<OperationResponse>("${ExistingErpRoutes.SUPPORT}/documents/${type.uppercase()}/$id/whatsapp")
    override suspend fun markDocumentEmail(type:String,id:Int):ApiResult<OperationResponse>{
        val path=when(type.uppercase()){
            "SALE","SALES"->"${ExistingErpRoutes.SALES}/email-sent/$id"
            "PURCHASE","PURCHASES"->"${ExistingErpRoutes.PURCHASES}/email-sent/$id"
            else->return ApiResult.NotImplemented("Email status is not supported for $type")
        }
        return postEmpty(path)
    }
    override suspend fun sendBusinessEmail(request:BusinessEmailRequest)=post<BusinessEmailResult,BusinessEmailRequest>(ExistingErpRoutes.BUSINESS_EMAIL,request)
    override suspend fun canonicalDocument(type:String,number:String,format:String)=getBytes(ExistingErpRoutes.CANONICAL_DOCUMENT){parameter("type",type);parameter("number",number);parameter("format",format)}

    override suspend fun purchasesPage(page:Int,size:Int,query:String)=purchasesPage(page,size,PurchaseFilter(q=query))
    override suspend fun purchasesPage(page:Int,size:Int,filter:PurchaseFilter)=cachedGet<PurchasePage>(
        OfflineRepository.cacheKey("purchases",page,size,filter.toString()),ExistingErpRoutes.PURCHASES_PAGE
    ){
        parameter("page",page);parameter("size",size);parameter("q",filter.q);parameter("supplier",filter.supplier);parameter("from",filter.from);parameter("to",filter.to)
        parameter("paymentStatus",filter.paymentStatus);parameter("mail",filter.mail);parameter("documentStatus",filter.documentStatus)
    }
    override suspend fun purchaseByInvoice(invoiceNo:String)=cachedGet<PurchaseRecord>("cache.purchase.${safeKey(invoiceNo)}",ExistingErpRoutes.PURCHASE_BY_INVOICE){parameter("invoiceNo",invoiceNo)}
    override suspend fun nextPurchaseNumber()=get<NextNumber>(ExistingErpRoutes.PURCHASES_NEXT)
    override suspend fun createPurchase(record:PurchaseRecord)=post<PurchaseRecord,PurchaseRecord>(ExistingErpRoutes.PURCHASES,record)
    override suspend fun updatePurchase(record:PurchaseRecord)=put<PurchaseRecord,PurchaseRecord>(ExistingErpRoutes.PURCHASES,record)
    override suspend fun deletePurchase(invoiceNo:String)=delete<OperationResponse>(ExistingErpRoutes.PURCHASES){parameter("invoiceNo",invoiceNo)}
    override suspend fun purchaseAction(invoiceNo:String,action:String,reason:String):ApiResult<OperationResponse>{
        val path=when(action.uppercase()){
            "CANCEL"->ExistingErpRoutes.PURCHASES_CANCEL
            "APPROVE"->ExistingErpRoutes.PURCHASES_APPROVE
            "REJECT"->ExistingErpRoutes.PURCHASES_REJECT
            else->return ApiResult.NotImplemented("Unknown Purchase action")
        }
        return postEmpty(path){parameter("invoiceNo",invoiceNo);if(action.equals("REJECT",true)&&reason.isNotBlank())parameter("reason",reason)}
    }

    override suspend fun financePage(page:Int,size:Int,query:String)=financePage(page,size,FinanceFilter(q=query))
    override suspend fun financePage(page:Int,size:Int,filter:FinanceFilter)=cachedGet<FinancePage>(
        OfflineRepository.cacheKey("finance",page,size,filter.toString()),ExistingErpRoutes.FINANCE_PAGE
    ){
        parameter("page",page);parameter("size",size);parameter("q",filter.q);parameter("mode",filter.mode);parameter("period",filter.period);parameter("type",filter.type)
    }
    override suspend fun financeById(id:Int)=cachedGet<FinanceRecord>("cache.finance.$id","${ExistingErpRoutes.FINANCE}/$id")
    override suspend fun financeMetrics()=cachedGet<FinanceMetrics>("cache.finance.metrics",ExistingErpRoutes.FINANCE_METRICS)
    override suspend fun nextFinanceNumber()=get<NextNumber>(ExistingErpRoutes.FINANCE_NEXT)
    override suspend fun createFinance(record:FinanceRecord)=post<FinanceRecord,FinanceRecord>(ExistingErpRoutes.FINANCE,record)
    override suspend fun updateFinance(record:FinanceRecord)=put<FinanceRecord,FinanceRecord>(ExistingErpRoutes.FINANCE,record)
    override suspend fun deleteFinance(id:Int,rowVersion:Long)=delete<OperationResponse>("${ExistingErpRoutes.FINANCE}/$id"){parameter("rowVersion",rowVersion)}
    override suspend fun stockHistory(itemCode:String)=cachedGet<List<StockHistoryRow>>("cache.stock.${safeKey(itemCode)}",ExistingErpRoutes.STOCK_HISTORY){parameter("itemCode",itemCode)}
    override suspend fun adjustStock(request:StockAdjustmentRequest)=post<OperationResponse,StockAdjustmentRequest>(ExistingErpRoutes.STOCK_ADJUST,request)

    override suspend fun quotationsPage(page:Int,size:Int,query:String)=quotationsPage(page,size,query,"","","","","","","","","","","")
    override suspend fun quotationsPage(page:Int,size:Int,query:String,number:String,customer:String,status:String,from:String,to:String,valid:String,salesperson:String,minAmount:String,maxAmount:String,followUp:String,source:String)=cachedGet<QuotationPage>(
        OfflineRepository.cacheKey("quotations",page,size,listOf(query,number,customer,status,from,to,valid,salesperson,minAmount,maxAmount,followUp,source).joinToString("|")),ExistingErpRoutes.QUOTATIONS_PAGE
    ){
        parameter("page",page);parameter("size",size);parameter("q",query);parameter("number",number);parameter("customer",customer);parameter("status",status);parameter("from",from);parameter("to",to)
        parameter("valid",valid);parameter("salesperson",salesperson);parameter("minAmount",minAmount);parameter("maxAmount",maxAmount);parameter("followUp",followUp);parameter("source",source)
    }
    override suspend fun quotationSources()=cachedGet<List<String>>("cache.quotation.sources",ExistingErpRoutes.QUOTATION_SOURCES)
    override suspend fun quotationById(id:Int)=cachedGet<QuotationRecord>("cache.quotation.$id","${ExistingErpRoutes.QUOTATIONS}/$id")
    override suspend fun quotationLines(id:Int)=cachedGet<List<QuotationLine>>("cache.quotation.$id.lines","${ExistingErpRoutes.QUOTATIONS}/$id/lines")
    override suspend fun createQuotation(request:QuotationSaveRequest)=post<QuotationRecord,QuotationSaveRequest>(ExistingErpRoutes.QUOTATIONS,request)
    override suspend fun updateQuotation(id:Int,request:QuotationSaveRequest)=put<QuotationRecord,QuotationSaveRequest>("${ExistingErpRoutes.QUOTATIONS}/$id",request)
    override suspend fun deleteQuotation(id:Int)=delete<QuoteOk>("${ExistingErpRoutes.QUOTATIONS}/$id")
    override suspend fun quotationAction(id:Int,action:String,user:String):ApiResult<QuoteText>{
        val a=action.lowercase();if(a!="convert"&&a!="duplicate")return ApiResult.NotImplemented("Unknown quotation action")
        return postEmpty("${ExistingErpRoutes.QUOTATIONS}/$id/$a"){parameter("user",user)}
    }
    override suspend fun quotationNotes(id:Int,value:String)=put<QuoteOk,TextResponse>("${ExistingErpRoutes.QUOTATIONS}/$id/notes",TextResponse(value))
    override suspend fun quotationSent(id:Int,channel:String)=postEmpty<QuoteOk>("${ExistingErpRoutes.QUOTATIONS}/$id/sent"){parameter("channel",channel)}
    override suspend fun quotationFollowUp(id:Int,date:String,notes:String)=post<QuoteOk,QuotationFollowUp>("${ExistingErpRoutes.QUOTATIONS}/$id/follow-up",QuotationFollowUp(date,notes))

    override suspend fun returnsPage(type:String,page:Int,size:Int,query:String)=cachedGet<ReturnPage>(OfflineRepository.cacheKey("returns-${type.lowercase()}",page,size,query),ExistingErpRoutes.RETURNS_PAGE){parameter("type",type);parameter("page",page);parameter("size",size);parameter("q",query)}
    override suspend fun returnsPage(type:String,page:Int,size:Int,filter:ReturnFilter)=cachedGet<ReturnPage>(OfflineRepository.cacheKey("returns-${type.lowercase()}",page,size,listOf(filter.q,filter.party,filter.status,filter.from,filter.to).joinToString("|")),ExistingErpRoutes.RETURNS_PAGE){parameter("type",type);parameter("page",page);parameter("size",size);parameter("q",filter.q);parameter("party",filter.party);parameter("status",filter.status);parameter("from",filter.from);parameter("to",filter.to)}
    override suspend fun returnDetails(no:String)=cachedGet<ReturnDetails>("cache.return.${safeKey(no)}","${ExistingErpRoutes.RETURNS}/${pathSegment(no)}")
    override suspend fun returnedQuantities(type:String,invoiceNo:String)=get<Map<String,Double>>("${ExistingErpRoutes.RETURNS}/returned"){parameter("type",type);parameter("invoice",invoiceNo)}
    override suspend fun returnSettlements(type:String)=cachedGet<List<ReturnSettlement>>("cache.return.settlements.${type.uppercase()}","${ExistingErpRoutes.RETURNS}/settlements"){parameter("type",type)}
    override suspend fun createReturn(request:ReturnCreateRequest)=post<ReturnCreated,ReturnCreateRequest>(ExistingErpRoutes.RETURNS,request)
    override suspend fun updateReturn(no:String,field:String,value:String)=put<ReturnOk,ReturnUpdateRequest>("${ExistingErpRoutes.RETURNS}/${pathSegment(no)}",ReturnUpdateRequest(field,value))
    override suspend fun approveReturn(no:String)=postEmpty<ReturnOk>("${ExistingErpRoutes.RETURNS}/${pathSegment(no)}/approve")
    override suspend fun rejectReturn(no:String,reason:String)=postEmpty<ReturnOk>("${ExistingErpRoutes.RETURNS}/${pathSegment(no)}/reject"){if(reason.isNotBlank())parameter("reason",reason)}
    override suspend fun returnRefunds(no:String)=cachedGet<List<ReturnRefundRow>>("cache.return.refunds.${safeKey(no)}","${ExistingErpRoutes.RETURNS}/${pathSegment(no)}/refunds")
    override suspend fun recordReturnRefund(no:String,request:ReturnRefundCreateRequest)=post<ReturnRefundCreated,ReturnRefundCreateRequest>("${ExistingErpRoutes.RETURNS}/${pathSegment(no)}/refunds",request)
    override suspend fun cancelReturn(no:String,sales:Boolean)=postEmpty<ReturnOk>("${ExistingErpRoutes.RETURNS}/${pathSegment(no)}/cancel"){parameter("sales",sales)}
    override suspend fun deleteReturn(no:String,sales:Boolean)=delete<ReturnOk>("${ExistingErpRoutes.RETURNS}/${pathSegment(no)}"){parameter("sales",sales)}

    override suspend fun payments(type:String,id:Int)=cachedGet<List<PaymentRow>>("cache.payments.${type.uppercase()}.$id",ExistingErpRoutes.PAYMENTS){parameter("type",type.uppercase());parameter("id",id)}
    override suspend fun recordPayment(request:PaymentRequest)=post<PaymentCreated,PaymentRequest>(ExistingErpRoutes.PAYMENTS_WITH_ID,request)
    override suspend fun updatePayment(paymentId:Int,request:PaymentUpdateRequest)=put<OperationResponse,PaymentUpdateRequest>("${ExistingErpRoutes.PAYMENTS}/$paymentId",request)

    override suspend fun bankBatches(page:Int,size:Int,query:String)=bankBatches(page,size,BankBatchFilter(q=query))
    override suspend fun bankBatches(page:Int,size:Int,filter:BankBatchFilter)=cachedGet<BankBatchPage>(OfflineRepository.cacheKey("bank-batches",page,size,filter.toString()),"${ExistingErpRoutes.BANK_STATEMENTS}/imports/page"){
        parameter("page",page);parameter("size",size);parameter("q",filter.q);parameter("account",filter.account);parameter("status",filter.status);parameter("fromDate",filter.fromDate);parameter("toDate",filter.toDate)
    }
    override suspend fun bankTransactions(batchId:Long,page:Int,size:Int,query:String)=bankTransactions(batchId,page,size,BankTransactionFilter(q=query))
    override suspend fun bankTransactions(batchId:Long,page:Int,size:Int,filter:BankTransactionFilter)=cachedGet<BankTransactionPage>(OfflineRepository.cacheKey("bank-$batchId",page,size,filter.toString()),"${ExistingErpRoutes.BANK_STATEMENTS}/imports/$batchId/page"){
        parameter("page",page);parameter("size",size);parameter("q",filter.q);parameter("status",filter.status);parameter("direction",filter.direction);parameter("fromDate",filter.fromDate);parameter("toDate",filter.toDate)
    }
    override suspend fun bankCandidates(transactionId:Long)=get<List<BankCandidate>>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/candidates")
    override suspend fun bankSuggest(transactionId:Long)=postEmpty<List<BankCandidate>>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/suggest")
    override suspend fun bankMatch(transactionId:Long,user:String,allocations:List<BankAllocationRequest>,note:String):ApiResult<BankOperationResult>{
        // Server v10.0.5 stores Match and Note through separate endpoints. Keep the financial operation atomic here; UI exposes Note as an explicit independent audit action.
        return post<BankOperationResult,BankMatchRequest>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/match",BankMatchRequest(user,allocations))
    }
    override suspend fun bankExpense(transactionId:Long,user:String,category:String,accountName:String,paymentMode:String,notes:String)=post<BankOperationResult,BankExpenseRequest>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/expense",BankExpenseRequest(category,accountName,paymentMode,notes,"",user))
    override suspend fun bankEntry(transactionId:Long,user:String,accountName:String,paymentMode:String,notes:String)=post<BankOperationResult,BankEntryRequest>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/bank-entry",BankEntryRequest(accountName,paymentMode,notes,user))
    override suspend fun bankIgnore(transactionId:Long,user:String,note:String)=post<BankOperationResult,BankIgnoreRequest>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/ignore",BankIgnoreRequest(note,user))
    override suspend fun bankReview(transactionId:Long,user:String,note:String)=post<BankOperationResult,BankNoteRequest>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/review",BankNoteRequest(note,user))
    override suspend fun bankNote(transactionId:Long,user:String,note:String)=post<BankOperationResult,BankNoteRequest>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/note",BankNoteRequest(note,user))
    override suspend fun bankReverse(transactionId:Long,user:String)=postEmpty<BankOperationResult>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/reverse"){parameter("user",user)}
    override suspend fun bankBulkExpense(request:BankBulkExpenseRequest)=post<BankBulkResult,BankBulkExpenseRequest>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/bulk/expense",request)
    override suspend fun bankBulkEntry(request:BankBulkEntryRequest)=post<BankBulkResult,BankBulkEntryRequest>("${ExistingErpRoutes.BANK_STATEMENTS}/transactions/bulk/bank-entry",request)
    override suspend fun deleteBankBatch(batchId:Long,user:String)=delete<BankBatchDeleteResult>("${ExistingErpRoutes.BANK_STATEMENTS}/imports/$batchId"){parameter("confirmation","DELETE");parameter("user",user)}
    override suspend fun bankAudit(transactionId:Long)=cachedGet<List<BankAudit>>("cache.bank.audit.$transactionId","${ExistingErpRoutes.BANK_STATEMENTS}/transactions/$transactionId/audit")
    override suspend fun bankSource(batchId:Long)=get<BankSource>("${ExistingErpRoutes.BANK_STATEMENTS}/imports/$batchId/source")

    override suspend fun searchParties(type:String,q:String,limit:Int)=cachedGet<List<MasterParty>>("cache.parties.${type.uppercase()}.$limit.${safeKey(q)}",ExistingErpRoutes.PARTIES_SEARCH){parameter("type",type);parameter("q",q);parameter("limit",limit.coerceIn(1,500))}
    override suspend fun listParties(type:String)=cachedGet<List<MasterParty>>("cache.parties.${type.uppercase()}.all",ExistingErpRoutes.PARTIES){parameter("type",type)}
    override suspend fun nextPartyCode(type:String)=get<NextCodeResponse>(ExistingErpRoutes.PARTIES_NEXT){parameter("type",type)}
    override suspend fun searchItems(q:String,limit:Int)=cachedGet<List<MasterItem>>("cache.items.$limit.${safeKey(q)}",ExistingErpRoutes.ITEMS_SEARCH){parameter("q",q);parameter("limit",limit.coerceIn(1,500))}
    override suspend fun listItems()=cachedGet<List<MasterItem>>("cache.items.all",ExistingErpRoutes.ITEMS)
    override suspend fun nextItemCode()=get<NextCodeResponse>(ExistingErpRoutes.ITEMS_NEXT)
    override suspend fun salesEntryBootstrap()=cachedGet<SalesEntryBootstrap>("cache.sales.bootstrap",ExistingErpRoutes.SALES_ENTRY_BOOTSTRAP)
    override suspend fun lookupValuesByCode(code:String):ApiResult<List<String>> = mapValues(get<LookupValuesResponse>(ExistingErpRoutes.LOOKUP_VALUES_BY_CODE){parameter("code",code)}){it.values}
    override suspend fun lookupsByCode(code:String)=cachedGet<List<LookupImportDto>>("cache.lookups.${code.uppercase()}",ExistingErpRoutes.LOOKUPS_BY_CODE){parameter("code",code)}
    override suspend fun lookupCategories()=cachedGet<List<CategoryImportDto>>("cache.lookup.categories",ExistingErpRoutes.CATEGORIES)
    override suspend fun referenceFormats()=cachedGet<ReferenceFormatsResponse>("cache.reference.formats",ExistingErpRoutes.REFERENCE_FORMATS)
    override suspend fun createParty(party:MasterParty)=post<MasterParty,MasterParty>(ExistingErpRoutes.PARTIES,party)
    override suspend fun updateParty(party:MasterParty)=put<MasterParty,MasterParty>(ExistingErpRoutes.PARTIES,party)
    override suspend fun deleteParty(id:Int,rowVersion:Long)=delete<OperationResponse>("${ExistingErpRoutes.PARTIES}/$id"){parameter("rowVersion",rowVersion)}
    override suspend fun createItem(item:MasterItem)=post<MasterItem,MasterItem>(ExistingErpRoutes.ITEMS,item)
    override suspend fun updateItem(item:MasterItem)=put<MasterItem,MasterItem>(ExistingErpRoutes.ITEMS,item)
    override suspend fun deleteItem(code:String,rowVersion:Long)=delete<OperationResponse>("${ExistingErpRoutes.ITEMS}/${pathSegment(code)}"){parameter("rowVersion",rowVersion)}
    override suspend fun createLookup(lookup:LookupImportDto)=post<LookupImportDto,LookupImportDto>(ExistingErpRoutes.LOOKUPS,lookup)
    override suspend fun updateLookup(lookup:LookupImportDto)=put<LookupImportDto,LookupImportDto>(ExistingErpRoutes.LOOKUPS,lookup)
    override suspend fun setLookupActive(id:Int,active:Boolean,rowVersion:Long)=putEmpty<LookupImportDto>("${ExistingErpRoutes.LOOKUPS}/$id/active"){parameter("active",active);parameter("rowVersion",rowVersion)}
    override suspend fun deleteLookup(id:Int,rowVersion:Long)=delete<OperationResponse>("${ExistingErpRoutes.LOOKUPS}/$id"){parameter("rowVersion",rowVersion)}
    override suspend fun nextLookupCode(type:String)=get<NextCodeResponse>(ExistingErpRoutes.LOOKUPS_NEXT){parameter("type",type)}
    override suspend fun upsertCategory(category:CategoryUpsertRequest)=put<CategoryImportDto,CategoryUpsertRequest>(ExistingErpRoutes.CATEGORIES_UPSERT,category)
    override suspend fun addCategory(name:String)=postEmpty<CategoryImportDto>(ExistingErpRoutes.CATEGORIES){parameter("name",name)}
    override suspend fun renameCategory(oldName:String,newName:String,rowVersion:Long)=put<CategoryImportDto,RenameCategoryRequest>("${ExistingErpRoutes.CATEGORIES}/rename",RenameCategoryRequest(oldName,newName)){parameter("rowVersion",rowVersion)}
    override suspend fun setCategoryActive(name:String,active:Boolean,rowVersion:Long)=putEmpty<CategoryImportDto>("${ExistingErpRoutes.CATEGORIES}/active"){parameter("name",name);parameter("active",active);parameter("rowVersion",rowVersion)}
    override suspend fun deleteCategory(name:String,rowVersion:Long)=delete<OperationResponse>(ExistingErpRoutes.CATEGORIES){parameter("name",name);parameter("rowVersion",rowVersion)}

    override suspend fun importBankStatement(request:BankImportRequest)=post<BankImportResult,BankImportRequest>("${ExistingErpRoutes.BANK_STATEMENTS}/imports",request)
    override suspend fun importPurchaseRecon(request:PurchaseReconImportRequest)=post<PurchaseReconImportResult,PurchaseReconImportRequest>("${ExistingErpRoutes.PURCHASE_RECON}/imports",request)

    override suspend fun purchaseReconPage(page:Int,size:Int,q:String,status:String)=cachedGet<PurchaseReconPage>(OfflineRepository.cacheKey("purchase-recon",page,size,"$q|$status"),"${ExistingErpRoutes.PURCHASE_RECON}/records/page"){parameter("page",page);parameter("size",size);parameter("q",q);parameter("status",status)}
    override suspend fun purchaseReconRecord(id:Int)=cachedGet<PurchaseReconRecord>("cache.purchase.recon.$id","${ExistingErpRoutes.PURCHASE_RECON}/records/$id")
    override suspend fun savePurchaseRecon(request:PurchaseReconSave)=post<PurchaseReconRecord,PurchaseReconSave>("${ExistingErpRoutes.PURCHASE_RECON}/records",request)
    override suspend fun updatePurchaseRecon(id:Int,request:PurchaseReconSave)=put<PurchaseReconRecord,PurchaseReconSave>("${ExistingErpRoutes.PURCHASE_RECON}/records/$id",request)
    override suspend fun deletePurchaseRecon(id:Int)=deleteNoBody("${ExistingErpRoutes.PURCHASE_RECON}/records/$id")
    override suspend fun purchaseReconSuppliers(q:String,limit:Int)=cachedGet<List<PurchaseReconSupplier>>("cache.purchase.recon.suppliers.$limit.${safeKey(q)}","${ExistingErpRoutes.PURCHASE_RECON}/suppliers"){parameter("q",q);parameter("limit",limit)}
    override suspend fun createPurchaseReconSupplier(request:PurchaseReconSupplierSave)=post<PurchaseReconSupplier,PurchaseReconSupplierSave>("${ExistingErpRoutes.PURCHASE_RECON}/suppliers",request)
    override suspend fun updatePurchaseReconSupplier(id:Int,request:PurchaseReconSupplierSave)=put<PurchaseReconSupplier,PurchaseReconSupplierSave>("${ExistingErpRoutes.PURCHASE_RECON}/suppliers/$id",request)
    override suspend fun deletePurchaseReconSupplier(id:Int)=deleteNoBody("${ExistingErpRoutes.PURCHASE_RECON}/suppliers/$id")

    override suspend fun insightDashboard(period:String)=cachedGet<InsightDashboardBundle>("cache.insights.dashboard.$period",ExistingErpRoutes.INSIGHTS_DASHBOARD){parameter("period",period)}
    override suspend fun reportFilters()=cachedGet<ReportFilters>("cache.report.filters",ExistingErpRoutes.REPORT_FILTERS)
    override suspend fun reminders()=cachedGet<List<ReminderRecord>>("cache.reminders",ExistingErpRoutes.REMINDERS)
    override suspend fun createReminder(reminder:ReminderRecord)=post<ReminderRecord,ReminderRecord>(ExistingErpRoutes.REMINDERS,reminder)
    override suspend fun updateReminder(id:Long,reminder:ReminderRecord)=put<ReminderRecord,ReminderRecord>("${ExistingErpRoutes.REMINDERS}/$id",reminder)
    override suspend fun setReminderStatus(id:Long,status:String,snoozedUntil:String?)=postEmpty<OperationResponse>("${ExistingErpRoutes.REMINDERS}/$id/status"){parameter("status",status);snoozedUntil?.takeIf{it.isNotBlank()}?.let{parameter("snoozedUntil",it)}}
    override suspend fun deleteReminder(id:Long)=delete<OperationResponse>("${ExistingErpRoutes.REMINDERS}/$id")
    override suspend fun notifications(limit:Int)=cachedGet<List<InsightNotification>>("cache.notifications.$limit",ExistingErpRoutes.NOTIFICATIONS){parameter("limit",limit)}
    override suspend fun createNotification(request:NotificationCreate)=post<InsightNotification,NotificationCreate>(ExistingErpRoutes.NOTIFICATIONS,request)
    override suspend fun markNotificationRead(id:Long)=postEmpty<OperationResponse>("${ExistingErpRoutes.NOTIFICATIONS}/$id/read")
    override suspend fun markNotificationUnread(id:Long)=postEmpty<OperationResponse>("${ExistingErpRoutes.NOTIFICATIONS}/$id/unread")
    override suspend fun markAllNotificationsRead()=postEmpty<OperationResponse>("${ExistingErpRoutes.NOTIFICATIONS}/read-all")
    override suspend fun deleteNotification(id:Long)=delete<OperationResponse>("${ExistingErpRoutes.NOTIFICATIONS}/$id")
    override suspend fun clearNotifications()=delete<OperationResponse>(ExistingErpRoutes.NOTIFICATIONS)
    override suspend fun reports(from:String,to:String,reportType:String,party:String,item:String,salesperson:String)=cachedGet<ReportBundle>("cache.reports.${safeKey("$from|$to|$reportType|$party|$item|$salesperson")}",ExistingErpRoutes.REPORTS){parameter("from",from);parameter("to",to);parameter("reportType",reportType);parameter("party",party);parameter("item",item);parameter("salesperson",salesperson)}
    override suspend fun globalSearch(q:String)=get<List<GlobalSearchRow>>(ExistingErpRoutes.GLOBAL_SEARCH){parameter("q",q)}
    override suspend fun resolveRecord(moduleKey:String,reference:String)=get<ResolvedRecord>(ExistingErpRoutes.RESOLVE_RECORD){parameter("moduleKey",moduleKey);parameter("reference",reference)}

    override suspend fun savedViews(screen:String,userId:Int?)=cachedGet<List<SavedView>>("cache.savedviews.${safeKey("$screen|$userId")}",ExistingErpRoutes.SAVED_VIEWS){parameter("screen",screen);userId?.let{parameter("userId",it)}}
    override suspend fun saveView(request:SavedViewSave)=post<OperationResponse,SavedViewSave>(ExistingErpRoutes.SAVED_VIEWS,request)
    override suspend fun communications()=cachedGet<List<CommunicationRow>>("cache.communications",ExistingErpRoutes.COMMUNICATIONS)
    override suspend fun activity(type:String,id:Int)=get<List<ActivityRow>>(ExistingErpRoutes.ACTIVITY){parameter("type",type.uppercase());parameter("id",id)}
    override suspend fun logCommunication(request:CommunicationRequest)=post<OperationResponse,CommunicationRequest>(ExistingErpRoutes.COMMUNICATIONS,request)
    override suspend fun documentAttachments(type:String,id:Int)=get<List<AttachmentMeta>>("${ExistingErpRoutes.SUPPORT}/documents/${type.uppercase()}/$id/attachments")
    override suspend fun addDocumentAttachment(type:String,id:Int,filename:String,data:ByteArray)=postBytes<AttachmentMeta>("${ExistingErpRoutes.SUPPORT}/documents/${type.uppercase()}/$id/attachments",data){parameter("filename",filename)}
    override suspend fun documentAttachmentFile(type:String,id:Int,attachmentId:Long)=getBytes("${ExistingErpRoutes.SUPPORT}/documents/${type.uppercase()}/$id/attachments/$attachmentId")
    override suspend fun deleteDocumentAttachment(type:String,id:Int,attachmentId:Long)=delete<OperationResponse>("${ExistingErpRoutes.SUPPORT}/documents/${type.uppercase()}/$id/attachments/$attachmentId")
    override suspend fun uploadReturnAttachment(no:String,filename:String,data:ByteArray)=putBytes<TextResponse>("${ExistingErpRoutes.SUPPORT}/returns/${pathSegment(no)}/attachment-file",data){parameter("filename",filename)}
    override suspend fun returnAttachmentFile(no:String)=getBytes("${ExistingErpRoutes.SUPPORT}/returns/${pathSegment(no)}/attachment-file")
    override suspend fun deleteReturnAttachment(no:String)=delete<OperationResponse>("${ExistingErpRoutes.SUPPORT}/returns/${pathSegment(no)}/attachment-file")
    override suspend fun uploadPaymentAttachment(paymentId:Int,filename:String,data:ByteArray)=putBytes<TextResponse>("${ExistingErpRoutes.SUPPORT}/payments/$paymentId/attachment-file",data){parameter("filename",filename)}
    override suspend fun paymentAttachmentFile(paymentId:Int)=getBytes("${ExistingErpRoutes.SUPPORT}/payments/$paymentId/attachment-file")
    override suspend fun deletePaymentAttachment(paymentId:Int)=delete<OperationResponse>("${ExistingErpRoutes.SUPPORT}/payments/$paymentId/attachment-file")
    override suspend fun uploadRefundAttachment(refundId:Int,filename:String,data:ByteArray)=putBytes<TextResponse>("${ExistingErpRoutes.SUPPORT}/return-refunds/$refundId/attachment-file",data){parameter("filename",filename)}
    override suspend fun refundAttachmentFile(refundId:Int)=getBytes("${ExistingErpRoutes.SUPPORT}/return-refunds/$refundId/attachment-file")
    override suspend fun deleteRefundAttachment(refundId:Int)=delete<OperationResponse>("${ExistingErpRoutes.SUPPORT}/return-refunds/$refundId/attachment-file")

    override suspend fun adminUsers()=cachedGet<List<AdminUser>>("cache.admin.users",ExistingErpRoutes.ADMIN_USERS)
    override suspend fun saveAdminUser(request:AdminUserSaveRequest)=post<AdminUser,AdminUserSaveRequest>(ExistingErpRoutes.ADMIN_USERS,request)
    override suspend fun updateAdminUser(id:Int,request:AdminUserSaveRequest)=put<AdminUser,AdminUserSaveRequest>("${ExistingErpRoutes.ADMIN_USERS}/$id",request)
    override suspend fun deleteAdminUser(id:Int)=delete<OperationResponse>("${ExistingErpRoutes.ADMIN_USERS}/$id")
    override suspend fun setAdminUserLocked(id:Int,locked:Boolean)=post<OperationResponse,LockRequest>("${ExistingErpRoutes.ADMIN_USERS}/$id/lock",LockRequest(locked))
    override suspend fun resetAdminPassword(id:Int,password:String)=post<OperationResponse,PasswordResetRequest>("${ExistingErpRoutes.ADMIN_USERS}/$id/password",PasswordResetRequest(password))
    override suspend fun adminRoles()=cachedGet<List<AdminRole>>("cache.admin.roles",ExistingErpRoutes.ADMIN_ROLES)
    override suspend fun saveAdminRole(request:AdminRoleSaveRequest)=post<AdminRole,AdminRoleSaveRequest>(ExistingErpRoutes.ADMIN_ROLES,request)
    override suspend fun updateAdminRole(id:Int,request:AdminRoleSaveRequest)=put<AdminRole,AdminRoleSaveRequest>("${ExistingErpRoutes.ADMIN_ROLES}/$id",request)
    override suspend fun deleteAdminRole(id:Int)=delete<OperationResponse>("${ExistingErpRoutes.ADMIN_ROLES}/$id")
    override suspend fun adminPermissions(role:String)=cachedGet<List<AdminPermission>>("cache.admin.permissions.${safeKey(role)}",ExistingErpRoutes.ADMIN_PERMISSIONS){parameter("role",role)}
    override suspend fun saveAdminPermissions(request:AdminPermissionSaveRequest)=put<OperationResponse,AdminPermissionSaveRequest>(ExistingErpRoutes.ADMIN_PERMISSIONS,request)

    override suspend fun dashboard():ApiResult<DashboardSnapshot> = mapValues(insightDashboard("Today")){bundle->
        DashboardSnapshot(
            generatedAt="",
            salesToday=bundle.snapshot.salesValue,
            receivables=bundle.snapshot.receivables,
            purchasesToday=bundle.snapshot.purchaseValue,
            payables=bundle.snapshot.payables,
            bankBalance=bundle.snapshot.cash,
            expensesToday=0.0,
            pendingReconciliation=0,
        )
    }
    override suspend fun sync(sinceCursor:Long):ApiResult<SyncPage> = ApiResult.NotImplemented("Durable mobile change feed is not part of the v10.0.5 server contract; local outbox remains explicit.")
    override suspend fun shipping(page:Int,size:Int):ApiResult<List<ShippingRecord>> = ApiResult.NotImplemented("Shipping remains Sale-owned data in v10.0.5; Live Activities are presentation-only.")

    fun close()=client.close()

    private suspend inline fun <reified T> cachedGet(key:String,path:String,authenticated:Boolean=true,crossinline configure:HttpRequestBuilder.()->Unit={}):ApiResult<T> =
        OfflineRepository.cached(key){execute<T>(false){client.get(url(path)){applyAuthentication(authenticated);configure()}}}

    private suspend inline fun <reified T> get(path:String,authenticated:Boolean=true,crossinline configure:HttpRequestBuilder.()->Unit={}):ApiResult<T> =
        execute(false){client.get(url(path)){applyAuthentication(authenticated);configure()}}

    private suspend inline fun <reified T,reified B:Any> post(path:String,body:B,authenticated:Boolean=true,crossinline configure:HttpRequestBuilder.()->Unit={}):ApiResult<T> =
        execute(true){client.post(url(path)){applyAuthentication(authenticated);contentType(ContentType.Application.Json);setBody(body);configure()}}

    private suspend inline fun <reified T,reified B:Any> put(path:String,body:B,authenticated:Boolean=true,crossinline configure:HttpRequestBuilder.()->Unit={}):ApiResult<T> =
        execute(true){client.put(url(path)){applyAuthentication(authenticated);contentType(ContentType.Application.Json);setBody(body);configure()}}

    private suspend inline fun <reified T> postEmpty(path:String,authenticated:Boolean=true,crossinline configure:HttpRequestBuilder.()->Unit={}):ApiResult<T> =
        execute(true){client.post(url(path)){applyAuthentication(authenticated);configure()}}

    private suspend inline fun <reified T> putEmpty(path:String,authenticated:Boolean=true,crossinline configure:HttpRequestBuilder.()->Unit={}):ApiResult<T> =
        execute(true){client.put(url(path)){applyAuthentication(authenticated);configure()}}

    private suspend inline fun <reified T> delete(path:String,authenticated:Boolean=true,crossinline configure:HttpRequestBuilder.()->Unit={}):ApiResult<T> =
        execute(true){client.delete(url(path)){applyAuthentication(authenticated);configure()}}

    private suspend inline fun <reified T> postBytes(path:String,data:ByteArray,crossinline configure:HttpRequestBuilder.()->Unit={}):ApiResult<T> =
        execute(true){client.post(url(path)){applyAuthentication(true);contentType(ContentType.Application.OctetStream);setBody(data);configure()}}

    private suspend inline fun <reified T> putBytes(path:String,data:ByteArray,crossinline configure:HttpRequestBuilder.()->Unit={}):ApiResult<T> =
        execute(true){client.put(url(path)){applyAuthentication(true);contentType(ContentType.Application.OctetStream);setBody(data);configure()}}

    private suspend fun getBytes(path:String,authenticated:Boolean=true,configure:HttpRequestBuilder.()->Unit={}):ApiResult<ByteArray>{
        endpointProblem?.let{return ApiResult.UnsafeEndpoint(it)}
        val response=try{client.get(url(path)){applyAuthentication(authenticated);configure()}}catch(e:CancellationException){throw e}catch(e:Exception){return ApiResult.NetworkError(e.message?:"Network error",false)}
        return when(response.status.value){
            in 200..299->try{ApiResult.Success(response.body<ByteArray>())}catch(e:CancellationException){throw e}catch(e:Exception){ApiResult.DecodeError(e.message?:"Unable to read attachment",response.status.value)}
            401->ApiResult.Unauthorized(response.safeMessage())
            403->ApiResult.Forbidden(response.safeMessage())
            404->ApiResult.NotFound(response.safeMessage())
            409->ApiResult.Conflict(response.safeMessage())
            else->ApiResult.ServerError(response.status.value,response.safeMessage())
        }
    }

    private suspend fun deleteNoBody(path:String):ApiResult<OperationResponse>{
        endpointProblem?.let{return ApiResult.UnsafeEndpoint(it)}
        val response=try{client.delete(url(path)){applyAuthentication(true)}}catch(e:CancellationException){throw e}catch(e:Exception){return ApiResult.NetworkError(e.message?:"Network error",true)}
        return when(response.status.value){
            in 200..299->ApiResult.Success(OperationResponse(true,"Deleted"))
            401->ApiResult.Unauthorized(response.safeMessage())
            403->ApiResult.Forbidden(response.safeMessage())
            404->ApiResult.NotFound(response.safeMessage())
            409->ApiResult.Conflict(response.safeMessage())
            else->ApiResult.ServerError(response.status.value,response.safeMessage())
        }
    }

    private suspend inline fun <reified T> execute(writeOperation:Boolean,request:suspend()->HttpResponse):ApiResult<T>{
        endpointProblem?.let{return ApiResult.UnsafeEndpoint(it)}
        val response=try{request()}catch(e:CancellationException){throw e}catch(e:Exception){return ApiResult.NetworkError(e.message?:"Network error",requestMayHaveReachedServer=writeOperation)}
        return when(response.status.value){
            in 200..299->try{ApiResult.Success(response.body<T>())}catch(e:CancellationException){throw e}catch(e:Exception){ApiResult.DecodeError(e.message?:"Unable to decode server response",response.status.value)}
            401->ApiResult.Unauthorized(response.safeMessage())
            403->ApiResult.Forbidden(response.safeMessage())
            404->ApiResult.NotFound(response.safeMessage())
            409->ApiResult.Conflict(response.safeMessage())
            else->ApiResult.ServerError(response.status.value,response.safeMessage())
        }
    }

    private fun HttpRequestBuilder.applyAuthentication(authenticated:Boolean){
        if(!authenticated)return
        val token=sessions.accessToken()?.takeIf{it.isNotBlank()}?:return
        bearerAuth(token)
    }
    private fun url(path:String)="$root$path"
    private suspend fun HttpResponse.safeMessage()=try{bodyAsText().take(1200)}catch(_:Exception){status.description}
    /** Collision-resistant stable cache key for configured references/codes. */
    private fun safeKey(value:String):String{
        var hash=-3750763034362895579L
        for(ch in value.trim().lowercase()){hash=hash xor ch.code.toLong();hash*=1099511628211L}
        return hash.toULong().toString(16)
    }
    private fun pathSegment(value:String)=value.trim().encodeURLPathPart()

    private inline fun <A,B> mapValues(result:ApiResult<A>,transform:(A)->B):ApiResult<B> = when(result){
        is ApiResult.Success->ApiResult.Success(transform(result.value),result.source,result.cachedAtMillis)
        is ApiResult.Unauthorized->result
        is ApiResult.Forbidden->result
        is ApiResult.Conflict->result
        is ApiResult.NotFound->result
        is ApiResult.ServerError->result
        is ApiResult.NetworkError->result
        is ApiResult.DecodeError->result
        is ApiResult.UnsafeEndpoint->result
        is ApiResult.NotImplemented->result
    }
}
