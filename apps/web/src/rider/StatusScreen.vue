<script setup lang="ts">
import { computed, ref } from 'vue'
import { messageFor, useRiderSession } from '@pos/web/rider/rider'
import type { RiderProfile } from '@pos/web/rider/api'

/*
 * What a rider sees when they are not cleared to work: waiting on review,
 * turned down, or suspended.
 *
 * All three in one screen because they are one screen — a headline, the
 * reviewer's note, and the details on file — and splitting them into three
 * components would triple the places the details have to be kept in step.
 *
 * The details are shown back deliberately. A rider waiting on a decision has
 * no other way to check that the plate they typed at 11pm is the plate on the
 * bike, and a mistyped one is the most likely reason a legitimate application
 * gets rejected.
 */

const props = defineProps<{ rider: RiderProfile }>()

const session = useRiderSession()

const refreshing = ref(false)
const refreshError = ref('')
const refreshedAt = ref('')

const copy = computed(() => {
  switch (props.rider.status) {
    case 'rejected':
      return {
        pill: 'Not approved',
        title: 'We could not approve this application',
        body: 'A reviewer looked at your documents and turned the application down. If you think something was misread — a blurry photo, a plate that has since changed — reply to the note below and we will take another look.',
      }
    case 'suspended':
      return {
        pill: 'Suspended',
        title: 'Your rider account is on hold',
        body: 'You cannot take new jobs while the account is suspended. The reason is below; get in touch once it is sorted and we can lift it.',
      }
    default:
      return {
        pill: 'Under review',
        title: 'Your application is with us',
        body: 'Someone is checking your licence and plate against what you typed. This is usually done within a day. You do not need to keep this page open — sign back in any time to check.',
      }
  }
})

function formatDate(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  )
}

async function refresh() {
  refreshing.value = true
  refreshError.value = ''

  try {
    await session.refresh()
    refreshedAt.value = new Intl.DateTimeFormat('en-PH', { timeStyle: 'short' }).format(new Date())
  } catch (error) {
    refreshError.value = messageFor(error, 'Could not check your status just now.')
  } finally {
    refreshing.value = false
  }
}
</script>

<template>
  <div>
    <span class="rdr-pill" :class="`rdr-pill--${rider.status}`">{{ copy.pill }}</span>

    <h1 class="rdr-title" style="margin-top: 14px">{{ copy.title }}</h1>
    <p class="rdr-sub">{{ copy.body }}</p>

    <!-- The reviewer's own words, when there are any. This is the whole reason
         a rejected rider is allowed to sign in at all. -->
    <div v-if="rider.reviewNote" class="rdr-card" style="margin-bottom: 12px">
      <p class="rdr-field__label" style="margin-bottom: 8px">Note from the reviewer</p>
      <p style="margin: 0; font-size: 15px; line-height: 1.6">{{ rider.reviewNote }}</p>
      <p v-if="rider.reviewedAt" class="rdr-field__hint">Reviewed {{ formatDate(rider.reviewedAt) }}</p>
    </div>

    <p class="rdr-section-title">What we have on file</p>

    <div class="rdr-card">
      <p class="rdr-job__line" style="margin: 0; padding: 0; border: none">
        <strong>{{ rider.name }}</strong><br>
        {{ rider.phone }}<br>
        {{ rider.email }}
      </p>

      <p class="rdr-job__line">
        Licence <strong>{{ rider.licenseNumber }}</strong><br>
        Plate <strong>{{ rider.plateNumber }}</strong>
      </p>

      <p class="rdr-job__line">
        Applied {{ formatDate(rider.createdAt) }}<br>
        <span class="rdr-field__hint" style="margin: 0">
          Your licence and plate photos are held privately for this check and are never shown to a
          shop or a customer.
        </span>
      </p>
    </div>

    <p v-if="refreshError" class="rdr-flash rdr-flash--error" style="margin: 18px 0 0">
      {{ refreshError }}
    </p>

    <div v-if="rider.status === 'pending'" class="rdr-actions">
      <button class="rdr-btn rdr-btn--ghost" type="button" :disabled="refreshing" @click="refresh">
        {{ refreshing ? 'Checking…' : 'Check again' }}
      </button>
      <span v-if="refreshedAt" class="rdr-field__hint" style="margin: 0">
        Last checked {{ refreshedAt }} — still under review.
      </span>
    </div>
  </div>
</template>
