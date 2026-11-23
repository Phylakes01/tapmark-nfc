package com.tapmark.nfc.model

enum class GatewayProvider {
    DEMO_SANDBOX,
    PAYMONGO,
    MAYA
}

enum class GatewayEnvironment {
    SANDBOX,
    LIVE
}

enum class PaymentMethodMode {
    NFC_TAP_DEMO,
    QR_PH_CHECKOUT
}

enum class ReceiptStatus {
    PENDING,
    APPROVED,
    PAID,
    FAILED,
    CANCELLED
}

data class GatewayConfig(
    val provider: GatewayProvider = GatewayProvider.DEMO_SANDBOX,
    val environment: GatewayEnvironment = GatewayEnvironment.SANDBOX,
    val merchantName: String = "Tapmark Terminal",
    val payMongoApiKey: String = "",
    val mayaPublicKey: String = "",
    val customBackendUrl: String = ""
)

data class CheckoutSessionResult(
    val sessionId: String,
    val referenceNumber: String,
    val checkoutUrl: String,
    val qrPayload: String,
    val provider: GatewayProvider,
    val environment: GatewayEnvironment,
    val isSimulated: Boolean,
    val statusMessage: String
)

data class TerminalReceipt(
    val id: String,
    val referenceNumber: String,
    val createdAtMillis: Long,
    val amountCentavos: Long,
    val currency: String = "PHP",
    val description: String,
    val merchantName: String,
    val provider: GatewayProvider,
    val environment: GatewayEnvironment,
    val methodMode: PaymentMethodMode,
    val status: ReceiptStatus,
    val isSandboxDemo: Boolean,
    val bankName: String? = null,
    val cardNetwork: String? = null,
    val panMasked: String? = null,
    val cardVisualStyle: CardVisualStyle? = null,
    val emvAid: String? = null,
    val emvAtc: Int? = null,
    val emvCryptogram: String? = null,
    val authCode: String? = null,
    val checkoutSessionId: String? = null,
    val checkoutUrl: String? = null
)
