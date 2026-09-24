<script setup lang="ts">
import { CircleCheck } from '@lucide/vue'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'

/**
 * What stands where the form was, once an application is in.
 *
 * It replaces the form rather than sitting above it, so there is no second
 * submit to press by accident — a duplicate application costs an operator a
 * phone call to work out which row is real.
 *
 * Nothing here claims a founding number. One is assigned when an operator
 * accepts the application, and this page has no way to know whether that will
 * happen — saying "you are #7" and then not issuing it is worse than not
 * saying it.
 */
defineProps<{ businessName: string }>()
</script>

<template>
  <section class="sent" aria-labelledby="sent-title" tabindex="-1">
    <span class="sent__mark" aria-hidden="true"><CircleCheck :size="30" :stroke-width="2" /></span>

    <h2 id="sent-title" class="sent__title">We have your application.</h2>
    <p class="sent__lead">
      <strong>{{ businessName }}</strong> is with our team. We have emailed you a copy —
      check your inbox, and your spam folder if it is not there.
    </p>

    <ol class="sent__steps">
      <li>
        <span class="sent__num">1</span>
        <span>Someone from Omaykan calls or messages you to go through your products.</span>
      </li>
      <li>
        <span class="sent__num">2</span>
        <span>We build your online store for you — there is nothing for you to set up.</span>
      </li>
      <li>
        <span class="sent__num">3</span>
        <span>You get your shop link and QR code, and you can start taking orders.</span>
      </li>
    </ol>

    <p class="sent__note">
      Anything to add or correct? Reply to the confirmation email, or write to
      <a :href="supportMailto('My seller application')">{{ SUPPORT_EMAIL }}</a>.
    </p>

    <div class="sent__links">
      <a class="sent__link" href="/">Have a look at the marketplace</a>
      <a class="sent__link" href="/about">How Omaykan works</a>
    </div>
  </section>
</template>

<style scoped>
.sent {
  padding: 28px 24px 24px;
  border-radius: 20px;
  background: var(--sf-paper);
  box-shadow: 0 30px 70px -34px rgba(13, 52, 36, 0.5), 0 2px 10px rgba(13, 52, 36, 0.07);
}

.sent:focus-visible {
  outline: 2px solid var(--sf-leaf);
  outline-offset: 3px;
}

.sent__mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 14px;
  border-radius: 16px;
  background: var(--sf-leaf-wash);
  color: var(--sf-leaf);
}

.sent__title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.025em;
  color: var(--sf-ink);
}

.sent__lead {
  margin: 0 0 20px;
  font-size: 14px;
  line-height: 1.55;
  color: var(--sf-muted);
}

.sent__lead strong { color: var(--sf-ink); }

.sent__steps {
  display: grid;
  gap: 12px;
  margin: 0 0 20px;
  padding: 16px 16px 16px 14px;
  border-radius: 14px;
  background: var(--sf-sand);
  list-style: none;
}

.sent__steps li {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--sf-ink);
}

.sent__num {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--sf-forest);
  color: #fff;
  font-size: 11.5px;
  font-weight: 800;
}

.sent__note {
  margin: 0 0 18px;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--sf-muted);
}

.sent__note a {
  color: var(--sf-clay);
  font-weight: 700;
}

.sent__note a:hover { text-decoration: underline; }

.sent__links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 16px;
  border-top: 1px solid var(--sf-rule);
}

.sent__link {
  padding: 9px 15px;
  border: 1px solid var(--sf-rule);
  border-radius: 9px;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--sf-forest);
  transition: background-color 140ms, border-color 140ms;
}

.sent__link:hover {
  background: var(--sf-leaf-wash);
  border-color: transparent;
}

@media (max-width: 560px) {
  .sent { padding: 22px 16px 18px; }
}
</style>
