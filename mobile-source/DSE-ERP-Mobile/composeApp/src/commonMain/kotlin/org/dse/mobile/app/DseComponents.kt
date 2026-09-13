@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.dse.mobile.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dse.mobile.core.api.ApiResult
import org.dse.mobile.core.api.DseErpHttpClient
import org.dse.mobile.core.model.ActivityRow
import org.dse.mobile.core.model.MasterItem
import org.dse.mobile.core.model.MasterParty
import org.dse.mobile.core.offline.platformOfflineNowMillis
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
internal fun DseField(
    label:String,
    value:String,
    modifier:Modifier=Modifier,
    singleLine:Boolean=false,
    readOnly:Boolean=false,
    enabled:Boolean=true,
    supporting:String?=null,
    required:Boolean=false,
    icon:ImageVector?=null,
    onValue:(String)->Unit,
){
    val accent=semanticFieldAccent(label)
    val focusManager=LocalFocusManager.current
    OutlinedTextField(
        value=value,
        onValueChange=onValue,
        label={PremiumFieldLabel(label,required,icon,accent)},
        modifier=modifier.fillMaxWidth(),
        singleLine=singleLine,
        readOnly=readOnly,
        enabled=enabled,
        keyboardOptions=KeyboardOptions(imeAction=if(singleLine)ImeAction.Done else ImeAction.Default),
        keyboardActions=KeyboardActions(onDone={focusManager.clearFocus()}),
        supportingText=supporting?.let{{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
        textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium),
        shape=RoundedCornerShape(18.dp),
        colors=OutlinedTextFieldDefaults.colors(
            focusedTextColor=accent,
            unfocusedTextColor=accent.copy(.92f),
            disabledTextColor=accent.copy(.62f),
            focusedBorderColor=accent.copy(.72f),
            unfocusedBorderColor=accent.copy(.32f),
            focusedContainerColor=accent.copy(.035f),
            unfocusedContainerColor=MaterialTheme.colorScheme.surface,
            cursorColor=accent,
        ),
    )
}

@Composable
internal fun DseNumberField(
    label:String,
    value:String,
    modifier:Modifier=Modifier,
    enabled:Boolean=true,
    allowNegative:Boolean=false,
    min:Double?=null,
    max:Double?=null,
    required:Boolean=false,
    icon:ImageVector?=null,
    onValue:(String)->Unit,
){
    val accent=semanticFieldAccent(label)
    val focusManager=LocalFocusManager.current
    OutlinedTextField(
        value=value,
        onValueChange={next->
            val numericPattern=if(allowNegative) Regex("^-?[0-9]*([.][0-9]*)?$") else Regex("^[0-9]*([.][0-9]*)?$")
            if(next.isBlank()||next.matches(numericPattern)){
                val parsed=next.toDoubleOrNull()
                if(parsed==null||(min==null||parsed>=min)&&(max==null||parsed<=max)) onValue(next)
            }
        },
        label={PremiumFieldLabel(label,required,icon,accent)},
        modifier=modifier.fillMaxWidth(),
        singleLine=true,
        enabled=enabled,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),
        keyboardActions=KeyboardActions(onDone={focusManager.clearFocus()}),
        textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium),
        shape=RoundedCornerShape(18.dp),
        colors=OutlinedTextFieldDefaults.colors(
            focusedTextColor=accent,
            unfocusedTextColor=accent.copy(.92f),
            disabledTextColor=accent.copy(.62f),
            focusedBorderColor=accent.copy(.72f),
            unfocusedBorderColor=accent.copy(.32f),
            focusedContainerColor=accent.copy(.035f),
            unfocusedContainerColor=MaterialTheme.colorScheme.surface,
            cursorColor=accent,
        ),
    )
}

@Composable
internal fun DseDateField(
    label:String,
    value:String,
    modifier:Modifier=Modifier,
    enabled:Boolean=true,
    required:Boolean=false,
    onValue:(String)->Unit,
){
    var open by remember{mutableStateOf(false)}
    val accent=semanticFieldAccent(label)
    OutlinedTextField(
        value=value,
        onValueChange={},
        label={PremiumFieldLabel(label,required,Icons.Rounded.CalendarMonth,accent)},
        modifier=modifier.fillMaxWidth(),
        readOnly=true,
        singleLine=true,
        enabled=enabled,
        trailingIcon={IconButton(enabled=enabled,onClick={open=true}){Icon(Icons.Rounded.CalendarMonth,"Choose $label",tint=accent)}},
        supportingText={Text("YYYY-MM-DD",color=MaterialTheme.colorScheme.onSurfaceVariant)},
        textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium),
        shape=RoundedCornerShape(18.dp),
        colors=OutlinedTextFieldDefaults.colors(
            focusedTextColor=accent,
            unfocusedTextColor=accent.copy(.92f),
            disabledTextColor=accent.copy(.62f),
            focusedBorderColor=accent.copy(.72f),
            unfocusedBorderColor=accent.copy(.32f),
            focusedContainerColor=accent.copy(.035f),
            unfocusedContainerColor=MaterialTheme.colorScheme.surface,
            cursorColor=accent,
        ),
    )
    if(open){
        val initialMillis=value.takeIf{it.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))}?.let{isoDateToEpochMillis(it)}
        val state=rememberDatePickerState(initialSelectedDateMillis=initialMillis)
        DatePickerDialog(
            onDismissRequest={open=false},
            confirmButton={PremiumPrimaryButton("Select",{state.selectedDateMillis?.let{onValue(isoDateFromEpochMillis(it))};open=false},leadingIcon=Icons.Rounded.Check)},
            dismissButton={PremiumSecondaryButton("Cancel",{open=false})},
        ){DatePicker(state=state)}
    }
}

@Composable
internal fun DseSelect(
    label:String,
    value:String,
    options:List<String>,
    modifier:Modifier=Modifier,
    enabled:Boolean=true,
    emptyMessage:String="No values configured in Master Data",
    required:Boolean=false,
    icon:ImageVector?=null,
    onValue:(String)->Unit,
){
    var expanded by remember{mutableStateOf(false)}
    val cleanOptions=remember(options){options.distinct().filter{it.isNotBlank()}}
    val accent=semanticFieldAccent(label)
    Box(modifier){
        OutlinedTextField(
            value=value,
            onValueChange={},
            readOnly=true,
            enabled=enabled,
            label={PremiumFieldLabel(label,required,icon,accent)},
            trailingIcon={Icon(if(expanded)Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,"Choose $label",tint=accent)},
            supportingText=if(cleanOptions.isEmpty()){{Text(emptyMessage,color=MaterialTheme.colorScheme.onSurfaceVariant)}}else null,
            modifier=Modifier.fillMaxWidth(),
            textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium),
            shape=RoundedCornerShape(18.dp),
            colors=OutlinedTextFieldDefaults.colors(
                focusedTextColor=accent,
                unfocusedTextColor=accent.copy(.92f),
                disabledTextColor=accent.copy(.62f),
                focusedBorderColor=accent.copy(.72f),
                unfocusedBorderColor=accent.copy(.32f),
                focusedContainerColor=accent.copy(.035f),
                unfocusedContainerColor=MaterialTheme.colorScheme.surface,
            ),
        )
        Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(18.dp)).clickable(
                enabled=enabled&&cleanOptions.isNotEmpty(),
                onClick={expanded=!expanded},
            )
        )
        DropdownMenu(
            expanded=expanded,
            onDismissRequest={expanded=false},
            modifier=Modifier.widthIn(min=220.dp,max=420.dp).heightIn(max=360.dp),
        ){
            cleanOptions.forEach{option->
                DropdownMenuItem(
                    text={Text(option,fontWeight=if(option==value)FontWeight.Bold else FontWeight.Medium,color=if(option==value)accent else MaterialTheme.colorScheme.onSurface)},
                    leadingIcon={Icon(if(option==value)Icons.Rounded.Check else semanticFieldIcon(label),null,tint=if(option==value)accent else accent.copy(.58f),modifier=Modifier.size(18.dp))},
                    onClick={onValue(option);expanded=false},
                )
            }
        }
    }
}

