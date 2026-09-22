@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

private data class ReturnLineDraft(
    val sourceLineId:Long,
    val code:String,
    val description:String,
    val rate:Double,
    val invoiced:Double,
    val previouslyReturned:Double,
    val eligible:Double,
    val qty:String="0",
    val reason:String="",
)

@Composable
internal fun ReturnsWorkspace(api:DseErpHttpClient,p:PermissionContext,sales:Boolean,username:String,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={},onDeepLink:(String)->Unit={}){
    val type=if(sales)"SALES RETURN" else "PURCHASE RETURN"
    val module=if(sales)"SALES" else "PURCHASE"
    var page by remember{mutableIntStateOf(0)}
    var filter by remember{mutableStateOf(ReturnFilter())}
    var data by remember{mutableStateOf<ReturnPage?>(null)}
    var settlements by remember{mutableStateOf<List<ReturnSettlement>>(emptyList())}
    var msg by remember{mutableStateOf("")}
    var refresh by remember{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<ReturnSummary?>(null)}
    var details by remember{mutableStateOf<ReturnDetails?>(null)}
    var reject by remember{mutableStateOf<ReturnSummary?>(null)}
    var refund by remember{mutableStateOf<ReturnDetails?>(null)}
    var edit by remember{mutableStateOf<ReturnDetails?>(null)}
    var attach by remember{mutableStateOf<ReturnDetails?>(null)}
    var confirm by remember{mutableStateOf<Pair<String,ReturnSummary>?>(null)}
    var actionTarget by remember{mutableStateOf<ReturnSummary?>(null)}
    var actionDetails by remember{mutableStateOf<ReturnDetails?>(null)}
    var auditTarget by remember{mutableStateOf<ReturnDetails?>(null)}
    var emailTarget by remember{mutableStateOf<ReturnDetails?>(null)}
    var filterOpen by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()

    LaunchedEffect(openTarget?.moduleKey,openTarget?.reference,openTarget?.action){
        val t=openTarget;val keys=if(sales)setOf("SALES_RETURN","SALE_RETURN") else setOf("PURCHASE_RETURN")
        if(t!=null&&t.moduleKey.uppercase() in keys&&t.reference.isNotBlank()){
            when(val r=api.returnDetails(t.reference)){
                is ApiResult.Success->{
                    val d=r.value
                    val summary=ReturnSummary(no=d.no,date=d.date,invoice=d.invoice,party=d.party,total=d.total,refund=d.refund,status=d.status,refundStatus=d.refundStatus)
                    if(t.action==LinkedRecordAction.REFUND){
                        val remaining=(d.total-d.refund).coerceAtLeast(0.0)
                        if(d.status.equals("APPROVED",true)&&remaining>0.005&&p.can(module,"EDIT")) refund=d
                        else { details=d;selected=summary;msg=when{!d.status.equals("APPROVED",true)->"Refund is available after the Return is approved.";remaining<=0.005->"This Return is already fully refunded.";else->"You do not have permission to record this refund."} }
                    } else { details=d;selected=summary }
                    onTargetConsumed()
                }
                else->{msg=r.readableMessage();onTargetConsumed()}
            }
        }
    }
    LaunchedEffect(api,page,filter,refresh){
        when(val r=api.returnsPage(type,page,25,filter)){
            is ApiResult.Success->{data=r.value;msg="${r.value.totalRows} ${type.lowercase()} record(s)${if(r.source==ApiDataSource.CACHE)" • cached" else ""}"}
            else->msg=r.readableMessage()
        }
        settlements=(api.returnSettlements(type) as? ApiResult.Success)?.value.orEmpty()
    }

    DseRegisterShell(
        if(sales)"Sales Returns" else "Purchase Returns",
        "Original document → pending approval → approve/reject → auditable refund ledger",
        filter.q,{filter=filter.copy(q=it);page=0},msg,{refresh++},null,
        kpis={data?.metrics?.let{m->Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            DseHeroKpi(if(sales)"Sales Returns" else "Purchase Returns",money(m.total),"${m.count} return document(s)",Icons.Rounded.AssignmentReturn);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("This Month",money(m.monthAmount),Icons.Rounded.CalendarMonth,Modifier.weight(1f),DseInfo);DseMetricTile("Approved",money(m.approvedAmount),Icons.Rounded.CheckCircle,Modifier.weight(1f),DseSuccess)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Refunded",money(m.refundAmount),Icons.Rounded.Payments,Modifier.weight(1f),DsePurple);DseMetricTile("Average",money(m.average),Icons.Rounded.Analytics,Modifier.weight(1f),DseWarning)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){PremiumSecondaryButton("Filters",{filterOpen=true},Modifier.weight(1f),icon=Icons.Rounded.FilterAlt);PremiumSecondaryButton("Export CSV",{scope.launch{when(val x=exportReturnsCsv(api,type,filter)){is ApiResult.Success->msg=if(shareReturnsCsv(x.value,sales))"Return CSV ready to share" else "Share service unavailable";else->msg=x.readableMessage()}}},Modifier.weight(1f),icon=Icons.Rounded.FileDownload)}
        }}},
        footer={PagingControls(page,data?.totalPages?:1,data?.totalRows?:0){page=it}}
    ){
        data?.rows?.forEach{r->
            fun openReturn(){selected=r;scope.launch{when(val d=api.returnDetails(r.no)){is ApiResult.Success->details=d.value;else->msg=d.readableMessage()}}}
            DseRecordCard(
                r.no,"${r.party} • ${r.invoice}",money(r.total),listOf("Return" to r.status.orEmpty(),"Refund" to r.refundStatus.orEmpty()),
                meta="${r.date} • Refunded ${money(r.refund)}",
                swipeStartActions=listOf(SwipeAction("Open",Icons.Rounded.Visibility){openReturn()}),
                swipeEndActions=listOf(
                    SwipeAction("Original",Icons.Rounded.OpenInNew){onDeepLink("dseerp://${if(sales)"sales" else "purchase"}/${urlEncodeReturn(r.invoice)}")},
                    SwipeAction("PDF",Icons.Rounded.PictureAsPdf){scope.launch{when(val d=api.returnDetails(r.no)){is ApiResult.Success->msg=if(shareBusinessPdf(returnBusinessDocument(d.value)))"Return PDF opened" else "Unable to open return PDF";else->msg=d.readableMessage()}}},
                ),
                onActions={actionTarget=r;actionDetails=null;scope.launch{when(val d=api.returnDetails(r.no)){is ApiResult.Success->actionDetails=d.value;else->{msg=d.readableMessage();actionTarget=null}}}},
            ){openReturn()}
        }
    }
    if(actionTarget!=null&&actionDetails!=null){
        val r=actionTarget!!;val d=actionDetails!!
        val pending=d.status.contains("PENDING",true)
        val approved=d.status.equals("APPROVED",true)
        val terminal=d.status.uppercase().let{it.contains("CANCEL")||it.contains("DELETE")||it.contains("REJECT")}
        val remaining=(d.total-d.refund).coerceAtLeast(0.0)
        DseRecordActionSheet("${if(sales)"Sales" else "Purchase"} Return ${d.no}","Return lifecycle actions",buildList{
            add(PremiumActionSpec("View Return",{selected=r;details=d}))
            add(PremiumActionSpec("Share PDF",{msg=if(shareBusinessPdf(returnBusinessDocument(d)))"Return PDF opened" else "Unable to open return PDF"}))
            add(PremiumActionSpec("Excel",{msg=if(shareBusinessXlsx(returnBusinessDocument(d)))"Return Excel opened" else "Unable to open return Excel"}))
            add(PremiumActionSpec("Send Email",{emailTarget=d}))
            add(PremiumActionSpec("Record Audit",{auditTarget=d}))
            add(PremiumActionSpec("Open Original",{onDeepLink("dseerp://${if(sales)"sales" else "purchase"}/${urlEncodeReturn(d.invoice)}")}))
            if(p.can(module,"APPROVE")){
                add(PremiumActionSpec("Approve Return",{scope.launch{when(val x=api.approveReturn(d.no)){is ApiResult.Success->{msg=x.value.message;refresh++};else->msg=x.readableMessage()}}},enabled=pending,disabledReason="Only returns pending approval can be approved."))
                add(PremiumActionSpec("Reject Return",{reject=r},destructive=true,enabled=pending,disabledReason="Only returns pending approval can be rejected."))
            }
            if(p.can(module,"EDIT")){
                add(PremiumActionSpec("Record Refund",{refund=d},enabled=approved&&remaining>0.005,disabledReason=when{!approved->"Refund is available after the Return is approved.";remaining<=0.005->"This Return is already fully refunded.";else->null}))
                add(PremiumActionSpec("Edit Notes / Reason",{edit=d},enabled=!terminal,disabledReason="Terminal Returns cannot be edited."))
                add(PremiumActionSpec("Attachment",{attach=d},enabled=!terminal,disabledReason="Terminal Returns cannot be changed."))
                add(PremiumActionSpec("Cancel Return",{confirm="cancel" to r},destructive=true,enabled=!terminal&&d.refund<=0.005,disabledReason=when{terminal->"This Return is already terminal.";d.refund>0.005->"A refunded Return cannot be cancelled.";else->null}))
            }
            if(p.can(module,"DELETE"))add(PremiumActionSpec("Delete Return",{confirm="delete" to r},destructive=true,enabled=!terminal&&d.refund<=0.005,disabledReason=when{terminal->"This Return is already terminal.";d.refund>0.005->"A refunded Return cannot be deleted.";else->null}))
        },{actionTarget=null;actionDetails=null})
    }
    auditTarget?.let{d->ReferenceAuditDialog(api,if(sales)"SALES_RETURN" else "PURCHASE_RETURN",d.no){auditTarget=null}}
    emailTarget?.let{d->ReturnEmailDialog(api,d,username,{emailTarget=null}){msg=it;emailTarget=null;refresh++}}
    if(filterOpen)ReturnFilterDialog(filter,{filterOpen=false}){filter=it;page=0;filterOpen=false}

    if(selected!=null&&details!=null){
        val r=selected!!;val d=details!!
        val pending=d.status.contains("PENDING",true);val approved=d.status.equals("APPROVED",true)
        val terminal=d.status.uppercase().let{it.contains("CANCEL")||it.contains("DELETE")||it.contains("REJECT")}
        val remaining=(d.total-d.refund).coerceAtLeast(0.0)
        val settlement=settlements.firstOrNull{it.invoiceNo==d.invoice}
        DetailDialog("${d.no} • ${d.invoice}",{selected=null;details=null},{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            DseStatus("Return",d.status);DseStatus("Refund",d.refundStatus)
            detailReturnRows(d)
            settlement?.let{s->DseSection("Original Document Settlement",Icons.Rounded.AccountBalanceWallet){detailReturnRows(listOf("Status" to s.status,"Original pending" to money(s.pendingAmount),"Approved returns" to money(s.approvedReturnAmount),"Settled returns" to money(s.settledAmount),"Settlement due" to s.dueDate))}}
            if(d.lines.isNotEmpty())DseSection("Returned Items",Icons.Rounded.Inventory2){d.lines.forEach{l->ListItem(headlineContent={Text("${l.code} • ${l.name}")},supportingContent={Text("Qty ${l.quantity} ${l.unit} • ${l.reason}")},trailingContent={Text(money(l.amount))})}}
            if(d.attachment.isNotBlank())OutlinedButton(onClick={scope.launch{when(val a=api.returnAttachmentFile(d.no)){is ApiResult.Success->{val name=returnFileName(d.attachment,"return-${d.no}.bin");if(!platformShareFile("Return ${d.no} attachment",name,a.value))msg="Unable to open return attachment"};else->msg=a.readableMessage()}}}){Icon(Icons.Rounded.OpenInNew,null);Text("Open Return Attachment")}
            Text("Actions",fontWeight=FontWeight.SemiBold)
            PremiumActionGrid(buildList{
                add(PremiumActionSpec("Share PDF",{msg=if(shareBusinessPdf(returnBusinessDocument(d)))"Return PDF opened" else "Unable to open return PDF"}))
                add(PremiumActionSpec("Excel",{msg=if(shareBusinessXlsx(returnBusinessDocument(d)))"Return Excel opened" else "Unable to open return Excel"}))
                add(PremiumActionSpec("Open Original",{onDeepLink("dseerp://${if(sales)"sales" else "purchase"}/${urlEncodeReturn(d.invoice)}")}))
                if(pending&&p.can(module,"APPROVE")){
                    add(PremiumActionSpec("Approve Return",{scope.launch{when(val x=api.approveReturn(d.no)){is ApiResult.Success->{msg=x.value.message;selected=null;details=null;refresh++};else->msg=x.readableMessage()}}}))
                    add(PremiumActionSpec("Reject Return",{reject=r;selected=null;details=null},true))
                }
                if(approved&&remaining>0.005&&p.can(module,"EDIT"))add(PremiumActionSpec("Record Refund",{refund=d;selected=null;details=null}))
                if(!terminal&&p.can(module,"EDIT")){add(PremiumActionSpec("Edit Notes / Reason",{edit=d;selected=null;details=null}));add(PremiumActionSpec("Attachment",{attach=d;selected=null;details=null}))}
                if(!terminal&&d.refund<=0.005&&p.can(module,"EDIT"))add(PremiumActionSpec("Cancel",{confirm="cancel" to r;selected=null;details=null},true))
                if(!terminal&&d.refund<=0.005&&p.can(module,"DELETE"))add(PremiumActionSpec("Delete",{confirm="delete" to r;selected=null;details=null},true))
            })
        }})
    }
    reject?.let{r->ReasonDialog("Reject ${r.no}","Only an authorized approver can reject a pending Return. Enter the reason.",{reject=null}){reason->scope.launch{when(val x=api.rejectReturn(r.no,reason)){is ApiResult.Success->{msg=x.value.message;reject=null;refresh++};else->msg=x.readableMessage()}}}}
    refund?.let{d->RefundDialog(api,d,username,{refund=null}){msg=it;refund=null;refresh++}}
    edit?.let{d->ReturnNotesDialog(api,d,{edit=null}){msg=it;edit=null;refresh++}}
    attach?.let{d->ReturnAttachmentDialog(api,d,{attach=null}){msg=it;attach=null;refresh++}}
    confirm?.let{(action,r)->
        if(action=="delete")ConfirmDialog("Delete Return","Permanently delete ${r.no}? The ERP server will preserve audit/integrity and reverse stock only when allowed.","Delete",true,{confirm=null},{scope.launch{when(val x=api.deleteReturn(r.no,sales)){is ApiResult.Success->{msg=x.value.message;confirm=null;refresh++};else->msg=x.readableMessage()}}},requiredPhrase="DELETE",requiredPhraseLabel="Type DELETE")
        else ConfirmDialog("Cancel Return","Cancel ${r.no}? The server will reject cancellation if refund, stock or lifecycle rules make it unsafe.","Cancel Return",true,{confirm=null}){scope.launch{when(val x=api.cancelReturn(r.no,sales)){is ApiResult.Success->{msg=x.value.message;confirm=null;refresh++};else->msg=x.readableMessage()}}}
    }
}

