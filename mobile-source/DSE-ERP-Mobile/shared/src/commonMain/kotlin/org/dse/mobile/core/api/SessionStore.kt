package org.dse.mobile.core.api

/** Platform session contract. Native mobile targets persist bearer tokens using OS-protected credential storage. */
interface SessionStore {
    fun accessToken(): String?
    fun saveAccessToken(token: String?)
    fun clear()
}

class InMemorySessionStore : SessionStore {
    private var token: String? = null
    override fun accessToken(): String? = token
    override fun saveAccessToken(token: String?) { this.token = token }
    override fun clear() { token = null }
}
