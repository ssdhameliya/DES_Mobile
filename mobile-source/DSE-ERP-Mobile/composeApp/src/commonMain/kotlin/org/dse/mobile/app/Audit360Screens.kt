@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

@Composable
internal fun GlobalAuditWorkspace(api:DseErpHttpClient,p:PermissionContext,onDeepLink:(String)->Unit){
    var filter by remember{mutableStateOf(AuditFilter())}
    var page by remember{mutableIntStateOf(0)}
    var data by remember{mutableStateOf<AuditGlobalPage?>(null)}
    var msg by remember{mutableStateOf("Loading audit trail…")}
    var selected by remember{mutableStateOf<AuditEventRow?>(null)}
    var refresh by remember{mutableIntStateOf(0)}
    LaunchedEffect(page,filter,refresh){
        when(val r=api.globalAudit(page,50,filter)){
            is ApiResult.Success->{data=r.value;msg="${r.value.total} audit event(s)"}
            else->msg=r.readableMessage()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=7.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            PremiumIconTile(Icons.Rounded.History,DseInfo,size=38.dp)
            Column(Modifier.weight(1f)){Text("Global Audit Trail",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold);Text("Server-owned field changes and lifecycle events",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            FilledTonalIconButton(onClick={refresh++}){Icon(Icons.Rounded.Refresh,"Refresh")}
        }
        data?.let{d->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                DseMetricTile("Business",d.businessChanges.toString(),Icons.Rounded.EditNote,Modifier.weight(1f),DseInfo)
                DseMetricTile("Financial",d.financialEvents.toString(),Icons.Rounded.Payments,Modifier.weight(1f),DseSuccess)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                DseMetricTile("Documents",d.documentEvents.toString(),Icons.Rounded.Description,Modifier.weight(1f),DsePurple)
                DseMetricTile("Comms",d.communications.toString(),Icons.Rounded.Forum,Modifier.weight(1f),DseWarning)
            }
        }
        PremiumCard(Modifier.fillMaxWidth(),padding=10.dp){
            DseField("Search",filter.query,singleLine=true,onValue={filter=filter.copy(query=it);page=0})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Box(Modifier.weight(1f)){DseField("Module",filter.module,singleLine=true,onValue={filter=filter.copy(module=it);page=0})}
                Box(Modifier.weight(1f)){DseField("Action",filter.action,singleLine=true,onValue={filter=filter.copy(action=it);page=0})}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Box(Modifier.weight(1f)){DseField("User",filter.user,singleLine=true,onValue={filter=filter.copy(user=it);page=0})}
                Box(Modifier.weight(1f)){DseField("Reference",filter.reference,singleLine=true,onValue={filter=filter.copy(reference=it);page=0})}
            }
            if(filter!=AuditFilter())TextButton(onClick={filter=AuditFilter();page=0}){Icon(Icons.Rounded.FilterAltOff,null);Spacer(Modifier.width(4.dp));Text("Clear filters")}
        }
        DseMessageFeedback(msg)
        data?.rows.orEmpty().forEach{e->
            DseRecordCard(
                title=e.referenceNo.ifBlank{"${e.entityType} #${e.entityId}"},
                subtitle=e.detail.ifBlank{e.action},
                amount="",
                statuses=listOf("Action" to e.action,"Category" to e.category),
                meta=listOf(e.createdBy,e.createdAt,e.source).filter{it.isNotBlank()}.joinToString(" • "),
                onActions={selected=e},
            ){selected=e}
        }
        data?.let{PagingControls(it.page,it.totalPages,it.total){page=it}}
    }
    selected?.let{AuditEventDialog(it,{selected=null}){
        val segment=it.entityType.lowercase().replace('_','-')
        val ref=it.referenceNo
        if(ref.isNotBlank())onDeepLink("dseerp://$segment/$ref")
    }}
}

