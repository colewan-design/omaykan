<script setup lang="ts">
import {
  BarChart3,
  Boxes,
  LayoutDashboard,
  LayoutGrid,
  type LucideIcon,
  MessageCircle,
  Package,
  Plug,
  ReceiptText,
  ShoppingCart,
  Truck,
  UserCircle2,
  Users,
} from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { useAuthStore } from '@pos/core/stores/auth'
import { useMessagesStore } from '@pos/core/stores/messages'
import type { AppPageKey } from '@pos/shared/index'

const emit = defineEmits<{ navigate: [] }>()
const route = useRoute()
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

const allNavItems: NavItem[] = [
  { page: 'dashboard', label: 'Dashboard', icon: LayoutDashboard, to: '/dashboard' },
  { page: 'register', label: 'Register', icon: ShoppingCart, to: '/register' },
  { page: 'sales', label: 'Sales', icon: BarChart3, to: '/sales' },
  { page: 'orders', label: 'Orders', icon: ReceiptText, to: '/orders' },
  { page: 'orders', label: 'Messages', icon: MessageCircle, to: '/messages' },
  { page: 'tables', label: 'Tables', icon: LayoutGrid, to: '/tables' },
  { page: 'products', label: 'Products', icon: Package, to: '/products' },
  { page: 'customers', label: 'Customers', icon: Users, to: '/customers' },
  { page: 'suppliers', label: 'Suppliers', icon: Truck, to: '/suppliers' },
  { page: 'employees', label: 'Employees', icon: UserCircle2, to: '/employees' },
  { page: 'inventory', label: 'Inventory', icon: Boxes, to: '/inventory' },
  { page: 'reports', label: 'Reports', icon: ShoppingCart, to: '/reports' },
  { page: 'integrations', label: 'Integrations', icon: Plug, to: '/integrations', ownerOnly: true },
] as const

const navItems = computed(() =>
  allNavItems.filter((item) => {
    if (item.page === 'tables' && store.settings.businessMode !== 'restaurant') {
      return false
    }
    if (item.ownerOnly && !auth.isOwner) {
      return false
    }
    return auth.canAccess(item.page)
  }),
)

function isActive(path: string) {
  return route.path === path
}

const unreadLabel = computed(() => (messages.unread > 99 ? '99+' : String(messages.unread)))

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
      <div class="workspace-nav__brand-copy">
        <p>{{ store.settings.businessName || 'Smart POS Solutions' }}</p>
      </div>
    </div>

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
      <span
        v-if="item.to === '/messages' && messages.unread > 0"
        class="workspace-nav__badge"
        :aria-label="`${messages.unread} unread`"
      >
        {{ unreadLabel }}
      </span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.workspace-nav {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-content: start;
  gap: var(--space-2);
}

.workspace-nav__brand {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
  padding: var(--space-2) 0 var(--space-4);
  border-bottom: 1px solid var(--separator);
}

.workspace-nav__brand-copy {
  min-width: 0;
}

.workspace-nav__brand p {
  margin: 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.workspace-nav__link {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 44px;
  padding: 0 var(--space-3);
  border-radius: 14px;
  color: var(--text-secondary);
  text-decoration: none;
  transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.workspace-nav__link:hover {
  background: var(--fill);
  color: var(--text-primary);
}

.workspace-nav__link--active {
  background: linear-gradient(135deg, var(--accent), var(--accent-pressed));
  color: var(--accent-text-on);
  box-shadow: 0 12px 24px color-mix(in srgb, var(--accent) 22%, transparent);
}

.workspace-nav__badge {
  display: grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  margin-left: auto;
  padding: 0 6px;
  border-radius: var(--radius-pill);
  background: var(--danger);
  color: #fff;
  font: var(--type-caption);
  font-weight: 700;
}
</style>