@Composable
private fun ReturnFilterDialog(current:ReturnFilter,onClose:()->Unit,onApply:(ReturnFilter)->Unit){
    var d by remember{mutableStateOf(current)}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Return Filters")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Party",d.party,singleLine=true,onValue={d=d.copy(party=it)});DseSelect("Status",d.status,listOf("ALL","PENDING APPROVAL","APPROVED","REJECTED","CANCELLED","DELETED"),onValue={d=d.copy(status=it.takeUnless{x->x=="ALL"}.orEmpty())});DseDateField("From",d.from,onValue={d=d.copy(from=it)});DseDateField("To",d.to,onValue={d=d.copy(to=it)})}},confirmButton={PremiumPrimaryButton("Apply Filters",{onApply(d)},leadingIcon=Icons.Rounded.FilterAlt)},dismissButton={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){PremiumSecondaryButton("Reset",{onApply(ReturnFilter())});PremiumSecondaryButton("Cancel",onClose)}})
}

@Composable
internal fun ReturnCreateDialog(api:DseErpHttpClient,source:ReturnSource,onClose:()->Unit,onDone:(String)->Unit){
    val type=if(source.type.equals("SALE",true))"SALES RETURN" else "PURCHASE RETURN"
    var date by remember{mutableStateOf(todayIso())};val drafts=remember{mutableStateListOf<ReturnLineDraft>()};var msg by remember{mutableStateOf("Loading returnable source lines…")};val scope=rememberCoroutineScope()
    LaunchedEffect(source.invoiceNo){
        when(val r=api.returnableLines(type,source.invoiceNo)){
            is ApiResult.Success->{
                drafts.clear()
                r.value.forEach{line->
                    val eligible=(line.quantity-line.returnedQuantity).coerceAtLeast(0.0)
                    drafts+=ReturnLineDraft(line.sourceLineId,line.code,line.description.ifBlank{line.code},line.rate,line.quantity,line.returnedQuantity,eligible)
                }
                msg=if(drafts.isEmpty())"No returnable invoice lines remain." else "Select quantities against the exact original invoice lines. Previously returned quantities are already deducted by the server."
            }
            else->msg=r.readableMessage()
        }
    }
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Create ${if(source.type=="SALE")"Sales" else "Purchase"} Return • ${source.invoiceNo}")},text={Column(Modifier.heightIn(max=680.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        detailReturnSource(source);DseDateField("Return Date",date,required=true,onValue={date=it});Text(msg,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        drafts.forEachIndexed{i,d->DseSection("${d.code} • ${d.description}",Icons.Rounded.Inventory2){Text("Original line #${d.sourceLineId} • rate ${money(d.rate)} • invoiced ${d.invoiced} • previously returned ${d.previouslyReturned} • eligible ${d.eligible}",style=MaterialTheme.typography.bodySmall);DseNumberField("Return Quantity",d.qty,enabled=d.eligible>0,min=0.0,max=d.eligible,required=true,onValue={v->drafts[i]=d.copy(qty=v)});DseField("Reason",d.reason,onValue={v->drafts[i]=d.copy(reason=v)})}}
    }},confirmButton={val chosen=drafts.filter{(it.qty.toDoubleOrNull()?:0.0)>0};Button(enabled=source.partyId>0&&chosen.isNotEmpty()&&chosen.all{(it.qty.toDoubleOrNull()?:0.0)<=it.eligible+0.0001},onClick={scope.launch{val lines=chosen.map{ReturnCreateLine(it.code,it.sourceLineId,it.qty.toDoubleOrNull()?:0.0,0.0,it.reason)};when(val r=api.createReturn(ReturnCreateRequest(type,source.invoiceNo,source.partyId,date,lines))){is ApiResult.Success->onDone("Created ${r.value.returnNo} • PENDING APPROVAL");else->msg=r.readableMessage()}}}){Text("Create Pending Return")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable
private fun RefundDialog(api:DseErpHttpClient,d:ReturnDetails,username:String,onClose:()->Unit,onDone:(String)->Unit){
    var history by remember{mutableStateOf<List<ReturnRefundRow>>(emptyList())};var date by remember{mutableStateOf(todayIso())};var full by remember{mutableStateOf(true)};val remaining=(d.total-d.refund).coerceAtLeast(0.0);var amount by remember{mutableStateOf(remaining.toString())};var mode by remember{mutableStateOf("")};var bank by remember{mutableStateOf("")};var reference by remember{mutableStateOf("")};var notes by remember{mutableStateOf("")};var modes by remember{mutableStateOf<List<String>>(emptyList())};var banks by remember{mutableStateOf<List<String>>(emptyList())};var proofName by remember{mutableStateOf<String?>(null)};var proofBase64 by remember{mutableStateOf<String?>(null)};var msg by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    LaunchedEffect(d.no){history=(api.returnRefunds(d.no) as? ApiResult.Success)?.value.orEmpty();modes=(api.lookupValuesByCode("PAYMENT_MODE") as? ApiResult.Success)?.value.orEmpty();banks=(api.lookupValuesByCode("BANK_ACCOUNT") as? ApiResult.Success)?.value.orEmpty();mode=modes.firstOrNull().orEmpty()}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Refund ${d.no}")},text={Column(Modifier.heightIn(max=680.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        detailReturnRows(d);Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("Refundable Balance",style=MaterialTheme.typography.labelMedium);Text(money(remaining),style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)}}
        DseDateField("Refund Date",date,required=true,onValue={date=it});Row(verticalAlignment=Alignment.CenterVertically){RadioButton(full,{full=true;amount=remaining.toString()});PremiumOptionLabel("Full Refund",accent=DseSuccess);RadioButton(!full,{full=false});PremiumOptionLabel("Partial Refund",accent=DseWarning)};DseNumberField("Refund Amount",amount,enabled=!full,min=0.01,max=remaining,required=true,onValue={amount=it});DseSelect("Payment Mode",mode,modes,required=true,onValue={mode=it});val bankRequired=mode.contains("bank",true)||mode.equals("NEFT",true)||mode.equals("RTGS",true);DseSelect("Bank Account",bank,banks,enabled=bankRequired,required=bankRequired,onValue={bank=it});DseField("Reference No",reference,singleLine=true,onValue={reference=it});DseField("Notes",notes,onValue={notes=it})
        PremiumSecondaryButton(proofName?:"Select Refund Proof",onClick={platformPickAttachment { picked->if(!picked.fileName.isNullOrBlank()&&!picked.base64.isNullOrBlank()){proofName=picked.fileName;proofBase64=picked.base64;msg="Proof selected: ${picked.fileName}"}else msg=picked.error?:"No proof selected" }},icon=Icons.Rounded.AttachFile)
        if(history.isNotEmpty())DseSection("Refund History",Icons.Rounded.History){history.forEach{h->ListItem(headlineContent={Text("${h.date} • ${money(h.amount)}")},supportingContent={Text(listOf(h.mode,h.reference,h.bankAccount,h.status).filter{it.isNotBlank()}.joinToString(" • "))},trailingContent={if(h.attachment.isNotBlank())Row{IconButton(onClick={scope.launch{when(val f=api.refundAttachmentFile(h.id)){is ApiResult.Success->{if(!platformShareFile("Refund proof ${d.no}",returnFileName(h.attachment,"refund-${h.id}.bin"),f.value))msg="Unable to open refund proof"};else->msg=f.readableMessage()}}}){Icon(Icons.Rounded.OpenInNew,"Open refund proof")};IconButton(onClick={scope.launch{when(val x=api.deleteRefundAttachment(h.id)){is ApiResult.Success->{msg="Refund proof removed";history=(api.returnRefunds(d.no) as? ApiResult.Success)?.value.orEmpty()};else->msg=x.readableMessage()}}}){Icon(Icons.Rounded.Delete,"Delete refund proof")}}})}}
        DseMessageFeedback(msg)
    }},confirmButton={val bankRequired=mode.contains("bank",true)||mode.equals("NEFT",true)||mode.equals("RTGS",true);Button(enabled=(amount.toDoubleOrNull()?:0.0)>0&&mode.isNotBlank()&&(!bankRequired||bank.isNotBlank()),onClick={scope.launch{val a=amount.toDoubleOrNull()?:0.0;if(a>remaining+0.005){msg="Refund cannot exceed ${money(remaining)}";return@launch};when(val r=api.recordReturnRefund(d.no,ReturnRefundCreateRequest(date,a,mode,reference,bank,d.party,notes,if(full)"FULL" else "PARTIAL",username))){is ApiResult.Success->{if(proofName!=null&&proofBase64!=null){when(val up=api.uploadRefundAttachment(r.value.id,proofName!!,decodeBase64Portable(proofBase64!!))){is ApiResult.Success->{};else->{msg="Refund saved, but proof upload failed: ${up.readableMessage()}";return@launch}}};onDone("Refund recorded in audit ledger • ${money(a)}")};else->msg=r.readableMessage()}}}){Text("Record Refund")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable
private fun ReturnEmailDialog(api:DseErpHttpClient,d:ReturnDetails,username:String,onClose:()->Unit,onDone:(String)->Unit){
    var recipient by remember{mutableStateOf("")}
    var subject by remember{mutableStateOf("${businessName()} ${d.type.lowercase().replaceFirstChar{it.uppercase()}} ${d.no}")}
    var body by remember{mutableStateOf("Dear ${d.party},\n\nPlease find return document ${d.no} attached.\n\nRegards,\n${businessName()}")}
    var msg by remember{mutableStateOf("Loading party email…")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(d.no){when(val r=api.returnPartyEmail(d.no)){is ApiResult.Success->{recipient=r.value.value;msg=if(recipient.isBlank())"Party email is not configured" else "Return PDF will be attached"};else->msg=r.readableMessage()}}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Email ${d.no}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Recipient",recipient,singleLine=true,required=true,onValue={recipient=it});DseField("Subject",subject,singleLine=true,required=true,onValue={subject=it});DseField("Message",body,onValue={body=it});DseMessageFeedback(msg)}},confirmButton={PremiumPrimaryButton("Send Email",{scope.launch{val payload=returnBusinessDocument(d);val bytes=businessDocumentPdf(payload);val req=BusinessEmailRequest(recipient.trim(),subject.trim(),body,"Return_${d.no}.pdf",encodeBase64Portable(bytes));when(val r=api.sendBusinessEmail(req)){is ApiResult.Success->{if(r.value.success){onDone(r.value.message.ifBlank{"Return email sent"})}else msg=r.value.message};else->msg=r.readableMessage()}}},enabled=recipient.contains("@"),leadingIcon=Icons.Rounded.Email)},dismissButton={PremiumSecondaryButton("Cancel",onClose)})
}

@Composable
private fun ReturnAttachmentDialog(api:DseErpHttpClient,d:ReturnDetails,onClose:()->Unit,onDone:(String)->Unit){
    var msg by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Return Attachment • ${d.no}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){if(d.attachment.isNotBlank()){Text("Current attachment: ${returnFileName(d.attachment,"attachment")}");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={scope.launch{when(val f=api.returnAttachmentFile(d.no)){is ApiResult.Success->{if(!platformShareFile("Return ${d.no} attachment",returnFileName(d.attachment,"return-${d.no}.bin"),f.value))msg="Unable to open attachment"};else->msg=f.readableMessage()}}}){Icon(Icons.Rounded.OpenInNew,null);Text("Open")};OutlinedButton(onClick={scope.launch{when(val x=api.deleteReturnAttachment(d.no)){is ApiResult.Success->onDone("Return attachment removed");else->msg=x.readableMessage()}}}){Icon(Icons.Rounded.Delete,null);Text("Remove")}}};Text("Attach supporting return proof/document.");DseMessageFeedback(msg)}},confirmButton={PremiumPrimaryButton("Choose & Upload",{platformPickAttachment { picked->if(picked.fileName.isNullOrBlank()||picked.base64.isNullOrBlank()){msg=picked.error?:"No attachment selected";return@platformPickAttachment};scope.launch{try{when(val r=api.uploadReturnAttachment(d.no,picked.fileName!!,decodeBase64Portable(picked.base64!!))){is ApiResult.Success->onDone("Return attachment saved");else->msg=r.readableMessage()}}catch(t:Throwable){msg=t.message?:"Attachment upload failed"}} }},leadingIcon=Icons.Rounded.AttachFile)},dismissButton={PremiumSecondaryButton("Close",onClose)})
}