@Composable
internal fun DsePasswordField(
    label:String,
    value:String,
    modifier:Modifier=Modifier,
    required:Boolean=true,
    enabled:Boolean=true,
    onValue:(String)->Unit,
    onDone:()->Unit={},
){
    var visible by remember{mutableStateOf(false)}
    val accent=semanticFieldAccent(label)
    val focusManager=LocalFocusManager.current
    OutlinedTextField(
        value=value,
        onValueChange=onValue,
        label={PremiumFieldLabel(label,required,Icons.Rounded.Lock,accent)},
        modifier=modifier.fillMaxWidth(),
        singleLine=true,
        enabled=enabled,
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
        keyboardActions=KeyboardActions(onDone={focusManager.clearFocus();onDone()}),
        visualTransformation=if(visible)androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        trailingIcon={IconButton(onClick={visible=!visible}){Icon(if(visible)Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,if(visible)"Hide password" else "Show password",tint=accent)}},
        textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium),
        shape=RoundedCornerShape(18.dp),
        colors=OutlinedTextFieldDefaults.colors(
            focusedTextColor=accent,
            unfocusedTextColor=accent.copy(.92f),
            focusedBorderColor=accent.copy(.72f),
            unfocusedBorderColor=accent.copy(.32f),
            focusedContainerColor=accent.copy(.035f),
            unfocusedContainerColor=MaterialTheme.colorScheme.surface,
            cursorColor=accent,
        ),
    )
}

@Composable
internal fun PartySelector(
    api:DseErpHttpClient,
    type:String,
    selected:MasterParty?,
    modifier:Modifier=Modifier,
    required:Boolean=true,
    onSelected:(MasterParty)->Unit,
    onCleared:()->Unit={},
){
    var query by remember(selected?.id){mutableStateOf(selected?.let{"${it.partyCode} • ${it.name}"}.orEmpty())}
    var results by remember{mutableStateOf<List<MasterParty>>(emptyList())}
    var expanded by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()

    suspend fun searchNow(term:String=query){
        val q=term.trim()
        if(q.length<2){results=emptyList();expanded=false;return}
        busy=true;error=""
        when(val r=api.searchParties(type,q,50)){
            is ApiResult.Success->{results=r.value.filter{it.active};expanded=true}
            else->{results=emptyList();expanded=false;error=r.readableMessage()}
        }
        busy=false
    }

    LaunchedEffect(query,selected?.id,type){
        if(selected!=null)return@LaunchedEffect
        val q=query.trim()
        if(q.length<2){results=emptyList();expanded=false;return@LaunchedEffect}
        delay(250)
        searchNow(q)
    }

    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){
        Box{
            OutlinedTextField(
                value=query,
                onValueChange={next->
                    query=next
                    val selectedDisplay=selected?.let{"${it.partyCode} • ${it.name}"}.orEmpty()
                    if(selected!=null&&next!=selectedDisplay)onCleared()
                },
                label={PremiumFieldLabel(if(type.equals("CUSTOMER",true))"Customer" else "Supplier",required,Icons.Rounded.PersonSearch,DseIndigo)},
                trailingIcon={IconButton(enabled=!busy&&query.trim().length>=2,onClick={scope.launch{searchNow()}}){Icon(if(busy)Icons.Rounded.Sync else Icons.Rounded.Search,"Search")}},
                modifier=Modifier.fillMaxWidth(),
                singleLine=true,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(onSearch={scope.launch{searchNow()}}),
                supportingText={Text(error.ifBlank{if(query.trim().length<2)"Type at least 2 characters — results appear automatically" else "Live results from ${if(type.equals("CUSTOMER",true))"Customer" else "Supplier"} Master"})},
                shape=RoundedCornerShape(18.dp),
                textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium),
                colors=OutlinedTextFieldDefaults.colors(focusedTextColor=DseIndigo,unfocusedTextColor=DseIndigo.copy(.92f),focusedBorderColor=DseIndigo.copy(.72f),unfocusedBorderColor=DseIndigo.copy(.32f),focusedContainerColor=DseIndigo.copy(.035f),unfocusedContainerColor=MaterialTheme.colorScheme.surface,cursorColor=DseIndigo),
            )
            DropdownMenu(
                expanded=expanded,
                onDismissRequest={expanded=false},
                modifier=Modifier.fillMaxWidth(.94f).heightIn(max=380.dp),
            ){
                if(results.isEmpty())DropdownMenuItem(text={Text("No matching ${type.lowercase()} found")},onClick={expanded=false})
                results.forEach{p->DropdownMenuItem(
                    text={Column{Text("${p.partyCode} • ${p.name}",fontWeight=FontWeight.SemiBold);Text(listOfNotNull(p.gstin?.takeIf{it.isNotBlank()},p.phone?.takeIf{it.isNotBlank()},p.address?.takeIf{it.isNotBlank()}).joinToString(" • "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)}},
                    onClick={onSelected(p);query="${p.partyCode} • ${p.name}";expanded=false;results=emptyList()},
                )}
            }
        }
        selected?.let{p->
            Surface(color=DseIndigo.copy(.045f),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,DseIndigo.copy(.16f)),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                        PremiumIconTile(Icons.Rounded.Person,DseIndigo,size=34.dp)
                        Column(Modifier.weight(1f)){
                            PremiumValueText(if(type.equals("CUSTOMER",true))"Customer" else "Supplier",p.name)
                            Text(p.partyCode,style=MaterialTheme.typography.labelSmall,color=DseIndigo.copy(.72f),fontWeight=FontWeight.Bold)
                        }
                    }
                    if(!p.gstin.isNullOrBlank()) detailRows(listOf("GSTIN" to p.gstin.orEmpty()))
                    if(!p.phone.isNullOrBlank()) detailRows(listOf("Phone" to p.phone.orEmpty()))
                    if(!p.address.isNullOrBlank()) detailRows(listOf("Address" to p.address.orEmpty()))
                }
            }
        }
    }
}

