<script setup lang="ts">
import { ref } from 'vue'
import { Bell, ChevronDown, LogOut, type LucideIcon, Menu, Search, X } from '@lucide/vue'
import type { Operator } from './api'

// The frame every operator screen sits in: the green rail down the left, the
// search bar across the top, and the page's own content between them.
//
// The rail is a plain list of buttons rather than a router: the portal is one
// page with one token in memory, and adding a route table would mean a signed
// -out reload on every navigation. The address bar does carry the view as a
// hash so a screen can be linked to — see AdminApp.
//
// On a phone the rail becomes a drawer over the page. It is the same markup
// either way; only its position changes, so there is no second nav to keep in
// step with this one.

export interface NavItem {
  key: string
  label: string
  icon: LucideIcon
  /** Shown as a count beside the label. Absent means nothing to say. */
  badge?: number
}

defineProps<{
  items: NavItem[]
  active: string
  operator: Operator | null
  /** The marketplace's own name, from Settings. */
  brandName: string
}>()

const emit = defineEmits<{
  navigate: [key: string]
  search: [term: string]
  signOut: []
}>()

const drawerOpen = ref(false)
const menuOpen = ref(false)
const searchTerm = ref('')

function go(key: string) {
  drawerOpen.value = false
  emit('navigate', key)
}

function submitSearch() {
  emit('search', searchTerm.value.trim())
}
</script>

<template>
  <div class="shell">
    <!-- ── The rail ─────────────────────────────────────────────────── -->
    <aside class="shell__rail" :class="{ 'shell__rail--open': drawerOpen }">
      <div class="shell__brand">
        <span class="shell__mark" aria-hidden="true">
          <!-- The storefront's leaf, drawn rather than fetched: the rail is
               the first thing painted and should not wait on an image. -->
          <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
            <path d="M12 2C7 4 4 8 4 13a8 8 0 0 0 8 8 8 8 0 0 0 8-8c0-5-3-9-8-11Zm0 3.2c3 1.9 4.8 4.7 4.8 7.8A4.8 4.8 0 0 1 12 17.8Z" />
          </svg>
        </span>
        <span class="shell__brandtext">
          <strong>{{ brandName }}</strong>
          <small>Admin Portal</small>
        </span>

        <button class="shell__close" type="button" aria-label="Close menu" @click="drawerOpen = false">
          <X :size="18" />
        </button>
      </div>

      <nav class="shell__nav" aria-label="Operator sections">
        <button
          v-for="item in items"
          :key="item.key"
          type="button"
          class="shell__navitem"
          :class="{ 'shell__navitem--on': item.key === active }"
          :aria-current="item.key === active ? 'page' : undefined"
          @click="go(item.key)"
        >
          <component :is="item.icon" :size="17" :stroke-width="1.9" aria-hidden="true" />
          <span>{{ item.label }}</span>
          <span v-if="item.badge" class="shell__badge">{{ item.badge }}</span>
        </button>
      </nav>

      <!-- The line from the storefront's own footer, kept so the portal reads
           as part of the same product rather than a bolted-on back office. -->
      <p class="shell__creed">
        Higher&nbsp;Communities,<br />Brighter&nbsp;Tomorrows
      </p>
    </aside>

    <div v-if="drawerOpen" class="shell__scrim" @click="drawerOpen = false"></div>

    <!-- ── The page ─────────────────────────────────────────────────── -->
    <div class="shell__main">
      <header class="shell__top">
        <button class="shell__hamburger" type="button" aria-label="Open menu" @click="drawerOpen = true">
          <Menu :size="20" />
        </button>

        <form class="adm-field shell__search" role="search" @submit.prevent="submitSearch">
          <Search :size="16" aria-hidden="true" />
          <input
            v-model="searchTerm"
            type="search"
            placeholder="Search orders, sellers, products, customers…"
            aria-label="Search the marketplace"
          />
        </form>

        <button type="button" class="shell__alerts" aria-label="Notifications">
          <Bell :size="18" :stroke-width="1.8" aria-hidden="true" />
          <span aria-hidden="true"></span>
        </button>

        <div class="shell__account">
          <button
            type="button"
            class="shell__chip"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            <span class="shell__avatar" aria-hidden="true">{{ (operator?.name ?? '?').slice(0, 1) }}</span>
            <span class="shell__who">
              <strong>{{ operator?.name ?? 'Operator' }}</strong>
              <small>{{ operator?.email ?? '' }}</small>
            </span>
            <ChevronDown :size="15" aria-hidden="true" />
          </button>

          <div v-if="menuOpen" class="shell__menu">
            <button type="button" @click="menuOpen = false; emit('signOut')">
              <LogOut :size="15" aria-hidden="true" />
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main class="shell__body">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--adm-sidebar) minmax(0, 1fr);
  min-height: 100vh;
  background: var(--sf-cream);
}

