package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.Catalog
import com.omaykan.seller.core.model.ProductDraft
import com.omaykan.seller.core.network.ApiBaseUrl
import com.omaykan.seller.core.network.ApiCaller
import com.omaykan.seller.core.network.ApiException
import com.omaykan.seller.core.network.SellerApi
import com.omaykan.seller.core.network.dto.ProductImageRequestDto
import com.omaykan.seller.core.network.dto.SyncPushRequestDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogState(
    val catalog: Catalog? = null,
    val loading: Boolean = false,
    /** The last load that failed. Beside the last good catalog, never instead of it. */
    val error: String? = null,
)

/**
 * What the shop sells, read and written the way the register does it.
 *
 * There is no product API for a phone. The till's own sync endpoints are the
 * catalog's only door — `GET /sync/bootstrap` to read, `POST /sync/push` to
 * write — and this uses them as they are rather than growing a second path to
 * the same rows that the register would then disagree with.
 *
 * Held in memory and nowhere else, like the orders: a cached catalog is a
 * price the merchant would quote and the till would not charge. It loads when
 * a screen first needs it and again after every save.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: SellerApi,
    private val caller: ApiCaller,
    @ApiBaseUrl private val baseUrl: String,
) {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val loading = Mutex()

    suspend fun refresh() = loading.withLock {
        _state.update { it.copy(loading = true) }
        try {
            val catalog = caller.call { api.bootstrap() }.toCatalog(baseUrl)
            _state.value = CatalogState(catalog = catalog)
        } catch (e: ApiException) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    /** Load once; the screens call this on the way in. */
    suspend fun ensureLoaded() {
        if (_state.value.catalog == null && !loading.isLocked) refresh()
    }

    /**
     * Send the form to the server and read the catalog back.
     *
     * A push answers 200 even when an event inside it failed — the till's
     * outbox retries failures on its own schedule — so the per-event result is
     * what says whether the product was saved. A failure is raised with the
     * server's own sentence, and the catalog is not re-read, so the form keeps
     * what the merchant typed.
     */
    suspend fun save(draft: ProductDraft) {
        val catalog = _state.value.catalog
            ?: throw ApiException.Validation("The product list has not loaded yet.", emptyMap())

        val events = productEvents(
            catalog = catalog,
            draft = draft,
            newId = { UUID.randomUUID().toString() },
            now = OffsetDateTime.now().toString(),
        )

        val reply = caller.call {
            api.push(SyncPushRequestDto(catalog.organizationId, catalog.storeId, events))
        }

        // Refused outright: this person's role may not change the catalog. The
        // Save button is hidden from cashiers, but a role can change while the
        // app is open, and the server is where the rule actually lives.
        reply.results.firstOrNull { it.status == "rejected" }?.let { rejected ->
            throw ApiException.Forbidden(
                rejected.message?.takeIf { it.isNotBlank() } ?: "Your role cannot change products.",
            )
        }

        reply.results.firstOrNull { it.status == "failed" }?.let { failed ->
            throw ApiException.Validation(
                failed.message?.takeIf { it.isNotBlank() } ?: "The server did not save that product.",
                emptyMap(),
            )
        }

        refresh()
    }

    /**
     * Upload a picked product photo, as a data URL, and return the URL the
     * product should point at. Uploaded before the save so the product event
     * carries a URL rather than kilobytes of base64. A photo uploaded for a
     * product that is then never saved is swept by the server within a week.
     */
    suspend fun uploadPhoto(dataUrl: String): String =
        caller.call { api.uploadProductImage(ProductImageRequestDto(dataUrl)) }.url

    /** The shop changed under the app. Nothing of the last one may show. */
    fun clear() {
        _state.value = CatalogState()
    }
}
