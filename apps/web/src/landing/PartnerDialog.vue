<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * Merchant recruitment prompt, shown once per visitor on the landing page.
 *
 * Deliberately once, not once per visit: the same shopper lands here often, and
 * a modal that reappears on every page load is the kind that gets the whole
 * site closed. The dismissal is remembered under an `sf_` key, matching the
 * other storefront-facing keys (sf_cart, sf_order_history) — the landing page
 * shares an origin with the real app, so anything written here has to be
 * unmistakably marketing state and never collide with POS data.
 */

// Bumping the suffix re-shows the dialog to everyone who already dismissed the
// previous one, which is what a new offer needs.
const STORAGE_KEY = 'sf_partner_invite_v1'

// Long enough for the hero to paint first: opening over a half-rendered page
// reads as a pop-up rather than an invitation.
const OPEN_DELAY_MS = 900

const open = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null

// Storage throws outright in some privacy modes, so a failed read must leave
// the visitor seeing the site rather than an error — treat it as "not yet
// dismissed" and simply skip remembering the answer.
function alreadyDismissed(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) !== null
  } catch {
    return false
  }
}

function remember(): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, new Date().toISOString())
  } catch {
    /* nothing to do — the dialog simply returns on the next visit */
  }
}

function dismiss(): void {
  open.value = false
  remember()
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') dismiss()
}

onMounted(() => {
  if (alreadyDismissed()) return

  timer = setTimeout(() => {
    open.value = true
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKeydown)
  }, OPEN_DELAY_MS)
})

onBeforeUnmount(() => {
  if (timer !== null) clearTimeout(timer)
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="partner"
      role="dialog"
      aria-modal="true"
      aria-labelledby="partner-title"
    >
      <div class="partner__scrim" @click="dismiss" />

      <div class="partner__panel">
        <button type="button" class="partner__x" aria-label="Close" @click="dismiss">&times;</button>

        <p class="partner__eyebrow">Early access</p>
        <h2 id="partner-title" class="partner__title">We&rsquo;re looking for shops to partner with.</h2>
        <p class="partner__body">
          Your first month is free — a full point-of-sale, your own storefront, and riders to
          deliver for you. If the platform isn&rsquo;t right for your shop, just cancel your
          account. No lock-in, and you keep 100% of every sale.
        </p>

        <div class="partner__actions">
          <!-- Counts as an answer: someone heading to signup should not be
               asked again on the way back. -->
          <a href="/signup" class="partner__cta" @click="remember">Register as a seller</a>
          <button type="button" class="partner__later" @click="dismiss">Maybe later</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.partner {
  position: fixed;
  inset: 0;
  z-index: 210;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.partner__scrim {
  position: absolute;
  inset: 0;
  background: rgba(23, 35, 28, 0.42);
  animation: partner-fade 0.18s ease-out;
}

.partner__panel {
  position: relative;
  width: min(460px, 100%);
  padding: 34px 34px 28px;
  border-radius: 14px;
  background: #fbf8f3;
  box-shadow: 0 24px 60px rgba(23, 35, 28, 0.28);
  animation: partner-rise 0.22s ease-out;
}

@keyframes partner-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes partner-rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}

@media (prefers-reduced-motion: reduce) {
  .partner__scrim, .partner__panel { animation: none; }
}

.partner__x {
  position: absolute;
  top: 12px;
  right: 14px;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #6f665c;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}
.partner__x:hover { background: #ede5d8; color: #231d18; }

.partner__eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #b4532a;
}

.partner__title {
  margin: 0 0 10px;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.2;
  color: #231d18;
}

.partner__body {
  margin: 0 0 22px;
  font-size: 14.5px;
  line-height: 1.6;
  color: #6f665c;
}

.partner__actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.partner__cta {
  padding: 13px 28px;
  border-radius: 6px;
  background: #b4532a;
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  text-decoration: none;
}
.partner__cta:hover { background: #93401d; color: #fff; }

.partner__later {
  border: 0;
  background: transparent;
  padding: 6px 2px;
  color: #6f665c;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.partner__later:hover { color: #231d18; text-decoration: underline; }

@media (max-width: 520px) {
  .partner__panel { padding: 28px 22px 24px; }
  .partner__actions { gap: 10px; }
  .partner__cta { width: 100%; text-align: center; }
}
.partner__title { font-family: 'Libre Caslon Text', Georgia, serif; font-weight: 700; letter-spacing: 0; }
</style>
