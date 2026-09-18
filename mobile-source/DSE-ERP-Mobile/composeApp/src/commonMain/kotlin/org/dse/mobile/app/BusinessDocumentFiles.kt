package org.dse.mobile.app

import org.dse.mobile.core.model.*

/**
 * Canonical mobile business-document payload.
 *
 * This intentionally mirrors the information used by the desktop invoice/document
 * flows instead of serialising a mobile screen. PDF, Excel, share and email all use
 * this same payload so recipients get a real business document.
 */
internal data class BusinessDocumentPayload(
    val documentType:String,
    val number:String,
    val date:String,
    val partyLabel:String,
    val partyName:String,
    val partyGstin:String="",
    val billingAddress:String="",
    val billingGstin:String="",
    val deliveryAddress:String="",
    val deliveryGstin:String="",
    val currency:String="INR",
    val subtotal:Double=0.0,
    val discount:Double=0.0,
    val gst:Double=0.0,
    val charges:List<DocumentCharge> = emptyList(),
    val total:Double=0.0,
    val paid:Double=0.0,
    val outstanding:Double=0.0,
    val documentStatus:String="",
    val paymentStatus:String="",
    val reference:String="",
    val orderNo:String="",
    val poDate:String="",
    val dueDate:String="",
    val paymentTerms:String="",
    val invoiceType:String="",
    val salesperson:String="",
    val warehouse:String="",
    val deliveryDate:String="",
    val gstTreatment:String="",
    val transporter:String="",
    val transporterGstin:String="",
    val vehicleNumber:String="",
    val contactPerson:String="",
    val contactMobile:String="",
    val notes:String="",
    val remarks:String="",
    val lines:List<DocumentLine> = emptyList(),
)

internal fun SaleRecord.businessDocument()=BusinessDocumentPayload(
    documentType="Sales Invoice",number=invoiceNo,date=invoiceDate,partyLabel="Customer",partyName=customer?.name.orEmpty(),
    partyGstin=customer?.gstin.orEmpty().ifBlank{gstin.orEmpty()},billingAddress=billingAddress.orEmpty(),billingGstin=billingGstin.orEmpty(),
    deliveryAddress=deliveryAddress.orEmpty(),deliveryGstin=deliveryGstin.orEmpty(),subtotal=subtotal,discount=discountAmount,gst=gstAmount,
    charges=charges,total=totalAmount,paid=paidAmount,outstanding=(totalAmount-paidAmount).coerceAtLeast(0.0),documentStatus=documentStatus.orEmpty(),
    paymentStatus=paymentStatus.orEmpty(),reference=referenceNo.orEmpty(),orderNo=orderNo.orEmpty(),poDate=poDate.orEmpty(),dueDate=dueDate.orEmpty(),
    paymentTerms=paymentTerms.orEmpty(),invoiceType=invoiceType.orEmpty(),salesperson=salesperson.orEmpty(),gstTreatment=gstType.orEmpty(),
    transporter=transporter.orEmpty(),transporterGstin=transporterGstin.orEmpty(),vehicleNumber=vehicleNumber.orEmpty(),contactPerson=contactPerson.orEmpty(),
    contactMobile=contactPersonMobile.orEmpty(),notes=notes.orEmpty(),remarks=remarks.orEmpty(),lines=lines,
)

