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

private data class QuoteLineDraft(val item:MasterItem?=null,val code:String="",val description:String="",val qty:String="1",val rate:String="0",val gst:String="0",val discount:String="0")
private data class QuoteFilter(val number:String="",val customer:String="",val status:String="",val from:String="",val to:String="",val valid:String="",val salesperson:String="",val minAmount:String="",val maxAmount:String="",val followUp:String="",val source:String="")

@Composable
internal fun QuotationsWorkspace(api:DseErpHttpClient,p:PermissionContext,username:String,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={},onDeepLink:(String)->Unit={}){
    var page by remember{mutableIntStateOf(0)};var q by remember{mutableStateOf("")};var filter by remember{mutableStateOf(QuoteFilter())}
    var data by remember{mutableStateOf<QuotationPage?>(null)};var msg by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<QuotationRecord?>(null)};var editor by remember{mutableStateOf<QuotationRecord?>(null)};var creating by remember{mutableStateOf(false)}
    var confirm by remember{mutableStateOf<QuotationRecord?>(null)};var filterOpen by remember{mutableStateOf(false)};var email by remember{mutableStateOf<QuotationRecord?>(null)}
    var followUp by remember{mutableStateOf<QuotationRecord?>(null)};var notes by remember{mutableStateOf<QuotationRecord?>(null)}
    var actionTarget by remember{mutableStateOf<QuotationRecord?>(null)};var timelineTarget by remember{mutableStateOf<QuotationRecord?>(null)};var auditTarget by remember{mutableStateOf<QuotationRecord?>(null)}
    var savedViews by remember{mutableStateOf<List<SavedView>>(emptyList())};var savedChoice by remember{mutableStateOf("")};var saveViewPrompt by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun loadFull(row:QuotationRecord,after:(QuotationRecord)->Unit){scope.launch{when(val x=api.quotationById(row.id)){is ApiResult.Success->after(x.value);else->msg=x.readableMessage()}}}
    LaunchedEffect(openTarget?.moduleKey,openTarget?.recordId,openTarget?.reference,openTarget?.openActions){
        val t=openTarget
        if(t!=null&&t.moduleKey.uppercase() in setOf("QUOTATION","QUOTATIONS")){
            if(t.reference=="__CREATE__"){creating=true;onTargetConsumed()}
            else {val id=t.recordId?.takeIf{it in 1..Int.MAX_VALUE.toLong()}?.toInt()
                if(id!=null){when(val r=api.quotationById(id)){is ApiResult.Success->{if(t.openActions)actionTarget=r.value else selected=r.value;onTargetConsumed()};else->{msg=r.readableMessage();onTargetConsumed()}}}
                else if(t.reference.isNotBlank()){
                    when(val pageResult=api.quotationsPage(0,25,t.reference)){
                        is ApiResult.Success->{val match=pageResult.value.rows.firstOrNull{it.no.equals(t.reference,true)};if(match!=null&&t.openActions)actionTarget=match else q=t.reference;onTargetConsumed()}
                        else->{q=t.reference;page=0;onTargetConsumed()}
                    }
                }
            }
        }
    }
    LaunchedEffect(api,refresh){savedViews=(api.savedViews("QUOTATION_REGISTER",p.user?.id) as? ApiResult.Success)?.value.orEmpty()}
    LaunchedEffect(api,page,q,filter,refresh){
        when(val r=api.quotationsPage(page,25,q,filter.number,filter.customer,filter.status,filter.from,filter.to,filter.valid,filter.salesperson,filter.minAmount,filter.maxAmount,filter.followUp,filter.source)){
            is ApiResult.Success->{data=r.value;msg="${r.value.totalRows} quotation(s) • filtered ${money(r.value.filteredAmount)}${if(r.source==ApiDataSource.CACHE)" • cached" else ""}"}
            else->msg=r.readableMessage()
        }
    }
    DseRegisterShell("Quotations","Create, track and convert quotations",q,{q=it;page=0},msg,{refresh++},if(p.can("QUOTATION","CREATE")){{creating=true}}else null,
        kpis={data?.let{d->val m=d.metrics;Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseHeroKpi("Quotation Value",money(m?.totalValue?:d.filteredAmount),"${m?.totalCount?:d.totalRows} quotation(s)",Icons.Rounded.RequestQuote);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Pending",money(m?.pendingValue?:0.0),Icons.Rounded.Schedule,Modifier.weight(1f),DseWarning);DseMetricTile("Accepted",money(m?.acceptedValue?:0.0),Icons.Rounded.CheckCircle,Modifier.weight(1f),DseSuccess)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Conversion",formatPercent(m?.conversionRate?:0.0),Icons.Rounded.TrendingUp,Modifier.weight(1f),DseInfo);DseMetricTile("Expired",money(m?.expiredValue?:0.0),Icons.Rounded.EventBusy,Modifier.weight(1f),DseDanger)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){PremiumSecondaryButton("Filters",{filterOpen=true},Modifier.weight(1f),icon=Icons.Rounded.FilterAlt);PremiumSecondaryButton("Excel",{scope.launch{msg=exportQuotationRegister(api,q,filter,"XLSX")}},Modifier.weight(1f),icon=Icons.Rounded.TableView);PremiumSecondaryButton("PDF",{scope.launch{msg=exportQuotationRegister(api,q,filter,"PDF")}},Modifier.weight(1f),icon=Icons.Rounded.PictureAsPdf)}}}},footer={PagingControls(page,data?.totalPages?:1,data?.totalRows?:0){page=it}}){
        data?.rows?.forEach{r->DseRecordCard(
            r.no,r.customer,money(r.amount),listOf("Status" to r.status.orEmpty()),
            meta="${r.date} • Valid ${r.valid.orEmpty()} • ${r.source.orEmpty()}",
            swipeStartActions=buildList{
                add(SwipeAction("Open",Icons.Rounded.Visibility){loadFull(r){selected=it}})
                if(p.can("QUOTATION","EDIT"))add(SwipeAction("Edit",Icons.Rounded.Edit){loadFull(r){editor=it}})
            },
            swipeEndActions=listOf(SwipeAction("Actions",Icons.Rounded.MoreHoriz){actionTarget=r}),
            onActions={actionTarget=r},
        ){loadFull(r){selected=it}}}
    }
    actionTarget?.let{r->
        val status=r.status.orEmpty().uppercase()
        val converted=!r.converted.isNullOrBlank()||status.contains("CONVERT")
        val terminal=status in setOf("REJECTED","EXPIRED","DELETED")
        val mutable=!converted&&!terminal
        val canEdit=mutable&&p.can("QUOTATION","EDIT")
        val canConvert=mutable&&p.can("QUOTATION","EDIT")&&p.can("SALES","CREATE")
        DseRecordActionSheet("Quotation ${r.no}","Desktop-style Quotation actions",buildList{
            add(PremiumActionSpec("View Quotation",{loadFull(r){selected=it}}))
            add(PremiumActionSpec("Activity Timeline",{timelineTarget=r},enabled=r.id>0,disabledReason="Activity history requires a saved Quotation."))
            add(PremiumActionSpec("Record Audit",{auditTarget=r},enabled=r.id>0,disabledReason="Audit trail requires a saved Quotation."))
            if(p.can("QUOTATION","EDIT"))add(PremiumActionSpec("Edit Quotation",{loadFull(r){editor=it}},enabled=canEdit,disabledReason=if(converted)"Converted Quotations cannot be edited." else if(terminal)"Rejected, expired or deleted Quotations cannot be edited." else null))
            if(p.can("QUOTATION","EDIT")&&p.can("SALES","CREATE"))add(PremiumActionSpec("Convert to Sale",{scope.launch{when(val x=api.quotationAction(r.id,"convert",username)){is ApiResult.Success->{msg=x.value.value.ifBlank{"Quotation converted"};refresh++};else->msg=x.readableMessage()}}},enabled=canConvert,disabledReason=if(converted)"This Quotation is already converted." else if(terminal)"This Quotation is no longer convertible." else null))
            if(converted)add(PremiumActionSpec("Open Converted Sale",{r.converted?.takeIf{it.isNotBlank()}?.let{onDeepLink("dseerp://sales/${urlEncode(it)}")}},enabled=!r.converted.isNullOrBlank(),disabledReason="Converted Sale reference is not available."))
            add(PremiumActionSpec("Share PDF",{loadFull(r){full->scope.launch{when(val lines=api.quotationLines(full.id)){is ApiResult.Success->msg=if(shareBusinessPdf(quotationBusinessDocument(full,lines.value)))"Quotation PDF opened" else "Unable to open quotation PDF";else->msg=lines.readableMessage()}}}}))
            add(PremiumActionSpec("Excel",{loadFull(r){full->scope.launch{when(val lines=api.quotationLines(full.id)){is ApiResult.Success->msg=if(shareBusinessXlsx(quotationBusinessDocument(full,lines.value)))"Quotation Excel opened" else "Unable to open quotation Excel";else->msg=lines.readableMessage()}}}}))
            add(PremiumActionSpec("Send Email",{loadFull(r){email=it}},enabled=!r.email.isNullOrBlank(),disabledReason="Customer email is not configured."))
            add(PremiumActionSpec("WhatsApp",{loadFull(r){full->val digits=full.phone.orEmpty().filter{it.isDigit()};if(digits.isBlank())msg="Customer phone is not configured" else if(platformOpenExternalUrl("https://wa.me/$digits?text=${urlEncode(quoteShareText(full))}")){scope.launch{when(val x=api.quotationSent(full.id,"WHATSAPP")){is ApiResult.Success->{msg="WhatsApp opened and quotation delivery status recorded";refresh++};else->msg=x.readableMessage()}}}else msg="WhatsApp could not be opened"}},enabled=!r.phone.isNullOrBlank(),disabledReason="Customer phone is not configured."))
            add(PremiumActionSpec("Duplicate Quotation",{scope.launch{when(val x=api.quotationAction(r.id,"duplicate",username)){is ApiResult.Success->{msg="Duplicated as ${x.value.value}";refresh++};else->msg=x.readableMessage()}}},enabled=mutable,disabledReason="Converted, rejected, expired or deleted Quotations cannot be duplicated."))
            if(p.can("QUOTATION","EDIT")){
                add(PremiumActionSpec("Follow-up",{loadFull(r){followUp=it}},enabled=mutable,disabledReason="Follow-up is unavailable for converted or terminal Quotations."))
                add(PremiumActionSpec("Notes",{loadFull(r){notes=it}},enabled=mutable,disabledReason="Notes cannot be changed on converted or terminal Quotations."))
            }
            if(p.can("QUOTATION","DELETE"))add(PremiumActionSpec("Delete Quotation",{loadFull(r){confirm=it}},destructive=true,enabled=mutable,disabledReason="Converted or terminal Quotations cannot be deleted."))
        },{actionTarget=null})
    }
    timelineTarget?.let{r->ActivityTimelineSheet(api,"QUOTATION",r.id,r.no){timelineTarget=null}}
    auditTarget?.let{r->RecordAuditDialog(api,"QUOTATION",r.id.toLong(),r.no){auditTarget=null}}
    if(filterOpen)QuotationFilterDialog(filter,data,{filterOpen=false}){filter=it;page=0;filterOpen=false}
    if(saveViewPrompt)TextPromptDialog("Save Quotation Filter View","View name",onDismiss={saveViewPrompt=false}){name->scope.launch{when(val r=api.saveView(SavedViewSave(p.user?.id,"QUOTATION_REGISTER",name,encodeQuoteView(filter)))){is ApiResult.Success->{msg="Saved view created";saveViewPrompt=false;refresh++};else->msg=r.readableMessage()}}}
    selected?.let{r->QuotationDetailDialog(api,r,p,
        onClose={selected=null},onEdit={editor=r;selected=null},onConvert={scope.launch{when(val x=api.quotationAction(r.id,"convert",username)){is ApiResult.Success->{msg=x.value.value.ifBlank{"Quotation converted"};selected=null;refresh++};else->msg=x.readableMessage()}}},
        onDuplicate={scope.launch{when(val x=api.quotationAction(r.id,"duplicate",username)){is ApiResult.Success->{msg="Duplicated as ${x.value.value}";selected=null;refresh++};else->msg=x.readableMessage()}}},
        onEmail={email=r},onPdf={scope.launch{when(val lines=api.quotationLines(r.id)){is ApiResult.Success->msg=if(shareBusinessPdf(quotationBusinessDocument(r,lines.value)))"Quotation PDF opened" else "Unable to open quotation PDF";else->msg=lines.readableMessage()}}},onExcel={scope.launch{when(val lines=api.quotationLines(r.id)){is ApiResult.Success->msg=if(shareBusinessXlsx(quotationBusinessDocument(r,lines.value)))"Quotation Excel opened" else "Unable to open quotation Excel";else->msg=lines.readableMessage()}}},
        onWhatsApp={val digits=r.phone.orEmpty().filter{it.isDigit()};if(digits.isBlank())msg="Customer phone is not configured" else if(platformOpenExternalUrl("https://wa.me/$digits?text=${urlEncode(quoteShareText(r))}")){scope.launch{when(val x=api.quotationSent(r.id,"WHATSAPP")){is ApiResult.Success->{msg="WhatsApp opened and quotation delivery status recorded";selected=null;refresh++};else->msg=x.readableMessage()}}}else msg="WhatsApp could not be opened"},
        onOpenConverted={if(!r.converted.isNullOrBlank())onDeepLink("dseerp://sales/${urlEncode(r.converted!!)}")},
        onFollowUp={followUp=r;selected=null},onNotes={notes=r;selected=null},onDelete={confirm=r;selected=null})}
    if(creating)QuotationEditorDialog(api,null,username,data?.salespersons.orEmpty(),{creating=false}){req->scope.launch{when(val r=api.createQuotation(req)){is ApiResult.Success->{msg="Created ${r.value.no}";creating=false;refresh++};else->msg=r.readableMessage()}}}
    editor?.let{r->QuotationEditorDialog(api,r,username,data?.salespersons.orEmpty(),{editor=null}){req->scope.launch{when(val x=api.updateQuotation(r.id,req)){is ApiResult.Success->{msg="Quotation ${x.value.no} updated";editor=null;refresh++};else->msg=x.readableMessage()}}}}
    email?.let{r->QuoteEmailDialog(api,r,username,{email=null}){msg=it;email=null;refresh++}}
    followUp?.let{r->QuoteFollowUpDialog(api,r,{followUp=null}){msg=it;followUp=null;refresh++}}
    notes?.let{r->QuoteNotesDialog(api,r,{notes=null}){msg=it;notes=null;refresh++}}
    confirm?.let{r->ConfirmDialog("Delete Quotation","Delete ${r.no}? Converted/lifecycle-protected quotations are rejected by the ERP server.","Delete",true,{confirm=null},{scope.launch{when(val x=api.deleteQuotation(r.id)){is ApiResult.Success->{msg=x.value.message.ifBlank{"Quotation deleted"};confirm=null;refresh++};else->msg=x.readableMessage()}}},requiredPhrase="DELETE",requiredPhraseLabel="Type DELETE")}
}

