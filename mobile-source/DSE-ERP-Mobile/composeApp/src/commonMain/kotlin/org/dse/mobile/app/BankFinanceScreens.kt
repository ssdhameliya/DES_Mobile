@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.dse.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dse.mobile.core.api.*
import org.dse.mobile.core.model.*
import org.dse.mobile.core.offline.OfflineRepository
import kotlin.math.abs
import kotlin.math.round

private enum class BankMode(val label:String){FINANCE("Bank & Expense"),STATEMENT("Bank Statement")}

@Composable
internal fun BankFinanceWorkspace(api:DseErpHttpClient,p:PermissionContext,username:String,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    var mode by remember{mutableStateOf(BankMode.STATEMENT)}
    Column(Modifier.fillMaxSize()){
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal=12.dp,vertical=6.dp)){BankMode.entries.forEachIndexed{i,m->SegmentedButton(selected=mode==m,onClick={mode=m},shape=SegmentedButtonDefaults.itemShape(i,BankMode.entries.size)){Text(m.label)}}}
        LaunchedEffect(openTarget?.moduleKey){if(openTarget?.moduleKey?.uppercase()=="BANK_STATEMENT")mode=BankMode.STATEMENT else if(openTarget?.moduleKey?.uppercase() in setOf("FINANCE","BANK","EXPENSE"))mode=BankMode.FINANCE}
        Box(Modifier.fillMaxWidth().weight(1f)){
            when(mode){BankMode.FINANCE->FinanceRegister(api,p,openTarget,onTargetConsumed);BankMode.STATEMENT->BankStatementWorkspace(api,p,username)}
        }
    }
}

