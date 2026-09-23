package com.omaykan.storefront.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.AccordionRow
import com.omaykan.storefront.core.designsystem.CompactSnackbarHost
import com.omaykan.storefront.core.designsystem.CtaButton
import com.omaykan.storefront.core.designsystem.ForestTopBar
import com.omaykan.storefront.core.designsystem.HeartToggle
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.QuantityBox
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.designsystem.SnackbarMessageEffect
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.ProductKind
import com.omaykan.storefront.core.model.Shop
import com.omaykan.storefront.feature.home.DiscountPill

/**
 * One product, laid out to the reference: the photograph edge to edge, the
 * name and price, a line where the reference puts its rating, a short
 * description, three promises, the quantity and Add to Cart, then three folds.
 *
 * Every slot carries something true. The rating line says "In-store price",
 * because there are no reviews behind this app and inventing "4.8 · 32
 * reviews" on a page about honest prices would be the one decoration that
 * costs something. The three promises are the platform's, which hold for every
 * product on it — not "handmade", which would be false of a tin of coffee. And
 * the folds hold the facts the API has: the unit and codes, how delivery
 * works, and who is selling.
 */
@Composable
fun ProductDetailScreen(
    onOpenCart: () -> Unit,
    onBack: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val product = state.product
    val snackbarHostState = remember { SnackbarHostState() }

    // "View cart" is the only action any message here offers.
    SnackbarMessageEffect(viewModel.snackbar, snackbarHostState, onAction = onOpenCart)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.loading -> LoadingState()

            product == null -> Column(Modifier.fillMaxSize()) {
                ForestTopBar(title = "Product", onBack = onBack)
                MessageState(
                    title = "This item is gone",
                    detail = "It sold out or the shop took it down. Go back for what is still " +
                        "on the shelf.",
                    actionLabel = "Back to the shelf",
                    onAction = onBack,
                )
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                ProductHero(
                    product = product,
                    saved = state.saved,
                    onBack = onBack,
                    onToggleSaved = viewModel::toggleSaved,
                )

                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 18.dp, bottom = 24.dp)
                        .navigationBarsPadding(),
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = OmaykanTheme.colors.ink,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            text = Money.peso(product.priceCents),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = OmaykanTheme.colors.ink,
                        )
                        if (product.onSale) {
                            Text(
                                text = Money.peso(product.compareAtPriceCents!!),
                                style = MaterialTheme.typography.bodyLarge,
                                color = OmaykanTheme.colors.textTertiary,
                                textDecoration = TextDecoration.LineThrough,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                        product.discountPercent?.let { percent ->
                            DiscountPill(percent, Modifier.padding(start = 10.dp))
                        }
                    }

                    // The rating line's slot, holding the covenant instead:
                    // this is the price at the counter, not a marked-up menu.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = null,
                            tint = OmaykanTheme.colors.success,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "In-store price",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OmaykanTheme.colors.success,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        Text(
                            text = " · ${unitOf(product)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OmaykanTheme.colors.textSecondary,
                        )
                    }

                    Text(
                        text = describe(product, state.shop),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OmaykanTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    Promises(Modifier.padding(top = 18.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        QuantityBox(
                            quantity = state.quantity,
                            onIncrement = { viewModel.setQuantity(state.quantity + 1) },
                            onDecrement = { viewModel.setQuantity(state.quantity - 1) },
                        )
                        CtaButton(
                            text = "Add to Cart",
                            onClick = viewModel::addToCart,
                            icon = Icons.Outlined.ShoppingCart,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (state.inCart > 0) {
                        Text(
                            text = "Already in your cart: ${formatQuantity(state.inCart)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OmaykanTheme.colors.textTertiary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    AccordionRow(title = "Product Details") {
                        FactRow("Unit", unitOf(product))
                        // A count only where the shop keeps one. An untracked
                        // shelf has no number to give, and "0" would be a lie
                        // about a product the server only sends because it is
                        // buyable.
                        product.stockQty?.let { qty -> FactRow("In stock", formatQuantity(qty)) }
                        if (product.sku.isNotBlank()) FactRow("Item code", product.sku)
                        if (product.barcode.isNotBlank()) FactRow("Barcode", product.barcode)
                        if (product.runningLow) {
                            Text(
                                text = "Only a few left at this shop",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OmaykanTheme.colors.warning,
                            )
                        }
                    }

                    AccordionRow(title = "Delivery Information") {
                        Text(
                            text = "Pick it up at the counter, or have a local rider bring it to " +
                                "your door. The delivery fee is quoted at checkout, and the rider " +
                                "keeps all of it. You pay when you get your order — nothing is " +
                                "charged in the app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OmaykanTheme.colors.textSecondary,
                        )
                    }

                    // Who the shopper is buying from, and where that counter
                    // is: the rider collects cash on this shop's behalf, and
                    // "the shop on the previous screen" is not something a
                    // shopper should have to remember.
                    state.shop?.let { shop ->
                        AccordionRow(title = "Sold By") { SoldBy(shop) }
                    }

                    HorizontalDivider(color = OmaykanTheme.colors.separator)
                }
            }
        }

        // Placed by the host itself, in the middle of the screen: nothing here
        // decides where it lands, and nothing here can cover the button the
        // shopper's thumb is still resting on.
        CompactSnackbarHost(snackbarHostState)
    }
}

