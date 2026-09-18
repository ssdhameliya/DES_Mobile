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

@Composable internal fun AdminRegistrationApprovals(api:DseErpHttpClient,p:PermissionContext){
    if(!p.isAdmin()){PermissionDeniedCard("Registration Approvals"){};return}
    var rows by remember{mutableStateOf<List<RegistrationRequestRow>>(emptyList())}
    var roles by remember{mutableStateOf<List<AdminRole>>(emptyList())}
    var defaultRole by remember{mutableStateOf<RegistrationRoleDto?>(null)}
    var msg by remember{mutableStateOf("Loading pending registrations…")}
    var refresh by remember{mutableIntStateOf(0)}
    var selected by remember{mutableStateOf<RegistrationRequestRow?>(null)}
    var roleEdit by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(refresh){
        (api.adminRoles() as? ApiResult.Success)?.value?.let{roles=it.filter{r->r.active&&!r.code.equals("ADMIN",true)}}
        (api.registrationRole() as? ApiResult.Success)?.value?.let{defaultRole=it}
        when(val r=api.registrationRequests("PENDING_ADMIN_APPROVAL")){is ApiResult.Success->{rows=r.value;msg="${rows.size} pending registration(s)"};else->msg=r.readableMessage()}
    }
    DseRegisterShell("Registration Approvals","Verified registrations waiting for administrator decision","",{},msg,{refresh++},kpis={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){DseMetricTile("Pending",rows.size.toString(),Icons.Rounded.PendingActions,Modifier.weight(1f),DseWarning);DseMetricTile("Verified",rows.count{it.emailVerified&&it.mfaVerified}.toString(),Icons.Rounded.VerifiedUser,Modifier.weight(1f),DseSuccess)}
        PremiumSecondaryButton("Public Registration Role • ${defaultRole?.displayName?.ifBlank{defaultRole?.code.orEmpty()}.orEmpty().ifBlank{"Not configured"}}",{roleEdit=true},Modifier.fillMaxWidth(),icon=Icons.Rounded.ManageAccounts)
    }}){
        rows.forEach{r->DseRecordCard(r.fullName.ifBlank{r.username},"${r.username} • ${r.email}",r.requestedRole,listOf("Email" to if(r.emailVerified)"VERIFIED" else "PENDING","MFA" to if(r.mfaVerified)"VERIFIED" else "PENDING"),r.requestedAt){selected=r}}
        if(rows.isEmpty())DseEmptyRegisterState("No pending registrations","New verified requests will appear here.",Icons.Rounded.HowToReg)
    }
    selected?.let { r ->
        RegistrationDecisionDialog(
            r,
            roles,
            { selected=null },
            { role,reason,approve ->
                scope.launch {
                    val req=RegistrationDecisionRequest(role,reason,r.rowVersion)
                    val result=if(approve) api.approveRegistration(r.id,req) else api.rejectRegistration(r.id,req)
                    when(result){
                        is ApiResult.Success->{msg=result.value.message;selected=null;refresh++}
                        else->msg=result.readableMessage()
                    }
                }
            },
        )
    }
    if(roleEdit)RegistrationRoleDialog(defaultRole,roles,{roleEdit=false}){role->scope.launch{when(val r=api.saveRegistrationRole(role)){is ApiResult.Success->{defaultRole=r.value;msg="Public registration role updated";roleEdit=false};else->msg=r.readableMessage()}}}
}

@Composable private fun RegistrationDecisionDialog(row:RegistrationRequestRow,roles:List<AdminRole>,onClose:()->Unit,onDecision:(String,String,Boolean)->Unit){
    var role by remember{mutableStateOf(row.requestedRole)}
    var reason by remember{mutableStateOf("")}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Review ${row.username}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        detailRows(listOf("Name" to row.fullName,"Email" to row.email,"Requested Role" to row.requestedRole,"Email" to if(row.emailVerified)"Verified" else "Pending","MFA" to if(row.mfaVerified)"Verified" else "Pending","Requested" to row.requestedAt))
        DseSelect("Final Role",role,roles.map{it.code},required=true,onValue={role=it})
        DseField("Decision Note / Rejection Reason",reason,onValue={reason=it})
        if(!row.emailVerified||!row.mfaVerified)Text("Approval is blocked until identity verification is complete.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }},confirmButton={Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){OutlinedButton(onClick={onDecision(role,reason,false)}){Icon(Icons.Rounded.Close,null);Text("Reject")};Button(enabled=row.emailVerified&&row.mfaVerified&&role.isNotBlank(),onClick={onDecision(role,reason,true)}){Icon(Icons.Rounded.Check,null);Text("Approve")}}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun RegistrationRoleDialog(current:RegistrationRoleDto?,roles:List<AdminRole>,onClose:()->Unit,onSave:(String)->Unit){
    var role by remember{mutableStateOf(current?.code.orEmpty())}
    PremiumAlertDialog(onDismissRequest=onClose,title={Text("Public Registration Role")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("New users may request registration only under this administrator-approved role policy.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);DseSelect("Default Role",role,roles.map{it.code},required=true,onValue={role=it})}},confirmButton={Button(enabled=role.isNotBlank(),onClick={onSave(role)}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
