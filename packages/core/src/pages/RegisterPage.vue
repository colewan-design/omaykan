<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChevronUp, Printer, ShoppingCart } from '@lucide/vue'
import { formatCurrency } from '@pos/shared/index'
import GroceryOrderPanel from '@pos/core/components/GroceryOrderPanel.vue'
import GroceryProductGrid from '@pos/core/components/GroceryProductGrid.vue'
import LowStockToast from '@pos/core/components/LowStockToast.vue'
import NailSalonOrderPanel from '@pos/core/components/NailSalonOrderPanel.vue'
import NailSalonProductGrid from '@pos/core/components/NailSalonProductGrid.vue'
import OrderPanel from '@pos/core/components/OrderPanel.vue'
import ProductGrid from '@pos/core/components/ProductGrid.vue'
import RestaurantOrderPanel from '@pos/core/components/RestaurantOrderPanel.vue'
import RestaurantProductGrid from '@pos/core/components/RestaurantProductGrid.vue'
import ShiftPanel from '@pos/core/components/ShiftPanel.vue'
import { usePosStore } from '@pos/core/stores/pos'
import { haptic, ImpactStyle } from '@pos/core/utils/haptics'
import { printReceipt } from '@pos/core/utils/receipt'
import { useSheetDrag } from '@pos/core/utils/sheetDrag'

const store = usePosStore()
const mobileCartOpen = ref(false)

function openMobileCart() {
  mobileCartOpen.value = true
  haptic(ImpactStyle.Light)
}

const {
  dragOffset: orderDragOffset,
  isDragging: isOrderDragging,
  isClosing: isOrderClosing,
  onPointerDown: onOrderPointerDown,
  onPointerMove: onOrderPointerMove,
  onPointerUp: onOrderPointerUp,
  onPointerCancel: onOrderPointerCancel,
} = useSheetDrag({ onDismiss: () => { mobileCartOpen.value = false } })

onMounted(() => {
  if (!store.isReady) {
    void store.initialize()
  }
})

const isGrocery = computed(() => store.settings.businessMode === 'grocery')
const isRestaurant = computed(() => store.settings.businessMode === 'restaurant')
const isNailSalon = computed(() => store.settings.businessMode === 'nail-salon')

function handlePrintReceipt() {
  if (!store.lastCompletedOrder) return
  printReceipt(store.lastCompletedOrder, {
    name: store.settings.businessName,
    imageUrl: store.settings.businessImageUrl,
  })
}
</script>

<template>
  <div class="register-page-stack">
    <div class="register-workspace">
      <ShiftPanel />

      <div class="register-layout">
        <div class="register-main-col">

        <div v-if="store.lastCompletedOrder" class="success-banner register-success-banner">
          <span>Payment captured for ticket {{ store.lastCompletedOrder.ticketNumber }}.</span>
          <button class="secondary-button register-success-banner__print" type="button" @click="handlePrintReceipt">
            <Printer :size="15" />
            <span>Print receipt</span>
          </button>
        </div>

        <div v-if="store.lowStockAlert.length" class="register-toast-area">
          <LowStockToast />
        </div>

        <RestaurantProductGrid v-if="isRestaurant" />
        <GroceryProductGrid v-else-if="isGrocery" />
        <NailSalonProductGrid v-else-if="isNailSalon" />
        <ProductGrid v-else />

        </div>

        <div
          class="register-order-backdrop"
          :class="{ 'register-order-backdrop--visible': mobileCartOpen }"
          :style="mobileCartOpen && (isOrderDragging || isOrderClosing) ? { '--sheet-backdrop-opacity': String(Math.max(0, 1 - orderDragOffset / 400)) } : undefined"
          @click="mobileCartOpen = false"
        />

        <div
          class="register-order-wrap"
          :class="{ 'register-order-wrap--open': mobileCartOpen, 'register-order-wrap--dragging': isOrderDragging }"
          :style="mobileCartOpen && orderDragOffset ? { transform: `translateY(${orderDragOffset}px)` } : undefined"
        >
        <button
          class="register-order-wrap__handle"
          type="button"
          aria-label="Drag down to close order"
          @pointerdown="onOrderPointerDown"
          @pointermove="onOrderPointerMove"
          @pointerup="onOrderPointerUp"
          @pointercancel="onOrderPointerCancel"
        >
          <span class="register-order-wrap__grabber" />
        </button>
          <RestaurantOrderPanel v-if="isRestaurant" @payment-open="mobileCartOpen = false" />
          <GroceryOrderPanel v-else-if="isGrocery" @payment-open="mobileCartOpen = false" />
          <NailSalonOrderPanel v-else-if="isNailSalon" @payment-open="mobileCartOpen = false" />
          <OrderPanel v-else @payment-open="mobileCartOpen = false" />
        </div>
      </div>

      <button
        class="register-cart-bar"
        :class="{ 'register-cart-bar--hidden': mobileCartOpen }"
        type="button"
        @click="openMobileCart"
      >
        <span class="register-cart-bar__info">
          <span class="register-cart-bar__icon-wrap">
            <ShoppingCart :size="18" />
            <span v-if="store.itemCount > 0" class="register-cart-bar__count">{{ store.itemCount }}</span>
          </span>
          <span class="register-cart-bar__total">{{ formatCurrency(store.totalCents) }}</span>
        </span>
        <span class="register-cart-bar__label">
          View order
          <ChevronUp :size="16" />
        </span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.register-page-stack {
  --pos-green: #05634f;
  --pos-green-dark: #034b3e;
  --pos-mint: #eef7f5;
  --pos-border: #dce8e5;
  --accent: #06634f;
  --accent-pressed: #044d3e;
  --accent-text-on: #ffffff;
  --bg-base: #edf6f4;
  --bg-surface: #ffffff;
  --bg-elevated: #ffffff;
  --fill: #f0f6f4;
  --text-primary: #17211f;
  --text-secondary: #65726f;
  --text-tertiary: #87918e;
  --separator: #dce8e5;
  /* Sized by the shell's content area (AppShell's --register main), not the
     viewport: the workspace nav rail sits beside this, same as every other
     page. */
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--separator);
  border-radius: 20px;
  background: #eef8f5;
  box-shadow: 0 18px 50px rgba(5, 80, 63, 0.06);
}

