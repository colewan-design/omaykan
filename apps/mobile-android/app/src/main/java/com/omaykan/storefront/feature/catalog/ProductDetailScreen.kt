package com.omaykan.storefront.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.CompactSnackbarHost
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.designsystem.SnackbarMessageEffect
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.core.model.ProductKind
import com.omaykan.storefront.core.model.Shop
import com.omaykan.storefront.feature.cart.Stepper
import com.omaykan.storefront.feature.home.DiscountPill

/**
 * One product, laid out to the reference: the picture in its own tile, the
 * markdown flag and price under it, the name, then the facts.
 *
 * Two things the reference carries that are not here, and their absence is the
 * point. There is no countdown to the end of the discount — nothing in the API
 * knows when a merchant will change a price, and a ticking clock that is not
 * counting toward anything is a manufactured hurry. And there are no reviews or
 * ratings — no such data exists, and inventing "4.8 · 1,230 reviews" on a page
 * whose whole argument is honest prices would be the one decoration that costs
 * something real.
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

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(OmaykanTheme.colors.fill)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "Product Detail",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 40.dp),
                    textAlign = TextAlign.Center,
                )
            }

            when {
                state.loading -> LoadingState()

                product == null -> MessageState(
                    title = "This item is gone",
                    detail = "It sold out or the shop took it down. Go back for what is still " +
                        "on the shelf.",
                    actionLabel = "Back to the shelf",
                    onAction = onBack,
                )

                else -> Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    RemoteImage(
                        url = product.imageUrl,
                        contentDescription = product.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.1f)
                            .clip(RoundedCornerShape(20.dp)),
                    )

                    product.discountPercent?.let { percent ->
                        DiscountPill(percent, Modifier.padding(top = 16.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(
                            text = Money.peso(product.priceCents),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (product.onSale) {
                            Text(
                                text = Money.peso(product.compareAtPriceCents!!),
                                style = MaterialTheme.typography.bodyLarge,
                                color = OmaykanTheme.colors.textTertiary,
                                textDecoration = TextDecoration.LineThrough,
                                modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (state.saved) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = if (state.saved) {
                                "Remove from saved"
                            } else {
                                "Save for later"
                            },
                            tint = if (state.saved) {
                                MaterialTheme.colorScheme.error
                            } else {
                                OmaykanTheme.colors.textTertiary
                            },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(26.dp)
                                .clickable(onClick = viewModel::toggleSaved),
                        )
                    }

                    // The covenant, said on the item itself: this is the price at
                    // the counter, not a marked-up online menu.
                    Text(
                        text = "In-store price",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OmaykanTheme.colors.success,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    // The facts, laid out the way the reference lays out a
                    // specifications strip: cells side by side, each a quiet
                    // label over the value it names, divided rather than
                    // stacked. Label/value rows read as a form; a shopper
                    // checking whether this is the 500 g tin wants to sweep the
                    // facts, not read down them. "Item code" rather than "SKU"
                    // is the word the web product page already uses.
                    SpecStrip(
                        title = "Details",
                        specs = buildList {
                            add(
                                "Unit" to when {
                                    product.kind == ProductKind.Weighted ->
                                        product.unitLabel?.let { "Per $it" } ?: "By weight"
                                    product.unitLabel != null -> "Per ${product.unitLabel}"
                                    else -> "Per piece"
                                },
                            )
                            // A count only where the shop keeps one. An
                            // untracked shelf has no number to give, and a
                            // cell reading "0" would be a lie about a product
                            // the server only sends because it is buyable.
                            product.stockQty?.let { qty ->
                                add("In stock" to formatQuantity(qty))
                            }
                            if (product.sku.isNotBlank()) add("Item code" to product.sku)
                            if (product.barcode.isNotBlank()) add("Barcode" to product.barcode)
                        },
                        modifier = Modifier.padding(top = 20.dp),
                    )

                    // Outside the strip on purpose: the cells are facts about
                    // the item, and this is a caution about the shelf.
                    if (product.runningLow) {
                        Text(
                            text = "Only a few left at this shop",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OmaykanTheme.colors.warning,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }

                    // Who the shopper is buying from, and where that counter
                    // actually is. The web product page has said this since it
                    // launched, and the reason holds harder on a phone: the
                    // rider collects cash on this shop's behalf, and "the shop
                    // on the previous screen" is not something a shopper should
                    // have to remember by the time they tap Buy Now.
                    state.shop?.let { shop -> SoldBySection(shop) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text(
                            text = "Quantity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OmaykanTheme.colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Stepper(
                            quantity = state.quantity,
                            onIncrement = { viewModel.setQuantity(state.quantity + 1) },
                            onDecrement = { viewModel.setQuantity(state.quantity - 1) },
                        )
                    }

                    if (state.inCart > 0) {
                        Text(
                            text = "Already in your cart: ${formatQuantity(state.inCart)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OmaykanTheme.colors.textTertiary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    Box(Modifier.padding(bottom = 16.dp))
                }
            }

            if (product != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { viewModel.addToCart() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Add to Cart", modifier = Modifier.padding(vertical = 4.dp))
                    }
                    // "Buy Now" in the reference means "straight to checkout". It
                    // still goes through the cart rather than around it: one order
                    // is one basket at one counter, and a second path into checkout
                    // would be a second place for that to go wrong.
                    Button(
                        onClick = { viewModel.buyNow(onAdded = onOpenCart) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OmaykanTheme.colors.ink,
                            contentColor = OmaykanTheme.colors.onInk,
                        ),
                    ) {
                        Text("Buy Now", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        // Placed by the host itself, in the middle of the screen: nothing
        // here decides where it lands, and nothing here can push the action
        // row the shopper's thumb is still resting on.
        CompactSnackbarHost(snackbarHostState)
    }
}

/** "2", not "2.0" — whole counts are the overwhelming case, and 2.0 kg is not. */
private fun formatQuantity(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()

/**
 * The seller, named the same way the web page names them: shop, then the person
 * behind the counter, then the address.
 *
 * Stacked rather than the label/value rows above it — a Philippine street
 * address is long enough that squeezing it into the right-hand column of a
 * DetailRow would leave the label nowhere to go.
 */
@Composable
private fun SoldBySection(shop: Shop) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OmaykanTheme.colors.separator, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Storefront,
                contentDescription = null,
                tint = OmaykanTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Sold by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column {
            Text(text = shop.name, style = MaterialTheme.typography.bodyLarge)
            shop.businessTypeLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                )
            }
        }

        shop.ownerName?.let { owner -> SoldByFact("Store owner", owner) }

        shop.address?.let { address ->
            SoldByFact("Store location", address, icon = Icons.Outlined.LocationOn)
        }
    }
}