private fun encodeQuoteView(f:QuoteFilter)=listOf(f.number,f.customer,f.from,f.to,f.status,f.valid,f.salesperson,f.minAmount,f.maxAmount,f.followUp,f.source).joinToString("|")
private fun decodeQuoteView(data:String):QuoteFilter{val x=data.split("|");if(x.size<11)return QuoteFilter();return QuoteFilter(number=x[0],customer=x[1],from=x[2],to=x[3],status=x[4],valid=x[5],salesperson=x[6],minAmount=x[7],maxAmount=x[8],followUp=x[9],source=x[10])}

@Composable
private fun QuotationFilterDialog(current:QuoteFilter,data:QuotationPage?,onClose:()->Unit,onApply:(QuoteFilter)->Unit){
    var d by remember{mutableStateOf(current)}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Quotation Filters")},text={Column(Modifier.heightIn(max=620.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Quotation No",d.number,singleLine=true,onValue={d=d.copy(number=it)});DseField("Customer",d.customer,singleLine=true,onValue={d=d.copy(customer=it)});DseSelect("Status",d.status,listOf("ALL","DRAFT","SENT","ACCEPTED","REJECTED","EXPIRED","CONVERTED"),onValue={d=d.copy(status=it.takeUnless{x->x=="ALL"}.orEmpty())});DseDateField("From",d.from,onValue={d=d.copy(from=it)});DseDateField("To",d.to,onValue={d=d.copy(to=it)});DseSelect("Validity",d.valid,listOf("ALL","VALID","EXPIRED"),onValue={d=d.copy(valid=it.takeUnless{x->x=="ALL"}.orEmpty())});DseSelect("Salesperson",d.salesperson,listOf("ALL")+data?.salespersons.orEmpty(),onValue={d=d.copy(salesperson=it.takeUnless{x->x=="ALL"}.orEmpty())});DseField("Min Amount",d.minAmount,singleLine=true,onValue={d=d.copy(minAmount=it.filter{x->x.isDigit()||x=='.'})});DseField("Max Amount",d.maxAmount,singleLine=true,onValue={d=d.copy(maxAmount=it.filter{x->x.isDigit()||x=='.'})});DseDateField("Follow-up",d.followUp,onValue={d=d.copy(followUp=it)});DseField("Source",d.source,singleLine=true,onValue={d=d.copy(source=it)})}},confirmButton={Button(onClick={onApply(d)}){Text("Apply")}},dismissButton={Row{TextButton(onClick={onApply(QuoteFilter())}){Text("Reset")};TextButton(onClick=onClose){Text("Cancel")}}})
}

