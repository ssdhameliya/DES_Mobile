package org.dse.mobile.app

import org.dse.mobile.core.config.MobileBuildInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val DseViolet = Color(0xFF7C3AED)
internal val DseElectricPurple = Color(0xFF8B5CF6)
internal val DseIndigo = Color(0xFF4F46E5)
internal val DseDeepPurple = Color(0xFF2E1065)
internal val DseInk = Color(0xFF111827)
internal val DseMuted = Color(0xFF64748B)
internal val DseCanvas = Color(0xFFF7F7FC)
internal val DseLavender = Color(0xFFF3E8FF)
internal val DseLavenderBlue = Color(0xFFEEF2FF)

internal val DseBrandGradient = Brush.linearGradient(
    listOf(Color(0xFF9333EA), Color(0xFF7C3AED), Color(0xFF4F46E5))
)
internal val DseHeroGradient = Brush.linearGradient(
    listOf(Color(0xFF8B2CF5), Color(0xFF6D28D9), Color(0xFF4F46E5))
)
internal val DseSplashGradient = Brush.verticalGradient(
    listOf(Color(0xFF28104F), Color(0xFF4C1D95), Color(0xFF6D28D9), Color(0xFF211044))
)

internal fun semanticFieldIcon(label:String):ImageVector {
    val t=label.trim().lowercase()
    return when {
        "customer" in t || "supplier" in t || "party" in t || "person" in t || "username" in t || "full name" in t -> Icons.Rounded.Person
        "email" in t || "mail" in t -> Icons.Rounded.Email
        "phone" in t || "mobile" in t || "contact" in t -> Icons.Rounded.Phone
        "password" in t || "authenticator" in t || "verification" in t || "captcha" in t || "access" in t || "role" in t -> Icons.Rounded.Security
        "date" in t || "from" == t || "to" == t || "until" in t || "follow-up" in t -> Icons.Rounded.CalendarMonth
        "gst" in t || "tax" in t || "hsn" in t -> Icons.Rounded.Percent
        "address" in t || "location" in t || "branch" in t || "warehouse" in t -> Icons.Rounded.LocationOn
        "invoice" in t || "quotation" in t || "voucher" in t || "order" in t || "po " in t || "reference" in t || "ref" == t -> Icons.Rounded.ReceiptLong
        "item" in t || "product" in t || "description" in t || "unit" in t -> Icons.Rounded.Inventory2
        "quantity" in t || "qty" in t -> Icons.Rounded.Numbers
        "amount" in t || "rate" in t || "price" in t || "total" in t || "payment" in t || "account" in t || "bank" in t || "currency" in t -> Icons.Rounded.Payments
        "discount" in t -> Icons.Rounded.LocalOffer
        "transporter" in t || "vehicle" in t || "lr" in t || "awb" in t -> Icons.Rounded.LocalShipping
        "status" in t || "type" in t || "mode" in t || "category" in t || "term" in t || "source" in t -> Icons.Rounded.Tune
        "note" in t || "remark" in t || "reason" in t || "message" in t -> Icons.Rounded.Notes
        "search" in t -> Icons.Rounded.Search
        "department" in t -> Icons.Rounded.Business
        else -> Icons.Rounded.Label
    }
}

internal fun semanticFieldAccent(label:String):Color {
    val t=label.trim().lowercase()
    return when {
        "amount" in t || "rate" in t || "price" in t || "total" in t || "payment" in t || "bank" in t || "account" in t || "currency" in t -> DseSuccess
        "gst" in t || "tax" in t || "hsn" in t || "discount" in t -> DseWarning
        "address" in t || "location" in t || "warehouse" in t || "branch" in t -> Color(0xFF0891B2)
        "phone" in t || "mobile" in t || "contact" in t || "email" in t -> DseInfo
        "customer" in t || "supplier" in t || "party" in t || "person" in t || "item" in t || "product" in t -> DseIndigo
        "transporter" in t || "vehicle" in t || "lr" in t || "awb" in t -> Color(0xFFEA580C)
        "password" in t || "authenticator" in t || "verification" in t || "captcha" in t -> Color(0xFFBE123C)
        "status" in t -> DseInfo
        "note" in t || "remark" in t || "reason" in t || "message" in t -> Color(0xFF7C3AED)
        else -> DseViolet
    }
}

