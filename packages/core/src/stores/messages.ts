import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getPosRepository } from '@pos/core/services/runtime'

/**
 * How many customer messages the shop hasn't opened.
 *
 * A store rather than state inside AppNav because AppNav is mounted twice —
 * the sidebar and the mobile drawer — and both badges have to agree, and
 * because MessagesPage needs to clear the count the moment it reads a thread
 * rather than leaving the badge up until the next poll.
 *
 * Polled. The store's Reverb channel carries order events, and a customer's
 * question is answered within minutes, not seconds; a poll that pauses while
 * the tab is hidden is enough, and costs one indexed SUM.
 */
const POLL_MS = 30_000

export const useMessagesStore = defineStore('messages', () => {
  const unread = ref(0)

  let timer: ReturnType<typeof setInterval> | null = null
  let subscribers = 0

  async function refresh() {
    try {
      unread.value = await getPosRepository().loadUnreadMessageCount()
    } catch {
      // Keep the last number rather than flashing the badge away.
    }
  }

  /** Start polling while at least one caller wants it. Returns the unsubscribe. */
  function subscribe(): () => void {
    subscribers += 1
    if (timer === null) {
      void refresh()
      timer = setInterval(() => {
        if (document.visibilityState !== 'hidden') void refresh()
      }, POLL_MS)
    }

    let done = false
    return () => {
      if (done) return
      done = true
      subscribers -= 1
      if (subscribers <= 0 && timer !== null) {
        clearInterval(timer)
        timer = null
        subscribers = 0
      }
    }
  }

  return { unread, refresh, subscribe }
})