internal fun PurchaseRecord.businessDocument()=BusinessDocumentPayload(
    documentType="Purchase Invoice",number=invoiceNo,date=invoiceDate,partyLabel="Supplier",partyName=supplier?.name.orEmpty(),
    partyGstin=supplier?.gstin.orEmpty(),billingAddress=billingAddress.orEmpty(),billingGstin=billingGstin.orEmpty(),deliveryAddress=deliveryAddress.orEmpty(),
    deliveryGstin=deliveryGstin.orEmpty(),currency=currency?.substringBefore(' ')?.ifBlank{"INR"}?:"INR",subtotal=subtotal,discount=discountAmount,
    gst=gstAmount,charges=charges,total=totalAmount,paid=paidAmount,outstanding=(totalAmount-paidAmount).coerceAtLeast(0.0),documentStatus=documentStatus.orEmpty(),
    paymentStatus=paymentStatus.orEmpty(),reference=referenceNo.orEmpty(),orderNo=orderNo.orEmpty(),poDate=poDate.orEmpty(),dueDate=dueDate.orEmpty(),
    paymentTerms=paymentTerms.orEmpty(),warehouse=warehouse.orEmpty(),deliveryDate=deliveryDate.orEmpty(),gstTreatment=gstTreatment.orEmpty().ifBlank{gstType.orEmpty()},
    transporter=transporter.orEmpty(),transporterGstin=transporterGstin.orEmpty(),vehicleNumber=vehicleNumber.orEmpty(),contactPerson=contactPerson.orEmpty(),
    contactMobile=contactPersonMobile.orEmpty(),notes=notes.orEmpty(),remarks=remarks.orEmpty(),lines=lines,
)

internal fun quotationBusinessDocument(record:QuotationRecord,lines:List<QuotationLine>)=BusinessDocumentPayload(
    documentType="Quotation",number=record.no,date=record.date,partyLabel="Customer",partyName=record.customer,partyGstin=record.gstin.orEmpty(),
    total=record.amount,discount=record.discount,documentStatus=record.status.orEmpty(),reference=record.source.orEmpty(),
    lines=lines.map{DocumentLine(itemCode=it.code,itemDescription=it.description,quantity=it.quantity,rate=it.rate,discountPercent=it.discount,gstPercent=it.gst,totalAmount=it.total)}
)

internal fun returnBusinessDocument(record:ReturnDetails)=BusinessDocumentPayload(
    documentType=if(record.type.contains("PURCHASE",true))"Purchase Return" else "Sales Return",number=record.no,date=record.date,
    partyLabel=if(record.type.contains("PURCHASE",true))"Supplier" else "Customer",partyName=record.party,total=record.total,paid=record.refund,
    outstanding=(record.total-record.refund).coerceAtLeast(0.0),documentStatus=record.status,paymentStatus=record.refundStatus,reference=record.invoice,
    lines=record.lines.map{DocumentLine(itemCode=it.code,itemDescription=it.name,itemUnit=it.unit,quantity=it.quantity,rate=it.rate,gstPercent=it.tax,totalAmount=it.amount,itemRemarks=it.reason)}
)

private fun moneyNumber(v:Double):String {
    val neg=v<0
    val scaled=kotlin.math.round(kotlin.math.abs(v)*100.0).toLong()
    val whole=scaled/100
    val minor=scaled%100
    return (if(neg)"-" else "")+whole.toString()+"."+minor.toString().padStart(2,'0')
}
private fun plainMoney(v:Double,currency:String):String = "${currency.ifBlank{"INR"}} ${moneyNumber(v)}"
private fun safeText(s:String)=s.replace('\r',' ').replace('\n',' ').replace('\t',' ').trim().replace(Regex("\\s+")," ")
private fun pdfAscii(s:String)=safeText(s).replace('₹',' ').map{if(it.code in 32..126)it else ' '}.joinToString("").replace(Regex("\\s+")," ").trim()
private fun pdfEscape(s:String)=pdfAscii(s).replace("\\","\\\\").replace("(","\\(").replace(")","\\)")
private fun fitText(value:String,maxChars:Int):String{val v=safeText(value);return if(v.length<=maxChars)v else v.take((maxChars-1).coerceAtLeast(1))+"…"}
private fun splitText(value:String,maxChars:Int,maxLines:Int=2):List<String>{
    val source=safeText(value);if(source.isBlank())return emptyList()
    val words=source.split(' ');val out=mutableListOf<String>();var line=""
    for(w in words){val candidate=if(line.isBlank())w else "$line $w";if(candidate.length<=maxChars)line=candidate else{if(line.isNotBlank())out+=line;line=w;if(out.size>=maxLines-1)break}}
    if(line.isNotBlank()&&out.size<maxLines)out+=line
    if(out.isNotEmpty()&&source.length>out.joinToString(" ").length)out[out.lastIndex]=fitText(out.last(),maxChars)
    return out
}

