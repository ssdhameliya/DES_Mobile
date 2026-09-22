package org.dse.mobile.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.dse.mobile.core.api.ApiResult
import org.dse.mobile.core.api.DseErpHttpClient

/** Central mobile presentation state sourced from the same server settings as desktop 10.0.26. */
internal object BusinessBrandingState {
    var companyName by mutableStateOf("Jasvi Industries")
        private set
    var displayName by mutableStateOf("Jasvi Industries")
        private set
    var tagline by mutableStateOf("Business. Anywhere.")
        private set
    var startingText by mutableStateOf("")
        private set

    fun apply(company:String?, display:String?, tag:String?, starting:String?) {
        companyName = company?.trim().takeUnless { it.isNullOrBlank() } ?: companyName
        displayName = display?.trim().takeUnless { it.isNullOrBlank() } ?: companyName
        tagline = tag?.trim().takeUnless { it.isNullOrBlank() } ?: tagline
        startingText = starting?.trim().orEmpty()
    }
}

internal suspend fun refreshBusinessBranding(api:DseErpHttpClient) {
    suspend fun value(key:String, def:String):String = when(val r=api.setting(key,def)) {
        is ApiResult.Success -> r.value.value.ifBlank { def }
        else -> def
    }
    val company=value("company.name",BusinessBrandingState.companyName)
    val display=value("application.displayName",company)
    val tagline=value("application.tagline",value("company.tagline",BusinessBrandingState.tagline))
    val starting=value("application.startingText",BusinessBrandingState.startingText)
    BusinessBrandingState.apply(company,display,tagline,starting)
}

internal fun businessName():String = BusinessBrandingState.companyName.ifBlank { "Jasvi Industries" }
