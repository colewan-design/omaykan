<script setup lang="ts">
import { ClipboardList, ChevronRight } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import { useStorefrontOrderHistory } from '../orderHistory'

const orderHistory = useStorefrontOrderHistory()

function formatPlacedAt(iso: string): string {
  return new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}
</script>

<template>
  <div class="history">
    <header class="history__header">
      <h1>Your orders</h1>
      <p>Orders you've placed from this device.</p>
    </header>

    <div v-if="orderHistory.entries.value.length === 0" class="history__empty">
      <ClipboardList :size="28" />
      <p>No orders yet — start shopping and your orders will show up here.</p>
      <RouterLink to="/" class="history__empty-cta">Browse the store</RouterLink>
    </div>

    <RouterLink
      v-for="entry in orderHistory.entries.value"
      :key="entry.orderId"
      :to="{ name: 'order', params: { orderId: entry.orderId } }"
      class="history__item"
    >
      <div>
        <strong>Order {{ entry.ticketNumber }}</strong>
        <span>{{ formatPlacedAt(entry.placedAt) }}</span>
      </div>
      <div class="history__item-side">
        <strong>{{ formatCurrency(entry.totalCents) }}</strong>
        <ChevronRight :size="18" />
      </div>
    </RouterLink>
  </div>
</template>

<style scoped>
.history {
  display: grid;
  gap: 12px;
}

.history__header {
  padding: 10px 4px 2px;
}

.history__header h1 {
  margin: 0;
  color: #1f2430;
  font-size: 1.55rem;
  line-height: 1.05;
  letter-spacing: -0.03em;
}

.history__header p {
  margin: 10px 0 0;
  color: #6b7280;
  line-height: 1.5;
}

.history__empty {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 48px 24px;
  border-radius: 22px;
  background: #fff;
  color: #6b7280;
  text-align: center;
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.06);
}

.history__empty-cta {
  padding: 12px 18px;
  border-radius: 999px;
  background: #fdecd9;
  color: var(--sf-primary);
  text-decoration: none;
  font-weight: 800;
}

.history__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-radius: 20px;
  background: #fff;
  color: inherit;
  text-decoration: none;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.history__item strong {
  display: block;
  color: #1f2430;
}

.history__item span {
  display: block;
  margin-top: 4px;
  color: #8a8b90;
  font-size: 0.84rem;
}

.history__item-side {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--sf-primary);
}

.history__item-side strong {
  color: var(--sf-primary);
}
</style>
