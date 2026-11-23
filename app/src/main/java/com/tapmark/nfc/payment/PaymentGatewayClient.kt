package com.tapmark.nfc.payment

import com.tapmark.nfc.model.CheckoutSessionResult
import com.tapmark.nfc.model.GatewayConfig
import com.tapmark.nfc.model.GatewayEnvironment
import com.tapmark.nfc.model.GatewayProvider
import com.tapmark.nfc.model.PaymentMethodMode
import com.tapmark.nfc.model.ReceiptStatus
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.model.TerminalReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Payment gateway client supporting Custom Backend Proxy, PayMongo Checkout Sessions,
 * Maya Checkout, and an instant offline QR Ph Sandbox Preview fallback.
 * Uses pure-Kotlin JSON builders/parsers (no org.json dependency) so JVM tests run cleanly.
 */
object PaymentGatewayClient {

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000
    private const val MAX_RESPONSE_BYTES = 128 * 1024
    private const val PAYMONGO_MIN_CENTAVOS = 2000L
    private const val PAYMONGO_CHECKOUT_URL = "https://api.paymongo.com/v1/checkout_sessions"
    private const val MAYA_SANDBOX_CHECKOUT_URL = "https://pg-sandbox.paymaya.com/checkout/v1/checkouts"
    private const val MAYA_LIVE_CHECKOUT_URL = "https://pg.paymaya.com/checkout/v1/checkouts"
    private const val DEFAULT_DESCRIPTION = "Tapmark Terminal Sale"
    private const val DEFAULT_MERCHANT_NAME = "Tapmark Terminal"
    private const val USER_AGENT = "Tapmark/1.4"
    private const val SANDBOX_QR_STATUS_MESSAGE =
        "Sandbox QR Preview · Add your free PayMongo or Maya key in Gateway Settings for live checkout"

    private val secureRandom = SecureRandom()