private data class PdfPage(val commands:String)
private class PdfCanvasBuilder{
    private val b=StringBuilder()
    fun raw(v:String){b.append(v).append('\n')}
    fun fill(r:Int,g:Int,bl:Int){raw("${r/255.0} ${g/255.0} ${bl/255.0} rg")}
    fun stroke(r:Int,g:Int,bl:Int){raw("${r/255.0} ${g/255.0} ${bl/255.0} RG")}
    fun rect(x:Double,y:Double,w:Double,h:Double,fill:Boolean=true,stroke:Boolean=false){raw("${n(x)} ${n(y)} ${n(w)} ${n(h)} re ${when{fill&&stroke->"B";fill->"f";else->"S"}}")}
    fun line(x1:Double,y1:Double,x2:Double,y2:Double){raw("${n(x1)} ${n(y1)} m ${n(x2)} ${n(y2)} l S")}
    fun text(x:Double,y:Double,value:String,size:Double=8.0,bold:Boolean=false){raw("BT /${if(bold)"F2" else "F1"} ${n(size)} Tf 1 0 0 1 ${n(x)} ${n(y)} Tm (${pdfEscape(value)}) Tj ET")}
    fun textRight(xRight:Double,y:Double,value:String,size:Double=8.0,bold:Boolean=false){val approx=pdfAscii(value).length*size*.48;text(xRight-approx,y,value,size,bold)}
    fun build()=b.toString()
    private fun n(v:Double)=((v*100).toLong()/100.0).toString()
}

/**
 * Desktop-style A4 business document renderer used by PDF/share/email.
 * It is deliberately independent of screen layout: every business field and item
 * is rendered into document geometry with repeated page headers and a closing totals stack.
 */
internal fun businessDocumentPdf(payload:BusinessDocumentPayload):ByteArray {
    val itemsPerPage=13
    val chunks=payload.lines.chunked(itemsPerPage).ifEmpty{listOf(emptyList())}
    val pages=chunks.mapIndexed{pageIndex,lines->buildBusinessPdfPage(payload,lines,pageIndex,chunks.size)}
    return assemblePdf(pages)
}

