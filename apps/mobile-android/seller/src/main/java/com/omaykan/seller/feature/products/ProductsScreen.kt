package com.omaykan.seller.feature.products

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.R
import com.omaykan.seller.core.designsystem.ButtonShape
import com.omaykan.seller.core.designsystem.ChipTabs
import com.omaykan.seller.core.designsystem.ForestTopBar
import com.omaykan.seller.core.designsystem.Pill
import com.omaykan.seller.core.designsystem.PillShape
import com.omaykan.seller.core.designsystem.RemoteThumb
import com.omaykan.seller.core.designsystem.ScreenMessage
import com.omaykan.seller.core.designsystem.SearchField
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.designsystem.SoftCard
import com.omaykan.seller.core.designsystem.TabItem
import com.omaykan.seller.core.model.Category
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.model.Product

/** The three chips. Low stock is reached from Home and shown as a banner. */
private val CHIP_FILTERS = listOf(ProductFilter.All, ProductFilter.Active, ProductFilter.Inactive)

/** Four to a row, two rows: "All" and seven categories, as the reference lays them out. */
private const val GRID_COLUMNS = 4
private const val COLLAPSED_CATEGORIES = 7

/**
 * What the shop sells: search, the categories as a grid of circles, three
 * filter chips, and one card per product.
 *
 * Read-only for a cashier — the server's own line is that admins and managers
 * change what a shop sells — so the add button, the form's save and the
 * row menu's on/off switch only appear for them. Everybody can look.
 */
@Composable
fun ProductsScreen(
    onEdit: (productId: String?) -> Unit,
    onOpenPromotions: () -> Unit,
    filterRequest: ProductFilter?,
    onFilterRequestHandled: () -> Unit,
    viewModel: ProductsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The home screen's "Low stock" figure opens this tab already filtered.
    LaunchedEffect(filterRequest) {
        filterRequest?.let {
            viewModel.onFilter(it)
            onFilterRequestHandled()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ForestTopBar(
            title = "Products",
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, "Refresh products", tint = SellerTheme.colors.onCanopy)
                }
                if (state.canEdit) {
                    // Promo codes set prices, so they sit with products and
                    // go to the same roles.
                    IconButton(onClick = onOpenPromotions) {
                        Icon(Icons.Filled.LocalOffer, "Promo codes", tint = SellerTheme.colors.onCanopy)
                    }
                    IconButton(onClick = { onEdit(null) }) {
                        Icon(Icons.Filled.Add, "Add product", tint = SellerTheme.colors.onCanopy)
                    }
                }
            },
        )

        val catalog = state.catalog
        when {
            catalog == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }

            catalog == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScreenMessage(
                    icon = Icons.Filled.CloudOff,
                    title = "Couldn't load your products",
                    body = state.error ?: "Check the connection and try again.",
                    actionLabel = "Try again",
                    onAction = viewModel::refresh,
                )
            }

            else -> ProductList(state = state, categories = catalog.categories, viewModel = viewModel, onEdit = onEdit)
        }
    }
}

