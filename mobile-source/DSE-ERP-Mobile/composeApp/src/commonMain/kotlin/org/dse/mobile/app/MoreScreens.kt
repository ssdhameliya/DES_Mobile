@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.dse.mobile.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dse.mobile.core.api.*
import org.dse.mobile.core.config.MobileBuildInfo
import org.dse.mobile.core.model.*

private fun PermissionContext.canOpen(d:MoreDestination):Boolean=when(d){
    MoreDestination.PURCHASE->can("PURCHASE")
    MoreDestination.QUOTATIONS->can("QUOTATION")
    MoreDestination.SALES_RETURNS->can("SALES")
    MoreDestination.PURCHASE_RETURNS->can("PURCHASE")
    MoreDestination.MASTERS->can("CUSTOMERS")||can("SUPPLIERS")||can("INVENTORY")||can("MASTERS")||isAdmin()
    MoreDestination.INVENTORY->can("INVENTORY")
    MoreDestination.PURCHASE_RECON->can("PURCHASE_RECON")
    MoreDestination.COMMUNICATIONS,MoreDestination.NOTIFICATIONS->can("COMMUNICATION")
    MoreDestination.REMINDERS->can("REMINDERS")
    MoreDestination.REPORTS->can("REPORTS")||can("SALES")||can("PURCHASE")
    MoreDestination.PROFILE->true
    MoreDestination.ADMIN->isAdmin()||can("USERS")
    MoreDestination.IMPORT->can("INVENTORY","CREATE")||can("CUSTOMERS","CREATE")||can("SUPPLIERS","CREATE")||can("PURCHASE_RECON","IMPORT")||isAdmin()
    MoreDestination.SYNC,MoreDestination.ABOUT->true
}

@Composable internal fun MoreWorkspace(api:DseErpHttpClient,p:PermissionContext,username:String,destination:MoreDestination?,onDestination:(MoreDestination?)->Unit,onDeepLink:(String)->Unit,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    if(destination==null){
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(horizontal=14.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.fillMaxWidth().background(DseHeroGradient,androidx.compose.foundation.shape.RoundedCornerShape(26.dp))){
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    DseBrandMark(compact=true,dark=true)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Text("Explore Jasvi Industries",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold,color=androidx.compose.ui.graphics.Color.White)
                        Text("Every business module, one mobile workspace.",style=MaterialTheme.typography.bodySmall,color=androidx.compose.ui.graphics.Color.White.copy(.78f))
                    }
                    Icon(Icons.Rounded.AutoAwesome,null,tint=androidx.compose.ui.graphics.Color.White.copy(.85f))
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){DseStatus("","SECURE");DseStatus("","OFFLINE READY");DseStatus("","ROLE BASED")}
            MoreDestination.entries.filter(p::canOpen).forEach{d->
                Surface(
                    shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color=MaterialTheme.colorScheme.surface,
                    border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(.34f)),
                    shadowElevation=2.dp,
                    modifier=Modifier.fillMaxWidth().clickable{onDestination(d)}
                ){
                    Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){
                        PremiumIconTile(moreIcon(d),moreAccent(d),size=42.dp)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                            Text(d.label,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            Text(moreSubtitle(d),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight,null,tint=MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        return
    }
    if(!p.canOpen(destination)){PermissionDeniedCard(destination.label){onDestination(null)};return}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilledTonalIconButton(onClick={onDestination(null)},shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)){Icon(Icons.Rounded.ArrowBack,"Back")}
            PremiumIconTile(moreIcon(destination),moreAccent(destination),size=38.dp)
            Text(destination.label,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold)
        }
        Box(Modifier.weight(1f)){when(destination){
            MoreDestination.PURCHASE->PurchaseWorkspace(api,p,username,openTarget,onTargetConsumed)
            MoreDestination.QUOTATIONS->QuotationsWorkspace(api,p,username,openTarget,onTargetConsumed,onDeepLink)
            MoreDestination.SALES_RETURNS->ReturnsWorkspace(api,p,true,username,openTarget,onTargetConsumed,onDeepLink)
            MoreDestination.PURCHASE_RETURNS->ReturnsWorkspace(api,p,false,username,openTarget,onTargetConsumed,onDeepLink)
            MoreDestination.MASTERS->MasterDataWorkspace(api,p,openTarget,onTargetConsumed)
            MoreDestination.INVENTORY->InventoryWorkspace(api,p,username,openTarget,onTargetConsumed)
            MoreDestination.PURCHASE_RECON->PurchaseReconWorkspace(api,p,openTarget,onTargetConsumed)
            MoreDestination.COMMUNICATIONS->CommunicationWorkspace(api,p)
            MoreDestination.REMINDERS->ReminderWorkspace(api,p,username,openTarget,onTargetConsumed)
            MoreDestination.NOTIFICATIONS->NotificationWorkspace(api,p,onDeepLink)
            MoreDestination.REPORTS->ReportsWorkspace(api)
            MoreDestination.PROFILE->ProfileWorkspace(api,p)
            MoreDestination.ADMIN->AdminWorkspace(api,p)
            MoreDestination.IMPORT->DataImportWorkspace(api,username)
            MoreDestination.SYNC->SyncWorkspace(api)
            MoreDestination.ABOUT->AboutWorkspace()
        }}
    }
}

private fun moreAccent(d:MoreDestination)=when(d){
    MoreDestination.PURCHASE->DseWarning
    MoreDestination.QUOTATIONS,MoreDestination.REPORTS->DsePurple
    MoreDestination.SALES_RETURNS,MoreDestination.PURCHASE_RETURNS->DseWarning
    MoreDestination.MASTERS,MoreDestination.INVENTORY->DseInfo
    MoreDestination.PURCHASE_RECON,MoreDestination.SYNC->DseSuccess
    MoreDestination.COMMUNICATIONS,MoreDestination.NOTIFICATIONS,MoreDestination.REMINDERS->DseDanger
    MoreDestination.PROFILE,MoreDestination.ADMIN->DseIndigo
    MoreDestination.IMPORT->DseWarning
    MoreDestination.ABOUT->DseMuted
}

private fun moreSubtitle(d:MoreDestination)=when(d){
    MoreDestination.PURCHASE->"Bills, suppliers, payments and Purchase actions"
    MoreDestination.QUOTATIONS->"Quotes, follow-ups and conversion"
    MoreDestination.SALES_RETURNS->"Customer returns and refunds"
    MoreDestination.PURCHASE_RETURNS->"Supplier returns and adjustments"
    MoreDestination.MASTERS->"Customers, suppliers, items and values"
    MoreDestination.INVENTORY->"Stock balance, location and movement"
    MoreDestination.PURCHASE_RECON->"Supplier and statement matching"
    MoreDestination.COMMUNICATIONS->"Email and communication history"
    MoreDestination.REMINDERS->"Follow-ups and due actions"
    MoreDestination.NOTIFICATIONS->"Alerts and ERP updates"
    MoreDestination.REPORTS->"Business insights and exports"
    MoreDestination.PROFILE->"Profile, password and security"
    MoreDestination.ADMIN->"Users, roles and permissions"
    MoreDestination.IMPORT->"Bring business data into Jasvi Industries"
    MoreDestination.SYNC->"Offline cache and sync status"
    MoreDestination.ABOUT->"App and environment information"
}

@Composable private fun PermissionDeniedCard(name:String,onBack:()->Unit){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Card{Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Rounded.Lock,null);Text("No permission for $name");TextButton(onClick=onBack){Text("Back")}}}}}
private fun moreIcon(d:MoreDestination)=when(d){
    MoreDestination.PURCHASE->Icons.Rounded.ShoppingCart
    MoreDestination.QUOTATIONS->Icons.Rounded.RequestQuote
    MoreDestination.SALES_RETURNS,MoreDestination.PURCHASE_RETURNS->Icons.Rounded.AssignmentReturn
    MoreDestination.MASTERS->Icons.Rounded.Dataset
    MoreDestination.INVENTORY->Icons.Rounded.Inventory2
    MoreDestination.PURCHASE_RECON->Icons.Rounded.FactCheck
    MoreDestination.COMMUNICATIONS->Icons.Rounded.Forum
    MoreDestination.REMINDERS->Icons.Rounded.Alarm
    MoreDestination.NOTIFICATIONS->Icons.Rounded.Notifications
    MoreDestination.REPORTS->Icons.Rounded.Assessment
    MoreDestination.PROFILE->Icons.Rounded.ManageAccounts
    MoreDestination.ADMIN->Icons.Rounded.AdminPanelSettings
    MoreDestination.IMPORT->Icons.Rounded.UploadFile
    MoreDestination.SYNC->Icons.Rounded.Sync
    MoreDestination.ABOUT->Icons.Rounded.Info
}

private enum class MasterMode(val label:String){CUSTOMERS("Customers"),SUPPLIERS("Suppliers"),ITEMS("Items"),LOOKUPS("Master Values")}
@Composable private fun MasterDataWorkspace(api:DseErpHttpClient,p:PermissionContext,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    val allowed=MasterMode.entries.filter{m->when(m){MasterMode.CUSTOMERS->p.can("CUSTOMERS");MasterMode.SUPPLIERS->p.can("SUPPLIERS");MasterMode.ITEMS->p.can("INVENTORY");MasterMode.LOOKUPS->p.can("MASTERS")}}
    var mode by remember{mutableStateOf(allowed.firstOrNull()?:MasterMode.CUSTOMERS)}
    LaunchedEffect(openTarget?.moduleKey){when(openTarget?.moduleKey?.uppercase()){"CUSTOMER","CUSTOMERS"->if(MasterMode.CUSTOMERS in allowed)mode=MasterMode.CUSTOMERS;"SUPPLIER","SUPPLIERS"->if(MasterMode.SUPPLIERS in allowed)mode=MasterMode.SUPPLIERS;"ITEM","INVENTORY"->if(MasterMode.ITEMS in allowed)mode=MasterMode.ITEMS;"MASTER","MASTERS","LOOKUP"->if(MasterMode.LOOKUPS in allowed)mode=MasterMode.LOOKUPS}}
    Column(Modifier.fillMaxSize()){ScrollableTabRow(allowed.indexOf(mode).coerceAtLeast(0)){allowed.forEach{m->Tab(selected=mode==m,onClick={mode=m},text={Text(m.label)})}};when(mode){MasterMode.CUSTOMERS->PartyMasterScreen(api,p,"CUSTOMER",openTarget,onTargetConsumed);MasterMode.SUPPLIERS->PartyMasterScreen(api,p,"SUPPLIER",openTarget,onTargetConsumed);MasterMode.ITEMS->ItemMasterScreen(api,p,openTarget,onTargetConsumed);MasterMode.LOOKUPS->LookupScreen(api,p)}}
}