@Composable
private fun QuotationDetailDialog(api:DseErpHttpClient,r:QuotationRecord,p:PermissionContext,onClose:()->Unit,onEdit:()->Unit,onConvert:()->Unit,onDuplicate:()->Unit,onEmail:()->Unit,onPdf:()->Unit,onExcel:()->Unit,onWhatsApp:()->Unit,onOpenConverted:()->Unit,onFollowUp:()->Unit,onNotes:()->Unit,onDelete:()->Unit){
    val status=r.status.orEmpty().uppercase();val converted=!r.converted.isNullOrBlank()||status.contains("CONVERT");val terminal=status in setOf("REJECTED","EXPIRED","DELETED")
    val mutable=!converted&&!terminal;val canEdit=mutable&&p.can("QUOTATION","EDIT");val canConvert=mutable&&p.can("QUOTATION","EDIT")&&p.can("SALES","CREATE")
    DetailDialog("Quotation ${r.no}",onClose,{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        DseStatus("Status",r.status.orEmpty());detailRows(listOf("Customer" to r.customer,"Date" to r.date,"Valid Until" to r.valid.orEmpty(),"Follow-up" to r.followUp.orEmpty(),"Amount" to money(r.amount),"Source" to r.source.orEmpty(),"Salesperson" to r.salesperson.orEmpty(),"Converted Sale" to r.converted.orEmpty(),"Phone" to r.phone.orEmpty(),"Email" to r.email.orEmpty(),"GSTIN" to r.gstin.orEmpty(),"Remarks" to r.remarks.orEmpty()))
        AttachmentManager(api,"QUOTATION",r.id,canEdit)
        Text("Actions",fontWeight=FontWeight.SemiBold)
        PremiumActionGrid(buildList{
            if(canEdit)add(PremiumActionSpec("Edit",onEdit))
            if(canConvert)add(PremiumActionSpec("Convert to Sale",onConvert))
            if(converted)add(PremiumActionSpec("Open Converted Sale",onOpenConverted))
            if(mutable)add(PremiumActionSpec("Duplicate",onDuplicate))
            add(PremiumActionSpec("Share PDF",onPdf));add(PremiumActionSpec("Excel",onExcel));add(PremiumActionSpec("Email",onEmail));add(PremiumActionSpec("WhatsApp",onWhatsApp))
            if(mutable)add(PremiumActionSpec("Follow-up",onFollowUp));add(PremiumActionSpec("Notes",onNotes))
            if(mutable&&p.can("QUOTATION","DELETE"))add(PremiumActionSpec("Delete",onDelete,true))
        })
    }})
}