@Composable
internal fun ItemSelector(
    api:DseErpHttpClient,
    selected:MasterItem?,
    modifier:Modifier=Modifier,
    required:Boolean=true,
    onSelected:(MasterItem)->Unit,
    onCleared:()->Unit={},
){
    var query by remember(selected?.id){mutableStateOf(selected?.let{"${it.itemCode} • ${it.description}"}.orEmpty())}
    var results by remember{mutableStateOf<List<MasterItem>>(emptyList())}
    var expanded by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()

    suspend fun searchNow(term:String=query){
        val q=term.trim()
        if(q.length<2){results=emptyList();expanded=false;return}
        busy=true;error=""
        when(val r=api.searchItems(q,50)){
            is ApiResult.Success->{results=r.value.filter{it.active};expanded=true}
            else->{results=emptyList();expanded=false;error=r.readableMessage()}
        }
        busy=false
    }

    LaunchedEffect(query,selected?.id){
        if(selected!=null)return@LaunchedEffect
        val q=query.trim()
        if(q.length<2){results=emptyList();expanded=false;return@LaunchedEffect}
        delay(250)
        searchNow(q)
    }

    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){
        Box{
            OutlinedTextField(
                value=query,onValueChange={next->
                    query=next
                    val selectedDisplay=selected?.let{"${it.itemCode} • ${it.description}"}.orEmpty()
                    if(selected!=null&&next!=selectedDisplay)onCleared()
                },label={PremiumFieldLabel("Item",required,Icons.Rounded.Inventory2,DseIndigo)},
                trailingIcon={IconButton(enabled=!busy&&query.trim().length>=2,onClick={scope.launch{searchNow()}}){Icon(if(busy)Icons.Rounded.Sync else Icons.Rounded.Search,"Search")}},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(onSearch={scope.launch{searchNow()}}),
                supportingText={Text(error.ifBlank{if(query.trim().length<2)"Type at least 2 characters — results appear automatically" else "Live results from Item Master"})},
                shape=RoundedCornerShape(18.dp),
                textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium),
                colors=OutlinedTextFieldDefaults.colors(focusedTextColor=DseIndigo,unfocusedTextColor=DseIndigo.copy(.92f),focusedBorderColor=DseIndigo.copy(.72f),unfocusedBorderColor=DseIndigo.copy(.32f),focusedContainerColor=DseIndigo.copy(.035f),unfocusedContainerColor=MaterialTheme.colorScheme.surface,cursorColor=DseIndigo),
            )
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.fillMaxWidth(.94f).heightIn(max=380.dp)){
                if(results.isEmpty())DropdownMenuItem(text={Text("No matching item found")},onClick={expanded=false})
                results.forEach{i->DropdownMenuItem(
                    text={Column{
                        Text("${i.itemCode} • ${i.description}",fontWeight=FontWeight.SemiBold,color=DseIndigo)
                        Text("${i.unit.orEmpty()} • GST ${i.gst}% • Sale ${money(i.sellingPrice)} • Purchase ${money(i.purchasePrice)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }},
                    leadingIcon={Icon(Icons.Rounded.Inventory2,null,tint=DseIndigo)},
                    onClick={onSelected(i);query="${i.itemCode} • ${i.description}";expanded=false;results=emptyList()}
                )}
            }
        }
        selected?.let{i->
            Surface(color=DseIndigo.copy(.045f),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,DseIndigo.copy(.16f)),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                        PremiumIconTile(Icons.Rounded.Inventory2,DseIndigo,size=34.dp)
                        Column(Modifier.weight(1f)){PremiumValueText("Item",i.description);Text(i.itemCode,style=MaterialTheme.typography.labelSmall,color=DseIndigo.copy(.72f),fontWeight=FontWeight.Bold)}
                    }
                    detailRows(listOf("Unit" to i.unit.orEmpty(),"GST %" to i.gst.toString(),"Sale Price" to money(i.sellingPrice),"Purchase Price" to money(i.purchasePrice)))
                }
            }
        }
    }
}

@Composable
internal fun AttachmentManager(
    api:DseErpHttpClient,
    documentType:String,
    documentId:Int,
    canEdit:Boolean,
    refreshKey:Int=0,
    onMessage:(String)->Unit={},
){
    var items by remember(documentType,documentId,refreshKey){mutableStateOf<List<org.dse.mobile.core.model.AttachmentMeta>>(emptyList())}
    var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(documentType,documentId,refreshKey){
        if(documentId>0){
            when(val r=api.documentAttachments(documentType,documentId)){
                is ApiResult.Success->items=r.value
                else->onMessage(r.readableMessage())
            }
        }
    }
    DseSection("Attachments",Icons.Rounded.AttachFile){
        if(items.isEmpty()) Text("No attachments",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach{a->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Rounded.Description,null);Spacer(Modifier.width(8.dp));Text(a.fileName,Modifier.weight(1f));
                IconButton(enabled=!busy,onClick={scope.launch{busy=true;when(val r=api.documentAttachmentFile(documentType,documentId,a.id)){is ApiResult.Success->{if(!platformShareFile(a.fileName,a.fileName,r.value))onMessage("Unable to open/share ${a.fileName}")};else->onMessage(r.readableMessage())};busy=false}}){Icon(Icons.Rounded.OpenInNew,"Open attachment")}
                if(canEdit) IconButton(onClick={scope.launch{busy=true;when(val r=api.deleteDocumentAttachment(documentType,documentId,a.id)){is ApiResult.Success->{items=items.filterNot{it.id==a.id};onMessage("Attachment removed")};else->onMessage(r.readableMessage())};busy=false}}){Icon(Icons.Rounded.Delete,"Delete attachment")}
            }
        }
        if(canEdit&&documentId>0) PremiumSecondaryButton("Add Attachment",onClick={
            if(busy)return@PremiumSecondaryButton
            platformPickAttachment { picked ->
                val name=picked.fileName;val encoded=picked.base64
                if(name.isNullOrBlank()||encoded.isNullOrBlank()){onMessage(picked.error?:"No attachment selected");return@platformPickAttachment}
                scope.launch{
                    busy=true
                    try {
                        when(val r=api.addDocumentAttachment(documentType,documentId,name,decodeBase64Portable(encoded))){
                            is ApiResult.Success->{items=items+r.value;onMessage("Attached $name")}
                            else->onMessage(r.readableMessage())
                        }
                    } catch(t:Throwable){onMessage(t.message?:"Attachment upload failed")} finally {busy=false}
                }
            }
        },enabled=!busy,icon=Icons.Rounded.AttachFile)
    }
}

@Composable
internal fun DseKpi(label:String,value:String,modifier:Modifier=Modifier,icon:ImageVector=Icons.Rounded.Analytics){
    val accent=semanticFieldAccent(label)
    PremiumCard(modifier=modifier,padding=9.dp){
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically){
            PremiumIconTile(icon,accent,size=32.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(0.dp)){
                Text(label,style=MaterialTheme.typography.labelSmall,color=accent,fontWeight=FontWeight.Bold,maxLines=1)
                Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,color=accent,maxLines=2)
            }
        }
    }
}

