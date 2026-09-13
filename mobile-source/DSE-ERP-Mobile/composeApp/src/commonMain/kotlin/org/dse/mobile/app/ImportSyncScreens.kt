@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package org.dse.mobile.app
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dse.mobile.core.api.*
import org.dse.mobile.core.config.MobileBuildInfo
import org.dse.mobile.core.model.*
import org.dse.mobile.core.offline.*
import kotlin.math.abs

private enum class ImportModule(val label:String){ITEMS("Item Master"),CUSTOMERS("Customers / CRM"),SUPPLIERS("Suppliers / HRM"),SALES("Sales"),PURCHASES("Purchases"),MASTER("Master Values"),PURCHASE_RECON("Purchase Recon"),BANK("Bank Statement")}
private enum class ImportStrategy(val label:String){CREATE_ONLY("Create only"),UPDATE_EXISTING("Create / Update"),SKIP_EXISTING("Create / Skip existing")}
private fun ImportModule.supportsStrategy()=this in setOf(ImportModule.ITEMS,ImportModule.CUSTOMERS,ImportModule.SUPPLIERS,ImportModule.MASTER)

@Composable internal fun SyncWorkspace(api:DseErpHttpClient){
 var status by remember{mutableStateOf(OfflineRepository.status())}
 var queue by remember{mutableStateOf(OfflineRepository.queue())}
 var msg by remember{mutableStateOf("Offline cache is a safe read-only fallback. Pending writes remain local until the server accepts them.")}
 var armed by remember{mutableStateOf<String?>(null)}
 var clearing by remember{mutableStateOf(false)}
 var busy by remember{mutableStateOf(false)}
 var widget by remember{mutableStateOf(OfflineRepository.widgetSharingEnabled())}
 val scope=rememberCoroutineScope()
 fun refresh(){status=OfflineRepository.status();queue=OfflineRepository.queue()}
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  PremiumCard(Modifier.fillMaxWidth(),padding=15.dp){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){PremiumIconTile(Icons.Rounded.Sync,DseSuccess,size=48.dp);Column(Modifier.weight(1f)){Text("Sync Center",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold);Text("Offline resilience, local queue and ${platformName()} system features",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){DseMetricTile("Cached Views",status.cachedEntries.toString(),Icons.Rounded.OfflineBolt,Modifier.weight(1f),DseInfo);DseMetricTile("Pending Local",status.pendingMutations.toString(),Icons.Rounded.Sync,Modifier.weight(1f),if(status.pendingMutations>0)DseWarning else DseSuccess)}
  DseSection("Offline Read Cache",Icons.Rounded.OfflineBolt){
   Text(status.lastUpdatedMillis?.let{"Latest cache: ${cacheAgeTools(it)}"}?:"No cache yet")
   Text("Cached registers show stale data when offline; server state remains authoritative.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   PremiumSecondaryButton(if(clearing)"Confirm Clear Cache" else "Clear Cache",{if(clearing){OfflineRepository.clearCache();clearing=false;refresh();msg="Offline read cache cleared"}else clearing=true},Modifier.fillMaxWidth(),icon=Icons.Rounded.DeleteSweep)
  }
  DseSection("Pending Local Sync",Icons.Rounded.Sync){
   Text("CREATE retries are manual because the current v10.0.5 contract does not expose an idempotency key for every write. This prevents silent duplicate documents.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(queue.isEmpty())Text("No pending local changes",color=DseSuccess,fontWeight=FontWeight.SemiBold) else queue.forEach{item->
    PremiumCard(Modifier.fillMaxWidth(),padding=11.dp){
     Text(item.label,fontWeight=FontWeight.Bold)
     Text("${item.kind} • ${item.state} • attempts ${item.attempts}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     if(item.lastError.isNotBlank())Text(item.lastError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      PremiumPrimaryButton(if(armed==item.id)"Confirm Retry" else "Retry",{if(armed!=item.id)armed=item.id else scope.launch{busy=true;msg=OfflineRepository.retry(item.id,api);armed=null;busy=false;refresh()}},Modifier.weight(1f),enabled=!busy,leadingIcon=Icons.Rounded.Sync)
      PremiumSecondaryButton("Discard",{OfflineRepository.remove(item.id);armed=null;refresh();msg="Discarded local pending item"},Modifier.weight(1f),enabled=!busy,icon=Icons.Rounded.DeleteOutline)
     }
    }
   }
  }
  DseSection("${platformName()} System Experience",if(platformName()=="Android") Icons.Rounded.PhoneAndroid else Icons.Rounded.PhoneIphone){
   Text(platformPushCapability(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   PremiumPrimaryButton("Enable Notifications",{scope.launch{val x=platformRequestPushNotifications();msg=if(x.granted)"${platformName()} notification permission enabled. Jasvi Industries v10.0.5 does not expose device-token registration, so remote ERP push delivery is not claimed by this build." else x.message}},Modifier.fillMaxWidth(),leadingIcon=Icons.Rounded.NotificationsActive)
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Switch(widget,{widget=it;OfflineRepository.setWidgetSharingEnabled(it);if(!it)platformPublishWidgetSnapshot(WidgetDashboardSnapshot(updatedAtMillis=platformOfflineNowMillis()))});Text("Share authenticated dashboard KPIs with ${platformName()} widget")}
   Text("Widgets and shortcuts are read-only presentation features and never alter business document state.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  if(msg.isNotBlank())Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer.copy(.45f),modifier=Modifier.fillMaxWidth()){Text(msg,Modifier.padding(11.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 }
}

@Composable internal fun DataImportWorkspace(api:DseErpHttpClient,username:String){
 var module by remember{mutableStateOf(ImportModule.ITEMS)}
 var sheet by remember{mutableStateOf<ImportSheet?>(null)}
 var mapping by remember{mutableStateOf<Map<String,String>>(emptyMap())}
 var strategy by remember{mutableStateOf(ImportStrategy.CREATE_ONLY)}
 var dry by remember{mutableStateOf(true)}
 var busy by remember{mutableStateOf(false)}
 var msg by remember{mutableStateOf("Choose a module and select a file. ${platformImportFormatHint()}")}
 var dismissedMessage by remember{mutableStateOf("")}
 var confirmImport by remember{mutableStateOf(false)}
 var summary by remember{mutableStateOf<ImportExecutionSummary?>(null)}
 var bankName by remember{mutableStateOf("")};var bankAccount by remember{mutableStateOf("")};var accountHolder by remember{mutableStateOf("")};var bankCurrency by remember{mutableStateOf("INR")};var from by remember{mutableStateOf("")};var to by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 fun mappedSheet():ImportSheet?=sheet?.let{applyImportMapping(it,module,mapping)}
 fun executeSelected(){
  val current=mappedSheet()?:return
  scope.launch{
   busy=true
   val validation=validateImport(current,module)
   if(validation.isNotEmpty()){
    summary=ImportExecutionSummary(current.rows.size,0,current.rows.size,validation.take(100))
    msg="Validation failed — no server writes performed"
   }else{
    summary=executeImport(api,module,current,username,dry,BankMeta(bankName,bankAccount,accountHolder,bankCurrency,from,to),strategy)
    msg=if(dry)"Dry run complete" else "Import complete successfully"
   }
   busy=false
  }
 }
 val notice=noticeKindFor(msg)
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
   PremiumIconTile(Icons.Rounded.UploadFile,DseWarning,size=34.dp)
   Column(Modifier.weight(1f)){Text("Data Import",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold);Text("Validate first, then import safely",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   DseStatus("",if(dry)"DRY RUN" else "WRITE")
  }
  DseSelect("Module",module.label,ImportModule.entries.map{it.label},required=true,onValue={v->module=ImportModule.entries.first{it.label==v};sheet=null;mapping=emptyMap();summary=null;strategy=ImportStrategy.CREATE_ONLY})
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   PremiumPrimaryButton("Select File",{scope.launch{sheet=pickImportSpreadsheet();mapping=sheet?.let{autoMapImport(it,module)}?:emptyMap();summary=null;msg=sheet?.let{"${it.fileName} • ${it.rows.size} row(s)${it.platformNotice.takeIf(String::isNotBlank)?.let{n->" • $n"}.orEmpty()}"}?:"No file selected"}},Modifier.weight(1f),enabled=!busy,leadingIcon=Icons.Rounded.UploadFile)
   PremiumSecondaryButton("Template",{shareImportTemplate(module)},Modifier.weight(1f),icon=Icons.Rounded.Download)
  }
  Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(.30f)),modifier=Modifier.fillMaxWidth()){
   Row(Modifier.padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Switch(!dry,{dry=!it})
    Column(Modifier.weight(1f)){Text(if(dry)"Dry Run" else "Write Enabled",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium);Text(if(dry)"Validate only — no ERP writes" else "Confirmation required before ERP writes",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   }
  }
  if(module.supportsStrategy())DseSelect("Existing Record Strategy",strategy.label,ImportStrategy.entries.map{it.label},onValue={v->strategy=ImportStrategy.entries.first{it.label==v}})
  sheet?.let{sht->
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
    PremiumIconTile(Icons.Rounded.Description,DseInfo,size=34.dp)
    Column(Modifier.weight(1f)){Text(sht.fileName,fontWeight=FontWeight.Bold,maxLines=1);Text("${sht.rows.size} rows • ${sht.headers.size} columns",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    TextButton(onClick={mapping=autoMapImport(sht,module)}){Text("Auto Map")}
   }
   DseSection("Column Mapping",Icons.Rounded.AccountTree){importFields(module).forEach{field->DseSelect(field.replace('_',' ').replaceFirstChar{it.uppercase()},mapping[field].orEmpty(),listOf("")+sht.headers,required=field in requiredImportFields(module),onValue={source->mapping=mapping.toMutableMap().also{if(source.isBlank())it.remove(field)else it[field]=source}})}}
   val preview=mappedSheet();if(preview!=null)DseSection("Preview",Icons.Rounded.Preview){preview.rows.take(5).forEachIndexed{i,row->Text("${i+1}. "+importFields(module).take(6).joinToString(" • "){f->"$f=${row[f].orEmpty()}"},style=MaterialTheme.typography.bodySmall)} }
  }
  if(module==ImportModule.BANK){DseSection("Bank Statement Metadata",Icons.Rounded.AccountBalance){DseField("Bank Name",bankName,singleLine=true,required=true,onValue={bankName=it});DseField("Bank Account",bankAccount,singleLine=true,required=true,onValue={bankAccount=it});DseField("Account Holder",accountHolder,singleLine=true,onValue={accountHolder=it});DseSelect("Currency",bankCurrency,listOf("INR","USD","EUR","GBP","JPY","AED","SAR"),required=true,onValue={bankCurrency=it});DseDateField("Statement From",from,onValue={from=it});DseDateField("Statement To",to,onValue={to=it})}}
  PremiumPrimaryButton(
   text=if(busy)"Processing…" else if(dry)"Validate / Dry Run" else "Import to ERP",
   enabled=!busy&&sheet?.rows?.isNotEmpty()==true,
   onClick={if(dry)executeSelected() else confirmImport=true},
   modifier=Modifier.fillMaxWidth(),leadingIcon=if(dry)Icons.Rounded.FactCheck else Icons.Rounded.UploadFile,trailingIcon=Icons.Rounded.ArrowForward,
  )
  if(busy)DseLoadingState(if(dry)"Validating import data…" else "Importing validated ERP records…")
  if(msg.isNotBlank()&&notice==null)Text(msg,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
  summary?.let{x->
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){DseMetricTile("Processed",x.processed.toString(),Icons.Rounded.Dataset,Modifier.weight(1f),DseInfo);DseMetricTile("Succeeded",x.succeeded.toString(),Icons.Rounded.CheckCircle,Modifier.weight(1f),DseSuccess);DseMetricTile("Failed",x.failed.toString(),Icons.Rounded.ErrorOutline,Modifier.weight(1f),if(x.failed>0)DseDanger else DseSuccess)}
   DseSection("Import Results",Icons.Rounded.FactCheck){x.messages.take(100).forEach{Text(it,style=MaterialTheme.typography.bodySmall)}}
  }
  Spacer(Modifier.height(4.dp))
 }
 if(notice!=null&&msg!=dismissedMessage)DseNoticeDialog(msg,notice){dismissedMessage=msg}
 if(confirmImport)ConfirmDialog(
  "Import to ERP",
  "Import ${sheet?.rows?.size?:0} row(s) for ${module.label}? Validation runs first, and only valid rows are submitted to the existing ERP APIs.",
  "Import",
  false,
  {confirmImport=false},
 ){confirmImport=false;executeSelected()}
}

private fun requiredImportFields(m:ImportModule):Set<String> = when(m){
 ImportModule.ITEMS->setOf("item_code","description")
 ImportModule.CUSTOMERS,ImportModule.SUPPLIERS->setOf("party_code","name")
 ImportModule.SALES,ImportModule.PURCHASES->setOf("invoice_date","party_code","item_code","quantity","rate")
 ImportModule.MASTER->setOf("value")
 ImportModule.PURCHASE_RECON->setOf("supplier_name","supplier_invoice_no","invoice_date","invoice_value")
 ImportModule.BANK->setOf("transaction_date","description")
}

private fun importFields(m:ImportModule):List<String> = when(m){
 ImportModule.ITEMS->listOf("item_code","description","category","brand","material","size","unit","hsn","gst","discount_percent","purchase_price","selling_price","opening_stock","minimum_stock","location","remarks","active")
 ImportModule.CUSTOMERS,ImportModule.SUPPLIERS->listOf("party_code","name","contact_person","phone","email","gstin","address","opening_balance","active")
 ImportModule.SALES,ImportModule.PURCHASES->listOf("invoice_no","invoice_date","party_code","item_code","quantity","rate","discount_percent","gst_percent","payment_terms","gst_type","remarks")
 ImportModule.MASTER->listOf("lookup_type","category_code","value_code","value","description","display_order","active")
 ImportModule.PURCHASE_RECON->listOf("supplier_name","supplier_gstin","supplier_invoice_no","invoice_date","taxable_value","cgst","sgst","igst","invoice_value")
 ImportModule.BANK->listOf("transaction_timestamp","transaction_date","value_date","description","reference","debit","credit","amount","direction","balance")
}
private val importAliases=mapOf("item_code" to listOf("item","sku","code"),"party_code" to listOf("customer_code","supplier_code","party","code"),"name" to listOf("customer","supplier","party_name"),"invoice_date" to listOf("date","document_date"),"item_code" to listOf("item","sku","product_code"),"quantity" to listOf("qty"),"rate" to listOf("price","unit_price"),"gst_percent" to listOf("gst","tax","tax_percent"),"transaction_date" to listOf("date","txn_date"),"description" to listOf("narration","details"),"reference" to listOf("ref","reference_no"),"value" to listOf("lookup_value","master_value"))
private fun autoMapImport(s:ImportSheet,m:ImportModule):Map<String,String>{val headers=s.headers.associateBy{it.lowercase()};return importFields(m).mapNotNull{field->val source=headers[field]?:importAliases[field].orEmpty().firstNotNullOfOrNull{headers[it]};source?.let{field to it}}.toMap()}
private fun applyImportMapping(s:ImportSheet,m:ImportModule,mapping:Map<String,String>):ImportSheet{val fields=importFields(m);val map=if(mapping.isEmpty())autoMapImport(s,m)else mapping;val rows=s.rows.map{row->linkedMapOf<String,String>().also{out->fields.forEach{field->out[field]=map[field]?.let{row[it]}.orEmpty()}}};return s.copy(headers=fields,rows=rows)}
private fun shareImportTemplate(m:ImportModule){val headers=importFields(m);val sample=headers.associateWith{field->when(field){"active"->"true";"invoice_date","transaction_date","value_date"->"2026-08-28";"quantity"->"1";"rate","selling_price","purchase_price","opening_balance","invoice_value","taxable_value"->"0";else->""}};val csv=headers.joinToString(",")+"\n"+headers.joinToString(","){csvCell(sample[it].orEmpty())}+"\n";platformShareFile("${m.label} Import Template","Jasvi_${m.name}_Import_Template.csv",csv.encodeToByteArray())}
private fun csvCell(v:String):String=if(v.any{it==','||it=='"'||it=='\n'||it=='\r'})"\""+v.replace("\"","\"\"")+"\"" else v

private data class BankMeta(val bankName:String,val bankAccount:String,val holder:String,val currency:String,val from:String,val to:String)
private fun validateImport(s: ImportSheet, m: ImportModule): List<String> {
    val errors = mutableListOf<String>()
    val normalizedHeaders = s.headers.map { it.trim().lowercase() }
    normalizedHeaders.groupingBy { it }.eachCount().filter { it.key.isNotBlank() && it.value > 1 }.keys.forEach {
        errors += "Duplicate column after normalization: $it"
    }
    if (s.rows.size > 50_000) errors += "Import contains ${s.rows.size} rows; maximum supported mobile import is 50,000 rows."
    if (s.rawCsv.length > 20 * 1024 * 1024) errors += "Import text exceeds the 20 MB mobile safety limit."

    fun requiredColumns(vararg names: String) {
        names.forEach { name -> if (name !in s.headers) errors += "Missing required column: $name" }
    }
    val requiredValues = when (m) {
        ImportModule.ITEMS -> listOf("item_code", "description")
        ImportModule.CUSTOMERS, ImportModule.SUPPLIERS -> listOf("party_code", "name")
        ImportModule.SALES, ImportModule.PURCHASES -> listOf("invoice_date", "party_code", "item_code", "quantity", "rate")
        ImportModule.MASTER -> listOf("value")
        ImportModule.PURCHASE_RECON -> listOf("supplier_name", "supplier_invoice_no", "invoice_date", "invoice_value")
        ImportModule.BANK -> listOf("transaction_date", "description")
    }
    requiredColumns(*requiredValues.toTypedArray())

    val numericFields = setOf(
        "quantity", "rate", "discount_percent", "gst_percent", "gst", "purchase_price", "selling_price",
        "opening_stock", "minimum_stock", "opening_balance", "invoice_value", "taxable_value", "cgst", "sgst",
        "igst", "debit", "credit", "balance", "amount"
    )
    val percentFields = setOf("discount_percent", "gst_percent", "gst")
    val nonNegativeFields = setOf(
        "rate", "purchase_price", "selling_price", "opening_stock", "minimum_stock", "invoice_value", "taxable_value",
        "cgst", "sgst", "igst", "debit", "credit"
    )
    val dateFields = setOf("invoice_date", "transaction_date", "value_date")
    val booleanFields = setOf("active")
    val integerFields = setOf("display_order")

    s.rows.forEachIndexed { index, row ->
        val rowNo = index + 2
        requiredValues.forEach { key -> if (row.str(key).isBlank()) errors += "Row $rowNo: $key is required" }

        numericFields.forEach { key ->
            val raw = row.str(key)
            if (raw.isNotBlank()) {
                val value = raw.replace(",", "").toDoubleOrNull()
                if (value == null) errors += "Row $rowNo: $key is not numeric: $raw"
                else {
                    if (key in nonNegativeFields && value < 0.0) errors += "Row $rowNo: $key cannot be negative"
                    if (key in percentFields && value !in 0.0..100.0) errors += "Row $rowNo: $key must be between 0 and 100"
                }
            }
        }
        if (m == ImportModule.SALES || m == ImportModule.PURCHASES) {
            row.str("quantity").replace(",", "").toDoubleOrNull()?.let { if (it <= 0.0) errors += "Row $rowNo: quantity must be greater than zero" }
        }
        integerFields.forEach { key ->
            val raw = row.str(key)
            if (raw.isNotBlank() && raw.replace(",", "").toIntOrNull() == null) errors += "Row $rowNo: $key is not a whole number: $raw"
        }
        booleanFields.forEach { key ->
            val raw = row.str(key)
            if (raw.isNotBlank() && !validBooleanToken(raw)) errors += "Row $rowNo: $key must be true/false, yes/no, 1/0, active/inactive"
        }
        dateFields.forEach { key ->
            val raw = row.str(key)
            if (raw.isNotBlank() && !isIsoDate(raw)) errors += "Row $rowNo: $key must be a valid YYYY-MM-DD date"
        }
        if (m == ImportModule.BANK) {
            val debit = row.numOrNull("debit") ?: 0.0
            val credit = row.numOrNull("credit") ?: 0.0
            val amount = row.numOrNull("amount") ?: 0.0
            if (debit > 0.0 && credit > 0.0) errors += "Row $rowNo: debit and credit cannot both be greater than zero"
            if (debit == 0.0 && credit == 0.0 && amount == 0.0) errors += "Row $rowNo: provide a non-zero debit, credit or amount"
        }
    }
    return errors.distinct()
}


private suspend fun executeImport(api:DseErpHttpClient,m:ImportModule,s:ImportSheet,user:String,dry:Boolean,bank:BankMeta,strategy:ImportStrategy):ImportExecutionSummary=when(m){ImportModule.ITEMS->importItems(api,s,dry,strategy);ImportModule.CUSTOMERS->importParties(api,s,"CUSTOMER",dry,strategy);ImportModule.SUPPLIERS->importParties(api,s,"SUPPLIER",dry,strategy);ImportModule.SALES->importDocs(api,s,true,dry);ImportModule.PURCHASES->importDocs(api,s,false,dry);ImportModule.MASTER->importLookups(api,s,dry,strategy);ImportModule.PURCHASE_RECON->importRecon(api,s,dry);ImportModule.BANK->importBank(api,s,user,dry,bank)}
private suspend fun importItems(api:DseErpHttpClient,s:ImportSheet,dry:Boolean,strategy:ImportStrategy):ImportExecutionSummary{
 if(dry)return ImportExecutionSummary(s.rows.size,s.rows.size,0,listOf("Item validation passed • ${strategy.label}"));var ok=0;val messages=mutableListOf<String>()
 for((i,r) in s.rows.withIndex()){
  val item=MasterItem(itemCode=r.str("item_code"),description=r.str("description"),category=r.opt("category"),brand=r.opt("brand"),material=r.opt("material"),size=r.opt("size"),unit=r.opt("unit"),hsn=r.opt("hsn"),gst=r.num("gst"),discountPercent=r.num("discount_percent"),purchasePrice=r.num("purchase_price"),sellingPrice=r.num("selling_price"),openingStock=r.num("opening_stock"),minimumStock=r.num("minimum_stock"),location=r.opt("location"),remarks=r.opt("remarks"),active=r.boolStrict("active",true))
  val existing=(api.searchItems(item.itemCode,50) as? ApiResult.Success)?.value?.firstOrNull{it.itemCode.equals(item.itemCode,true)}
  if(existing!=null&&strategy==ImportStrategy.SKIP_EXISTING){ok++;messages+="${item.itemCode}: skipped existing";continue}
  val x=if(existing!=null&&strategy==ImportStrategy.UPDATE_EXISTING)api.updateItem(item.copy(id=existing.id,rowVersion=existing.rowVersion,reservedStock=existing.reservedStock))else if(existing!=null)ApiResult.ServerError(400,"Item ${item.itemCode} already exists")else api.createItem(item)
  when(x){is ApiResult.Success->{ok++;messages+="${item.itemCode}: ${if(existing!=null)"updated" else "created"}"};else->messages+="Row ${i+2}: ${x.readableMessage()}"}
 }
 return ImportExecutionSummary(s.rows.size,ok,s.rows.size-ok,messages)
}
private suspend fun importParties(api:DseErpHttpClient,s:ImportSheet,type:String,dry:Boolean,strategy:ImportStrategy):ImportExecutionSummary{
 if(dry)return ImportExecutionSummary(s.rows.size,s.rows.size,0,listOf("$type validation passed • ${strategy.label}"));var ok=0;val messages=mutableListOf<String>()
 for((i,r) in s.rows.withIndex()){
  val p=MasterParty(partyType=type,partyCode=r.str("party_code"),name=r.str("name"),contactPerson=r.opt("contact_person"),phone=r.opt("phone"),email=r.opt("email"),gstin=r.opt("gstin"),address=r.opt("address"),openingBalance=r.num("opening_balance"),active=r.boolStrict("active",true))
  val existing=(api.searchParties(type,p.partyCode,50) as? ApiResult.Success)?.value?.firstOrNull{it.partyCode.equals(p.partyCode,true)}
  if(existing!=null&&strategy==ImportStrategy.SKIP_EXISTING){ok++;messages+="${p.partyCode}: skipped existing";continue}
  val x=if(existing!=null&&strategy==ImportStrategy.UPDATE_EXISTING)api.updateParty(p.copy(id=existing.id,rowVersion=existing.rowVersion))else if(existing!=null)ApiResult.ServerError(400,"$type ${p.partyCode} already exists")else api.createParty(p)
  when(x){is ApiResult.Success->{ok++;messages+="${p.partyCode}: ${if(existing!=null)"updated" else "created"}"};else->messages+="Row ${i+2}: ${x.readableMessage()}"}
 }
 return ImportExecutionSummary(s.rows.size,ok,s.rows.size-ok,messages)
}

private suspend fun importDocs(api: DseErpHttpClient, s: ImportSheet, sales: Boolean, dry: Boolean): ImportExecutionSummary {
    val keyedRows = s.rows.mapIndexed { index, row -> (row.opt("invoice_no") ?: "ROW-${index + 2}") to row }
    val groups = keyedRows.groupBy({ it.first }, { it.second })
    if (dry) return ImportExecutionSummary(groups.size, groups.size, 0, listOf("${if (sales) "Sales" else "Purchase"} validation passed; numbering/lifecycle remain server-controlled"))
    val boot = (api.salesEntryBootstrap() as? ApiResult.Success)?.value
    val defaultTerm = paymentTermDefault(boot?.paymentTerms.orEmpty())
    var ok = 0
    val messages = mutableListOf<String>()
    for ((ref, rows) in groups) {
        val first = rows.first()
        val partyType = if (sales) "CUSTOMER" else "SUPPLIER"
        val code = first.str("party_code")
        val parties = (api.searchParties(partyType, code, 50) as? ApiResult.Success)?.value.orEmpty()
        val party = parties.firstOrNull { it.partyCode.equals(code, true) }
        if (party == null) { messages += "$ref: $partyType $code not found"; continue }
        val lines = mutableListOf<DocumentLine>()
        var bad: String? = null
        for (row in rows) {
            val itemCode = row.str("item_code")
            val item = (api.searchItems(itemCode, 50) as? ApiResult.Success)?.value?.firstOrNull { it.itemCode.equals(itemCode, true) }
            if (item == null) { bad = "Item $itemCode not found"; break }
            val qty = row.num("quantity")
            val rate = row.num("rate")
            val discount = row.num("discount_percent")
            val gst = row.num("gst_percent")
            lines += DocumentLine(itemCode, item.description, item.hsn, item.unit, item.remarks, qty, rate, discount, qty * rate * discount / 100.0, gst, lineTotal(qty, rate, discount, gst))
        }
        if (bad != null) { messages += "$ref: $bad"; continue }
        val partyRef = PartyRef(party.id, party.partyCode, party.name, party.email, party.phone, party.gstin, party.address)
        val date = first.str("invoice_date")
        val term = first.opt("payment_terms") ?: defaultTerm
        val result: ApiResult<*> = if (sales) {
            api.createSale(SaleRecord(
                invoiceNo = first.opt("invoice_no").orEmpty(), invoiceDate = date, customer = partyRef,
                source = if (first.opt("invoice_no").isNullOrBlank()) "MOBILE IMPORT" else "IMPORT",
                paymentTerms = term, dueDate = dueDate(date, term), billingAddress = party.address, deliveryAddress = party.address,
                billingGstin = party.gstin, deliveryGstin = party.gstin, sameAsBilling = true,
                gstType = first.opt("gst_type") ?: boot?.gstTypes?.firstOrNull(), remarks = first.opt("remarks"), lines = lines
            ))
        } else {
            val due = dueDate(date, term)
            api.createPurchase(PurchaseRecord(
                invoiceDate = date, supplier = partyRef, paymentTerms = term, dueDate = due, deliveryDate = due,
                billingAddress = party.address, deliveryAddress = party.address, billingGstin = party.gstin, deliveryGstin = party.gstin,
                sameAsBilling = true, gstType = first.opt("gst_type") ?: boot?.gstTypes?.firstOrNull(), notes = first.opt("remarks"),
                currency = "INR - Indian Rupee", warehouse = "Main Warehouse", gstTreatment = "Business Purchase", discountType = "Item Level", lines = lines
            ))
        }
        when (result) {
            is ApiResult.Success<*> -> { ok++; messages += "$ref: imported" }
            else -> messages += "$ref: ${result.readableMessage()}"
        }
    }
    return ImportExecutionSummary(groups.size, ok, groups.size - ok, messages)
}

private suspend fun importLookups(api:DseErpHttpClient,s:ImportSheet,dry:Boolean,strategy:ImportStrategy):ImportExecutionSummary{
 if(dry)return ImportExecutionSummary(s.rows.size,s.rows.size,0,listOf("Lookup validation passed • ${strategy.label}"));var ok=0;val messages=mutableListOf<String>()
 for((i,r) in s.rows.withIndex()){
  val type=r.opt("lookup_type")?:r.opt("category_code")?:"MOBILE_IMPORT";val value=r.str("value");var code=r.opt("value_code").orEmpty()
  val existingValues=(api.lookupsByCode(type) as? ApiResult.Success)?.value.orEmpty();val existing=existingValues.firstOrNull{(code.isNotBlank()&&it.lookupCode.equals(code,true))||it.lookupValue.equals(value,true)}
  if(existing!=null&&strategy==ImportStrategy.SKIP_EXISTING){ok++;messages+="$type / $value: skipped existing";continue}
  if(code.isBlank())code=if(existing!=null)existing.lookupCode else (api.nextLookupCode(type) as? ApiResult.Success)?.value?.code.orEmpty().ifBlank{value.uppercase().replace(' ','_')}
  val dto=LookupImportDto(id=existing?.id,lookupType=type,lookupCode=code,lookupValue=value,description=r.opt("description"),displayOrder=r.intStrict("display_order",existing?.displayOrder?:0),active=r.boolStrict("active",existing?.active?:true),rowVersion=existing?.rowVersion?:0)
  val x=if(existing!=null&&strategy==ImportStrategy.UPDATE_EXISTING)api.updateLookup(dto)else if(existing!=null)ApiResult.ServerError(400,"Master value $type / $code already exists")else api.createLookup(dto)
  when(x){is ApiResult.Success->{ok++;messages+="$type / $value: ${if(existing!=null)"updated" else "created"}"};else->messages+="Row ${i+2}: ${x.readableMessage()}"}
 }
 return ImportExecutionSummary(s.rows.size,ok,s.rows.size-ok,messages)
}
private suspend fun importRecon(api:DseErpHttpClient,s:ImportSheet,dry:Boolean):ImportExecutionSummary{val req=PurchaseReconImportRequest(s.fileName,fingerprint(s),"Jasvi Mobile ${MobileBuildInfo.MOBILE_VERSION}",dry,s.rows.mapIndexed{i,r->PurchaseReconImportRow(s.sheetName,i+2,r.str("supplier_name"),r.opt("supplier_gstin").orEmpty(),r.str("supplier_invoice_no"),r.str("invoice_date"),r.num("taxable_value"),r.num("cgst"),r.num("sgst"),r.num("igst"),r.num("invoice_value"))});return when(val x=api.importPurchaseRecon(req)){is ApiResult.Success->{val v=x.value;ImportExecutionSummary(v.totalRows,v.importedRows+v.updatedRows+v.alreadyCurrentRows,v.conflictRows+v.ignoredRows,v.details.take(50).map{"${it.status} ${it.invoiceNo}: ${it.message}"})};else->ImportExecutionSummary(s.rows.size,0,s.rows.size,listOf(x.readableMessage()))}}
private suspend fun importBank(api:DseErpHttpClient,s:ImportSheet,user:String,dry:Boolean,b:BankMeta):ImportExecutionSummary{if(b.bankName.isBlank()||b.bankAccount.isBlank())return ImportExecutionSummary(s.rows.size,0,s.rows.size,listOf("Bank name and account are required"));val rows=s.rows.mapIndexed{i,r->val amount=r.num("amount");val dir=r.opt("direction").orEmpty().uppercase();val debit=if(r.containsKey("debit"))r.num("debit")else if(dir.startsWith("D")||amount<0)abs(amount)else 0.0;val credit=if(r.containsKey("credit"))r.num("credit")else if(dir.startsWith("C")||amount>0)abs(amount)else 0.0;BankImportRow(i+2,r.opt("transaction_timestamp").orEmpty(),r.str("transaction_date"),r.opt("value_date").orEmpty(),r.str("description"),r.opt("reference").orEmpty(),debit,credit,r.num("balance"),rowHash(r,i))};val from=b.from.ifBlank{rows.map{it.transactionDate}.filter{it.isNotBlank()}.minOrNull().orEmpty()};val to=b.to.ifBlank{rows.map{it.transactionDate}.filter{it.isNotBlank()}.maxOrNull().orEmpty()};val req=BankImportRequest(b.bankName,b.bankAccount,b.holder,from,to,b.currency,null,null,fingerprint(s),s.fileName,s.rawCsv,user,dry,rows);return when(val x=api.importBankStatement(req)){is ApiResult.Success->{val v=x.value;ImportExecutionSummary(rows.size,v.importedRows,v.duplicateRows,listOf(if(v.alreadyImported)"Statement already imported" else "Bank statement ${if(dry)"validated" else "imported"}"))};else->ImportExecutionSummary(rows.size,0,rows.size,listOf(x.readableMessage()))}}

private fun Map<String, String>.str(k: String) = get(k).orEmpty().trim()
private fun Map<String, String>.opt(k: String) = str(k).takeIf { it.isNotBlank() }
private fun Map<String, String>.numOrNull(k: String) = str(k).replace(",", "").takeIf { it.isNotBlank() }?.toDoubleOrNull()
private fun Map<String, String>.num(k: String) = numOrNull(k) ?: 0.0
private fun Map<String, String>.intStrict(k: String, d: Int) = str(k).replace(",", "").takeIf { it.isNotBlank() }?.toIntOrNull() ?: d
private fun Map<String, String>.boolStrict(k: String, d: Boolean) = when (str(k).lowercase()) {
    "" -> d
    "1", "true", "yes", "y", "active" -> true
    "0", "false", "no", "n", "inactive" -> false
    else -> d
}
private fun validBooleanToken(v: String) = v.trim().lowercase() in setOf("1", "true", "yes", "y", "active", "0", "false", "no", "n", "inactive")
private fun isIsoDate(value: String): Boolean {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(value) ?: return false
    val year = m.groupValues[1].toIntOrNull() ?: return false
    val month = m.groupValues[2].toIntOrNull() ?: return false
    val day = m.groupValues[3].toIntOrNull() ?: return false
    if (month !in 1..12) return false
    val leap = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
    val maxDay = when (month) { 2 -> if (leap) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
    return day in 1..maxDay
}

private fun fingerprint(s:ImportSheet)=hash(s.fileName+"|"+s.rows.joinToString("|"){it.entries.joinToString(";"){e->"${e.key}=${e.value}"}});private fun rowHash(r:Map<String,String>,i:Int)=hash("$i|"+r.entries.joinToString(";"){"${it.key}=${it.value}"});private fun hash(v:String):String{var h=0xcbf29ce484222325UL;v.encodeToByteArray().forEach{b->h=(h xor b.toUByte().toULong())*0x100000001b3UL};return h.toString(16)}
private fun cacheAgeTools(last:Long):String{val m=((platformOfflineNowMillis()-last).coerceAtLeast(0)/60_000);return when{m<1->"just now";m<60->"$m min ago";m<1440->"${m/60} hr ago";else->"${m/1440} day(s) ago"}}
