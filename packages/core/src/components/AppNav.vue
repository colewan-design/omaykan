<script setup lang="ts">
import {
  ArrowRight,
  BarChart3,
  Boxes,
  ClipboardList,
  Crown,
  LayoutDashboard,
  LayoutGrid,
  LogOut,
  type LucideIcon,
  MessageCircle,
  Package,
  Plug,
  ReceiptText,
  Settings,
  ShoppingCart,
  Truck,
  UserCircle2,
  Users,
} from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { useAuthStore } from '@pos/core/stores/auth'
import { useMessagesStore } from '@pos/core/stores/messages'
import type { AppPageKey } from '@pos/shared/index'

const emit = defineEmits<{ navigate: [] }>()
const route = useRoute()
const router = useRouter()
const store = usePosStore()
const auth = useAuthStore()
const messages = useMessagesStore()

interface NavItem {
  /** The permission that shows the item. Not unique — Messages rides on `orders`. */
  page: AppPageKey
  label: string
  icon: LucideIcon
  to: string
  ownerOnly?: boolean
}

/**
 * One flat list, ordered the way a shop moves through a day: take the money,
 * then what was sold, then what there is to sell, then who bought it, then the
 * back office. The headings this replaced cost a line of chrome above every
 * three items and split a list short enough to scan in one pass.
 */
const allNavItems: NavItem[] = [
  { page: 'dashboard', label: 'Dashboard', icon: LayoutDashboard, to: '/dashboard' },
  { page: 'register', label: 'Register', icon: ShoppingCart, to: '/register' },
  { page: 'sales', label: 'Sales', icon: BarChart3, to: '/sales' },
  { page: 'orders', label: 'Orders', icon: ReceiptText, to: '/orders' },
  { page: 'tables', label: 'Tables', icon: LayoutGrid, to: '/tables' },
  { page: 'orders', label: 'Messages', icon: MessageCircle, to: '/messages' },
  { page: 'products', label: 'Products', icon: Package, to: '/products' },
  { page: 'customers', label: 'Customers', icon: Users, to: '/customers' },
  { page: 'suppliers', label: 'Suppliers', icon: Truck, to: '/suppliers' },
  { page: 'employees', label: 'Employees', icon: UserCircle2, to: '/employees' },
  { page: 'inventory', label: 'Inventory', icon: Boxes, to: '/inventory' },
  { page: 'reports', label: 'Reports', icon: ClipboardList, to: '/reports' },
  { page: 'integrations', label: 'Integrations', icon: Plug, to: '/integrations', ownerOnly: true },
]

function visible(item: NavItem) {
  if (item.page === 'tables' && store.settings.businessMode !== 'restaurant') {
    return false
  }
  if (item.ownerOnly && !auth.isOwner) {
    return false
  }
  return auth.canAccess(item.page)
}

const navItems = computed(() => allNavItems.filter(visible))

const canOpenSettings = computed(() => auth.canAccess('settings'))

/**
 * The card sends people to Integrations, so only someone who can open that
 * page sees it — an upsell that dead-ends on a permission wall is worse than
 * no upsell.
 */
const showUpsell = computed(() => auth.isOwner && auth.canAccess('integrations'))

function isActive(path: string) {
  return route.path === path
}

const unreadLabel = computed(() => (messages.unread > 99 ? '99+' : String(messages.unread)))

async function handleLogout() {
  emit('navigate')
  await auth.logout()
  await router.replace({ name: 'auth' })
}

// Only someone who can open Messages is worth polling for. The store counts
// subscribers, so the sidebar and the mobile drawer share one timer.
let unsubscribe: (() => void) | null = null
onMounted(() => {
  if (auth.canAccess('orders')) unsubscribe = messages.subscribe()
})
onBeforeUnmount(() => unsubscribe?.())
</script>

