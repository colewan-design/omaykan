<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ArrowRight, AtSign, Check, Mail, MapPin, Phone, Store, Users } from '@lucide/vue'
import {
  ApplicationError,
  FALLBACK_CATEGORIES,
  submitApplication,
  type ApplicationDraft,
  type FoundingStatus,
} from './api'

const props = defineProps<{
  /** The campaign's live state, or null while it loads or if it cannot be read. */
  status: FoundingStatus | null
}>()

const emit = defineEmits<{ (event: 'applied', businessName: string): void }>()

const DESCRIPTION_LIMIT = 500

/**
 * `offersDelivery` starts unset rather than false. It is a question about the
 * business, and a pre-picked "No" is an answer nobody gave — an operator
 * planning rider coverage would be reading our default, not the shop.
 */
const form = reactive({
  businessName: '',
  ownerName: '',
  category: '',
  mobile: '',
  email: '',
  socialUrl: '',
  address: '',
  productsDescription: '',
  offersDelivery: null as boolean | null,
  wantsFounding: true,
})

const categories = computed(() => props.status?.categories ?? FALLBACK_CATEGORIES)

const saving = ref(false)
const formError = ref('')
/** Per-field messages, from our own checks or from the API's 422. */
const fieldErrors = reactive<Record<string, string>>({})

const remaining = computed(() => form.productsDescription.length)
const overLimit = computed(() => remaining.value > DESCRIPTION_LIMIT)

/**
 * Clear a field's error as soon as it is touched. Leaving it under an input
 * the person is actively fixing reads as though the fix did not take.
 */
function touched(field: string) {
  if (fieldErrors[field]) delete fieldErrors[field]
  if (formError.value) formError.value = ''
}

// The category list arrives with `status`. Nothing is preselected — picking
// one for them would file a stall under whatever happened to be first.
watch(
  () => props.status,
  (next) => {
    if (next && form.category && !next.categories.includes(form.category)) {
      form.category = ''
    }
  },
)

/**
 * The same rules the API enforces, checked here first so a correction happens
 * next to the field rather than after a round trip. The server is still the
 * authority — this only saves the trip.
 */
function validate(): boolean {
  for (const key of Object.keys(fieldErrors)) delete fieldErrors[key]

  if (!form.businessName.trim()) fieldErrors.businessName = 'Tell us the name of your business.'
  if (!form.ownerName.trim()) fieldErrors.ownerName = 'Who should we talk to?'
  if (!form.category) fieldErrors.category = 'Pick the closest one — we can change it later.'
  if (!form.mobile.trim()) fieldErrors.mobile = 'We need a number to call you on.'

  if (!form.email.trim()) {
    fieldErrors.email = 'We need an email to send your confirmation to.'
  } else if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) {
    fieldErrors.email = 'That email address does not look right.'
  }

  if (!form.address.trim()) fieldErrors.address = 'Where can customers find you?'

  if (!form.productsDescription.trim()) {
    fieldErrors.productsDescription = 'A line or two about what you sell is enough.'
  } else if (overLimit.value) {
    fieldErrors.productsDescription = `That is ${remaining.value - DESCRIPTION_LIMIT} characters over.`
  }

  if (form.offersDelivery === null) fieldErrors.offersDelivery = 'Let us know either way.'

  return Object.keys(fieldErrors).length === 0
}