@Composable
internal fun PremiumFieldLabel(
    label:String,
    required:Boolean=false,
    icon:ImageVector?=null,
    accent:Color=semanticFieldAccent(label),
){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
        Icon(icon?:semanticFieldIcon(label),null,modifier=Modifier.size(13.dp),tint=accent)
        Text(label,color=accent,fontWeight=FontWeight.SemiBold)
        if(required)Text("*",color=DseDanger,fontWeight=FontWeight.ExtraBold)
    }
}

@Composable
internal fun PremiumValueText(label:String,value:String,modifier:Modifier=Modifier){
    val accent=semanticFieldAccent(label)
    Text(value,modifier=modifier,color=accent,fontWeight=FontWeight.SemiBold)
}

internal fun semanticActionIcon(label:String):ImageVector {
    val t=label.trim().lowercase()
    return when {
        "pdf" in t || "print" in t -> Icons.Rounded.PictureAsPdf
        "excel" in t || "xlsx" in t || "spreadsheet" in t || "csv" in t || "export" in t -> Icons.Rounded.TableChart
        "email" in t || "mail" in t -> Icons.Rounded.Email
        "whatsapp" in t || "message" in t -> Icons.Rounded.Forum
        "payment" in t || t=="pay" || "record payment" in t -> Icons.Rounded.Payments
        "return" in t -> Icons.Rounded.AssignmentReturn
        "duplicate" in t || "copy" in t -> Icons.Rounded.ContentCopy
        "approve" in t || "complete" in t -> Icons.Rounded.CheckCircle
        "reject" in t || "block" in t -> Icons.Rounded.Block
        "cancel" in t || "void" in t -> Icons.Rounded.Cancel
        "delete" in t || "remove" in t || "clear" in t -> Icons.Rounded.Delete
        "timeline" in t || "history" in t || "activity" in t -> Icons.Rounded.Timeline
        "attachment" in t || "attach" in t || "proof" in t -> Icons.Rounded.AttachFile
        "edit" in t || "update" in t -> Icons.Rounded.Edit
        "view" in t || "open" in t || "detail" in t -> Icons.Rounded.Visibility
        "save" in t -> Icons.Rounded.Save
        "send" in t -> Icons.Rounded.Send
        "filter" in t -> Icons.Rounded.FilterAlt
        "share" in t -> Icons.Rounded.Share
        "download" in t -> Icons.Rounded.Download
        "upload" in t -> Icons.Rounded.UploadFile
        "add" in t || "new" in t || "create" in t -> Icons.Rounded.Add
        "refresh" in t || "reload" in t -> Icons.Rounded.Refresh
        "close" in t || "back" in t -> Icons.Rounded.Close
        "snooze" in t -> Icons.Rounded.Alarm
        "activate" in t || "reopen" in t -> Icons.Rounded.ToggleOn
        "deactivate" in t -> Icons.Rounded.ToggleOff
        else -> Icons.Rounded.AutoAwesome
    }
}

@Composable
internal fun DseBrandMark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    dark: Boolean = false,
) {
    val size = if (compact) 42.dp else 64.dp
    Box(
        modifier
            .size(size)
            .shadow(if (compact) 8.dp else 16.dp, RoundedCornerShape(if (compact) 13.dp else 20.dp))
            .clip(RoundedCornerShape(if (compact) 13.dp else 20.dp))
            .background(if (dark) Brush.linearGradient(listOf(Color.White.copy(.96f), Color(0xFFE9D5FF))) else DseBrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(if (compact) 23.dp else 34.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val bars = if (compact) listOf(8.dp, 13.dp, 18.dp, 23.dp) else listOf(12.dp, 20.dp, 27.dp, 34.dp)
            bars.forEach { h ->
                Box(
                    Modifier
                        .width(if (compact) 4.dp else 6.dp)
                        .height(h)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (dark) DseViolet else Color.White)
                )
            }
        }
    }
}

@Composable
internal fun PremiumBackdrop(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = if (dark) DseSplashGradient else Brush.verticalGradient(listOf(Color(0xFFF8F7FF), Color.White, Color(0xFFF4F5FB)))
    Box(modifier.fillMaxSize().background(base)) {
        Box(
            Modifier
                .size(280.dp)
                .offset(x = (-120).dp, y = (-100).dp)
                .clip(CircleShape)
                .background((if (dark) Color(0xFFB794F4) else DseElectricPurple).copy(alpha = if (dark) .10f else .08f))
        )
        Box(
            Modifier
                .size(330.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 160.dp, y = 130.dp)
                .clip(CircleShape)
                .background((if (dark) Color(0xFF60A5FA) else DseIndigo).copy(alpha = if (dark) .08f else .055f))
        )
        content()
    }
}