private fun buildBusinessPdfPage(p:BusinessDocumentPayload,lines:List<DocumentLine>,pageIndex:Int,pageCount:Int):PdfPage{
    val c=PdfCanvasBuilder()
    val left=30.0;val right=565.0;val width=535.0
    // Header band follows the approved desktop JASVI navy/blue visual language.
    c.fill(30,67,123);c.rect(left,770.0,width,54.0)
    c.fill(255,255,255);c.text(left+14,801.0,"JASVI INDUSTRIES",18.0,true);c.text(left+14,784.0,"ERP BUSINESS DOCUMENT",7.5,false)
    c.fill(255,255,255);c.textRight(right-12,801.0,p.documentType.uppercase(),12.0,true);c.textRight(right-12,785.0,"Page ${pageIndex+1} of $pageCount",7.5,false)
    c.fill(55,117,188);c.rect(left,762.0,width,5.0)

    // Invoice metadata.
    c.fill(248,250,253);c.rect(left,706.0,width,48.0)
    c.fill(30,67,123);c.text(left+12,737.0,"DOCUMENT NO",7.0,true);c.fill(20,25,35);c.text(left+12,720.0,p.number,11.0,true)
    c.fill(30,67,123);c.text(left+205,737.0,"DATE",7.0,true);c.fill(20,25,35);c.text(left+205,720.0,p.date,10.0,true)
    val due=p.dueDate.takeIf{it.isNotBlank()}?:p.deliveryDate
    if(due.isNotBlank()){c.fill(30,67,123);c.text(left+325,737.0,if(p.dueDate.isNotBlank())"DUE DATE" else "DELIVERY DATE",7.0,true);c.fill(20,25,35);c.text(left+325,720.0,due,10.0,true)}
    if(p.reference.isNotBlank()){c.fill(30,67,123);c.text(left+425,737.0,"REFERENCE",7.0,true);c.fill(20,25,35);c.text(left+425,720.0,fitText(p.reference,22),9.0,true)}

    // Bill/ship party cards.
    c.stroke(117,153,198);c.fill(238,244,251);c.rect(left,614.0,262.0,82.0,true,true);c.rect(left+273,614.0,262.0,82.0,true,true)
    c.fill(30,67,123);c.text(left+10,679.0,"${p.partyLabel.uppercase()} / BILL TO",7.2,true);c.text(left+283,679.0,"DELIVER / SHIP TO",7.2,true)
    c.fill(20,25,35);c.text(left+10,661.0,fitText(p.partyName,42),10.0,true)
    val billGstin=p.billingGstin.ifBlank{p.partyGstin};if(billGstin.isNotBlank())c.text(left+10,647.0,"GSTIN: ${fitText(billGstin,26)}",7.2,false)
    splitText(p.billingAddress.ifBlank{p.partyName},48,2).forEachIndexed{i,v->c.text(left+10,633.0-i*11,v,7.0,false)}
    val deliveryTitle=p.deliveryAddress.ifBlank{p.billingAddress.ifBlank{p.partyName}};splitText(deliveryTitle,48,3).forEachIndexed{i,v->c.text(left+283,660.0-i*11,v,7.0,i==0)}
    val deliveryGstin=p.deliveryGstin.ifBlank{billGstin};if(deliveryGstin.isNotBlank())c.text(left+283,622.0,"GSTIN: ${fitText(deliveryGstin,26)}",7.0,false)

    // Business metadata strip.
    val meta=listOf(
        "Type" to p.invoiceType,
        "Payment Terms" to p.paymentTerms,
        "PO / Order" to listOf(p.orderNo,p.poDate).filter{it.isNotBlank()}.joinToString(" / "),
        "Salesperson" to p.salesperson,
        "Warehouse" to p.warehouse,
        "GST" to p.gstTreatment,
        "Transporter" to p.transporter,
        "Vehicle" to p.vehicleNumber,
    ).filter{it.second.isNotBlank()}.take(4)
    c.fill(248,250,253);c.rect(left,578.0,width,28.0)
    if(meta.isEmpty()){c.fill(78,90,108);c.text(left+10,589.0,"Business document generated from the ERP record",7.0,false)}else{
        val cell=width/meta.size
        meta.forEachIndexed{i,(k,v)->val x=left+i*cell;c.fill(30,67,123);c.text(x+7,594.0,k.uppercase(),5.8,true);c.fill(20,25,35);c.text(x+7,582.0,fitText(v,(cell/4.4).toInt().coerceAtLeast(12)),7.0,true)}
    }

    // Items table.
    val top=564.0;val headerH=24.0;val rowH=29.0
    val cols=listOf(24.0,198.0,48.0,42.0,58.0,44.0,52.0,69.0) // #, item, hsn, qty, rate, disc, gst, amount = 535
    val headers=listOf("#","ITEM / DESCRIPTION","HSN / UNIT","QTY","RATE","DISC %","GST %","AMOUNT")
    c.fill(30,67,123);c.rect(left,top-headerH,width,headerH)
    var x=left
    headers.forEachIndexed{i,h->c.fill(255,255,255);c.text(x+4,top-16,h,6.2,true);x+=cols[i]}
    c.stroke(117,153,198)
    var y=top-headerH
    if(lines.isEmpty()){
        c.fill(255,255,255);c.rect(left,y-rowH,width,rowH,true,true);c.fill(78,90,108);c.text(left+10,y-18,"No line items are available for this document.",7.5,false);y-=rowH
    }else lines.forEachIndexed{idx,l->
        c.fill(if(idx%2==0)248 else 255,if(idx%2==0)250 else 255,if(idx%2==0)253 else 255);c.rect(left,y-rowH,width,rowH,true,true)
        x=left;c.fill(20,25,35);c.text(x+6,y-18,(pageIndex*13+idx+1).toString(),7.0,true);x+=cols[0]
        c.text(x+5,y-12,fitText(l.itemCode,16),6.8,true);splitText(l.itemDescription.orEmpty(),38,1).forEach{c.text(x+5,y-23,it,6.2,false)};x+=cols[1]
        c.text(x+4,y-12,fitText(l.itemHsn.orEmpty(),10),6.2,false);c.text(x+4,y-23,fitText(l.itemUnit.orEmpty(),10),6.0,false);x+=cols[2]
        c.textRight(x+cols[3]-4,y-18,moneyNumber(l.quantity),6.6,false);x+=cols[3]
        c.textRight(x+cols[4]-4,y-18,moneyNumber(l.rate),6.6,false);x+=cols[4]
        c.textRight(x+cols[5]-4,y-18,moneyNumber(l.discountPercent),6.4,false);x+=cols[5]
        c.textRight(x+cols[6]-4,y-18,moneyNumber(l.gstPercent),6.4,false);x+=cols[6]
        c.textRight(x+cols[7]-4,y-18,moneyNumber(l.totalAmount),6.6,true)
        y-=rowH
    }

    val isFinal=pageIndex==pageCount-1
    if(isFinal){
        val closeTop=(y-12).coerceAtLeast(118.0)
        // Notes / transport left block.
        val leftBoxW=315.0;val totalsX=left+327;val totalsW=208.0
        c.fill(248,250,253);c.stroke(117,153,198);c.rect(left,closeTop-112,leftBoxW,112.0,true,true)
        c.fill(30,67,123);c.text(left+10,closeTop-17,"DOCUMENT DETAILS",7.0,true)
        val details=mutableListOf<Pair<String,String>>()
        if(p.transporter.isNotBlank())details += "Transporter" to p.transporter
        if(p.transporterGstin.isNotBlank())details += "Transport GSTIN" to p.transporterGstin
        if(p.vehicleNumber.isNotBlank())details += "Vehicle" to p.vehicleNumber
        if(p.contactPerson.isNotBlank())details += "Contact" to listOf(p.contactPerson,p.contactMobile).filter{it.isNotBlank()}.joinToString(" / ")
        val noteText=p.notes.ifBlank{p.remarks}
        if(noteText.isNotBlank())details += "Notes" to noteText
        if(details.isEmpty())details += "Status" to listOf(p.documentStatus,p.paymentStatus).filter{it.isNotBlank()}.joinToString(" / ")
        details.take(5).forEachIndexed{i,(k,v)->c.fill(30,67,123);c.text(left+10,closeTop-34-i*16,"$k:",6.4,true);c.fill(20,25,35);c.text(left+76,closeTop-34-i*16,fitText(v,48),6.4,false)}

        // Totals block.
        c.stroke(117,153,198);c.fill(255,255,255);c.rect(totalsX,closeTop-112,totalsW,112.0,true,true)
        val totalRows=mutableListOf(
            "Subtotal" to p.subtotal,
            "Discount" to -p.discount,
            "GST" to p.gst,
        )
        p.charges.filter{kotlin.math.abs(it.amount)>0.0001}.take(2).forEach{totalRows += (it.chargeType.ifBlank{"Charges"}) to it.amount}
        val chargesNotShown=p.charges.drop(2).sumOf{it.amount};if(kotlin.math.abs(chargesNotShown)>0.0001)totalRows += "Other Charges" to chargesNotShown
        var ty=closeTop-17
        totalRows.take(5).forEach{(k,v)->c.fill(78,90,108);c.text(totalsX+9,ty,k,6.5,false);c.fill(20,25,35);c.textRight(totalsX+totalsW-9,ty,plainMoney(v,p.currency),6.5,true);ty-=15}
        c.fill(30,67,123);c.rect(totalsX,closeTop-112,totalsW,25.0);c.fill(255,255,255);c.text(totalsX+9,closeTop-103,"GRAND TOTAL",7.2,true);c.textRight(totalsX+totalsW-9,closeTop-103,plainMoney(p.total,p.currency),7.5,true)

        // Payment band.
        val paymentY=closeTop-139
        c.fill(223,245,227);c.rect(left,paymentY,width,20.0)
        c.fill(20,25,35);c.text(left+9,paymentY+7,"Paid: ${plainMoney(p.paid,p.currency)}",6.8,true);c.text(left+185,paymentY+7,"Outstanding: ${plainMoney(p.outstanding,p.currency)}",6.8,true)
        val status=listOf(p.documentStatus,p.paymentStatus).filter{it.isNotBlank()}.joinToString(" / ");if(status.isNotBlank())c.textRight(right-9,paymentY+7,status,6.2,true)
    }

    // Footer.
    c.stroke(117,153,198);c.line(left,34.0,right,34.0);c.fill(78,90,108);c.text(left,20.0,"Generated from ${businessName()} ERP business data",6.2,false);c.textRight(right,20.0,"${p.documentType} ${p.number}",6.2,true)
    return PdfPage(c.build())
}

