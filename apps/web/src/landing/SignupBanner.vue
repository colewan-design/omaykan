<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useCustomerAccount } from '@pos/web/commerce/customer'

/**
 * Account prompt above the site chrome, shown once per visitor.
 *
 * Dismissal is remembered under an `sf_` key, the same convention
 * PartnerDialog and the cart already use — the landing page shares an origin
 * with the real app, so anything written here has to be unmistakably
 * marketing state and never collide with POS data.
 *
 * It sits *outside* the sticky header block on purpose: a promo strip that
 * stays pinned eats a row of screen on every scroll of a long grocery shelf,
 * which is the opposite of what a shelf needs. It scrolls away with the page
 * and the chrome sticks on its own, as it did before.
 *
 * What it does not do is promise anything. There is no discount engine, no
 * promo code, and no free-delivery budget in this product — the delivery fee
 * goes to the rider whole — so the offer is the account itself: a saved
 * address, an order you can watch, and a basket you can repeat. Naming a perk
 * we cannot honour at checkout would be a lie the customer finds at the worst
 * possible moment.
 */

// Bumping the suffix re-shows the banner to everyone who dismissed the
// previous one, which is what changed copy needs.
const STORAGE_KEY = 'sf_account_invite_v1'

const account = useCustomerAccount()

const dismissed = ref(true)

// Storage throws outright in some privacy modes. A failed read must leave the
// visitor seeing the site rather than an error, so treat it as "not yet
// dismissed" and simply skip remembering the answer.
onMounted(() => {
  try {
    dismissed.value = window.localStorage.getItem(STORAGE_KEY) !== null
  } catch {
    dismissed.value = false
  }
})

/**
 * Hidden while the stored token is still being traded for an account: showing
 * "create an account" to someone who has one, for the moment that resolves,
 * is the same jolt the account page avoids on its own gate.
 */
const visible = computed(
  () => !dismissed.value && !account.hydrating.value && !account.signedIn.value,
)

function dismiss(): void {
  dismissed.value = true
  try {
    window.localStorage.setItem(STORAGE_KEY, new Date().toISOString())
  } catch {
    /* nothing to do — the banner simply returns on the next visit */
  }
}
</script>

<template>
  <aside v-if="visible" class="sfb" aria-label="Create an account">
    <div class="sfb__inner">
      <p class="sfb__lead">
        <strong>New here?</strong>
        <span class="sfb__pitch">
          Save your address, follow your order to the door, and reorder your
          usual in one tap.
        </span>
      </p>

      <a class="sfb__cta" href="/account?mode=register">Create a free account</a>

      <span class="sfb__signin">
        Already have one? <a href="/account">Sign in</a>
      </span>
    </div>

    <button type="button" class="sfb__x" aria-label="Dismiss" @click="dismiss">
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2.2"
        stroke-linecap="round"
        aria-hidden="true"
      >
        <path d="M18 6 6 18M6 6l12 12" />
      </svg>
    </button>
  </aside>
</template>

<style scoped>
/* Lime on near-black: the same pair the cart badge and the active category
   underline already use, so the strip reads as part of the chrome rather than
   a third-party ad pasted above it. */
.sfb {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 var(--fd-gutter);
  background: #eeeeee;
  color: #231d18;
}

.sfb__inner {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  /* Room for the close button so centred copy is never overlapped by it. */
  padding: 10px 28px 10px 0;
}

.sfb__lead {
  margin: 0;
  font-size: 14px;
  line-height: 1.4;
}

.sfb__lead strong {
  font-weight: 800;
}

.sfb__pitch {
  margin-left: 6px;
}

.sfb__cta {
  flex-shrink: 0;
  padding: 7px 16px;
  border-radius: 999px;
  background: #1f2e25;
  color: #fff;
  font-size: 13.5px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.sfb__cta:hover {
  background: #17231c;
}

.sfb__signin {
  font-size: 13px;
  white-space: nowrap;
}

.sfb__signin a {
  color: #231d18;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 2px;
}

/* Absolute so it pins to the strip's right edge without being pulled into the
   centred group when the copy wraps. */
.sfb__x {
  position: absolute;
  top: 50%;
  right: 10px;
  transform: translateY(-50%);
  display: grid;
  place-items: center;
  /* A 32px box around a 16px glyph: the close control is the one thing here a
     phone user is most likely to aim at, and it has to be hittable. */
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #231d18;
  cursor: pointer;
}

.sfb__x:hover {
  background: rgba(35, 29, 24, 0.12);
}

@media (max-width: 760px) {
  .sfb__inner {
    justify-content: flex-start;
    gap: 6px 12px;
    padding: 9px 34px 9px 0;
  }

  .sfb__lead {
    font-size: 13px;
  }

  /* The full pitch is three lines on a phone and pushes the shelves off the
     first screen. The offer survives in the button, which is the part that
     has to stay. */
  .sfb__pitch,
  .sfb__signin {
    display: none;
  }
}
</style>