@Composable
internal fun DseHeroKpi(
    label:String,
    value:String,
    supporting:String="",
    icon:ImageVector=Icons.Rounded.TrendingUp,
    modifier:Modifier=Modifier,
){
    Box(
        modifier
            .fillMaxWidth()
            .shadow(10.dp,RoundedCornerShape(20.dp),ambientColor=DsePurple.copy(.14f),spotColor=DsePurple.copy(.18f))
            .clip(RoundedCornerShape(20.dp))
            .background(DseHeroGradient)
    ){
        Row(Modifier.padding(horizontal=12.dp,vertical=11.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.14f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(20.dp),tint=Color.White)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)){
                Text(label,style=MaterialTheme.typography.labelSmall,color=Color.White.copy(.82f),maxLines=1)
                Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold,color=Color.White,maxLines=2)
                if(supporting.isNotBlank())Text(supporting,style=MaterialTheme.typography.labelSmall,color=Color.White.copy(.76f),maxLines=1)
            }
        }
    }
}

@Composable
internal fun DseMetricTile(
    label:String,
    value:String,
    icon:ImageVector=Icons.Rounded.Analytics,
    modifier:Modifier=Modifier,
    accent:Color=semanticFieldAccent(label),
){
    Surface(
        modifier=modifier.shadow(4.dp,RoundedCornerShape(16.dp),ambientColor=Color.Black.copy(.04f),spotColor=Color.Black.copy(.05f)),
        shape=RoundedCornerShape(16.dp),
        color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,accent.copy(.12f)),
        tonalElevation=0.dp,
    ){
        Row(Modifier.padding(horizontal=9.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
            PremiumIconTile(icon,accent,size=30.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(0.dp)){
                Text(label,style=MaterialTheme.typography.labelSmall,color=accent,fontWeight=FontWeight.Bold,maxLines=1)
                Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,color=accent,maxLines=2)
            }
        }
    }
}

@Composable
internal fun DseSection(title:String,icon:ImageVector=Icons.Rounded.Topic,content:@Composable ColumnScope.()->Unit){
    val accent=semanticFieldAccent(title)
    PremiumCard(modifier=Modifier.fillMaxWidth(),padding=10.dp){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
            PremiumIconTile(icon,accent,size=32.dp)
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=accent)
        }
        content()
    }
}

@Composable
internal fun DseStatus(label:String,status:String){
    val s=status.ifBlank{"—"};val (container,content)=statusTone(s)
    Surface(color=container,contentColor=content,shape=RoundedCornerShape(999.dp),border=BorderStroke(1.dp,content.copy(.10f))){
        Row(Modifier.padding(horizontal=9.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(statusIcon(s),null,Modifier.size(14.dp));Text(if(label.isBlank())s else "$label: $s",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold)
        }
    }
}

internal data class SwipeAction(
    val label:String,
    val icon:ImageVector,
    val destructive:Boolean=false,
    val onClick:()->Unit,
)

@Composable
internal fun SwipeActionContainer(
    startActions:List<SwipeAction> = emptyList(),
    endActions:List<SwipeAction> = emptyList(),
    modifier:Modifier=Modifier,
    content:@Composable ()->Unit,
){
    if(startActions.isEmpty()&&endActions.isEmpty()){
        Box(modifier){content()}
        return
    }
    val density=LocalDensity.current
    val actionWidth=56.dp
    val startMax=with(density){(actionWidth*startActions.size.toFloat()).toPx()}
    val endMax=with(density){(actionWidth*endActions.size.toFloat()).toPx()}
    var offset by remember{mutableFloatStateOf(0f)}
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))){
        Row(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(.55f)),verticalAlignment=Alignment.CenterVertically){
            Row(Modifier.width(actionWidth*startActions.size.toFloat()).fillMaxHeight(),verticalAlignment=Alignment.CenterVertically){
                startActions.forEach{action->SwipeActionButton(action,actionWidth){offset=0f;action.onClick()}}
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.width(actionWidth*endActions.size.toFloat()).fillMaxHeight(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.End){
                endActions.forEach{action->SwipeActionButton(action,actionWidth){offset=0f;action.onClick()}}
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset{IntOffset(offset.roundToInt(),0)}
                .pointerInput(startActions.size,endActions.size){
                    detectHorizontalDragGestures(
                        onHorizontalDrag={change,dragAmount->
                            change.consume()
                            offset=(offset+dragAmount).coerceIn(if(endActions.isEmpty())0f else -endMax,if(startActions.isEmpty())0f else startMax)
                        },
                        onDragEnd={
                            offset=when{
                                offset>startMax*.28f&&startActions.isNotEmpty()->startMax
                                offset<(-endMax*.28f)&&endActions.isNotEmpty()->-endMax
                                else->0f
                            }
                        },
                        onDragCancel={offset=0f},
                    )
                }
        ){content()}
    }
}

@Composable
private fun SwipeActionButton(action:SwipeAction,width:Dp,onClick:()->Unit){
    val accent=if(action.destructive)DseDanger else MaterialTheme.colorScheme.primary
    Box(
        Modifier.width(width).fillMaxHeight().clickable(onClick=onClick),
        contentAlignment=Alignment.Center,
    ){
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(.12f)),contentAlignment=Alignment.Center){Icon(action.icon,null,tint=accent,modifier=Modifier.size(19.dp))}
            Text(action.label,style=MaterialTheme.typography.labelSmall,color=accent,fontWeight=FontWeight.Bold,maxLines=1)
        }
    }
}

@Composable
internal fun DseRecordCard(
    title:String,
    subtitle:String,
    amount:String,
    statuses:List<Pair<String,String>>,
    meta:String="",
    selected:Boolean=false,
    onSelect:(()->Unit)?=null,
    swipeStartActions:List<SwipeAction> = emptyList(),
    swipeEndActions:List<SwipeAction> = emptyList(),
    onActions:(()->Unit)?=null,
    onClick:()->Unit,
){
    val effectiveStartActions=if(swipeStartActions.isEmpty()) listOf(SwipeAction("Open",Icons.Rounded.ChevronRight,onClick=onClick)) else swipeStartActions
    SwipeActionContainer(effectiveStartActions,swipeEndActions){
    Surface(
        shape=RoundedCornerShape(18.dp),
        color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(.10f)),
        tonalElevation=0.dp,
        shadowElevation=6.dp,
        modifier=Modifier.fillMaxWidth().clickable(onClick=onClick)
    ){
        Row(Modifier.padding(horizontal=10.dp,vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(99.dp)).background(DseBrandGradient))
            if(onSelect!=null)Checkbox(checked=selected,onCheckedChange={onSelect()})
            PremiumIconTile(Icons.Rounded.Description,MaterialTheme.colorScheme.primary,size=36.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                    Text(amount,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.ExtraBold)
                }
                Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(meta.isNotBlank())Text(meta,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){statuses.filter{it.second.isNotBlank()}.forEach{DseStatus(it.first,it.second)}}
            }
            if(onActions!=null){
                IconButton(onClick=onActions){Icon(Icons.Rounded.MoreVert,"Actions",tint=MaterialTheme.colorScheme.primary)}
            }else{
                Icon(Icons.Rounded.ChevronRight,"Open",tint=MaterialTheme.colorScheme.primary)
            }
        }
    }
    }
}