@Composable
private fun FinanceRegister(api:DseErpHttpClient,p:PermissionContext,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    var page by remember{mutableIntStateOf(0)};var filter by remember{mutableStateOf(FinanceFilter())};var data by remember{mutableStateOf<FinancePage?>(null)};var metrics by remember{mutableStateOf<FinanceMetrics?>(null)};var msg by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)};var selected by remember{mutableStateOf<FinanceRecord?>(null)};var editor by remember{mutableStateOf<FinanceRecord?>(null)};var creating by remember{mutableStateOf(false)};var confirm by remember{mutableStateOf<FinanceRecord?>(null)};var actionTarget by remember{mutableStateOf<FinanceRecord?>(null)};var filterOpen by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    fun loadFull(row:FinanceRecord,after:(FinanceRecord)->Unit){scope.launch{val id=row.id?:return@launch;when(val r=api.financeById(id)){is ApiResult.Success->after(r.value);else->msg=r.readableMessage()}}}
    suspend fun save(rec:FinanceRecord,create:Boolean){
        when(val h=api.health()){
            is ApiResult.NetworkError->{if(create){msg="ERP is offline. New Finance entries are not queued automatically because v10.0.5 has no idempotency key. Reconnect and press Save again."}else{OfflineRepository.enqueueFinance(rec,false);msg="Pending Local Sync — this row-versioned Finance update was not sent because ERP is offline.";editor=null}}
            is ApiResult.Success->{val r=if(create)api.createFinance(rec)else api.updateFinance(rec);when(r){is ApiResult.Success->{msg=if(create)"Created ${r.value.voucherNo}" else "Finance entry updated";creating=false;editor=null;refresh++};else->msg=r.readableMessage()}}
            else->msg=h.readableMessage()
        }
    }
    LaunchedEffect(openTarget?.moduleKey,openTarget?.recordId,openTarget?.reference){val t=openTarget;if(t!=null&&t.moduleKey.uppercase() in setOf("FINANCE","BANK","EXPENSE")){if(t.reference=="__CREATE__"){creating=true;onTargetConsumed()}else{val id=t.recordId?.toIntOrNullSafe();if(id!=null){when(val r=api.financeById(id)){is ApiResult.Success->{selected=r.value;onTargetConsumed()};else->{msg=r.readableMessage();onTargetConsumed()}}}else if(t.reference.isNotBlank()){filter=filter.copy(q=t.reference);page=0;onTargetConsumed()}}}}
    LaunchedEffect(api,page,filter,refresh){when(val r=api.financePage(page,25,filter)){is ApiResult.Success->{data=r.value;msg="${r.value.totalRows} finance entry(s)${if(r.source==ApiDataSource.CACHE)" • cached" else ""}"};else->msg=r.readableMessage()};metrics=(api.financeMetrics() as? ApiResult.Success)?.value}
    DseRegisterShell("Bank & Expense","Cash, bank and business expenses in one place",filter.q,{filter=filter.copy(q=it);page=0},msg,{refresh++},if(p.can("BANK_EXPENSE","CREATE")){{creating=true}}else null,
        kpis={metrics?.let{m->Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseHeroKpi("Bank Balance",money(m.bankBalance),"${m.bankEntries} finance entries",Icons.Rounded.AccountBalance);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Credits",money(m.credits),Icons.Rounded.SouthWest,Modifier.weight(1f),DseSuccess);DseMetricTile("Debits",money(m.debits),Icons.Rounded.NorthEast,Modifier.weight(1f),DseDanger)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Expense Month",money(m.expenseMonth),Icons.Rounded.ReceiptLong,Modifier.weight(1f),DseWarning);DseMetricTile("Pending Recon",money(m.pendingReconcileAmount),Icons.Rounded.FactCheck,Modifier.weight(1f),DseInfo)};PremiumSecondaryButton("Filters",{filterOpen=true},icon=Icons.Rounded.FilterAlt)}}},footer={PagingControls(page,data?.totalPages?:1,data?.totalRows?:0){page=it}}){
        if(data==null){
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Loading bank and expense records…",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }else if(data?.rows.orEmpty().isEmpty()){
            DseEmptyRegisterState("No bank or expense records match this view","Change the filters, then refresh.",Icons.Rounded.AccountBalance)
        }
        data?.rows?.forEach{r->DseRecordCard(
            r.voucherNo,r.category?:r.voucherType,money(r.amount),listOf("Reconcile" to if(r.reconciled)"RECONCILED" else "PENDING"),
            meta="${r.voucherDate} • ${r.paymentMode.orEmpty()} • ${r.accountName.orEmpty()}",
            swipeStartActions=buildList{
                add(SwipeAction("Open",Icons.Rounded.Visibility){loadFull(r){selected=it}})
                if(!r.reconciled&&p.can("BANK_EXPENSE","EDIT"))add(SwipeAction("Edit",Icons.Rounded.Edit){loadFull(r){editor=it}})
            },
            swipeEndActions=buildList{
                if(!r.reconciled&&p.can("BANK_EXPENSE","DELETE"))add(SwipeAction("Delete",Icons.Rounded.Delete,true){loadFull(r){confirm=it}})
            },
            onActions={actionTarget=r},
        ){loadFull(r){selected=it}}}
    }
    actionTarget?.let{r->
        DseRecordActionSheet("Finance ${r.voucherNo}","Bank & Expense actions",buildList{
            add(PremiumActionSpec("View Finance Entry",{loadFull(r){selected=it}}))
            if(p.can("BANK_EXPENSE","EDIT"))add(PremiumActionSpec("Edit Finance Entry",{loadFull(r){editor=it}},enabled=!r.reconciled,disabledReason="Reconciled entries are locked to protect the bank audit trail."))
            if(r.linkedDocumentNo?.isNotBlank()==true)add(PremiumActionSpec("Open Linked ERP",{platformOpenExternalUrl("dseerp://${linkedRoute(r.linkedTargetType)}/${encodeBank(r.linkedDocumentNo.orEmpty())}")}))
            if(p.can("BANK_EXPENSE","DELETE"))add(PremiumActionSpec("Delete Finance Entry",{loadFull(r){confirm=it}},destructive=true,enabled=!r.reconciled,disabledReason="Reconciled entries cannot be deleted."))
        },{actionTarget=null})
    }
    if(filterOpen)FinanceFilterDialog(filter,{filterOpen=false}){filter=it;page=0;filterOpen=false}
    selected?.let{r->DetailDialog("Finance ${r.voucherNo}",{selected=null},{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){detailFinanceRows(r);if(!r.billPath.isNullOrBlank())Text("Bill / Proof: ${r.billPath}",style=MaterialTheme.typography.bodySmall);Text("Actions",fontWeight=FontWeight.SemiBold);PremiumActionGrid(buildList{if(!r.reconciled&&p.can("BANK_EXPENSE","EDIT"))add(PremiumActionSpec("Edit",{editor=r;selected=null}));if(!r.reconciled&&p.can("BANK_EXPENSE","DELETE"))add(PremiumActionSpec("Delete",{confirm=r;selected=null},true));if(r.linkedDocumentNo?.isNotBlank()==true)add(PremiumActionSpec("Open Linked ERP",{platformOpenExternalUrl("dseerp://${linkedRoute(r.linkedTargetType)}/${encodeBank(r.linkedDocumentNo.orEmpty())}")}))})}})}
    if(creating)FinanceEditorDialog(api,null,{creating=false}){rec->scope.launch{save(rec,true)}}
    editor?.let{old->FinanceEditorDialog(api,old,{editor=null}){rec->scope.launch{save(rec,false)}}}
    confirm?.let{r->ConfirmDialog("Delete Finance Entry","Delete ${r.voucherNo}? Reconciled/linked entries are protected by the v10.0.5 server.","Delete",true,{confirm=null}){scope.launch{when(val x=api.deleteFinance(r.id?:0,r.rowVersion)){is ApiResult.Success->{msg=x.value.message;confirm=null;refresh++};else->msg=x.readableMessage()}}}}
}