@Composable private fun PartyMasterScreen(api:DseErpHttpClient,p:PermissionContext,type:String,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    val module=if(type=="CUSTOMER")"CUSTOMERS" else "SUPPLIERS"
    var q by remember{mutableStateOf("")};var rows by remember{mutableStateOf<List<MasterParty>>(emptyList())};var msg by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)};var edit by remember{mutableStateOf<MasterParty?>(null)};var editing by remember{mutableStateOf<MasterParty?>(null)};var creating by remember{mutableStateOf(false)};var deleting by remember{mutableStateOf<MasterParty?>(null)};val scope=rememberCoroutineScope()
    LaunchedEffect(api,refresh){when(val r=api.listParties(type)){is ApiResult.Success->{rows=r.value;msg="${rows.size} ${type.lowercase()} record(s)${if(r.source==ApiDataSource.CACHE)" • cached" else ""}"};else->msg=r.readableMessage()}}
    LaunchedEffect(openTarget?.moduleKey,openTarget?.reference,rows.size){val t=openTarget;val expected=if(type=="CUSTOMER")setOf("CUSTOMER","CUSTOMERS")else setOf("SUPPLIER","SUPPLIERS");if(t!=null&&t.moduleKey.uppercase() in expected){if(t.reference=="__CREATE__"){creating=true;onTargetConsumed()}else if(t.reference.isNotBlank()&&rows.isNotEmpty()){q=t.reference;edit=rows.firstOrNull{it.partyCode.equals(t.reference,true)||it.id?.toLong()==t.recordId};onTargetConsumed()}}}
    val shown=remember(rows,q){if(q.isBlank())rows else rows.filter{listOf(it.partyCode,it.name,it.gstin,it.phone,it.email).any{x->x.orEmpty().contains(q,true)}}}
    DseRegisterShell(if(type=="CUSTOMER")"Customers / CRM" else "Suppliers / HRM","Contacts, balances and business activity",q,{q=it},msg,{refresh++},if(p.can(module,"CREATE")){{creating=true}}else null,kpis={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseHeroKpi(if(type=="CUSTOMER")"Customers" else "Suppliers",rows.size.toString(),"${rows.count{it.active}} active master record(s)",if(type=="CUSTOMER")Icons.Rounded.Groups else Icons.Rounded.LocalShipping);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Active",rows.count{it.active}.toString(),Icons.Rounded.CheckCircle,Modifier.weight(1f),DseSuccess);DseMetricTile("GSTIN",rows.count{!it.gstin.isNullOrBlank()}.toString(),Icons.Rounded.Badge,Modifier.weight(1f),DseInfo)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Opening",money(rows.sumOf{it.openingBalance}),Icons.Rounded.AccountBalanceWallet,Modifier.weight(1f),DsePurple);DseMetricTile("Matched",shown.size.toString(),Icons.Rounded.Search,Modifier.weight(1f),DseWarning)}}}){
        shown.forEach{r->
            DseRecordCard(
                "${r.partyCode} • ${r.name}",r.gstin.orEmpty(),money(r.openingBalance),
                listOf("Status" to if(r.active)"ACTIVE" else "INACTIVE"),
                meta=listOfNotNull(r.phone,r.email).joinToString(" • "),
                swipeStartActions=buildList{
                    add(SwipeAction("Open",Icons.Rounded.Visibility){edit=r})
                    if(p.can(module,"EDIT"))add(SwipeAction("Edit",Icons.Rounded.Edit){editing=r})
                },
                swipeEndActions=buildList{
                    if(p.can(module,"DELETE"))add(SwipeAction("Delete",Icons.Rounded.Delete,true){deleting=r})
                },
            ){edit=r}
        }
    }
    if(creating)PartyEditor(api,type,null,{creating=false}){party->scope.launch{when(val r=api.createParty(party)){is ApiResult.Success->{msg="Created ${r.value.partyCode}";creating=false;refresh++};else->msg=r.readableMessage()}}}
    edit?.let{old->PartyDetailDialog(old,p,module,{edit=null},{editing=it;edit=null},{deleting=it})}
    editing?.let{old->PartyEditor(api,type,old,{editing=null}){party->scope.launch{when(val r=api.updateParty(party)){is ApiResult.Success->{msg="Updated ${r.value.partyCode}";editing=null;refresh++};else->msg=r.readableMessage()}}}}
    deleting?.let{old->ConfirmDialog("Delete ${old.partyCode}","Delete this ${type.lowercase()} only if it is not referenced by ERP transactions.","Delete",true,{deleting=null}){scope.launch{val id=old.id?:return@launch;when(val r=api.deleteParty(id,old.rowVersion)){is ApiResult.Success->{msg="Deleted ${old.partyCode}";deleting=null;edit=null;refresh++};else->{msg=r.readableMessage();deleting=null}}}}}
}

@Composable private fun PartyDetailDialog(row:MasterParty,p:PermissionContext,module:String,onClose:()->Unit,onEdit:(MasterParty)->Unit,onDelete:(MasterParty)->Unit){
    DetailDialog("${row.partyCode} • ${row.name}",listOf("GSTIN" to row.gstin.orEmpty(),"Contact" to row.contactPerson.orEmpty(),"Phone" to row.phone.orEmpty(),"Email" to row.email.orEmpty(),"Address" to row.address.orEmpty(),"Opening Balance" to money(row.openingBalance),"Status" to if(row.active)"ACTIVE" else "INACTIVE"),buildList{if(p.can(module,"EDIT"))add("Edit" to {onClose();onEdit(row)});if(p.can(module,"DELETE"))add("Delete" to {onDelete(row)})},onClose)
}

@Composable private fun PartyEditor(api:DseErpHttpClient,type:String,current:MasterParty?,onClose:()->Unit,onSave:(MasterParty)->Unit){
    var code by remember{mutableStateOf(current?.partyCode.orEmpty())};var name by remember{mutableStateOf(current?.name.orEmpty())};var contact by remember{mutableStateOf(current?.contactPerson.orEmpty())};var phone by remember{mutableStateOf(current?.phone.orEmpty())};var email by remember{mutableStateOf(current?.email.orEmpty())};var gstin by remember{mutableStateOf(current?.gstin.orEmpty())};var address by remember{mutableStateOf(current?.address.orEmpty())};var opening by remember{mutableStateOf((current?.openingBalance?:0.0).toString())};var active by remember{mutableStateOf(current?.active?:true)};var msg by remember{mutableStateOf("")}
    LaunchedEffect(current,type){if(current==null&&code.isBlank())when(val r=api.nextPartyCode(type)){is ApiResult.Success->code=r.value.code;else->msg=r.readableMessage()}}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"New ${type.lowercase().replaceFirstChar{it.uppercase()}}" else "Edit ${current.partyCode}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Party Code",code,singleLine=true,readOnly=true,required=true,onValue={});DseField("Name",name,singleLine=true,required=true,onValue={name=it});DseField("Contact Person",contact,singleLine=true,onValue={contact=it});DseField("Phone",phone,singleLine=true,onValue={phone=it});DseField("Email",email,singleLine=true,onValue={email=it});DseField("GSTIN",gstin,singleLine=true,onValue={gstin=it});DseField("Address",address,onValue={address=it});DseNumberField("Opening Balance",opening,allowNegative=true,onValue={opening=it});Row(verticalAlignment=Alignment.CenterVertically){Switch(active,{active=it});PremiumOptionLabel("Active",accent=DseSuccess)};if(msg.isNotBlank())Text(msg,color=MaterialTheme.colorScheme.error)}},confirmButton={Button(enabled=code.isNotBlank()&&name.isNotBlank(),onClick={onSave((current?:MasterParty()).copy(partyType=type,partyCode=code,name=name.trim(),contactPerson=contact.trim(),phone=phone.trim(),email=email.trim(),gstin=gstin.trim(),address=address.trim(),openingBalance=opening.toDoubleOrNull()?:0.0,active=active))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun ItemMasterScreen(api:DseErpHttpClient,p:PermissionContext,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    var q by remember{mutableStateOf("")};var rows by remember{mutableStateOf<List<MasterItem>>(emptyList())};var msg by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)};var edit by remember{mutableStateOf<MasterItem?>(null)};var editing by remember{mutableStateOf<MasterItem?>(null)};var creating by remember{mutableStateOf(false)};var deleting by remember{mutableStateOf<MasterItem?>(null)};val scope=rememberCoroutineScope()
    LaunchedEffect(api,refresh){when(val r=api.listItems()){is ApiResult.Success->{rows=r.value;msg="${rows.size} item(s)${if(r.source==ApiDataSource.CACHE)" • cached" else ""}"};else->msg=r.readableMessage()}}
    LaunchedEffect(openTarget?.moduleKey,openTarget?.reference,rows.size){val t=openTarget;if(t!=null&&t.moduleKey.uppercase() in setOf("ITEM","INVENTORY")){if(t.reference=="__CREATE__"){creating=true;onTargetConsumed()}else if(t.reference.isNotBlank()&&rows.isNotEmpty()){q=t.reference;edit=rows.firstOrNull{it.itemCode.equals(t.reference,true)||it.id?.toLong()==t.recordId};onTargetConsumed()}}}
    val shown=remember(rows,q){if(q.isBlank())rows else rows.filter{listOf(it.itemCode,it.description,it.category,it.brand,it.material,it.hsn,it.location).any{x->x.orEmpty().contains(q,true)}}}
    DseRegisterShell("Item Master","Products, pricing, tax and stock defaults",q,{q=it},msg,{refresh++},if(p.can("INVENTORY","CREATE")){{creating=true}}else null,kpis={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseHeroKpi("Item Master",rows.size.toString(),"${rows.count{it.active}} active item(s)",Icons.Rounded.Inventory2);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Low Stock",rows.count{it.openingStock<=it.minimumStock}.toString(),Icons.Rounded.WarningAmber,Modifier.weight(1f),if(rows.any{it.openingStock<=it.minimumStock})DseWarning else DseSuccess);DseMetricTile("Matched",shown.size.toString(),Icons.Rounded.Search,Modifier.weight(1f),DseInfo)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Stock Qty",rows.sumOf{it.openingStock}.toString(),Icons.Rounded.Warehouse,Modifier.weight(1f),DsePurple);DseMetricTile("Stock Value",compactMoney(rows.sumOf{it.openingStock*it.purchasePrice}),Icons.Rounded.AccountBalanceWallet,Modifier.weight(1f),DseSuccess)}}}){
        shown.forEach{r->DseRecordCard(
            r.itemCode,r.description,money(r.sellingPrice),listOf("Status" to if(r.active)"ACTIVE" else "INACTIVE"),
            meta="${r.category.orEmpty()} • ${r.unit.orEmpty()} • GST ${r.gst}% • Stock ${r.openingStock}",
            swipeStartActions=buildList{
                add(SwipeAction("Open",Icons.Rounded.Visibility){edit=r})
                if(p.can("INVENTORY","EDIT"))add(SwipeAction("Edit",Icons.Rounded.Edit){editing=r})
            },
            swipeEndActions=buildList{
                if(p.can("INVENTORY","DELETE"))add(SwipeAction("Delete",Icons.Rounded.Delete,true){deleting=r})
            },
        ){edit=r}}
    }
    if(creating)ItemEditor(api,null,{creating=false}){item->scope.launch{when(val r=api.createItem(item)){is ApiResult.Success->{msg="Created ${r.value.itemCode}";creating=false;refresh++};else->msg=r.readableMessage()}}}
    edit?.let{row->DetailDialog(row.itemCode,listOf("Description" to row.description,"Category" to row.category.orEmpty(),"Brand" to row.brand.orEmpty(),"Material" to row.material.orEmpty(),"Size" to row.size.orEmpty(),"Unit" to row.unit.orEmpty(),"HSN" to row.hsn.orEmpty(),"GST" to formatPercent(row.gst),"Discount" to formatPercent(row.discountPercent),"Purchase Price" to money(row.purchasePrice),"Selling Price" to money(row.sellingPrice),"Opening Stock" to row.openingStock.toString(),"Reserved" to row.reservedStock.toString(),"Minimum Stock" to row.minimumStock.toString(),"Location" to row.location.orEmpty(),"Remarks" to row.remarks.orEmpty()),buildList{if(p.can("INVENTORY","EDIT"))add("Edit" to {editing=row;edit=null});if(p.can("INVENTORY","DELETE"))add("Delete" to {deleting=row})},{edit=null})}
    editing?.let{row->ItemEditor(api,row,{editing=null}){item->scope.launch{when(val r=api.updateItem(item)){is ApiResult.Success->{msg="Updated ${r.value.itemCode}";editing=null;refresh++};else->msg=r.readableMessage()}}}}
    deleting?.let{row->ConfirmDialog("Delete ${row.itemCode}","Deletion is allowed only when stock is zero and the item is not referenced by ERP transactions.","Delete",true,{deleting=null}){scope.launch{when(val r=api.deleteItem(row.itemCode,row.rowVersion)){is ApiResult.Success->{msg="Deleted ${row.itemCode}";deleting=null;edit=null;refresh++};else->{msg=r.readableMessage();deleting=null}}}}}
}

