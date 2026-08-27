<script setup lang="ts">
import { Bell, LogOut, Menu, Search, UserCircle2, X } from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref, watch, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppNav from '@pos/core/components/AppNav.vue'
import { useAuthStore } from '@pos/core/stores/auth'
import { usePosStore } from '@pos/core/stores/pos'

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

async function handleLogout() {
  await auth.logout()
  await router.replace({ name: 'auth' })
}

function closeNav() {
  navOpen.value = false
}

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
    <template v-if="showShellChrome && !isRegisterRoute">
      <aside class="workspace-shell__sidebar">
        <AppNav />
        <button class="workspace-shell__signout" type="button" @click="handleLogout">
          <LogOut :size="18" />
          <span>Sign out</span>
        </button>
      </aside>

      <div class="workspace-shell__main">
        <header class="workspace-topbar">
          <button class="workspace-topbar__menu" type="button" aria-label="Open navigation" @click="navOpen = true">
            <Menu :size="20" />
          </button>

          <label class="workspace-topbar__search">
            <Search :size="18" />
            <input v-model="search" type="search" placeholder="Search products, orders, customers..." />
          </label>

          <div class="workspace-topbar__actions">
            <button class="workspace-topbar__icon" type="button" aria-label="Notifications">
              <Bell :size="18" />
            </button>
            <div class="workspace-topbar__profile-wrap" data-profile-menu>
              <button class="workspace-topbar__profile" type="button" @click.stop="toggleProfileMenu">
                <UserCircle2 :size="20" />
                <span>{{ auth.currentUser?.fullName }}</span>
              </button>
              <div v-if="profileOpen" class="workspace-topbar__profile-menu">
                <button
                  v-if="canOpenSettings"
                  class="workspace-topbar__profile-item"
                  type="button"
                  @click="goToSettings"
                >
                  Settings
                </button>
                <button class="workspace-topbar__profile-item workspace-topbar__profile-item--danger" type="button" @click="handleLogout">
                  Sign out
                </button>
              </div>
            </div>
          </div>
        </header>

        <main class="workspace-topbar__content">
          <RouterView />
        </main>
      </div>

      <div v-if="navOpen" class="workspace-mobile-nav" @click.self="closeNav">
        <div class="workspace-mobile-nav__panel">
          <div class="workspace-mobile-nav__header">
            <strong>Navigation</strong>
            <button class="workspace-topbar__icon" type="button" aria-label="Close navigation" @click="closeNav">
              <X :size="18" />
            </button>
          </div>
          <AppNav @navigate="closeNav" />
          <button class="workspace-shell__signout workspace-shell__signout--mobile" type="button" @click="handleLogout">
            <LogOut :size="18" />
            <span>Sign out</span>
          </button>
        </div>
      </div>
    </template>

    <main v-else-if="showShellChrome" class="workspace-shell__register">
      <RouterView />
    </main>

    <main v-else class="workspace-shell__auth">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.workspace-shell {
  --workspace-shell-edge-gap: 20px;
  --workspace-shell-sidebar-width: 248px;
  --workspace-shell-content-gap: 40px;
  min-height: 100vh;
}

/* No padding: the sign-in screen paints its own full-bleed ground and owns its
   gutters (see .auth-page in app.css). A frame of app background around that
   green would read as a box sitting on the page rather than the page itself. */
.workspace-shell__auth {
  padding: 0;
}

.workspace-shell__register {
  min-height: 100vh;
}

.workspace-shell__sidebar {
  position: fixed;
  inset: var(--workspace-shell-edge-gap) auto var(--workspace-shell-edge-gap) var(--workspace-shell-edge-gap);
  width: var(--workspace-shell-sidebar-width);
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: 1fr auto;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 1px solid var(--separator);
  border-radius: 28px;
  background: var(--material-bar-bg);
  backdrop-filter: var(--material-bar);
  box-shadow: var(--shadow-lg);
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

.workspace-topbar {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--separator);
  border-radius: 24px;
  background: var(--material-bar-bg);
  backdrop-filter: var(--material-bar);
}

.workspace-topbar__menu,
.workspace-topbar__icon {
  display: none;
  width: 42px;
  height: 42px;
  border: 1px solid var(--separator);
  border-radius: 14px;
  background: var(--bg-elevated);
  color: var(--text-primary);
}

.workspace-topbar__search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 48px;
  padding: 0 var(--space-4);
  border: 1px solid var(--separator);
  border-radius: 16px;
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
  gap: var(--space-3);
}

.workspace-topbar__profile-wrap {
  position: relative;
}

.workspace-topbar__icon {
  display: inline-grid;
  place-items: center;
}

.workspace-topbar__profile {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 44px;
  padding: 0 var(--space-4);
  border: 1px solid var(--separator);
  border-radius: 16px;
  background: var(--bg-elevated);
  color: var(--text-primary);
  font: var(--type-subhead);
  box-shadow: var(--shadow-sm);
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
  gap: var(--space-5);
}

.workspace-shell__signout {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 46px;
  border: 1px solid var(--separator);
  border-radius: 16px;
  background: var(--bg-elevated);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
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
  grid-template-rows: auto 1fr auto;
  gap: var(--space-4);
  box-shadow: var(--shadow-lg);
}

.workspace-mobile-nav__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.workspace-shell__signout--mobile {
  width: 100%;
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
}

@media (max-width: 720px) {
  .workspace-shell__register {
    padding: 12px;
  }

  .workspace-topbar {
    flex-wrap: wrap;
    padding: var(--space-4);
  }

  .workspace-topbar__search {
    order: 3;
    width: 100%;
    flex-basis: 100%;
  }

  .workspace-topbar__actions {
    margin-left: auto;
  }

  .workspace-topbar__profile span {
    display: none;
  }
}
</style>