@Composable
private fun FinanceFilterDialog(current:FinanceFilter,onClose:()->Unit,onApply:(FinanceFilter)->Unit){var d by remember{mutableStateOf(current)};PremiumAlertDialog(onDismissRequest=onClose,title={Text("Bank & Expense Filters")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseSelect("Type",d.type,listOf("ALL","BANK DEPOSIT","BANK WITHDRAWAL","EXPENSE"),onValue={d=d.copy(type=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("Period",d.period,listOf("ALL","TODAY","THIS WEEK","THIS MONTH","THIS YEAR"),onValue={d=d.copy(period=it.takeUnless{x->x=="ALL"}.orEmpty())});DseField("Payment mode",d.mode,singleLine=true,onValue={d=d.copy(mode=it)})}},confirmButton={Button(onClick={onApply(d)}){Text("Apply")}},dismissButton={Row{TextButton(onClick={onApply(FinanceFilter())}){Text("Reset")};TextButton(onClick=onClose){Text("Cancel")}}})}

@Composable
private fun FinanceEditorDialog(api:DseErpHttpClient,current:FinanceRecord?,onClose:()->Unit,onSave:(FinanceRecord)->Unit){
    var type by remember{mutableStateOf(current?.voucherType?:"BANK DEPOSIT")};var date by remember{mutableStateOf(current?.voucherDate?.ifBlank{todayIso()}?:todayIso())};var category by remember{mutableStateOf(current?.category.orEmpty())};var amount by remember{mutableStateOf((current?.amount?:0.0).takeIf{it>0}?.toString().orEmpty())};var mode by remember{mutableStateOf(current?.paymentMode.orEmpty())};var account by remember{mutableStateOf(current?.accountName.orEmpty())};var ref by remember{mutableStateOf(current?.referenceNo.orEmpty())};var notes by remember{mutableStateOf(current?.notes.orEmpty())};var categories by remember{mutableStateOf<List<String>>(emptyList())};var modes by remember{mutableStateOf<List<String>>(emptyList())};var accounts by remember{mutableStateOf<List<String>>(emptyList())};var next by remember{mutableStateOf(current?.voucherNo.orEmpty())};var msg by remember{mutableStateOf("")}
    LaunchedEffect(current){categories=(api.lookupValuesByCode("EXPENSE_CATEGORY") as? ApiResult.Success)?.value.orEmpty();modes=(api.lookupValuesByCode("PAYMENT_MODE") as? ApiResult.Success)?.value.orEmpty();accounts=(api.lookupValuesByCode("BANK_ACCOUNT") as? ApiResult.Success)?.value.orEmpty();if(mode.isBlank())mode=modes.firstOrNull().orEmpty();if(account.isBlank())account=accounts.firstOrNull().orEmpty();if(current==null)next=(api.nextFinanceNumber() as? ApiResult.Success)?.value?.value.orEmpty()}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"New Bank / Expense Entry" else "Edit ${current.voucherNo}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){if(next.isNotBlank())DseField("Voucher No",next,readOnly=true,singleLine=true,onValue={});DseSelect("Entry Type",type,listOf("BANK DEPOSIT","BANK WITHDRAWAL","EXPENSE"),required=true,onValue={type=it});DseDateField("Date",date,required=true,onValue={date=it});if(type=="EXPENSE")DseSelect("Expense Category",category,categories,required=true,onValue={category=it});DseSelect("Account",account,accounts,required=true,onValue={account=it});DseSelect("Payment Mode",mode,modes,required=true,onValue={mode=it});DseNumberField("Amount",amount,min=0.01,required=true,onValue={amount=it});DseField("Reference",ref,singleLine=true,onValue={ref=it});DseField("Description / Notes",notes,onValue={notes=it});if(msg.isNotBlank())Text(msg,color=MaterialTheme.colorScheme.error)}},confirmButton={Button(enabled=(amount.toDoubleOrNull()?:0.0)>0&&account.isNotBlank()&&mode.isNotBlank()&&(type!="EXPENSE"||category.isNotBlank()),onClick={onSave((current?:FinanceRecord()).copy(voucherType=type,voucherDate=date,category=category.takeIf{type=="EXPENSE"},amount=amount.toDoubleOrNull()?:0.0,paymentMode=mode,accountName=account,referenceNo=ref,notes=notes))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable
private fun BankStatementWorkspace(api:DseErpHttpClient,p:PermissionContext,username:String){
    val canRecon=p.can("BANK_EXPENSE","RECONCILE");val canDelete=p.can("BANK_EXPENSE","DELETE")
    var batchPage by remember{mutableIntStateOf(0)};var batchFilter by remember{mutableStateOf(BankBatchFilter())};var batches by remember{mutableStateOf<BankBatchPage?>(null)};var batch by remember{mutableStateOf<BankBatch?>(null)};var batchFilterOpen by remember{mutableStateOf(false)}
    var txPage by remember{mutableIntStateOf(0)};var txFilter by remember{mutableStateOf(BankTransactionFilter())};var txs by remember{mutableStateOf<BankTransactionPage?>(null)};var txFilterOpen by remember{mutableStateOf(false)};var msg by remember{mutableStateOf("")};var dismissedMessage by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)};var selected by remember{mutableStateOf<BankTransaction?>(null)};var actionTarget by remember{mutableStateOf<BankTransaction?>(null)};val selectedIds=remember{mutableStateListOf<Long>()};var bulk by remember{mutableStateOf<String?>(null)};var deleteBatch by remember{mutableStateOf<BankBatch?>(null)};var source by remember{mutableStateOf<BankSource?>(null)};val scope=rememberCoroutineScope()
    LaunchedEffect(api,batchPage,batchFilter,refresh){when(val r=api.bankBatches(batchPage,25,batchFilter)){is ApiResult.Success->{batches=r.value;if(batch==null||r.value.rows.none{it.id==batch?.id})batch=r.value.rows.firstOrNull()};else->msg=r.readableMessage()}}
    LaunchedEffect(batch?.id,txPage,txFilter,refresh){val id=batch?.id?:return@LaunchedEffect;when(val r=api.bankTransactions(id,txPage,50,txFilter)){is ApiResult.Success->{txs=r.value;msg="${r.value.totalRows} transaction(s) • ${r.value.metrics?.reconciledPercent?:0.0}% reconciled${if(r.source==ApiDataSource.CACHE)" • cached" else ""}";selectedIds.retainAll(r.value.rows.map{it.id}.toSet())};else->msg=r.readableMessage()}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){PremiumIconTile(Icons.Rounded.AccountBalance,DseInfo,size=34.dp);Column{Text("Bank Statement",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold);Text("Transactions, matching and audit",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};FilledTonalIconButton(onClick={refresh++},modifier=Modifier.size(38.dp)){Icon(Icons.Rounded.Refresh,"Refresh",Modifier.size(19.dp))}}
        if(batches?.rows.orEmpty().isEmpty())Text("No imported bank statements found.") else {
            DseSelect("Statement Batch",batch?.let{bankBatchLabel(it)}.orEmpty(),batches?.rows.orEmpty().map(::bankBatchLabel),onValue={label->batch=batches?.rows.orEmpty().firstOrNull{bankBatchLabel(it)==label};txPage=0;selectedIds.clear()})
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={batchFilterOpen=true}){Icon(Icons.Rounded.FilterAlt,null);Text("Statement Filters")};OutlinedButton(enabled=batch!=null,onClick={scope.launch{val id=batch?.id?:return@launch;when(val r=api.bankSource(id)){is ApiResult.Success->source=r.value;else->msg=r.readableMessage()}}}){Text("Source")};if(canDelete&&batch!=null)OutlinedButton(onClick={deleteBatch=batch}){Text("Delete Statement")}}
            PagingControls(batchPage,batches?.totalPages?:1,batches?.totalRows?:0){batchPage=it;batch=null}
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={txFilterOpen=true}){Icon(Icons.Rounded.FilterAlt,null);Text("Transaction Filters")};Text("${selectedIds.size} selected",fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=txs?.rows.orEmpty().isNotEmpty()&&selectedIds.size==txs?.rows.orEmpty().size,onCheckedChange={checked->selectedIds.clear();if(checked)selectedIds.addAll(txs?.rows.orEmpty().map{it.id})});Text("Select All Visible",fontWeight=FontWeight.SemiBold)}
            if(canRecon&&selectedIds.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton(onClick={bulk="review"}){Text("Reviewed")};OutlinedButton(onClick={bulk="ignore"}){Text("Ignore")};Button(onClick={bulk="expense"}){Text("Move to Expense")};Button(onClick={bulk="entry"}){Text("Move to Bank Entry")}}
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(7.dp)){
                if(txs==null){
                    DseLoadingState("Loading bank transactions…")
                }else if(txs?.rows.orEmpty().isEmpty()){
                    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(.35f),modifier=Modifier.fillMaxWidth()){
                        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            Icon(Icons.Rounded.ReceiptLong,null,tint=MaterialTheme.colorScheme.primary)
                            Column{Text("No transactions in this view",fontWeight=FontWeight.Bold);Text("Change the statement or transaction filters and refresh.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                    }
                }else{
                    txs?.rows?.forEach{t->DseRecordCard(if(t.credit>0)"Credit ${money(t.credit,batch?.currency?:"INR")}" else "Debit ${money(t.debit,batch?.currency?:"INR")}",t.description,if(t.credit>0)money(t.credit,batch?.currency?:"INR") else money(t.debit,batch?.currency?:"INR"),listOf("Status" to t.status),meta="${t.transactionDate} • ${t.reference}",selected=t.id in selectedIds,onSelect={if(t.id in selectedIds)selectedIds.remove(t.id)else selectedIds.add(t.id)},onActions={actionTarget=t}){selected=t}}
                }
            }
            PagingControls(txPage,txs?.totalPages?:1,txs?.totalRows?:0){txPage=it};if(msg.isNotBlank()&&noticeKindFor(msg)==null)Text(msg,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
        }
    }
    noticeKindFor(msg)?.let{kind->if(msg!=dismissedMessage)DseNoticeDialog(msg,kind){dismissedMessage=msg}}
    actionTarget?.let{t->
        val canRecon=p.can("BANK_EXPENSE","RECONCILE")
        DseRecordActionSheet("Bank Transaction • ${t.transactionDate}","Statement transaction actions",buildList{
            add(PremiumActionSpec(if(canRecon)"View / Reconcile" else "View Transaction",{selected=t}))
            add(PremiumActionSpec(if(t.id in selectedIds)"Remove from Bulk Selection" else "Add to Bulk Selection",{if(t.id in selectedIds)selectedIds.remove(t.id)else selectedIds.add(t.id)}))
            if(!t.linkedDocumentNo.isNullOrBlank())add(PremiumActionSpec("Open Linked ERP",{platformOpenExternalUrl("dseerp://${linkedRoute(t.linkedTargetType)}/${encodeBank(t.linkedDocumentNo.orEmpty())}")}))
            add(PremiumActionSpec("Bank Audit Trail",{selected=t},icon=Icons.Rounded.Timeline))
        },{actionTarget=null})
    }
    if(batchFilterOpen)BankBatchFilterDialog(batchFilter,{batchFilterOpen=false}){batchFilter=it;batchPage=0;batch=null;batchFilterOpen=false}
    if(txFilterOpen)BankTransactionFilterDialog(txFilter,{txFilterOpen=false}){txFilter=it;txPage=0;selectedIds.clear();txFilterOpen=false}
    selected?.let{t->BankTransactionDialog(api,t,p,username,{selected=null}){msg=it;selected=null;refresh++}}
    bulk?.let{action->if(canRecon)BulkBankDialog(api,action,selectedIds.toList(),username,{bulk=null}){msg=it;bulk=null;selectedIds.clear();refresh++} else bulk=null}
    deleteBatch?.let{b->TypedBankDeleteDialog(b,{deleteBatch=null}){scope.launch{when(val r=api.deleteBankBatch(b.id,username)){is ApiResult.Success->{msg="${r.value.message} • ${r.value.deletedTransactions} transactions deleted • ${r.value.reversedTransactions} reversed";deleteBatch=null;batch=null;refresh++};else->msg=r.readableMessage()}}}}
    source?.let{s->PremiumAlertDialog(onDismissRequest={source=null},title={Text("Statement Source")},text={Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState())){detailFinanceRows(listOf("File" to s.fileName,"Fingerprint" to s.fingerprint));Text("Source content is preserved by the server for audit.",style=MaterialTheme.typography.bodySmall)}},confirmButton={Button(onClick={platformShareText("Bank Statement ${s.fileName}",s.csvContent)}){Text("Share Source")}},dismissButton={TextButton(onClick={source=null}){Text("Close")}})}
}