/** Move focus to the first thing that needs fixing, so it is not scrolled past. */
function focusFirstError() {
  const first = document.querySelector<HTMLElement>('.sa [aria-invalid="true"], .sa [data-error="true"]')
  first?.focus({ preventScroll: true })
  first?.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

async function submit() {
  if (saving.value) return
  formError.value = ''

  if (!validate()) {
    formError.value = 'Some details need another look.'
    focusFirstError()
    return
  }

  saving.value = true
  try {
    const draft: ApplicationDraft = {
      businessName: form.businessName.trim(),
      ownerName: form.ownerName.trim(),
      category: form.category,
      mobile: form.mobile.trim(),
      email: form.email.trim(),
      socialUrl: form.socialUrl.trim(),
      address: form.address.trim(),
      productsDescription: form.productsDescription.trim(),
      // Checked by `validate` above, so the null is gone by here.
      offersDelivery: form.offersDelivery === true,
      wantsFounding: form.wantsFounding,
    }

    const result = await submitApplication(draft)
    emit('applied', result.businessName)
  } catch (error) {
    if (error instanceof ApplicationError) {
      formError.value = error.message
      Object.assign(fieldErrors, error.fields)
      if (Object.keys(error.fields).length > 0) focusFirstError()
    } else {
      formError.value = 'Something went wrong sending that. Try again in a moment.'
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="sa" aria-labelledby="sa-title">
    <header class="sa__head">
      <span class="sa__mark" aria-hidden="true"><Store :size="22" :stroke-width="2.1" /></span>
      <div>
        <h2 id="sa-title" class="sa__title">Seller Application</h2>
        <p class="sa__sub">Tell us about your business. We'll take care of the rest.</p>
      </div>
    </header>

    <!-- novalidate: the browser's own bubbles say "Please fill out this field"
         over every input at once, and cannot be styled or read in place. The
         checks in `validate` replace them, field by field. -->
    <form class="sa__form" novalidate @submit.prevent="submit">
      <div class="sa__row">
        <label class="sa__field">
          <span class="sa__label">Business Name</span>
          <input
            v-model="form.businessName"
            type="text"
            class="sa__input"
            placeholder="e.g. Aling Rosa's Pasalubong"
            autocomplete="organization"
            maxlength="120"
            :aria-invalid="Boolean(fieldErrors.businessName)"
            :aria-describedby="fieldErrors.businessName ? 'sa-err-businessName' : undefined"
            @input="touched('businessName')"
          />
          <span v-if="fieldErrors.businessName" id="sa-err-businessName" class="sa__error">
            {{ fieldErrors.businessName }}
          </span>
        </label>

        <label class="sa__field">
          <span class="sa__label">Owner / Contact Person</span>
          <input
            v-model="form.ownerName"
            type="text"
            class="sa__input"
            placeholder="e.g. Maria Santos"
            autocomplete="name"
            maxlength="120"
            :aria-invalid="Boolean(fieldErrors.ownerName)"
            :aria-describedby="fieldErrors.ownerName ? 'sa-err-ownerName' : undefined"
            @input="touched('ownerName')"
          />
          <span v-if="fieldErrors.ownerName" id="sa-err-ownerName" class="sa__error">
            {{ fieldErrors.ownerName }}
          </span>
        </label>
      </div>

      <label class="sa__field">
        <span class="sa__label">Business Category</span>
        <select
          v-model="form.category"
          class="sa__input sa__select"
          :class="{ 'sa__select--empty': !form.category }"
          :aria-invalid="Boolean(fieldErrors.category)"
          :aria-describedby="fieldErrors.category ? 'sa-err-category' : undefined"
          @change="touched('category')"
        >
          <option value="" disabled>Select a category</option>
          <option v-for="category in categories" :key="category" :value="category">{{ category }}</option>
        </select>
        <span v-if="fieldErrors.category" id="sa-err-category" class="sa__error">{{ fieldErrors.category }}</span>
      </label>

      <div class="sa__row">
        <label class="sa__field">
          <span class="sa__label">Mobile Number</span>
          <span class="sa__wrap">
            <Phone class="sa__icon" :size="15" aria-hidden="true" />
            <input
              v-model="form.mobile"
              type="tel"
              class="sa__input sa__input--iconed"
              placeholder="e.g. 0917 123 4567"
              autocomplete="tel"
              inputmode="tel"
              maxlength="40"
              :aria-invalid="Boolean(fieldErrors.mobile)"
              :aria-describedby="fieldErrors.mobile ? 'sa-err-mobile' : undefined"
              @input="touched('mobile')"
            />
          </span>
          <span v-if="fieldErrors.mobile" id="sa-err-mobile" class="sa__error">{{ fieldErrors.mobile }}</span>
        </label>

        <label class="sa__field">
          <span class="sa__label">Email Address</span>
          <span class="sa__wrap">
            <Mail class="sa__icon" :size="15" aria-hidden="true" />
            <input
              v-model="form.email"
              type="email"
              class="sa__input sa__input--iconed"
              placeholder="e.g. you@domain.com"
              autocomplete="email"
              inputmode="email"
              maxlength="190"
              :aria-invalid="Boolean(fieldErrors.email)"
              :aria-describedby="fieldErrors.email ? 'sa-err-email' : undefined"
              @input="touched('email')"
            />
          </span>
          <span v-if="fieldErrors.email" id="sa-err-email" class="sa__error">{{ fieldErrors.email }}</span>
        </label>
      </div>

      <label class="sa__field">
        <span class="sa__label">Facebook Page or Instagram <span class="sa__optional">(optional)</span></span>
        <span class="sa__wrap">
          <AtSign class="sa__icon" :size="15" aria-hidden="true" />
          <input
            v-model="form.socialUrl"
            type="text"
            class="sa__input sa__input--iconed"
            placeholder="e.g. facebook.com/yourpage or @yourhandle"
            maxlength="190"
            :aria-invalid="Boolean(fieldErrors.socialUrl)"
            :aria-describedby="fieldErrors.socialUrl ? 'sa-err-socialUrl' : undefined"
            @input="touched('socialUrl')"
          />
        </span>
        <span v-if="fieldErrors.socialUrl" id="sa-err-socialUrl" class="sa__error">{{ fieldErrors.socialUrl }}</span>
      </label>

      <label class="sa__field">
        <span class="sa__label">Business Address</span>
        <span class="sa__wrap">
          <MapPin class="sa__icon" :size="15" aria-hidden="true" />
          <input
            v-model="form.address"
            type="text"
            class="sa__input sa__input--iconed"
            placeholder="e.g. Poblacion, La Trinidad, Benguet"
            autocomplete="street-address"
            maxlength="255"
            :aria-invalid="Boolean(fieldErrors.address)"
            :aria-describedby="fieldErrors.address ? 'sa-err-address' : undefined"
            @input="touched('address')"
          />
        </span>
        <span v-if="fieldErrors.address" id="sa-err-address" class="sa__error">{{ fieldErrors.address }}</span>
      </label>

      <label class="sa__field">
        <span class="sa__label">Short description of products</span>
        <textarea
          v-model="form.productsDescription"
          class="sa__input sa__textarea"
          rows="3"
          placeholder="Tell us what you sell, your bestsellers, etc."
          :aria-invalid="Boolean(fieldErrors.productsDescription)"
          :aria-describedby="fieldErrors.productsDescription ? 'sa-err-productsDescription' : 'sa-count'"
          @input="touched('productsDescription')"
        ></textarea>
        <!-- Not a maxlength on the textarea: a hard stop mid-word looks like a
             broken keyboard. The count turns red, and `validate` refuses. -->
        <span id="sa-count" class="sa__count" :class="{ 'sa__count--over': overLimit }" aria-live="polite">
          {{ remaining }}/{{ DESCRIPTION_LIMIT }}
        </span>
        <span v-if="fieldErrors.productsDescription" id="sa-err-productsDescription" class="sa__error">
          {{ fieldErrors.productsDescription }}
        </span>
      </label>

      <fieldset
        class="sa__fieldset"
        :data-error="Boolean(fieldErrors.offersDelivery)"
        :aria-describedby="fieldErrors.offersDelivery ? 'sa-err-offersDelivery' : undefined"
      >
        <legend class="sa__label">Do you already offer delivery?</legend>
        <div class="sa__choices">
          <label class="sa__choice">
            <input
              v-model="form.offersDelivery"
              type="radio"
              name="offers-delivery"
              :value="true"
              @change="touched('offersDelivery')"
            />
            <span>Yes, we offer delivery</span>
          </label>
          <label class="sa__choice">
            <input
              v-model="form.offersDelivery"
              type="radio"
              name="offers-delivery"
              :value="false"
              @change="touched('offersDelivery')"
            />
            <span>No, not yet</span>
          </label>
        </div>
        <span v-if="fieldErrors.offersDelivery" id="sa-err-offersDelivery" class="sa__error">
          {{ fieldErrors.offersDelivery }}
        </span>
      </fieldset>

      <!-- Unticking this does not withdraw the application. The campaign is a
           reason to apply, not a condition of applying — the API takes either. -->
      <label class="sa__consent">
        <input v-model="form.wantsFounding" type="checkbox" class="sa__check" />
        <span class="sa__box" aria-hidden="true"><Check :size="14" :stroke-width="3.2" /></span>
        <span>I want to be considered as one of the first 30 founding sellers</span>
      </label>

      <p v-if="formError" class="sa__alert" role="alert">{{ formError }}</p>

      <button type="submit" class="sa__submit" :disabled="saving">
        <span>{{ saving ? 'Sending your application…' : 'Apply as a Founding Seller' }}</span>
        <ArrowRight v-if="!saving" :size="18" :stroke-width="2.4" aria-hidden="true" />
      </button>

      <p class="sa__limit">
        <Users :size="14" :stroke-width="2.2" aria-hidden="true" />
        <template v-if="props.status && !props.status.open">
          All {{ props.status.limit }} founding places are taken — we're still setting up new shops
        </template>
        <template v-else-if="props.status && props.status.claimed > 0">
          {{ props.status.remaining }} of {{ props.status.limit }} founding places left in Baguio / La Trinidad
        </template>
        <template v-else>
          Limited to 30 businesses in Baguio / La Trinidad
        </template>
      </p>
    </form>
  </section>
</template>

<style scoped>
.sa {
  padding: 20px 22px 18px;
  border-radius: 20px;
  background: var(--sf-paper);
  box-shadow: 0 30px 70px -34px rgba(13, 52, 36, 0.5), 0 2px 10px rgba(13, 52, 36, 0.07);
}

.sa__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.sa__mark {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: var(--sf-forest);
  color: #fff;
}

.sa__title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--sf-ink);
}

.sa__sub {
  margin: 2px 0 0;
  font-size: 13px;
  line-height: 1.4;
  color: var(--sf-muted);
}

.sa__form {
  display: grid;
  gap: 10px;
}

.sa__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 11px;
}

.sa__field {
  display: grid;
  gap: 4px;
  /* Without this a long option in the select stretches the grid column. */
  min-width: 0;
}

.sa__label {
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--sf-ink);
}