@Composable
private fun SoldByFact(label: String, value: String, icon: ImageVector? = null) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OmaykanTheme.colors.textSecondary,
        )
        Row(Modifier.padding(top = 2.dp)) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = OmaykanTheme.colors.textTertiary,
                    modifier = Modifier
                        .padding(end = 6.dp, top = 2.dp)
                        .size(16.dp),
                )
            }
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A titled strip of facts: label above value, one cell per fact, hairlines
 * between them on a single filled ground.
 *
 * Three or fewer share the width evenly, the way the reference's do. Past that
 * an even split squeezes a barcode past reading, so the strip scrolls under the
 * thumb instead, and the cut-off edge of the last cell is what says so.
 */
@Composable
private fun SpecStrip(
    title: String,
    specs: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    if (specs.isEmpty()) return

    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        val ground = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OmaykanTheme.colors.fill)
        val scrolls = specs.size > 3

        Row(
            modifier = if (scrolls) ground.horizontalScroll(rememberScrollState()) else ground,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            specs.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(OmaykanTheme.colors.separator),
                    )
                }
                SpecCell(
                    label = label,
                    value = value,
                    modifier = if (scrolls) Modifier.width(118.dp) else Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One fact: the label muted above, the value carrying the weight below.
 *
 * Both lines are held to one line and clipped. Cells of different heights stop
 * reading as one row of facts, and these values — a unit, a count, a code — are
 * short by nature; the one that is not, a long barcode, is still recognisable
 * from its opening digits.
 */
@Composable
private fun SpecCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OmaykanTheme.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