@Composable private fun BankBatchFilterDialog(current:BankBatchFilter,onClose:()->Unit,onApply:(BankBatchFilter)->Unit){var d by remember{mutableStateOf(current)};PremiumAlertDialog(onDismissRequest=onClose,title={Text("Statement Filters")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Search",d.q,singleLine=true,onValue={d=d.copy(q=it)});DseField("Account",d.account,singleLine=true,onValue={d=d.copy(account=it)});DseSelect("Status",d.status,listOf("ALL","OPEN","PARTIAL","RECONCILED"),onValue={d=d.copy(status=it.takeUnless{x->x=="ALL"}.orEmpty())});DseDateField("From",d.fromDate,onValue={d=d.copy(fromDate=it)});DseDateField("To",d.toDate,onValue={d=d.copy(toDate=it)})}},confirmButton={Button(onClick={onApply(d)}){Text("Apply")}},dismissButton={Row{TextButton(onClick={onApply(BankBatchFilter())}){Text("Reset")};TextButton(onClick=onClose){Text("Cancel")}}})}
@Composable private fun BankTransactionFilterDialog(current:BankTransactionFilter,onClose:()->Unit,onApply:(BankTransactionFilter)->Unit){var d by remember{mutableStateOf(current)};PremiumAlertDialog(onDismissRequest=onClose,title={Text("Transaction Filters")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Search",d.q,singleLine=true,onValue={d=d.copy(q=it)});DseSelect("Status",d.status,listOf("ALL","UNMATCHED","PARTIAL","MATCHED","REVIEWED","IGNORED","EXPENSE","BANK ENTRY"),onValue={d=d.copy(status=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("Direction",d.direction,listOf("ALL","CREDIT","DEBIT"),onValue={d=d.copy(direction=it)});DseDateField("From",d.fromDate,onValue={d=d.copy(fromDate=it)});DseDateField("To",d.toDate,onValue={d=d.copy(toDate=it)})}},confirmButton={Button(onClick={onApply(d)}){Text("Apply")}},dismissButton={Row{TextButton(onClick={onApply(BankTransactionFilter())}){Text("Reset")};TextButton(onClick=onClose){Text("Cancel")}}})}

@Composable
private fun BankTransactionDialog(
    api: DseErpHttpClient,
    t: BankTransaction,
    p: PermissionContext,
    username: String,
    onClose: () -> Unit,
    onDone: (String) -> Unit
) {
    val canRecon = p.can("BANK_EXPENSE", "RECONCILE")
    var candidates by remember { mutableStateOf<List<BankCandidate>>(emptyList()) }
    val selected = remember { mutableStateListOf<BankCandidate>() }
    val allocations = remember { mutableStateMapOf<Int, String>() }
    var note by remember { mutableStateOf("") }
    val reconEditable = canRecon && t.status.uppercase() in setOf("UNMATCHED", "SUGGESTED", "REVIEW")
    var mode by remember(t.id,t.status,canRecon) { mutableStateOf(if (reconEditable) "match" else "audit") }
    var category by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var modes by remember { mutableStateOf<List<String>>(emptyList()) }
    var audit by remember { mutableStateOf<List<BankAudit>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val amount = roundBank(if (t.credit > 0) t.credit else t.debit)

    LaunchedEffect(t.id) {
        if (reconEditable) {
            when (val r = api.bankCandidates(t.id)) {
                is ApiResult.Success -> candidates = r.value.filter { it.type.uppercase() != "PURCHASE_RECON" || p.can("PURCHASE_RECON","MATCH") }
                else -> when (val suggestion = api.bankSuggest(t.id)) {
                    is ApiResult.Success -> candidates = suggestion.value.filter { it.type.uppercase() != "PURCHASE_RECON" || p.can("PURCHASE_RECON","MATCH") }
                    else -> msg = suggestion.readableMessage()
                }
            }
        }
        categories = (api.lookupValuesByCode("EXPENSE_CATEGORY") as? ApiResult.Success)?.value.orEmpty()
        accounts = (api.lookupValuesByCode("BANK_ACCOUNT") as? ApiResult.Success)?.value.orEmpty()
        modes = (api.lookupValuesByCode("PAYMENT_MODE") as? ApiResult.Success)?.value.orEmpty()
        account = accounts.firstOrNull().orEmpty()
        paymentMode = modes.firstOrNull().orEmpty()
        audit = (api.bankAudit(t.id) as? ApiResult.Success)?.value.orEmpty()
    }

    val options = if (reconEditable) listOf("match", "expense", "entry", "review", "ignore", "note", "audit") else listOf("audit")
    PremiumAlertDialog(
        onDismissRequest = onClose,
        title = { Text("Bank Transaction • ${t.transactionDate}") },
        text = {
            Column(
                Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detailFinanceRows(
                    listOf(
                        "Description" to t.description,
                        "Reference" to t.reference,
                        "Amount" to money(amount),
                        "Balance" to money(t.balance),
                        "Status" to t.status,
                        "Linked Document" to t.linkedDocumentNo.orEmpty()
                    )
                )
                SingleChoiceSegmentedButtonRow {
                    options.forEachIndexed { i, v ->
                        SegmentedButton(
                            selected = mode == v,
                            onClick = { mode = v },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size)
                        ) { Text(v.replaceFirstChar { it.uppercase() }) }
                    }
                }
                when (mode) {
                    "match" -> {
                        Text("ERP Match Candidates", fontWeight = FontWeight.SemiBold)
                        if (candidates.isEmpty()) {
                            Text(
                                "No candidate found. Use Review rather than entering an internal Target ID.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            candidates.forEach { candidate ->
                                val checked = candidate in selected
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = { isChecked ->
                                                    if (isChecked && !checked) {
                                                        val used = roundBank(selected.sumOf { allocations[it.id]?.toDoubleOrNull() ?: 0.0 })
                                                        val remaining = (amount - used).coerceAtLeast(0.0)
                                                        if (remaining > 0.005) {
                                                            selected.add(candidate)
                                                            allocations[candidate.id] = roundBank(minOf(candidate.outstanding, remaining)).toString()
                                                        }
                                                    } else if (!isChecked) {
                                                        selected.remove(candidate)
                                                        allocations.remove(candidate.id)
                                                    }
                                                }
                                            )
                                            Column {
                                                Text("${candidate.documentNo} • ${candidate.partyName}", fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "${candidate.type} • ${candidate.documentDate} • Outstanding ${money(candidate.outstanding)} • confidence ${(candidate.confidence * 100).toInt()}%",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        if (checked) {
                                            val other = roundBank(
                                                selected.filter { it.id != candidate.id }
                                                    .sumOf { allocations[it.id]?.toDoubleOrNull() ?: 0.0 }
                                            )
                                            DseNumberField(
                                                "Allocate",
                                                allocations[candidate.id].orEmpty(),
                                                min = 0.01,
                                                required = true,
                                                max = (amount - other).coerceAtLeast(0.01),
                                                onValue = { allocations[candidate.id] = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        val allocated = roundBank(selected.sumOf { allocations[it.id]?.toDoubleOrNull() ?: 0.0 })
                        Text(
                            "Allocated ${money(allocated)} / ${money(amount)}" +
                                if (abs(allocated - amount) > 0.009) " • must equal transaction amount" else " • balanced",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (abs(allocated - amount) > 0.009) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    "expense" -> {
                        DseSelect("Expense Category", category, categories, required=true, onValue = { category = it })
                        DseSelect("Expense Account", account, accounts, required=true, onValue = { account = it })
                        DseSelect("Payment Mode", paymentMode, modes, required=true, onValue = { paymentMode = it })
                        DseField("Notes", note, onValue = { note = it })
                    }

                    "entry" -> {
                        DseSelect("Bank Account", account, accounts, required=true, onValue = { account = it })
                        DseSelect("Payment Mode", paymentMode, modes, required=true, onValue = { paymentMode = it })
                        DseField("Notes", note, onValue = { note = it })
                    }

                    "ignore" -> DseField("Ignore Reason", note, required=true, onValue = { note = it })
                    "review" -> DseField("Review Note", note, onValue = { note = it })
                    "note" -> {
                        Text("Notes are stored as a separate audit action so the financial Match remains atomic on Server v10.0.5.", style = MaterialTheme.typography.bodySmall)
                        DseField("Transaction Note", note, required=true, onValue = { note = it })
                    }
                    "audit" -> DseSection("Audit History", Icons.Rounded.History) {
                        if (audit.isEmpty()) Text("No audit entries")
                        audit.forEach { a ->
                            ListItem(
                                headlineContent = { Text("${a.eventType} • ${a.createdAt}") },
                                supportingContent = { Text("${a.detail} • ${a.previousStatus} → ${a.newStatus} • ${a.performedBy}") }
                            )
                        }
                        if (canRecon && t.status.uppercase() != "UNMATCHED") {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    when (val r = api.bankReverse(t.id, username)) {
                                        is ApiResult.Success -> onDone(r.value.message.ifBlank { "Reconciliation reversed" })
                                        else -> msg = r.readableMessage()
                                    }
                                }
                            }) { Text("Reverse / Reopen") }
                        }
                    }
                }
                if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            if (mode != "audit") {
                Button(
                    enabled = canRecon,
                    onClick = {
                        scope.launch {
                            val result: ApiResult<BankOperationResult> = when (mode) {
                                "match" -> {
                                    val requestAllocations = selected.mapNotNull { candidate ->
                                        val value = roundBank(allocations[candidate.id]?.toDoubleOrNull() ?: 0.0)
                                        if (value > 0) BankAllocationRequest(candidate.type, candidate.id, value) else null
                                    }
                                    val sum = roundBank(requestAllocations.sumOf { it.amount })
                                    if (requestAllocations.isEmpty() || abs(sum - amount) > 0.009) {
                                        msg = "Allocations must exactly equal ${money(amount)} before Apply"
                                        return@launch
                                    }
                                    api.bankMatch(t.id, username, requestAllocations, "")
                                }

                                "expense" -> {
                                    if (category.isBlank() || account.isBlank() || paymentMode.isBlank()) {
                                        msg = "Category, account and payment mode are required"
                                        return@launch
                                    }
                                    api.bankExpense(t.id, username, category, account, paymentMode, note)
                                }

                                "entry" -> {
                                    if (account.isBlank() || paymentMode.isBlank()) {
                                        msg = "Account and payment mode are required"
                                        return@launch
                                    }
                                    api.bankEntry(t.id, username, account, paymentMode, note)
                                }

                                "ignore" -> {
                                    if (note.isBlank()) {
                                        msg = "Ignore reason is required"
                                        return@launch
                                    }
                                    api.bankIgnore(t.id, username, note)
                                }

                                "note" -> {
                                    if (note.isBlank()) { msg = "Note is required"; return@launch }
                                    api.bankNote(t.id, username, note)
                                }
                                else -> api.bankReview(t.id, username, note)
                            }
                            when (result) {
                                is ApiResult.Success -> onDone(result.value.message.ifBlank { "Bank transaction updated" })
                                else -> msg = result.readableMessage()
                            }
                        }
                    }
                ) { Text("Apply") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun BulkBankDialog(api:DseErpHttpClient,action:String,ids:List<Long>,username:String,onClose:()->Unit,onDone:(String)->Unit){
    var note by remember{mutableStateOf("")};var category by remember{mutableStateOf("")};var account by remember{mutableStateOf("")};var mode by remember{mutableStateOf("")};var categories by remember{mutableStateOf<List<String>>(emptyList())};var accounts by remember{mutableStateOf<List<String>>(emptyList())};var modes by remember{mutableStateOf<List<String>>(emptyList())};var msg by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){categories=(api.lookupValuesByCode("EXPENSE_CATEGORY") as? ApiResult.Success)?.value.orEmpty();accounts=(api.lookupValuesByCode("BANK_ACCOUNT") as? ApiResult.Success)?.value.orEmpty();modes=(api.lookupValuesByCode("PAYMENT_MODE") as? ApiResult.Success)?.value.orEmpty();account=accounts.firstOrNull().orEmpty();mode=modes.firstOrNull().orEmpty()}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("${action.replaceFirstChar{it.uppercase()}} • ${ids.size} selected")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){if(action=="expense")DseSelect("Expense Category",category,categories,required=true,onValue={category=it});if(action=="expense"||action=="entry"){DseSelect("Account",account,accounts,required=true,onValue={account=it});DseSelect("Payment Mode",mode,modes,required=true,onValue={mode=it})};DseField(if(action=="ignore")"Reason" else "Notes",note,required=action=="ignore",onValue={note=it});if(msg.isNotBlank())Text(msg,color=MaterialTheme.colorScheme.error)}},confirmButton={Button(enabled=ids.isNotEmpty()&&(action!="ignore"||note.isNotBlank())&&(action!="expense"||(category.isNotBlank()&&account.isNotBlank()&&mode.isNotBlank()))&&(action!="entry"||(account.isNotBlank()&&mode.isNotBlank())),onClick={scope.launch{when(action){"expense"->when(val r=api.bankBulkExpense(BankBulkExpenseRequest(ids,category,account,mode,note,"",username))){is ApiResult.Success->onDone(r.value.message);else->msg=r.readableMessage()};"entry"->when(val r=api.bankBulkEntry(BankBulkEntryRequest(ids,account,mode,note,username))){is ApiResult.Success->onDone(r.value.message);else->msg=r.readableMessage()};"review","ignore"->{var ok=0;var failed=0;ids.forEach{id->val r=if(action=="review")api.bankReview(id,username,note)else api.bankIgnore(id,username,note);if(r is ApiResult.Success)ok++ else failed++};onDone("$ok/${ids.size} transaction(s) updated${if(failed>0)" • $failed failed" else ""}")}}}}){Text("Apply to Selected")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun TypedBankDeleteDialog(b:BankBatch,onClose:()->Unit,onDelete:()->Unit){var typed by remember{mutableStateOf("")};PremiumAlertDialog(onDismissRequest=onClose,title={Text("Delete Bank Statement")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Deleting ${b.bankName} • ${b.statementFrom} → ${b.statementTo} can reverse linked reconciliations. Type DELETE to continue.");DseField("Type DELETE",typed,singleLine=true,required=true,onValue={typed=it})}},confirmButton={Button(enabled=typed=="DELETE",colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error),onClick=onDelete){Text("Delete Statement")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}
@Composable private fun detailFinanceRows(r:FinanceRecord)=detailFinanceRows(listOf("Type" to r.voucherType,"Date" to r.voucherDate,"Category" to r.category.orEmpty(),"Amount" to money(r.amount),"Payment Mode" to r.paymentMode.orEmpty(),"Account" to r.accountName.orEmpty(),"Reference" to r.referenceNo.orEmpty(),"Notes" to r.notes.orEmpty(),"Linked Document" to r.linkedDocumentNo.orEmpty()))
@Composable private fun detailFinanceRows(rows:List<Pair<String,String>>){detailRows(rows)}
private fun bankBatchLabel(b:BankBatch)="${b.bankName.ifBlank{"Bank"}} • ${b.bankAccount.ifBlank{"Account"}} • ${b.statementFrom} → ${b.statementTo} • ${b.status}"
private fun roundBank(v:Double)=round(v*100.0)/100.0
private fun linkedRoute(type:String?)=when(type.orEmpty().uppercase()){"SALE"->"sales";"PURCHASE"->"purchase";"SALES_RETURN"->"sales-return";"PURCHASE_RETURN"->"purchase-return";else->"finance"}
private fun encodeBank(value:String):String=value.encodeToByteArray().joinToString(""){b->val n=b.toInt() and 0xff;val c=n.toChar();if((c in 'a'..'z')||(c in 'A'..'Z')||(c in '0'..'9')||c in "-_.~")c.toString() else "%"+n.toString(16).uppercase().padStart(2,'0')}

private fun Long.toIntOrNullSafe():Int?=takeIf{it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()}?.toInt()