@Composable
internal fun PremiumCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(.08f), spotColor = Color.Black.copy(.10f)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun PremiumIconTile(
    icon: ImageVector,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(accent.copy(.17f), accent.copy(.07f))))
            .border(1.dp, accent.copy(.16f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(size * .48f))
    }
}

@Composable
internal fun UatStatusPill(
    online: Boolean? = true,
    modifier: Modifier = Modifier,
) {
    val accent = when (online) {
        true -> DseSuccess
        false -> DseDanger
        null -> DseWarning
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = .10f),
        border = BorderStroke(1.dp, accent.copy(alpha = .18f)),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Icon(if (online == true) Icons.Rounded.CloudDone else Icons.Rounded.Security, null, Modifier.size(14.dp), tint = accent)
            Text(
                when (online) { true -> "${MobileBuildInfo.RELEASE_CHANNEL} Online"; false -> "${MobileBuildInfo.RELEASE_CHANNEL} Offline"; null -> "Checking ${MobileBuildInfo.RELEASE_CHANNEL}" },
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


@Composable
internal fun PremiumPrimaryButton(
    text:String,
    onClick:()->Unit,
    modifier:Modifier=Modifier,
    enabled:Boolean=true,
    leadingIcon:ImageVector?=null,
    trailingIcon:ImageVector?=null,
){
    Box(
        modifier
            .heightIn(min=46.dp)
            .shadow(if(enabled)12.dp else 0.dp,RoundedCornerShape(18.dp),ambientColor=DseViolet.copy(.18f),spotColor=DseViolet.copy(.24f))
            .clip(RoundedCornerShape(18.dp))
            .background(if(enabled)DseBrandGradient else Brush.linearGradient(listOf(Color(0xFFD7D3DF),Color(0xFFC7C1CE))))
            .alpha(if(enabled)1f else .72f)
            .clickable(enabled=enabled,onClick=onClick),
        contentAlignment=Alignment.Center,
    ){
        Row((if(trailingIcon!=null)Modifier.fillMaxWidth() else Modifier).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
            val resolvedLeading=leadingIcon ?: semanticActionIcon(text)
            Icon(resolvedLeading,null,tint=Color.White,modifier=Modifier.size(19.dp));Spacer(Modifier.width(8.dp))
            Text(text,color=Color.White,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
            if(trailingIcon!=null){Spacer(Modifier.weight(1f));Icon(trailingIcon,null,tint=Color.White,modifier=Modifier.size(19.dp))}
        }
    }
}

@Composable
internal fun PremiumSectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent=semanticFieldAccent(title)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PremiumIconTile(semanticFieldIcon(title),accent,size=32.dp)
        Text(title, color=accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (!action.isNullOrBlank() && onAction != null) TextButton(onClick = onAction) {
            Icon(semanticActionIcon(action),null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(action)
        }
    }
}

@Composable
internal fun PremiumOptionLabel(label:String, modifier:Modifier=Modifier, accent:Color=semanticFieldAccent(label)){
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        Icon(semanticFieldIcon(label),null,Modifier.size(16.dp),tint=accent)
        Text(label,color=accent,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun PremiumSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 46.dp)
            .shadow(if (enabled) 6.dp else 0.dp, RoundedCornerShape(18.dp), ambientColor = DseViolet.copy(.07f), spotColor = DseViolet.copy(.10f))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(if (enabled) .26f else .10f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val resolvedIcon = icon ?: semanticActionIcon(text)
            Icon(resolvedIcon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(.55f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun PremiumDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .heightIn(min = 46.dp)
            .shadow(if (enabled) 8.dp else 0.dp, shape, ambientColor = DseDanger.copy(.10f), spotColor = DseDanger.copy(.16f))
            .clip(shape)
            .background(
                if (enabled) Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                else Brush.linearGradient(listOf(Color(0xFFE5E7EB), Color(0xFFD1D5DB)))
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val resolvedIcon = icon ?: semanticActionIcon(text)
            Icon(resolvedIcon, null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun PremiumBiometricPanel(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = DseViolet.copy(.07f), spotColor = DseViolet.copy(.10f))
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, DseViolet.copy(.18f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(DseBrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Fingerprint, null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(platformBiometricUnlockLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (enabled) "Unlock your secure workspace instantly" else "Available after your first secure sign-in on ${platformDeviceClassLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (enabled) Icons.Rounded.ArrowForward else if(platformName()=="Android") Icons.Rounded.PhoneAndroid else Icons.Rounded.PhoneIphone,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
