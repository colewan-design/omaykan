package com.omaykan.storefront.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.ShopSummary

/**
 * The red discount flag. One shape, used on the promo cards, the grid and the
 * product page, so a markdown looks the same wherever a shopper meets it.
 */
@Composable
fun DiscountPill(percent: Int, modifier: Modifier = Modifier) {
    Text(
        text = "◷ $percent%",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 10.dp, bottomEnd = 10.dp, bottomStart = 4.dp, topEnd = 4.dp))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The promo carousel.
 *
 * The reference fills these with campaign artwork. There is no campaign table
 * behind this app and inventing one would be a lie on the front page, so each
 * card is built from a real markdown the merchant themselves entered: the
 * product, its own saving, and the price it actually rings up at.
 */
@Composable
fun PromoCarousel(
    deals: List<Product>,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (deals.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(deals.take(6), key = { _, product -> product.id }) { index, product ->
            PromoCard(
                product = product,
                alternate = index % 2 == 1,
                onClick = { onOpenProduct(product.id) },
            )
        }
    }
}

@Composable
private fun PromoCard(product: Product, alternate: Boolean, onClick: () -> Unit) {
    val ground = if (alternate) OmaykanTheme.colors.promoAlt else OmaykanTheme.colors.promo
    val onGround = if (alternate) OmaykanTheme.colors.onPromoAlt else OmaykanTheme.colors.onPromo

    Row(
        Modifier
            .width(300.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ground)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                color = onGround,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "In-store price, straight from the counter.",
                style = MaterialTheme.typography.bodySmall,
                color = onGround.copy(alpha = 0.75f),
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = product.discountPercent?.let { "Save $it%" } ?: Money.peso(product.priceCents),
                style = MaterialTheme.typography.labelLarge,
                color = OmaykanTheme.colors.onInk,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(OmaykanTheme.colors.ink)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        RemoteImage(
            url = product.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
    }
}

/**
 * The circular aisle rail.
 *
 * Categories are the merchant's own, so no icon set can cover all of them.
 * [categoryIcon] answers for the aisle names the seeded taxonomy uses, and
 * anything else keeps the first letter of the aisle — stable, legible here, and
 * it cannot mislabel a shelf the way a guessed pictogram would.
 */
@Composable
fun CategoryRail(
    categories: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(categories, key = { it.first }) { (id, name) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(62.dp)
                    .clickable { onPick(id) },
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(OmaykanTheme.colors.fill),
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = categoryIcon(name)
                    if (icon != null) {
                        // The glyphs ship as untinted silhouettes so the one
                        // asset serves both themes; the label below already
                        // names the aisle, so the image itself is decorative.
                        Image(
                            painter = painterResource(icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.size(34.dp),
                        )
                    } else {
                        Text(
                            text = name.trim().take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = OmaykanTheme.colors.textSecondary,
                        )
                    }
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * A product in the two-up grid.
 *
 * Where the reference puts a star rating and a "6.2k sold" line, this puts what
 * the API actually knows: the unit the shop sells by, and a warning when the
 * shelf is nearly bare. There are no reviews and no sales counts behind this
 * app, and inventing plausible ones on a page about honest prices is the one
 * decoration that would cost something.
 */
@Composable
fun GridProductCard(
    product: Product,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier
            .clip(shape)
            .border(1.dp, OmaykanTheme.colors.separator, shape)
            .clickable(onClick = onClick),
    ) {
        // The photo runs to the card's own edge and is clipped by the card's
        // corners rather than sitting in a smaller rounded tile inside it. A
        // second radius inside the first read as a frame nobody asked for.
        Box(Modifier.fillMaxWidth()) {
            RemoteImage(
                url = product.imageUrl,
                contentDescription = product.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            product.discountPercent?.let { percent ->
                DiscountPill(percent, Modifier.align(Alignment.TopStart))
            }
            Icon(
                imageVector = if (saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (saved) "Remove from saved" else "Save for later",
                tint = if (saved) MaterialTheme.colorScheme.error else OmaykanTheme.colors.textTertiary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clickable(onClick = onToggleSaved),
            )
        }

        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
        )

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp),
        ) {
            Text(
                text = Money.peso(product.priceCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (product.onSale) {
                Text(
                    text = Money.peso(product.compareAtPriceCents!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                    textDecoration = TextDecoration.LineThrough,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
            }
        }

        val footnote = when {
            product.runningLow -> "Only a few left"
            product.unitLabel != null -> "Per ${product.unitLabel}"
            else -> "In-store price"
        }
        Text(
            text = footnote,
            style = MaterialTheme.typography.bodySmall,
            color = if (product.runningLow) {
                OmaykanTheme.colors.warning
            } else {
                OmaykanTheme.colors.textTertiary
            },
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
        )
    }
}

/** One shop in the directory. */
@Composable
fun ShopCard(
    shop: ShopSummary,
    browsingNow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OmaykanTheme.colors.separator, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(OmaykanTheme.colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            if (shop.imageUrl == null) {
                Icon(
                    Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = OmaykanTheme.colors.textTertiary,
                )
            } else {
                RemoteImage(
                    url = shop.imageUrl,
                    contentDescription = shop.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shop.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (browsingNow) {
                    Text(
                        text = "Browsing now",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmaykanTheme.colors.success,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(OmaykanTheme.colors.success.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = shop.businessTypeLabel ?: shop.businessMode.wire,
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
            )
            if (shop.address.isNotBlank()) {
                Text(
                    text = shop.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    text = "${shop.productCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textTertiary,
                )
                shop.distanceKm?.let { km ->
                    Text(
                        text = " · ${"%.1f".format(km)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = OmaykanTheme.colors.textTertiary,
                    )
                }
            }
        }
    }
}
