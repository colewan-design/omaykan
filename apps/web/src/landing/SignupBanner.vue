<script setup lang="ts">
import { computed } from 'vue'
import { useCustomerAccount } from '@pos/web/commerce/customer'

const account = useCustomerAccount()
const accountLabel = computed(() => {
  const firstName = account.account.value?.name.trim().split(/\s+/)[0]
  return account.signedIn.value && firstName ? `Hi, ${firstName}` : 'Sign in'
})
</script>

<template>
  <aside class="utility" aria-label="Omaykan service links">
    <div class="utility__inner">
      <div class="utility__place">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M12 2a7 7 0 0 0-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 0 0-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z" />
        </svg>
        <strong>Baguio City &amp; La Trinidad</strong>
        <span class="utility__promise">Delivering fresh local goods to your home</span>
      </div>

      <nav class="utility__links" aria-label="Service shortcuts">
        <a href="/seller/signup">Become a seller</a>
        <a href="/about#help">Help</a>
        <a href="/account">Track order</a>
        <a href="/account">{{ accountLabel }}</a>
        <a v-if="!account.signedIn.value" class="utility__cta" href="/account?mode=register">
          Create an account
        </a>
      </nav>
    </div>
  </aside>
</template>

<style scoped>
.utility {
  background: #123f2b;
  color: rgba(255, 255, 255, 0.9);
}

.utility__inner {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 0 var(--fd-inset);
  font-size: 12px;
}

.utility__place,
.utility__links {
  display: flex;
  align-items: center;
}

.utility__place { gap: 7px; }
.utility__place svg { color: #f0a94b; }
.utility__promise { margin-left: 12px; color: rgba(255, 255, 255, 0.65); }

.utility__links { gap: 0; }
.utility__links a {
  padding: 5px 10px;
  color: inherit;
  font-weight: 600;
  white-space: nowrap;
}
.utility__links a + a { border-left: 1px solid rgba(255, 255, 255, 0.18); }
.utility__links a:hover { color: #fff; text-decoration: underline; text-underline-offset: 3px; }

.utility__links .utility__cta {
  margin-left: 10px;
  padding: 6px 14px;
  border: 0;
  border-radius: 999px;
  background: #e9683b;
  color: #fff;
  text-decoration: none;
}
.utility__cta:hover { background: #d9582f; }

@media (max-width: 840px) {
  .utility__promise,
  .utility__links a:not(:last-child):not(:nth-last-child(2)) { display: none; }
  .utility__inner { min-height: 36px; }
}

@media (max-width: 520px) {
  .utility__inner { justify-content: center; }
  .utility__links { display: none; }
  .utility__place { font-size: 11.5px; }
}
</style>
