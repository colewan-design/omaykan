package com.omaykan.seller.feature.products

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.seller.core.data.CatalogRepository
import com.omaykan.seller.core.data.PhotoEncoder
import com.omaykan.seller.core.model.Category
import com.omaykan.seller.core.model.Product
import com.omaykan.seller.core.model.ProductDraft
import com.omaykan.seller.core.model.StorefrontFields
import com.omaykan.seller.core.model.formatQuantity
import com.omaykan.seller.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/** What the merchant has typed, as typed. Parsed only on save. */
data class ProductForm(
    val name: String = "",
    val categoryId: String? = null,
    val price: String = "",
    val stock: String = "",
    val lowStock: String = "",
    val active: Boolean = true,
    /** The primary photo's URL — already uploaded — or null for none. */
    val imageUrl: String? = null,
    /** "kg", "pack", "bundle" — what one of the price buys. */
    val unit: String = "",
    val description: String = "",
)

data class ProductEditorUiState(
    val loading: Boolean = true,
    /** The catalog could not be read, or the product is gone. Nothing to edit. */
    val loadError: String? = null,
    val isNew: Boolean = true,
    val product: Product? = null,
    val categories: List<Category> = emptyList(),
    val canEdit: Boolean = false,
    val form: ProductForm = ProductForm(),
    val nameError: String? = null,
    val priceError: String? = null,
    val stockError: String? = null,
    val lowStockError: String? = null,
    val saving: Boolean = false,
    /** A picked photo on its way up. Save waits for it rather than racing it. */
    val uploadingPhoto: Boolean = false,
    val photoError: String? = null,
    /** The server's refusal of the save, in its own words. */
    val error: String? = null,
    val saved: Boolean = false,
) {
    /** A new product is always stock-tracked; an existing one keeps its setting. */
    val tracksStock: Boolean get() = product?.trackInventory ?: true
}

/**
 * Adding a product, or changing one.
 *
 * The form is seeded once from the catalog and then belongs to the merchant:
 * a catalog refresh landing while they type must not put the old price back
 * under their thumb.
 */