.sa__optional {
  font-weight: 500;
  color: var(--sf-faint);
}

.sa__wrap {
  position: relative;
  display: block;
}

.sa__icon {
  position: absolute;
  left: 11px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--sf-faint);
  pointer-events: none;
}

.sa__input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 11px;
  border: 1px solid var(--sf-rule);
  border-radius: 9px;
  background: var(--sf-paper);
  color: var(--sf-ink);
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.4;
  transition: border-color 140ms, box-shadow 140ms;
}

.sa__input--iconed { padding-left: 32px; }

.sa__input::placeholder { color: var(--sf-faint); }

.sa__input:focus-visible {
  outline: none;
  border-color: var(--sf-leaf);
  box-shadow: 0 0 0 3px rgba(26, 122, 69, 0.16);
}

.sa__input[aria-invalid='true'] {
  border-color: var(--sf-clay-deep);
  box-shadow: 0 0 0 3px rgba(207, 84, 44, 0.12);
}

.sa__select {
  /* Our own chevron; the native one sits differently in every browser. */
  appearance: none;
  padding-right: 34px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8' fill='none'%3E%3Cpath d='M1 1.5 6 6.5 11 1.5' stroke='%236f665c' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  cursor: pointer;
}

/* The disabled placeholder option should read as a placeholder, not a value. */
.sa__select--empty { color: var(--sf-faint); }

