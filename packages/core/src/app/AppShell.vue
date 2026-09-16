<script setup lang="ts">
import { Bell, ChevronDown, Menu, Search, Store, X } from '@lucide/vue'
import { computed, onMounted, onUnmounted, provide, ref, watch, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppNav from '@pos/core/components/AppNav.vue'
import { useAuthStore } from '@pos/core/stores/auth'
import { usePosStore } from '@pos/core/stores/pos'
import { OPEN_APP_NAV } from '@pos/core/app/navDrawer'

const store = usePosStore()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const navOpen = ref(false)
const profileOpen = ref(false)
const search = ref('')
const hydratedSessionUserId = ref<string | null | undefined>(undefined)

const showShellChrome = computed(() => route.name !== 'auth' && !!auth.currentUser)
const isRegisterRoute = computed(() => route.name === 'register')
const canOpenSettings = computed(() => auth.canAccess('settings'))
const storeName = computed(() => store.settings.businessName || 'Unnamed store')
const hasNotifications = computed(() =>
  store.lowStockProducts.length > 0
  || store.onlineOrders.some((order) => !order.voidedAt && (order.status !== 'served' || order.paymentStatus === 'unpaid')),
)

async function handleLogout() {
  await auth.logout()
  await router.replace({ name: 'auth' })
}

function closeNav() {
  navOpen.value = false
}

// The Register route hides the workspace topbar (ShiftPanel is its header), so
// its own header button is the only way to reach the nav on a narrow screen.
provide(OPEN_APP_NAV, () => {
  navOpen.value = true
})

function toggleProfileMenu() {
  profileOpen.value = !profileOpen.value
}

function closeProfileMenu() {
  profileOpen.value = false
}

async function goToSettings() {
  closeProfileMenu()
  if (!canOpenSettings.value) {
    return
  }
  await router.push({ name: 'settings' })
}

function handleDocumentClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  const profileRoot = document.querySelector('[data-profile-menu]')
  if (profileOpen.value && profileRoot && !profileRoot.contains(target)) {
    closeProfileMenu()
  }
}

watchEffect(() => {
  if (typeof document === 'undefined') {
    return
  }

  const appearance = store.settings.appearance

  if (appearance === 'light' || appearance === 'dark') {
    document.documentElement.dataset.theme = appearance
    return
  }

  delete document.documentElement.dataset.theme
})

watchEffect(() => {
  if (typeof document === 'undefined') {
    return
  }

  const theme = store.settings.theme

  if (theme === 'default') {
    delete document.documentElement.dataset.colorTheme
    return
  }

  document.documentElement.dataset.colorTheme = theme
})

watch(
  () => [auth.isReady, auth.session?.userId ?? null] as const,
  ([ready, userId]) => {
    if (!ready) {
      return
    }

    if (hydratedSessionUserId.value === userId && store.isReady) {
      return
    }

    hydratedSessionUserId.value = userId
    void store.initialize(true)
  },
  { immediate: true },
)