@Composable
private fun ReturnNotesDialog(api:DseErpHttpClient,d:ReturnDetails,onClose:()->Unit,onDone:(String)->Unit){
    var field by remember{mutableStateOf("notes")};var value by remember{mutableStateOf(d.notes)};var msg by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Edit ${d.no}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseSelect("Editable Field",field,listOf("notes","reason"),onValue={field=it});DseField(if(field=="notes")"Notes" else "Reason",value,onValue={value=it});Text("Return status cannot be edited here. Approve/Reject/Cancel are protected lifecycle actions.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);DseMessageFeedback(msg)}},confirmButton={Button(onClick={scope.launch{when(val r=api.updateReturn(d.no,field,value,d.rowVersion)){is ApiResult.Success->onDone(r.value.message.ifBlank{"Return updated"});else->msg=r.readableMessage()}}}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}


@Composable private fun detailReturnRows(d:ReturnDetails)=detailReturnRows(listOf("Original Invoice" to d.invoice,"Party" to d.party,"Return Date" to d.date,"Return Total" to money(d.total),"Already Refunded" to money(d.refund),"Refundable" to money((d.total-d.refund).coerceAtLeast(0.0)),"Payment Terms" to d.paymentTerms,"Currency" to d.currency,"Notes" to d.notes))
@Composable private fun detailReturnRows(rows:List<Pair<String,String>>){detailRows(rows)}
@Composable private fun detailReturnSource(s:ReturnSource){Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("Original ${s.type}: ${s.invoiceNo}",fontWeight=FontWeight.SemiBold);Text(s.partyName);Text("Party and invoice are locked from the source document; no manual internal IDs.",style=MaterialTheme.typography.bodySmall)}}}
private fun returnShareText(d:ReturnDetails)=buildString{appendLine("${businessName()} • ${d.type} ${d.no}");appendLine("Original: ${d.invoice}");appendLine("Party: ${d.party}");appendLine("Return total: ${money(d.total)}");appendLine("Refunded: ${money(d.refund)}");appendLine("Return status: ${d.status} • Refund: ${d.refundStatus}")}
private fun returnFileName(path:String,fallback:String):String=path.replace('\\','/').substringAfterLast('/').ifBlank{fallback}
private fun urlEncodeReturn(value:String):String=value.encodeToByteArray().joinToString(""){b->val n=b.toInt() and 0xff;val c=n.toChar();if((c in 'a'..'z')||(c in 'A'..'Z')||(c in '0'..'9')||c in "-_.~")c.toString() else "%"+n.toString(16).uppercase().padStart(2,'0')}