/**
 * The photograph, edge to edge and square, with the way back and the heart
 * laid over its top corners. A dark wash behind the status bar keeps both —
 * and the clock — legible on a packshot shot on white.
 */
@Composable
private fun ProductHero(
    product: Product,
    saved: Boolean,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        RemoteImage(
            url = product.imageUrl,
            contentDescription = product.name,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            HeartToggle(
                saved = saved,
                onToggle = onToggleSaved,
                size = 26.dp,
                idleTint = Color.White,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/**
 * The reference's three badges, carrying the three promises the platform makes
 * about every order — so they are true of whatever product they sit under.
 */
@Composable
private fun Promises(modifier: Modifier = Modifier) {
    Column(modifier) {
        HorizontalDivider(color = OmaykanTheme.colors.separator)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Promise(Icons.Outlined.Sell, "Same Price\nas In-Store", Modifier.weight(1f))
            PromiseRule()
            Promise(Icons.Outlined.Storefront, "Supports a\nLocal Shop", Modifier.weight(1f))
            PromiseRule()
            Promise(Icons.Outlined.DeliveryDining, "Rider Keeps\nthe Whole Fee", Modifier.weight(1f))
        }
        HorizontalDivider(color = OmaykanTheme.colors.separator)
    }
}

@Composable
private fun Promise(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = OmaykanTheme.colors.ink,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = OmaykanTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PromiseRule() {
    Box(
        Modifier
            .width(1.dp)
            .height(44.dp)
            .background(OmaykanTheme.colors.separator),
    )
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OmaykanTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = OmaykanTheme.colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

/**
 * The seller, named the way the web page names them: shop, then the person
 * behind the counter, then the address.
 */
@Composable
private fun SoldBy(shop: Shop) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = shop.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = OmaykanTheme.colors.ink,
        )
        shop.businessTypeLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textTertiary,
            )
        }
    }
    shop.ownerName?.let { owner -> FactRow("Store owner", owner) }
    shop.address?.let { address ->
        Row {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = OmaykanTheme.colors.textTertiary,
                modifier = Modifier
                    .padding(end = 6.dp, top = 2.dp)
                    .size(16.dp),
            )
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                color = OmaykanTheme.colors.textSecondary,
            )
        }
    }
}

/** The description slot, said from facts: whose shelf, and whose price. */
private fun describe(product: Product, shop: Shop?): String {
    val shelf = shop?.name?.let { "$it's shelf" } ?: "the shop's shelf"
    return "${product.name}, straight from $shelf at the price on its own counter. " +
        "The shop confirms the total when you check out."
}

private fun unitOf(product: Product): String = when {
    product.kind == ProductKind.Weighted -> product.unitLabel?.let { "Per $it" } ?: "By weight"
    product.unitLabel != null -> "Per ${product.unitLabel}"
    else -> "Per piece"
}

/** "2", not "2.0" — whole counts are the overwhelming case, and 2.0 kg is not. */
private fun formatQuantity(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()
