package com.tapmark.nfc.payment

import android.content.Context
import com.tapmark.nfc.model.GatewayConfig
import com.tapmark.nfc.model.GatewayEnvironment
import com.tapmark.nfc.model.GatewayProvider

/**
 * Persists payment gateway configuration and merchant settings in private SharedPreferences.
 * API keys stay strictly in app-private storage and are never logged or exported.
 */
class GatewayConfigRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(): GatewayConfig = synchronized(lock) {
        val providerName = prefs.getString(KEY_PROVIDER, null)
        val environmentName = prefs.getString(KEY_ENVIRONMENT, null)

        val provider = GatewayProvider.entries.firstOrNull { it.name == providerName }
            ?: GatewayProvider.DEMO_SANDBOX
        val environment = GatewayEnvironment.entries.firstOrNull { it.name == environmentName }
            ?: GatewayEnvironment.SANDBOX
        val merchantName = prefs.getString(KEY_MERCHANT_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_MERCHANT_NAME
        val payMongoApiKey = prefs.getString(KEY_PAYMONGO_API_KEY, "")?.trim().orEmpty()
        val mayaPublicKey = prefs.getString(KEY_MAYA_PUBLIC_KEY, "")?.trim().orEmpty()
        val customBackendUrl = prefs.getString(KEY_CUSTOM_BACKEND_URL, "")?.trim().orEmpty()

        GatewayConfig(
            provider = provider,
            environment = environment,
            merchantName = merchantName,
            payMongoApiKey = payMongoApiKey,
            mayaPublicKey = mayaPublicKey,
            customBackendUrl = customBackendUrl
        )
    }

    fun save(config: GatewayConfig): Unit = synchronized(lock) {
        val normalizedMerchant = config.merchantName.trim().ifBlank { DEFAULT_MERCHANT_NAME }
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_ENVIRONMENT, config.environment.name)
            .putString(KEY_MERCHANT_NAME, normalizedMerchant)
            .putString(KEY_PAYMONGO_API_KEY, config.payMongoApiKey.trim())
            .putString(KEY_MAYA_PUBLIC_KEY, config.mayaPublicKey.trim())
            .putString(KEY_CUSTOM_BACKEND_URL, config.customBackendUrl.trim())
            .apply()
    }

    private companion object {
        private const val PREFS_NAME = "tapmark_gateway_prefs"
        private const val DEFAULT_MERCHANT_NAME = "Tapmark Terminal"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_ENVIRONMENT = "environment"
        private const val KEY_MERCHANT_NAME = "merchant_name"
        private const val KEY_PAYMONGO_API_KEY = "paymongo_api_key"
        private const val KEY_MAYA_PUBLIC_KEY = "maya_public_key"
        private const val KEY_CUSTOM_BACKEND_URL = "custom_backend_url"
    }
}