onMounted(() => {
  if (!auth.isReady) {
    void auth.initialize()
  }
  document.addEventListener('click', handleDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<template>
  <div class="workspace-shell">
    <template v-if="showShellChrome">
      <aside v-if="!isRegisterRoute" class="workspace-shell__sidebar">
        <AppNav />
      </aside>

      <div class="workspace-shell__main" :class="{ 'workspace-shell__main--register': isRegisterRoute }">
        <!-- Register brings its own header (ShiftPanel: clock, shift state,
             store), so it skips this one rather than stacking two. The rail
             beside it is the same AppNav every other page gets. -->
        <header v-if="!isRegisterRoute" class="workspace-topbar">
          <button class="workspace-topbar__menu" type="button" aria-label="Open navigation" @click="navOpen = true">
            <Menu :size="20" />
          </button>

          <label class="workspace-topbar__search">
            <Search :size="18" />
            <input v-model="search" type="search" placeholder="Search products, orders, customers..." />
          </label>

          <div class="workspace-topbar__actions">
            <button
              class="workspace-topbar__icon"
              type="button"
              :aria-label="hasNotifications ? 'Notifications, attention needed' : 'Notifications'"
            >
              <Bell :size="18" />
              <span v-if="hasNotifications" class="workspace-topbar__notification-dot" aria-hidden="true" />
            </button>
            <div class="workspace-topbar__profile-wrap" data-profile-menu>
              <button
                class="workspace-topbar__profile"
                type="button"
                :aria-expanded="profileOpen"
                aria-haspopup="menu"
                @click.stop="toggleProfileMenu"
              >
                <span class="workspace-topbar__avatar" aria-hidden="true">
                  <Store :size="16" />
                </span>
                <span class="workspace-topbar__profile-copy">
                  <strong>{{ auth.currentUser?.fullName }}</strong>
                  <small>{{ storeName }}</small>
                </span>
                <ChevronDown
                  class="workspace-topbar__profile-chevron"
                  :class="{ 'workspace-topbar__profile-chevron--open': profileOpen }"
                  :size="15"
                  aria-hidden="true"
                />
              </button>
              <div v-if="profileOpen" class="workspace-topbar__profile-menu" role="menu">
                <button
                  v-if="canOpenSettings"
                  class="workspace-topbar__profile-item"
                  type="button"
                  role="menuitem"
                  @click="goToSettings"
                >
                  Settings
                </button>
                <button
                  class="workspace-topbar__profile-item workspace-topbar__profile-item--danger"
                  type="button"
                  role="menuitem"
                  @click="handleLogout"
                >
                  Sign out
                </button>
              </div>
            </div>
          </div>
        </header>

        <main class="workspace-topbar__content" :class="{ 'workspace-topbar__content--register': isRegisterRoute }">
          <RouterView />
        </main>
      </div>

      <div v-if="navOpen" class="workspace-mobile-nav" @click.self="closeNav">
        <div class="workspace-mobile-nav__panel">
          <!-- No "Navigation" heading: AppNav opens with the brand lockup
               directly below, and the two read as one label repeated. -->
          <div class="workspace-mobile-nav__header">
            <button class="workspace-topbar__icon" type="button" aria-label="Close navigation" @click="closeNav">
              <X :size="18" />
            </button>
          </div>
          <AppNav @navigate="closeNav" />
        </div>
      </div>
    </template>

    <main v-else class="workspace-shell__auth">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.workspace-shell {
  --workspace-shell-edge-gap: 12px;
  --workspace-shell-sidebar-width: 232px;
  --workspace-shell-content-gap: 16px;
  min-height: 100vh;
}

/* No padding: the sign-in screen paints its own full-bleed ground and owns its
   gutters (see .auth-page in app.css). A frame of app background around that
   green would read as a box sitting on the page rather than the page itself. */
.workspace-shell__auth {
  padding: 0;
}

.workspace-shell__sidebar {
  position: fixed;
  inset: var(--workspace-shell-edge-gap) auto var(--workspace-shell-edge-gap) var(--workspace-shell-edge-gap);
  width: var(--workspace-shell-sidebar-width);
  /* One row, handed whole to AppNav: the nav pins its own brand and footer and
     scrolls only the links between them. */
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  padding: var(--space-4);
  border: 1px solid var(--separator);
  border-radius: 28px;
  background: var(--material-bar-bg);
  backdrop-filter: var(--material-bar);
  /* The rail is a panel resting on the page, not a sheet floating over it —
     --shadow-lg's 40px spread read as a thick dark edge beside the content. */
  box-shadow: var(--shadow-sm);
}

.workspace-shell__main {
  min-width: 0;
  margin-left: calc(
    var(--workspace-shell-edge-gap) +
    var(--workspace-shell-sidebar-width) +
    var(--workspace-shell-content-gap)
  );
  min-height: 100vh;
  padding: var(--workspace-shell-edge-gap) var(--workspace-shell-edge-gap) var(--workspace-shell-edge-gap) 0;
}

/* The till is a fixed-height workspace, not a scrolling page: it fills what is
   left of the viewport beside the rail and lets its own columns scroll. */
.workspace-shell__main--register {
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  margin-left: 0;
  height: 100vh;
  min-height: 0;
  padding: 22px 24px;
}

.workspace-topbar__content--register {
  grid-template-rows: minmax(0, 1fr);
  min-height: 0;
}

.workspace-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: var(--space-3);
}

.workspace-topbar__menu,
.workspace-topbar__icon {
  display: none;
  width: 40px;
  height: 40px;
  border: 1px solid var(--separator);
  border-radius: 12px;
  background: var(--bg-elevated);
  color: var(--text-primary);
}

.workspace-topbar__search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid var(--separator);
  border-radius: 10px;
  background: var(--bg-elevated);
  color: var(--text-secondary);
  box-shadow: var(--shadow-sm);
}

.workspace-topbar__search input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
}