@Composable
private fun QuotationEditorDialog(api:DseErpHttpClient,current:QuotationRecord?,username:String,salespersons:List<String>,onClose:()->Unit,onSave:(QuotationSaveRequest)->Unit){
    var party by remember{mutableStateOf<MasterParty?>(null)};var date by remember{mutableStateOf(current?.date?.ifBlank{todayIso()}?:todayIso())};var valid by remember{mutableStateOf(current?.valid?.ifBlank{addDaysIso(date,15)}?:addDaysIso(date,15))};var follow by remember{mutableStateOf(current?.followUp.orEmpty())};var source by remember{mutableStateOf(current?.source.orEmpty())};var salesperson by remember{mutableStateOf(current?.salesperson.orEmpty())};var remarks by remember{mutableStateOf(current?.remarks.orEmpty())};var sources by remember{mutableStateOf<List<String>>(emptyList())};var msg by remember{mutableStateOf("")};val lines=remember{mutableStateListOf<QuoteLineDraft>()};var lineEditor by remember{mutableStateOf<Int?>(null)}
    LaunchedEffect(current?.id){
        sources=(api.quotationSources() as? ApiResult.Success)?.value.orEmpty();if(source.isBlank())source=sources.firstOrNull().orEmpty()
        if(current!=null){
            when(val pr=api.searchParties("CUSTOMER",current.customer,50)){
                is ApiResult.Success->{party=pr.value.firstOrNull{it.id==current.customerId};if(party==null)msg="The original customer could not be resolved exactly. Select the correct customer before saving."}
                else->msg=pr.readableMessage()
            }
            when(val lr=api.quotationLines(current.id)){is ApiResult.Success->{lines.clear();lines.addAll(lr.value.map{QuoteLineDraft(code=it.code,description=it.description,qty=it.quantity.toString(),rate=it.rate.toString(),gst=it.gst.toString(),discount=it.discount.toString())})};else->msg=lr.readableMessage()}
        }
    }
    val qlines=lines.map{it.toQuoteLine()};val subtotal=roundQuote(qlines.sumOf{val gross=roundQuote(it.quantity*it.rate);roundQuote(gross-roundQuote(gross*it.discount.coerceIn(0.0,100.0)/100.0))});val total=roundQuote(qlines.sumOf{it.total});val gst=roundQuote((total-subtotal).coerceAtLeast(0.0));val discountAmount=roundQuote(qlines.sumOf{val gross=roundQuote(it.quantity*it.rate);roundQuote(gross*it.discount.coerceIn(0.0,100.0)/100.0)})
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"Create Quotation" else "Edit ${current.no}")},text={Column(Modifier.heightIn(max=680.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(current!=null)DseField("Quotation No",current.no,readOnly=true,singleLine=true,onValue={});DseDateField("Quotation Date",date,required=true,onValue={date=it});DseDateField("Valid Until",valid,required=true,onValue={valid=it});DseDateField("Follow-up Date",follow,onValue={follow=it})
        PartySelector(api,"CUSTOMER",party,allowCreate=true,onSelected={party=it},onCleared={party=null});DseSelect("Quotation Source",source,sources,required=true,onValue={source=it});if(salespersons.isNotEmpty())DseSelect("Salesperson",salesperson,salespersons,onValue={salesperson=it}) else DseField("Salesperson",salesperson,singleLine=true,onValue={salesperson=it})
        DseSection("Items",Icons.Rounded.Inventory2){lines.forEachIndexed{i,l->Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(l.code.ifBlank{"Select item"},fontWeight=FontWeight.SemiBold);Text("Qty ${l.qty} × ${l.rate} • GST ${l.gst}% • ${money(l.toQuoteLine().total)}",style=MaterialTheme.typography.bodySmall)};IconButton(onClick={lineEditor=i}){Icon(Icons.Rounded.Edit,"Edit")};if(lines.size>1)IconButton(onClick={lines.removeAt(i)}){Icon(Icons.Rounded.Delete,"Remove")}}}};OutlinedButton(onClick={lines.add(QuoteLineDraft());lineEditor=lines.lastIndex}){Text("Add Item")}}
        DseField("Remarks",remarks,onValue={remarks=it});DseSection("Totals — server recalculates on Save",Icons.Rounded.Calculate){detailRows(listOf("Subtotal" to money(subtotal),"Discount" to money(discountAmount),"GST" to money(gst),"Total" to money(total)))};DseMessageFeedback(msg)
    }},confirmButton={Button(enabled=date.isNotBlank()&&valid.isNotBlank()&&source.isNotBlank()&&party?.id!=null&&qlines.isNotEmpty()&&qlines.all{it.code.isNotBlank()&&it.quantity>0&&it.rate>=0&&it.discount in 0.0..100.0&&it.gst in 0.0..100.0},onClick={val cp=party?:return@Button;onSave(QuotationSaveRequest(id=current?.id,date=date,valid=valid,customerId=cp.id?:0,subtotal=subtotal,discountAmount=discountAmount,gstAmount=gst,total=total,remarks=remarks,followUp=follow,salesperson=salesperson,source=source,createdBy=current?.createdBy?:username,lines=qlines))}){Text("Save Quotation")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
    lineEditor?.let{i->if(i in lines.indices)QuoteLineEditor(api,lines[i],{lineEditor=null}){lines[i]=it;lineEditor=null}}
}

@Composable
private fun QuoteLineEditor(api:DseErpHttpClient,initial:QuoteLineDraft,onClose:()->Unit,onSave:(QuoteLineDraft)->Unit){
    var d by remember{mutableStateOf(initial)};var item by remember{mutableStateOf<MasterItem?>(initial.item)};val qty=d.qty.toDoubleOrNull()?:0.0;val rate=d.rate.toDoubleOrNull()?:0.0;val dis=d.discount.toDoubleOrNull()?:0.0;val gst=d.gst.toDoubleOrNull()?:0.0
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Quotation Item")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){ItemSelector(api,item,onSelected={i->item=i;d=d.copy(item=i,code=i.itemCode,description=i.description,rate=i.sellingPrice.toString(),gst=i.gst.toString(),discount=i.discountPercent.toString())},onCleared={item=null;d=d.copy(item=null,code="",description="")});DseNumberField("Quantity",d.qty,min=0.000001,required=true,onValue={d=d.copy(qty=it)});DseNumberField("Rate",d.rate,min=0.0,required=true,onValue={d=d.copy(rate=it)});DseNumberField("Discount %",d.discount,min=0.0,max=100.0,onValue={d=d.copy(discount=it)});DseNumberField("GST %",d.gst,min=0.0,max=100.0,required=true,onValue={d=d.copy(gst=it)});Text("Line total ${money(d.toQuoteLine().total)}")}},confirmButton={Button(enabled=d.code.isNotBlank()&&qty>0&&rate>=0&&dis in 0.0..100.0&&gst in 0.0..100.0,onClick={onSave(d)}){Text("Apply")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun QuoteEmailDialog(api:DseErpHttpClient,r:QuotationRecord,username:String,onClose:()->Unit,onDone:(String)->Unit){
    var recipient by remember{mutableStateOf(r.email.orEmpty())};var subject by remember{mutableStateOf("${businessName()} Quotation ${r.no}")};var body by remember{mutableStateOf("Dear ${r.customer},\n\nPlease find your quotation ${r.no} attached.\n\nRegards,\n${businessName()}")};var msg by remember{mutableStateOf("Preparing official PDF…")};var payload by remember{mutableStateOf<BusinessDocumentPayload?>(null)};val scope=rememberCoroutineScope()
    LaunchedEffect(r.id){when(val x=api.quotationLines(r.id)){is ApiResult.Success->{payload=quotationBusinessDocument(r,x.value);msg="Official PDF document attached"};else->msg=x.readableMessage()}}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Email ${r.no}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Recipient",recipient,singleLine=true,required=true,onValue={recipient=it});DseField("Subject",subject,singleLine=true,required=true,onValue={subject=it});DseField("Message",body,onValue={body=it});DseMessageFeedback(msg)}},confirmButton={PremiumPrimaryButton("Send Email",{scope.launch{val doc=payload;if(doc==null){msg="Quotation PDF is not ready";return@launch};val bytes=businessDocumentPdf(doc);val req=BusinessEmailRequest(recipient.trim(),subject.trim(),body,"Quotation_${r.no}.pdf",encodeBase64Portable(bytes));when(val x=api.sendBusinessEmail(req)){is ApiResult.Success->{if(x.value.success){api.quotationSent(r.id,"EMAIL");api.logCommunication(CommunicationRequest("QUOTATION",r.id,"EMAIL",recipient,subject,"SENT","",username));onDone(x.value.message)}else msg=x.value.message};else->msg=x.readableMessage()}}},enabled=recipient.contains("@")&&payload!=null,leadingIcon=Icons.Rounded.Email)},dismissButton={PremiumSecondaryButton("Cancel",onClose)})
}

@Composable private fun QuoteFollowUpDialog(api:DseErpHttpClient,r:QuotationRecord,onClose:()->Unit,onDone:(String)->Unit){var date by remember{mutableStateOf(r.followUp.orEmpty())};var notes by remember{mutableStateOf("")};var msg by remember{mutableStateOf("")};val scope=rememberCoroutineScope();PremiumAlertDialog(onDismissRequest=onClose,title={Text("Quotation Follow-up")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseDateField("Follow-up Date",date,required=true,onValue={date=it});DseField("Notes",notes,onValue={notes=it});DseMessageFeedback(msg)}},confirmButton={Button(enabled=date.isNotBlank(),onClick={scope.launch{when(val x=api.quotationFollowUp(r.id,date,notes)){is ApiResult.Success->onDone("Follow-up updated");else->msg=x.readableMessage()}}}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}
@Composable private fun QuoteNotesDialog(api:DseErpHttpClient,r:QuotationRecord,onClose:()->Unit,onDone:(String)->Unit){var value by remember{mutableStateOf(r.remarks.orEmpty())};var msg by remember{mutableStateOf("")};val scope=rememberCoroutineScope();PremiumAlertDialog(onDismissRequest=onClose,title={Text("Quotation Notes")},text={Column{DseField("Notes",value,onValue={value=it});DseMessageFeedback(msg)}},confirmButton={Button(onClick={scope.launch{when(val x=api.quotationNotes(r.id,value)){is ApiResult.Success->onDone("Notes updated");else->msg=x.readableMessage()}}}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}
private fun QuoteLineDraft.toQuoteLine():QuotationLine{val q=qty.toDoubleOrNull()?:0.0;val r=rate.toDoubleOrNull()?:0.0;val g=gst.toDoubleOrNull()?:0.0;val dis=discount.toDoubleOrNull()?:0.0;return QuotationLine(code,description,q,r,g,dis,lineTotal(q,r,dis,g))}
private fun roundQuote(v:Double)=kotlin.math.round(v*100.0)/100.0
private fun quoteShareText(r:QuotationRecord)=buildString{appendLine("${businessName()} • Quotation ${r.no}");appendLine("Customer: ${r.customer}");appendLine("Date: ${r.date} • Valid: ${r.valid.orEmpty()}");appendLine("Amount: ${money(r.amount)}");appendLine("Status: ${r.status.orEmpty()}")}


private suspend fun exportQuotationCsv(api:DseErpHttpClient,q:String,f:QuoteFilter):ApiResult<List<QuotationRecord>>{
    val rows=mutableListOf<QuotationRecord>();var page=0
    while(page<1000){when(val r=api.quotationsPage(page,100,q,f.number,f.customer,f.status,f.from,f.to,f.valid,f.salesperson,f.minAmount,f.maxAmount,f.followUp,f.source)){is ApiResult.Success->{rows+=r.value.rows;if(page+1>=r.value.totalPages)return ApiResult.Success(rows);page++};is ApiResult.NetworkError->return ApiResult.NetworkError(r.message,r.requestMayHaveReachedServer);is ApiResult.DecodeError->return ApiResult.DecodeError(r.message,r.status);is ApiResult.Unauthorized->return r;is ApiResult.Forbidden->return r;is ApiResult.Conflict->return r;is ApiResult.NotFound->return r;is ApiResult.ServerError->return r;is ApiResult.UnsafeEndpoint->return r;is ApiResult.NotImplemented->return r}}
    return ApiResult.ServerError(400,"Quotation export exceeded safe page limit")
}
private suspend fun exportQuotationRegister(api:DseErpHttpClient,q:String,f:QuoteFilter,format:String):String=when(val x=exportQuotationCsv(api,q,f)){
    is ApiResult.Success->{val headers=listOf("Quotation","Date","Customer","Amount","Status","Valid Until","Source","Salesperson","Converted Sale");val rows=x.value.map{r->listOf(r.no,r.date,r.customer,r.amount.toString(),r.status.orEmpty(),r.valid.orEmpty(),r.source.orEmpty(),r.salesperson.orEmpty(),r.converted.orEmpty())};if(platformShareTabularExport("Quotation Register","${businessName().replace(" ","_")}_Quotation_Register",headers,rows,format))"Quotation $format export prepared" else "Unable to prepare Quotation $format export"}
    else->x.readableMessage()
}
