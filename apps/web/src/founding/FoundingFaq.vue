<script setup lang="ts">
import { ChevronDown } from '@lucide/vue'
import MountainMark from '@pos/web/landing/MountainMark.vue'
import { SUPPORT_EMAIL, supportMailto } from '@pos/shared/index'

/**
 * The questions a shop owner actually asks before filling the form in, in the
 * order they ask them.
 *
 * Built on <details>/<summary> rather than buttons and a `ref`. Three reasons:
 * the open/closed state is the element's own, so nothing here has to track it;
 * a browser's find-in-page opens a closed one to show the match; and the build
 * prerenders this page, so the answers are in the saved HTML whether or not
 * anything ever runs.
 */
const QUESTIONS: Array<{ q: string; a: string }> = [
  {
    q: 'What is the Omaykan Founding Seller Program?',
    a: 'It is our launch campaign for the first 30 businesses in Baguio and La Trinidad to join Omaykan. You get your online store set up free, no commission on your sales, and a founding-seller number that stays yours for good.',
  },
  {
    q: 'Who can apply?',
    a: 'Any real business selling in Baguio or La Trinidad — a sari-sari store, a market stall, a carinderia, a bakery, a salon, someone selling handicrafts. You do not need a website, a computer, or any online selling experience. If you have a stall and a phone, you can apply.',
  },
  {
    q: 'Is there really no commission?',
    a: 'Yes. Customers pay you directly — cash or GCash — and Omaykan never holds the money or takes a cut of a sale. What you charge is what you keep.',
  },
  {
    q: 'What happens after the free 2–3 months?',
    a: 'Your store carries on and it becomes ₱199 a month. That rate is locked in for founding sellers, so it stays ₱199 even when the regular price goes up later. We will tell you before the free period ends — nothing is charged automatically without your say-so.',
  },
  {
    q: 'How long does it take to get set up?',
    a: 'We call you after you apply to go through your products and take it from there. Most shops are live within a few days of that call. You do not build anything yourself — we set the store up and show you how to take orders on your phone.',
  },
  {
    q: 'What if all 30 places are gone?',
    a: 'Apply anyway. The 30 founding places are a launch campaign, not a limit on who can sell — once they are issued we still set new shops up, just without the founding badge and the locked-in rate.',
  },
]
</script>

<template>
  <section class="faq" aria-labelledby="faq-title">
    <header class="faq__head">
      <MountainMark :size="38" :sun="false" />
      <div>
        <h2 id="faq-title" class="faq__title">Frequently Asked Questions</h2>
        <p class="faq__sub">Quick answers about the founding seller program.</p>
      </div>
    </header>

    <details v-for="item in QUESTIONS" :key="item.q" class="faq__item">
      <summary class="faq__q">
        <span>{{ item.q }}</span>
        <ChevronDown class="faq__chev" :size="17" :stroke-width="2.2" aria-hidden="true" />
      </summary>
      <p class="faq__a">{{ item.a }}</p>
    </details>

    <p class="faq__more">
      Still unsure? Email us at
      <a :href="supportMailto('Founding seller question')">{{ SUPPORT_EMAIL }}</a>
      and a person will answer.
    </p>
  </section>
</template>

<style scoped>
.faq {
  padding: 20px 22px 18px;
  border-radius: 20px;
  background: var(--sf-paper);
  box-shadow: 0 24px 60px -36px rgba(13, 52, 36, 0.45), 0 1px 4px rgba(13, 52, 36, 0.05);
}

.faq__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.faq__head :deep(.mtn) {
  color: var(--sf-forest);
  flex-shrink: 0;
}

.faq__title {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--sf-ink);
}

.faq__sub {
  margin: 2px 0 0;
  font-size: 12.5px;
  line-height: 1.4;
  color: var(--sf-muted);
}

.faq__item {
  border-top: 1px solid var(--sf-rule);
}

.faq__q {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 13px 2px;
  font-size: 13.5px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--sf-ink);
  cursor: pointer;
  /* Safari still paints its own triangle without this. */
  list-style: none;
}

.faq__q::-webkit-details-marker { display: none; }

.faq__q:hover { color: var(--sf-forest); }

.faq__q:focus-visible {
  outline: 2px solid var(--sf-leaf);
  outline-offset: 2px;
  border-radius: 6px;
}

.faq__chev {
  flex-shrink: 0;
  color: var(--sf-muted);
  transition: transform 180ms ease;
}

.faq__item[open] .faq__chev { transform: rotate(180deg); }

.faq__a {
  margin: 0 0 14px;
  padding-right: 28px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--sf-muted);
}

.faq__more {
  margin: 14px 0 0;
  padding-top: 13px;
  border-top: 1px solid var(--sf-rule);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--sf-muted);
}

.faq__more a {
  color: var(--sf-clay);
  font-weight: 700;
}

.faq__more a:hover { text-decoration: underline; }

@media (max-width: 560px) {
  .faq { padding: 18px 16px 14px; }
}

@media (prefers-reduced-motion: reduce) {
  .faq__chev { transition: none; }
}
</style>