.workspace-topbar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workspace-topbar__profile-wrap {
  position: relative;
}

.workspace-topbar__icon {
  position: relative;
  display: inline-grid;
  place-items: center;
}

.workspace-topbar__notification-dot {
  position: absolute;
  top: 7px;
  right: 7px;
  width: 7px;
  height: 7px;
  border: 2px solid var(--bg-elevated);
  border-radius: var(--radius-pill);
  background: var(--danger);
  box-sizing: content-box;
}

.workspace-topbar__profile {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 152px;
  min-height: 44px;
  padding: 4px 10px 4px 5px;
  border: 1px solid var(--separator);
  border-radius: 12px;
  background: var(--bg-elevated);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.workspace-topbar__avatar {
  display: inline-grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-pill);
  background: var(--accent);
  color: var(--accent-text-on);
}

.workspace-topbar__profile-copy {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 1px;
  text-align: left;
}

.workspace-topbar__profile-copy strong,
.workspace-topbar__profile-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-topbar__profile-copy strong {
  font-size: 12px;
  font-weight: 700;
  line-height: 15px;
}

.workspace-topbar__profile-copy small {
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 400;
  line-height: 13px;
}

.workspace-topbar__profile-chevron {
  flex: none;
  margin-left: 2px;
  color: var(--text-secondary);
  transition: transform var(--dur-fast) var(--ease-out);
}

.workspace-topbar__profile-chevron--open {
  transform: rotate(180deg);
}

.workspace-topbar__profile-menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  min-width: 180px;
  padding: 8px;
  border: 1px solid var(--separator);
  border-radius: 16px;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-md);
  display: grid;
  gap: 4px;
  z-index: 20;
}

.workspace-topbar__profile-item {
  min-height: 40px;
  padding: 0 14px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: var(--text-primary);
  text-align: left;
  font: var(--type-subhead);
}

.workspace-topbar__profile-item:hover {
  background: var(--fill);
}

.workspace-topbar__profile-item--danger {
  color: var(--danger);
}

.workspace-topbar__content {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-width: 0;
  gap: var(--space-4);
}

.workspace-mobile-nav {
  position: fixed;
  inset: 0;
  z-index: 30;
  background: rgba(15, 23, 42, 0.28);
  display: grid;
  align-items: stretch;
}

.workspace-mobile-nav__panel {
  width: min(88vw, 300px);
  height: 100%;
  padding: var(--space-5);
  background: var(--bg-elevated);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-4);
  box-shadow: var(--shadow-lg);
}

.workspace-mobile-nav__header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

@media (max-width: 1080px) {
  .workspace-shell__sidebar {
    display: none;
  }

  .workspace-shell__main {
    margin-left: 0;
    padding: 16px;
  }

  .workspace-topbar__menu {
    display: inline-grid;
    place-items: center;
  }

  .workspace-shell__main--register {
    display: block;
    height: auto;
    padding: 0;
  }
}

@media (max-width: 720px) {
  .workspace-topbar {
    flex-wrap: wrap;
  }

  .workspace-topbar__search {
    order: 3;
    width: 100%;
    flex-basis: 100%;
  }

  .workspace-topbar__actions {
    margin-left: auto;
  }

  .workspace-topbar__profile {
    justify-content: center;
    width: 40px;
    min-width: 40px;
    min-height: 40px;
    padding: 3px;
  }

  .workspace-topbar__avatar {
    width: 32px;
    height: 32px;
  }

  .workspace-topbar__profile-copy,
  .workspace-topbar__profile-chevron {
    display: none;
  }
}
</style>