@Composable
private fun AuditEventDialog(event:AuditEventRow,onClose:()->Unit,onOpen:()->Unit){
    DetailDialog(event.referenceNo.ifBlank{"Audit #${event.id}"},onClose){
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){DseStatus("",event.action);if(event.category.isNotBlank())DseStatus("",event.category)}
        listOf("Entity" to "${event.entityType} #${event.entityId}","User" to event.createdBy,"Time" to event.createdAt,"Source" to event.source,"Detail" to event.detail).filter{it.second.isNotBlank()}.forEach{(k,v)->
            PremiumCard(Modifier.fillMaxWidth(),padding=10.dp){Text(k,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);Text(v)}
        }
        if(event.changes.isNotEmpty()){
            Text("Field changes",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold)
            event.changes.forEach{c->PremiumCard(Modifier.fillMaxWidth(),padding=10.dp){Text(c.fieldName,fontWeight=FontWeight.Bold);Text("Old: ${c.oldValue.ifBlank{"—"}}",style=MaterialTheme.typography.bodySmall);Text("New: ${c.newValue.ifBlank{"—"}}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}}
        }
        if(event.referenceNo.isNotBlank())PremiumPrimaryButton("Open ERP Record",onOpen,Modifier.fillMaxWidth(),leadingIcon=Icons.Rounded.OpenInNew)
    }
}

@Composable
internal fun RecordAuditDialog(api:DseErpHttpClient,type:String,id:Long,reference:String,onClose:()->Unit){
    var rows by remember{mutableStateOf<List<AuditEventRow>>(emptyList())}
    var q by remember{mutableStateOf("")};var action by remember{mutableStateOf("")};var user by remember{mutableStateOf("")};var category by remember{mutableStateOf("")};var source by remember{mutableStateOf("")}
    var msg by remember{mutableStateOf("Loading record audit…")}
    LaunchedEffect(type,id){when(val r=api.recordAudit(type,id)){is ApiResult.Success->{rows=r.value;msg="${rows.size} audit event(s)"};else->msg=r.readableMessage()}}
    val filtered=rows.filter{e->
        (q.isBlank()||(e.detail+" "+e.referenceNo+" "+e.changes.joinToString(" "){it.fieldName+" "+it.oldValue+" "+it.newValue}).contains(q,true)) &&
        (action.isBlank()||e.action.contains(action,true)) && (user.isBlank()||e.createdBy.contains(user,true)) &&
        (category.isBlank()||e.category.contains(category,true)) && (source.isBlank()||e.source.contains(source,true))
    }
    DetailDialog("Audit Trail • $reference",onClose){
        DseMessageFeedback(msg)
        DseField("Search changes",q,singleLine=true,onValue={q=it})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.weight(1f)){DseField("Action",action,singleLine=true,onValue={action=it})};Box(Modifier.weight(1f)){DseField("User",user,singleLine=true,onValue={user=it})}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.weight(1f)){DseField("Category",category,singleLine=true,onValue={category=it})};Box(Modifier.weight(1f)){DseField("Source",source,singleLine=true,onValue={source=it})}}
        filtered.forEach{e->PremiumCard(Modifier.fillMaxWidth(),padding=10.dp){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(e.action,fontWeight=FontWeight.Bold);Text(e.createdBy+" • "+e.createdAt,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(e.category.isNotBlank())DseStatus("",e.category)};if(e.detail.isNotBlank())Text(e.detail,style=MaterialTheme.typography.bodySmall);e.changes.forEach{c->Text("${c.fieldName}: ${c.oldValue.ifBlank{"—"}} → ${c.newValue.ifBlank{"—"}}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}}
        }
    }
}

@Composable
internal fun ReferenceAuditDialog(api:DseErpHttpClient,type:String,reference:String,onClose:()->Unit){
    var rows by remember{mutableStateOf<List<AuditEventRow>>(emptyList())}
    var q by remember{mutableStateOf("")};var action by remember{mutableStateOf("")};var user by remember{mutableStateOf("")};var category by remember{mutableStateOf("")};var source by remember{mutableStateOf("")}
    var msg by remember{mutableStateOf("Loading record audit…")}
    LaunchedEffect(type,reference){
        when(val r=api.globalAudit(0,100,AuditFilter(reference=reference))){
            is ApiResult.Success->{rows=r.value.rows.filter{it.referenceNo.equals(reference,true)};msg="${rows.size} audit event(s)"}
            else->msg=r.readableMessage()
        }
    }
    val filtered=rows.filter{e->
        (q.isBlank()||(e.detail+" "+e.referenceNo+" "+e.changes.joinToString(" "){it.fieldName+" "+it.oldValue+" "+it.newValue}).contains(q,true)) &&
        (action.isBlank()||e.action.contains(action,true)) && (user.isBlank()||e.createdBy.contains(user,true)) &&
        (category.isBlank()||e.category.contains(category,true)) && (source.isBlank()||e.source.contains(source,true))
    }
    DetailDialog("Audit Trail • $reference",onClose){
        DseMessageFeedback(msg)
        DseField("Search changes",q,singleLine=true,onValue={q=it})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.weight(1f)){DseField("Action",action,singleLine=true,onValue={action=it})};Box(Modifier.weight(1f)){DseField("User",user,singleLine=true,onValue={user=it})}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.weight(1f)){DseField("Category",category,singleLine=true,onValue={category=it})};Box(Modifier.weight(1f)){DseField("Source",source,singleLine=true,onValue={source=it})}}
        filtered.forEach{e->PremiumCard(Modifier.fillMaxWidth(),padding=10.dp){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(e.action,fontWeight=FontWeight.Bold);Text(e.createdBy+" • "+e.createdAt,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(e.category.isNotBlank())DseStatus("",e.category)};if(e.detail.isNotBlank())Text(e.detail,style=MaterialTheme.typography.bodySmall);e.changes.forEach{c->Text("${c.fieldName}: ${c.oldValue.ifBlank{"—"}} → ${c.newValue.ifBlank{"—"}}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}}}
    }
}

private enum class Party360Tab(val label:String){OVERVIEW("Overview"),TRANSACTIONS("Transactions"),CONTACTS("Contacts"),NOTES("Notes"),DOCUMENTS("Documents")}

@Composable
internal fun Party360Dialog(api:DseErpHttpClient,p:PermissionContext,type:String,partyId:Int,partyName:String,onClose:()->Unit,onTarget:(RecordTarget)->Unit){
    val customer=type.equals("CUSTOMER",true)
    var tab by remember{mutableStateOf(Party360Tab.OVERVIEW)}
    var customerSummary by remember{mutableStateOf<Customer360Summary?>(null)}
    var supplierSummary by remember{mutableStateOf<Supplier360Summary?>(null)}
    var contacts by remember{mutableStateOf<List<Party360ContactRow>>(emptyList())}
    var notes by remember{mutableStateOf<List<Party360NoteRow>>(emptyList())}
    var quotations by remember{mutableStateOf<List<Party360QuotationRow>>(emptyList())}
    var invoices by remember{mutableStateOf<List<Party360InvoiceRow>>(emptyList())}
    var payments by remember{mutableStateOf<List<Party360PaymentRow>>(emptyList())}
    var attachments by remember{mutableStateOf<List<AttachmentMeta>>(emptyList())}
    var msg by remember{mutableStateOf("Loading 360° workspace…")}
    var refresh by remember{mutableIntStateOf(0)}
    var contactEdit by remember{mutableStateOf<Party360ContactRow?>(null)}
    var contactCreate by remember{mutableStateOf(false)}
    var noteEdit by remember{mutableStateOf<Party360NoteRow?>(null)}
    var noteCreate by remember{mutableStateOf(false)}
    var attachmentBusy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    suspend fun load(){
        if(customer){
            when(val r=api.customer360Summary(partyId)){is ApiResult.Success->{customerSummary=r.value;msg="Customer 360° loaded"};else->msg=r.readableMessage()}
            contacts=(api.customer360Contacts(partyId) as? ApiResult.Success)?.value.orEmpty()
            notes=(api.customer360Notes(partyId) as? ApiResult.Success)?.value.orEmpty()
            quotations=(api.customer360Quotations(partyId) as? ApiResult.Success)?.value.orEmpty()
            invoices=(api.customer360Invoices(partyId) as? ApiResult.Success)?.value.orEmpty()
            payments=(api.customer360Payments(partyId) as? ApiResult.Success)?.value.orEmpty()
        }else{
            when(val r=api.supplier360Summary(partyId)){is ApiResult.Success->{supplierSummary=r.value;msg="Supplier 360° loaded"};else->msg=r.readableMessage()}
            contacts=(api.supplier360Contacts(partyId) as? ApiResult.Success)?.value.orEmpty()
            notes=(api.supplier360Notes(partyId) as? ApiResult.Success)?.value.orEmpty()
            invoices=(api.supplier360Purchases(partyId) as? ApiResult.Success)?.value.orEmpty()
            payments=(api.supplier360Payments(partyId) as? ApiResult.Success)?.value.orEmpty()
        }
        attachments=(api.documentAttachments(if(customer)"CUSTOMER" else "SUPPLIER",partyId) as? ApiResult.Success)?.value.orEmpty()
    }
    LaunchedEffect(refresh){load()}
    PremiumAlertDialog(
        onDismissRequest=onClose,
        title={Column{Text("${if(customer)"Customer" else "Supplier"} 360°",fontWeight=FontWeight.ExtraBold);Text(partyName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
        text={Column(Modifier.fillMaxWidth().heightIn(max=680.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            DseMessageFeedback(msg)
            ScrollableTabRow(Party360Tab.entries.indexOf(tab),edgePadding=0.dp){Party360Tab.entries.forEach{t->Tab(tab==t,{tab=t},text={Text(t.label)})}}
            Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){
                when(tab){
                    Party360Tab.OVERVIEW->{
                        if(customer)customerSummary?.let{s->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){DseMetricTile("Receivable",money(s.outstandingReceivable),Icons.Rounded.CreditCard,Modifier.weight(1f),DseInfo);DseMetricTile("Sales",money(s.totalSales),Icons.Rounded.TrendingUp,Modifier.weight(1f),DseSuccess)}
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){DseMetricTile("Open Quotes",s.openQuotationCount.toString(),Icons.Rounded.RequestQuote,Modifier.weight(1f),DsePurple);DseMetricTile("Last Payment",money(s.lastPaymentAmount),Icons.Rounded.Payments,Modifier.weight(1f),DseWarning)}
                            PartySummaryCard(s.customer)
                        }?:DseLoadingState("Loading customer summary…")
                        else supplierSummary?.let{s->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){DseMetricTile("Payable",money(s.outstandingPayable),Icons.Rounded.CreditCard,Modifier.weight(1f),DseWarning);DseMetricTile("Purchases",money(s.totalPurchases),Icons.Rounded.ShoppingBag,Modifier.weight(1f),DseInfo)}
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){DseMetricTile("Purchase Count",s.purchaseCount.toString(),Icons.Rounded.ReceiptLong,Modifier.weight(1f),DsePurple);DseMetricTile("Last Payment",money(s.lastPaymentAmount),Icons.Rounded.Payments,Modifier.weight(1f),DseSuccess)}
                            PartySummaryCard(s.supplier)
                        }?:DseLoadingState("Loading supplier summary…")
                        if((customer&&p.can("SALES","CREATE"))||(!customer&&p.can("PURCHASE","CREATE")))PremiumPrimaryButton(if(customer)"New Sale" else "New Purchase",{onClose();onTarget(RecordTarget(if(customer)"SALES" else "PURCHASE","__CREATE__",partyId.toLong(),source="360:$partyId"))},Modifier.fillMaxWidth(),leadingIcon=if(customer)Icons.Rounded.AddShoppingCart else Icons.Rounded.ShoppingBag)
                    }
                    Party360Tab.TRANSACTIONS->{
                        if(customer&&quotations.isNotEmpty()){Text("Quotations",fontWeight=FontWeight.ExtraBold);quotations.forEach{q->DseRecordCard(q.no,q.status,money(q.amount),listOf("Valid" to q.valid),q.date){onClose();onTarget(RecordTarget("QUOTATION",q.no,q.id.toLong()))}}}
                        Text(if(customer)"Invoices" else "Purchases",fontWeight=FontWeight.ExtraBold)
                        invoices.forEach{i->DseRecordCard(i.invoiceNo,i.paymentStatus,money(i.totalAmount),listOf("Outstanding" to money(i.outstanding),"Status" to i.documentStatus),i.invoiceDate){onClose();onTarget(RecordTarget(if(customer)"SALES" else "PURCHASE",i.invoiceNo,i.id.toLong()))}}
                        Text("Payments",fontWeight=FontWeight.ExtraBold)
                        payments.forEach{r->PremiumCard(Modifier.fillMaxWidth(),padding=10.dp){Text(r.invoiceNo.ifBlank{r.referenceNo},fontWeight=FontWeight.Bold);Text("${r.paymentDate} • ${r.paymentMode} • ${money(r.amount)}",style=MaterialTheme.typography.bodySmall)}}
                    }
                    Party360Tab.CONTACTS->{
                        if((customer&&p.can("CUSTOMERS","EDIT"))||(!customer&&p.can("SUPPLIERS","EDIT")))PremiumSecondaryButton("Add Contact",{contactCreate=true},Modifier.fillMaxWidth(),icon=Icons.Rounded.PersonAdd)
                        contacts.forEach{c->DseRecordCard(c.name,c.designation,"",listOf("" to if(c.primary)"PRIMARY" else "CONTACT"),listOf(c.mobile,c.email).filter{it.isNotBlank()}.joinToString(" • "),onActions={contactEdit=c}){contactEdit=c}}
                    }
                    Party360Tab.NOTES->{
                        if((customer&&p.can("CUSTOMERS","EDIT"))||(!customer&&p.can("SUPPLIERS","EDIT")))PremiumSecondaryButton("Add Note",{noteCreate=true},Modifier.fillMaxWidth(),icon=Icons.Rounded.NoteAdd)
                        notes.forEach{n->DseRecordCard("Note #${n.id}",n.note,"",emptyList(),"${n.createdBy} • ${n.createdAt}",onActions={noteEdit=n}){noteEdit=n}}
                    }
                    Party360Tab.DOCUMENTS->{
                        if(!attachmentBusy&&((customer&&p.can("CUSTOMERS","EDIT"))||(!customer&&p.can("SUPPLIERS","EDIT"))))PremiumSecondaryButton("Add Document",{
                            attachmentBusy=true
                            platformPickAttachment { pick->
                                val name=pick.fileName;val encoded=pick.base64
                                if(name.isNullOrBlank()||encoded.isNullOrBlank()){attachmentBusy=false;msg=pick.error?:"No attachment selected";return@platformPickAttachment}
                                scope.launch{
                                    try{when(val r=api.addDocumentAttachment(if(customer)"CUSTOMER" else "SUPPLIER",partyId,name,decodeBase64Portable(encoded))){is ApiResult.Success->{msg="Document added";refresh++};else->msg=r.readableMessage()}}
                                    finally{attachmentBusy=false}
                                }
                            }
                        },Modifier.fillMaxWidth(),icon=Icons.Rounded.AttachFile)
                        attachments.forEach{a->DseRecordCard(a.fileName,a.createdBy,"",emptyList(),a.createdAt,onActions={scope.launch{when(val f=api.documentAttachmentFile(if(customer)"CUSTOMER" else "SUPPLIER",partyId,a.id)){is ApiResult.Success->platformShareFile(a.fileName,a.fileName,f.value);else->msg=f.readableMessage()}}}){scope.launch{when(val f=api.documentAttachmentFile(if(customer)"CUSTOMER" else "SUPPLIER",partyId,a.id)){is ApiResult.Success->platformShareFile(a.fileName,a.fileName,f.value);else->msg=f.readableMessage()}}}}
                    }
                }
            }
        }},
        confirmButton={TextButton(onClick=onClose){Text("Close")}},
        dismissButton={FilledTonalIconButton(onClick={refresh++}){Icon(Icons.Rounded.Refresh,"Refresh")}},
    )
    if(contactCreate)Party360ContactEditor(null,{contactCreate=false}){save->scope.launch{val r=if(customer)api.saveCustomer360Contact(partyId,save) else api.saveSupplier360Contact(partyId,save);when(r){is ApiResult.Success->{contactCreate=false;refresh++};else->msg=r.readableMessage()}}}
    contactEdit?.let{c->Party360ContactEditor(c,{contactEdit=null}){save->scope.launch{val r=if(customer)api.saveCustomer360Contact(partyId,save) else api.saveSupplier360Contact(partyId,save);when(r){is ApiResult.Success->{contactEdit=null;refresh++};else->msg=r.readableMessage()}}}}
    if(noteCreate)Party360NoteEditor(null,{noteCreate=false}){save->scope.launch{val r=if(customer)api.saveCustomer360Note(partyId,save) else api.saveSupplier360Note(partyId,save);when(r){is ApiResult.Success->{noteCreate=false;refresh++};else->msg=r.readableMessage()}}}
    noteEdit?.let{n->Party360NoteEditor(n,{noteEdit=null}){save->scope.launch{val r=if(customer)api.saveCustomer360Note(partyId,save) else api.saveSupplier360Note(partyId,save);when(r){is ApiResult.Success->{noteEdit=null;refresh++};else->msg=r.readableMessage()}}}}
}

@Composable private fun PartySummaryCard(p:Party360Party){PremiumCard(Modifier.fillMaxWidth(),padding=11.dp){Text("${p.code} • ${p.name}",fontWeight=FontWeight.ExtraBold);if(p.contactPerson.isNotBlank())Text(p.contactPerson);if(p.phone.isNotBlank()||p.email.isNotBlank())Text(listOf(p.phone,p.email).filter{it.isNotBlank()}.joinToString(" • "),style=MaterialTheme.typography.bodySmall);if(p.gstin.isNotBlank())Text("GSTIN ${p.gstin}",style=MaterialTheme.typography.bodySmall);if(p.address.isNotBlank())Text(p.address,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}

@Composable private fun Party360ContactEditor(current:Party360ContactRow?,onClose:()->Unit,onSave:(Party360ContactSave)->Unit){
    var name by remember{mutableStateOf(current?.name.orEmpty())};var designation by remember{mutableStateOf(current?.designation.orEmpty())};var dept by remember{mutableStateOf(current?.department.orEmpty())};var mobile by remember{mutableStateOf(current?.mobile.orEmpty())};var email by remember{mutableStateOf(current?.email.orEmpty())};var primary by remember{mutableStateOf(current?.primary?:false)};var notes by remember{mutableStateOf(current?.notes.orEmpty())}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"Add Contact" else "Edit Contact")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){DseField("Name",name,required=true,onValue={name=it});DseField("Designation",designation,onValue={designation=it});DseField("Department",dept,onValue={dept=it});DseField("Mobile",mobile,singleLine=true,onValue={mobile=it});DseField("Email",email,singleLine=true,onValue={email=it});Row(verticalAlignment=Alignment.CenterVertically){Checkbox(primary,{primary=it});Text("Primary contact")};DseField("Notes",notes,onValue={notes=it})}},confirmButton={Button(enabled=name.isNotBlank(),onClick={onSave(Party360ContactSave(current?.id,name,designation,dept,mobile,email,primary,notes,current?.rowVersion?:0))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun Party360NoteEditor(current:Party360NoteRow?,onClose:()->Unit,onSave:(Party360NoteSave)->Unit){var note by remember{mutableStateOf(current?.note.orEmpty())};PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"Add Note" else "Edit Note")},text={DseField("Note",note,required=true,onValue={note=it})},confirmButton={Button(enabled=note.isNotBlank(),onClick={onSave(Party360NoteSave(current?.id,note,current?.rowVersion?:0))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}
