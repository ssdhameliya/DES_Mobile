@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.dse.mobile.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dse.mobile.core.api.*
import org.dse.mobile.core.model.*
import org.dse.mobile.core.offline.OfflineRepository

data class ReturnSource(val type:String,val invoiceNo:String,val partyId:Int,val partyName:String,val lines:List<DocumentLine>)
private data class LineDraft(val item:MasterItem?=null,val code:String="",val description:String="",val unit:String="",val hsn:String="",val qty:String="1",val rate:String="0",val discount:String="0",val gst:String="0",val remarks:String="")

@Composable
private fun SalesDocumentCard(record:SaleRecord,onActions:()->Unit,onClick:()->Unit){
    val outstanding=(record.totalAmount-record.paidAmount).coerceAtLeast(0.0)
    val payment=record.paymentStatus.orEmpty().ifBlank{if(outstanding<=0)"PAID" else "PENDING"}
    val accent=when{
        payment.contains("PAID",true)&&!payment.contains("PARTIAL",true)->DseSuccess
        payment.contains("PARTIAL",true)->DseWarning
        payment.contains("OVERDUE",true)->DseDanger
        outstanding>0->DseDanger
        else->DseInfo
    }
    Surface(
        shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color=MaterialTheme.colorScheme.surface,
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(.34f)),
        shadowElevation=3.dp,
        modifier=Modifier.fillMaxWidth().clickable(onClick=onClick)
    ){
        Column(Modifier.padding(horizontal=10.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                PremiumIconTile(Icons.Rounded.ReceiptLong,DseInfo,size=36.dp)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        Text(record.invoiceNo,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold)
                        DseStatus("",payment)
                    }
                    Text(record.customer?.name.orEmpty().ifBlank{"Customer not available"},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                }
                IconButton(onClick=onActions){Icon(Icons.Rounded.MoreVert,"Actions",tint=MaterialTheme.colorScheme.primary)}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                    Text(record.invoiceDate,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(!record.dueDate.isNullOrBlank())Text("Due ${record.dueDate}",style=MaterialTheme.typography.labelSmall,color=if(outstanding>0)accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)){
                    Text(money(record.totalAmount),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold)
                    Text(if(outstanding>0)"Outstanding ${money(outstanding)}" else "Paid in full",style=MaterialTheme.typography.labelMedium,color=accent,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable internal fun SalesWorkspace(api:DseErpHttpClient,p:PermissionContext,username:String,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    var page by remember{mutableIntStateOf(0)}
    var filter by remember{mutableStateOf(SalesFilter())}
    var data by remember{mutableStateOf<SalesPage?>(null)}
    var msg by remember{mutableStateOf("")}
    var refresh by remember{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<SaleRecord?>(null)}
    var editor by remember{mutableStateOf<SaleRecord?>(null)}
    var creating by remember{mutableStateOf(false)}
    var payment by remember{mutableStateOf<SaleRecord?>(null)}
    var returning by remember{mutableStateOf<SaleRecord?>(null)}
    var reject by remember{mutableStateOf<SaleRecord?>(null)}
    var confirm by remember{mutableStateOf<Pair<String,SaleRecord>?>(null)}
    var filterOpen by remember{mutableStateOf(false)}
    var email by remember{mutableStateOf<SaleRecord?>(null)}
    var actionTarget by remember{mutableStateOf<SaleRecord?>(null)}
    var timelineTarget by remember{mutableStateOf<SaleRecord?>(null)}
    var auditTarget by remember{mutableStateOf<SaleRecord?>(null)}
    var savedViews by remember{mutableStateOf<List<SavedView>>(emptyList())}
    var savedChoice by remember{mutableStateOf("")}
    var saveViewPrompt by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()

    fun loadFull(row:SaleRecord,after:(SaleRecord)->Unit){scope.launch{
        when(val x=api.saleByInvoice(row.invoiceNo)){
            is ApiResult.Success->after(x.value)
            else->msg=x.readableMessage()
        }
    }}
    suspend fun save(record:SaleRecord,create:Boolean){
        // Queue only when ERP is known unreachable before the mutation starts. Never queue an ambiguous post-send failure.
        when(val health=api.health()){
            is ApiResult.NetworkError->{
                if(create){
                    msg="ERP is offline. New Sales are kept on screen and are not queued automatically because the current ERP API does not expose an idempotency key for this create operation. Reconnect and press Save again."
                }else{
                    OfflineRepository.enqueueSale(record,false)
                    msg="Pending Local Sync — this row-versioned Sale update was not sent because ERP is offline."
                    editor=null
                }
            }
            is ApiResult.Success->{
                val result=if(create)api.createSale(record) else api.updateSale(record)
                when(result){
                    is ApiResult.Success->{msg="${if(create)"Created" else "Updated"} ${result.value.invoiceNo} • ${money(result.value.totalAmount)}";creating=false;editor=null;refresh++}
                    else->msg=result.readableMessage()
                }
            }
            else->msg=health.readableMessage()
        }
    }

    LaunchedEffect(openTarget?.moduleKey,openTarget?.reference,openTarget?.recordId,openTarget?.openActions,openTarget?.action){
        val t=openTarget
        if(t!=null&&t.moduleKey.uppercase() in setOf("SALE","SALES")){
            if(t.reference=="__CREATE__"){creating=true;onTargetConsumed()}
            else if(t.reference.isNotBlank())when(val r=api.saleByInvoice(t.reference)){
                is ApiResult.Success->{
                    when{
                        t.action==LinkedRecordAction.PAYMENT->payment=r.value
                        t.openActions->actionTarget=r.value
                        else->selected=r.value
                    }
                    onTargetConsumed()
                }
                else->{msg=r.readableMessage();onTargetConsumed()}
            }
        }
    }
    LaunchedEffect(api,refresh){savedViews=(api.savedViews("SALES_REGISTER",p.user?.id) as? ApiResult.Success)?.value.orEmpty()}
    LaunchedEffect(api,page,filter,refresh){
        when(val r=api.salesPage(page,25,filter)){
            is ApiResult.Success->{data=r.value;msg="${r.value.totalRows} sale(s) • page ${r.value.page+1}/${r.value.totalPages.coerceAtLeast(1)}${if(r.source==ApiDataSource.CACHE)" • cached" else ""}"}
            else->msg=r.readableMessage()
        }
    }
    DseRegisterShell(
        "Sales Register","Invoices, payments and receivables",
        filter.q,{filter=filter.copy(q=it);page=0},msg,{refresh++},
        if(p.can("SALES","CREATE")){{creating=true}}else null,
        kpis={data?.metrics?.let{m->Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            DseHeroKpi("Total Sales",money(m.totalSales),"${m.invoiceCount} invoice(s) in the current result",Icons.Rounded.ReceiptLong)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                FilterChip(selected=filter.paymentStatus.isBlank()&&filter.due.isBlank(),onClick={filter=filter.copy(paymentStatus="",due="");page=0},label={Text("All")})
                FilterChip(selected=filter.paymentStatus.equals("PENDING",true),onClick={filter=filter.copy(paymentStatus="PENDING",due="");page=0},label={Text("Open")})
                FilterChip(selected=filter.paymentStatus.equals("PAID",true),onClick={filter=filter.copy(paymentStatus="PAID",due="");page=0},label={Text("Paid")})
                FilterChip(selected=filter.due.equals("OVERDUE",true),onClick={filter=filter.copy(paymentStatus="",due="OVERDUE");page=0},label={Text("Overdue")})
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                DseMetricTile("Receivables",money(m.pendingBalance),Icons.Rounded.AccountBalanceWallet,Modifier.weight(1f),DseWarning)
                DseMetricTile("Overdue",money(m.overdueBalance),Icons.Rounded.WarningAmber,Modifier.weight(1f),if(m.overdueBalance>0)DseDanger else DseSuccess)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                DseMetricTile("Today",money(m.todaySales),Icons.Rounded.Today,Modifier.weight(1f),DseSuccess)
                DseMetricTile("Pending",m.pendingCount.toString(),Icons.Rounded.Schedule,Modifier.weight(1f),DseInfo)
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={filterOpen=true}){Icon(Icons.Rounded.FilterAlt,null);Spacer(Modifier.width(5.dp));Text("Filters")}
                OutlinedButton(onClick={scope.launch{msg=exportSalesRegister(api,filter,"XLSX")}}){Icon(Icons.Rounded.TableView,null);Spacer(Modifier.width(5.dp));Text("Excel")}
                OutlinedButton(onClick={scope.launch{msg=exportSalesRegister(api,filter,"PDF")}}){Icon(Icons.Rounded.PictureAsPdf,null);Spacer(Modifier.width(5.dp));Text("PDF")}

            }

        }}},
        footer={PagingControls(page,data?.totalPages?:1,data?.totalRows?:0){page=it}}
    ){
        if(data==null){
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Loading sales records…",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }else if(data?.rows.orEmpty().isEmpty()){
            DseEmptyRegisterState("No sales records match this view","Change the search or filters, then refresh.",Icons.Rounded.ReceiptLong)
        }
        data?.rows?.forEach{r->
            SwipeActionContainer(
                startActions=buildList{
                    add(SwipeAction("Open",Icons.Rounded.Visibility){loadFull(r){selected=it}})
                    if(p.can("SALES","EDIT"))add(SwipeAction("Edit",Icons.Rounded.Edit){loadFull(r){editor=it}})
                },
                endActions=listOf(SwipeAction("Actions",Icons.Rounded.MoreHoriz){actionTarget=r}),
            ){SalesDocumentCard(r,{actionTarget=r}){loadFull(r){selected=it}}}
        }
    }
    actionTarget?.let{r->
        val state=r.documentStatus.orEmpty().trim().uppercase()
        val active=state !in setOf("CANCELLED","DELETED")
        val documentOutputAllowed=canonicalBusinessDocumentAllowed(state)
        val approvalLocked=state in setOf("PENDING APPROVAL","REJECTED")
        val outstanding=(r.totalAmount-r.paidAmount).coerceAtLeast(0.0)
        val paymentState=r.paymentStatus.orEmpty().trim().uppercase()
        val fullyPaid=paymentState in setOf("PAID","SETTLED")||r.paidAmount+0.009>=r.totalAmount
        val financiallyLocked=r.paidAmount>0.009||paymentState.contains("PAID")||paymentState.contains("SETTLED")||paymentState.contains("PARTIAL")
        val canEdit=active&&p.can("SALES","EDIT")
        val canPay=active&&!approvalLocked&&outstanding>0.005&&p.can("SALES","EDIT")
        val canReturn=state=="APPROVED"&&fullyPaid&&paymentState !in setOf("RETURN PENDING","RETURN PARTIAL")&&p.can("SALES","EDIT")
        DseRecordActionSheet("Sale ${r.invoiceNo}","Desktop-style Sales actions",buildList{
            add(PremiumActionSpec("View Sale",{loadFull(r){selected=it}}))
            add(PremiumActionSpec("Activity Timeline",{timelineTarget=r},enabled=r.id!=null,disabledReason="Activity history requires a saved ERP record."))
            add(PremiumActionSpec("Record Audit",{auditTarget=r},enabled=r.id!=null,disabledReason="Audit trail requires a saved ERP record."))
            if(p.can("SALES","EDIT"))add(PremiumActionSpec("Edit Sale",{loadFull(r){editor=it}},enabled=canEdit,disabledReason=if(!active)"Cancelled or deleted Sales cannot be edited." else null))
            if(p.can("SALES","EDIT"))add(PremiumActionSpec("View / Record Payments",{loadFull(r){payment=it}},enabled=canPay,disabledReason=when{approvalLocked->"Approval must be completed before payment.";outstanding<=0.005->"This Sale is already fully paid.";!active->"Cancelled or deleted Sales cannot receive payment.";else->null}))
            if(p.can("SALES","EDIT"))add(PremiumActionSpec("Create Sales Return",{loadFull(r){returning=it}},enabled=canReturn,disabledReason=when{state!="APPROVED"->"Only approved Sales can be returned.";!fullyPaid->"The Sale must be fully paid before creating a return.";else->"A return is already pending or partial."}))
            if(p.can("SALES","VIEW")){
                add(PremiumActionSpec("Share PDF",{scope.launch{msg=shareCanonicalDocument(api,"SALES_INVOICE",r.invoiceNo,"PDF")}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Sales cannot be shared." else null))
                add(PremiumActionSpec("Excel",{scope.launch{msg=shareCanonicalDocument(api,"SALES_INVOICE",r.invoiceNo,"XLSX")}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Sales cannot be exported." else null))
                add(PremiumActionSpec("Send Email",{loadFull(r){email=it}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Sales cannot be emailed." else null))
                val hasPhone=!r.customer?.phone.isNullOrBlank()
                add(PremiumActionSpec("WhatsApp",{
                    loadFull(r){full->val phone=full.customer?.phone.orEmpty().filter{it.isDigit()||it=='+'};if(phone.isBlank())msg="Customer phone is not configured" else {platformOpenExternalUrl("https://wa.me/${phone.filter{it.isDigit()}}?text=${urlEncode("${businessName()} Sale ${full.invoiceNo} • ${money(full.totalAmount)}")}");scope.launch{full.id?.let{api.markDocumentWhatsapp("SALE",it)}}}}
                },enabled=active&&!approvalLocked&&hasPhone,disabledReason=if(!hasPhone)"Customer phone is not configured." else "WhatsApp is available after approval."))
            }
            if(p.can("SALES","CREATE"))add(PremiumActionSpec("Duplicate Sale",{scope.launch{val id=r.id?:return@launch;when(val x=api.duplicateSale(id,username)){is ApiResult.Success->{msg="Duplicated as ${x.value.value}";refresh++};else->msg=x.readableMessage()}}},enabled=r.id!=null,disabledReason="Only saved Sales can be duplicated."))
            if(p.isAdmin()){
                add(PremiumActionSpec("Approve",{scope.launch{when(val x=api.saleAction(r.invoiceNo,"approve")){is ApiResult.Success->{msg=x.value.message;refresh++};else->msg=x.readableMessage()}}},enabled=state=="PENDING APPROVAL",disabledReason="Only Sales pending approval can be approved."))
                add(PremiumActionSpec("Reject",{reject=r},destructive=true,enabled=state=="PENDING APPROVAL",disabledReason="Only Sales pending approval can be rejected."))
            }
            if(p.can("SALES","EDIT"))add(PremiumActionSpec("Cancel Sale",{confirm="cancel" to r},destructive=true,enabled=active&&!financiallyLocked,disabledReason=when{!active->"This Sale is already cancelled or deleted.";financiallyLocked->"Paid or partially paid Sales cannot be cancelled. Create a return instead.";else->null}))
            if(p.can("SALES","DELETE"))add(PremiumActionSpec("Delete Sale",{confirm="delete" to r},destructive=true,enabled=active&&!financiallyLocked,disabledReason=when{!active->"This Sale is already cancelled or deleted.";financiallyLocked->"Paid or partially paid Sales cannot be deleted. Create a return instead.";else->null}))
        },{actionTarget=null})
    }
    timelineTarget?.let{r->r.id?.let{id->ActivityTimelineSheet(api,"SALE",id,r.invoiceNo){timelineTarget=null}}?:run{timelineTarget=null}}
    auditTarget?.let{r->r.id?.let{id->RecordAuditDialog(api,"SALE",id.toLong(),r.invoiceNo){auditTarget=null}}?:run{auditTarget=null}}
    if(filterOpen) SalesFilterDialog(filter,{filterOpen=false}){filter=it;page=0;filterOpen=false}
    if(saveViewPrompt)TextPromptDialog("Save Sales Filter View","View name",onDismiss={saveViewPrompt=false}){name->scope.launch{when(val r=api.saveView(SavedViewSave(p.user?.id,"SALES_REGISTER",name,encodeSalesView(filter)))){is ApiResult.Success->{msg="Saved view created";saveViewPrompt=false;refresh++};else->msg=r.readableMessage()}}}
    selected?.let{r->SaleDetailDialog(api,r,p,{selected=null},{editor=r;selected=null},{payment=r;selected=null},{returning=r;selected=null},{email=r},{
        scope.launch{msg=shareCanonicalDocument(api,"SALES_INVOICE",r.invoiceNo,"PDF")}
    },{
        scope.launch{msg=shareCanonicalDocument(api,"SALES_INVOICE",r.invoiceNo,"XLSX")}
    },{
        val phone=r.customer?.phone.orEmpty().filter{it.isDigit()||it=='+'};if(phone.isBlank())msg="Customer phone is not configured" else {platformOpenExternalUrl("https://wa.me/${phone.filter{it.isDigit()}}?text=${urlEncode("${businessName()} Sale ${r.invoiceNo} • ${money(r.totalAmount)}")}");scope.launch{r.id?.let{api.markDocumentWhatsapp("SALE",it)}}}
    },{
        scope.launch{val id=r.id?:return@launch;when(val x=api.duplicateSale(id,username)){is ApiResult.Success->{msg="Duplicated as ${x.value.value}";selected=null;refresh++};else->msg=x.readableMessage()}}
    },{confirm="cancel" to r},{confirm="delete" to r},{scope.launch{when(val x=api.saleAction(r.invoiceNo,"approve")){is ApiResult.Success->{msg=x.value.message;selected=null;refresh++};else->msg=x.readableMessage()}}},{reject=r;selected=null})}
    if(creating)SaleEditorDialog(api,null,{creating=false}){draft->scope.launch{save(draft,true)}}
    editor?.let{current->SaleEditorDialog(api,current,{editor=null}){draft->scope.launch{save(draft,false)}}}
    payment?.let{r->PaymentDialog(api,"SALE",r.id?:0,r.invoiceNo,r.totalAmount,r.paidAmount,r.customer?.name.orEmpty(),username,{payment=null}){msg=it;payment=null;refresh++}}
    returning?.let{r->ReturnCreateDialog(api,ReturnSource("SALE",r.invoiceNo,r.customer?.id?:0,r.customer?.name.orEmpty(),r.lines),{returning=null}){msg=it;returning=null;refresh++}}
    reject?.let{r->ReasonDialog("Reject Sale","Reason",{reject=null}){reason->scope.launch{when(val x=api.saleAction(r.invoiceNo,"reject",reason)){is ApiResult.Success->{msg=x.value.message;reject=null;refresh++};else->msg=x.readableMessage()}}}}
    email?.let{r->BusinessEmailDialog(api,"SALE",r.id?:0,r.invoiceNo,r.customer?.email.orEmpty(),businessEmailBody(r.businessDocument()),username,r.businessDocument(),{email=null}){msg=it;email=null;refresh++}}
    confirm?.let{(action,r)->ConfirmDialog(if(action=="delete")"Delete Sale" else "Cancel Sale",if(action=="delete")"Delete ${r.invoiceNo}? The ERP server will refuse deletion when financial or return integrity rules do not allow it." else "Cancel ${r.invoiceNo}? The server will enforce payment and return safety rules.",if(action=="delete")"Delete" else "Cancel",action=="delete",{confirm=null}){scope.launch{val x=if(action=="delete")api.deleteSale(r.invoiceNo)else api.saleAction(r.invoiceNo,"cancel");when(x){is ApiResult.Success->{msg=x.value.message;confirm=null;refresh++};else->msg=x.readableMessage()}}}}
}

@Composable internal fun PurchaseWorkspace(api:DseErpHttpClient,p:PermissionContext,username:String,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    var page by remember{mutableIntStateOf(0)}
    var filter by remember{mutableStateOf(PurchaseFilter())}
    var data by remember{mutableStateOf<PurchasePage?>(null)}
    var msg by remember{mutableStateOf("")}
    var refresh by remember{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<PurchaseRecord?>(null)}
    var editor by remember{mutableStateOf<PurchaseRecord?>(null)}
    var duplicate by remember{mutableStateOf<PurchaseRecord?>(null)}
    var creating by remember{mutableStateOf(false)}
    var payment by remember{mutableStateOf<PurchaseRecord?>(null)}
    var returning by remember{mutableStateOf<PurchaseRecord?>(null)}
    var reject by remember{mutableStateOf<PurchaseRecord?>(null)}
    var confirm by remember{mutableStateOf<Pair<String,PurchaseRecord>?>(null)}
    var filterOpen by remember{mutableStateOf(false)}
    var email by remember{mutableStateOf<PurchaseRecord?>(null)}
    var actionTarget by remember{mutableStateOf<PurchaseRecord?>(null)}
    var timelineTarget by remember{mutableStateOf<PurchaseRecord?>(null)}
    var auditTarget by remember{mutableStateOf<PurchaseRecord?>(null)}
    val scope=rememberCoroutineScope()
    fun loadFull(row:PurchaseRecord,after:(PurchaseRecord)->Unit){scope.launch{when(val x=api.purchaseByInvoice(row.invoiceNo)){is ApiResult.Success->after(x.value);else->msg=x.readableMessage()}}}
    suspend fun save(record:PurchaseRecord,create:Boolean){
        when(val health=api.health()){
            is ApiResult.NetworkError->{if(create){msg="ERP is offline. New Purchases are kept on screen and are not queued automatically because the current ERP API does not expose an idempotency key for this create operation. Reconnect and press Save again."}else{OfflineRepository.enqueuePurchase(record,false);msg="Pending Local Sync — this row-versioned Purchase update was not sent because ERP is offline.";editor=null}}
            is ApiResult.Success->{val result=if(create)api.createPurchase(record)else api.updatePurchase(record);when(result){is ApiResult.Success->{msg="${if(create)"Created" else "Updated"} ${result.value.invoiceNo} • ${money(result.value.totalAmount,result.value.currency?.substringBefore(' ')?:"INR")}";creating=false;editor=null;refresh++};else->msg=result.readableMessage()}}
            else->msg=health.readableMessage()
        }
    }
    LaunchedEffect(openTarget?.moduleKey,openTarget?.reference,openTarget?.recordId,openTarget?.openActions,openTarget?.action){
        val t=openTarget
        if(t!=null&&t.moduleKey.uppercase() in setOf("PURCHASE","PURCHASES")){
            if(t.reference=="__CREATE__"){creating=true;onTargetConsumed()}
            else if(t.reference.isNotBlank())when(val r=api.purchaseByInvoice(t.reference)){
                is ApiResult.Success->{
                    when{
                        t.action==LinkedRecordAction.PAYMENT->payment=r.value
                        t.openActions->actionTarget=r.value
                        else->selected=r.value
                    }
                    onTargetConsumed()
                }
                else->{msg=r.readableMessage();onTargetConsumed()}
            }
        }
    }
    LaunchedEffect(api,page,filter,refresh){when(val r=api.purchasesPage(page,25,filter)){is ApiResult.Success->{data=r.value;msg="${r.value.totalRows} purchase(s) • page ${r.value.page+1}/${r.value.totalPages.coerceAtLeast(1)}${if(r.source==ApiDataSource.CACHE)" • cached" else ""}"};else->msg=r.readableMessage()}}
    DseRegisterShell("Purchase Register","Bills, suppliers and payments",filter.q,{filter=filter.copy(q=it);page=0},msg,{refresh++},if(p.can("PURCHASE","CREATE")){{creating=true}}else null,kpis={data?.metrics?.let{m->Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        DseHeroKpi("Total Purchases",money(m.totalPurchases),"${m.activeDocuments} active purchase document(s)",Icons.Rounded.ShoppingBag)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Paid",money(m.paidAmount),Icons.Rounded.Payments,Modifier.weight(1f),DseSuccess);DseMetricTile("Suppliers",m.suppliers.toString(),Icons.Rounded.LocalShipping,Modifier.weight(1f),DseInfo)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Active",m.activeDocuments.toString(),Icons.Rounded.Description,Modifier.weight(1f),DsePurple);DseMetricTile("Quantity",m.itemQuantity.toString(),Icons.Rounded.Inventory2,Modifier.weight(1f),DseWarning)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){PremiumSecondaryButton("Filters",{filterOpen=true},Modifier.weight(1f),icon=Icons.Rounded.FilterAlt);PremiumSecondaryButton("Excel",{scope.launch{msg=exportPurchaseRegister(api,filter,"XLSX")}},Modifier.weight(1f),icon=Icons.Rounded.TableView);PremiumSecondaryButton("PDF",{scope.launch{msg=exportPurchaseRegister(api,filter,"PDF")}},Modifier.weight(1f),icon=Icons.Rounded.PictureAsPdf)}
    }}},footer={PagingControls(page,data?.totalPages?:1,data?.totalRows?:0){page=it}}){
        if(data==null){
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Loading purchase records…",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }else if(data?.rows.orEmpty().isEmpty()){
            DseEmptyRegisterState("No purchase records match this view","Change the search or filters, then refresh.",Icons.Rounded.ShoppingCart)
        }
        data?.rows?.forEach{r->
            val outstanding=(r.totalAmount-r.paidAmount).coerceAtLeast(0.0)
            DseRecordCard(
                r.invoiceNo,
                r.supplier?.name.orEmpty(),
                money(r.totalAmount,r.currency?.substringBefore(' ')?:"INR"),
                listOf("Document" to r.documentStatus.orEmpty(),"Payment" to r.paymentStatus.orEmpty(),"Email" to if(r.emailSent)"SENT" else "PENDING"),
                meta="${r.invoiceDate} • Due ${r.dueDate.orEmpty()} • Outstanding ${money(outstanding,r.currency?.substringBefore(' ')?:"INR")}",
                swipeStartActions=buildList{
                    add(SwipeAction("Open",Icons.Rounded.Visibility){loadFull(r){selected=it}})
                    if(p.can("PURCHASE","EDIT"))add(SwipeAction("Edit",Icons.Rounded.Edit){loadFull(r){editor=it}})
                },
                swipeEndActions=listOf(SwipeAction("Actions",Icons.Rounded.MoreHoriz){actionTarget=r}),
                onActions={actionTarget=r},
            ){loadFull(r){selected=it}}
        }
    }
    actionTarget?.let{r->
        val state=r.documentStatus.orEmpty().trim().uppercase()
        val active=state !in setOf("CANCELLED","DELETED")
        val documentOutputAllowed=canonicalBusinessDocumentAllowed(state)
        val approvalLocked=state in setOf("PENDING APPROVAL","REJECTED")
        val outstanding=(r.totalAmount-r.paidAmount).coerceAtLeast(0.0)
        val paymentState=r.paymentStatus.orEmpty().trim().uppercase()
        val fullyPaid=paymentState in setOf("PAID","SETTLED")||r.paidAmount+0.009>=r.totalAmount
        val financiallyLocked=r.paidAmount>0.009||paymentState.contains("PAID")||paymentState.contains("SETTLED")||paymentState.contains("PARTIAL")
        val canEdit=active&&p.can("PURCHASE","EDIT")
        val canPay=active&&state!="DRAFT"&&!approvalLocked&&outstanding>0.005&&p.can("PURCHASE","EDIT")
        val canReturn=state=="APPROVED"&&fullyPaid&&paymentState !in setOf("RETURN PENDING","RETURN PARTIAL")&&p.can("PURCHASE","EDIT")
        DseRecordActionSheet("Purchase ${r.invoiceNo}","Desktop-style Purchase actions",buildList{
            add(PremiumActionSpec("View Purchase",{loadFull(r){selected=it}}))
            add(PremiumActionSpec("Activity Timeline",{timelineTarget=r},enabled=r.id!=null,disabledReason="Activity history requires a saved ERP record."))
            add(PremiumActionSpec("Record Audit",{auditTarget=r},enabled=r.id!=null,disabledReason="Audit trail requires a saved ERP record."))
            if(p.can("PURCHASE","EDIT"))add(PremiumActionSpec("Edit Purchase",{loadFull(r){editor=it}},enabled=canEdit,disabledReason=if(!active)"Cancelled or deleted Purchases cannot be edited." else null))
            if(p.can("PURCHASE","EDIT"))add(PremiumActionSpec("View / Record Payments",{loadFull(r){payment=it}},enabled=canPay,disabledReason=when{state=="DRAFT"->"Draft Purchases cannot receive payment.";approvalLocked->"Approval must be completed before payment.";outstanding<=0.005->"This Purchase is already fully paid.";!active->"Cancelled or deleted Purchases cannot receive payment.";else->null}))
            if(p.can("PURCHASE","EDIT"))add(PremiumActionSpec("Create Purchase Return",{loadFull(r){returning=it}},enabled=canReturn,disabledReason=when{state!="APPROVED"->"Only approved Purchases can be returned.";!fullyPaid->"The Purchase must be fully paid before creating a return.";else->"A return is already pending or partial."}))
            if(p.can("PURCHASE","VIEW")){
                add(PremiumActionSpec("Share PDF",{scope.launch{msg=shareCanonicalDocument(api,"PURCHASE_INVOICE",r.invoiceNo,"PDF")}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Purchases cannot be shared." else null))
                add(PremiumActionSpec("Excel",{scope.launch{msg=shareCanonicalDocument(api,"PURCHASE_INVOICE",r.invoiceNo,"XLSX")}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Purchases cannot be exported." else null))
                add(PremiumActionSpec("Send Email",{loadFull(r){email=it}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Purchases cannot be emailed." else null))
                val hasPhone=!r.supplier?.phone.isNullOrBlank()
                add(PremiumActionSpec("WhatsApp",{loadFull(r){full->val phone=full.supplier?.phone.orEmpty().filter{it.isDigit()||it=='+'};if(phone.isBlank())msg="Supplier phone is not configured" else platformOpenExternalUrl("https://wa.me/${phone.filter{it.isDigit()}}?text=${urlEncode("${businessName()} Purchase ${full.invoiceNo} • ${money(full.totalAmount,full.currency?.substringBefore(' ')?:"INR")}")}")}},enabled=active&&!approvalLocked&&hasPhone,disabledReason=if(!hasPhone)"Supplier phone is not configured." else "WhatsApp is available after approval."))
            }
            if(p.can("PURCHASE","CREATE"))add(PremiumActionSpec("Duplicate Purchase",{loadFull(r){duplicate=it}},enabled=r.id!=null,disabledReason="Only saved Purchases can be duplicated."))
            if(p.isAdmin()){
                add(PremiumActionSpec("Approve",{scope.launch{when(val x=api.purchaseAction(r.invoiceNo,"approve")){is ApiResult.Success->{msg=x.value.message;refresh++};else->msg=x.readableMessage()}}},enabled=state=="PENDING APPROVAL",disabledReason="Only Purchases pending approval can be approved."))
                add(PremiumActionSpec("Reject",{reject=r},destructive=true,enabled=state=="PENDING APPROVAL",disabledReason="Only Purchases pending approval can be rejected."))
            }
            if(p.can("PURCHASE","EDIT"))add(PremiumActionSpec("Cancel Purchase",{confirm="cancel" to r},destructive=true,enabled=active&&!financiallyLocked,disabledReason=when{!active->"This Purchase is already cancelled or deleted.";financiallyLocked->"Paid or partially paid Purchases cannot be cancelled. Create a return instead.";else->null}))
            if(p.can("PURCHASE","DELETE"))add(PremiumActionSpec("Delete Purchase",{confirm="delete" to r},destructive=true,enabled=active&&!financiallyLocked,disabledReason=when{!active->"This Purchase is already cancelled or deleted.";financiallyLocked->"Paid or partially paid Purchases cannot be deleted. Create a return instead.";else->null}))
        },{actionTarget=null})
    }
    timelineTarget?.let{r->r.id?.let{id->ActivityTimelineSheet(api,"PURCHASE",id,r.invoiceNo){timelineTarget=null}}?:run{timelineTarget=null}}
    auditTarget?.let{r->r.id?.let{id->RecordAuditDialog(api,"PURCHASE",id.toLong(),r.invoiceNo){auditTarget=null}}?:run{auditTarget=null}}
    if(filterOpen)PurchaseFilterDialog(filter,{filterOpen=false}){filter=it;page=0;filterOpen=false}
    selected?.let{r->PurchaseDetailDialog(api,r,p,{selected=null},{editor=r;selected=null},{payment=r;selected=null},{returning=r;selected=null},{email=r},{scope.launch{msg=shareCanonicalDocument(api,"PURCHASE_INVOICE",r.invoiceNo,"PDF")}},{scope.launch{msg=shareCanonicalDocument(api,"PURCHASE_INVOICE",r.invoiceNo,"XLSX")}},{val phone=r.supplier?.phone.orEmpty().filter{it.isDigit()||it=='+'};if(phone.isBlank())msg="Supplier phone is not configured" else platformOpenExternalUrl("https://wa.me/${phone.filter{it.isDigit()}}?text=${urlEncode("${businessName()} Purchase ${r.invoiceNo} • ${money(r.totalAmount,r.currency?.substringBefore(' ')?:"INR")}")}")},{duplicate=r;selected=null},{confirm="cancel" to r},{confirm="delete" to r},{scope.launch{when(val x=api.purchaseAction(r.invoiceNo,"approve")){is ApiResult.Success->{msg=x.value.message;selected=null;refresh++};else->msg=x.readableMessage()}}},{reject=r;selected=null})}
    if(creating)PurchaseEditorDialog(api,null,{creating=false}){draft->scope.launch{save(draft,true)}}
    duplicate?.let{source->PurchaseEditorDialog(api,source,{duplicate=null},createFromTemplate=true){draft->scope.launch{save(draft.copy(id=null,invoiceNo="",paidAmount=0.0,paymentStatus="PENDING",documentStatus="PENDING APPROVAL",emailSent=false,attachmentPath=null,createdAt=null,rowVersion=0),true);duplicate=null}}}
    editor?.let{current->PurchaseEditorDialog(api,current,{editor=null}){draft->scope.launch{save(draft,false)}}}
    payment?.let{r->PaymentDialog(api,"PURCHASE",r.id?:0,r.invoiceNo,r.totalAmount,r.paidAmount,r.supplier?.name.orEmpty(),username,{payment=null}){msg=it;payment=null;refresh++}}
    returning?.let{r->ReturnCreateDialog(api,ReturnSource("PURCHASE",r.invoiceNo,r.supplier?.id?:0,r.supplier?.name.orEmpty(),r.lines),{returning=null}){msg=it;returning=null;refresh++}}
    reject?.let{r->ReasonDialog("Reject Purchase","Reason",{reject=null}){reason->scope.launch{when(val x=api.purchaseAction(r.invoiceNo,"reject",reason)){is ApiResult.Success->{msg=x.value.message;reject=null;refresh++};else->msg=x.readableMessage()}}}}
    email?.let{r->BusinessEmailDialog(api,"PURCHASE",r.id?:0,r.invoiceNo,r.supplier?.email.orEmpty(),businessEmailBody(r.businessDocument()),username,r.businessDocument(),{email=null}){msg=it;email=null;refresh++}}
    confirm?.let{(action,r)->ConfirmDialog(if(action=="delete")"Delete Purchase" else "Cancel Purchase","${if(action=="delete")"Delete" else "Cancel"} ${r.invoiceNo}? The server will enforce payment, stock and return integrity.",if(action=="delete")"Delete" else "Cancel",action=="delete",{confirm=null}){scope.launch{val x=if(action=="delete")api.deletePurchase(r.invoiceNo)else api.purchaseAction(r.invoiceNo,"cancel");when(x){is ApiResult.Success->{msg=x.value.message;confirm=null;refresh++};else->msg=x.readableMessage()}}}}
}

private fun encodeSalesView(f:SalesFilter)=listOf(f.invoice,f.customer,f.from,f.to,f.paymentStatus,f.due,f.mail,f.whatsapp,f.invoiceType,f.minAmount?.toString().orEmpty(),f.maxAmount?.toString().orEmpty(),f.documentStatus,f.returnStatus).joinToString("|")
private fun decodeSalesView(data:String):SalesFilter{val x=data.split("|");if(x.size<12)return SalesFilter();return SalesFilter(invoice=x[0],customer=x[1],from=x[2],to=x[3],paymentStatus=x[4],due=x[5],mail=x[6],whatsapp=x[7],invoiceType=x[8],minAmount=x[9].toDoubleOrNull(),maxAmount=x[10].toDoubleOrNull(),documentStatus=x[11],returnStatus=x.getOrNull(12).orEmpty())}

@Composable private fun SalesFilterDialog(current:SalesFilter,onClose:()->Unit,onApply:(SalesFilter)->Unit){
    var d by remember{mutableStateOf(current)}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Sales Filters")},text={Column(Modifier.heightIn(max=620.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Invoice No",d.invoice,singleLine=true,onValue={d=d.copy(invoice=it)});DseField("Customer",d.customer,singleLine=true,onValue={d=d.copy(customer=it)});DseDateField("From",d.from,onValue={d=d.copy(from=it)});DseDateField("To",d.to,onValue={d=d.copy(to=it)});DseSelect("Payment Status",d.paymentStatus,listOf("ALL","PENDING","PARTIAL","PAID","OVERDUE"),onValue={d=d.copy(paymentStatus=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("Document Status",d.documentStatus,listOf("ALL","PENDING APPROVAL","APPROVED","COMPLETED","CANCELLED","REJECTED"),onValue={d=d.copy(documentStatus=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("Return Status",d.returnStatus,listOf("ALL","NONE","RETURN PENDING","RETURN PARTIAL","RETURNED"),onValue={d=d.copy(returnStatus=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("Due",d.due,listOf("ALL","OVERDUE","DUE SOON","NOT DUE"),onValue={d=d.copy(due=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("Email",d.mail,listOf("ALL","SENT","PENDING"),onValue={d=d.copy(mail=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("WhatsApp",d.whatsapp,listOf("ALL","SENT","PENDING"),onValue={d=d.copy(whatsapp=it.takeUnless{x->x=="ALL"}.orEmpty())});DseNumberField("Minimum Amount",d.minAmount?.toString().orEmpty(),min=0.0,onValue={d=d.copy(minAmount=it.toDoubleOrNull())});DseNumberField("Maximum Amount",d.maxAmount?.toString().orEmpty(),min=0.0,onValue={d=d.copy(maxAmount=it.toDoubleOrNull())})}},confirmButton={Button(onClick={onApply(d)}){Text("Apply")}},dismissButton={Row{TextButton(onClick={onApply(SalesFilter())}){Text("Reset")};TextButton(onClick=onClose){Text("Cancel")}}})
}

@Composable private fun PurchaseFilterDialog(current:PurchaseFilter,onClose:()->Unit,onApply:(PurchaseFilter)->Unit){
    var d by remember{mutableStateOf(current)}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Purchase Filters")},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Supplier",d.supplier,singleLine=true,onValue={d=d.copy(supplier=it)});DseDateField("From",d.from,onValue={d=d.copy(from=it)});DseDateField("To",d.to,onValue={d=d.copy(to=it)});DseSelect("Payment Status",d.paymentStatus,listOf("ALL","PENDING","PARTIAL","PAID","OVERDUE"),onValue={d=d.copy(paymentStatus=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("Document Status",d.documentStatus,listOf("ALL","PENDING APPROVAL","APPROVED","COMPLETED","CANCELLED","REJECTED"),onValue={d=d.copy(documentStatus=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("Email",d.mail,listOf("ALL","SENT","PENDING"),onValue={d=d.copy(mail=it.takeUnless{x->x=="ALL"}.orEmpty())})}},confirmButton={Button(onClick={onApply(d)}){Text("Apply")}},dismissButton={Row{TextButton(onClick={onApply(PurchaseFilter())}){Text("Reset")};TextButton(onClick=onClose){Text("Cancel")}}})
}

@Composable
private fun SaleDetailDialog(
    api:DseErpHttpClient,
    r:SaleRecord,
    p:PermissionContext,
    onClose:()->Unit,
    onEdit:()->Unit,
    onPayment:()->Unit,
    onReturn:()->Unit,
    onEmail:()->Unit,
    onPdf:()->Unit,
    onExcel:()->Unit,
    onWhatsApp:()->Unit,
    onDuplicate:()->Unit,
    onCancel:()->Unit,
    onDelete:()->Unit,
    onApprove:()->Unit,
    onReject:()->Unit,
){
    val state=r.documentStatus.orEmpty().trim().uppercase()
    val active=state !in setOf("CANCELLED","DELETED")
    val documentOutputAllowed=canonicalBusinessDocumentAllowed(state)
    val approvalLocked=state in setOf("PENDING APPROVAL","REJECTED")
    val outstanding=(r.totalAmount-r.paidAmount).coerceAtLeast(0.0)
    val paymentState=r.paymentStatus.orEmpty().trim().uppercase()
    val fullyPaid=paymentState in setOf("PAID","SETTLED")||r.paidAmount+0.009>=r.totalAmount
    val financiallyLocked=r.paidAmount>0.009||paymentState.contains("PAID")||paymentState.contains("SETTLED")||paymentState.contains("PARTIAL")
    val canEdit=active&&p.can("SALES","EDIT")
    val canPay=active&&!approvalLocked&&outstanding>0.005&&p.can("SALES","EDIT")
    val canReturn=state=="APPROVED"&&fullyPaid&&paymentState !in setOf("RETURN PENDING","RETURN PARTIAL")&&p.can("SALES","EDIT")
    DetailDialog("Sale ${r.invoiceNo}",onClose,{
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            DseStatus("Document",r.documentStatus.orEmpty());DseStatus("Payment",r.paymentStatus.orEmpty())
            detailRows(listOf(
                "Customer" to r.customer?.name.orEmpty(),"Customer GSTIN" to r.customer?.gstin.orEmpty(),
                "Invoice Date" to r.invoiceDate,"Due Date" to r.dueDate.orEmpty(),"Invoice Type" to r.invoiceType.orEmpty(),
                "Payment Term" to r.paymentTerms.orEmpty(),"Salesperson" to r.salesperson.orEmpty(),"Reference / PO" to r.referenceNo.orEmpty(),"PO Date" to r.poDate.orEmpty(),
                "Subtotal" to money(r.subtotal),"Discount" to money(r.discountAmount),"GST" to money(r.gstAmount),"Charges" to money(r.charges.sumOf{it.amount}),
                "Total" to money(r.totalAmount),"Paid" to money(r.paidAmount),"Outstanding" to money(outstanding),
                "Billing GSTIN" to r.billingGstin.orEmpty(),"Billing" to r.billingAddress.orEmpty(),"Delivery GSTIN" to r.deliveryGstin.orEmpty(),"Delivery" to r.deliveryAddress.orEmpty(),
                "Transporter" to r.transporter.orEmpty(),"Transporter GSTIN" to r.transporterGstin.orEmpty(),"Vehicle" to r.vehicleNumber.orEmpty(),
                "Contact" to r.contactPerson.orEmpty(),"Contact Mobile" to r.contactPersonMobile.orEmpty(),"Transport Note" to r.transportNote.orEmpty(),
                "Notes" to r.notes.orEmpty(),"Items" to r.lines.size.toString()
            ))
            if(r.lines.isNotEmpty())DseSection("Items",Icons.Rounded.Inventory2){r.lines.forEach{l->Text("${l.itemCode} • ${l.itemDescription.orEmpty()} • ${l.quantity} × ${money(l.rate)} • GST ${l.gstPercent}% • ${money(l.totalAmount)}",style=MaterialTheme.typography.bodySmall)}}
            r.id?.let{AttachmentManager(api,"SALE",it,p.can("SALES","EDIT"))}
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){PremiumIconTile(Icons.Rounded.TouchApp,MaterialTheme.colorScheme.primary,size=36.dp);Column{Text("Actions",fontWeight=FontWeight.ExtraBold);Text("Invoice actions",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            PremiumActionGrid(buildList{
                if(canEdit)add(PremiumActionSpec("Edit",onEdit))
                if(canPay)add(PremiumActionSpec("Record Payment",onPayment))
                if(canReturn)add(PremiumActionSpec("Create Sales Return",onReturn))
                if(documentOutputAllowed&&p.can("SALES","VIEW")){add(PremiumActionSpec("Share PDF",onPdf,icon=Icons.Rounded.PictureAsPdf));add(PremiumActionSpec("Excel",onExcel,icon=Icons.Rounded.TableChart));add(PremiumActionSpec("Email",onEmail));if(!approvalLocked)add(PremiumActionSpec("WhatsApp",onWhatsApp))}
                if(p.can("SALES","CREATE"))add(PremiumActionSpec("Duplicate",onDuplicate))
                if(state=="PENDING APPROVAL"&&p.isAdmin()){add(PremiumActionSpec("Approve",onApprove));add(PremiumActionSpec("Reject",onReject,destructive=true))}
                if(active&&!financiallyLocked&&p.can("SALES","EDIT"))add(PremiumActionSpec("Cancel",onCancel,destructive=true))
                if(active&&!financiallyLocked&&p.can("SALES","DELETE"))add(PremiumActionSpec("Delete",onDelete,destructive=true))
            })
        }
    })
}

@Composable
private fun PurchaseDetailDialog(
    api:DseErpHttpClient,
    r:PurchaseRecord,
    p:PermissionContext,
    onClose:()->Unit,
    onEdit:()->Unit,
    onPayment:()->Unit,
    onReturn:()->Unit,
    onEmail:()->Unit,
    onPdf:()->Unit,
    onExcel:()->Unit,
    onWhatsApp:()->Unit,
    onDuplicate:()->Unit,
    onCancel:()->Unit,
    onDelete:()->Unit,
    onApprove:()->Unit,
    onReject:()->Unit,
){
    val state=r.documentStatus.orEmpty().trim().uppercase()
    val active=state !in setOf("CANCELLED","DELETED")
    val documentOutputAllowed=canonicalBusinessDocumentAllowed(state)
    val approvalLocked=state in setOf("PENDING APPROVAL","REJECTED")
    val outstanding=(r.totalAmount-r.paidAmount).coerceAtLeast(0.0)
    val paymentState=r.paymentStatus.orEmpty().trim().uppercase()
    val fullyPaid=paymentState in setOf("PAID","SETTLED")||r.paidAmount+0.009>=r.totalAmount
    val financiallyLocked=r.paidAmount>0.009||paymentState.contains("PAID")||paymentState.contains("SETTLED")||paymentState.contains("PARTIAL")
    val currency=r.currency?.substringBefore(' ')?.ifBlank{"INR"}?:"INR"
    val canEdit=active&&p.can("PURCHASE","EDIT")
    val canPay=active&&state!="DRAFT"&&!approvalLocked&&outstanding>0.005&&p.can("PURCHASE","EDIT")
    val canReturn=state=="APPROVED"&&fullyPaid&&paymentState !in setOf("RETURN PENDING","RETURN PARTIAL")&&p.can("PURCHASE","EDIT")
    DetailDialog("Purchase ${r.invoiceNo}",onClose,{
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            DseStatus("Document",r.documentStatus.orEmpty());DseStatus("Payment",r.paymentStatus.orEmpty())
            detailRows(listOf(
                "Supplier" to r.supplier?.name.orEmpty(),"Supplier GSTIN" to r.supplier?.gstin.orEmpty(),"Purchase Date" to r.invoiceDate,"Delivery Date" to r.deliveryDate.orEmpty(),
                "Due Date" to r.dueDate.orEmpty(),"Payment Term" to r.paymentTerms.orEmpty(),"Currency" to currency,"Warehouse" to r.warehouse.orEmpty(),"GST Treatment" to r.gstTreatment.orEmpty(),
                "Reference" to r.referenceNo.orEmpty(),"Order No" to r.orderNo.orEmpty(),"PO Date" to r.poDate.orEmpty(),"LR / AWB" to r.lrAwbNo.orEmpty(),
                "Subtotal" to money(r.subtotal,currency),"Discount" to money(r.discountAmount,currency),"GST" to money(r.gstAmount,currency),"Charges" to money(r.charges.sumOf{it.amount},currency),
                "Total" to money(r.totalAmount,currency),"Paid" to money(r.paidAmount,currency),"Outstanding" to money(outstanding,currency),
                "Billing GSTIN" to r.billingGstin.orEmpty(),"Billing" to r.billingAddress.orEmpty(),"Delivery GSTIN" to r.deliveryGstin.orEmpty(),"Delivery" to r.deliveryAddress.orEmpty(),
                "Transporter" to r.transporter.orEmpty(),"Transporter GSTIN" to r.transporterGstin.orEmpty(),"Vehicle" to r.vehicleNumber.orEmpty(),
                "Contact" to r.contactPerson.orEmpty(),"Contact Mobile" to r.contactPersonMobile.orEmpty(),"Notes" to r.notes.orEmpty(),"Items" to r.lines.size.toString()
            ))
            if(r.lines.isNotEmpty())DseSection("Items",Icons.Rounded.Inventory2){r.lines.forEach{l->Text("${l.itemCode} • ${l.itemDescription.orEmpty()} • ${l.quantity} × ${money(l.rate,currency)} • GST ${l.gstPercent}% • ${money(l.totalAmount,currency)}",style=MaterialTheme.typography.bodySmall)}}
            r.id?.let{AttachmentManager(api,"PURCHASE",it,p.can("PURCHASE","EDIT"))}
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){PremiumIconTile(Icons.Rounded.TouchApp,MaterialTheme.colorScheme.primary,size=36.dp);Column{Text("Actions",fontWeight=FontWeight.ExtraBold);Text("Purchase document actions",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            PremiumActionGrid(buildList{
                if(canEdit)add(PremiumActionSpec("Edit",onEdit))
                if(canPay)add(PremiumActionSpec("Record Payment",onPayment))
                if(canReturn)add(PremiumActionSpec("Create Purchase Return",onReturn))
                if(documentOutputAllowed&&p.can("PURCHASE","VIEW")){add(PremiumActionSpec("Share PDF",onPdf,icon=Icons.Rounded.PictureAsPdf));add(PremiumActionSpec("Excel",onExcel,icon=Icons.Rounded.TableChart));add(PremiumActionSpec("Email",onEmail));if(!approvalLocked)add(PremiumActionSpec("WhatsApp",onWhatsApp))}
                if(p.can("PURCHASE","CREATE"))add(PremiumActionSpec("Duplicate",onDuplicate))
                if(state=="PENDING APPROVAL"&&p.isAdmin()){add(PremiumActionSpec("Approve",onApprove));add(PremiumActionSpec("Reject",onReject,destructive=true))}
                if(active&&!financiallyLocked&&p.can("PURCHASE","EDIT"))add(PremiumActionSpec("Cancel",onCancel,destructive=true))
                if(active&&!financiallyLocked&&p.can("PURCHASE","DELETE"))add(PremiumActionSpec("Delete",onDelete,destructive=true))
            })
        }
    })
}

@Composable
internal fun detailRows(rows:List<Pair<String,String>>){
    rows.filter{it.second.isNotBlank()}.forEach{(k,v)->
        val accent=semanticFieldAccent(k)
        ListItem(
            leadingContent={PremiumIconTile(semanticFieldIcon(k),accent,size=34.dp)},
            headlineContent={Text(k,color=accent,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)},
            trailingContent={PremiumValueText(k,v,Modifier.widthIn(max=220.dp))},
            colors=ListItemDefaults.colors(containerColor=Color.Transparent),
        )
    }
}

@Composable
private fun SaleEditorDialog(api:DseErpHttpClient,current:SaleRecord?,onClose:()->Unit,onSave:(SaleRecord)->Unit){
    var party by remember{mutableStateOf(current?.customer?.let{MasterParty(id=it.id,partyType="CUSTOMER",partyCode=it.partyCode.orEmpty(),name=it.name,email=it.email,phone=it.phone,gstin=it.gstin,address=it.address)})}
    var invoiceDate by remember{mutableStateOf(current?.invoiceDate?.ifBlank{todayIso()}?:todayIso())}
    var due by remember{mutableStateOf(current?.dueDate.orEmpty())}
    var paymentTerm by remember{mutableStateOf(current?.paymentTerms.orEmpty())}
    var billing by remember{mutableStateOf(current?.billingAddress.orEmpty())}
    var delivery by remember{mutableStateOf(current?.deliveryAddress.orEmpty())}
    var billingGstin by remember{mutableStateOf(current?.billingGstin.orEmpty())}
    var deliveryGstin by remember{mutableStateOf(current?.deliveryGstin.orEmpty())}
    var same by remember{mutableStateOf(current?.sameAsBilling?:false)}
    var gstType by remember{mutableStateOf(current?.gstType.orEmpty())}
    var invoiceType by remember{mutableStateOf(current?.invoiceType?:"TAX INVOICE")}
    var salesperson by remember{mutableStateOf(current?.salesperson.orEmpty())}
    var referenceNo by remember{mutableStateOf(current?.referenceNo.orEmpty())}
    var orderNo by remember{mutableStateOf(current?.orderNo.orEmpty())}
    var poDate by remember{mutableStateOf(current?.poDate.orEmpty())}
    var transporter by remember{mutableStateOf(current?.transporter.orEmpty())}
    var transporterGstin by remember{mutableStateOf(current?.transporterGstin.orEmpty())}
    var vehicle by remember{mutableStateOf(current?.vehicleNumber.orEmpty())}
    var contact by remember{mutableStateOf(current?.contactPerson.orEmpty())}
    var contactMobile by remember{mutableStateOf(current?.contactPersonMobile.orEmpty())}
    var transportNote by remember{mutableStateOf(current?.transportNote.orEmpty())}
    var notes by remember{mutableStateOf(current?.notes.orEmpty())}
    var preview by remember{mutableStateOf(current?.invoiceNo.orEmpty())}
    var bootstrap by remember{mutableStateOf<SalesEntryBootstrap?>(null)}
    val lines=remember{mutableStateListOf<LineDraft>()}
    val charges=remember{mutableStateListOf<DocumentCharge>()}
    var lineEditor by remember{mutableStateOf<Int?>(null)}
    var chargeEditor by remember{mutableStateOf<Int?>(null)}
    var msg by remember{mutableStateOf("")}
    LaunchedEffect(current){
        if(lines.isEmpty())lines.addAll(current?.lines?.map{it.toDraft(true)}?:emptyList())
        if(charges.isEmpty())charges.addAll(current?.charges.orEmpty())
        when(val b=api.salesEntryBootstrap()){
            is ApiResult.Success->{bootstrap=b.value;if(paymentTerm.isBlank())paymentTerm=paymentTermDefault(b.value.paymentTerms);if(gstType.isBlank())gstType=b.value.gstTypes.firstOrNull().orEmpty()}
            else->msg=b.readableMessage()
        }
        if(current==null)when(val n=api.nextSaleNumber()){is ApiResult.Success->preview=n.value.value;else->{}}
        if(due.isBlank())due=dueDate(invoiceDate,paymentTerm)
    }
    fun recalcDue(){due=dueDate(invoiceDate,paymentTerm)}
    val docLines=lines.map{it.toDocumentLine()}
    val totals=documentPreview(docLines,charges)
    val saleChargeError=chargeValidationError(charges,2)
    val effectiveDelivery=if(same)billing else delivery
    val effectiveDeliveryGstin=if(same)billingGstin else deliveryGstin
    val saleValidationError=when{
        invoiceDate.isBlank()->"Select invoice date"
        party?.id==null->"Select customer"
        effectiveDelivery.isBlank()->"Enter delivery address"
        !same&&normalizedBusiness(billing)==normalizedBusiness(effectiveDelivery)&&normalizedBusiness(billingGstin)==normalizedBusiness(effectiveDeliveryGstin)->"Delivery address and GSTIN still match billing details. Select 'Delivery same as billing' or update the delivery details."
        docLines.isEmpty()||docLines.none{it.itemCode.isNotBlank()}->"Add items"
        !docLines.all(::validLine)->"Correct invalid item quantities, rates, discounts or GST values."
        saleChargeError!=null->saleChargeError
        else->null
    }
    PremiumAlertDialog(
        onDismissRequest=onClose,
        title={Text(if(current==null)"Create Sale" else "Edit ${current.invoiceNo}")},
        text={Column(Modifier.fillMaxWidth().heightIn(max=680.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(preview.isNotBlank())DseField("Invoice No",preview,readOnly=true,singleLine=true,onValue={})
            DseDateField("Invoice Date",invoiceDate,required=true,onValue={invoiceDate=it;recalcDue()})
            PartySelector(api,"CUSTOMER",party,allowCreate=true,onSelected={p->
                party=p
                billing=p.address.orEmpty()
                billingGstin=p.gstin.orEmpty()
                contact=p.contactPerson.orEmpty()
                contactMobile=p.phone.orEmpty()
                if(same){delivery=billing;deliveryGstin=billingGstin}
            },onCleared={party=null})
            DseSelect("Payment Term",paymentTerm,bootstrap?.paymentTerms.orEmpty(),onValue={paymentTerm=it;recalcDue()})
            DseField("Due Date",due,readOnly=true,singleLine=true,onValue={})
            DseSelect("GST Type",gstType,bootstrap?.gstTypes.orEmpty(),onValue={gstType=it})
            DseSelect("Invoice Type",invoiceType,listOf("TAX INVOICE","BILL OF SUPPLY","EXPORT INVOICE"),onValue={invoiceType=it})
            DseField("Salesperson",salesperson,singleLine=true,onValue={salesperson=it})
            DseField("Customer PO / Reference No",referenceNo,singleLine=true,onValue={referenceNo=it})
            DseField("Order No",orderNo,singleLine=true,onValue={orderNo=it})
            DseDateField("PO Date",poDate,onValue={poDate=it})
            DseSection("Addresses",Icons.Rounded.LocationOn){
                DseField("Billing GSTIN",billingGstin,singleLine=true,onValue={billingGstin=it})
                DseField("Billing Address",billing,onValue={billing=it;if(same)delivery=it})
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(same,{same=it;if(it){delivery=billing;deliveryGstin=billingGstin}});PremiumOptionLabel("Delivery same as billing")}
                if(!same){DseField("Delivery GSTIN",deliveryGstin,singleLine=true,onValue={deliveryGstin=it});DseField("Delivery Address",delivery,required=true,onValue={delivery=it})}
            }
            DocumentLinesEditor(api,lines,true,lineEditor,{lineEditor=it})
            DocumentChargesEditor(bootstrap?.chargeTypes.orEmpty(),charges,chargeEditor,{chargeEditor=it},maxCharges=2)
            DseSection("Transport & Notes",Icons.Rounded.LocalShipping){
                DseSelect("Transporter",transporter,bootstrap?.transporters.orEmpty().filter{it.active}.map{it.lookupValue},onValue={value->
                    transporter=value
                    transporterGstin=bootstrap?.transporters.orEmpty().firstOrNull{it.lookupValue.equals(value,true)}?.description.orEmpty().trim()
                })
                DseField("Transporter GSTIN",transporterGstin,singleLine=true,onValue={transporterGstin=it})
                DseField("Vehicle No",vehicle,singleLine=true,onValue={vehicle=it})
                DseField("Contact Person",contact,singleLine=true,onValue={contact=it})
                DseField("Contact Mobile",contactMobile,singleLine=true,onValue={contactMobile=it})
                DseField("Transport Note",transportNote,onValue={transportNote=it})
                DseField("Notes",notes,onValue={notes=it})
            }
            DseSection("Totals — server recalculates on Save",Icons.Rounded.Calculate){
                detailRows(listOf("Gross" to money(totals.gross),"Discount" to money(totals.discount),"Taxable" to money(totals.taxable),"GST" to money(totals.tax),"Charges" to money(totals.chargeTotal),"Total" to money(totals.total)))
            }
            val visibleSaleError=saleValidationError?.takeUnless{it in setOf("Select customer","Enter delivery address","Add items")}
            if(visibleSaleError!=null)Text(visibleSaleError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            DseMessageFeedback(msg)
        }},
        confirmButton={PremiumPrimaryButton(
            text=if(current==null)"Create Sale" else "Save Changes",
            enabled=saleValidationError==null,
            leadingIcon=if(current==null)Icons.Rounded.AddShoppingCart else Icons.Rounded.Save,
            onClick=save@{
                val p=party?:return@save
                onSave((current?:SaleRecord()).copy(
                    invoiceDate=invoiceDate,customer=PartyRef(p.id,p.partyCode,p.name,p.email,p.phone,p.gstin,p.address),dueDate=due,paymentTerms=paymentTerm,
                    billingAddress=billing,deliveryAddress=effectiveDelivery,billingGstin=billingGstin,deliveryGstin=effectiveDeliveryGstin,sameAsBilling=same,
                    gstin=p.gstin,gstType=gstType,invoiceType=invoiceType,salesperson=salesperson,referenceNo=referenceNo,orderNo=orderNo,poDate=poDate,
                    transporter=transporter,transporterGstin=transporterGstin,vehicleNumber=vehicle,contactPerson=contact,contactPersonMobile=contactMobile,transportNote=transportNote,notes=notes,
                    subtotal=totals.taxable,discountAmount=totals.discount,gstAmount=totals.tax,totalAmount=totals.total,quantity=docLines.sumOf{it.quantity},charges=charges.toList(),lines=docLines
                ))
            }
        )},
        dismissButton={TextButton(onClick=onClose){Text("Cancel")}}
    )
    lineEditor?.let{i->if(i in lines.indices)LineEditorDialog(api,lines[i],true,{lineEditor=null}){lines[i]=it;lineEditor=null}}
    chargeEditor?.let{i->ChargeEditorDialog(bootstrap?.chargeTypes.orEmpty(),charges.getOrNull(i),{chargeEditor=null}){value->if(i in charges.indices)charges[i]=value else charges.add(value);chargeEditor=null}}
}

@Composable
private fun PurchaseEditorDialog(api:DseErpHttpClient,current:PurchaseRecord?,onClose:()->Unit,createFromTemplate:Boolean=false,onSave:(PurchaseRecord)->Unit){
    var party by remember{mutableStateOf(current?.supplier?.let{MasterParty(id=it.id,partyType="SUPPLIER",partyCode=it.partyCode.orEmpty(),name=it.name,email=it.email,phone=it.phone,gstin=it.gstin,address=it.address)})}
    var invoiceDate by remember{mutableStateOf(current?.invoiceDate?.ifBlank{todayIso()}?:todayIso())}
    var deliveryDate by remember{mutableStateOf(current?.deliveryDate.orEmpty())}
    var due by remember{mutableStateOf(current?.dueDate.orEmpty())}
    var term by remember{mutableStateOf(current?.paymentTerms.orEmpty())}
    val currency = current?.currency?.takeIf{it.isNotBlank()} ?: "INR - Indian Rupee"
    val warehouse = current?.warehouse?.takeIf{it.isNotBlank()} ?: "Main Warehouse"
    val gstTreatment = current?.gstTreatment?.takeIf{it.isNotBlank()} ?: "Business Purchase"
    var billing by remember{mutableStateOf(current?.billingAddress.orEmpty())}
    var delivery by remember{mutableStateOf(current?.deliveryAddress.orEmpty())}
    var billingGstin by remember{mutableStateOf(current?.billingGstin.orEmpty())}
    var deliveryGstin by remember{mutableStateOf(current?.deliveryGstin.orEmpty())}
    var same by remember{mutableStateOf(current?.sameAsBilling?:false)}
    var gstType by remember{mutableStateOf(current?.gstType.orEmpty())}
    var referenceNo by remember{mutableStateOf(current?.referenceNo.orEmpty())}
    var orderNo by remember{mutableStateOf(current?.orderNo.orEmpty())}
    var poDate by remember{mutableStateOf(current?.poDate.orEmpty())}
    var lrAwb by remember{mutableStateOf(current?.lrAwbNo.orEmpty())}
    val discountType = current?.discountType?.takeIf{it.isNotBlank()} ?: "Item Level"
    var transporter by remember{mutableStateOf(current?.transporter.orEmpty())}
    var transporterGstin by remember{mutableStateOf(current?.transporterGstin.orEmpty())}
    var vehicle by remember{mutableStateOf(current?.vehicleNumber.orEmpty())}
    var contact by remember{mutableStateOf(current?.contactPerson.orEmpty())}
    var contactMobile by remember{mutableStateOf(current?.contactPersonMobile.orEmpty())}
    var notes by remember{mutableStateOf(current?.notes.orEmpty())}
    var preview by remember{mutableStateOf(current?.invoiceNo.orEmpty())}
    var bootstrap by remember{mutableStateOf<SalesEntryBootstrap?>(null)}
    val lines=remember{mutableStateListOf<LineDraft>()}
    val charges=remember{mutableStateListOf<DocumentCharge>()}
    var lineEditor by remember{mutableStateOf<Int?>(null)}
    var chargeEditor by remember{mutableStateOf<Int?>(null)}
    var msg by remember{mutableStateOf("")}
    LaunchedEffect(current){
        if(lines.isEmpty())lines.addAll(current?.lines?.map{it.toDraft(false)}?:emptyList())
        if(charges.isEmpty())charges.addAll(current?.charges.orEmpty())
        when(val b=api.salesEntryBootstrap()){
            is ApiResult.Success->{bootstrap=b.value;if(term.isBlank())term=paymentTermDefault(b.value.paymentTerms);if(gstType.isBlank())gstType=b.value.gstTypes.firstOrNull().orEmpty()}
            else->msg=b.readableMessage()
        }
        if(current==null||createFromTemplate)when(val n=api.nextPurchaseNumber()){is ApiResult.Success->preview=n.value.value;else->{}}
        if(due.isBlank())due=dueDate(invoiceDate,term)
        if(deliveryDate.isBlank())deliveryDate=due
    }
    val docLines=lines.map{it.toDocumentLine()};val totals=documentPreview(docLines,charges)
    val purchaseChargeError=chargeValidationError(charges,null)
    val effectiveDelivery=if(same)billing else delivery
    val effectiveDeliveryGstin=if(same)billingGstin else deliveryGstin
    val purchaseValidationError=when{
        invoiceDate.isBlank()->"Select invoice date"
        party?.id==null->"Select supplier"
        effectiveDelivery.isBlank()->"Enter delivery address"
        !same&&normalizedBusiness(billing)==normalizedBusiness(effectiveDelivery)&&normalizedBusiness(billingGstin)==normalizedBusiness(effectiveDeliveryGstin)->"Delivery address and GSTIN still match billing details. Select 'Delivery same as billing' or update the delivery details."
        docLines.isEmpty()||docLines.none{it.itemCode.isNotBlank()}->"Add items"
        term.isBlank()->"Configure and select Payment Terms from Master Data."
        gstType.isBlank()->"Configure and select GST Type from Master Data."
        !docLines.all(::validLine)->"Correct invalid item quantities, rates, discounts or GST values."
        purchaseChargeError!=null->purchaseChargeError
        else->null
    }
    PremiumAlertDialog(
        onDismissRequest=onClose,title={Text(if(current==null||createFromTemplate)"Create Purchase" else "Edit ${current.invoiceNo}")},
        text={Column(Modifier.fillMaxWidth().heightIn(max=680.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(preview.isNotBlank())DseField("Purchase No",preview,readOnly=true,singleLine=true,onValue={})
            DseDateField("Purchase Date",invoiceDate,required=true,onValue={invoiceDate=it;due=dueDate(it,term);deliveryDate=due})
            PartySelector(api,"SUPPLIER",party,allowCreate=true,onSelected={p->
                party=p
                billing=p.address.orEmpty()
                billingGstin=p.gstin.orEmpty()
                contact=p.contactPerson.orEmpty()
                contactMobile=p.phone.orEmpty()
                if(same){delivery=billing;deliveryGstin=billingGstin}
            },onCleared={party=null})
            DseSelect("Payment Term",term,bootstrap?.paymentTerms.orEmpty(),required=true,onValue={term=it;due=dueDate(invoiceDate,it);deliveryDate=due})
            DseSelect("GST Type",gstType,bootstrap?.gstTypes.orEmpty(),required=true,onValue={gstType=it})
            DseField("PO / Supplier Ref",orderNo,singleLine=true,onValue={orderNo=it})
            DseDateField("PO Date",poDate,onValue={poDate=it})
            DseSection("Addresses",Icons.Rounded.LocationOn){
                DseField("Billing GSTIN",billingGstin,singleLine=true,onValue={billingGstin=it})
                DseField("Billing Address",billing,onValue={billing=it;if(same)delivery=it})
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(same,{same=it;if(it){delivery=billing;deliveryGstin=billingGstin}});PremiumOptionLabel("Delivery same as billing")}
                if(!same){DseField("Delivery GSTIN",deliveryGstin,singleLine=true,onValue={deliveryGstin=it});DseField("Delivery Address",delivery,required=true,onValue={delivery=it})}
            }
            DocumentLinesEditor(api,lines,false,lineEditor,{lineEditor=it},currency.substringBefore(' '))
            DocumentChargesEditor(bootstrap?.chargeTypes.orEmpty(),charges,chargeEditor,{chargeEditor=it},currency.substringBefore(' '))
            DseSection("Transport & Notes",Icons.Rounded.LocalShipping){
                DseSelect("Transporter",transporter,bootstrap?.transporters.orEmpty().filter{it.active}.map{it.lookupValue},onValue={value->
                    transporter=value
                    transporterGstin=bootstrap?.transporters.orEmpty().firstOrNull{it.lookupValue.equals(value,true)}?.description.orEmpty().trim()
                })
                DseField("Transporter GSTIN",transporterGstin,singleLine=true,onValue={transporterGstin=it})
                DseField("Vehicle No",vehicle,singleLine=true,onValue={vehicle=it})
                DseField("Contact Person",contact,singleLine=true,onValue={contact=it})
                DseField("Contact Mobile",contactMobile,singleLine=true,onValue={contactMobile=it})
                DseField("Notes",notes,onValue={notes=it})
            }
            DseSection("Totals — server recalculates on Save",Icons.Rounded.Calculate){val c=currency.substringBefore(' ');detailRows(listOf("Gross" to money(totals.gross,c),"Discount" to money(totals.discount,c),"Taxable" to money(totals.taxable,c),"GST" to money(totals.tax,c),"Charges" to money(totals.chargeTotal,c),"Total" to money(totals.total,c)))}
            val visiblePurchaseError=purchaseValidationError?.takeUnless{it in setOf("Select supplier","Enter delivery address","Add items")}
            if(visiblePurchaseError!=null)Text(visiblePurchaseError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            DseMessageFeedback(msg)
        }},
        confirmButton={PremiumPrimaryButton(
            text=if(current==null||createFromTemplate)"Create Purchase" else "Save Changes",
            enabled=purchaseValidationError==null,
            leadingIcon=if(current==null||createFromTemplate)Icons.Rounded.ShoppingBag else Icons.Rounded.Save,
            onClick=save@{
                val p=party?:return@save
                onSave((current?:PurchaseRecord()).copy(
                    invoiceDate=invoiceDate,supplier=PartyRef(p.id,p.partyCode,p.name,p.email,p.phone,p.gstin,p.address),deliveryDate=deliveryDate,dueDate=due,paymentTerms=term,currency=currency,
                    warehouse=warehouse,gstTreatment=gstTreatment,billingAddress=billing,deliveryAddress=effectiveDelivery,billingGstin=billingGstin,deliveryGstin=effectiveDeliveryGstin,sameAsBilling=same,
                    gstType=gstType,referenceNo=referenceNo,orderNo=orderNo,poDate=poDate,lrAwbNo=lrAwb,discountType=discountType,
                    transporter=transporter,transporterGstin=transporterGstin,vehicleNumber=vehicle,contactPerson=contact,contactPersonMobile=contactMobile,notes=notes,
                    subtotal=totals.taxable,discountAmount=totals.discount,gstAmount=totals.tax,totalAmount=totals.total,quantity=docLines.sumOf{it.quantity},charges=charges.toList(),lines=docLines
                ))
            }
        )},dismissButton={TextButton(onClick=onClose){Text("Cancel")}}
    )
    lineEditor?.let{i->if(i in lines.indices)LineEditorDialog(api,lines[i],false,{lineEditor=null}){lines[i]=it;lineEditor=null}}
    chargeEditor?.let{i->ChargeEditorDialog(bootstrap?.chargeTypes.orEmpty(),charges.getOrNull(i),{chargeEditor=null}){value->if(i in charges.indices)charges[i]=value else charges.add(value);chargeEditor=null}}
}

@Composable
private fun DocumentLinesEditor(api:DseErpHttpClient,lines:MutableList<LineDraft>,sales:Boolean,editing:Int?,onEdit:(Int?)->Unit,currency:String="INR"){
    DseSection("Items",Icons.Rounded.Inventory2){
        if(lines.isEmpty()){
            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(.28f),modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                    PremiumIconTile(Icons.Rounded.AddShoppingCart,MaterialTheme.colorScheme.primary,size=38.dp)
                    Column(Modifier.weight(1f)){Text("No items added yet",fontWeight=FontWeight.Bold);Text("Tap Add Item to search the live item master.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
        }
        lines.forEachIndexed{i,l->
            Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(l.code.ifBlank{"Select item"},fontWeight=FontWeight.SemiBold);Text("Qty ${l.qty} × ${l.rate} • GST ${l.gst}% • ${money(l.toDocumentLine().totalAmount,currency)}",style=MaterialTheme.typography.bodySmall)}
                    IconButton(onClick={onEdit(i)}){Icon(Icons.Rounded.Edit,"Edit line")}
                    IconButton(onClick={lines.removeAt(i)}){Icon(Icons.Rounded.Delete,"Remove line")}
                }
            }
        }
        OutlinedButton(onClick={lines.add(LineDraft());onEdit(lines.lastIndex)}){Icon(Icons.Rounded.Add,null);Text("Add Item")}
    }
}

@Composable
private fun DocumentChargesEditor(types:List<String>,charges:MutableList<DocumentCharge>,editing:Int?,onEdit:(Int?)->Unit,currency:String="INR",maxCharges:Int?=null){
    DseSection("Additional Charges",Icons.Rounded.AddCard){
        if(charges.isEmpty())Text("No additional charges",style=MaterialTheme.typography.bodySmall)
        charges.forEachIndexed{i,c->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(c.chargeType.ifBlank{"Charge"});Text("${money(c.amount,currency)}${if(c.taxable)" • taxable ${c.gstPercent}%" else " • non-taxable"}",style=MaterialTheme.typography.bodySmall)};IconButton(onClick={onEdit(i)}){Icon(Icons.Rounded.Edit,"Edit charge")};IconButton(onClick={charges.removeAt(i)}){Icon(Icons.Rounded.Delete,"Remove charge")}}}
        OutlinedButton(enabled=types.isNotEmpty()&&(maxCharges==null||charges.size<maxCharges),onClick={onEdit(charges.size)}){Icon(Icons.Rounded.Add,null);Text(if(maxCharges!=null&&charges.size>=maxCharges)"Maximum $maxCharges Charges" else "Add Charge")}
    }
}

@Composable
private fun ChargeEditorDialog(types:List<String>,initial:DocumentCharge?,onClose:()->Unit,onSave:(DocumentCharge)->Unit){
    var type by remember{mutableStateOf(initial?.chargeType.orEmpty())};var amount by remember{mutableStateOf(initial?.amount?.toString().orEmpty())};var taxable by remember{mutableStateOf(initial?.taxable?:false)};var gst by remember{mutableStateOf(initial?.gstPercent?.toString() ?: "0")}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(initial==null)"Add Charge" else "Edit Charge")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseSelect("Charge Type",type,types,required=true,onValue={type=it});DseNumberField("Amount",amount,min=0.0,required=true,onValue={amount=it});Row(verticalAlignment=Alignment.CenterVertically){Checkbox(taxable,{taxable=it});PremiumOptionLabel("Taxable",accent=DseWarning)};if(taxable)DseNumberField("GST %",gst,min=0.0,max=100.0,required=true,onValue={gst=it})}},confirmButton={Button(enabled=type.isNotBlank()&&(amount.toDoubleOrNull()?:0.0)>0,onClick={onSave(DocumentCharge(type,amount.toDoubleOrNull()?:0.0,taxable,if(taxable)gst.toDoubleOrNull()?:0.0 else 0.0))}){Text("Apply")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable
private fun LineEditorDialog(api:DseErpHttpClient,initial:LineDraft,sales:Boolean,onClose:()->Unit,onSave:(LineDraft)->Unit){
    var draft by remember{mutableStateOf(initial)};var selected by remember{mutableStateOf<MasterItem?>(initial.item)}
    val q=draft.qty.toDoubleOrNull()?:0.0;val rate=draft.rate.toDoubleOrNull()?:0.0;val disc=draft.discount.toDoubleOrNull()?:0.0;val gst=draft.gst.toDoubleOrNull()?:0.0
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Item Line")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        ItemSelector(api,selected,onSelected={i->selected=i;draft=draft.copy(item=i,code=i.itemCode,description=i.description,unit=i.unit.orEmpty(),hsn=i.hsn.orEmpty(),rate=(if(sales)i.sellingPrice else i.purchasePrice).toString(),discount=i.discountPercent.toString(),gst=i.gst.toString())},onCleared={selected=null;draft=draft.copy(item=null,code="",description="",unit="",hsn="")})
        DseNumberField("Quantity",draft.qty,min=0.000001,required=true,onValue={draft=draft.copy(qty=it)})
        if(sales&&selected!=null){
            val item=selected!!
            val onHand=item.openingStock
            val reserved=item.reservedStock
            val available=(onHand-reserved).coerceAtLeast(0.0)
            val after=available-q
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(.55f),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("Stock position",fontWeight=FontWeight.Bold)
                    detailRows(listOf("On Hand" to onHand.toString(),"Reserved" to reserved.toString(),"Available" to available.toString(),"After Sale" to after.toString()))
                    if(after<0)Text("Quantity exceeds currently available stock. Server validation remains authoritative.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        DseNumberField("Rate",draft.rate,min=0.0,required=true,onValue={draft=draft.copy(rate=it)})
        DseNumberField("Discount %",draft.discount,min=0.0,max=100.0,onValue={draft=draft.copy(discount=it)})
        DseNumberField("GST %",draft.gst,min=0.0,max=100.0,required=true,onValue={draft=draft.copy(gst=it)})
        DseField("Remarks",draft.remarks,onValue={draft=draft.copy(remarks=it)})
        Text("Line total ${money(lineTotal(q,rate,disc,gst))}",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
    }},confirmButton={Button(enabled=draft.code.isNotBlank()&&q>0&&rate>=0&&disc in 0.0..100.0&&gst in 0.0..100.0,onClick={onSave(draft)}){Text("Apply")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable
internal fun PaymentDialog(api:DseErpHttpClient,type:String,documentId:Int,documentNo:String,total:Double,paid:Double,party:String,username:String,onClose:()->Unit,onDone:(String)->Unit){
    var history by remember{mutableStateOf<List<PaymentRow>>(emptyList())};var refresh by remember{mutableIntStateOf(0)}
    var date by remember{mutableStateOf(todayIso())};var amount by remember{mutableStateOf(((total-paid).coerceAtLeast(0.0)).toString())};var full by remember{mutableStateOf(true)}
    var mode by remember{mutableStateOf("")};var reference by remember{mutableStateOf("")};var notes by remember{mutableStateOf("")};var modes by remember{mutableStateOf<List<String>>(emptyList())}
    var msg by remember{mutableStateOf("")};var editing by remember{mutableStateOf<PaymentRow?>(null)};var attaching by remember{mutableStateOf<PaymentRow?>(null)};var busy by remember{mutableStateOf(false)}
    val outstanding=(total-paid).coerceAtLeast(0.0);val scope=rememberCoroutineScope()
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    LaunchedEffect(documentId,refresh){
        when(val r=api.payments(type,documentId)){is ApiResult.Success->history=r.value;else->msg=r.readableMessage()}
        modes=(api.lookupValuesByCode("PAYMENT_MODE") as? ApiResult.Success)?.value.orEmpty();if(mode.isBlank())mode=modes.firstOrNull().orEmpty()
    }
    ModalBottomSheet(
        onDismissRequest=onClose,
        sheetState=sheetState,
        shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=30.dp,topEnd=30.dp,bottomEnd=0.dp,bottomStart=0.dp),
        containerColor=MaterialTheme.colorScheme.surface,
        dragHandle={BottomSheetDefaults.DragHandle(color=MaterialTheme.colorScheme.outline)},
    ){
        Column(
            Modifier.fillMaxWidth().heightIn(max=760.dp).verticalScroll(rememberScrollState()).padding(start=18.dp,end=18.dp,bottom=24.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),
        ){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("Record Payment",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold);Text(if(type=="SALE")"Receive against sales invoice" else "Pay supplier document",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(onClick=onClose){Icon(Icons.Rounded.Close,"Close")}
            }
            Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(.45f),modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    PremiumIconTile(Icons.Rounded.Payments,DseSuccess,size=44.dp)
                    Column(Modifier.weight(1f)){Text(documentNo,fontWeight=FontWeight.ExtraBold);Text(party,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Column(horizontalAlignment=Alignment.End){Text(money(outstanding),fontWeight=FontWeight.ExtraBold);Text("Outstanding",style=MaterialTheme.typography.labelSmall,color=DseDanger)}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                DseMetricTile("Document",money(total),Icons.Rounded.ReceiptLong,Modifier.weight(1f),DseInfo)
                DseMetricTile("Paid",money(paid),Icons.Rounded.CheckCircle,Modifier.weight(1f),DseSuccess)
            }
            DseDateField("Payment Date",date,required=true,onValue={date=it})
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(selected=full,onClick={full=true;amount=outstanding.toString()},label={Text("Full payment")},leadingIcon=if(full){{Icon(Icons.Rounded.Check,null,Modifier.size(16.dp))}}else null)
                FilterChip(selected=!full,onClick={full=false},label={Text("Partial")},leadingIcon=if(!full){{Icon(Icons.Rounded.Check,null,Modifier.size(16.dp))}}else null)
            }
            DseNumberField("Payment Amount",amount,enabled=!full,min=0.01,required=true,onValue={amount=it})
            DseSelect("Payment Mode",mode,modes,required=true,onValue={mode=it})
            DseField("Reference",reference,singleLine=true,onValue={reference=it})
            DseField("Notes",notes,onValue={notes=it})
            val after=(outstanding-(amount.toDoubleOrNull()?:0.0)).coerceAtLeast(0.0)
            Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(.55f),modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(11.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Balance after payment",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(money(after),fontWeight=FontWeight.Bold,color=if(after>0)DseWarning else DseSuccess)}
            }
            DseMessageFeedback(msg)
            PremiumPrimaryButton(
                text=if(busy)"Recording…" else "Record Payment",
                enabled=!busy&&(amount.toDoubleOrNull()?:0.0)>0&&mode.isNotBlank()&&documentId>0,
                onClick={scope.launch{
                    val a=amount.toDoubleOrNull()?:0.0
                    if(a>outstanding+0.005){msg="Amount exceeds outstanding ${money(outstanding)}";return@launch}
                    busy=true
                    when(val r=api.recordPayment(PaymentRequest(type,documentId,date,a,mode,reference,notes,party,if(full)"FULL" else "PARTIAL",null,username))){
                        is ApiResult.Success->{msg="Payment recorded • ${money(a)}";refresh++;onDone(msg)}
                        else->msg=r.readableMessage()
                    }
                    busy=false
                }},
                modifier=Modifier.fillMaxWidth(),
                leadingIcon=if(busy)null else Icons.Rounded.Payments,
                trailingIcon=if(busy)null else Icons.Rounded.ArrowForward,
            )
            if(history.isNotEmpty())DseSection("Payment History",Icons.Rounded.History){
                history.take(6).forEach{h->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        PremiumIconTile(Icons.Rounded.CheckCircle,DseSuccess,size=34.dp)
                        Column(Modifier.weight(1f)){Text("${h.date} • ${money(h.amount)}",fontWeight=FontWeight.SemiBold);Text(listOf(h.mode,h.reference,h.receivedFrom).filter{it.isNotBlank()}.joinToString(" • "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        IconButton(onClick={editing=h}){Icon(Icons.Rounded.Edit,"Edit payment")}
                        IconButton(onClick={attaching=h}){Icon(Icons.Rounded.AttachFile,"Payment proof")}
                    }
                }
            }
        }
    }
    editing?.let{row->PaymentEditDialog(api,row,{editing=null}){msg=it;editing=null;refresh++}}
    attaching?.let{row->PaymentProofDialog(api,row,{attaching=null}){msg=it;attaching=null;refresh++}}
}

@Composable
private fun PaymentEditDialog(api:DseErpHttpClient,row:PaymentRow,onClose:()->Unit,onDone:(String)->Unit){
    var date by remember{mutableStateOf(row.date)};var amount by remember{mutableStateOf(row.amount.toString())};var mode by remember{mutableStateOf(row.mode)};var reference by remember{mutableStateOf(row.reference)};var notes by remember{mutableStateOf(row.notes)};var from by remember{mutableStateOf(row.receivedFrom)};var modes by remember{mutableStateOf<List<String>>(emptyList())};var msg by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){modes=(api.lookupValuesByCode("PAYMENT_MODE") as? ApiResult.Success)?.value.orEmpty()}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Edit Payment #${row.id}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseDateField("Date",date,required=true,onValue={date=it});DseNumberField("Amount",amount,min=0.01,required=true,onValue={amount=it});DseSelect("Mode",mode,modes.ifEmpty{listOf(row.mode)},required=true,onValue={mode=it});DseField("Reference",reference,singleLine=true,onValue={reference=it});DseField("Received From / Paid To",from,singleLine=true,onValue={from=it});DseField("Notes",notes,onValue={notes=it});DseMessageFeedback(msg)}},confirmButton={Button(onClick={scope.launch{when(val r=api.updatePayment(row.id,PaymentUpdateRequest(date,amount.toDoubleOrNull()?:0.0,mode,reference,notes,from))){is ApiResult.Success->onDone("Payment updated");else->msg=r.readableMessage()}}}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable
private fun PaymentProofDialog(api:DseErpHttpClient,row:PaymentRow,onClose:()->Unit,onDone:(String)->Unit){
    var msg by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Payment Proof #${row.id}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){if(row.attachment.isNotBlank()){Text("Existing proof: ${documentFileName(row.attachment,"payment-${row.id}.bin")}");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={scope.launch{when(val f=api.paymentAttachmentFile(row.id)){is ApiResult.Success->{if(!platformShareFile("Payment proof #${row.id}",documentFileName(row.attachment,"payment-${row.id}.bin"),f.value))msg="Unable to open payment proof"};else->msg=f.readableMessage()}}}){Icon(Icons.Rounded.OpenInNew,null);Text("Open")};OutlinedButton(onClick={scope.launch{busy=true;when(val x=api.deletePaymentAttachment(row.id)){is ApiResult.Success->onDone("Payment proof removed");else->msg=x.readableMessage()};busy=false}}){Icon(Icons.Rounded.Delete,null);Text("Remove")}}};Text("Choose a file or image to attach to this payment.");DseMessageFeedback(msg)}},confirmButton={PremiumPrimaryButton("Choose & Upload",{if(!busy)platformPickAttachment { picked->if(picked.fileName.isNullOrBlank()||picked.base64.isNullOrBlank()){msg=picked.error?:"No attachment selected";return@platformPickAttachment};scope.launch{busy=true;try{when(val r=api.uploadPaymentAttachment(row.id,picked.fileName!!,decodeBase64Portable(picked.base64!!))){is ApiResult.Success->onDone("Payment proof attached");else->msg=r.readableMessage()}}catch(t:Throwable){msg=t.message?:"Attachment upload failed"}finally{busy=false}} }},enabled=!busy,leadingIcon=Icons.Rounded.AttachFile)},dismissButton={PremiumSecondaryButton("Close",onClose)})
}

@Composable
private fun BusinessEmailDialog(api:DseErpHttpClient,type:String,documentId:Int,documentNo:String,defaultRecipient:String,defaultBody:String,username:String,payload:BusinessDocumentPayload?=null,onClose:()->Unit,onDone:(String)->Unit){
    val canonicalType=when(type.uppercase()){"SALE","SALES"->"SALES_INVOICE";"PURCHASE","PURCHASES"->"PURCHASE_INVOICE";else->null}
    var recipient by remember{mutableStateOf(defaultRecipient)}
    var subject by remember{mutableStateOf(payload?.let{"${businessName()} ${it.documentType} ${it.number}"}?:"${businessName()} $documentNo")}
    var body by remember{mutableStateOf(defaultBody)}
    var attachmentName by remember{mutableStateOf<String?>(null)}
    var attachmentBase64 by remember{mutableStateOf<String?>(null)}
    var msg by remember{mutableStateOf(if(canonicalType!=null)"Preparing canonical ERP PDF…" else "")}
    var preparing by remember{mutableStateOf(canonicalType!=null)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(api,canonicalType,documentNo){
        if(canonicalType!=null){
            preparing=true
            when(val r=api.canonicalDocument(canonicalType,documentNo,"PDF")){
                is ApiResult.Success->{
                    attachmentName=canonicalDocumentFileName(canonicalType,documentNo,"PDF")
                    attachmentBase64=encodeBase64Portable(r.value)
                    msg="Canonical ERP PDF attached — same renderer/template as Desktop"
                }
                else->{attachmentName=null;attachmentBase64=null;msg="Unable to prepare canonical PDF: ${r.readableMessage()}"}
            }
            preparing=false
        }else if(payload!=null){
            attachmentName="${payload.documentType.replace(' ','_')}_${payload.number}.pdf"
            attachmentBase64=encodeBase64Portable(businessDocumentPdf(payload))
            msg="PDF document attached"
        }
    }
    PremiumAlertDialog(
        onDismissRequest=onClose,
        title={Text("Email $documentNo")},
        text={Column(Modifier.heightIn(max=620.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            DseField("Recipient",recipient,singleLine=true,required=true,onValue={recipient=it})
            DseField("Subject",subject,singleLine=true,required=true,onValue={subject=it})
            DseField("Message",body,onValue={body=it})
            PremiumSecondaryButton(if(preparing)"Preparing official PDF…" else attachmentName?:"Attach file",onClick={platformPickAttachment { picked->if(!picked.fileName.isNullOrBlank()&&!picked.base64.isNullOrBlank()){attachmentName=picked.fileName;attachmentBase64=picked.base64;msg="Attached ${picked.fileName}"}else msg=picked.error?:"No attachment selected" }},enabled=!preparing,icon=Icons.Rounded.AttachFile)
            DseMessageFeedback(msg)
        }},
        confirmButton={PremiumPrimaryButton("Send Email",{scope.launch{
            if(canonicalType!=null&&attachmentBase64.isNullOrBlank()){msg="Canonical ERP PDF is required before this email can be sent";return@launch}
            when(val r=api.sendBusinessEmail(BusinessEmailRequest(recipient.trim(),subject.trim(),body,attachmentName,attachmentBase64))){
                is ApiResult.Success->{if(r.value.success){if(documentId>0)api.markDocumentEmail(type,documentId);api.logCommunication(CommunicationRequest(type,documentId,"EMAIL",recipient,subject,"SENT","",username));onDone(r.value.message)}else msg=r.value.message}
                else->msg=r.readableMessage()
            }
        }},enabled=recipient.contains("@")&&subject.isNotBlank()&&!preparing&&(canonicalType==null||!attachmentBase64.isNullOrBlank()),leadingIcon=Icons.Rounded.Email)},
        dismissButton={PremiumSecondaryButton("Cancel",onClose)}
    )
}

private suspend fun shareCanonicalDocument(api:DseErpHttpClient,type:String,documentNo:String,format:String):String = when(val r=api.canonicalDocument(type,documentNo,format)){
    is ApiResult.Success->{
        val name=canonicalDocumentFileName(type,documentNo,format)
        if(platformShareFile("${businessName()} $documentNo",name,r.value)) "${format.uppercase()} opened from the canonical Desktop/Server renderer" else "Share service unavailable"
    }
    else->r.readableMessage()
}
private fun canonicalDocumentFileName(type:String,documentNo:String,format:String):String{
    val prefix=if(type.equals("SALES_INVOICE",true))"Sales-Tax-Invoice-" else "Purchase-Invoice-"
    val ext=if(format.equals("XLSX",true))".xlsx" else ".pdf"
    val safe=documentNo.map{c->if(c in "\\/:*?\"<>|")'_' else c}.joinToString("")
    return prefix+safe+ext
}

private data class DocumentPreview(val gross:Double,val discount:Double,val taxable:Double,val tax:Double,val chargeTotal:Double,val total:Double)
private fun documentPreview(lines:List<DocumentLine>,charges:List<DocumentCharge>):DocumentPreview{
    val gross=roundMoney(lines.sumOf{roundMoney(it.quantity*it.rate)})
    val discount=roundMoney(lines.sumOf{roundMoney(roundMoney(it.quantity*it.rate)*it.discountPercent.coerceIn(0.0,100.0)/100.0)})
    val taxable=roundMoney(lines.sumOf{lineTaxable(it.quantity,it.rate,it.discountPercent)})
    val lineTax=roundMoney(lines.sumOf{roundMoney(lineTaxable(it.quantity,it.rate,it.discountPercent)*it.gstPercent.coerceIn(0.0,100.0)/100.0)})
    val chargeBase=roundMoney(charges.sumOf{roundMoney(it.amount.coerceAtLeast(0.0))})
    val chargeTax=roundMoney(charges.filter{it.taxable}.sumOf{roundMoney(it.amount.coerceAtLeast(0.0)*it.gstPercent.coerceIn(0.0,100.0)/100.0)})
    return DocumentPreview(gross,discount,taxable,roundMoney(lineTax+chargeTax),roundMoney(chargeBase+chargeTax),roundMoney(taxable+lineTax+chargeBase+chargeTax))
}
private fun normalizedBusiness(value:String?)=value.orEmpty().trim().uppercase()
private fun chargeValidationError(charges:List<DocumentCharge>,maxCharges:Int?):String?{
    if(maxCharges!=null&&charges.size>maxCharges)return "A maximum of $maxCharges additional charges is allowed."
    val names=mutableSetOf<String>()
    charges.forEach{charge->
        if(charge.chargeType.isBlank())return "Select a charge type for every charge row."
        if(charge.amount<=0)return "Charge amount must be greater than zero."
        if(!names.add(normalizedBusiness(charge.chargeType)))return "The same charge type cannot be selected twice."
        if(charge.gstPercent !in 0.0..100.0)return "Charge GST percent must be between 0 and 100."
        if(!charge.taxable&&charge.gstPercent>0.0001)return "Non-taxable charges cannot have a GST percent."
    }
    return null
}
private fun validLine(l:DocumentLine)=l.itemCode.isNotBlank()&&l.quantity>0&&l.rate>=0&&l.discountPercent in 0.0..100.0&&l.gstPercent in 0.0..100.0
private fun roundMoney(v:Double)=kotlin.math.round(v*100.0)/100.0
private fun DocumentLine.toDraft(sales:Boolean)=LineDraft(code=itemCode,description=itemDescription.orEmpty(),unit=itemUnit.orEmpty(),hsn=itemHsn.orEmpty(),qty=quantity.toString(),rate=rate.toString(),discount=discountPercent.toString(),gst=gstPercent.toString(),remarks=itemRemarks.orEmpty())
private fun LineDraft.toDocumentLine():DocumentLine{val q=qty.toDoubleOrNull()?:0.0;val r=rate.toDoubleOrNull()?:0.0;val d=discount.toDoubleOrNull()?:0.0;val g=gst.toDoubleOrNull()?:0.0;val gross=roundMoney(q*r);val discAmt=roundMoney(gross*d.coerceIn(0.0,100.0)/100.0);return DocumentLine(code,description,hsn,unit,remarks,q,r,d,discAmt,g,lineTotal(q,r,d,g))}
private fun documentFileName(path:String,fallback:String):String=path.replace('\\','/').substringAfterLast('/').ifBlank{fallback}
private fun saleShareText(r:SaleRecord)=buildString{appendLine("${businessName()} • Sale ${r.invoiceNo}");appendLine("Customer: ${r.customer?.name.orEmpty()}");appendLine("Date: ${r.invoiceDate}");appendLine("Total: ${money(r.totalAmount)}");appendLine("Paid: ${money(r.paidAmount)}");appendLine("Outstanding: ${money((r.totalAmount-r.paidAmount).coerceAtLeast(0.0))}");appendLine("Document: ${r.documentStatus.orEmpty()} • Payment: ${r.paymentStatus.orEmpty()}")}
private fun purchaseShareText(r:PurchaseRecord)=buildString{val c=r.currency?.substringBefore(' ')?:"INR";appendLine("${businessName()} • Purchase ${r.invoiceNo}");appendLine("Supplier: ${r.supplier?.name.orEmpty()}");appendLine("Date: ${r.invoiceDate}");appendLine("Total: ${money(r.totalAmount,c)}");appendLine("Paid: ${money(r.paidAmount,c)}");appendLine("Outstanding: ${money((r.totalAmount-r.paidAmount).coerceAtLeast(0.0),c)}");appendLine("Document: ${r.documentStatus.orEmpty()} • Payment: ${r.paymentStatus.orEmpty()}")}
internal fun urlEncode(value:String):String=value.encodeToByteArray().joinToString(""){b->val n=b.toInt() and 0xff;val c=n.toChar();if((c in 'a'..'z')||(c in 'A'..'Z')||(c in '0'..'9')||c in "-_.~")c.toString() else "%"+n.toString(16).uppercase().padStart(2,'0')}


private suspend fun exportSalesCsv(api:DseErpHttpClient,filter:SalesFilter):ApiResult<List<SaleRecord>>{
    val rows=mutableListOf<SaleRecord>();var page=0
    while(page<1000){when(val r=api.salesPage(page,100,filter)){is ApiResult.Success->{rows+=r.value.rows;if(page+1>=r.value.totalPages)return ApiResult.Success(rows);page++};else->return r.mapFailure()}}
    return ApiResult.ServerError(400,"Sales export exceeded safe page limit")
}
private suspend fun exportSalesRegister(api:DseErpHttpClient,filter:SalesFilter,format:String):String=when(val x=exportSalesCsv(api,filter)){
    is ApiResult.Success->{val headers=listOf("Invoice","Date","Customer","GSTIN","Amount","Paid","Outstanding","Due","Document Status","Payment Status","Return Status","Email","WhatsApp");val rows=x.value.map{r->listOf(r.invoiceNo,r.invoiceDate,r.customer?.name.orEmpty(),r.customer?.gstin.orEmpty(),r.totalAmount.toString(),r.paidAmount.toString(),(r.totalAmount-r.paidAmount).coerceAtLeast(0.0).toString(),r.dueDate.orEmpty(),r.documentStatus.orEmpty(),r.paymentStatus.orEmpty(),r.returnStatus.orEmpty(),if(r.emailSent)"SENT" else "PENDING",if(r.whatsappSent)"SENT" else "PENDING")};if(platformShareTabularExport("Sales Register","${businessName().replace(" ","_")}_Sales_Register",headers,rows,format))"Sales $format export prepared" else "Unable to prepare Sales $format export"}
    else->x.readableMessage()
}
private suspend fun exportPurchasesCsv(api:DseErpHttpClient,filter:PurchaseFilter):ApiResult<List<PurchaseRecord>>{
    val rows=mutableListOf<PurchaseRecord>();var page=0
    while(page<1000){when(val r=api.purchasesPage(page,100,filter)){is ApiResult.Success->{rows+=r.value.rows;if(page+1>=r.value.totalPages)return ApiResult.Success(rows);page++};else->return r.mapFailure()}}
    return ApiResult.ServerError(400,"Purchase export exceeded safe page limit")
}
private suspend fun exportPurchaseRegister(api:DseErpHttpClient,filter:PurchaseFilter,format:String):String=when(val x=exportPurchasesCsv(api,filter)){
    is ApiResult.Success->{val headers=listOf("Invoice","Date","Supplier","GSTIN","Currency","Amount","Paid","Outstanding","Due","Document Status","Payment Status","Return Status","Email");val rows=x.value.map{r->listOf(r.invoiceNo,r.invoiceDate,r.supplier?.name.orEmpty(),r.supplier?.gstin.orEmpty(),r.currency.orEmpty(),r.totalAmount.toString(),r.paidAmount.toString(),(r.totalAmount-r.paidAmount).coerceAtLeast(0.0).toString(),r.dueDate.orEmpty(),r.documentStatus.orEmpty(),r.paymentStatus.orEmpty(),r.returnStatus.orEmpty(),if(r.emailSent)"SENT" else "PENDING")};if(platformShareTabularExport("Purchase Register","${businessName().replace(" ","_")}_Purchase_Register",headers,rows,format))"Purchase $format export prepared" else "Unable to prepare Purchase $format export"}
    else->x.readableMessage()
}
private fun <T> ApiResult<*>.mapFailure():ApiResult<T> = when(this){
    is ApiResult.NetworkError->ApiResult.NetworkError(message,requestMayHaveReachedServer)
    is ApiResult.DecodeError->ApiResult.DecodeError(message,status)
    is ApiResult.Unauthorized->ApiResult.Unauthorized(message)
    is ApiResult.Forbidden->ApiResult.Forbidden(message)
    is ApiResult.Conflict->ApiResult.Conflict(message)
    is ApiResult.NotFound->ApiResult.NotFound(message)
    is ApiResult.ServerError->ApiResult.ServerError(status,message)
    is ApiResult.UnsafeEndpoint->ApiResult.UnsafeEndpoint(message)
    is ApiResult.NotImplemented->ApiResult.NotImplemented(message)
    is ApiResult.Success<*>->ApiResult.ServerError(500,"Unexpected export state")
}
