<script setup lang="ts">
import BrandLogo from '@pos/core/components/BrandLogo.vue'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'

// Deliberately smaller than the storefront's FdFooter: this page has one job,
// and a full sitemap under the registration form competes with it. Everything
// here is an exit for someone who has decided *not* to sign up today.
</script>

<template>
  <footer class="mf">
    <div class="mf__inner">
      <div class="mf__brand">
        <BrandLogo variant="light" :size="19" />
        <p class="mf__line">Local vendors, local riders, no commissions.</p>
      </div>

      <nav class="mf__links" aria-label="Footer">
        <a href="/">Back to shopping</a>
        <a href="/about">Our story</a>
        <a href="/app/auth">Sign in</a>
        <a :href="supportMailto('Omaykan signup')">{{ SUPPORT_EMAIL }}</a>
      </nav>
    </div>

    <p class="mf__legal">© 2026 Omaykan</p>
  </footer>
</template>

<style scoped>
.mf {
  background: #ffffff;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
  padding: 40px 40px 32px;
}

.mf__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  flex-wrap: wrap;
  max-width: 1280px;
  margin: 0 auto;
}

.mf__brand { display: grid; gap: 8px; }

.mf__line {
  margin: 0;
  font-size: 13px;
  color: #8a938e;
}

.mf__links {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.mf__links a {
  font-size: 13.5px;
  font-weight: 600;
  color: #5b6b62;
  text-decoration: none;
  transition: color 150ms;
}

.mf__links a:hover { color: #1a1a1a; }

.mf__legal {
  max-width: 1280px;
  margin: 28px auto 0;
  padding-top: 20px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  font-size: 12.5px;
  color: #a3aca7;
}

@media (max-width: 720px) {
  .mf { padding: 32px 20px 24px; }
  .mf__links { gap: 16px; }
}
</style>
