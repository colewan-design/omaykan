package com.omaykan.seller.feature.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.omaykan.seller.core.data.CatalogRepository
import com.omaykan.seller.core.data.OrderFeed
import com.omaykan.seller.core.data.PhotoEncoder
import com.omaykan.seller.core.data.SessionRepository
import com.omaykan.seller.core.data.StoreProfileRepository
import com.omaykan.seller.core.designsystem.MountainBackdrop
import com.omaykan.seller.core.designsystem.RemoteThumb
import com.omaykan.seller.core.designsystem.SellerSwitch
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.SettingsRow
import com.omaykan.seller.core.map.StaticMap
import com.omaykan.seller.core.model.PairedStore
import com.omaykan.seller.core.model.RoutePoint
import com.omaykan.seller.core.model.StaffRole
import com.omaykan.seller.core.model.StoreProfile
import com.omaykan.seller.core.network.ApiException
import com.omaykan.seller.core.notify.OrderWatchController
import com.omaykan.seller.feature.shell.REGISTER_URL
import com.omaykan.seller.feature.shell.openInBrowser
import com.omaykan.seller.feature.shell.openMap
import com.omaykan.seller.feature.shell.rememberAlertToggle
import com.omaykan.seller.feature.shell.storeImageUrl
import com.omaykan.seller.feature.shell.storefrontUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class AccountUiState(
    val userName: String? = null,
    val role: StaffRole? = null,
    val watching: Boolean = false,
    val signingOut: Boolean = false,
    val profile: StoreProfile? = null,
    val profileLoading: Boolean = true,
    val uploading: Boolean = false,
    val photoError: String? = null,
) {
    /**
     * The server's own rule for the shop photo: admins and managers only —
     * the same line `StoreContext::isManager()` draws for the catalog.
     */
    val canChangePhoto: Boolean get() = role?.managesCatalog == true
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val sessions: SessionRepository,
    private val watcher: OrderWatchController,
    private val feed: OrderFeed,
    private val profiles: StoreProfileRepository,
    private val photos: PhotoEncoder,
) : ViewModel() {

    private data class Local(
        val signingOut: Boolean = false,
        val profileLoading: Boolean = true,
        val uploading: Boolean = false,
        val photoError: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<AccountUiState> =
        combine(catalog.state, watcher.watching, profiles.profile, local) { loaded, watching, profile, screen ->
            AccountUiState(
                userName = loaded.catalog?.userName,
                role = loaded.catalog?.role,
                watching = watching,
                signingOut = screen.signingOut,
                profile = profile,
                profileLoading = screen.profileLoading && profile == null,
                uploading = screen.uploading,
                photoError = screen.photoError,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    init {
        viewModelScope.launch { catalog.ensureLoaded() }
    }

    /**
     * Read the shop's public listing. A failure leaves the rows saying what
     * is unknown rather than raising an error: nothing on this screen stops
     * working without an address.
     */
    fun loadProfile(store: PairedStore) {
        viewModelScope.launch {
            try {
                profiles.load(store)
            } catch (_: ApiException) {
                // Leave what we had.
            } finally {
                local.update { it.copy(profileLoading = false) }
            }
        }
    }

    fun setWatching(on: Boolean) {
        if (on) watcher.start() else watcher.stop()
    }

    /** Shrink the picked photo, send it, and show the server's new URL. */
    fun uploadPhoto(uri: Uri) {
        if (local.value.uploading || !state.value.canChangePhoto) return
        local.update { it.copy(uploading = true, photoError = null) }

        viewModelScope.launch {
            try {
                profiles.uploadPhoto(photos.jpegDataUrl(uri))
            } catch (e: ApiException) {
                local.update { it.copy(photoError = e.message) }
            } catch (_: IOException) {
                local.update { it.copy(photoError = "That photo couldn't be read. Try another one.") }
            } finally {
                local.update { it.copy(uploading = false) }
            }
        }
    }

    fun signOut() {
        if (local.value.signingOut) return
        local.update { it.copy(signingOut = true) }

        viewModelScope.launch {
            // Ordered: the watch first, so the service is on its way down
            // before the token it was using disappears underneath it.
            watcher.stop()
            sessions.signOut()
            // The shop is changing under the app. Without these the next
            // sign-in would read the old shop's orders as new arrivals, and
            // could show the old shop's prices or photo for a frame.
            feed.reset()
            catalog.clear()
            profiles.clear()
        }
    }
}

/**
 * "Store Profile", laid out as the reference draws it: the highland cover, a
 * white sheet rising over it with the shop's photo on the seam, and one list
 * of what can be done about the shop.
 *
 * The reference's list also has Pickup & Delivery Options, Payout Settings
 * and Business Documents. The backend publishes no delivery settings to a
 * phone; customers pay the shop directly, so there is nothing to pay out; and
 * there are no documents to file. Those rows would open onto nothing, so they
 * are not here. Everything the shop keeps about itself is edited on the
 * register, which is where "Edit" goes.
 */
@Composable
fun AccountScreen(
    store: PairedStore,
    onOpenMessages: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toggleAlerts = rememberAlertToggle(viewModel::setWatching)
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::uploadPhoto)
    }

    LaunchedEffect(store.id) { viewModel.loadProfile(store) }

    val profile = state.profile
    val shopName = store.name.ifBlank { "Your shop" }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        MountainBackdrop(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(150.dp),
            ) {
                Text(
                    text = "Store Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 18.sp,
                    color = SellerTheme.colors.onCanopy,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                )
                TextButton(
                    onClick = { context.openInBrowser(REGISTER_URL) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 4.dp),
                ) {
                    Text("Edit", color = SellerTheme.colors.onCanopy, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .offset(y = (-28).dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = shopName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    text = listOfNotNull(profile?.businessTypeLabel, "Branch ${store.code}").joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SellerTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp, start = 24.dp, end = 24.dp),
                )
                state.userName?.let { name ->
                    Text(
                        text = listOfNotNull("Signed in as $name", state.role?.label).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = SellerTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                state.photoError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = SellerTheme.colors.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))
                RowDivider()

                SettingsRow(
                    icon = Icons.Outlined.Storefront,
                    title = "Store Information",
                    subtitle = "Name, business type, photo and staff — kept on the register",
                    onClick = { context.openInBrowser(REGISTER_URL) },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Outlined.LocationOn,
                    title = "Store Address",
                    subtitle = when {
                        profile?.address != null -> profile.address
                        state.profileLoading -> "Loading…"
                        profile?.listed == false -> "Not in the shop directory yet — it lists shops once they have products"
                        else -> "No address on file — add it on the register"
                    },
                    onClick = if (profile?.placed == true || profile?.address != null) {
                        { context.openMap(profile.lat, profile.lng, shopName, profile.address) }
                    } else {
                        null
                    },
                    trailing = { AddressThumb(profile) },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "Order Alerts",
                    subtitle = if (state.watching) {
                        "On — sounds for new orders, even in the background"
                    } else {
                        "Off — orders arrive quietly"
                    },
                    trailing = { SellerSwitch(checked = state.watching, onCheckedChange = toggleAlerts) },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "Messages",
                    subtitle = "What customers have written to your shop",
                    onClick = onOpenMessages,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Outlined.Public,
                    title = "View Your Storefront",
                    subtitle = "The page customers order from",
                    onClick = { context.openInBrowser(storefrontUrl(store)) },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = if (state.signingOut) "Signing out…" else "Sign Out",
                    subtitle = "Leaves $shopName on this phone",
                    tint = SellerTheme.colors.danger,
                    onClick = { if (!state.signingOut) confirming = true },
                )
                Spacer(Modifier.height(24.dp))
            }

            ShopPhoto(
                url = profile?.photoUrl ?: storeImageUrl(store.id),
                shopName = shopName,
                canChange = state.canChangePhoto,
                uploading = state.uploading,
                onChange = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-52).dp),
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "Order alerts stop on this phone, and you'll need your password " +
                        "or Google account to get back in.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        viewModel.signOut()
                    },
                ) {
                    Text("Sign out", color = SellerTheme.colors.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Stay signed in") }
            },
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(start = 52.dp),
        color = SellerTheme.colors.separator,
    )
}

