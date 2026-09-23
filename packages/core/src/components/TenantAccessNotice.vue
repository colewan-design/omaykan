<script setup lang="ts">
import { AlertTriangle, Ban, RefreshCw } from '@lucide/vue'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { supportMailto, SUPPORT_EMAIL } from '@pos/shared/index'
import type { TenantAccessStatus } from '@pos/data/index'
import { getPosRepository } from '@pos/core/services/runtime'

/**
 * What the till says when the server says this shop may not trade.
 *
 * Suspended covers the whole workspace. Every call would fail anyway, one
 * screen at a time and each in its own words; one clear sentence with a way to
 * reach support is the only useful thing left to show.
 *
 * Unpaid is a banner. The shop can still read its own records — orders,
 * reports, history — and the data layer already refuses a new sale, so the
 * banner is there to say *why* before someone reaches the register and finds
 * out the hard way.
 *
 * Both re-ask the server: on mount, from the button, and every minute while
 * showing. A till that was refused learns it has been let back in from that
 * check, not by being made to sign out and in again.
 *
 * See documentation/subscription-and-suspension.md §5.
 */

const emit = defineEmits<{ signOut: [] }>()

const RECHECK_MS = 60_000

const repository = getPosRepository()
const status = ref<TenantAccessStatus>({ access: 'allowed', message: null })
const checking = ref(false)
let unsubscribe: (() => void) | null = null
let timer: ReturnType<typeof setInterval> | null = null

async function recheck() {
  if (checking.value) {
    return
  }

  checking.value = true
  try {
    status.value = await repository.refreshTenantAccess()
  } finally {
    checking.value = false
  }
}

watch(
  () => status.value.access,
  (access) => {
    if (access === 'allowed') {
      if (timer) clearInterval(timer)
      timer = null
      return
    }

    timer ??= setInterval(() => void recheck(), RECHECK_MS)
  },
)

onMounted(async () => {
  unsubscribe = repository.onTenantAccessChange((next) => {
    status.value = next
  })
  status.value = await repository.loadTenantAccess()
  void recheck()
})

onUnmounted(() => {
  unsubscribe?.()
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div
    v-if="status.access === 'suspended'"
    class="tenant-block"
    role="alertdialog"
    aria-modal="true"
    aria-labelledby="tenant-block-title"
    aria-describedby="tenant-block-body"
  >
    <div class="tenant-block__card">
      <span class="tenant-block__icon" aria-hidden="true"><Ban :size="28" /></span>
      <h2 id="tenant-block-title" class="tenant-block__title">This shop is suspended</h2>
      <p id="tenant-block-body" class="tenant-block__body">{{ status.message }}</p>
      <p class="tenant-block__note">
        Nothing on this till has been lost. Sales already taken are kept here and will send once the
        shop is reactivated.
      </p>
      <div class="tenant-block__actions">
        <a class="primary-button" :href="supportMailto('Shop suspended')">Email {{ SUPPORT_EMAIL }}</a>
        <button class="secondary-button" type="button" :disabled="checking" @click="recheck">
          <RefreshCw :size="16" :class="{ 'tenant-spin': checking }" aria-hidden="true" />
          {{ checking ? 'Checking…' : 'Check again' }}
        </button>
        <button class="tenant-block__signout" type="button" @click="emit('signOut')">Sign out</button>
      </div>
    </div>
  </div>

  <div v-else-if="status.access === 'unpaid'" class="tenant-banner" role="status">
    <AlertTriangle class="tenant-banner__icon" :size="18" aria-hidden="true" />
    <p class="tenant-banner__text">
      <strong>New sales are paused.</strong>
      {{ status.message }} Orders already placed can still be finished.
    </p>
    <button class="tenant-banner__action" type="button" :disabled="checking" @click="recheck">
      {{ checking ? 'Checking…' : 'Check again' }}
    </button>
  </div>
</template>

<style scoped>
.tenant-block {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 16px;
  background: color-mix(in srgb, var(--bg-base) 88%, transparent);
  backdrop-filter: blur(6px);
}

.tenant-block__card {
  display: grid;
  justify-items: center;
  gap: 12px;
  max-width: 440px;
  width: 100%;
  padding: 28px 24px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-xl);
  background: var(--bg-elevated);
  box-shadow: var(--shadow-lg);
  text-align: center;
}

.tenant-block__icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--danger) 14%, transparent);
  color: var(--danger);
}

.tenant-block__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.tenant-block__body,
.tenant-block__note {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.tenant-block__body {
  color: var(--text-primary);
}

.tenant-block__actions {
  display: grid;
  gap: 8px;
  width: 100%;
  margin-top: 8px;
}

.tenant-block__actions .primary-button,
.tenant-block__actions .secondary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  text-decoration: none;
}

.tenant-block__signout {
  padding: 8px;
  border: 0;
  background: none;
  font: inherit;
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
}

.tenant-block__signout:hover {
  color: var(--text-primary);
}

.tenant-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: var(--space-3, 12px);
  padding: 10px 14px;
  border: 1px solid color-mix(in srgb, var(--warning) 45%, transparent);
  border-left: 4px solid var(--warning);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--warning) 10%, var(--bg-elevated));
}

.tenant-banner__icon {
  flex-shrink: 0;
  color: var(--warning);
}

.tenant-banner__text {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
  color: var(--text-primary);
}

.tenant-banner__action {
  flex-shrink: 0;
  padding: 6px 12px;
  border: 1px solid var(--separator);
  border-radius: var(--radius-pill);
  background: var(--bg-elevated);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
}

.tenant-banner__action:disabled,
.tenant-block__actions button:disabled {
  opacity: 0.6;
  cursor: default;
}

.tenant-spin {
  animation: tenant-spin 0.9s linear infinite;
}

@keyframes tenant-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 560px) {
  .tenant-banner {
    flex-wrap: wrap;
  }

  .tenant-banner__action {
    margin-left: 28px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tenant-spin {
    animation: none;
  }
}
</style>