    suspend fun createCheckoutSession(
        config: GatewayConfig,
        amountCentavos: Long,
        description: String
    ): Result<CheckoutSessionResult> {
        if (amountCentavos <= 0L) {
            return Result.failure(IllegalArgumentException("Amount must be greater than ₱0.00"))
        }

        val cleanDescription = description.trim().ifBlank { DEFAULT_DESCRIPTION }
        val cleanMerchant = config.merchantName.trim().ifBlank { DEFAULT_MERCHANT_NAME }
        val referenceNumber = generateReferenceNumber()

        if (config.customBackendUrl.isNotBlank()) {
            val url = config.customBackendUrl.trim()
            if (!url.startsWith("https://", ignoreCase = true) &&
                !url.startsWith("http://", ignoreCase = true)
            ) {
                return Result.failure(
                    IllegalArgumentException("Custom backend URL must start with http:// or https://")
                )
            }
            val requestBody = buildCustomBackendRequestJson(
                amountCentavos = amountCentavos,
                description = cleanDescription,
                referenceNumber = referenceNumber,
                provider = config.provider,
                environment = config.environment
            )
            return withContext(Dispatchers.IO) {
                executeJsonRequest(
                    urlString = url,
                    method = "POST",
                    headers = emptyMap(),
                    body = requestBody
                ).fold(
                    onSuccess = { response ->
                        if (response.statusCode !in 200..299) {
                            Result.failure(
                                IllegalStateException(extractGatewayError(response.statusCode, response.body))
                            )
                        } else {
                            parseCustomBackendResponse(
                                json = response.body,
                                referenceNumber = referenceNumber,
                                provider = config.provider,
                                environment = config.environment
                            )
                        }
                    },
                    onFailure = { Result.failure(it) }
                )
            }
        }

        if (config.provider == GatewayProvider.PAYMONGO && config.payMongoApiKey.isNotBlank()) {
            if (amountCentavos < PAYMONGO_MIN_CENTAVOS) {
                return Result.failure(IllegalArgumentException("PayMongo requires a minimum of ₱20.00"))
            }
            val requestBody = buildPayMongoCheckoutRequestJson(
                amountCentavos = amountCentavos,
                description = cleanDescription,
                referenceNumber = referenceNumber
            )
            val headers = mapOf("Authorization" to basicAuthHeader(config.payMongoApiKey))
            return withContext(Dispatchers.IO) {
                executeJsonRequest(
                    urlString = PAYMONGO_CHECKOUT_URL,
                    method = "POST",
                    headers = headers,
                    body = requestBody
                ).fold(
                    onSuccess = { response ->
                        if (response.statusCode !in 200..299) {
                            Result.failure(
                                IllegalStateException(extractGatewayError(response.statusCode, response.body))
                            )
                        } else {
                            parsePayMongoCheckoutResponse(
                                json = response.body,
                                referenceNumber = referenceNumber,
                                environment = config.environment
                            )
                        }
                    },
                    onFailure = { Result.failure(it) }
                )
            }
        }

        if (config.provider == GatewayProvider.MAYA && config.mayaPublicKey.isNotBlank()) {
            val endpoint = if (config.environment == GatewayEnvironment.SANDBOX) {
                MAYA_SANDBOX_CHECKOUT_URL
            } else {
                MAYA_LIVE_CHECKOUT_URL
            }
            val requestBody = buildMayaCheckoutRequestJson(
                amountCentavos = amountCentavos,
                description = cleanDescription,
                referenceNumber = referenceNumber
            )
            val headers = mapOf("Authorization" to basicAuthHeader(config.mayaPublicKey))
            return withContext(Dispatchers.IO) {
                executeJsonRequest(
                    urlString = endpoint,
                    method = "POST",
                    headers = headers,
                    body = requestBody
                ).fold(
                    onSuccess = { response ->
                        if (response.statusCode !in 200..299) {
                            Result.failure(
                                IllegalStateException(extractGatewayError(response.statusCode, response.body))
                            )
                        } else {
                            parseMayaCheckoutResponse(
                                json = response.body,
                                referenceNumber = referenceNumber,
                                environment = config.environment
                            )
                        }
                    },
                    onFailure = { Result.failure(it) }
                )
            }
        }

        // Offline Sandbox Demo fallback (DEMO_SANDBOX or active provider without key)
        val qrPayload = buildDemoQrPhPayload(
            merchantName = cleanMerchant,
            amountCentavos = amountCentavos,
            referenceNumber = referenceNumber
        )
        return Result.success(
            CheckoutSessionResult(
                sessionId = "demo-$referenceNumber",
                referenceNumber = referenceNumber,
                checkoutUrl = "",
                qrPayload = qrPayload,
                provider = GatewayProvider.DEMO_SANDBOX,
                environment = GatewayEnvironment.SANDBOX,
                isSimulated = true,
                statusMessage = SANDBOX_QR_STATUS_MESSAGE
            )
        )
    }

