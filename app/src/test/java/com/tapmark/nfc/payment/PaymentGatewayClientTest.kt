package com.tapmark.nfc.payment

import com.tapmark.nfc.data.SampleCards
import com.tapmark.nfc.model.CheckoutSessionResult
import com.tapmark.nfc.model.GatewayConfig
import com.tapmark.nfc.model.GatewayEnvironment
import com.tapmark.nfc.model.GatewayProvider
import com.tapmark.nfc.model.PaymentMethodMode
import com.tapmark.nfc.model.ReceiptStatus
import com.tapmark.nfc.model.TerminalReceipt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PaymentGatewayClientTest {

    @Test
    fun `formatPhpAmount formats centavos cleanly with comma separators`() {
        assertEquals("₱0.00", PaymentGatewayClient.formatPhpAmount(0L))
        assertEquals("₱0.50", PaymentGatewayClient.formatPhpAmount(50L))
        assertEquals("₱250.00", PaymentGatewayClient.formatPhpAmount(25000L))
        assertEquals("₱1,250.50", PaymentGatewayClient.formatPhpAmount(125050L))
        assertEquals("₱10,000.00", PaymentGatewayClient.formatPhpAmount(1000000L))
        assertEquals("-₱12.50", PaymentGatewayClient.formatPhpAmount(-1250L))
    }

    @Test
    fun `builds PayMongo checkout request JSON with required attributes and line items`() {
        val json = PaymentGatewayClient.buildPayMongoCheckoutRequestJson(
            amountCentavos = 25000L,
            description = "Iced Latte \"Large\"",
            referenceNumber = "TM-9A4C1F"
        )
        val root = PaymentGatewayClient.parseJsonObject(json)
        assertNotNull(root)
        val attrs = root!!.obj("data")?.obj("attributes")
        assertNotNull(attrs)
        assertEquals(false, attrs!!.bool("send_email_receipt"))
        assertEquals(true, attrs.bool("show_description"))
        assertEquals(true, attrs.bool("show_line_items"))
        assertEquals("Iced Latte \"Large\"", attrs.string("description"))
        assertEquals("TM-9A4C1F", attrs.string("reference_number"))
        assertEquals("tapmark://payment/success?ref=TM-9A4C1F", attrs.string("success_url"))
        assertEquals("tapmark://payment/cancel?ref=TM-9A4C1F", attrs.string("cancel_url"))

        val methods = attrs.array("payment_method_types")
        assertEquals(listOf("qrph", "gcash", "paymaya", "card"), methods)

        val lineItems = attrs.array("line_items")
        assertNotNull(lineItems)
        assertEquals(1, lineItems!!.size)
        @Suppress("UNCHECKED_CAST")
        val item = PaymentGatewayClient.JsonObj(lineItems[0] as Map<String, Any?>)
        assertEquals("PHP", item.string("currency"))
        assertEquals(25000L, item.long("amount"))
        assertEquals("Iced Latte \"Large\"", item.string("name"))
        assertEquals(1L, item.long("quantity"))
    }

    @Test
    fun `parses PayMongo checkout response and error payloads`() {
        val successJson = """
            {
              "data": {
                "id": "cs_test_abc123",
                "type": "checkout_session",
                "attributes": {
                  "checkout_url": "https://checkout.paymongo.com/cs_test_abc123",
                  "reference_number": "TM-9A4C1F"
                }
              }
            }
        """.trimIndent()

        val result = PaymentGatewayClient.parsePayMongoCheckoutResponse(
            json = successJson,
            referenceNumber = "TM-9A4C1F",
            environment = GatewayEnvironment.SANDBOX
        )
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("cs_test_abc123", session.sessionId)
        assertEquals("TM-9A4C1F", session.referenceNumber)
        assertEquals("https://checkout.paymongo.com/cs_test_abc123", session.checkoutUrl)
        assertEquals("https://checkout.paymongo.com/cs_test_abc123", session.qrPayload)
        assertEquals(GatewayProvider.PAYMONGO, session.provider)
        assertEquals(GatewayEnvironment.SANDBOX, session.environment)
        assertFalse(session.isSimulated)

        val errorJson = """
            {
              "errors": [
                {
                  "code": "parameter_below_minimum",
                  "detail": "Amount cannot be less than 2000 centavos."
                }
              ]
            }
        """.trimIndent()
        val failure = PaymentGatewayClient.parsePayMongoCheckoutResponse(
            json = errorJson,
            referenceNumber = "TM-9A4C1F",
            environment = GatewayEnvironment.SANDBOX
        )
        assertTrue(failure.isFailure)
        assertEquals("Amount cannot be less than 2000 centavos.", failure.exceptionOrNull()?.message)
        assertEquals(
            "HTTP 400: Amount cannot be less than 2000 centavos.",
            PaymentGatewayClient.extractGatewayError(400, errorJson)
        )
    }

    @Test
    fun `parses PayMongo session status across paid, awaiting_payment_method, failed, and payments array`() {
        val paidJson = """{"data":{"id":"cs_1","attributes":{"status":"paid"}}}"""
        assertEquals(
            ReceiptStatus.PAID,
            PaymentGatewayClient.parsePayMongoSessionStatus(paidJson).getOrThrow()
        )

        val pendingJson = """{"data":{"id":"cs_1","attributes":{"status":"awaiting_payment_method"}}}"""
        assertEquals(
            ReceiptStatus.PENDING,
            PaymentGatewayClient.parsePayMongoSessionStatus(pendingJson).getOrThrow()
        )

        val failedJson = """{"data":{"id":"cs_1","attributes":{"status":"failed"}}}"""
        assertEquals(
            ReceiptStatus.FAILED,
            PaymentGatewayClient.parsePayMongoSessionStatus(failedJson).getOrThrow()
        )

        val nestedPaidJson = """
            {
              "data": {
                "id": "cs_1",
                "attributes": {
                  "status": "active",
                  "payments": [
                    { "attributes": { "status": "failed" } },
                    { "attributes": { "status": "paid" } }
                  ]
                }
              }
            }
        """.trimIndent()
        assertEquals(
            ReceiptStatus.PAID,
            PaymentGatewayClient.parsePayMongoSessionStatus(nestedPaidJson).getOrThrow()
        )

        val intentSucceededJson = """
            {
              "data": {
                "id": "cs_1",
                "attributes": {
                  "status": "active",
                  "payment_intent": {
                    "attributes": { "status": "succeeded" }
                  }
                }
              }
            }
        """.trimIndent()
        assertEquals(
            ReceiptStatus.PAID,
            PaymentGatewayClient.parsePayMongoSessionStatus(intentSucceededJson).getOrThrow()
        )
    }

    @Test
    fun `builds Maya checkout request JSON with decimal string value and parses response`() {
        val json = PaymentGatewayClient.buildMayaCheckoutRequestJson(
            amountCentavos = 25050L,
            description = "",
            referenceNumber = "TM-MAYA01"
        )
        val root = PaymentGatewayClient.parseJsonObject(json)
        assertNotNull(root)
        val totalAmount = root!!.obj("totalAmount")
        assertNotNull(totalAmount)
        assertEquals("250.50", totalAmount!!.string("value"))
        assertEquals("PHP", totalAmount.string("currency"))
        assertEquals("TM-MAYA01", root.string("requestReferenceNumber"))

        val redirectUrl = root.obj("redirectUrl")
        assertNotNull(redirectUrl)
        assertEquals("tapmark://payment/success?ref=TM-MAYA01", redirectUrl!!.string("success"))
        assertEquals("tapmark://payment/failed?ref=TM-MAYA01", redirectUrl.string("failure"))
        assertEquals("tapmark://payment/cancel?ref=TM-MAYA01", redirectUrl.string("cancel"))

        val items = root.array("items")
        assertNotNull(items)
        @Suppress("UNCHECKED_CAST")
        val item = PaymentGatewayClient.JsonObj(items!![0] as Map<String, Any?>)
        assertEquals("Tapmark Terminal Sale", item.string("name"))
        assertEquals(1L, item.long("quantity"))
        assertEquals("250.50", item.obj("totalAmount")?.string("value"))

        val mayaResponse = """
            {
              "checkoutId": "maya-chk-9981",
              "redirectUrl": "https://payments-sandbox.maya.ph/checkout?id=maya-chk-9981"
            }
        """.trimIndent()
        val parsed = PaymentGatewayClient.parseMayaCheckoutResponse(
            json = mayaResponse,
            referenceNumber = "TM-MAYA01",
            environment = GatewayEnvironment.SANDBOX
        )
        assertTrue(parsed.isSuccess)
        val session = parsed.getOrThrow()
        assertEquals("maya-chk-9981", session.sessionId)
        assertEquals("https://payments-sandbox.maya.ph/checkout?id=maya-chk-9981", session.checkoutUrl)
        assertEquals("https://payments-sandbox.maya.ph/checkout?id=maya-chk-9981", session.qrPayload)
        assertEquals(GatewayProvider.MAYA, session.provider)
        assertFalse(session.isSimulated)
    }

    @Test
    fun `validates PayMongo minimum amount and non-positive amounts`() = runBlocking {
        val zeroResult = PaymentGatewayClient.createCheckoutSession(
            config = GatewayConfig(),
            amountCentavos = 0L,
            description = "Test"
        )
        assertTrue(zeroResult.isFailure)

        val payMongoUnderMin = PaymentGatewayClient.createCheckoutSession(
            config = GatewayConfig(
                provider = GatewayProvider.PAYMONGO,
                payMongoApiKey = "sk_test_dummy"
            ),
            amountCentavos = 1999L,
            description = "Small item"
        )
        assertTrue(payMongoUnderMin.isFailure)
        assertEquals("PayMongo requires a minimum of ₱20.00", payMongoUnderMin.exceptionOrNull()?.message)
    }

    @Test
    fun `Sandbox Demo fallback returns simulated QR Ph EMVCo payload`() = runBlocking {
        val config = GatewayConfig(
            provider = GatewayProvider.DEMO_SANDBOX,
            environment = GatewayEnvironment.SANDBOX,
            merchantName = "Cafe Tapmark"
        )
        val result = PaymentGatewayClient.createCheckoutSession(
            config = config,
            amountCentavos = 25000L,
            description = "Matcha Latte"
        )
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertTrue(session.isSimulated)
        assertEquals("", session.checkoutUrl)
        assertTrue(session.referenceNumber.startsWith("TM-"))
        assertTrue(session.qrPayload.startsWith("000201010212"))
        assertTrue(session.qrPayload.contains("5303608"))
        assertTrue(session.qrPayload.contains("250.00"))
        assertTrue(session.qrPayload.contains("Cafe Tapmark"))
        assertTrue(session.qrPayload.contains(session.referenceNumber))
        assertTrue(session.statusMessage.contains("Sandbox QR Preview"))

        // Also falls back to simulated preview if PayMongo/Maya selected without API key
        val blankKeyFallback = PaymentGatewayClient.createCheckoutSession(
            config = GatewayConfig(
                provider = GatewayProvider.PAYMONGO,
                payMongoApiKey = ""
            ),
            amountCentavos = 5000L,
            description = "Pastry"
        )
        assertTrue(blankKeyFallback.isSuccess)
        assertTrue(blankKeyFallback.getOrThrow().isSimulated)
    }

    @Test
    fun `custom backend proxy parses snake_case and camelCase checkout fields over HTTP`() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 3000
        val ready = CountDownLatch(1)
        var capturedBody = ""

        val thread = Thread {
            ready.countDown()
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                if (contentLength > 0) {
                    val chars = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = reader.read(chars, read, contentLength - read)
                        if (n <= 0) break
                        read += n
                    }
                    capturedBody = String(chars, 0, read)
                }
                val responseJson =
                    """{"session_id":"proxy-sess-42","checkout_url":"https://pay.example.test/c/42"}"""
                val httpResponse = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: ${responseJson.toByteArray(Charsets.UTF_8).size}\r\n")
                    append("Connection: close\r\n\r\n")
                    append(responseJson)
                }
                socket.getOutputStream().write(httpResponse.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            }
        }.apply {
            isDaemon = true
            start()
        }

        try {
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            val config = GatewayConfig(
                provider = GatewayProvider.PAYMONGO,
                environment = GatewayEnvironment.SANDBOX,
                customBackendUrl = "http://127.0.0.1:${server.localPort}/checkout"
            )
            val result = PaymentGatewayClient.createCheckoutSession(
                config = config,
                amountCentavos = 15000L,
                description = "Proxy Order"
            )
            assertTrue(result.isSuccess)
            val session = result.getOrThrow()
            assertEquals("proxy-sess-42", session.sessionId)
            assertEquals("https://pay.example.test/c/42", session.checkoutUrl)
            assertEquals("https://pay.example.test/c/42", session.qrPayload)
            assertFalse(session.isSimulated)

            val sentJson = PaymentGatewayClient.parseJsonObject(capturedBody)
            assertNotNull(sentJson)
            assertEquals(15000L, sentJson!!.long("amountCentavos"))
            assertEquals("PHP", sentJson.string("currency"))
            assertEquals("Proxy Order", sentJson.string("description"))
            assertEquals(session.referenceNumber, sentJson.string("referenceNumber"))
        } finally {
            runCatching { server.close() }
            runCatching { thread.join(1000) }
        }
    }

    @Test
    fun `buildNfcDemoReceipt, buildQrReceipt, and buildReceiptShareText populate fields safely`() {
        val bpiCard = SampleCards.all[0]
        val transitCard = SampleCards.all[5]
        val config = GatewayConfig(
            provider = GatewayProvider.PAYMONGO,
            environment = GatewayEnvironment.SANDBOX,
            merchantName = "Tapmark Bakery",
            payMongoApiKey = "sk_test_secret_should_never_export"
        )

        val nfcReceipt = PaymentGatewayClient.buildNfcDemoReceipt(
            card = bpiCard,
            amountCentavos = 45000L,
            description = "Sourdough Loaf",
            config = config
        )
        assertEquals(PaymentMethodMode.NFC_TAP_DEMO, nfcReceipt.methodMode)
        assertEquals(ReceiptStatus.APPROVED, nfcReceipt.status)
        assertTrue(nfcReceipt.isSandboxDemo)
        assertEquals(45000L, nfcReceipt.amountCentavos)
        assertEquals("Tapmark Bakery", nfcReceipt.merchantName)
        assertEquals("Bank of the Philippine Islands", nfcReceipt.bankName)
        assertEquals("Visa", nfcReceipt.cardNetwork)
        assertEquals("4537 •••• •••• 8911", nfcReceipt.panMasked)
        assertEquals(bpiCard.visualStyle, nfcReceipt.cardVisualStyle)
        assertEquals("A0000000031010", nfcReceipt.emvAid)
        assertEquals(142, nfcReceipt.emvAtc)
        assertEquals("7A3C19F4028E5B11", nfcReceipt.emvCryptogram)
        assertNotNull(nfcReceipt.authCode)
        assertEquals(6, nfcReceipt.authCode!!.length)
        assertTrue(nfcReceipt.referenceNumber.startsWith("TM-"))

        val nonEmvReceipt = PaymentGatewayClient.buildNfcDemoReceipt(
            card = transitCard,
            amountCentavos = 10000L,
            description = "",
            config = config
        )
        assertEquals("MetroLink Transit Pass", nonEmvReceipt.bankName)
        assertEquals("TRANSIT", nonEmvReceipt.cardNetwork)
        assertEquals(transitCard.uidHex, nonEmvReceipt.panMasked)
        assertEquals("Tapmark Terminal Sale", nonEmvReceipt.description)
        assertNull(nonEmvReceipt.emvAid)

        val qrSession = CheckoutSessionResult(
            sessionId = "cs_qr_77",
            referenceNumber = "TM-QR77",
            checkoutUrl = "https://checkout.paymongo.com/cs_qr_77",
            qrPayload = "https://checkout.paymongo.com/cs_qr_77",
            provider = GatewayProvider.PAYMONGO,
            environment = GatewayEnvironment.SANDBOX,
            isSimulated = false,
            statusMessage = "PayMongo Checkout ready (SANDBOX)"
        )
        val qrReceipt = PaymentGatewayClient.buildQrReceipt(
            session = qrSession,
            amountCentavos = 125050L,
            description = "Pastry Box",
            config = config
        )
        assertEquals(PaymentMethodMode.QR_PH_CHECKOUT, qrReceipt.methodMode)
        assertEquals(ReceiptStatus.PENDING, qrReceipt.status)
        assertEquals("TM-QR77", qrReceipt.referenceNumber)
        assertEquals("cs_qr_77", qrReceipt.checkoutSessionId)
        assertEquals("https://checkout.paymongo.com/cs_qr_77", qrReceipt.checkoutUrl)
        assertFalse(qrReceipt.isSandboxDemo)

        val shareText = PaymentGatewayClient.buildReceiptShareText(nfcReceipt)
        assertTrue(shareText.contains("Tapmark Terminal Receipt (SANDBOX DEMO)"))
        assertTrue(shareText.contains("Tapmark Bakery"))
        assertTrue(shareText.contains("₱450.00"))
        assertTrue(shareText.contains("Bank of the Philippine Islands"))
        assertTrue(shareText.contains("4537 •••• •••• 8911"))
        assertTrue(shareText.contains(nfcReceipt.referenceNumber))
        assertFalse(shareText.contains("sk_test_secret_should_never_export"))
    }

    @Test
    fun `todayTotalCentavos sums only APPROVED and PAID receipts on current local calendar day`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = now - 24 * 60 * 60 * 1000L

        fun receipt(id: String, amount: Long, status: ReceiptStatus, ts: Long) = TerminalReceipt(
            id = id,
            referenceNumber = "TM-$id",
            createdAtMillis = ts,
            amountCentavos = amount,
            description = "Sale",
            merchantName = "Tapmark Terminal",
            provider = GatewayProvider.DEMO_SANDBOX,
            environment = GatewayEnvironment.SANDBOX,
            methodMode = PaymentMethodMode.NFC_TAP_DEMO,
            status = status,
            isSandboxDemo = true
        )

        val list = listOf(
            receipt("1", 25000L, ReceiptStatus.APPROVED, now),
            receipt("2", 15050L, ReceiptStatus.PAID, now),
            receipt("3", 99900L, ReceiptStatus.PENDING, now),
            receipt("4", 50000L, ReceiptStatus.FAILED, now),
            receipt("5", 30000L, ReceiptStatus.CANCELLED, now),
            receipt("6", 80000L, ReceiptStatus.APPROVED, yesterday)
        )

        assertEquals(40050L, TerminalReceiptRepository.computeTodayTotalCentavos(list, now))
    }
}