@Composable
internal fun DseRegisterShell(
    title:String,
    subtitle:String,
    query:String,
    onQuery:(String)->Unit,
    message:String,
    onRefresh:()->Unit,
    onAdd:(()->Unit)?=null,
    kpis:(@Composable ()->Unit)?=null,
    footer:(@Composable ()->Unit)?=null,
    content:@Composable ColumnScope.()->Unit,
){
    val pageScroll=rememberScrollState()
    val focusManager=LocalFocusManager.current
    var dismissedMessage by remember{mutableStateOf("")}
    val dialogKind=remember(message){noticeKindFor(message)}
    val showDialog=message.isNotBlank()&&dialogKind!=null&&message!=dismissedMessage
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(pageScroll)
            .imePadding()
            .padding(horizontal=10.dp,vertical=7.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp),
    ){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            PremiumIconTile(semanticFieldIcon(title),semanticFieldAccent(title),size=34.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(0.dp)){
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold,maxLines=1)
                Text(subtitle,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            }
            FilledTonalIconButton(onClick=onRefresh,shape=RoundedCornerShape(13.dp),modifier=Modifier.size(38.dp)){Icon(Icons.Rounded.Refresh,"Refresh",Modifier.size(19.dp))}
            if(onAdd!=null)FilledIconButton(onClick=onAdd,shape=RoundedCornerShape(13.dp),modifier=Modifier.size(38.dp)){Icon(Icons.Rounded.Add,"New",Modifier.size(20.dp))}
        }
        kpis?.invoke()
        OutlinedTextField(
            value=query,onValueChange=onQuery,placeholder={Text("Search $title",style=MaterialTheme.typography.bodySmall)},leadingIcon={Icon(Icons.Rounded.Search,null,Modifier.size(19.dp))},
            trailingIcon=if(query.isNotBlank()){{IconButton(onClick={onQuery("")}){Icon(Icons.Rounded.Clear,"Clear",Modifier.size(18.dp))}}}else null,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),singleLine=true,shape=RoundedCornerShape(15.dp),
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
            keyboardActions=KeyboardActions(onSearch={focusManager.clearFocus();onRefresh()}),
            colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=MaterialTheme.colorScheme.primary.copy(.55f),unfocusedBorderColor=MaterialTheme.colorScheme.outline.copy(.45f),focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface)
        )
        if(message.isNotBlank()&&dialogKind==null){
            Row(Modifier.fillMaxWidth().padding(horizontal=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                Icon(if(message.contains("offline",true))Icons.Rounded.CloudOff else Icons.Rounded.Info,null,Modifier.size(14.dp),tint=if(message.contains("offline",true))DseWarning else MaterialTheme.colorScheme.primary)
                Text(message,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
            }
        }
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp),content=content)
        footer?.invoke()
        Spacer(Modifier.height(4.dp))
    }
    if(showDialog)DseNoticeDialog(message,dialogKind!!){dismissedMessage=message}
}

@Composable
internal fun DseEmptyRegisterState(title:String,subtitle:String,icon:ImageVector){
    Surface(
        modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(18.dp),
        color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.22f)),
    ){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            PremiumIconTile(icon,MaterialTheme.colorScheme.primary,size=38.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                Text(title,fontWeight=FontWeight.Bold)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun PagingControls(page:Int,totalPages:Int,totalRows:Long,onPage:(Int)->Unit){
    val pages=totalPages.coerceAtLeast(1)
    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(.35f)),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(horizontal=8.dp,vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            IconButton(enabled=page>0,onClick={onPage(page-1)}){Icon(Icons.Rounded.ChevronLeft,"Previous")}
            Column(horizontalAlignment=Alignment.CenterHorizontally){Text("${page+1} / $pages",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold);Text("$totalRows records",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            IconButton(enabled=page+1<pages,onClick={onPage(page+1)}){Icon(Icons.Rounded.ChevronRight,"Next")}
        }
    }
}

internal enum class DseNoticeKind { SUCCESS, WARNING, ERROR, INFO }

internal fun noticeKindFor(message:String):DseNoticeKind?{
    val m=message.trim()
    if(m.isBlank())return null
    val lower=m.lowercase()
    return when{
        lower.startsWith("authentication failed")||lower.startsWith("permission denied")||lower.startsWith("conflict:")||lower.startsWith("not found:")||lower.startsWith("server error")||lower.startsWith("network error")||lower.startsWith("erp response")||lower.contains("unable to")||lower.contains("could not")||lower.contains(" failed")||lower.startsWith("failed") -> DseNoticeKind.ERROR
        lower.contains("connection interrupted")||lower.contains("offline")&&lower.contains("not sent")||lower.contains("required")||lower.contains("cannot")||lower.contains("refuse") -> DseNoticeKind.WARNING
        lower.startsWith("created ")||lower.startsWith("updated ")||lower.startsWith("saved ")||lower.startsWith("deleted")||lower.startsWith("cancelled")||lower.startsWith("approved")||lower.startsWith("rejected")||lower.startsWith("recorded")||lower.startsWith("sent")||lower.startsWith("attached")||lower.startsWith("duplicated")||lower.contains(" ready to share")||lower.contains(" successfully") -> DseNoticeKind.SUCCESS
        else -> null
    }
}

@Composable
internal fun DseNoticeDialog(message:String,kind:DseNoticeKind,onDismiss:()->Unit){
    val accent=when(kind){DseNoticeKind.SUCCESS->DseSuccess;DseNoticeKind.WARNING->DseWarning;DseNoticeKind.ERROR->DseDanger;DseNoticeKind.INFO->DseInfo}
    val icon=when(kind){DseNoticeKind.SUCCESS->Icons.Rounded.CheckCircle;DseNoticeKind.WARNING->Icons.Rounded.WarningAmber;DseNoticeKind.ERROR->Icons.Rounded.ErrorOutline;DseNoticeKind.INFO->Icons.Rounded.Info}
    val title=when(kind){DseNoticeKind.SUCCESS->"Completed";DseNoticeKind.WARNING->"Attention Required";DseNoticeKind.ERROR->"Unable to Complete Action";DseNoticeKind.INFO->"Information"}
    PremiumAlertDialog(
        onDismissRequest=onDismiss,
        title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,tint=accent);Text(title,fontWeight=FontWeight.ExtraBold,color=accent)}},
        text={Text(message,style=MaterialTheme.typography.bodyMedium)},
        confirmButton={PremiumPrimaryButton("Close",onDismiss,modifier=Modifier.widthIn(min=150.dp),leadingIcon=Icons.Rounded.Check)}
    )
}