private fun assemblePdf(pages:List<PdfPage>):ByteArray{
    val objects=mutableListOf<ByteArray>()
    fun obj(text:String)=text.encodeToByteArray()
    objects += obj("<< /Type /Catalog /Pages 2 0 R >>")
    val pageIds=pages.indices.map{3+it*2}
    objects += obj("<< /Type /Pages /Kids [${pageIds.joinToString(" "){"$it 0 R"}}] /Count ${pages.size} >>")
    val fontRegular=3+pages.size*2
    val fontBold=fontRegular+1
    pages.forEachIndexed{index,page->
        val pageId=3+index*2;val contentId=pageId+1
        objects += obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 $fontRegular 0 R /F2 $fontBold 0 R >> >> /Contents $contentId 0 R >>")
        val stream=page.commands.encodeToByteArray()
        objects += ("<< /Length ${stream.size} >>\nstream\n".encodeToByteArray()+stream+"\nendstream".encodeToByteArray())
    }
    objects += obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    objects += obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>")
    val out=ArrayList<Byte>()
    fun add(bytes:ByteArray){bytes.forEach{out.add(it)}}
    add("%PDF-1.4\n".encodeToByteArray())
    val offsets=mutableListOf<Int>()
    objects.forEachIndexed{i,o->offsets+=out.size;add("${i+1} 0 obj\n".encodeToByteArray());add(o);add("\nendobj\n".encodeToByteArray())}
    val xref=out.size
    add("xref\n0 ${objects.size+1}\n0000000000 65535 f \n".encodeToByteArray())
    offsets.forEach{off->add(off.toString().padStart(10,'0').plus(" 00000 n \n").encodeToByteArray())}
    add("trailer\n<< /Size ${objects.size+1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n".encodeToByteArray())
    return out.toByteArray()
}