    suspend fun checkPayMongoSessionStatus(
        config: GatewayConfig,
        sessionId: String
    ): Result<ReceiptStatus> {
        if (config.payMongoApiKey.isBlank()) {
            return Result.failure(IllegalArgumentException("PayMongo API key is required"))
        }
        val cleanSessionId = sessionId.trim()
        if (cleanSessionId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Checkout session ID is required"))
        }

        val encodedId = URLEncoder.encode(cleanSessionId, Charsets.UTF_8.name())
        val headers = mapOf("Authorization" to basicAuthHeader(config.payMongoApiKey))
        return withContext(Dispatchers.IO) {
            executeJsonRequest(
                urlString = "$PAYMONGO_CHECKOUT_URL/$encodedId",
                method = "GET",
                headers = headers,
                body = null
            ).fold(
                onSuccess = { response ->
                    if (response.statusCode !in 200..299) {
                        Result.failure(
                            IllegalStateException(extractGatewayError(response.statusCode, response.body))
                        )
                    } else {
                        parsePayMongoSessionStatus(response.body)
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }
    }

    fun buildNfcDemoReceipt(
        card: ScannedCard,
        amountCentavos: Long,
        description: String,
        config: GatewayConfig
    ): TerminalReceipt {
        val cleanDescription = description.trim().ifBlank { DEFAULT_DESCRIPTION }
        val cleanMerchant = config.merchantName.trim().ifBlank { DEFAULT_MERCHANT_NAME }
        return TerminalReceipt(
            id = UUID.randomUUID().toString(),
            referenceNumber = generateReferenceNumber(),
            createdAtMillis = System.currentTimeMillis(),
            amountCentavos = amountCentavos,
            currency = "PHP",
            description = cleanDescription,
            merchantName = cleanMerchant,
            provider = config.provider,
            environment = config.environment,
            methodMode = PaymentMethodMode.NFC_TAP_DEMO,
            status = ReceiptStatus.APPROVED,
            isSandboxDemo = true,
            bankName = card.emvInfo?.bankName ?: card.cardTitle,
            cardNetwork = card.emvInfo?.network ?: card.category.name,
            panMasked = card.emvInfo?.panMasked ?: card.uidHex,
            cardVisualStyle = card.visualStyle,
            emvAid = card.emvInfo?.aid,
            emvAtc = card.emvInfo?.atc,
            emvCryptogram = card.emvInfo?.cryptogramHex,
            authCode = generateAuthCode(),
            checkoutSessionId = null,
            checkoutUrl = null
        )
    }

    fun buildQrReceipt(
        session: CheckoutSessionResult,
        amountCentavos: Long,
        description: String,
        config: GatewayConfig,
        status: ReceiptStatus = ReceiptStatus.PENDING
    ): TerminalReceipt {
        val cleanDescription = description.trim().ifBlank { DEFAULT_DESCRIPTION }
        val cleanMerchant = config.merchantName.trim().ifBlank { DEFAULT_MERCHANT_NAME }
        return TerminalReceipt(
            id = UUID.randomUUID().toString(),
            referenceNumber = session.referenceNumber,
            createdAtMillis = System.currentTimeMillis(),
            amountCentavos = amountCentavos,
            currency = "PHP",
            description = cleanDescription,
            merchantName = cleanMerchant,
            provider = session.provider,
            environment = session.environment,
            methodMode = PaymentMethodMode.QR_PH_CHECKOUT,
            status = status,
            isSandboxDemo = session.isSimulated,
            checkoutSessionId = session.sessionId.takeIf { it.isNotBlank() },
            checkoutUrl = session.checkoutUrl.takeIf { it.isNotBlank() }
        )
    }

    fun formatPhpAmount(amountCentavos: Long): String {
        val negative = amountCentavos < 0L
        val absCentavos = if (amountCentavos == Long.MIN_VALUE) Long.MAX_VALUE else abs(amountCentavos)
        val whole = absCentavos / 100L
        val cents = absCentavos % 100L
        val wholeFormatted = NumberFormat.getIntegerInstance(Locale.US).format(whole)
        val prefix = if (negative) "-₱" else "₱"
        return "$prefix$wholeFormatted.${"%02d".format(Locale.US, cents)}"
    }

    fun buildReceiptShareText(receipt: TerminalReceipt): String {
        val sb = StringBuilder()
        val demoTag = if (receipt.isSandboxDemo) " (SANDBOX DEMO)" else ""
        sb.appendLine("Tapmark Terminal Receipt$demoTag")
        sb.appendLine("Merchant: ${receipt.merchantName}")
        sb.appendLine("Amount: ${formatPhpAmount(receipt.amountCentavos)} (${receipt.currency})")
        sb.appendLine("Status: ${receipt.status.name}")
        sb.appendLine("Description: ${receipt.description}")
        sb.appendLine("Reference: ${receipt.referenceNumber}")
        sb.appendLine("Date: ${formatReceiptTimestamp(receipt.createdAtMillis)}")
        sb.appendLine(
            "Method: " + when (receipt.methodMode) {
                PaymentMethodMode.NFC_TAP_DEMO -> "NFC Tap (Sandbox Demo)"
                PaymentMethodMode.QR_PH_CHECKOUT -> "QR Ph / Checkout"
            }
        )
        sb.appendLine("Gateway: ${receipt.provider.name} (${receipt.environment.name})")
        receipt.bankName?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Bank: $it") }
        receipt.cardNetwork?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Network: $it") }
        receipt.panMasked?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Card: $it") }
        receipt.authCode?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Auth Code: $it") }
        receipt.emvAid?.takeIf { it.isNotBlank() }?.let { sb.appendLine("EMV AID: $it") }
        receipt.emvAtc?.let { sb.appendLine("EMV ATC: $it") }
        receipt.emvCryptogram?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Cryptogram: $it") }
        receipt.checkoutSessionId?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Session ID: $it") }
        receipt.checkoutUrl?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Checkout URL: $it") }
        return sb.toString().trimEnd()
    }

    // ---- Pure-Kotlin request builders & response extractors ----

    internal fun generateReferenceNumber(): String {
        val timePart = System.currentTimeMillis().toString(36).uppercase(Locale.US)
        val suffix = "%04X".format(Locale.US, secureRandom.nextInt(0x10000))
        return "TM-$timePart$suffix"
    }

    internal fun generateAuthCode(): String =
        "%06X".format(Locale.US, secureRandom.nextInt(0x1000000))

    internal fun formatCentavosDecimal(amountCentavos: Long): String {
        val negative = amountCentavos < 0L
        val absCentavos = if (amountCentavos == Long.MIN_VALUE) Long.MAX_VALUE else abs(amountCentavos)
        val whole = absCentavos / 100L
        val cents = absCentavos % 100L
        val sign = if (negative) "-" else ""
        return "$sign$whole.${"%02d".format(Locale.US, cents)}"
    }

    internal fun buildPayMongoCheckoutRequestJson(
        amountCentavos: Long,
        description: String,
        referenceNumber: String
    ): String {
        val lineName = description.trim().ifBlank { DEFAULT_DESCRIPTION }
        val successUrl = "tapmark://payment/success?ref=$referenceNumber"
        val cancelUrl = "tapmark://payment/cancel?ref=$referenceNumber"
        return buildString {
            append("""{"data":{"attributes":{""")
            append(""""send_email_receipt":false,""")
            append(""""show_description":true,""")
            append(""""show_line_items":true,""")
            append(""""description":${jsonString(lineName)},""")
            append(""""line_items":[{"currency":"PHP","amount":$amountCentavos,"name":${jsonString(lineName)},"quantity":1}],""")
            append(""""payment_method_types":["qrph","gcash","paymaya","card"],""")
            append(""""reference_number":${jsonString(referenceNumber)},""")
            append(""""success_url":${jsonString(successUrl)},""")
            append(""""cancel_url":${jsonString(cancelUrl)}""")
            append("}}}")
        }
    }

    internal fun parsePayMongoCheckoutResponse(
        json: String,
        referenceNumber: String,
        environment: GatewayEnvironment
    ): Result<CheckoutSessionResult> {
        val root = parseJsonObject(json)
            ?: return Result.failure(IllegalStateException("Invalid JSON response from PayMongo"))

        extractPayMongoErrorDetail(root)?.let { detail ->
            return Result.failure(IllegalStateException(detail))
        }

        val data = root.obj("data")
            ?: return Result.failure(IllegalStateException("Missing PayMongo checkout data"))
        val sessionId = data.string("id")?.trim().orEmpty()
        val attrs = data.obj("attributes")
        val checkoutUrl = attrs?.string("checkout_url")?.trim().orEmpty()
        val resolvedRef = attrs?.string("reference_number")?.trim()?.takeIf { it.isNotEmpty() }
            ?: referenceNumber

        if (sessionId.isEmpty() || checkoutUrl.isEmpty()) {
            return Result.failure(IllegalStateException("PayMongo response missing session id or checkout_url"))
        }

        return Result.success(
            CheckoutSessionResult(
                sessionId = sessionId,
                referenceNumber = resolvedRef,
                checkoutUrl = checkoutUrl,
                qrPayload = checkoutUrl,
                provider = GatewayProvider.PAYMONGO,
                environment = environment,
                isSimulated = false,
                statusMessage = "PayMongo Checkout ready (${environment.name})"
            )
        )
    }

    internal fun parsePayMongoSessionStatus(json: String): Result<ReceiptStatus> {
        val root = parseJsonObject(json)
            ?: return Result.failure(IllegalStateException("Invalid JSON status response from PayMongo"))

        extractPayMongoErrorDetail(root)?.let { detail ->
            return Result.failure(IllegalStateException(detail))
        }

        val attrs = root.obj("data")?.obj("attributes") ?: root

        // 1. Check recorded payments array inside checkout session attributes
        var sawFailedPayment = false
        val payments = attrs.array("payments")
        if (payments != null) {
            for (item in payments) {
                val paymentObj = asJsonObj(item) ?: continue
                val paymentStatus = paymentObj.obj("attributes")?.string("status")
                    ?: paymentObj.string("status")
                when (mapPayMongoStatusString(paymentStatus)) {
                    ReceiptStatus.PAID -> return Result.success(ReceiptStatus.PAID)
                    ReceiptStatus.FAILED -> sawFailedPayment = true
                    else -> Unit
                }
            }
        }

        // 2. Check payment_intent.attributes.status if attached
        val intentStatus = attrs.obj("payment_intent")?.obj("attributes")?.string("status")
        when (mapPayMongoStatusString(intentStatus)) {
            ReceiptStatus.PAID -> return Result.success(ReceiptStatus.PAID)
            ReceiptStatus.FAILED -> sawFailedPayment = true
            else -> Unit
        }

        // 3. Check direct payment_status / status field
        val directStatus = attrs.string("payment_status") ?: attrs.string("status")
        val mappedDirect = mapPayMongoStatusString(directStatus)
        return Result.success(
            when {
                mappedDirect == ReceiptStatus.PAID -> ReceiptStatus.PAID
                mappedDirect == ReceiptStatus.FAILED || sawFailedPayment -> ReceiptStatus.FAILED
                else -> ReceiptStatus.PENDING
            }
        )
    }

    internal fun buildMayaCheckoutRequestJson(
        amountCentavos: Long,
        description: String,
        referenceNumber: String
    ): String {
        val itemName = description.trim().ifBlank { DEFAULT_DESCRIPTION }
        val amountDecimal = formatCentavosDecimal(amountCentavos)
        val successUrl = "tapmark://payment/success?ref=$referenceNumber"
        val failureUrl = "tapmark://payment/failed?ref=$referenceNumber"
        val cancelUrl = "tapmark://payment/cancel?ref=$referenceNumber"
        return buildString {
            append("{")
            append(""""totalAmount":{"value":${jsonString(amountDecimal)},"currency":"PHP"},""")
            append(""""items":[{"name":${jsonString(itemName)},"quantity":1,"totalAmount":{"value":${jsonString(amountDecimal)},"currency":"PHP"}}],""")
            append(""""requestReferenceNumber":${jsonString(referenceNumber)},""")
            append(""""redirectUrl":{"success":${jsonString(successUrl)},"failure":${jsonString(failureUrl)},"cancel":${jsonString(cancelUrl)}}""")
            append("}")
        }
    }

    internal fun parseMayaCheckoutResponse(
        json: String,
        referenceNumber: String,
        environment: GatewayEnvironment
    ): Result<CheckoutSessionResult> {
        val root = parseJsonObject(json)
            ?: return Result.failure(IllegalStateException("Invalid JSON response from Maya"))

        extractMayaErrorDetail(root)?.let { detail ->
            return Result.failure(IllegalStateException(detail))
        }

        val checkoutId = (root.string("checkoutId") ?: root.string("id"))?.trim().orEmpty()
        val redirectUrl = (root.string("redirectUrl") ?: root.string("redirect_url"))?.trim().orEmpty()
        if (checkoutId.isEmpty() || redirectUrl.isEmpty()) {
            return Result.failure(IllegalStateException("Maya response missing checkoutId or redirectUrl"))
        }

        return Result.success(
            CheckoutSessionResult(
                sessionId = checkoutId,
                referenceNumber = referenceNumber,
                checkoutUrl = redirectUrl,
                qrPayload = redirectUrl,
                provider = GatewayProvider.MAYA,
                environment = environment,
                isSimulated = false,
                statusMessage = "Maya Checkout ready (${environment.name})"
            )
        )
    }

    internal fun buildCustomBackendRequestJson(
        amountCentavos: Long,
        description: String,
        referenceNumber: String,
        provider: GatewayProvider,
        environment: GatewayEnvironment
    ): String {
        val cleanDesc = description.trim().ifBlank { DEFAULT_DESCRIPTION }
        return buildString {
            append("{")
            append(""""amountCentavos":$amountCentavos,""")
            append(""""currency":"PHP",""")
            append(""""description":${jsonString(cleanDesc)},""")
            append(""""referenceNumber":${jsonString(referenceNumber)},""")
            append(""""provider":${jsonString(provider.name)},""")
            append(""""environment":${jsonString(environment.name)}""")
            append("}")
        }
    }

    internal fun parseCustomBackendResponse(
        json: String,
        referenceNumber: String,
        provider: GatewayProvider,
        environment: GatewayEnvironment
    ): Result<CheckoutSessionResult> {
        val root = parseJsonObject(json)
            ?: return Result.failure(IllegalStateException("Invalid JSON response from custom backend"))

        val errorMsg = root.string("error")?.trim()?.takeIf { it.isNotEmpty() }
            ?: root.string("message")?.trim()?.takeIf { it.isNotEmpty() }
            ?: extractPayMongoErrorDetail(root)
        if (errorMsg != null) {
            return Result.failure(IllegalStateException(errorMsg))
        }

        val dataObj = root.obj("data")
        val attrsObj = dataObj?.obj("attributes")

        val checkoutUrl = firstNonBlank(
            root.string("checkoutUrl"),
            root.string("checkout_url"),
            root.string("redirectUrl"),
            root.string("redirect_url"),
            attrsObj?.string("checkout_url"),
            attrsObj?.string("checkoutUrl")
        ).orEmpty()

        val sessionId = firstNonBlank(
            root.string("sessionId"),
            root.string("session_id"),
            root.string("checkoutId"),
            root.string("id"),
            dataObj?.string("id")
        ) ?: referenceNumber

        val qrPayload = firstNonBlank(
            root.string("qrPayload"),
            root.string("qr_payload"),
            attrsObj?.string("qr_payload"),
            attrsObj?.string("qrPayload"),
            checkoutUrl
        ).orEmpty()

        if (qrPayload.isBlank() && checkoutUrl.isBlank()) {
            return Result.failure(
                IllegalStateException("Custom backend response missing checkoutUrl and qrPayload")
            )
        }

        return Result.success(
            CheckoutSessionResult(
                sessionId = sessionId,
                referenceNumber = referenceNumber,
                checkoutUrl = checkoutUrl,
                qrPayload = qrPayload.ifBlank { checkoutUrl },
                provider = provider,
                environment = environment,
                isSimulated = false,
                statusMessage = "Custom Backend Checkout ready (${environment.name})"
            )
        )
    }

    internal fun extractGatewayError(statusCode: Int, responseBody: String): String {
        val root = parseJsonObject(responseBody)
        if (root != null) {
            extractPayMongoErrorDetail(root)?.let { return "HTTP $statusCode: $it" }
            extractMayaErrorDetail(root)?.let { return "HTTP $statusCode: $it" }
            root.string("error")?.trim()?.takeIf { it.isNotEmpty() }?.let { return "HTTP $statusCode: $it" }
            root.string("message")?.trim()?.takeIf { it.isNotEmpty() }?.let { return "HTTP $statusCode: $it" }
        }
        return "Gateway request failed (HTTP $statusCode)"
    }

    internal fun buildDemoQrPhPayload(
        merchantName: String,
        amountCentavos: Long,
        referenceNumber: String
    ): String {
        val cleanMerchant = sanitizeEmvAscii(merchantName).ifBlank { DEFAULT_MERCHANT_NAME }.take(25)
        val cleanRef = sanitizeEmvAscii(referenceNumber).ifBlank { "TM-DEMO" }.take(25)
        val amountStr = formatCentavosDecimal(amountCentavos)

        val merchantAccountInfo = emvQrTag("00", "ph.ppmi.p2m.demo") +
            emvQrTag("01", cleanRef)
        val additionalData = emvQrTag("05", cleanRef)

        val withoutCrc = buildString {
            append(emvQrTag("00", "01"))
            append(emvQrTag("01", "12"))
            append(emvQrTag("27", merchantAccountInfo))
            append(emvQrTag("52", "5999"))
            append(emvQrTag("53", "608"))
            append(emvQrTag("54", amountStr))
            append(emvQrTag("58", "PH"))
            append(emvQrTag("59", cleanMerchant))
            append(emvQrTag("60", "Manila"))
            append(emvQrTag("62", additionalData))
            append("6304")
        }
        val crc = crc16Ccitt(withoutCrc.toByteArray(Charsets.US_ASCII))
        return withoutCrc + "%04X".format(Locale.US, crc)
    }

    private fun mapPayMongoStatusString(raw: String?): ReceiptStatus? {
        val normalized = raw?.trim()?.lowercase(Locale.US) ?: return null
        return when (normalized) {
            "paid", "succeeded" -> ReceiptStatus.PAID
            "failed" -> ReceiptStatus.FAILED
            else -> ReceiptStatus.PENDING
        }
    }

    private fun extractPayMongoErrorDetail(root: JsonObj): String? {
        val errors = root.array("errors") ?: return null
        for (item in errors) {
            val errObj = asJsonObj(item) ?: continue
            val detail = errObj.string("detail")?.trim()?.takeIf { it.isNotEmpty() }
            if (detail != null) return detail
            val code = errObj.string("code")?.trim()?.takeIf { it.isNotEmpty() }
            if (code != null) return code
        }
        return null
    }

    private fun extractMayaErrorDetail(root: JsonObj): String? {
        val message = root.string("message")?.trim()?.takeIf { it.isNotEmpty() }
        if (message != null) return message
        val error = root.string("error")?.trim()?.takeIf { it.isNotEmpty() }
        if (error != null) return error
        val code = root.string("code")?.trim()?.takeIf { it.isNotEmpty() }
        if (code != null && root.string("checkoutId") == null) return code
        return null
    }

    private fun basicAuthHeader(secretOrPublicKey: String): String {
        val raw = "${secretOrPublicKey.trim()}:"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    private data class HttpResponseData(
        val statusCode: Int,
        val body: String
    )

    private fun executeJsonRequest(
        urlString: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ): Result<HttpResponseData> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URI(urlString).toURL().openConnection() as? HttpURLConnection)
                ?: return Result.failure(IllegalStateException("Unable to open HTTP connection"))
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) {
                conn.setRequestProperty(k, v)
            }

            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { out ->
                    out.write(bytes)
                    out.flush()
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseBody = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    val sb = StringBuilder()
                    val buf = CharArray(1024)
                    var total = 0
                    while (true) {
                        val n = reader.read(buf)
                        if (n <= 0) break
                        val allowed = minOf(n, MAX_RESPONSE_BYTES - total)
                        if (allowed <= 0) break
                        sb.append(buf, 0, allowed)
                        total += allowed
                    }
                    sb.toString()
                }
            } else {
                ""
            }
            Result.success(HttpResponseData(statusCode = code, body = responseBody))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun formatReceiptTimestamp(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return formatter.format(Date(millis))
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun emvQrTag(tag: String, value: String): String {
        val bounded = value.take(99)
        return "$tag${"%02d".format(Locale.US, bounded.length)}$bounded"
    }

    private fun sanitizeEmvAscii(input: String): String =
        input.trim().filter { it in '\u0020'..'\u007E' }

    private fun crc16Ccitt(bytes: ByteArray): Int {
        var crc = 0xFFFF
        for (b in bytes) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            for (bit in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    private fun jsonString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u%04X".format(Locale.US, ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // ---- Pure-Kotlin minimal JSON parser (no android.jar org.json dependency) ----

    internal class JsonObj(val map: Map<String, Any?>) {
        fun string(key: String): String? = map[key] as? String
        fun long(key: String): Long? = (map[key] as? Number)?.toLong()
        fun bool(key: String): Boolean? = map[key] as? Boolean

        @Suppress("UNCHECKED_CAST")
        fun obj(key: String): JsonObj? = (map[key] as? Map<String, Any?>)?.let { JsonObj(it) }

        fun array(key: String): List<Any?>? = map[key] as? List<Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun asJsonObj(value: Any?): JsonObj? =
        (value as? Map<String, Any?>)?.let { JsonObj(it) }

    internal fun parseJsonObject(json: String): JsonObj? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) return null
        val parsed = MiniJsonParser(trimmed).parseRoot() ?: return null
        @Suppress("UNCHECKED_CAST")
        return (parsed as? Map<String, Any?>)?.let { JsonObj(it) }
    }

    private class MiniJsonParser(private val src: String) {
        private var pos = 0

        fun parseRoot(): Any? {
            skipWhitespace()
            if (pos >= src.length || src[pos] != '{') return null
            return runCatching { readValue() }.getOrNull()
        }

        private fun readValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalStateException("EOF")
            return when (val ch = src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalStateException("Unexpected char $ch")
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // '{'
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (pos < src.length) {
                skipWhitespace()
                if (pos >= src.length || src[pos] != '"') throw IllegalStateException("Expected key")
                val key = readString()
                skipWhitespace()
                if (pos >= src.length || src[pos] != ':') throw IllegalStateException("Expected ':'")
                pos++
                val value = readValue()
                map[key] = value
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == '}') {
                    pos++
                    return map
                } else {
                    throw IllegalStateException("Expected ',' or '}'")
                }
            }
            throw IllegalStateException("Unterminated object")
        }

        private fun readArray(): List<Any?> {
            pos++ // '['
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (pos < src.length) {
                list.add(readValue())
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == ']') {
                    pos++
                    return list
                } else {
                    throw IllegalStateException("Expected ',' or ']'")
                }
            }
            throw IllegalStateException("Unterminated array")
        }

        private fun readString(): String {
            pos++ // opening '"'
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw IllegalStateException("Dangling escape")
                        when (val esc = src[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw IllegalStateException("Bad unicode")
                                val code = src.substring(pos, pos + 4).toInt(16)
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw IllegalStateException("Unterminated string")
        }

        private fun readBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalStateException("Bad boolean")
            }
        }

        private fun readNull(): Any? {
            if (src.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw IllegalStateException("Bad null")
        }

        private fun readNumber(): Number {
            val start = pos
            while (pos < src.length && src[pos] in "0123456789+-.eE") {
                pos++
            }
            val raw = src.substring(start, pos)
            return if ('.' in raw || 'e' in raw || 'E' in raw) {
                raw.toDoubleOrNull() ?: throw IllegalStateException("Bad decimal: $raw")
            } else {
                raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: throw IllegalStateException("Bad number: $raw")
            }
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