@Composable
internal fun DseLoadingState(label:String="Loading ERP data…"){
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(.30f),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable
internal fun PremiumAlertDialog(
    onDismissRequest:()->Unit,
    title:@Composable ()->Unit,
    text:@Composable (() -> Unit)?=null,
    confirmButton:@Composable ()->Unit,
    dismissButton:@Composable (() -> Unit)?=null,
){
    Dialog(
        onDismissRequest=onDismissRequest,
        properties=DialogProperties(usePlatformDefaultWidth=false),
    ){
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()){
            val compact=maxWidth<700.dp
            val panelShape=if(compact) RoundedCornerShape(topStart=30.dp,topEnd=30.dp,bottomStart=18.dp,bottomEnd=18.dp) else RoundedCornerShape(30.dp)
            val alignment=if(compact)Alignment.BottomCenter else Alignment.Center
            val sidePadding=if(compact)8.dp else 20.dp
            val availableHeight=maxHeight
            val bodyMax=if(compact)availableHeight*.68f else 620.dp
            Box(Modifier.fillMaxSize().padding(horizontal=sidePadding,vertical=if(compact)8.dp else 18.dp),contentAlignment=alignment){
                Surface(
                    modifier=Modifier
                        .fillMaxWidth()
                        .widthIn(max=560.dp)
                        .heightIn(max=availableHeight*.94f)
                        .shadow(28.dp,panelShape,ambientColor=DseViolet.copy(.14f),spotColor=DseViolet.copy(.20f)),
                    shape=panelShape,
                    color=MaterialTheme.colorScheme.surface,
                    border=BorderStroke(1.dp,DseViolet.copy(.14f)),
                    tonalElevation=0.dp,
                ){
                    Column(Modifier.fillMaxWidth()){
                        Box(Modifier.fillMaxWidth().height(6.dp).background(DseBrandGradient))
                        if(compact)Box(Modifier.padding(top=8.dp).width(38.dp).height(4.dp).clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterHorizontally))
                        Row(
                            Modifier.fillMaxWidth().padding(start=20.dp,end=10.dp,top=10.dp,bottom=8.dp),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(10.dp),
                        ){
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(DseBrandGradient),contentAlignment=Alignment.Center){
                                Icon(Icons.Rounded.AutoAwesome,null,tint=Color.White,modifier=Modifier.size(20.dp))
                            }
                            Box(Modifier.weight(1f)){title()}
                            IconButton(onClick=onDismissRequest){Icon(Icons.Rounded.Close,"Close",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                        if(text!=null){
                            Box(Modifier.fillMaxWidth().heightIn(max=bodyMax).padding(horizontal=20.dp,vertical=8.dp)){text()}
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(.24f)).padding(horizontal=16.dp,vertical=13.dp),
                            horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.spacedBy(8.dp),
                        ){
                            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){confirmButton()}
                            if(dismissButton!=null)Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){dismissButton()}
                        }
                    }
                }
            }
        }
    }
}

data class PremiumActionSpec(
    val label:String,
    val onClick:()->Unit,
    val destructive:Boolean=false,
    val enabled:Boolean=true,
    val icon:ImageVector=semanticActionIcon(label),
    val disabledReason:String?=null,
)

@Composable
internal fun PremiumActionGrid(actions:List<PremiumActionSpec>){
    if(actions.isEmpty())return
    BoxWithConstraints(Modifier.fillMaxWidth()){
        val columns=if(maxWidth<430.dp)1 else 2
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
            actions.chunked(columns).forEach{chunk->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    chunk.forEach{action->
                        if(action.destructive) PremiumDangerButton(action.label,action.onClick,Modifier.weight(1f),action.enabled,action.icon)
                        else PremiumSecondaryButton(action.label,action.onClick,Modifier.weight(1f),action.enabled,action.icon)
                    }
                    repeat(columns-chunk.size){Spacer(Modifier.weight(1f))}
                }
            }
        }
    }
}


