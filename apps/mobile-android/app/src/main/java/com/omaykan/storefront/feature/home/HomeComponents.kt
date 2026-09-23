package com.omaykan.storefront.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omaykan.storefront.R
import com.omaykan.storefront.core.designsystem.HeartToggle
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.designsystem.SerifFamily
import com.omaykan.storefront.core.designsystem.softCard
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.ShopSummary

/**
 * The markdown flag. One shape, used on the tiles, the grid and the product
 * page, so a markdown looks the same wherever a shopper meets it.
 */
@Composable
fun DiscountPill(percent: Int, modifier: Modifier = Modifier) {
    Text(
        text = "−$percent%",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = OmaykanTheme.colors.onCta,
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(OmaykanTheme.colors.cta)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * The banner under the header: words on the forest at the left, a shopkeeper
 * at their own counter at the right, and one way in.
 *
 * The reference's banner is a campaign ("Tradition Lives On"); this one says
 * the thing the whole product is — the shops near you, at their counter price
 * — because there is no campaign behind this app to advertise.
 */
@Composable
fun HeroBanner(onShopNow: () -> Unit, modifier: Modifier = Modifier) {
    val forest = OmaykanTheme.colors.forest

    Box(
        modifier
            .fillMaxWidth()
            .height(214.dp)
            .background(forest),
    ) {
        Image(
            painter = painterResource(R.drawable.hero_shop_owner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.64f),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to forest,
                        0.36f to forest,
                        0.6f to forest.copy(alpha = 0.45f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.6f)
                .padding(start = 20.dp, end = 4.dp),
        ) {
            Text(
                text = "Local Shops,\nHonest Prices",
                style = MaterialTheme.typography.headlineLarge,
                fontSize = 27.sp,
                lineHeight = 31.sp,
                color = OmaykanTheme.colors.onForest,
            )
            Text(
                text = "Support your neighbours. Order at the price on their own counter.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = OmaykanTheme.colors.onForest.copy(alpha = 0.92f),
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .clickable(onClick = onShopNow)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Shop Now",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnWhite,
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = OnWhite,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(16.dp),
                )
            }
        }
    }
}

/** Text on the few fixed-white controls that sit on photographs in either theme. */
private val OnWhite = Color(0xFF2A2420)

/**
 * The aisles as circles, each holding a photograph from its own shelf.
 *
 * Where nothing in an aisle has been photographed, the seeded taxonomy's
 * glyph stands in ([categoryIcon]), and past that the aisle's first letter —
 * stable, legible, and unable to mislabel a shelf the way a guessed pictogram
 * would.
 */
@Composable
fun AisleCircles(
    aisles: List<Aisle>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(aisles, key = { it.id }) { aisle ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(86.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPick(aisle.id) }
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(OmaykanTheme.colors.peach),
                    contentAlignment = Alignment.Center,
                ) {
                    AisleArt(aisle)
                }
                Text(
                    text = aisle.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun AisleArt(aisle: Aisle) {
    val glyph = categoryIcon(aisle.name)

    when {
        aisle.imageUrl != null -> RemoteImage(
            url = aisle.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape),
        )

        // The glyphs ship as untinted silhouettes so the one asset serves both
        // themes; the label below already names the aisle, so it is decorative.
        glyph != null -> Image(
            painter = painterResource(glyph),
            contentDescription = null,
            colorFilter = ColorFilter.tint(OmaykanTheme.colors.cta),
            modifier = Modifier.size(36.dp),
        )

        else -> Text(
            text = aisle.name.trim().take(1).uppercase(),
            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
            color = OmaykanTheme.colors.cta,
        )
    }
}

/** A horizontal shelf of [ProductTile]s, the reference's "Featured Products" row. */
@Composable
fun ProductShelf(
    products: List<Product>,
    isSaved: (String) -> Boolean,
    onToggleSaved: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        // Vertical room too, or the row clips the cards' shadows.
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(products, key = { it.id }) { product ->
            ProductTile(
                product = product,
                saved = isSaved(product.id),
                onToggleSaved = { onToggleSaved(product.id) },
                onClick = { onOpenProduct(product.id) },
            )
        }
    }
}

/**
 * The small card on a horizontal shelf: photograph, heart, name, price.
 *
 * The name keeps two lines whether it needs them or not, so every card on a
 * shelf is the same height and the prices line up across the row.
 */
@Composable
fun ProductTile(
    product: Product,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(120.dp)
            .softCard(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        ProductPhoto(product, saved, onToggleSaved)

        Text(
            text = product.name,
            style = MaterialTheme.typography.bodySmall,
            color = OmaykanTheme.colors.ink,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
        )
        Text(
            text = Money.peso(product.priceCents),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = OmaykanTheme.colors.ink,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 9.dp),
        )
    }
}

