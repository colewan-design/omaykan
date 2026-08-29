package com.omaykan.storefront.core.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.omaykan.storefront.core.model.Cart
import com.omaykan.storefront.core.model.CartLine
import com.omaykan.storefront.core.model.Contact
import com.omaykan.storefront.core.model.DeliveryDestination
import com.omaykan.storefront.core.model.FulfillmentMethod
import com.omaykan.storefront.core.model.PaymentPreference
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.ProductKind
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.core.network.ApiCaller
import com.omaykan.storefront.core.network.ApiException
import com.omaykan.storefront.core.network.OmaykanApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class OrderRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: OrderRepository
    private val json = Json { ignoreUnknownKeys = true }

    private val ref = StoreRef("demo-coffee", "main")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OmaykanApi::class.java)

        repository = OrderRepository(api, ApiCaller(json))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun product(id: String, priceCents: Long) = Product(
        id = id,
        categoryId = "c1",
        sku = "",
        barcode = "",
        name = "Barako",
        priceCents = priceCents,
        compareAtPriceCents = null,
        taxRate = 0.0,
        kind = ProductKind.Standard,
        imageUrl = null,
        unitLabel = null,
        stockQty = null,
        lowStockThreshold = null,
    )

    private fun cart(vararg lines: CartLine) = Cart(ref = ref, lines = lines.toList())

    @Test
    fun `the request carries ids and quantities and nothing about money`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"orderId":"abc","ticketNumber":"1042","totalCents":13000,"deliveryFeeCents":0}""",
            ),
        )

        repository.place(
            cart = cart(CartLine(product("p1", 6500), 2.0)),
            businessMode = "coffee-shop",
            contact = Contact("Ana", "09171234567", ""),
            method = FulfillmentMethod.Pickup,
            destination = null,
            payment = PaymentPreference.Cash,
        )

        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val item = body["items"]!!.jsonArray.first().jsonObject

        assertEquals("p1", item["productId"]!!.jsonPrimitive.content)
        assertEquals(2.0, item["quantity"]!!.jsonPrimitive.content.toDouble(), 0.0)
        // Nothing the client says about price is trusted, so nothing is sent.
        assertNull(item["priceCents"])
        assertNull(body["totalCents"])
        assertNull(body["subtotalCents"])
    }

    @Test
    fun `the response's totals are returned, not the cart's`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"orderId":"abc","ticketNumber":"1042","totalCents":9900,"deliveryFeeCents":4900}""",
            ),
        )

        // The phone would have said ₱65. The shop says ₱99, delivery included.
        val placed = repository.place(
            cart = cart(CartLine(product("p1", 6500), 1.0)),
            businessMode = "grocery",
            contact = Contact("Ana", "09171234567", ""),
            method = FulfillmentMethod.Delivery,
            destination = DeliveryDestination("12 Session Road"),
            payment = PaymentPreference.Cash,
        )

        assertEquals(9900, placed.totalCents)
        assertEquals(4900, placed.deliveryFeeCents)
    }

    @Test
    fun `an address with no pin sends no coordinates at all`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"orderId":"abc","totalCents":100}"""),
        )

        repository.place(
            cart = cart(CartLine(product("p1", 100), 1.0)),
            businessMode = "grocery",
            contact = Contact("Ana", "", "ana@example.com"),
            method = FulfillmentMethod.Delivery,
            // No geocoder exists, so a typed address never carries a pin.
            destination = DeliveryDestination("12 Session Road", lat = null, lng = null),
            payment = PaymentPreference.Cash,
        )

        val fulfillment = json.parseToJsonElement(server.takeRequest().body.readUtf8())
            .jsonObject["fulfillment"]!!
            .jsonObject

        assertEquals("12 Session Road", fulfillment["address"]!!.jsonPrimitive.content)
        // Half a coordinate is a 422, and would also dodge the distance surcharge.
        assertNull(fulfillment["lat"])
        assertNull(fulfillment["lng"])
    }

    @Test
    fun `an out-of-range delivery comes back keyed on the address field`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(422).setBody(
                """
                {"message":"The given data was invalid.",
                 "errors":{"fulfillment.address":["We do not deliver that far yet."]}}
                """.trimIndent(),
            ),
        )

        val failure = runCatching {
            repository.place(
                cart = cart(CartLine(product("p1", 100), 1.0)),
                businessMode = "grocery",
                contact = Contact("Ana", "09171234567", ""),
                method = FulfillmentMethod.Delivery,
                destination = DeliveryDestination("Somewhere far"),
                payment = PaymentPreference.Cash,
            )
        }.exceptionOrNull()

        assertTrue(failure is ApiException.Validation)
        assertEquals(
            "We do not deliver that far yet.",
            (failure as ApiException.Validation).first("fulfillment.address"),
        )
    }

    @Test
    fun `a guest order sends no auth header`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"orderId":"abc","totalCents":100}"""),
        )

        repository.place(
            cart = cart(CartLine(product("p1", 100), 1.0)),
            businessMode = "grocery",
            contact = Contact("Ana", "09171234567", ""),
            method = FulfillmentMethod.Pickup,
            destination = null,
            payment = PaymentPreference.Ewallet,
        )

        // Checkout is open to a guest — the endpoint reads a customer token
        // when there is one and takes the order without one. Nothing here
        // should ever start requiring a sign-in.
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `the payment preference travels as the wire value`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"orderId":"abc","totalCents":100}"""),
        )

        repository.place(
            cart = cart(CartLine(product("p1", 100), 1.0)),
            businessMode = "grocery",
            contact = Contact("Ana", "09171234567", ""),
            method = FulfillmentMethod.Pickup,
            destination = null,
            payment = PaymentPreference.Ewallet,
        )

        val body: JsonObject = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("ewallet", body["paymentMethod"]!!.jsonPrimitive.content)
    }
}