.register-workspace {
  display: flex;
  height: 100%;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}

.register-layout {
  flex: 1;
  min-height: 0;
  padding: 16px;
  gap: 16px;
  grid-template-columns: minmax(0, 1.9fr) minmax(420px, 1fr);
}

/* The till now starts where the workspace rail ends, ~230px further in than
   its old private rail left it. Through that band the order panel gives up its
   420px floor rather than squeezing the catalog down to a single card. */
@media (min-width: 1081px) and (max-width: 1320px) {
  .register-layout {
    gap: 12px;
    padding: 12px 12px 12px 10px;
    grid-template-columns: minmax(0, 1fr) minmax(320px, 0.62fr);
  }
}

.register-main-col {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.register-success-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin: 0;
}

.register-success-banner__print {
  flex: none;
  min-height: 36px;
  padding: 0 var(--space-3);
}

.register-order-wrap {
  min-width: 0;
  min-height: 0;
  margin: 0;
  overflow: hidden;
}

.register-order-wrap__handle {
  display: none;
}

.register-order-backdrop {
  position: fixed;
  inset: 0;
  z-index: 38;
  display: none;
  background: rgba(15, 23, 42, calc(0.32 * var(--sheet-backdrop-opacity, 1)));
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--dur-fast, 0.2s) var(--ease-out, ease), background-color var(--dur-fast, 0.2s) var(--ease-out, ease);
}

.register-cart-bar {
  display: none;
}

@media (max-width: 1024px) {
  .register-page-stack {
    height: auto;
    overflow: visible;
    border: none;
    border-radius: 0;
  }

  .register-layout {
    flex: none;
    padding: 12px;
  }

  .register-workspace {
    height: auto;
    min-height: 100vh;
  }

  .register-main-col {
    overflow: visible;
  }

  .page-stack {
    padding-bottom: 88px;
  }

  .register-order-backdrop {
    display: block;
  }

  .register-order-backdrop--visible {
    opacity: 1;
    pointer-events: auto;
  }

  .register-order-wrap {
    position: fixed;
    inset: auto 0 0 0;
    z-index: 39;
    height: 85vh;
    display: flex;
    flex-direction: column;
    border-radius: 24px 24px 0 0;
    background: var(--bg-surface);
    box-shadow: var(--shadow-lg);
    transform: translateY(110%);
    transition: transform var(--dur-base, 0.3s) var(--ease-spring, ease);
    overflow: hidden;
    will-change: transform;
  }

  .register-order-wrap--open {
    transform: translateY(0);
  }

  .register-order-wrap--dragging {
    transition: none;
  }

  .register-order-wrap__handle {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: none;
    width: 100%;
    height: 28px;
    padding: 0;
    border: none;
    background: transparent;
    touch-action: none;
    cursor: grab;
  }

  .register-order-wrap__handle:active {
    cursor: grabbing;
  }

  .register-order-wrap__grabber {
    width: 36px;
    height: 5px;
    border-radius: var(--radius-pill);
    background: var(--separator);
    transition: background-color var(--dur-fast, 0.2s) var(--ease-out, ease), width var(--dur-fast, 0.2s) var(--ease-out, ease);
  }

  .register-order-wrap__handle:hover .register-order-wrap__grabber,
  .register-order-wrap__handle:active .register-order-wrap__grabber {
    width: 44px;
    background: var(--text-tertiary);
  }

  .register-order-wrap .order-panel {
    position: static;
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    border: none;
    box-shadow: none;
    background: transparent;
  }

  .register-cart-bar {
    position: fixed;
    right: 12px;
    bottom: 12px;
    left: 12px;
    z-index: 36;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
    min-height: 60px;
    padding: 0 var(--space-4);
    border: none;
    border-radius: 20px;
    background: var(--accent);
    color: var(--accent-text-on);
    box-shadow: var(--shadow-lg);
    transition: transform var(--dur-fast, 0.2s) var(--ease-out, ease), opacity var(--dur-fast, 0.2s) var(--ease-out, ease);
  }

  .register-cart-bar--hidden {
    transform: translateY(120%);
    opacity: 0;
    pointer-events: none;
  }

  .register-cart-bar__info {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }

  .register-cart-bar__icon-wrap {
    position: relative;
    display: inline-flex;
  }

  .register-cart-bar__count {
    position: absolute;
    top: -8px;
    right: -10px;
    display: inline-grid;
    place-items: center;
    min-width: 18px;
    height: 18px;
    padding: 0 4px;
    border-radius: 999px;
    background: var(--bg-elevated);
    color: var(--accent);
    font-size: 11px;
    font-weight: 700;
    line-height: 1;
  }

  .register-cart-bar__total {
    font: var(--type-headline);
    font-variant-numeric: tabular-nums;
  }

  .register-cart-bar__label {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    font: var(--type-subhead);
    font-weight: 600;
  }
}
</style>