@Composable
private fun ProductList(
    state: ProductsUiState,
    categories: List<Category>,
    viewModel: ProductsViewModel,
    onEdit: (String?) -> Unit,
) {
    val products = state.visible

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQuery,
                placeholder = "Search your products…",
            )
        }

        if (categories.isNotEmpty()) {
            item {
                CategoryGrid(
                    categories = categories,
                    selectedId = state.categoryId,
                    onSelect = viewModel::onCategory,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item {
            ChipTabs(
                tabs = CHIP_FILTERS.map { TabItem(it.label, state.count(it)) },
                selected = CHIP_FILTERS.indexOf(state.filter),
                onSelect = { viewModel.onFilter(CHIP_FILTERS[it]) },
                equalWidth = true,
            )
        }

        if (state.filter == ProductFilter.LowStock) {
            item {
                LowStockBanner(
                    count = state.count(ProductFilter.LowStock),
                    onClear = { viewModel.onFilter(ProductFilter.All) },
                )
            }
        }

        if (!state.canEdit) {
            item { ReadOnlyNote() }
        }

        (state.actionError ?: state.error?.let { "$it Showing the products that last loaded." })?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.actionError != null) SellerTheme.colors.danger else SellerTheme.colors.warning,
                    modifier = Modifier.clickable(onClick = viewModel::dismissActionError),
                )
            }
        }

        if (products.isEmpty()) {
            item {
                ScreenMessage(
                    icon = Icons.Filled.Inventory2,
                    title = if (state.catalog?.products.isNullOrEmpty()) "No products yet" else "Nothing matches",
                    body = if (state.catalog?.products.isNullOrEmpty()) {
                        "Add what you sell and it will show on your storefront."
                    } else {
                        "Try another search, category or filter."
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(products, key = { it.id }) { product ->
            ProductRow(
                product = product,
                categoryName = state.catalog?.categoryName(product.categoryId),
                canEdit = state.canEdit,
                busy = state.busyProductId == product.id,
                enabled = state.busyProductId == null,
                onOpen = { onEdit(product.id) },
                onToggleActive = { viewModel.setActive(product, !product.active) },
            )
        }
    }
}

/** What goes in one cell of the category grid. */
private sealed interface CategoryCell {
    data object All : CategoryCell

    data class Of(val category: Category) : CategoryCell

    data class Toggle(val expanded: Boolean) : CategoryCell
}

/**
 * "All" and the categories in rows of four.
 *
 * Two rows by default, as the reference draws it. A shop with more than seven
 * categories gets a "More" cell in the eighth place rather than a grid that
 * pushes its products off the first screen — and the grid opens by itself if
 * the chosen category would otherwise be hidden in the folded part.
 */
@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val overflows = categories.size > COLLAPSED_CATEGORIES
    val selectedIndex = categories.indexOfFirst { it.id == selectedId }
    val showAll = !overflows || expanded || selectedIndex >= COLLAPSED_CATEGORIES - 1

    val shown = if (showAll) categories else categories.take(COLLAPSED_CATEGORIES - 1)
    val cells = buildList {
        add(CategoryCell.All)
        shown.forEach { add(CategoryCell.Of(it)) }
        if (overflows) add(CategoryCell.Toggle(expanded = showAll))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(GRID_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        when (cell) {
                            CategoryCell.All -> CategoryCircle(
                                name = "All",
                                icon = Icons.Filled.GridView,
                                art = R.drawable.category_art_grid,
                                selected = selectedId == null,
                                onClick = { onSelect(null) },
                            )

                            is CategoryCell.Of -> CategoryCircle(
                                name = cell.category.name,
                                icon = categoryIcon(cell.category.name),
                                art = categoryArt(cell.category.name),
                                selected = selectedId == cell.category.id,
                                onClick = { onSelect(cell.category.id) },
                            )

                            is CategoryCell.Toggle -> CategoryCircle(
                                name = if (cell.expanded) "Less" else "More",
                                icon = if (cell.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                art = null,
                                selected = false,
                                onClick = { expanded = !cell.expanded },
                            )
                        }
                    }
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One category: its illustration in a circle when the name matches one of the
 * drawn categories, the Material icon on peach when it does not.
 *
 * The chosen one is ringed in terracotta. The illustrations are painted on
 * their own dark ground, so filling the circle terracotta — what the icon
 * version does — would hide the picture it is marking.
 */
@Composable
private fun CategoryCircle(
    name: String,
    icon: ImageVector,
    @DrawableRes art: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) {
            Box(
                Modifier
                    .size(58.dp)
                    .border(
                        width = if (selected) 3.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .padding(if (selected) 3.dp else 0.dp)
                    .clip(CircleShape),
            ) {
                Image(
                    painter = painterResource(art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else SellerTheme.colors.accentSoft,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else SellerTheme.colors.onAccentSoft,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "Low stock only (6)", with a way back to everything. */
@Composable
private fun LowStockBanner(count: Int, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(SellerTheme.colors.warning.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WarningAmber, null, tint = SellerTheme.colors.warning, modifier = Modifier.size(16.dp))
        Text(
            text = "Showing low stock only ($count)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, "Show all products", tint = SellerTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun ReadOnlyNote() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(SellerTheme.colors.fill)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Lock, null, tint = SellerTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
        Text(
            text = "Only an admin or manager can add or change products.",
            style = MaterialTheme.typography.bodySmall,
            color = SellerTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * One product: photo, name, price per unit, stock — and at the top right its
 * status beside a ⋮ menu, as the reference draws it.
 */
@Composable
private fun ProductRow(
    product: Product,
    categoryName: String?,
    canEdit: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onToggleActive: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    SoftCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(8.dp)) {
            RemoteThumb(
                url = product.imageUrl,
                fallback = categoryIcon(categoryName ?: product.name),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(76.dp),
            )

            Column(
                Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .padding(start = 12.dp, end = 4.dp),
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = priceLabel(product),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SellerTheme.colors.ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = product.stockLabel?.let { "Stock: $it" } ?: "Stock not tracked",
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(product)
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { menu = true }, enabled = enabled) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "More for ${product.name}",
                                tint = SellerTheme.colors.textSecondary,
                            )
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (canEdit) "Edit product" else "View product") },
                            onClick = {
                                menu = false
                                onOpen()
                            },
                        )
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text(if (product.active) "Mark inactive" else "Mark active") },
                                onClick = {
                                    menu = false
                                    onToggleActive()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * "₱180/kg" — whole pesos when there are no centavos, the way a shop writes a
 * price on a tag, and the full "₱180.50" when there are.
 */
private fun priceLabel(product: Product): String {
    val cents = product.shownPriceCents
    val price = if (cents % 100 == 0L) Money.pesoRounded(cents) else Money.peso(cents)
    return price + (product.unitLabel?.let { "/$it" } ?: "")
}

/**
 * One word for where the product stands, worst news first.
 *
 * "Sold out" outranks "Active" because the storefront hides a tracked product
 * with nothing on the shelf, active or not — so "Active" beside it would be
 * telling the merchant customers can buy something they cannot.
 */
@Composable
private fun StatusPill(product: Product) {
    val colors = SellerTheme.colors
    when {
        !product.active -> Pill("Inactive", colors.textTertiary, dense = true)
        product.branchAvailable == false -> Pill("Off here", colors.textTertiary, dense = true)
        product.soldOut -> Pill("Sold out", colors.danger, dense = true)
        product.lowStock -> Pill("Low stock", colors.warning, dense = true)
        else -> Pill("Active", colors.success, dense = true)
    }
}

/**
 * The drawn illustration for a category, when its name is one the art set
 * covers — produce, groceries, souvenirs, home goods, delicacies, drinks and
 * handicrafts. Null otherwise, and the circle falls back to [categoryIcon]:
 * a coffee shop's "Pastries" gets a Material croissant rather than a jam jar
 * that would be a wrong picture.
 */
@DrawableRes
internal fun categoryArt(name: String): Int? {
    val n = name.lowercase()
    return when {
        listOf("produce", "vegetable", "fruit", "farm", "fresh").any { it in n } -> R.drawable.category_art_leaf
        listOf("coffee", "tea", "cafe", "beverage", "drink", "juice").any { it in n } -> R.drawable.category_art_cup
        listOf("delicac", "jam", "preserve", "pasalubong").any { it in n } -> R.drawable.category_art_jar
        listOf("souvenir", "gift").any { it in n } -> R.drawable.category_art_bag
        listOf("craft", "handi", "woven", "pottery").any { it in n } -> R.drawable.category_art_vase
        listOf("home", "house").any { it in n } -> R.drawable.category_art_house
        listOf("grocer", "pantry").any { it in n } -> R.drawable.category_art_basket
        else -> null
    }
}

/**
 * An icon for a category, guessed from its name.
 *
 * Categories are the shop's own words, so there is no fixed list to map.
 * A guess that falls through lands on a neutral shape rather than a wrong
 * picture.
 */
internal fun categoryIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        listOf("produce", "vegetable", "fruit", "farm", "fresh").any { it in n } -> Icons.Filled.Eco
        listOf("coffee", "tea", "cafe").any { it in n } -> Icons.Filled.LocalCafe
        listOf("beverage", "drink", "juice").any { it in n } -> Icons.Filled.LocalDrink
        listOf("bak", "bread", "pastr").any { it in n } -> Icons.Filled.BakeryDining
        listOf("dessert", "sweet", "delicac", "cake").any { it in n } -> Icons.Filled.Cake
        listOf("snack", "chip", "cookie").any { it in n } -> Icons.Filled.Cookie
        listOf("meat", "seafood", "fish").any { it in n } -> Icons.Filled.SetMeal
        listOf("dairy", "egg").any { it in n } -> Icons.Filled.Egg
        listOf("frozen", "ice").any { it in n } -> Icons.Filled.AcUnit
        listOf("rice", "meal", "main", "ready", "starter").any { it in n } -> Icons.Filled.RiceBowl
        listOf("souvenir", "gift").any { it in n } -> Icons.Filled.CardGiftcard
        listOf("craft", "handi", "woven", "art").any { it in n } -> Icons.Filled.Palette
        listOf("salon", "nail", "beauty", "spa").any { it in n } -> Icons.Filled.Spa
        listOf("home", "house").any { it in n } -> Icons.Filled.Home
        listOf("grocer", "pantry", "goods").any { it in n } -> Icons.Filled.ShoppingBasket
        else -> Icons.Filled.Category
    }
}
