@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.dse.mobile.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dse.mobile.core.api.*
import org.dse.mobile.core.model.*
import org.dse.mobile.core.offline.OfflineRepository
import org.dse.mobile.core.offline.platformOfflineNowMillis

@Composable
internal fun DashboardScreen(
    api:DseErpHttpClient,
    p:PermissionContext,
    onTab:(MainTab)->Unit,
    onMore:(MoreDestination)->Unit,
    onSearch:()->Unit={},
    onTarget:(RecordTarget)->Unit={},
){
    var data by remember{mutableStateOf<InsightDashboardBundle?>(null)}
    var msg by remember{mutableStateOf("Loading live workspace…")}
    var dismissedMessage by remember{mutableStateOf("")}
    var key by remember{mutableIntStateOf(0)}
    LaunchedEffect(api,key){
        when(val r=api.insightDashboard("This Month")){
            is ApiResult.Success->{
                data=r.value
                msg=if(r.source==ApiDataSource.CACHE)"Offline snapshot • ${r.cachedAtMillis?.let(::cacheAgeDash).orEmpty()}" else "Live UAT • updated now"
                if(r.source==ApiDataSource.LIVE&&OfflineRepository.widgetSharingEnabled()){
                    val today=(api.insightDashboard("Today") as? ApiResult.Success)?.value?.snapshot
                    if(today!=null)platformPublishWidgetSnapshot(
                        WidgetDashboardSnapshot(
                            salesToday=today.salesValue,
                            receivables=r.value.snapshot.receivables,
                            purchases=today.purchaseValue,
                            payables=r.value.snapshot.payables,
                            bankBalance=r.value.snapshot.cash,
                            updatedAtMillis=platformOfflineNowMillis(),
                        )
                    )
                }
            }
            else->msg=r.readableMessage()
        }
    }
    val s=data?.snapshot
    val name=p.user?.fullName?.takeIf{!it.isNullOrBlank()}?:p.user?.username.orEmpty()
    val firstName=name.substringBefore(' ').ifBlank{"there"}
    val notice=noticeKindFor(msg)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=7.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp),
    ){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
            Column(Modifier.weight(1f)){
                Text("Welcome back, $firstName",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold)
                Text(if(msg.startsWith("Live",true)||msg.startsWith("Offline",true))msg else "Business overview",style=MaterialTheme.typography.labelSmall,color=if(msg.startsWith("Offline",true))DseWarning else MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            }
            FilledTonalIconButton(onClick={key++},shape=androidx.compose.foundation.shape.RoundedCornerShape(13.dp),modifier=Modifier.size(38.dp)){Icon(Icons.Rounded.Refresh,"Refresh",Modifier.size(19.dp))}
        }

        Surface(
            modifier=Modifier.fillMaxWidth().clickable(onClick=onSearch),
            shape=androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
            color=MaterialTheme.colorScheme.surface,
            border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(.35f)),
        ){
            Row(Modifier.padding(horizontal=11.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Icon(Icons.Rounded.Search,null,Modifier.size(19.dp),tint=MaterialTheme.colorScheme.primary)
                Text("Search ERP records…",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Rounded.Tune,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
            }
        }

        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            DseMetricTile("Sales",money(s?.salesValue),Icons.Rounded.BarChart,Modifier.weight(1f),DseSuccess)
            DseMetricTile("Receivable",money(s?.receivables),Icons.Rounded.CreditCard,Modifier.weight(1f),DseInfo)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            DseMetricTile("Payable",money(s?.payables),Icons.Rounded.Receipt,Modifier.weight(1f),DseWarning)
            DseMetricTile("Bank",money(s?.cash),Icons.Rounded.AccountBalance,Modifier.weight(1f),DsePurple)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            DseMetricTile("Sales Invoices",(s?.invoices?:0).toString(),Icons.Rounded.ReceiptLong,Modifier.weight(1f),DsePurple)
            DseMetricTile("Purchase Invoices",(s?.purchases?:0).toString(),Icons.Rounded.ShoppingCart,Modifier.weight(1f),DseWarning)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            DseMetricTile("Customers",(s?.customers?:0).toString(),Icons.Rounded.Groups,Modifier.weight(1f),DseInfo)
            DseMetricTile("Catalog Items",(s?.products?:0).toString(),Icons.Rounded.Inventory2,Modifier.weight(1f),DseSuccess)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            DseMetricTile("Purchase Value",money(s?.purchaseValue),Icons.Rounded.ShoppingBag,Modifier.weight(1f),DseWarning)
            DseMetricTile("Low Stock",(s?.lowStock?:0).toString(),Icons.Rounded.WarningAmber,Modifier.weight(1f),if((s?.lowStock?:0)>0)DseDanger else DseSuccess)
        }

        if(data==null)DseLoadingState("Loading dashboard records…")

        data?.recent?.take(6)?.takeIf{it.isNotEmpty()}?.let{rows->
            PremiumSectionHeader("Recent Documents","View Sales"){onTab(MainTab.SALES)}
            rows.forEach{r->
                DseRecordCard(
                    title=r.number,
                    subtitle=r.party.ifBlank{r.type},
                    amount=money(r.amount),
                    statuses=listOf("Module" to r.type),
                    meta=r.date,
                    swipeEndActions=listOf(SwipeAction("Actions",Icons.Rounded.MoreHoriz){onTarget(RecordTarget(r.type,r.number,openActions=true))}),
                    onActions={onTarget(RecordTarget(r.type,r.number,openActions=true))},
                ){onTarget(RecordTarget(r.type,r.number))}
            }
        }

        data?.activities?.take(4)?.takeIf{it.isNotEmpty()}?.let{rows->
            PremiumSectionHeader("Follow-ups","Reminders"){onMore(MoreDestination.REMINDERS)}
            rows.forEach{n->
                Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(.28f)),modifier=Modifier.fillMaxWidth().clickable(enabled=!n.moduleKey.isNullOrBlank()&&!n.referenceNo.isNullOrBlank()){onTarget(RecordTarget(n.moduleKey.orEmpty(),n.referenceNo.orEmpty(),n.recordId))}){
                    Row(Modifier.padding(horizontal=10.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        PremiumIconTile(Icons.Rounded.NotificationsActive,if(n.severity.contains("HIGH",true)||n.severity.contains("ERROR",true))DseDanger else DseWarning,size=34.dp)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)){Text(n.title.ifBlank{n.category},fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium,maxLines=1);Text(n.message,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)}
                        Icon(Icons.Rounded.ChevronRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        PremiumSectionHeader("Quick Actions","More"){onMore(MoreDestination.MASTERS)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            if(p.can("SALES","CREATE"))QuickActionTile("New Sale",Icons.Rounded.AddShoppingCart,DsePurple,Modifier.weight(1f)){onTarget(RecordTarget("SALES","__CREATE__"))}
            if(p.can("PURCHASE","CREATE"))QuickActionTile("Purchase",Icons.Rounded.ShoppingBag,DseWarning,Modifier.weight(1f)){onTarget(RecordTarget("PURCHASE","__CREATE__"))}
            if(p.can("QUOTATION","CREATE"))QuickActionTile("Quote",Icons.Rounded.RequestQuote,DseInfo,Modifier.weight(1f)){onTarget(RecordTarget("QUOTATION","__CREATE__"))}
            if(p.can("CUSTOMERS","CREATE"))QuickActionTile("Customer",Icons.Rounded.PersonAdd,DseSuccess,Modifier.weight(1f)){onTarget(RecordTarget("CUSTOMER","__CREATE__"))}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            if(p.can("INVENTORY","CREATE"))QuickActionTile("Add Item",Icons.Rounded.Inventory2,DseInfo,Modifier.weight(1f)){onTarget(RecordTarget("ITEM","__CREATE__"))}
            if(p.can("SUPPLIERS","CREATE"))QuickActionTile("Add Supplier",Icons.Rounded.LocalShipping,DseSuccess,Modifier.weight(1f)){onTarget(RecordTarget("SUPPLIER","__CREATE__"))}
            if(p.can("BANK_EXPENSE","CREATE"))QuickActionTile("Bank Entry",Icons.Rounded.AccountBalance,DsePurple,Modifier.weight(1f)){onTarget(RecordTarget("FINANCE","__CREATE__",source="BANK_ENTRY"))}
            if(p.can("BANK_EXPENSE","CREATE"))QuickActionTile("Expense Entry",Icons.Rounded.ReceiptLong,DseWarning,Modifier.weight(1f)){onTarget(RecordTarget("EXPENSE","__CREATE__",source="EXPENSE"))}
        }

        data?.ageing?.takeIf{it.isNotEmpty()}?.let{rows->
            PremiumSectionHeader("Receivables Ageing")
            rows.forEach{entry->val parts=entry.split('|',limit=2);val label=parts.firstOrNull().orEmpty();val amount=parts.getOrNull(1)?.toDoubleOrNull()?:0.0;Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(money(amount),fontWeight=FontWeight.Bold)}}
        }

        data?.topCustomers?.take(4)?.takeIf{it.isNotEmpty()}?.let{rows->
            PremiumSectionHeader("Top Customers")
            rows.forEachIndexed{i,r->
                Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Surface(shape=androidx.compose.foundation.shape.CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Text("${i+1}",Modifier.padding(horizontal=8.dp,vertical=5.dp),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)}
                    Text(r,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    if(notice!=null&&msg!=dismissedMessage)DseNoticeDialog(msg,notice){dismissedMessage=msg}
}

@Composable
private fun QuickActionTile(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,accent:androidx.compose.ui.graphics.Color,modifier:Modifier=Modifier,onClick:()->Unit){
    Surface(
        modifier=modifier.clickable(onClick=onClick),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color=MaterialTheme.colorScheme.surface,
        border=androidx.compose.foundation.BorderStroke(1.dp,accent.copy(.13f)),
        shadowElevation=2.dp,
    ){
        Column(Modifier.padding(horizontal=6.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
            PremiumIconTile(icon,accent,size=32.dp)
            Text(label,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold,maxLines=1)
        }
    }
}


@Composable internal fun GlobalSearchDialog(api:DseErpHttpClient,onClose:()->Unit,onOpen:(GlobalSearchRow)->Unit={}){
    var q by remember{mutableStateOf("")}
    var rows by remember{mutableStateOf<List<GlobalSearchRow>>(emptyList())}
    var msg by remember{mutableStateOf("Search invoices, parties, items, payments, returns and bank records")}
    var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val focusManager=LocalFocusManager.current
    suspend fun searchNow(term:String=q){
        val value=term.trim()
        if(value.length<2){rows=emptyList();msg="Type at least 2 characters";return}
        busy=true
        when(val r=api.globalSearch(value)){
            is ApiResult.Success->{rows=r.value;msg="${rows.size} result(s)"}
            else->{rows=emptyList();msg=r.readableMessage()}
        }
        busy=false
    }
    LaunchedEffect(q){
        val value=q.trim()
        if(value.length<2){rows=emptyList();return@LaunchedEffect}
        delay(300)
        searchNow(value)
    }
    PremiumAlertDialog(
        onDismissRequest=onClose,
        title={Text("Global Search")},
        text={Column(Modifier.fillMaxWidth().heightIn(max=620.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(
                q,{q=it},label={Text("Search ${BusinessBrandingState.displayName}")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=MaterialTheme.shapes.medium,
                leadingIcon={Icon(Icons.Rounded.Search,null)},
                trailingIcon={if(busy)Icon(Icons.Rounded.Sync,"Searching") else if(q.isNotBlank())IconButton(onClick={q="";rows=emptyList();msg="Search invoices, parties, items, payments, returns and bank records"}){Icon(Icons.Rounded.Clear,"Clear")}},
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(onSearch={focusManager.clearFocus();scope.launch{searchNow()}}),
            )
            DseMessageFeedback(msg)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!busy&&q.trim().length>=2&&rows.isEmpty()){
                Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(.35f),modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Rounded.SearchOff,null,tint=MaterialTheme.colorScheme.primary)
                        Column{Text("No matching ERP records",fontWeight=FontWeight.Bold);Text("Try invoice number, party, item, payment reference or bank narration.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                }
            }
            rows.take(100).groupBy{it.module.ifBlank{it.moduleKey}}.forEach{(module,moduleRows)->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    PremiumIconTile(semanticActionIcon(module),MaterialTheme.colorScheme.primary,size=34.dp)
                    Text(module.ifBlank{"ERP Records"},style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.ExtraBold,modifier=Modifier.weight(1f))
                    Text("${moduleRows.size}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                }
                moduleRows.forEach{r->
                    DseRecordCard(
                        title=r.reference.ifBlank{r.description},
                        subtitle=r.description.ifBlank{r.module},
                        amount="",
                        statuses=listOf("Module" to r.module),
                        meta=r.detail,
                        swipeStartActions=listOf(SwipeAction("View",Icons.Rounded.Visibility){focusManager.clearFocus();onOpen(r)}),
                    ){focusManager.clearFocus();onOpen(r)}
                }
            }
            Spacer(Modifier.height(8.dp))
        }},
        confirmButton={TextButton(onClick=onClose){Text("Close")}},
    )
}

private fun cacheAgeDash(last:Long):String{val m=((platformOfflineNowMillis()-last).coerceAtLeast(0)/60_000);return when{m<1->"just now";m<60->"$m min ago";m<1440->"${m/60} hr ago";else->"${m/1440} day(s) ago"}}