.sa__textarea {
  resize: vertical;
  min-height: 70px;
}

.sa__count {
  justify-self: end;
  margin-top: -2px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--sf-faint);
  font-variant-numeric: tabular-nums;
}

.sa__count--over { color: var(--sf-clay-deep); }

.sa__error {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--sf-clay-deep);
}

.sa__fieldset {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}

.sa__fieldset > .sa__label { padding: 0; }

.sa__choices {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
}

.sa__choice {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--sf-ink);
  cursor: pointer;
}

.sa__choice input {
  width: 17px;
  height: 17px;
  margin: 0;
  accent-color: var(--sf-leaf);
  cursor: pointer;
}

.sa__consent {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 3px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--sf-ink);
  cursor: pointer;
}

/* The real checkbox stays in the layout and keeps the focus ring; `.sa__box`
   is what is painted. Hiding it with display:none would take it out of the
   tab order entirely. */
.sa__check {
  position: absolute;
  opacity: 0;
  width: 22px;
  height: 22px;
  margin: 0;
  cursor: pointer;
}

.sa__box {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border: 1.5px solid var(--sf-rule);
  border-radius: 7px;
  background: var(--sf-paper);
  color: transparent;
  transition: background-color 140ms, border-color 140ms, color 140ms;
}

.sa__check:checked + .sa__box {
  background: var(--sf-leaf);
  border-color: var(--sf-leaf);
  color: #fff;
}

.sa__check:focus-visible + .sa__box {
  outline: 2px solid var(--sf-leaf);
  outline-offset: 2px;
}

.sa__alert {
  margin: 0;
  padding: 10px 12px;
  border-radius: 9px;
  background: #fdeee8;
  color: #8f3415;
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.4;
}

.sa__submit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  width: 100%;
  margin-top: 3px;
  padding: 12px 20px;
  border: 0;
  border-radius: 11px;
  background: var(--sf-forest);
  color: #fff;
  font-family: inherit;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.01em;
  cursor: pointer;
  transition: background-color 140ms, transform 140ms;
}

.sa__submit:hover:not(:disabled) {
  background: var(--sf-forest-deep);
  transform: translateY(-1px);
}

.sa__submit:disabled {
  opacity: 0.62;
  cursor: progress;
  transform: none;
}

.sa__limit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 0;
  padding: 8px 14px;
  border-radius: 10px;
  background: var(--sf-leaf-wash);
  color: #14603a;
  font-size: 12.5px;
  font-weight: 700;
  line-height: 1.35;
  text-align: center;
}

.sa__limit svg { flex-shrink: 0; }

@media (max-width: 560px) {
  .sa { padding: 18px 16px 16px; }
  .sa__row { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .sa__submit:hover:not(:disabled) { transform: none; }
}
</style>