/**
 * The shop's photo on the seam between cover and sheet, with the reference's
 * camera badge for the people allowed to change it. Everybody else sees the
 * photo and no badge — offering a button the server will refuse with a 403
 * would be a trap.
 */
@Composable
private fun ShopPhoto(
    url: String,
    shopName: String,
    canChange: Boolean,
    uploading: Boolean,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(112.dp)) {
        RemoteThumb(
            url = url,
            fallback = Icons.Filled.Storefront,
            shape = CircleShape,
            contentDescription = "$shopName photo",
            iconSize = 40.dp,
            modifier = Modifier
                .fillMaxSize()
                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
        if (canChange) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SellerTheme.colors.canopy)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(enabled = !uploading, onClick = onChange),
                contentAlignment = Alignment.Center,
            ) {
                if (uploading) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = SellerTheme.colors.onCanopy,
                    )
                } else {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = "Change shop photo",
                        tint = SellerTheme.colors.onCanopy,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

/**
 * The little map beside the address: the shop's pin, when it has one and
 * this build has a Mapbox token; a red pin on peach otherwise — the
 * reference's picture, drawn without pretending to be a map.
 */
@Composable
private fun AddressThumb(profile: StoreProfile?) {
    val mapUrl = profile?.takeIf { it.placed }?.let {
        StaticMap.url(
            rider = null,
            pickup = RoutePoint(name = null, address = null, lat = it.lat, lng = it.lng),
            dropoff = null,
            width = 128,
            height = 96,
        )
    }

    Box(
        Modifier
            .size(width = 64.dp, height = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SellerTheme.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = if (profile?.placed == true || profile?.address != null) {
                SellerTheme.colors.danger
            } else {
                SellerTheme.colors.textTertiary
            },
            modifier = Modifier.size(24.dp),
        )
        if (mapUrl != null) {
            AsyncImage(
                model = mapUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
            )
        }
    }
}

private val StaffRole.label: String
    get() = when (this) {
        StaffRole.Admin -> "Admin"
        StaffRole.Manager -> "Manager"
        StaffRole.Cashier -> "Staff"
    }