private fun xmlEscape(v:String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
private fun colName(index:Int):String{var n=index+1;val s=StringBuilder();while(n>0){val r=(n-1)%26;s.append(('A'.code+r).toChar());n=(n-1)/26};return s.reverse().toString()}
private fun crc32(data:ByteArray):Long {var crc=0xFFFFFFFFL;for(b in data){crc=crc xor (b.toLong() and 0xFF);repeat(8){crc=if((crc and 1L)!=0L)(crc ushr 1) xor 0xEDB88320L else crc ushr 1}};return crc xor 0xFFFFFFFFL}
private fun put16(out:MutableList<Byte>,v:Int){out.add((v and 0xff).toByte());out.add((v ushr 8 and 0xff).toByte())}
private fun put32(out:MutableList<Byte>,v:Long){repeat(4){i->out.add((v ushr (8*i) and 0xff).toByte())}}
private data class ZipEntryData(val name:String,val bytes:ByteArray,val crc:Long=crc32(bytes),var offset:Int=0)
private fun storedZip(entries:List<Pair<String,ByteArray>>):ByteArray{
    val out=mutableListOf<Byte>();val all=entries.map{ZipEntryData(it.first,it.second)}
    all.forEach{e->e.offset=out.size;put32(out,0x04034b50);put16(out,20);put16(out,0);put16(out,0);put16(out,0);put16(out,0);put32(out,e.crc);put32(out,e.bytes.size.toLong());put32(out,e.bytes.size.toLong());val n=e.name.encodeToByteArray();put16(out,n.size);put16(out,0);n.forEach(out::add);e.bytes.forEach(out::add)}
    val centralOffset=out.size
    all.forEach{e->put32(out,0x02014b50);put16(out,20);put16(out,20);put16(out,0);put16(out,0);put16(out,0);put16(out,0);put32(out,e.crc);put32(out,e.bytes.size.toLong());put32(out,e.bytes.size.toLong());val n=e.name.encodeToByteArray();put16(out,n.size);put16(out,0);put16(out,0);put16(out,0);put16(out,0);put32(out,0);put32(out,e.offset.toLong());n.forEach(out::add)}
    val centralSize=out.size-centralOffset
    put32(out,0x06054b50);put16(out,0);put16(out,0);put16(out,all.size);put16(out,all.size);put32(out,centralSize.toLong());put32(out,centralOffset.toLong());put16(out,0)
    return out.toByteArray()
}

/** Styled invoice workbook rather than a dump of the mobile screen. */
internal fun businessDocumentXlsx(payload:BusinessDocumentPayload):ByteArray{
    data class XRow(val values:List<String>,val style:Int=0,val numeric:Set<Int> = emptySet())
    val rows=mutableListOf<XRow>()
    rows += XRow(listOf(businessName().uppercase()),1)
    rows += XRow(listOf(payload.documentType.uppercase()),2)
    rows += XRow(listOf("Document No",payload.number,"Date",payload.date,"Due / Delivery",payload.dueDate.ifBlank{payload.deliveryDate}),3)
    rows += XRow(listOf(payload.partyLabel,payload.partyName,"GSTIN",payload.partyGstin),3)
    rows += XRow(listOf("Billing Address",payload.billingAddress,"Billing GSTIN",payload.billingGstin.ifBlank{payload.partyGstin}),3)
    rows += XRow(listOf("Delivery Address",payload.deliveryAddress,"Delivery GSTIN",payload.deliveryGstin),3)
    rows += XRow(listOf("Reference",payload.reference,"Order / PO",listOf(payload.orderNo,payload.poDate).filter{it.isNotBlank()}.joinToString(" / ")),3)
    rows += XRow(listOf("Payment Terms",payload.paymentTerms,"Transporter",payload.transporter,"Vehicle",payload.vehicleNumber),3)
    rows += XRow(emptyList())
    rows += XRow(listOf("#","Item Code","Description","HSN","Unit","Quantity","Rate","Discount %","GST %","Amount"),4)
    payload.lines.forEachIndexed{i,l->rows += XRow(listOf((i+1).toString(),l.itemCode,l.itemDescription.orEmpty(),l.itemHsn.orEmpty(),l.itemUnit.orEmpty(),moneyNumber(l.quantity),moneyNumber(l.rate),moneyNumber(l.discountPercent),moneyNumber(l.gstPercent),moneyNumber(l.totalAmount)),5,setOf(0,5,6,7,8,9))}
    rows += XRow(emptyList())
    rows += XRow(listOf("Subtotal",moneyNumber(payload.subtotal)),6,setOf(1))
    rows += XRow(listOf("Discount",moneyNumber(payload.discount)),6,setOf(1))
    rows += XRow(listOf("GST",moneyNumber(payload.gst)),6,setOf(1))
    payload.charges.forEach{rows += XRow(listOf(it.chargeType.ifBlank{"Charge"},moneyNumber(it.amount),if(it.taxable)"Taxable ${moneyNumber(it.gstPercent)}%" else "Non-taxable"),6,setOf(1))}
    rows += XRow(listOf("GRAND TOTAL",moneyNumber(payload.total)),7,setOf(1))
    rows += XRow(listOf("Paid",moneyNumber(payload.paid),"Outstanding",moneyNumber(payload.outstanding)),6,setOf(1,3))
    rows += XRow(listOf("Document Status",payload.documentStatus,"Payment Status",payload.paymentStatus),3)
    if(payload.notes.isNotBlank()||payload.remarks.isNotBlank())rows += XRow(listOf("Notes",payload.notes.ifBlank{payload.remarks}),3)

    fun cell(ref:String,value:String,style:Int,numeric:Boolean):String = if(numeric&&value.toDoubleOrNull()!=null)
        "<c r=\"$ref\" s=\"$style\"><v>${xmlEscape(value)}</v></c>"
    else "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t>${xmlEscape(value)}</t></is></c>"
    val sheet=buildString{
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<cols><col min=\"1\" max=\"1\" width=\"8\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"18\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"34\" customWidth=\"1\"/><col min=\"4\" max=\"5\" width=\"16\" customWidth=\"1\"/><col min=\"6\" max=\"10\" width=\"14\" customWidth=\"1\"/></cols><sheetData>")
        rows.forEachIndexed{ri,row->append("<row r=\"${ri+1}\"${if(ri<2)" ht=\"24\" customHeight=\"1\"" else ""}>");row.values.forEachIndexed{ci,value->append(cell("${colName(ci)}${ri+1}",value,row.style,ci in row.numeric))};append("</row>")}
        append("</sheetData><mergeCells count=\"2\"><mergeCell ref=\"A1:J1\"/><mergeCell ref=\"A2:J2\"/></mergeCells><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"10\" topLeftCell=\"A11\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews></worksheet>")
    }
    val styles="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="3"><font><sz val="10"/><name val="Aptos"/></font><font><b/><sz val="18"/><color rgb="FFFFFFFF"/><name val="Aptos Display"/></font><font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Aptos"/></font></fonts><fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1E437B"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FF3775BC"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEEF4FB"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border/><border><left style="thin"><color rgb="FF7599C6"/></left><right style="thin"><color rgb="FF7599C6"/></right><top style="thin"><color rgb="FF7599C6"/></top><bottom style="thin"><color rgb="FF7599C6"/></bottom></border></borders><cellXfs count="8"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf><xf numFmtId="0" fontId="2" fillId="3" borderId="0" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf><xf numFmtId="0" fontId="0" fillId="4" borderId="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="2" fillId="2" borderId="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="4" borderId="1"/><xf numFmtId="0" fontId="2" fillId="2" borderId="1"/></cellXfs></styleSheet>"""
    val contentTypes="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    val rels="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    val workbook="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${xmlEscape(payload.documentType.take(28))}" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    val workbookRels="""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    return storedZip(listOf("[Content_Types].xml" to contentTypes.encodeToByteArray(),"_rels/.rels" to rels.encodeToByteArray(),"xl/workbook.xml" to workbook.encodeToByteArray(),"xl/_rels/workbook.xml.rels" to workbookRels.encodeToByteArray(),"xl/styles.xml" to styles.encodeToByteArray(),"xl/worksheets/sheet1.xml" to sheet.encodeToByteArray()))
}

internal fun shareBusinessPdf(payload:BusinessDocumentPayload):Boolean=platformShareFile("${payload.documentType} ${payload.number}","${payload.documentType.replace(' ','_')}_${payload.number}.pdf",businessDocumentPdf(payload))
internal fun shareBusinessXlsx(payload:BusinessDocumentPayload):Boolean=platformShareFile("${payload.documentType} ${payload.number}","${payload.documentType.replace(' ','_')}_${payload.number}.xlsx",businessDocumentXlsx(payload))

internal fun businessEmailBody(payload:BusinessDocumentPayload):String = buildString {
    append("Dear ").append(payload.partyName.ifBlank{payload.partyLabel}).append(",\n\n")
    append("Please find your ").append(payload.documentType.lowercase()).append(" ").append(payload.number).append(" attached.\n\n")
    append("Regards,\n").append(businessName())
}