/* ── Rail ────────────────────────────────────────────────────────────── */

.shell__rail {
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
  background: var(--sf-forest);
  color: #e8e3d9;
}

.shell__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
}

.shell__mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.1);
  color: #b9d9a8;
  flex: none;
}

.shell__brandtext {
  display: grid;
  min-width: 0;
}

.shell__brandtext strong {
  font-family: var(--sf-serif);
  font-size: 15px;
  line-height: 1.2;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shell__brandtext small {
  font-size: 10.5px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(232, 227, 217, 0.6);
}

.shell__close {
  display: none;
  margin-left: auto;
  border: none;
  background: none;
  color: inherit;
  cursor: pointer;
}

.shell__nav {
  display: grid;
  gap: 2px;
  padding: 6px 10px;
  overflow-y: auto;
}

.shell__navitem {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 9px 11px;
  border: none;
  border-radius: 9px;
  background: none;
  color: rgba(232, 227, 217, 0.82);
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
}

.shell__navitem:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #fff;
}

.shell__navitem--on {
  background: var(--sf-forest-soft);
  color: #fff;
  font-weight: 600;
}

.shell__badge {
  margin-left: auto;
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--sf-clay);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.shell__creed {
  margin: auto 16px 12px;
  color: rgba(232, 227, 217, 0.5);
  font-family: var(--sf-serif);
  font-size: 11.5px;
  line-height: 1.45;
}

.shell__scrim {
  display: none;
}

/* ── Top bar ─────────────────────────────────────────────────────────── */

.shell__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.shell__top {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px var(--adm-gutter);
  border-bottom: 1px solid var(--sf-rule);
  background: var(--sf-paper);
  position: sticky;
  top: 0;
  z-index: 5;
}

.shell__hamburger {
  display: none;
  border: none;
  background: none;
  color: var(--sf-ink);
  cursor: pointer;
}

.shell__search {
  flex: 1;
  max-width: 460px;
}

.shell__account {
  position: relative;
}

.shell__alerts {
  position: relative;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  margin-left: auto;
  padding: 0;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--sf-ink);
  cursor: pointer;
}

.shell__alerts:hover {
  background: var(--sf-sand);
}

.shell__alerts span {
  position: absolute;
  top: 6px;
  right: 7px;
  width: 7px;
  height: 7px;
  border: 1.5px solid var(--sf-paper);
  border-radius: 50%;
  background: #ef4936;
}

.shell__chip {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 5px 9px 5px 5px;
  border: 1px solid var(--sf-rule);
  border-radius: 999px;
  background: #fff;
  color: var(--sf-ink);
  font: inherit;
  cursor: pointer;
}

.shell__avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--sf-forest);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
}

.shell__who {
  display: grid;
  text-align: left;
  line-height: 1.25;
}

.shell__who strong {
  font-size: 13px;
}

.shell__who small {
  color: var(--sf-faint);
  font-size: 11px;
}

.shell__menu {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  min-width: 160px;
  padding: 5px;
  border: 1px solid var(--sf-rule);
  border-radius: 11px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(35, 29, 24, 0.14);
}

.shell__menu button {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 9px 11px;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--sf-ink);
  font: inherit;
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
}

.shell__menu button:hover {
  background: var(--sf-sand);
}

.shell__body {
  padding: 0 0 56px;
}

/* ── Narrow ──────────────────────────────────────────────────────────── */

@media (max-width: 900px) {
  .shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .shell__rail {
    position: fixed;
    z-index: 30;
    width: var(--adm-sidebar);
    transform: translateX(-100%);
    transition: transform 200ms var(--ease-out, ease);
  }

  .shell__rail--open {
    transform: none;
  }

  .shell__close,
  .shell__hamburger {
    display: block;
  }

  .shell__scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 20;
    background: rgba(23, 35, 28, 0.45);
  }

  .shell__who {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .shell__rail {
    transition: none;
  }
}
</style>