@HiltViewModel
class ProductEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val photos: PhotoEncoder,
) : ViewModel() {

    private val productId: String? = savedStateHandle.get<String>("id")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(ProductEditorUiState(isNew = productId == null))
    val state: StateFlow<ProductEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            catalog.ensureLoaded()
            catalog.state.collect { loaded ->
                if (!_state.value.loading) return@collect

                val current = loaded.catalog
                if (current == null) {
                    // Still loading, or failed with nothing to show.
                    if (loaded.error != null && !loaded.loading) {
                        _state.update { it.copy(loading = false, loadError = loaded.error) }
                    }
                    return@collect
                }

                val product = productId?.let { id -> current.products.firstOrNull { it.id == id } }
                if (productId != null && product == null) {
                    _state.update {
                        it.copy(loading = false, loadError = "That product is no longer in your catalog.")
                    }
                    return@collect
                }

                _state.update {
                    it.copy(
                        loading = false,
                        loadError = null,
                        product = product,
                        categories = current.categories,
                        canEdit = current.role.managesCatalog,
                        form = product?.toForm() ?: ProductForm(categoryId = current.categories.firstOrNull()?.id),
                    )
                }
            }
        }
    }

    fun onName(value: String) = edit(clear = { copy(nameError = null) }) { copy(name = value) }

    fun onCategory(id: String?) = edit { copy(categoryId = id) }

    fun onPrice(value: String) = edit(clear = { copy(priceError = null) }) { copy(price = value) }

    fun onStock(value: String) = edit(clear = { copy(stockError = null) }) { copy(stock = value) }

    fun onLowStock(value: String) = edit(clear = { copy(lowStockError = null) }) { copy(lowStock = value) }

    fun onActive(value: Boolean) = edit { copy(active = value) }

    fun onUnit(value: String) = edit { copy(unit = value) }

    fun onDescription(value: String) = edit { copy(description = value.take(DESCRIPTION_MAX)) }

    fun onRemovePhoto() = edit { copy(imageUrl = null) }

    /**
     * Scale the picked photo down on the phone, upload it, and hold its URL.
     *
     * Uploaded now rather than at Save so the save is one small sync event, and
     * so a photo the server refuses is said beside the photo, not as a failed
     * save. `PhotoEncoder` is the one the shop photo already uses: 1280px on the
     * long edge, JPEG.
     */
    fun onPhotoPicked(uri: Uri) {
        if (_state.value.uploadingPhoto || !_state.value.canEdit) return
        _state.update { it.copy(uploadingPhoto = true, photoError = null) }

        viewModelScope.launch {
            try {
                val url = catalog.uploadPhoto(photos.jpegDataUrl(uri))
                _state.update { it.copy(uploadingPhoto = false, form = it.form.copy(imageUrl = url)) }
            } catch (e: ApiException) {
                _state.update { it.copy(uploadingPhoto = false, photoError = e.message) }
            } catch (_: IOException) {
                _state.update { it.copy(uploadingPhoto = false, photoError = "That photo couldn't be read. Try another one.") }
            }
        }
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.uploadingPhoto || !current.canEdit || current.loading) return

        val form = current.form
        val cents = parsePriceCents(form.price)
        val stock = form.stock.trim().takeIf { it.isNotEmpty() }?.replace(",", "")?.toDoubleOrNull()
        val lowStock = form.lowStock.trim().takeIf { it.isNotEmpty() }?.replace(",", "")?.toDoubleOrNull()

        val nameError = if (form.name.isBlank()) "Give the product a name." else null
        val priceError = when {
            form.price.isBlank() -> "Enter a price."
            cents == null -> "Enter a price like 180 or 180.50."
            else -> null
        }
        val stockError = when {
            !current.tracksStock -> null
            form.stock.isBlank() -> "Enter how many you have."
            stock == null || stock < 0 -> "Enter a number, like 50 or 1.5."
            else -> null
        }
        val lowStockError = if (form.lowStock.isNotBlank() && (lowStock == null || lowStock < 0)) {
            "Enter a number, or leave it empty for the default of 5."
        } else {
            null
        }

        if (listOfNotNull(nameError, priceError, stockError, lowStockError).isNotEmpty()) {
            _state.update {
                it.copy(
                    nameError = nameError,
                    priceError = priceError,
                    stockError = stockError,
                    lowStockError = lowStockError,
                )
            }
            return
        }

        _state.update { it.copy(saving = true, error = null) }

        viewModelScope.launch {
            try {
                catalog.save(
                    ProductDraft(
                        productId = productId,
                        name = form.name,
                        categoryId = form.categoryId,
                        priceCents = cents!!,
                        stockQty = if (current.tracksStock) stock else null,
                        lowStockThreshold = lowStock,
                        active = form.active,
                        storefront = StorefrontFields(
                            imageUrl = form.imageUrl,
                            unitLabel = form.unit,
                            description = form.description,
                        ),
                    ),
                )
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    private fun edit(
        clear: ProductEditorUiState.() -> ProductEditorUiState = { this },
        change: ProductForm.() -> ProductForm,
    ) = _state.update { it.clear().copy(form = it.form.change(), error = null) }
}

private fun Product.toForm() = ProductForm(
    name = name,
    categoryId = categoryId,
    price = centsToInput(priceCents),
    stock = stockQty?.let(::formatQuantity).orEmpty(),
    lowStock = lowStockThreshold?.let(::formatQuantity).orEmpty(),
    active = active,
    imageUrl = imageUrl,
    unit = unitLabel.orEmpty(),
    description = description.orEmpty(),
)

/** SyncController::DESCRIPTION_MAX. Longer is cut there anyway; stopping here says so. */
internal const val DESCRIPTION_MAX = 2000

/** 18000 → "180", 18050 → "180.50" — what a merchant would type. */
internal fun centsToInput(cents: Long): String =
    if (cents % 100 == 0L) {
        (cents / 100).toString()
    } else {
        BigDecimal.valueOf(cents, 2).toPlainString()
    }

/**
 * "180", "180.5", "1,250.00" → cents. Null for anything else, including a
 * negative price or more than two decimal places — "180.555" is a typo, not a
 * price to round silently.
 */
internal fun parsePriceCents(text: String): Long? {
    val value = text.trim().replace(",", "").toBigDecimalOrNull() ?: return null
    if (value.signum() < 0 || value.scale() > 2) return null
    return runCatching { value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
}