private suspend fun exportReturnsCsv(api:DseErpHttpClient,type:String,filter:ReturnFilter):ApiResult<List<ReturnSummary>>{
    val rows=mutableListOf<ReturnSummary>();var page=0
    while(page<1000){when(val r=api.returnsPage(type,page,100,filter)){is ApiResult.Success->{rows+=r.value.rows;if(page+1>=r.value.totalPages)return ApiResult.Success(rows);page++};is ApiResult.NetworkError->return ApiResult.NetworkError(r.message,r.requestMayHaveReachedServer);is ApiResult.DecodeError->return ApiResult.DecodeError(r.message,r.status);is ApiResult.Unauthorized->return r;is ApiResult.Forbidden->return r;is ApiResult.Conflict->return r;is ApiResult.NotFound->return r;is ApiResult.ServerError->return r;is ApiResult.UnsafeEndpoint->return r;is ApiResult.NotImplemented->return r}}
    return ApiResult.ServerError(400,"Return export exceeded safe page limit")
}
private fun shareReturnsCsv(rows:List<ReturnSummary>,sales:Boolean)=shareCsvFile(if(sales)"Sales Returns" else "Purchase Returns",if(sales)"Jasvi_Sales_Returns.csv" else "Jasvi_Purchase_Returns.csv",listOf("Return No","Date","Original Document","Party","Amount","Refunded","Return Status","Refund Status"),rows.map{r->listOf(r.no,r.date,r.invoice,r.party,r.total.toString(),r.refund.toString(),r.status.orEmpty(),r.refundStatus.orEmpty())})