/** The square photograph shared by both card sizes, with its flag and heart. */
@Composable
private fun ProductPhoto(product: Product, saved: Boolean, onToggleSaved: () -> Unit) {
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
        product.discountPercent?.let { percent ->
            DiscountPill(percent, Modifier.align(Alignment.TopStart))
        }
        // A pale disc behind the heart: the photographs are the shops' own,
        // and a dark outline on a dark photo would vanish.
        HeartToggle(
            saved = saved,
            onToggle = onToggleSaved,
            size = 17.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.82f)),
            idleTint = OnWhite,
        )
    }
}

/**
 * A product in a two-up grid — a shop's shelf, search results, Favorites.
 *
 * Where a marketplace puts a star rating and a "6.2k sold" line, this puts what
 * the API actually knows: the unit the shop sells by, and a warning when the
 * shelf is nearly bare. There are no reviews and no sales counts behind this
 * app, and inventing them on a page about honest prices would cost something.
 */
@Composable
fun GridProductCard(
    product: Product,
    saved: Boolean,
    onToggleSaved: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /*
     * Three to a row rather than two, as a shop's shelf is.
     *
     * A third column costs a card about a third of its width, and two lines of
     * bodyMedium stopped holding the names real Philippine groceries have —
     * "A MARK LAUREL LEAVES 10G" and "Adobo Connection Adobo Flakes" both cut
     * off mid-word. bodySmall fits them in the same two lines. It is a flag
     * rather than a separate card because everything else about the two is
     * identical, and two cards would drift.
     */
    compact: Boolean = false,
) {
    Column(
        modifier
            .softCard()
            .clickable(onClick = onClick),
    ) {
        ProductPhoto(product, saved, onToggleSaved)

        Text(
            text = product.name,
            style = if (compact) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = OmaykanTheme.colors.ink,
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
                fontWeight = FontWeight.SemiBold,
                color = OmaykanTheme.colors.ink,
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
            .softCard()
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    fontWeight = FontWeight.SemiBold,
                    color = OmaykanTheme.colors.ink,
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
            Text(
                text = "${shop.productCount} items" +
                    (shop.distanceKm?.let { km -> " · ${"%.1f".format(km)} km" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = OmaykanTheme.colors.textTertiary,
        )
    }
}

/**
 * A wide aisle card for the Shop tab: the aisle's name set in the serif over
 * a photograph from its shelf, darkened from the left so the words hold.
 */
@Composable
fun AisleCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(90.dp)
            .softCard()
            .background(AisleGround)
            .clickable(onClick = onClick),
    ) {
        if (imageUrl != null) {
            RemoteImage(
                url = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.62f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to AisleGround,
                        0.4f to AisleGround.copy(alpha = 0.88f),
                        1f to AisleGround.copy(alpha = 0.2f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, end = 56.dp),
        ) {
            Text(
                text = title,
                style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TextStyle(fontFamily = SerifFamily, fontSize = 14.sp),
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .size(28.dp),
        )
    }
}

/** The dark ground behind an aisle card, whatever the theme — it sits under a photograph. */
private val AisleGround = Color(0xFF2B211B)

/**
 * A shop told as a story card: its photograph, its name, and what and where it
 * is. The reference fills these with articles; the true stories this app has
 * are the shops themselves.
 */
@Composable
fun StoryCard(
    shop: ShopSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(176.dp)
            .softCard(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .background(OmaykanTheme.colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            if (shop.imageUrl == null) {
                Icon(
                    Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = OmaykanTheme.colors.textTertiary,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                RemoteImage(
                    url = shop.imageUrl,
                    contentDescription = shop.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = shop.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OmaykanTheme.colors.ink,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    shop.businessTypeLabel,
                    shop.address.takeIf { it.isNotBlank() },
                    "${shop.productCount} items on the shelf",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
                maxLines = 3,
                minLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
