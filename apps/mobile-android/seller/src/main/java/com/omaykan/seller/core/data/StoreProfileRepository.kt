package com.omaykan.seller.core.data

import com.omaykan.seller.core.model.PairedStore
import com.omaykan.seller.core.model.StoreProfile
import com.omaykan.seller.core.network.ApiBaseUrl
import com.omaykan.seller.core.network.ApiCaller
import com.omaykan.seller.core.network.SellerApi
import com.omaykan.seller.core.network.dto.StoreImageRequestDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shop's public face: its address, pin, business type and photo.
 *
 * Read from the public directory rather than a seller endpoint because that
 * is the only place the backend publishes them to anyone but the register —
 * and what the directory says is, by definition, what customers see. Held as
 * a flow so the home screen's store card picks up a new photo the moment the
 * account tab uploads one, instead of showing Coil's cached copy of the old.
 */
@Singleton
class StoreProfileRepository @Inject constructor(
    private val api: SellerApi,
    private val caller: ApiCaller,
    @ApiBaseUrl private val baseUrl: String,
) {
    private val _profile = MutableStateFlow<StoreProfile?>(null)
    val profile: StateFlow<StoreProfile?> = _profile.asStateFlow()

    suspend fun load(store: PairedStore) {
        val row = caller.call { api.storeDirectory() }.stores.firstOrNull {
            store.organizationSlug.isNotBlank() &&
                it.orgSlug == store.organizationSlug &&
                it.storeCode.equals(store.code, ignoreCase = true)
        }

        _profile.value = StoreProfile(
            listed = row != null,
            businessTypeLabel = row?.businessTypeLabel?.takeIf { it.isNotBlank() },
            address = row?.address?.takeIf { it.isNotBlank() },
            lat = row?.lat,
            lng = row?.lng,
            // The directory falls back to a photo off the shelf for a shop
            // with no photo of its own. That is a product, not the shop, so
            // only the owner's upload counts here. A photo just uploaded is
            // kept if the directory has not caught up with it.
            photoUrl = row?.imageUrl
                ?.takeIf { OWN_PHOTO in it }
                ?.let { absoluteUrl(it, baseUrl) }
                ?: _profile.value?.photoUrl,
        )
    }

    /**
     * Upload a new shop photo, as a data URL. Never retried: a second copy
     * would only waste the upload, but a merchant watching a spinner should
     * decide whether to try again.
     */
    suspend fun uploadPhoto(dataUrl: String): String? {
        val url = caller.call { api.updateStoreImage(StoreImageRequestDto(dataUrl)) }
            .imageUrl
            ?.let { absoluteUrl(it, baseUrl) }

        _profile.update { (it ?: StoreProfile.Unknown).copy(photoUrl = url) }
        return url
    }

    /** The shop changed under the app. */
    fun clear() {
        _profile.value = null
    }

    private companion object {
        /** The path `StoreImageController::urlFor` serves an owner's photo from. */
        const val OWN_PHOTO = "/api/stores/"
    }
}