<template>
  <nav class="workspace-nav">
    <div class="workspace-nav__brand">
      <BrandLogo variant="light" :size="20" />
      <p>{{ store.settings.businessName || 'Smart POS Solutions' }}</p>
    </div>

    <div class="workspace-nav__links">
      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        class="workspace-nav__link"
        :class="{ 'workspace-nav__link--active': isActive(item.to) }"
        :to="item.to"
        @click="emit('navigate')"
      >
        <component :is="item.icon" :size="18" />
        <span>{{ item.label }}</span>
        <!-- A dot, not a count: the number lives on the Messages page itself,
             and the rail only has to say "something is waiting". The count
             still reaches screen readers through the label. -->
        <span
          v-if="item.to === '/messages' && messages.unread > 0"
          class="workspace-nav__dot"
          role="status"
          :aria-label="`${unreadLabel} unread`"
        />
      </RouterLink>
    </div>

    <div class="workspace-nav__footer">
      <div v-if="showUpsell" class="workspace-nav__promo">
        <span class="workspace-nav__promo-icon">
          <Crown :size="16" />
        </span>
        <p class="workspace-nav__promo-title">Grow your business with Omaykan</p>
        <p class="workspace-nav__promo-copy">Discover new tools and features to sell more.</p>
        <RouterLink class="workspace-nav__promo-cta" to="/integrations" @click="emit('navigate')">
          <span>Explore now</span>
          <ArrowRight :size="16" />
        </RouterLink>
      </div>

      <div class="workspace-nav__account">
        <RouterLink
          v-if="canOpenSettings"
          class="workspace-nav__link"
          :class="{ 'workspace-nav__link--active': isActive('/settings') }"
          to="/settings"
          @click="emit('navigate')"
        >
          <Settings :size="18" />
          <span>Settings</span>
        </RouterLink>

        <button class="workspace-nav__link workspace-nav__link--button" type="button" @click="handleLogout">
          <LogOut :size="18" />
          <span>Sign out</span>
        </button>
      </div>
    </div>
  </nav>
</template>

<style scoped>
/* Brand and footer are pinned; only the link list scrolls, so the promo card
   and Sign out stay reachable on a short window without the whole rail
   sliding away under them. */
.workspace-nav {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 0;
  gap: var(--space-4);
}

.workspace-nav__brand {
  display: grid;
  justify-items: start;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2) 0;
}

.workspace-nav__brand p {
  margin: 0;
  color: var(--text-tertiary);
  font: var(--type-caption);
}

.workspace-nav__links {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-content: start;
  gap: 2px;
  min-height: 0;
  overflow-y: auto;
  /* The rail already has rounded corners framing this list; a full-width
     scrollbar track inside them reads as a second edge. */
  scrollbar-width: thin;
  /*
   * The list only overflows on a short window, and then the promo card sits
   * directly under it — so a hard cut looks like the list simply ends at
   * Inventory. The fade lands on empty space when everything fits, and on the
   * last row when it doesn't, which is the only time it has anything to say.
   */
  mask-image: linear-gradient(to bottom, #000 calc(100% - 20px), transparent);
}

.workspace-nav__link {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-height: 38px;
  padding: 0 var(--space-3);
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  font: var(--type-subhead);
  font-weight: 500;
  text-align: left;
  text-decoration: none;
  transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.workspace-nav__link:hover {
  background: var(--fill);
  color: var(--text-primary);
}

.workspace-nav__link--active,
.workspace-nav__link--active:hover {
  background: var(--accent);
  color: var(--accent-text-on);
  font-weight: 600;
}

.workspace-nav__link--button {
  cursor: pointer;
}

.workspace-nav__dot {
  width: 8px;
  height: 8px;
  margin-left: auto;
  border-radius: var(--radius-pill);
  background: var(--accent);
}

.workspace-nav__link--active .workspace-nav__dot {
  background: var(--accent-text-on);
}

.workspace-nav__footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-4);
}

/* Tinted off --warning rather than a fixed cream, so the card stays a warm
   note beside the accent in every theme — including the dark-only ones, where
   a literal cream would be the brightest thing on screen. */
.workspace-nav__promo {
  display: grid;
  justify-items: start;
  gap: var(--space-1);
  padding: var(--space-3);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--warning) 12%, var(--bg-elevated));
}

.workspace-nav__promo-icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--warning) 30%, var(--bg-elevated));
  color: var(--warning);
}

.workspace-nav__promo-title {
  margin: 0;
  color: var(--text-primary);
  font: var(--type-subhead);
  font-weight: 700;
}

.workspace-nav__promo-copy {
  margin: 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.workspace-nav__promo-cta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  width: 100%;
  min-height: 36px;
  margin-top: var(--space-1);
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  background: var(--accent);
  color: var(--accent-text-on);
  font: var(--type-subhead);
  font-weight: 600;
  text-decoration: none;
  transition: background var(--dur-fast) var(--ease-out);
}

.workspace-nav__promo-cta:hover {
  background: var(--accent-pressed);
}

.workspace-nav__account {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 2px;
  padding-top: var(--space-3);
  border-top: 1px solid var(--separator);
}

</style>