@Composable
internal fun DseRecordActionSheet(
    title:String,
    subtitle:String="Record actions",
    actions:List<PremiumActionSpec>,
    onDismiss:()->Unit,
){
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    ModalBottomSheet(
        onDismissRequest=onDismiss,
        sheetState=sheetState,
        shape=RoundedCornerShape(topStart=30.dp,topEnd=30.dp),
        containerColor=MaterialTheme.colorScheme.surface,
        dragHandle={BottomSheetDefaults.DragHandle(color=MaterialTheme.colorScheme.outline)},
    ){
        Column(
            Modifier.fillMaxWidth().heightIn(max=760.dp).verticalScroll(rememberScrollState()).padding(start=18.dp,end=18.dp,bottom=28.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp),
        ){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                PremiumIconTile(Icons.Rounded.TouchApp,MaterialTheme.colorScheme.primary,size=44.dp)
                Column(Modifier.weight(1f)){
                    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
                    Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=onDismiss){Icon(Icons.Rounded.Close,"Close")}
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            actions.forEach{action->
                val accent=when{
                    !action.enabled->MaterialTheme.colorScheme.onSurfaceVariant
                    action.destructive->DseDanger
                    else->MaterialTheme.colorScheme.primary
                }
                Surface(
                    shape=RoundedCornerShape(17.dp),
                    color=if(action.enabled)accent.copy(.055f) else MaterialTheme.colorScheme.surfaceVariant.copy(.45f),
                    border=BorderStroke(1.dp,accent.copy(if(action.enabled) 0.18f else 0.12f)),
                    modifier=Modifier.fillMaxWidth().clickable(enabled=action.enabled){onDismiss();action.onClick()},
                ){
                    Row(Modifier.padding(horizontal=12.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        PremiumIconTile(action.icon,accent,size=36.dp)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                            Text(action.label,fontWeight=FontWeight.Bold,color=accent)
                            if(!action.enabled&&!action.disabledReason.isNullOrBlank())Text(action.disabledReason,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if(action.enabled)Icons.Rounded.ChevronRight else Icons.Rounded.Lock,if(action.enabled)"Open" else "Unavailable",tint=accent)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActivityTimelineSheet(
    api:DseErpHttpClient,
    entityType:String,
    entityId:Int,
    reference:String,
    onDismiss:()->Unit,
){
    var rows by remember(entityType,entityId){mutableStateOf<List<ActivityRow>?>(null)}
    var message by remember(entityType,entityId){mutableStateOf("")}
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    LaunchedEffect(entityType,entityId){
        when(val r=api.activity(entityType,entityId)){
            is ApiResult.Success->rows=r.value
            else->{rows=emptyList();message=r.readableMessage()}
        }
    }
    ModalBottomSheet(
        onDismissRequest=onDismiss,
        sheetState=sheetState,
        shape=RoundedCornerShape(topStart=30.dp,topEnd=30.dp),
        containerColor=MaterialTheme.colorScheme.surface,
        dragHandle={BottomSheetDefaults.DragHandle(color=MaterialTheme.colorScheme.outline)},
    ){
        Column(Modifier.fillMaxWidth().heightIn(max=760.dp).verticalScroll(rememberScrollState()).padding(start=18.dp,end=18.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                PremiumIconTile(Icons.Rounded.Timeline,DseInfo,size=44.dp)
                Column(Modifier.weight(1f)){Text("Activity Timeline",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold);Text(reference,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(onClick=onDismiss){Icon(Icons.Rounded.Close,"Close")}
            }
            when{
                rows==null->LinearProgressIndicator(Modifier.fillMaxWidth())
                !message.isBlank()->Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                rows!!.isEmpty()->DseEmptyRegisterState("No activity recorded yet","Server audit history for this record is currently empty.",Icons.Rounded.History)
                else->rows!!.forEachIndexed{i,row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            PremiumIconTile(semanticActionIcon(row.action),if(i==0)MaterialTheme.colorScheme.primary else DseInfo,size=34.dp)
                            if(i<rows!!.lastIndex)Box(Modifier.width(2.dp).height(30.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(.36f),modifier=Modifier.weight(1f)){
                            Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                                Text(row.action.replace('_',' ').lowercase().replaceFirstChar{it.uppercase()},fontWeight=FontWeight.Bold)
                                if(row.detail.isNotBlank())Text(row.detail,style=MaterialTheme.typography.bodySmall)
                                Text(listOf(row.createdAt,row.createdBy).filter{it.isNotBlank()}.joinToString(" • "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailDialog(title:String,rows:List<Pair<String,String>>,actions:List<Pair<String,()->Unit>>,onDismiss:()->Unit){
    PremiumAlertDialog(
        onDismissRequest=onDismiss,
        title={Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            rows.filter{it.second.isNotBlank()}.forEach{(k,v)->
                val accent=semanticFieldAccent(k)
                Surface(shape=RoundedCornerShape(16.dp),color=accent.copy(.055f),border=BorderStroke(1.dp,accent.copy(.16f)),modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(horizontal=12.dp,vertical=11.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically){
                        PremiumIconTile(semanticFieldIcon(k),accent,size=32.dp)
                        Text(k,Modifier.widthIn(min=90.dp),style=MaterialTheme.typography.labelMedium,color=accent,fontWeight=FontWeight.Bold)
                        PremiumValueText(k,v,Modifier.weight(1f))
                    }
                }
            }
            if(actions.isNotEmpty()){
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    PremiumIconTile(Icons.Rounded.TouchApp,MaterialTheme.colorScheme.primary,size=36.dp)
                    Column{Text("Actions",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold);Text("Same business actions, optimized for mobile",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                PremiumActionGrid(actions.map{(label,fn)->PremiumActionSpec(label,fn,destructive=label.contains("delete",true)||label.contains("reject",true)||label.contains("cancel",true)||label.contains("void",true)||label.contains("remove",true))})
            }
        }},
        confirmButton={PremiumSecondaryButton("Close",onDismiss,modifier=Modifier.widthIn(min=160.dp),icon=Icons.Rounded.Close)}
    )
}

@Composable
internal fun ReasonDialog(title:String,label:String="Reason",onDismiss:()->Unit,onConfirm:(String)->Unit){
    var reason by remember{mutableStateOf("")}
    PremiumAlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={DseField(label,reason,required=true){reason=it}},confirmButton={PremiumPrimaryButton("Confirm",{onConfirm(reason.trim())},enabled=reason.isNotBlank(),leadingIcon=Icons.Rounded.Check)},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable
internal fun TextPromptDialog(title:String,label:String,initial:String="",onDismiss:()->Unit,onConfirm:(String)->Unit){
    var value by remember{mutableStateOf(initial)}
    PremiumAlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={DseField(label,value,singleLine=true,required=true,onValue={value=it})},confirmButton={PremiumPrimaryButton("Save",{onConfirm(value.trim())},enabled=value.isNotBlank(),leadingIcon=Icons.Rounded.Save)},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable
internal fun ConfirmDialog(title:String,message:String,confirmLabel:String="Confirm",danger:Boolean=false,onDismiss:()->Unit,onConfirm:()->Unit){
    val accent=if(danger)DseDanger else DseWarning
    val icon=if(danger)Icons.Rounded.WarningAmber else Icons.Rounded.HelpOutline
    PremiumAlertDialog(
        onDismissRequest=onDismiss,
        title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,tint=accent);Text(title,fontWeight=FontWeight.ExtraBold,color=accent)}},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(message);if(danger)Text("This action may change or remove ERP data.",style=MaterialTheme.typography.labelSmall,color=DseDanger,fontWeight=FontWeight.SemiBold)}},
        confirmButton={if(danger)PremiumDangerButton(confirmLabel,onConfirm,icon=Icons.Rounded.Delete)else PremiumPrimaryButton(confirmLabel,onConfirm,leadingIcon=Icons.Rounded.Check)},
        dismissButton={PremiumSecondaryButton("Cancel",onDismiss,icon=Icons.Rounded.Close)}
    )
}



internal fun ApiResult<*>.readableMessage():String=when(this){
    is ApiResult.Success<*>->"OK"
    is ApiResult.Unauthorized->"Authentication failed: $message"
    is ApiResult.Forbidden->"Permission denied: $message"
    is ApiResult.Conflict->"Conflict: this record changed elsewhere. Reload and try again. $message"
    is ApiResult.NotFound->"Not found: $message"
    is ApiResult.ServerError->"Server error $status: $message"
    is ApiResult.NetworkError->if(requestMayHaveReachedServer) "Connection interrupted after the request may have reached ERP. Verify the record before retrying. $message" else "Network error: $message"
    is ApiResult.DecodeError->"ERP response could not be read safely${status?.let{" (HTTP $it)"}.orEmpty()}: $message"
    is ApiResult.UnsafeEndpoint->message
    is ApiResult.NotImplemented->message
}

internal fun formatPercent(v:Double?):String{
    if(v==null||!v.isFinite())return "—"
    val scaled=(v*10.0).roundToLong()/10.0
    return if(scaled % 1.0 == 0.0) "${scaled.toLong()}%" else "$scaled%"
}

internal fun compactMoney(v:Double?,currency:String="INR"):String{
    if(v==null||!v.isFinite())return "—"
    val absValue=kotlin.math.abs(v)
    val unit=when(currency.trim().uppercase()){"INR"->"₹";"USD"->"$";"EUR"->"€";"GBP"->"£";else->currency.trim().uppercase().ifBlank{"₹"}}
    val sign=if(v<0)"-" else ""
    val text=when{
        absValue>=1_000_000_000->"%.2fB".formatPortable(absValue/1_000_000_000.0)
        absValue>=1_000_000->"%.2fM".formatPortable(absValue/1_000_000.0)
        absValue>=1_000->"%.1fK".formatPortable(absValue/1_000.0)
        else->absValue.roundToLong().toString()
    }
    return "$unit $sign$text"
}

private fun String.formatPortable(value:Double):String{
    val decimals=substringAfter("%.","0").substringBefore("f").toIntOrNull()?:0
    val scale=when(decimals){0->1L;1->10L;2->100L;else->1000L}
    val scaled=kotlin.math.round(value*scale).toLong()
    val whole=scaled/scale
    if(decimals==0)return whole.toString()
    val frac=kotlin.math.abs(scaled%scale).toString().padStart(decimals,'0')
    return "$whole.$frac"
}

internal fun money(v:Double?,currency:String="INR"):String{
    if(v==null||!v.isFinite())return "—"
    val neg=v<0;val scaled=(abs(v)*100).roundToLong();val whole=scaled/100;val minor=scaled%100;val digits=whole.toString()
    val grouped=if(digits.length<=3)digits else{val last=digits.takeLast(3);var lead=digits.dropLast(3);val groups=mutableListOf<String>();while(lead.length>2){groups.add(0,lead.takeLast(2));lead=lead.dropLast(2)};if(lead.isNotEmpty())groups.add(0,lead);groups.joinToString(",")+","+last}
    val unit=when(currency.trim().uppercase()){
        "INR"->"₹";"USD"->"$";"EUR"->"€";"GBP"->"£";"JPY"->"¥";"AED"->"AED";"SAR"->"SAR";else->currency.trim().uppercase().ifBlank{"₹"}
    }
    return "$unit ${if(neg)"-" else ""}$grouped.${minor.toString().padStart(2,'0')}"
}

private fun statusIcon(status:String):ImageVector{val s=status.uppercase();return when{
    s.contains("PAID")||s.contains("APPROVED")||s.contains("COMPLETED")||s.contains("DELIVERED")||s.contains("SENT")||s.contains("RECONCILED")->Icons.Rounded.CheckCircle
    s.contains("PENDING")||s.contains("DUE")->Icons.Rounded.Schedule
    s.contains("PARTIAL")||s.contains("IN PROGRESS")||s.contains("TRANSIT")||s.contains("SUGGESTED")->Icons.Rounded.Sync
    s.contains("CANCEL")||s.contains("REJECT")||s.contains("OVERDUE")||s.contains("FAILED")||s.contains("DELETE")->Icons.Rounded.Error
    else->Icons.Rounded.Info
}}
private fun statusTone(status:String):Pair<Color,Color>{val s=status.uppercase();return when{
    s.contains("PAID")||s.contains("APPROVED")||s.contains("COMPLETED")||s.contains("DELIVERED")||s.contains("SENT")||s.contains("RECONCILED")->Color(0xFFDCFCE7) to Color(0xFF166534)
    s.contains("PENDING")||s.contains("DUE")->Color(0xFFFEF3C7) to Color(0xFF92400E)
    s.contains("PARTIAL")||s.contains("IN PROGRESS")||s.contains("TRANSIT")||s.contains("SUGGESTED")->Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
    s.contains("CANCEL")||s.contains("REJECT")||s.contains("OVERDUE")||s.contains("FAILED")||s.contains("DELETE")->Color(0xFFFEE2E2) to Color(0xFFB91C1C)
    else->Color(0xFFE2E8F0) to Color(0xFF334155)
}}

internal object BusinessDateContext {
    private var serverDate:String=""
    private var serverZone:String=""
    fun update(date:String,zone:String){ if(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) serverDate=date; if(zone.isNotBlank()) serverZone=zone }
    fun today():String=serverDate.ifBlank{platformLocalDateIso()}
    fun zone():String=serverZone
}
internal fun todayIso():String=BusinessDateContext.today()
internal fun addDaysIso(date:String,days:Int):String{
    val parts=date.split('-');if(parts.size!=3)return date
    val y=parts[0].toIntOrNull()?:return date;val m=parts[1].toIntOrNull()?:return date;val d=parts[2].toIntOrNull()?:return date
    return civilFromDays(daysFromCivil(y,m,d)+days.toLong())
}
internal fun dueDate(invoiceDate:String,paymentTerm:String):String{
    if(invoiceDate.isBlank())return ""
    val days=Regex("(\\d+)").find(paymentTerm)?.groupValues?.getOrNull(1)?.toIntOrNull()?:0
    return addDaysIso(invoiceDate,days)
}
internal fun isoDateFromEpochMillis(ms:Long):String=civilFromDays(floorDiv(ms,86_400_000L))
private fun isoDateToEpochMillis(value:String):Long{
    val p=value.split('-');if(p.size!=3)return platformOfflineNowMillis()
    val y=p[0].toIntOrNull()?:return platformOfflineNowMillis();val m=p[1].toIntOrNull()?:return platformOfflineNowMillis();val d=p[2].toIntOrNull()?:return platformOfflineNowMillis()
    return daysFromCivil(y,m,d)*86_400_000L
}
private fun floorDiv(a:Long,b:Long):Long{var q=a/b;val r=a%b;if(r!=0L&&((r<0)!=(b<0)))q--;return q}
private fun civilFromDays(days:Long):String{
    var z=days+719468
    val era=if(z>=0)z/146097 else (z-146096)/146097
    val doe=z-era*146097
    val yoe=(doe-doe/1460+doe/36524-doe/146096)/365
    var y=(yoe+era*400).toInt()
    val doy=doe-(365*yoe+yoe/4-yoe/100)
    val mp=(5*doy+2)/153
    val d=(doy-(153*mp+2)/5+1).toInt()
    val m=(mp+(if(mp<10)3 else -9)).toInt()
    y+=if(m<=2)1 else 0
    return y.toString().padStart(4,'0')+"-"+m.toString().padStart(2,'0')+"-"+d.toString().padStart(2,'0')
}
private fun daysFromCivil(year:Int,month:Int,day:Int):Long{
    var y=year;if(month<=2)y--
    val era=if(y>=0)y/400 else (y-399)/400
    val yoe=y-era*400
    val mp=month+(if(month>2)-3 else 9)
    val doy=(153*mp+2)/5+day-1
    val doe=yoe*365+yoe/4-yoe/100+doy
    return era.toLong()*146097+doe-719468
}


internal fun urlDecode(value:String):String{
    if('%' !in value)return value
    val out=StringBuilder();var i=0
    while(i<value.length){
        if(value[i]=='%'&&i+2<value.length){val hex=value.substring(i+1,i+3);val n=hex.toIntOrNull(16);if(n!=null){out.append(n.toChar());i+=3;continue}}
        out.append(if(value[i]=='+')' ' else value[i]);i++
    }
    return out.toString()
}
internal fun paymentTermDefault(values:List<String>):String=values.firstOrNull{it.equals("15 Days",true)}?:values.firstOrNull().orEmpty()
internal fun lineTotal(quantity:Double,rate:Double,discountPercent:Double,gstPercent:Double):Double{val gross=quantity*rate;val discount=gross*discountPercent/100.0;val taxable=gross-discount;return taxable+taxable*gstPercent/100.0}
internal fun lineTaxable(quantity:Double,rate:Double,discountPercent:Double):Double{val gross=quantity*rate;return gross-(gross*discountPercent/100.0)}

@Composable
internal fun PagingControls(page:Int,totalPages:Int,onPage:(Int)->Unit)=PagingControls(page,totalPages,0,onPage)

@Composable
internal fun DetailDialog(title:String,onDismiss:()->Unit,content:@Composable ()->Unit){
    PremiumAlertDialog(onDismissRequest=onDismiss,title={Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)},text={Column(Modifier.heightIn(max=660.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){content()}},confirmButton={PremiumSecondaryButton("Close",onDismiss,modifier=Modifier.widthIn(min=160.dp),icon=Icons.Rounded.Close)})
}

@Composable
internal fun ConfirmDialog(title:String,message:String,onDismiss:()->Unit,onConfirm:()->Unit)=ConfirmDialog(title,message,"Confirm",false,onDismiss,onConfirm)

internal fun shareCsvFile(title:String,fileName:String,headers:List<String>,rows:List<List<String>>):Boolean{
    fun esc(v:String):String=if(v.any{it==','||it=='"'||it=='\n'||it=='\r'})"\""+v.replace("\"","\"\"")+"\"" else v
    val csv=buildString{
        append(headers.joinToString(","){esc(it)}).append('\n')
        rows.forEach{row->append(row.joinToString(","){esc(it)}).append('\n')}
    }
    return platformShareFile(title,fileName,csv.encodeToByteArray())
}
