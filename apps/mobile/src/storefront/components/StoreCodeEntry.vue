<script setup lang="ts">
import { ref } from 'vue'
import { Store } from '@lucide/vue'
import { useStorefrontPairing } from '../pairing'

const emit = defineEmits<{ paired: [] }>()
const pairing = useStorefrontPairing()

const code = ref('')
const submitting = ref(false)
const error = ref('')

async function handleSubmit() {
  const trimmed = code.value.trim()
  if (!trimmed || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    await pairing.resolve(trimmed)
    emit('paired')
  } catch {
    error.value = "We couldn't find a store with that code. Double-check and try again."
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="pair">
    <div class="pair__card">
      <div class="pair__icon"><Store :size="26" /></div>
      <h1>Welcome</h1>
      <p>Enter your store code to start shopping.</p>

      <form class="pair__form" @submit.prevent="handleSubmit">
        <input
          v-model="code"
          type="text"
          autocapitalize="characters"
          autocomplete="off"
          placeholder="e.g. DEMO01"
        />
        <p v-if="error" class="pair__error">{{ error }}</p>
        <button type="submit" :disabled="submitting || !code.trim()">
          {{ submitting ? 'Checking…' : 'Continue' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.pair {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #f8f6f2;
  color: #1f2430;
  font-family: "Poppins", "Segoe UI", sans-serif;
}

.pair__card {
  width: 100%;
  max-width: 360px;
  display: grid;
  gap: 6px;
  padding: 28px 24px;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 16px 34px rgba(31, 36, 48, 0.1);
  text-align: center;
}

.pair__icon {
  justify-self: center;
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 6px;
  border-radius: 16px;
  background: var(--sf-banner-green);
  color: var(--sf-primary);
}

.pair__card h1 {
  margin: 0;
  font-size: 1.4rem;
}

.pair__card p {
  margin: 0 0 14px;
  color: #6f7480;
  font-size: 0.92rem;
}

.pair__form {
  display: grid;
  gap: 12px;
}

.pair__form input {
  min-height: 50px;
  padding: 0 16px;
  border: 1px solid rgba(31, 36, 48, 0.12);
  border-radius: 14px;
  background: #f8f6f2;
  color: #1f2430;
  text-align: center;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font: 700 1.02rem/1 inherit;
}

.pair__form input::placeholder {
  color: #b7b4ad;
  text-transform: none;
  letter-spacing: normal;
  font-weight: 500;
}

.pair__error {
  margin: -4px 0 0;
  color: #c1503f;
  font-size: 0.86rem;
  font-weight: 600;
}

.pair__form button {
  min-height: 50px;
  border: none;
  border-radius: 14px;
  background: var(--sf-primary);
  color: #fff;
  font: 800 1rem/1 inherit;
}

.pair__form button:disabled {
  opacity: 0.5;
}
</style>