@Composable private fun ItemEditor(api:DseErpHttpClient,current:MasterItem?,onClose:()->Unit,onSave:(MasterItem)->Unit){
    var d by remember{mutableStateOf(current?:MasterItem())};var code by remember{mutableStateOf(current?.itemCode.orEmpty())};var msg by remember{mutableStateOf("")};var categories by remember{mutableStateOf<List<String>>(emptyList())}
    LaunchedEffect(current){if(current==null&&code.isBlank())when(val r=api.nextItemCode()){is ApiResult.Success->code=r.value.code;else->msg=r.readableMessage()};when(val c=api.lookupValuesByCode("ITEM_CATEGORY")){is ApiResult.Success->categories=c.value;else->Unit}}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"New Item" else "Edit ${current.itemCode}")},text={Column(Modifier.heightIn(max=650.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        DseField("Item Code",code,readOnly=true,singleLine=true,required=true,onValue={});DseField("Description",d.description,required=true,onValue={d=d.copy(description=it)});DseSelect("Category",d.category.orEmpty(),categories,required=true,onValue={d=d.copy(category=it)});DseField("Brand",d.brand.orEmpty(),singleLine=true,onValue={d=d.copy(brand=it)});DseField("Material",d.material.orEmpty(),singleLine=true,onValue={d=d.copy(material=it)});DseField("Size",d.size.orEmpty(),singleLine=true,onValue={d=d.copy(size=it)});DseField("Unit",d.unit.orEmpty(),singleLine=true,required=true,onValue={d=d.copy(unit=it)});DseField("HSN",d.hsn.orEmpty(),singleLine=true,required=true,onValue={d=d.copy(hsn=it)});DseNumberField("GST %",d.gst.toString(),min=0.0,max=100.0,required=true,onValue={d=d.copy(gst=it.toDoubleOrNull()?:0.0)});DseNumberField("Discount %",d.discountPercent.toString(),min=0.0,max=100.0,required=true,onValue={d=d.copy(discountPercent=it.toDoubleOrNull()?:0.0)});DseNumberField("Purchase Price",d.purchasePrice.toString(),min=0.0,onValue={d=d.copy(purchasePrice=it.toDoubleOrNull()?:0.0)});DseNumberField("Selling Price",d.sellingPrice.toString(),min=0.0,onValue={d=d.copy(sellingPrice=it.toDoubleOrNull()?:0.0)});DseNumberField("Opening Stock",d.openingStock.toString(),min=0.0,required=true,onValue={d=d.copy(openingStock=it.toDoubleOrNull()?:0.0)});DseNumberField("Minimum Stock",d.minimumStock.toString(),min=0.0,onValue={d=d.copy(minimumStock=it.toDoubleOrNull()?:0.0)});DseField("Location",d.location.orEmpty(),onValue={d=d.copy(location=it)});DseField("Remarks",d.remarks.orEmpty(),required=true,onValue={d=d.copy(remarks=it)});Row(verticalAlignment=Alignment.CenterVertically){Switch(d.active,{d=d.copy(active=it)});PremiumOptionLabel("Active",accent=DseSuccess)};if(msg.isNotBlank())Text(msg,color=MaterialTheme.colorScheme.error)
    }},confirmButton={Button(enabled=code.isNotBlank()&&d.description.isNotBlank()&&!d.category.isNullOrBlank()&&!d.unit.isNullOrBlank()&&!d.hsn.isNullOrBlank()&&!d.remarks.isNullOrBlank(),onClick={onSave(d.copy(itemCode=code))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun LookupScreen(api:DseErpHttpClient,p:PermissionContext){
    var categories by remember{mutableStateOf<List<CategoryImportDto>>(emptyList())}
    var selected by remember{mutableStateOf<CategoryImportDto?>(null)}
    var values by remember{mutableStateOf<List<LookupImportDto>>(emptyList())}
    var refresh by remember{mutableIntStateOf(0)}
    var msg by remember{mutableStateOf("")}
    var edit by remember{mutableStateOf<LookupImportDto?>(null)}
    var addingValue by remember{mutableStateOf(false)}
    var addingCategory by remember{mutableStateOf(false)}
    var editingCategory by remember{mutableStateOf<CategoryImportDto?>(null)}
    var deletingCategory by remember{mutableStateOf<CategoryImportDto?>(null)}
    var suggestedCode by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(api,refresh){
        when(val r=api.lookupCategories()){
            is ApiResult.Success->{
                categories=r.value
                selected=selected?.let{old->r.value.firstOrNull{it.categoryCode==old.categoryCode}}?:r.value.firstOrNull()
            }
            else->msg=r.readableMessage()
        }
    }
    LaunchedEffect(selected,refresh){
        val cat=selected
        if(cat!=null){
            when(val r=api.lookupsByCode(cat.categoryCode)){is ApiResult.Success->values=r.value;else->msg=r.readableMessage()}
            suggestedCode=(api.nextLookupCode(cat.categoryCode) as? ApiResult.Success)?.value?.code.orEmpty()
        }else{values=emptyList();suggestedCode=""}
    }
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("Master Data",style=MaterialTheme.typography.headlineSmall)
            if(p.can("MASTERS","CREATE"))IconButton(onClick={addingCategory=true}){Icon(Icons.Rounded.CreateNewFolder,"New category")}
        }
        DseSelect("Category",selected?.categoryName.orEmpty(),categories.map{it.categoryName},onValue={v->selected=categories.firstOrNull{it.categoryName==v}})
        selected?.let{cat->
            Text("${cat.categoryCode} • ${cat.valueCount} values • ${if(cat.active)"ACTIVE" else "INACTIVE"}",style=MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                if(p.can("MASTERS","CREATE"))OutlinedButton(onClick={addingValue=true}){Icon(Icons.Rounded.Add,null);Text("Add Value")}
                if(p.can("MASTERS","EDIT")){
                    OutlinedButton(onClick={editingCategory=cat}){Icon(Icons.Rounded.Edit,null);Text("Rename")}
                    OutlinedButton(onClick={scope.launch{when(val r=api.setCategoryActive(cat.categoryName,!cat.active,cat.rowVersion)){is ApiResult.Success->{msg=if(cat.active)"Category deactivated" else "Category activated";refresh++};else->msg=r.readableMessage()}}}){Text(if(cat.active)"Deactivate" else "Activate")}
                }
                if(p.can("MASTERS","DELETE"))OutlinedButton(onClick={deletingCategory=cat}){Icon(Icons.Rounded.Delete,null);Text("Delete")}
            }
        }
        if(msg.isNotBlank())Text(msg,style=MaterialTheme.typography.bodySmall)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(5.dp)){
            values.forEach{v->Surface(tonalElevation=1.dp,shape=MaterialTheme.shapes.small,modifier=Modifier.fillMaxWidth().clickable{edit=v}){ListItem(headlineContent={Text(v.lookupValue)},supportingContent={Text("${v.lookupCode} • ${v.description.orEmpty()}")},trailingContent={DseStatus("",if(v.active)"ACTIVE" else "INACTIVE")})}}
        }
    }
    if(addingCategory)CategoryDialog(null,{addingCategory=false}){code,name,desc->scope.launch{when(val r=api.upsertCategory(CategoryUpsertRequest(code,name,desc))){is ApiResult.Success->{msg="Category saved";addingCategory=false;refresh++};else->msg=r.readableMessage()}}}
    editingCategory?.let{cat->CategoryRenameDialog(cat,{editingCategory=null}){newName->scope.launch{when(val r=api.renameCategory(cat.categoryName,newName,cat.rowVersion)){is ApiResult.Success->{msg="Category renamed";editingCategory=null;selected=r.value;refresh++};else->msg=r.readableMessage()}}}}
    deletingCategory?.let{cat->ConfirmDialog("Delete ${cat.categoryName}","Delete this category only when it has no active master values and is not referenced by ERP records.","Delete",true,{deletingCategory=null}){scope.launch{when(val r=api.deleteCategory(cat.categoryName,cat.rowVersion)){is ApiResult.Success->{msg="Category deleted";deletingCategory=null;selected=null;refresh++};else->{msg=r.readableMessage();deletingCategory=null}}}}}
    if(addingValue)LookupDialog(selected?.categoryCode.orEmpty(),null,suggestedCode,{addingValue=false}){v->scope.launch{when(val r=api.createLookup(v)){is ApiResult.Success->{msg="Master value added";addingValue=false;refresh++};else->msg=r.readableMessage()}}}
    edit?.let{old->LookupDetail(old,p,{edit=null},{edited->scope.launch{when(val r=api.updateLookup(edited)){is ApiResult.Success->{msg="Updated";edit=null;refresh++};else->msg=r.readableMessage()}}},{scope.launch{when(val r=api.setLookupActive(old.id?:0,!old.active,old.rowVersion)){is ApiResult.Success->{edit=null;refresh++};else->msg=r.readableMessage()}}},{scope.launch{when(val r=api.deleteLookup(old.id?:0,old.rowVersion)){is ApiResult.Success->{edit=null;refresh++};else->msg=r.readableMessage()}}})}
}

@Composable private fun CategoryDialog(current:CategoryImportDto?,onClose:()->Unit,onSave:(String,String,String)->Unit){
    var code by remember{mutableStateOf(current?.categoryCode.orEmpty())};var name by remember{mutableStateOf(current?.categoryName.orEmpty())};var desc by remember{mutableStateOf(current?.description.orEmpty())}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"New Master Category" else "Edit Category")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Category Code",code,singleLine=true,required=true,onValue={code=it.uppercase().replace(' ','_')});DseField("Category Name",name,singleLine=true,required=true,onValue={name=it});DseField("Description",desc,onValue={desc=it})}},confirmButton={Button(enabled=code.isNotBlank()&&name.isNotBlank(),onClick={onSave(code,name,desc)}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
@Composable private fun CategoryRenameDialog(current:CategoryImportDto,onClose:()->Unit,onSave:(String)->Unit){
    var name by remember{mutableStateOf(current.categoryName)}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Rename ${current.categoryCode}")},text={DseField("Category Name",name,singleLine=true,required=true,onValue={name=it})},confirmButton={Button(enabled=name.isNotBlank()&&name!=current.categoryName,onClick={onSave(name.trim())}){Text("Rename")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
@Composable private fun LookupDialog(type:String,current:LookupImportDto?,suggestedCode:String="",onClose:()->Unit,onSave:(LookupImportDto)->Unit){
    var code by remember{mutableStateOf(current?.lookupCode?.ifBlank{suggestedCode}?:suggestedCode)};var value by remember{mutableStateOf(current?.lookupValue.orEmpty())};var desc by remember{mutableStateOf(current?.description.orEmpty())};var order by remember{mutableStateOf((current?.displayOrder?:0).toString())}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"Add $type" else "Edit ${current.lookupCode}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Value Code",code,readOnly=current==null&&suggestedCode.isNotBlank(),singleLine=true,onValue={if(current!=null||suggestedCode.isBlank())code=it});DseField("Value",value,singleLine=true,required=true,onValue={value=it});DseField("Description",desc,onValue={desc=it});DseNumberField("Display Order",order,min=0.0,onValue={order=it})}},confirmButton={Button(enabled=value.isNotBlank(),onClick={onSave((current?:LookupImportDto()).copy(lookupType=type,lookupCode=code.ifBlank{value.uppercase().replace(' ','_')},lookupValue=value,description=desc,displayOrder=order.toIntOrNull()?:0,active=current?.active?:true))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
@Composable private fun LookupDetail(v:LookupImportDto,p:PermissionContext,onClose:()->Unit,onSave:(LookupImportDto)->Unit,onToggle:()->Unit,onDelete:()->Unit){
    var editing by remember{mutableStateOf(false)}
    if(editing){LookupDialog(v.lookupType,v,"",{editing=false}){onSave(it)};return}
    DetailDialog(v.lookupValue,listOf("Code" to v.lookupCode,"Type" to v.lookupType,"Description" to v.description.orEmpty(),"Order" to v.displayOrder.toString(),"Status" to if(v.active)"ACTIVE" else "INACTIVE"),buildList{if(p.can("MASTERS","EDIT")){add("Edit" to {editing=true});add((if(v.active)"Deactivate" else "Activate") to onToggle)};if(p.can("MASTERS","DELETE"))add("Delete" to onDelete)},onClose)
}

@Composable private fun InventoryWorkspace(api:DseErpHttpClient,p:PermissionContext,username:String,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    var q by remember{mutableStateOf("")};var items by remember{mutableStateOf<List<MasterItem>>(emptyList())};var msg by remember{mutableStateOf("Loading inventory…")};var selected by remember{mutableStateOf<MasterItem?>(null)};var history by remember{mutableStateOf<List<StockHistoryRow>>(emptyList())};var adjust by remember{mutableStateOf(false)};var refresh by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope()
    LaunchedEffect(refresh){when(val r=api.listItems()){is ApiResult.Success->{items=r.value;msg="${r.value.size} items"};else->msg=r.readableMessage()}}
    LaunchedEffect(openTarget?.moduleKey,openTarget?.reference,openTarget?.recordId){val t=openTarget;if(t!=null&&t.moduleKey.uppercase() in setOf("ITEM","INVENTORY")&&(t.reference.isNotBlank()||t.recordId!=null)){q=t.reference;when(val r=api.searchItems(t.reference,50)){is ApiResult.Success->{selected=r.value.firstOrNull{it.itemCode.equals(t.reference,true)||(t.recordId!=null&&it.id?.toLong()==t.recordId)};if(selected==null)msg="Inventory item ${t.reference.ifBlank{t.recordId?.toString().orEmpty()}} was not found. Refresh Item Master and try again." else selected?.let{i->history=(api.stockHistory(i.itemCode) as? ApiResult.Success)?.value.orEmpty()}};else->msg=r.readableMessage()};onTargetConsumed()}}
    val shown=remember(items,q){items.filter{q.isBlank()||it.itemCode.contains(q,true)||it.description.contains(q,true)||it.location.orEmpty().contains(q,true)}}
    DseRegisterShell("Inventory","Stock balance and adjustment history",q,{q=it},msg,{refresh++},kpis={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseHeroKpi("Stock On Hand",items.sumOf{it.openingStock}.toString(),"${items.size} inventory item(s)",Icons.Rounded.Warehouse);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Low Stock",items.count{it.openingStock<=it.minimumStock}.toString(),Icons.Rounded.WarningAmber,Modifier.weight(1f),if(items.any{it.openingStock<=it.minimumStock})DseWarning else DseSuccess);DseMetricTile("Reserved",items.sumOf{it.reservedStock}.toString(),Icons.Rounded.Inventory,Modifier.weight(1f),DseInfo)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Available",items.sumOf{(it.openingStock-it.reservedStock).coerceAtLeast(0.0)}.toString(),Icons.Rounded.Inventory2,Modifier.weight(1f),DseSuccess);DseMetricTile("Matched",shown.size.toString(),Icons.Rounded.Search,Modifier.weight(1f),DsePurple)}}}){
        shown.forEach{i->
            fun openItem(){selected=i;scope.launch{history=(api.stockHistory(i.itemCode) as? ApiResult.Success)?.value.orEmpty()}}
            DseRecordCard(
                i.itemCode,i.description,"${i.openingStock} ${i.unit.orEmpty()}",listOf("Stock" to if(i.openingStock<=i.minimumStock)"LOW STOCK" else "AVAILABLE"),
                meta="Reserved ${i.reservedStock} • Min ${i.minimumStock} • ${i.location.orEmpty()}",
                swipeStartActions=listOf(SwipeAction("Open",Icons.Rounded.Visibility){openItem()}),
                swipeEndActions=if(p.can("INVENTORY","EDIT"))listOf(SwipeAction("Adjust",Icons.Rounded.Tune){openItem();adjust=true}) else emptyList(),
            ){openItem()}
        }
    }
    selected?.let{i->DetailDialog(i.itemCode,listOf("Description" to i.description,"Current Stock" to i.openingStock.toString(),"Reserved" to i.reservedStock.toString(),"Minimum" to i.minimumStock.toString(),"Location" to i.location.orEmpty(),"Recent History" to history.take(8).joinToString("\n"){h->"${h.date} • ${h.type} • ${h.quantity} • ${h.reference}"}),buildList{if(p.can("INVENTORY","EDIT"))add("Adjust Stock" to {adjust=true})},{selected=null})}
    if(adjust)StockAdjustmentDialog(selected,{adjust=false}){req->scope.launch{when(val r=api.adjustStock(req.copy(createdBy=username))){is ApiResult.Success->{msg="Stock adjusted";adjust=false;selected=null;refresh++};else->msg=r.readableMessage()}}}
}
@Composable private fun StockAdjustmentDialog(item:MasterItem?,onClose:()->Unit,onSave:(StockAdjustmentRequest)->Unit){var type by remember{mutableStateOf("ADD")};var qty by remember{mutableStateOf("")};var reason by remember{mutableStateOf("")};var ref by remember{mutableStateOf("")};PremiumAlertDialog(onDismissRequest=onClose,title={Text("Adjust ${item?.itemCode.orEmpty()}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseSelect("Adjustment Type",type,listOf("ADD","REMOVE","SET"),required=true,onValue={type=it});DseNumberField("Quantity",qty,min=0.000001,required=true,onValue={qty=it});DseField("Reason",reason,required=true,onValue={reason=it});DseField("Reference No",ref,singleLine=true,onValue={ref=it})}},confirmButton={Button(enabled=(qty.toDoubleOrNull()?:0.0)>0&&reason.isNotBlank(),onClick={onSave(StockAdjustmentRequest(itemCode=item?.itemCode.orEmpty(),type=type,quantity=qty.toDoubleOrNull()?:0.0,reason=reason,referenceNo=ref))}){Text("Apply")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}

@Composable private fun PurchaseReconWorkspace(api:DseErpHttpClient,p:PermissionContext,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    var page by remember{mutableIntStateOf(0)}
    var q by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("")}
    var data by remember{mutableStateOf<PurchaseReconPage?>(null)}
    var msg by remember{mutableStateOf("Loading…")}
    var selected by remember{mutableStateOf<PurchaseReconRecord?>(null)}
    var creating by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<PurchaseReconRecord?>(null)}
    var manageSuppliers by remember{mutableStateOf(false)}
    var refresh by remember{mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(openTarget?.moduleKey,openTarget?.recordId,openTarget?.reference){
        val t=openTarget
        if(t!=null&&t.moduleKey.uppercase() in setOf("PURCHASE_RECON","RECON")){
            val id=t.recordId?.takeIf{it in 1..Int.MAX_VALUE.toLong()}?.toInt()
            if(id!=null){when(val r=api.purchaseReconRecord(id)){is ApiResult.Success->{selected=r.value;onTargetConsumed()};else->{msg=r.readableMessage();onTargetConsumed()}}}
            else if(t.reference.isNotBlank()){q=t.reference;page=0;onTargetConsumed()}
        }
    }
    LaunchedEffect(page,q,status,refresh){when(val r=api.purchaseReconPage(page,25,q,status)){is ApiResult.Success->{data=r.value;msg="${r.value.totalRows} recon record(s)"};else->msg=r.readableMessage()}}
    DseRegisterShell("Purchase Reconciliation","Match supplier invoices and statement activity",q,{q=it;page=0},msg,{refresh++},if(p.can("PURCHASE_RECON","CREATE")){{creating=true}}else null,footer={data?.let{PagingControls(it.page,it.totalPages,it.totalRows){page=it}}}){
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            DseSelect("Status",status,listOf("","OPEN","PARTIAL","RECONCILED","NEEDS REVIEW"),onValue={status=it;page=0})
            if(p.can("RECON_SUPPLIER","VIEW"))OutlinedButton(onClick={manageSuppliers=true}){Icon(Icons.Rounded.Groups,null);Text("Recon Suppliers")}
        }
        data?.rows.orEmpty().forEach{r->DseRecordCard(r.reference,"${r.supplierName} • ${r.supplierInvoiceNo}",money(r.invoiceValue),listOf("Status" to r.status),meta="Linked ${money(r.linkedAmount)} • Balance ${money(r.balance)}"){scope.launch{selected=(api.purchaseReconRecord(r.id?:0) as? ApiResult.Success)?.value?:r}}}
    }
    selected?.let{r->
        DetailDialog(r.reference,listOf("Supplier" to r.supplierName,"GSTIN" to r.supplierGstin,"Invoice" to r.supplierInvoiceNo,"Date" to r.invoiceDate,"Taxable" to money(r.taxableValue),"CGST" to money(r.cgst),"SGST" to money(r.sgst),"IGST" to money(r.igst),"Other Adjustment" to money(r.otherAdjustment),"Invoice Value" to money(r.invoiceValue),"Linked" to money(r.linkedAmount),"Balance" to money(r.balance),"Tax Difference" to money(r.taxDifference),"Tax Review" to if(r.taxReviewRequired)"REQUIRED" else "OK","Notes" to r.notes,"Bank Links" to r.bankLinks.joinToString("\n"){"${it.bankTransactionDate} • ${it.bankReference} • ${money(it.allocatedAmount)}"}),buildList{
            if(p.can("PURCHASE_RECON","EDIT"))add("Edit" to {editing=r;selected=null})
            if(p.can("PURCHASE_RECON","DELETE")&&r.linkedAmount<=0.009)add("Delete" to {scope.launch{when(val x=api.deletePurchaseRecon(r.id?:0)){is ApiResult.Success->{selected=null;refresh++;msg="Purchase Recon deleted"};else->msg=x.readableMessage()}}})
        },{selected=null})
    }
    if(creating)PurchaseReconEditor(api,null,{creating=false}){req->scope.launch{when(val r=api.savePurchaseRecon(req)){is ApiResult.Success->{creating=false;selected=r.value;refresh++;msg="Purchase Recon ${r.value.reference} created"};else->msg=r.readableMessage()}}}
    editing?.let{old->PurchaseReconEditor(api,old,{editing=null}){req->scope.launch{when(val r=api.updatePurchaseRecon(old.id?:0,req)){is ApiResult.Success->{editing=null;selected=r.value;refresh++;msg="Purchase Recon updated"};else->msg=r.readableMessage()}}}}
    if(manageSuppliers)ReconSupplierManager(api,p,{manageSuppliers=false})
}

@Composable private fun PurchaseReconEditor(api:DseErpHttpClient,current:PurchaseReconRecord?,onClose:()->Unit,onSave:(PurchaseReconSave)->Unit){
    var suppliers by remember{mutableStateOf<List<PurchaseReconSupplier>>(emptyList())}
    var supplier by remember{mutableStateOf<PurchaseReconSupplier?>(null)}
    var invoice by remember{mutableStateOf(current?.supplierInvoiceNo.orEmpty())}
    var date by remember{mutableStateOf(current?.invoiceDate?.ifBlank{todayIso()}?:todayIso())}
    var taxable by remember{mutableStateOf((current?.taxableValue?:0.0).toString())}
    var cgst by remember{mutableStateOf((current?.cgst?:0.0).toString())}
    var sgst by remember{mutableStateOf((current?.sgst?:0.0).toString())}
    var igst by remember{mutableStateOf((current?.igst?:0.0).toString())}
    var other by remember{mutableStateOf((current?.otherAdjustment?:0.0).toString())}
    var total by remember{mutableStateOf((current?.invoiceValue?:0.0).toString())}
    var notes by remember{mutableStateOf(current?.notes.orEmpty())}
    var msg by remember{mutableStateOf("")}
    LaunchedEffect(Unit){when(val r=api.purchaseReconSuppliers("",100)){is ApiResult.Success->{suppliers=r.value;supplier=current?.supplierId?.let{id->r.value.firstOrNull{it.id==id}}};else->msg=r.readableMessage()}}
    val options=suppliers.map{"${it.reference} • ${it.legalName}"}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"New Purchase Recon" else "Edit ${current.reference}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        DseSelect("Recon Supplier",supplier?.let{"${it.reference} • ${it.legalName}"}.orEmpty(),options,required=true,onValue={v->supplier=suppliers.firstOrNull{"${it.reference} • ${it.legalName}"==v}})
        DseField("Supplier Invoice No.",invoice,singleLine=true,required=true,onValue={invoice=it});DseDateField("Invoice Date",date,required=true,onValue={date=it})
        DseNumberField("Taxable Value",taxable,min=0.0,onValue={taxable=it});DseNumberField("CGST",cgst,min=0.0,onValue={cgst=it});DseNumberField("SGST",sgst,min=0.0,onValue={sgst=it});DseNumberField("IGST",igst,min=0.0,onValue={igst=it});DseNumberField("Other Adjustment",other,min=0.0,onValue={other=it});DseNumberField("Invoice Value",total,min=0.01,required=true,onValue={total=it});DseField("Notes",notes,onValue={notes=it})
        if(current!=null&&current.linkedAmount>.009)Text("This record is linked to Bank Statement. The server will prevent material supplier/date/amount changes until the bank match is reversed.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)
        if(msg.isNotBlank())Text(msg,color=MaterialTheme.colorScheme.error)
    }},confirmButton={Button(enabled=supplier?.id!=null&&invoice.isNotBlank()&&(total.toDoubleOrNull()?:0.0)>0,onClick={onSave(PurchaseReconSave(id=current?.id,supplierId=supplier?.id,supplierInvoiceNo=invoice.trim(),invoiceDate=date,taxableValue=taxable.toDoubleOrNull()?:0.0,cgst=cgst.toDoubleOrNull()?:0.0,sgst=sgst.toDoubleOrNull()?:0.0,igst=igst.toDoubleOrNull()?:0.0,otherAdjustment=other.toDoubleOrNull()?:0.0,invoiceValue=total.toDoubleOrNull()?:0.0,notes=notes,rowVersion=current?.rowVersion?:0))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun ReconSupplierManager(api:DseErpHttpClient,p:PermissionContext,onClose:()->Unit){
    var q by remember{mutableStateOf("")};var rows by remember{mutableStateOf<List<PurchaseReconSupplier>>(emptyList())};var msg by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)};var edit by remember{mutableStateOf<PurchaseReconSupplier?>(null)};var creating by remember{mutableStateOf(false)};var deleting by remember{mutableStateOf<PurchaseReconSupplier?>(null)};val scope=rememberCoroutineScope()
    LaunchedEffect(q,refresh){when(val r=api.purchaseReconSuppliers(q,100)){is ApiResult.Success->{rows=r.value;msg="${rows.size} recon supplier(s)"};else->msg=r.readableMessage()}}
    PremiumAlertDialog(onDismissRequest=onClose,title={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Recon Suppliers");if(p.can("RECON_SUPPLIER","CREATE"))IconButton(onClick={creating=true}){Icon(Icons.Rounded.Add,"New supplier")}}},text={Column(Modifier.heightIn(max=600.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){DseField("Search",q,singleLine=true,onValue={q=it});Text(msg,style=MaterialTheme.typography.bodySmall);Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){rows.forEach{r->Surface(Modifier.fillMaxWidth().clickable{edit=r}){ListItem(headlineContent={Text("${r.reference} • ${r.legalName}")},supportingContent={Text("${r.gstin} • ${r.phone}")},trailingContent={DseStatus("",r.status)})}}}}},confirmButton={TextButton(onClick=onClose){Text("Close")}})
    if(creating)ReconSupplierEditor(null,{creating=false}){req->scope.launch{when(val r=api.createPurchaseReconSupplier(req)){is ApiResult.Success->{creating=false;refresh++;msg="Recon Supplier ${r.value.reference} created"};else->msg=r.readableMessage()}}}
    edit?.let{r->ReconSupplierDetail(r,p,{edit=null},{value->scope.launch{when(val x=api.updatePurchaseReconSupplier(r.id?:0,value)){is ApiResult.Success->{edit=null;refresh++;msg="Recon Supplier updated"};else->msg=x.readableMessage()}}},{deleting=r})}
    deleting?.let{r->ConfirmDialog("Delete ${r.reference}","Recon Suppliers already used by Purchase Recon records cannot be deleted.","Delete",true,{deleting=null}){scope.launch{when(val x=api.deletePurchaseReconSupplier(r.id?:0)){is ApiResult.Success->{deleting=null;edit=null;refresh++;msg="Recon Supplier deleted"};else->{deleting=null;msg=x.readableMessage()}}}}}
}
@Composable private fun ReconSupplierEditor(current:PurchaseReconSupplier?,onClose:()->Unit,onSave:(PurchaseReconSupplierSave)->Unit){
    var name by remember{mutableStateOf(current?.legalName.orEmpty())};var gstin by remember{mutableStateOf(current?.gstin.orEmpty())};var pan by remember{mutableStateOf(current?.pan.orEmpty())};var contact by remember{mutableStateOf(current?.contactPerson.orEmpty())};var phone by remember{mutableStateOf(current?.phone.orEmpty())};var email by remember{mutableStateOf(current?.email.orEmpty())};var notes by remember{mutableStateOf(current?.notes.orEmpty())};var status by remember{mutableStateOf(current?.status?.ifBlank{"ACTIVE"}?:"ACTIVE")}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"New Recon Supplier" else "Edit ${current.reference}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Legal Name",name,required=true,onValue={name=it});DseField("GSTIN",gstin,singleLine=true,onValue={gstin=it.uppercase()});DseField("PAN",pan,singleLine=true,onValue={pan=it.uppercase()});DseField("Contact Person",contact,onValue={contact=it});DseField("Phone",phone,singleLine=true,onValue={phone=it});DseField("Email",email,singleLine=true,onValue={email=it});DseField("Notes",notes,onValue={notes=it});DseSelect("Status",status,listOf("ACTIVE","INACTIVE"),onValue={status=it})}},confirmButton={Button(enabled=name.isNotBlank(),onClick={onSave(PurchaseReconSupplierSave(id=current?.id,legalName=name.trim(),gstin=gstin.trim(),pan=pan.trim(),contactPerson=contact.trim(),phone=phone.trim(),email=email.trim(),notes=notes.trim(),status=status,rowVersion=current?.rowVersion?:0))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
@Composable private fun ReconSupplierDetail(r:PurchaseReconSupplier,p:PermissionContext,onClose:()->Unit,onSave:(PurchaseReconSupplierSave)->Unit,onDelete:()->Unit){
    var editing by remember{mutableStateOf(false)}
    if(editing){ReconSupplierEditor(r,{editing=false},onSave);return}
    DetailDialog("${r.reference} • ${r.legalName}",listOf("GSTIN" to r.gstin,"PAN" to r.pan,"Contact" to r.contactPerson,"Phone" to r.phone,"Email" to r.email,"Notes" to r.notes,"Status" to r.status,"Recon Records" to r.reconCount.toString()),buildList{if(p.can("RECON_SUPPLIER","EDIT"))add("Edit" to {editing=true});if(p.can("RECON_SUPPLIER","DELETE")&&r.reconCount==0L)add("Delete" to onDelete)},onClose)
}

@Composable private fun CommunicationWorkspace(api:DseErpHttpClient,p:PermissionContext){
    var rows by remember{mutableStateOf<List<CommunicationRow>>(emptyList())}
    var msg by remember{mutableStateOf("Loading…")}
    var refresh by remember{mutableIntStateOf(0)}
    var q by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<CommunicationRow?>(null)}
    LaunchedEffect(refresh){
        when(val r=api.communications()){
            is ApiResult.Success->{rows=r.value;msg="${rows.size} communication record(s)"}
            else->msg=r.readableMessage()
        }
    }
    val shown=rows.filter{q.isBlank()||listOf(it.documentLabel,it.entityType,it.channel,it.recipient,it.subject,it.status).any{x->x.contains(q,true)}}
    DseRegisterShell(
        title="Communication Center",
        subtitle="Email, WhatsApp and document communication history",
        query=q,
        onQuery={q=it},
        message=msg,
        onRefresh={refresh++},
    ){
        shown.forEach{r->
            DseRecordCard(
                title=r.documentLabel.ifBlank{"${r.entityType} #${r.entityId}"},
                subtitle="${r.channel} • ${r.recipient}",
                amount="",
                statuses=listOf("Status" to r.status),
                meta=listOf(r.subject,r.errorMessage).filter{it.isNotBlank()}.joinToString(" • "),
            ){selected=r}
        }
    }
    selected?.let{r->
        DetailDialog(
            r.documentLabel.ifBlank{"Communication"},
            listOf("Channel" to r.channel,"Recipient" to r.recipient,"Subject" to r.subject,"Status" to r.status,"Message" to r.errorMessage),
            emptyList(),
        ){selected=null}
    }
}

@Composable private fun ReminderWorkspace(api:DseErpHttpClient,p:PermissionContext,username:String,openTarget:RecordTarget?=null,onTargetConsumed:()->Unit={}){
    var rows by remember{mutableStateOf<List<ReminderRecord>>(emptyList())}
    var msg by remember{mutableStateOf("Loading…")}
    var refresh by remember{mutableIntStateOf(0)}
    var edit by remember{mutableStateOf<ReminderRecord?>(null)}
    var creating by remember{mutableStateOf(false)}
    var q by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(refresh){when(val r=api.reminders()){is ApiResult.Success->{rows=r.value;msg="${rows.size} reminder(s)"};else->msg=r.readableMessage()}}
    LaunchedEffect(openTarget?.moduleKey,openTarget?.recordId,openTarget?.reference){val t=openTarget;if(t!=null&&t.moduleKey.uppercase()=="REMINDER"){edit=rows.firstOrNull{it.id==t.recordId||it.referenceNo.equals(t.reference,true)};if(edit==null&&t.reference.isNotBlank())msg="Reminder ${t.reference} was not found in the current list";onTargetConsumed()}}
    val shown=rows.filter{q.isBlank()||listOf(it.title,it.referenceNo,it.notes,it.status,it.priority).any{x->x.contains(q,true)}}
    DseRegisterShell(
        title="Reminder Center",
        subtitle="Follow-ups, due actions and priority work",
        query=q,
        onQuery={q=it},
        message=msg,
        onRefresh={refresh++},
        onAdd=if(p.can("REMINDERS","CREATE")){{creating=true}}else null,
    ){
        shown.forEach{r->
            DseRecordCard(
                title=r.title,
                subtitle=r.referenceNo.ifBlank{"Business reminder"},
                amount=r.dueDate,
                statuses=listOf("Status" to r.status,"Priority" to r.priority),
                meta=r.notes,
            ){edit=r}
        }
    }
    if(creating)ReminderDialog(null,username,{creating=false}){value->scope.launch{when(val r=api.createReminder(value)){is ApiResult.Success->{creating=false;refresh++};else->msg=r.readableMessage()}}}
    edit?.let{r->ReminderActions(r,p,{edit=null},{scope.launch{when(val x=api.setReminderStatus(r.id?:0,it.first,it.second)){is ApiResult.Success->{edit=null;refresh++};else->msg=x.readableMessage()}}},{value->scope.launch{when(val x=api.updateReminder(r.id?:0,value)){is ApiResult.Success->{edit=null;refresh++};else->msg=x.readableMessage()}}},{scope.launch{when(val x=api.deleteReminder(r.id?:0)){is ApiResult.Success->{edit=null;refresh++};else->msg=x.readableMessage()}}})}
}
@Composable private fun ReminderDialog(current:ReminderRecord?,username:String,onClose:()->Unit,onSave:(ReminderRecord)->Unit){var title by remember{mutableStateOf(current?.title.orEmpty())};var ref by remember{mutableStateOf(current?.referenceNo.orEmpty())};var due by remember{mutableStateOf(current?.dueDate?.ifBlank{todayIso()}?:todayIso())};var priority by remember{mutableStateOf(current?.priority?.ifBlank{"MEDIUM"}?:"MEDIUM")};var notes by remember{mutableStateOf(current?.notes.orEmpty())};PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"New Reminder" else "Edit Reminder")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Title",title,required=true,onValue={title=it});DseField("Reference",ref,singleLine=true,onValue={ref=it});DseDateField("Due Date",due,required=true,onValue={due=it});DseSelect("Priority",priority,listOf("LOW","MEDIUM","HIGH","URGENT"),onValue={priority=it});DseField("Notes",notes,onValue={notes=it})}},confirmButton={Button(enabled=title.isNotBlank(),onClick={onSave((current?:ReminderRecord()).copy(title=title,referenceNo=ref,dueDate=due,priority=priority,notes=notes,createdBy=current?.createdBy?.ifBlank{username}?:username))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}
@Composable private fun ReminderActions(r:ReminderRecord,p:PermissionContext,onClose:()->Unit,onStatus:(Pair<String,String?>)->Unit,onUpdate:(ReminderRecord)->Unit,onDelete:()->Unit){var editing by remember{mutableStateOf(false)};var snooze by remember{mutableStateOf(false)};if(editing){ReminderDialog(r,r.createdBy,{editing=false},onUpdate);return};if(snooze){var date by remember{mutableStateOf(addDaysIso(todayIso(),1))};PremiumAlertDialog(onDismissRequest={snooze=false},title={Text("Snooze Reminder")},text={DseDateField("Snoozed Until",date,onValue={date=it})},confirmButton={Button(onClick={onStatus("SNOOZED" to date)}){Text("Snooze")}},dismissButton={TextButton(onClick={snooze=false}){Text("Cancel")}});return};DetailDialog(r.title,listOf("Reference" to r.referenceNo,"Due" to r.dueDate,"Priority" to r.priority,"Status" to r.status,"Notes" to r.notes),buildList{if(p.can("REMINDERS","EDIT"))add("Edit" to {editing=true});if(p.can("REMINDERS","COMPLETE")){add("Complete" to {onStatus("COMPLETED" to null)});add("Reopen" to {onStatus("OPEN" to null)})};if(p.can("REMINDERS","SNOOZE"))add("Snooze" to {snooze=true});if(p.can("REMINDERS","DELETE"))add("Delete" to onDelete)},onClose)}

@Composable private fun NotificationWorkspace(api:DseErpHttpClient,p:PermissionContext,onDeepLink:(String)->Unit){
    var rows by remember{mutableStateOf<List<InsightNotification>>(emptyList())}
    var msg by remember{mutableStateOf("Loading…")}
    var refresh by remember{mutableIntStateOf(0)}
    var q by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(refresh){when(val r=api.notifications(200)){is ApiResult.Success->{rows=r.value;msg="${rows.count{!it.read}} unread • ${rows.size} total"};else->msg=r.readableMessage()}}
    val shown=rows.filter{q.isBlank()||listOf(it.title,it.message,it.referenceNo.orEmpty(),it.moduleKey.orEmpty()).any{x->x.contains(q,true)}}
    DseRegisterShell(
        title="Notification Center",
        subtitle="Business alerts, approvals and record activity",
        query=q,
        onQuery={q=it},
        message=msg,
        onRefresh={refresh++},
        kpis={
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(p.can("COMMUNICATION","EDIT"))PremiumSecondaryButton("Mark all read",{scope.launch{api.markAllNotificationsRead();refresh++}},Modifier.weight(1f),icon=Icons.Rounded.Done)
                if(p.can("COMMUNICATION","DELETE"))PremiumSecondaryButton("Clear",{scope.launch{api.clearNotifications();refresh++}},Modifier.weight(1f),icon=Icons.Rounded.DeleteSweep)
            }
        },
    ){
        shown.forEach{r->
            DseRecordCard(
                title=r.title,
                subtitle=r.message,
                amount="",
                statuses=listOf("Status" to if(r.read)"READ" else "UNREAD"),
                meta=r.referenceNo.orEmpty(),
            ){
                scope.launch{
                    if(!r.read)api.markNotificationRead(r.id)
                    if(!r.moduleKey.isNullOrBlank()||!r.targetFxml.isNullOrBlank()){
                        val key=(r.moduleKey?:r.targetFxml.orEmpty()).lowercase().replace('_','-')
                        onDeepLink("dseerp://$key/${r.referenceNo.orEmpty()}")
                    } else refresh++
                }
            }
        }
    }
}


@Composable private fun ReportsWorkspace(api:DseErpHttpClient){
    var from by remember{mutableStateOf(addDaysIso(todayIso(),-30))}
    var to by remember{mutableStateOf(todayIso())}
    var type by remember{mutableStateOf("All Reports")}
    var party by remember{mutableStateOf("")}
    var item by remember{mutableStateOf("")}
    var salesperson by remember{mutableStateOf("")}
    var filters by remember{mutableStateOf(ReportFilters())}
    var data by remember{mutableStateOf<ReportBundle?>(null)}
    var msg by remember{mutableStateOf("Choose filters and run report")}
    var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){(api.reportFilters() as? ApiResult.Success)?.value?.let{filters=it}}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        PremiumCard(Modifier.fillMaxWidth(),padding=15.dp){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                PremiumIconTile(Icons.Rounded.Assessment,DsePurple,size=46.dp)
                Column(Modifier.weight(1f)){Text("Reports & Insights",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold);Text("Live business intelligence from your UAT workspace",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            DseSelect("Report Type",type,listOf("All Reports","Sales","Purchase","Profit","Receivables","Payables","Inventory","Customers","Items"),onValue={type=it})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseDateField("From",from,Modifier.weight(1f),onValue={from=it});DseDateField("To",to,Modifier.weight(1f),onValue={to=it})}
            DseSelect("Party",party,listOf("")+filters.parties,onValue={party=it})
            DseSelect("Item",item,listOf("")+filters.items,onValue={item=it})
            DseSelect("Salesperson",salesperson,listOf("")+filters.salespeople,onValue={salesperson=it})
            PremiumPrimaryButton(
                text=if(busy)"Loading insights…" else "Run Report",
                onClick={scope.launch{busy=true;when(val r=api.reports(from,to,type,party,item,salesperson)){is ApiResult.Success->{data=r.value;msg="Report loaded"};else->msg=r.readableMessage()};busy=false}},
                modifier=Modifier.fillMaxWidth(),
                enabled=!busy,
                leadingIcon=Icons.Rounded.AutoGraph,
                trailingIcon=Icons.Rounded.ArrowForward,
            )
        }
        data?.let{r->
            DseHeroKpi("Profit",money(r.profit),"$type • $from to $to",Icons.Rounded.TrendingUp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Sales",compactMoney(r.sales),Icons.Rounded.ReceiptLong,Modifier.weight(1f),DsePurple);DseMetricTile("Purchases",compactMoney(r.purchase),Icons.Rounded.ShoppingCart,Modifier.weight(1f),DseWarning)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Receivables",compactMoney(r.receivables),Icons.Rounded.CreditCard,Modifier.weight(1f),DseDanger);DseMetricTile("Payables",compactMoney(r.payables),Icons.Rounded.AccountBalanceWallet,Modifier.weight(1f),DseInfo)}
            DseSection("Recent Sales",Icons.Rounded.ReceiptLong){r.salesRows.take(20).forEach{x->DseRecordCard(x.number,"${x.date} • ${x.party}",money(x.amount),listOf("Status" to x.status)){}}}
            DseSection("Recent Purchases",Icons.Rounded.ShoppingCart){r.purchaseRows.take(20).forEach{x->DseRecordCard(x.number,"${x.date} • ${x.party}",money(x.amount),listOf("Status" to x.status)){}}}
            DseSection("Top Customers",Icons.Rounded.Groups){r.customerPoints.take(10).forEach{x->Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(x.label,Modifier.weight(1f));Text(money(x.value),fontWeight=FontWeight.Bold)}}}
            DseSection("Top Items",Icons.Rounded.Inventory2){r.itemPoints.take(10).forEach{x->Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(x.label,Modifier.weight(1f));Text(money(x.value),fontWeight=FontWeight.Bold)}}}
            PremiumSecondaryButton("Share / Print Report",{platformShareText("Jasvi Industries Report",reportShareText(from,to,type,r))},Modifier.fillMaxWidth(),icon=Icons.Rounded.Share)
        }
        if(msg.isNotBlank())Text(msg,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
    }
}

private fun reportShareText(from:String,to:String,type:String,r:ReportBundle)=buildString{appendLine("Jasvi Industries • $type");appendLine("Period: $from to $to");appendLine("Sales: ${money(r.sales)}");appendLine("Purchases: ${money(r.purchase)}");appendLine("Profit: ${money(r.profit)}");appendLine("Receivables: ${money(r.receivables)}");appendLine("Payables: ${money(r.payables)}");if(r.customerPoints.isNotEmpty()){appendLine("Top Customers:");r.customerPoints.take(10).forEach{appendLine("- ${it.label}: ${money(it.value)}")}};if(r.itemPoints.isNotEmpty()){appendLine("Top Items:");r.itemPoints.take(10).forEach{appendLine("- ${it.label}: ${money(it.value)}")}}}


@Composable private fun ProfileWorkspace(api:DseErpHttpClient,p:PermissionContext){
    var profile by remember{mutableStateOf<UserProfile?>(null)}
    var msg by remember{mutableStateOf("Loading profile…")}
    var edit by remember{mutableStateOf(false)}
    var password by remember{mutableStateOf(false)}
    var refresh by remember{mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(refresh){when(val r=api.currentProfile()){is ApiResult.Success->{profile=r.value;msg=""};else->msg=r.readableMessage()}}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        profile?.let{u->
            PremiumCard(Modifier.fillMaxWidth(),padding=16.dp){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Box(Modifier.size(58.dp).background(DseBrandGradient,androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center){Text(u.fullName.orEmpty().ifBlank{u.username}.take(2).uppercase(),color=androidx.compose.ui.graphics.Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge)}
                    Column(Modifier.weight(1f)){Text(u.fullName.orEmpty().ifBlank{u.username},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold);Text("${u.role} • ${u.department.orEmpty().ifBlank{"Jasvi Industries"}}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    DseStatus("",if(u.mfaEnabled)"MFA ON" else "MFA OFF")
                }
                Text("Profile & Security",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
                Text("Identity, access and device security in one place.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DseSection("Account",Icons.Rounded.AccountCircle){
                detailRows(listOf("Username" to u.username,"Full Name" to u.fullName.orEmpty(),"Email" to u.email.orEmpty(),"Role" to u.role,"Department" to u.department.orEmpty(),"Branch" to u.branch.orEmpty(),"Access Level" to u.accessLevel.orEmpty(),"MFA" to if(u.mfaEnabled)"ENABLED" else "DISABLED"))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                PremiumPrimaryButton("Edit Profile",{edit=true},Modifier.weight(1f),leadingIcon=Icons.Rounded.Edit)
                PremiumSecondaryButton("Password",{password=true},Modifier.weight(1f),icon=Icons.Rounded.Lock)
            }
            PremiumCard(Modifier.fillMaxWidth(),padding=13.dp){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){PremiumIconTile(Icons.Rounded.Fingerprint,DseSuccess,size=42.dp);Column(Modifier.weight(1f)){Text(platformBiometricUnlockLabel(),fontWeight=FontWeight.Bold);Text("Native ${platformDeviceClassLabel()} secure unlock is supported after sign-in.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            }
        }
        if(msg.isNotBlank())Text(msg,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(edit)profile?.let{u->ProfileEditDialog(u,{edit=false}){req->scope.launch{when(val r=api.updateProfile(req)){is ApiResult.Success->{profile=r.value;edit=false;msg="Profile updated"};else->msg=r.readableMessage()}}}}
    if(password)profile?.let{u->ChangePasswordDialog(u.id,{password=false}){req->scope.launch{when(val r=api.changePassword(req)){is ApiResult.Success->{password=false;msg="Password changed successfully"};else->msg=r.readableMessage()}}}}
}
@Composable private fun ProfileEditDialog(u:UserProfile,onClose:()->Unit,onSave:(ProfileUpdate)->Unit){var full by remember{mutableStateOf(u.fullName.orEmpty())};var email by remember{mutableStateOf(u.email.orEmpty())};var dept by remember{mutableStateOf(u.department.orEmpty())};var branch by remember{mutableStateOf(u.branch.orEmpty())};PremiumAlertDialog(onDismissRequest=onClose,title={Text("Edit Profile")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DseField("Full Name",full,required=true,onValue={full=it});DseField("Email",email,onValue={email=it});DseField("Department",dept,onValue={dept=it});DseField("Branch",branch,onValue={branch=it})}},confirmButton={PremiumPrimaryButton("Save Profile",{onSave(ProfileUpdate(full,email,dept,branch))},enabled=full.isNotBlank(),leadingIcon=Icons.Rounded.Save)},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}
@Composable private fun ChangePasswordDialog(userId:Int,onClose:()->Unit,onSave:(ChangePasswordRequest)->Unit){var old by remember{mutableStateOf("")};var new by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};PremiumAlertDialog(onDismissRequest=onClose,title={Text("Change Password")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DsePasswordField("Current Password",old,required=true,onValue={old=it});DsePasswordField("New Password",new,required=true,onValue={new=it});DsePasswordField("Confirm Password",confirm,required=true,onValue={confirm=it});if(new.isNotBlank()&&new!=confirm)Text("Passwords do not match",color=MaterialTheme.colorScheme.error)}},confirmButton={PremiumPrimaryButton("Change Password",{onSave(ChangePasswordRequest(userId,old,new))},enabled=old.isNotBlank()&&new.length>=8&&new==confirm,leadingIcon=Icons.Rounded.Lock)},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}

@Composable private fun AdminWorkspace(api:DseErpHttpClient,p:PermissionContext){
    if(!p.isAdmin()&&!p.can("USERS")){PermissionDeniedCard("User Access & Roles"){};return}
    var mode by remember{mutableIntStateOf(0)}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top=10.dp)){
        PremiumCard(Modifier.fillMaxWidth().padding(horizontal=14.dp),padding=14.dp){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){
                PremiumIconTile(Icons.Rounded.AdminPanelSettings,MaterialTheme.colorScheme.primary,size=46.dp)
                Column(Modifier.weight(1f)){
                    Text("Access & Security",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
                    Text("Users, roles and permissions for Jasvi Industries",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DseStatus("","SECURE")
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(Modifier.padding(horizontal=14.dp).fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=4.dp){
            TabRow(mode,containerColor=androidx.compose.ui.graphics.Color.Transparent,divider={}){listOf("Users","Roles","Permissions").forEachIndexed{i,t->Tab(selected=mode==i,onClick={mode=i},text={Text(t,fontWeight=if(mode==i)FontWeight.Bold else FontWeight.Medium)})}}
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f)){when(mode){0->AdminUsers(api,p);1->AdminRoles(api,p);else->AdminPermissions(api,p)}}
    }
}

@Composable private fun AdminUsers(api:DseErpHttpClient,p:PermissionContext){
    var rows by remember{mutableStateOf<List<AdminUser>>(emptyList())}
    var roleOptions by remember{mutableStateOf<List<String>>(emptyList())}
    var msg by remember{mutableStateOf("Loading users…")}
    var refresh by remember{mutableIntStateOf(0)}
    var query by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<AdminUser?>(null)}
    var editing by remember{mutableStateOf<AdminUser?>(null)}
    var creating by remember{mutableStateOf(false)}
    var reset by remember{mutableStateOf<AdminUser?>(null)}
    val scope=rememberCoroutineScope()
    val canCreate=p.isAdmin()||p.can("USERS","CREATE")
    LaunchedEffect(refresh){
        when(val r=api.adminUsers()){is ApiResult.Success->{rows=r.value;msg="${rows.size} users • role-based access"};else->msg=r.readableMessage()}
        (api.adminRoles() as? ApiResult.Success)?.value?.let{roleOptions=it.map{role->role.code}}
    }
    val filtered=rows.filter{u->query.isBlank()||listOf(u.username,u.fullName.orEmpty(),u.email.orEmpty(),u.role,u.department.orEmpty()).any{it.contains(query,true)}}
    DseRegisterShell("Users","Accounts, MFA and workspace access",query,{query=it},msg,{refresh++},if(canCreate){{creating=true}}else null){
        filtered.forEach{u->
            DseRecordCard(
                title=u.fullName.orEmpty().ifBlank{u.username},
                subtitle="${u.username} • ${u.email.orEmpty().ifBlank{"No email"}}",
                amount=u.role,
                statuses=listOf("" to if(!u.active)"INACTIVE" else if(u.locked)"LOCKED" else "ACTIVE","MFA" to if(u.mfaEnabled)"ON" else "OFF"),
                meta=listOf(u.department.orEmpty(),u.branch.orEmpty()).filter{it.isNotBlank()}.joinToString(" • "),
                onClick={selected=u}
            )
        }
        if(filtered.isEmpty())PremiumCard(Modifier.fillMaxWidth(),padding=14.dp){Text("No users match your search.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    selected?.let{u->
        val actions=buildList<Pair<String,()->Unit>>{
            if(p.isAdmin()||p.can("USERS","EDIT")){
                add("Edit" to {editing=u;selected=null})
                add((if(u.locked)"Unlock" else "Lock") to {scope.launch{when(val r=api.setAdminUserLocked(u.id,!u.locked)){is ApiResult.Success->{msg=r.value.message;selected=null;refresh++};else->msg=r.readableMessage()}}})
                add("Reset Password" to {reset=u;selected=null})
            }
            if(p.isAdmin()||p.can("USERS","DELETE"))add("Delete" to {scope.launch{when(val r=api.deleteAdminUser(u.id)){is ApiResult.Success->{msg=r.value.message;selected=null;refresh++};else->msg=r.readableMessage()}}})
        }
        DetailDialog(u.username,listOf("Name" to u.fullName.orEmpty(),"Email" to u.email.orEmpty(),"Role" to u.role,"Department" to u.department.orEmpty(),"Branch" to u.branch.orEmpty(),"Access" to u.accessLevel.orEmpty(),"MFA" to if(u.mfaEnabled)"ENABLED" else "DISABLED"),actions,{selected=null})
    }
    if(creating)AdminUserDialog(null,roleOptions,{creating=false}){req->scope.launch{when(val r=api.saveAdminUser(req)){is ApiResult.Success->{creating=false;msg="User ${r.value.username} created";refresh++};else->msg=r.readableMessage()}}}
    editing?.let{u->AdminUserDialog(u,roleOptions,{editing=null}){req->scope.launch{when(val r=api.updateAdminUser(u.id,req)){is ApiResult.Success->{editing=null;msg="User ${r.value.username} updated";refresh++};else->msg=r.readableMessage()}}}}
    reset?.let{u->AdminResetPasswordDialog(u,{reset=null}){password->scope.launch{when(val r=api.resetAdminPassword(u.id,password)){is ApiResult.Success->{reset=null;msg=r.value.message};else->msg=r.readableMessage()}}}}
}

@Composable private fun AdminUserDialog(current:AdminUser?,knownRoles:List<String>,onClose:()->Unit,onSave:(AdminUserSaveRequest)->Unit){
    var username by remember{mutableStateOf(current?.username.orEmpty())};var password by remember{mutableStateOf("")};var full by remember{mutableStateOf(current?.fullName.orEmpty())};var email by remember{mutableStateOf(current?.email.orEmpty())};var role by remember{mutableStateOf(current?.role.orEmpty())};var dept by remember{mutableStateOf(current?.department.orEmpty())};var access by remember{mutableStateOf(current?.accessLevel.orEmpty())};var branch by remember{mutableStateOf(current?.branch.orEmpty())};var active by remember{mutableStateOf(current?.active?:true)};var locked by remember{mutableStateOf(current?.locked?:false)};var mfa by remember{mutableStateOf(current?.mfaEnabled?:false)}
    val roles=(knownRoles+listOf("ADMIN","USER")).filter{it.isNotBlank()}.distinct()
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"Add User" else "Edit ${current.username}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
        DseField("Username",username,singleLine=true,readOnly=current!=null,required=true,onValue={username=it});if(current==null)DsePasswordField("Initial Password",password,required=true,onValue={password=it});DseField("Full Name",full,required=true,onValue={full=it});DseField("Email",email,singleLine=true,onValue={email=it});DseSelect("Role",role,roles,required=true,onValue={role=it});DseField("Department",dept,onValue={dept=it});DseField("Access Level",access,onValue={access=it});DseField("Branch",branch,onValue={branch=it});Row(verticalAlignment=Alignment.CenterVertically){Switch(active,{active=it});PremiumOptionLabel("Active",accent=DseSuccess)};Row(verticalAlignment=Alignment.CenterVertically){Switch(locked,{locked=it});PremiumOptionLabel("Locked",accent=DseDanger)};Row(verticalAlignment=Alignment.CenterVertically){Switch(mfa,{mfa=it});PremiumOptionLabel("MFA enabled",accent=DseInfo)}
    }},confirmButton={PremiumPrimaryButton("Save User",{onSave(AdminUserSaveRequest(current?.id,username,password,full,email,role,dept,access,branch,active,locked,mfa))},enabled=username.isNotBlank()&&full.isNotBlank()&&role.isNotBlank()&&(current!=null||password.length>=8),leadingIcon=Icons.Rounded.Save)},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun AdminResetPasswordDialog(user:AdminUser,onClose:()->Unit,onSave:(String)->Unit){
    var password by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Reset ${user.username} Password")},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){DsePasswordField("New Password",password,required=true,onValue={password=it});DsePasswordField("Confirm Password",confirm,required=true,onValue={confirm=it});if(password.isNotBlank()&&password!=confirm)Text("Passwords do not match",color=MaterialTheme.colorScheme.error)}},confirmButton={PremiumPrimaryButton("Reset Password",{onSave(password)},enabled=password.length>=8&&password==confirm,leadingIcon=Icons.Rounded.Lock)},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun AdminRoles(api:DseErpHttpClient,p:PermissionContext){
    var rows by remember{mutableStateOf<List<AdminRole>>(emptyList())}
    var msg by remember{mutableStateOf("Loading roles…")}
    var refresh by remember{mutableIntStateOf(0)}
    var query by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<AdminRole?>(null)}
    var editing by remember{mutableStateOf<AdminRole?>(null)}
    var creating by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(refresh){when(val r=api.adminRoles()){is ApiResult.Success->{rows=r.value;msg="${rows.size} roles • permission profiles"};else->msg=r.readableMessage()}}
    val filtered=rows.filter{r->query.isBlank()||r.code.contains(query,true)||r.displayName.contains(query,true)||r.description.orEmpty().contains(query,true)}
    DseRegisterShell("Roles","Permission profiles and user groups",query,{query=it},msg,{refresh++},if(p.isAdmin()){{creating=true}}else null){
        filtered.forEach{r->DseRecordCard(r.displayName.ifBlank{r.code},r.description.orEmpty().ifBlank{"No description"},"${r.userCount} users",listOf("" to if(r.active)"ACTIVE" else "INACTIVE"),meta=r.code,onClick={selected=r})}
        if(filtered.isEmpty())PremiumCard(Modifier.fillMaxWidth(),padding=14.dp){Text("No roles match your search.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    selected?.let{r->
        val actions=buildList<Pair<String,()->Unit>>{if(p.isAdmin()){add("Edit" to {editing=r;selected=null});if(r.userCount==0L)add("Delete" to {scope.launch{when(val x=api.deleteAdminRole(r.id)){is ApiResult.Success->{msg=x.value.message;selected=null;refresh++};else->msg=x.readableMessage()}}})}}
        DetailDialog(r.displayName.ifBlank{r.code},listOf("Code" to r.code,"Description" to r.description.orEmpty(),"Users" to r.userCount.toString(),"Status" to if(r.active)"ACTIVE" else "INACTIVE"),actions,{selected=null})
    }
    if(creating)AdminRoleDialog(null,{creating=false}){req->scope.launch{when(val r=api.saveAdminRole(req)){is ApiResult.Success->{creating=false;msg="Role ${r.value.displayName.ifBlank{r.value.code}} created";refresh++};else->msg=r.readableMessage()}}}
    editing?.let{r->AdminRoleDialog(r,{editing=null}){req->scope.launch{when(val x=api.updateAdminRole(r.id,req)){is ApiResult.Success->{editing=null;msg="Role updated";refresh++};else->msg=x.readableMessage()}}}}
}

@Composable private fun AdminRoleDialog(current:AdminRole?,onClose:()->Unit,onSave:(AdminRoleSaveRequest)->Unit){
    var name by remember{mutableStateOf(current?.displayName?.ifBlank{current.code}.orEmpty())};var desc by remember{mutableStateOf(current?.description.orEmpty())};var active by remember{mutableStateOf(current?.active?:true)}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text(if(current==null)"Add Role" else "Edit Role")},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){DseField("Role Name",name,required=true,onValue={name=it});DseField("Description",desc,onValue={desc=it});Row(verticalAlignment=Alignment.CenterVertically){Switch(active,{active=it});PremiumOptionLabel("Active",accent=DseSuccess)}}},confirmButton={PremiumPrimaryButton("Save Role",{onSave(AdminRoleSaveRequest(current?.id,name.trim(),desc.trim(),active))},enabled=name.isNotBlank(),leadingIcon=Icons.Rounded.Save)},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun AdminPermissions(api:DseErpHttpClient,p:PermissionContext){
    var roles by remember{mutableStateOf<List<AdminRole>>(emptyList())};var role by remember{mutableStateOf("")};var rows by remember{mutableStateOf<List<AdminPermission>>(emptyList())};var msg by remember{mutableStateOf("")};var refresh by remember{mutableIntStateOf(0)}
    val scope=rememberCoroutineScope();val canSave=p.isAdmin()||p.can("USERS","MANAGE_PERMISSIONS")
    LaunchedEffect(Unit){(api.adminRoles() as? ApiResult.Success)?.value?.let{roles=it;role=it.firstOrNull()?.code.orEmpty()}}
    LaunchedEffect(role,refresh){if(role.isNotBlank())when(val r=api.adminPermissions(role)){is ApiResult.Success->{rows=r.value;msg="${rows.count{it.allowed}} of ${rows.size} permissions enabled"};else->msg=r.readableMessage()}}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        PremiumCard(Modifier.fillMaxWidth(),padding=14.dp){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){PremiumIconTile(Icons.Rounded.Security,MaterialTheme.colorScheme.primary,size=44.dp);Column(Modifier.weight(1f)){Text("Role Permissions",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold);Text("Fine-grained module and action access",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            DseSelect("Role",role,roles.map{it.code},required=true,onValue={role=it})
            if(msg.isNotBlank())Text(msg,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            rows.forEachIndexed{i,r->
                PremiumCard(Modifier.fillMaxWidth(),padding=11.dp){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Checkbox(r.allowed,{checked->if(canSave)rows=rows.toMutableList().also{it[i]=r.copy(allowed=checked)}},enabled=canSave)
                        Column(Modifier.weight(1f)){Text("${r.module}.${r.action}",fontWeight=FontWeight.Bold);Text(r.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        Icon(if(r.allowed)Icons.Rounded.VerifiedUser else Icons.Rounded.Shield, null, tint=if(r.allowed)DseSuccess else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if(canSave)PremiumPrimaryButton("Save Permissions",{scope.launch{when(val r=api.saveAdminPermissions(AdminPermissionSaveRequest(role,rows.map{AdminPermissionSave(it.id,it.allowed)}))){is ApiResult.Success->{msg="Permissions saved";refresh++};else->msg=r.readableMessage()}}},Modifier.fillMaxWidth(),enabled=role.isNotBlank(),leadingIcon=Icons.Rounded.Save)
    }
}


@Composable private fun AboutWorkspace(){
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.fillMaxWidth().background(DseHeroGradient,androidx.compose.foundation.shape.RoundedCornerShape(28.dp))){
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){
                DseBrandMark(compact=false,dark=true)
                Column(Modifier.weight(1f)){Text("Jasvi Industries",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold,color=androidx.compose.ui.graphics.Color.White);Text("Enterprise Mobile • Business. Anywhere.",style=MaterialTheme.typography.bodyMedium,color=androidx.compose.ui.graphics.Color.White.copy(.80f))}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Mobile",MobileBuildInfo.MOBILE_VERSION,Icons.Rounded.PhoneIphone,Modifier.weight(1f),DseViolet);DseMetricTile("Server",MobileBuildInfo.SERVER_BASELINE,Icons.Rounded.CloudDone,Modifier.weight(1f),DseSuccess)}
        DseSection("Compatibility",Icons.Rounded.Verified){Text("API contract ${MobileBuildInfo.API_CONTRACT_VERSION}");Text("Business authority remains on the Jasvi Industries server. Mobile lifecycle, numbering, validation, permissions and resulting ERP state stay server-owned.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        DseSection(platformDeviceClassLabel(),if(platformName()=="Android") Icons.Rounded.PhoneAndroid else Icons.Rounded.PhoneIphone){Text(platformSecurityLabel());Text(platformImportCapability());Text(platformPushCapability());Text(platformSystemExperienceCapability())}
        DseSection("Secure architecture",Icons.Rounded.Security){Text("Server backup/restore, safe rollback, updater and template-studio authoring remain protected desktop/server administration operations.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("Mobile consumes approved ERP data and documents without duplicating destructive server-maintenance tools.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
