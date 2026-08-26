<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { usePlatformSession } from '@pos/web/platform-admin/admin'
import { fetchOverview } from '@pos/web/platform-admin/api'
import SignIn from '@pos/web/platform-admin/SignIn.vue'
import { router } from '@pos/web/platform-admin/router'

/*
 * The portal's frame.
 *
 * Sign-in replaces the whole layout rather than sitting behind a route guard:
 * there is nothing in here worth rendering without a session, and a guard on a
 * cross-tenant tool would be theatre — the server is what enforces this.
 *
 * The queue counts live up here, on the nav, because they are the reason to
 * click. An operator opening this page wants to know whether there is work
 * before choosing where to go, and the alternative is visiting two screens to
 * find out that both are empty.
 */

const session = usePlatformSession()
const route = useRoute()

const pendingSignups = ref(0)
const pendingRiders = ref(0)

async function loadCounts() {
  if (!session.signedIn.value) return

  try {
    const overview = await fetchOverview()
    pendingSignups.value = overview.queues.pendingSignups
    pendingRiders.value = overview.queues.pendingRiders
  } catch {
    // The badges are a convenience. A failure here must not take the shell
    // down with it — the views report their own errors.
  }
}

onMounted(loadCounts)

// Re-read after any navigation: verifying a signup on the tenants screen has
// to take it off the badge without a reload.
watch(() => route.fullPath, loadCounts)
watch(() => session.signedIn.value, loadCounts)

async function signOut() {
  await session.signOut()
  await router.push('/')
}
</script>

<template>
  <div class="pa">
    <!-- Held until the stored token has been checked, so a returning operator
         never sees the sign-in form flash up before their own dashboard. -->
    <p v-if="session.hydrating.value" class="pa-empty">Loading…</p>

    <SignIn v-else-if="!session.signedIn.value" />

    <div v-else class="pa-shell">
      <aside class="pa-side">
        <div class="pa-side__brand">
          <BrandLogo variant="light" :size="18" />
          <span>Omaykan</span>
          <span class="pa-signin__tag">Platform</span>
        </div>

        <nav class="pa-nav">
          <RouterLink to="/" :class="{ 'pa-nav--on': route.name === 'overview' }">Overview</RouterLink>
          <RouterLink
            to="/tenants"
            :class="{ 'pa-nav--on': route.name === 'tenants' || route.name === 'tenant' }"
          >
            <span>Tenants</span>
            <span v-if="pendingSignups" class="pa-nav__count">{{ pendingSignups }}</span>
          </RouterLink>
          <RouterLink to="/riders" :class="{ 'pa-nav--on': route.name === 'riders' }">
            <span>Riders</span>
            <span v-if="pendingRiders" class="pa-nav__count">{{ pendingRiders }}</span>
          </RouterLink>
          <RouterLink to="/audit" :class="{ 'pa-nav--on': route.name === 'audit' }">Audit log</RouterLink>
          <RouterLink
            v-if="session.isOwner.value"
            to="/operators"
            :class="{ 'pa-nav--on': route.name === 'operators' }"
          >
            Operators
          </RouterLink>
        </nav>

        <div class="pa-side__who">
          <div>
            <div class="pa-side__name">{{ session.admin.value?.name }}</div>
            <div class="pa-side__role">{{ session.admin.value?.role }}</div>
          </div>
          <button class="pa-side__out" type="button" @click="signOut">Sign out</button>
        </div>
      </aside>

      <main class="pa-main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
