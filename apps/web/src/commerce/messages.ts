import { computed, effectScope, ref, watch } from 'vue'
import { fetchUnreadMessageCount } from '@pos/web/commerce/api'
import { useCustomerAccount } from '@pos/web/commerce/customer'
import { MESSAGING_ENABLED } from '@pos/web/commerce/features'

/*
 * How many shop replies the signed-in shopper hasn't read.
 *
 * Module-level, like the account and the cart: the header badge and the
 * portal's menu row have to show the same number, and opening a thread in the
 * portal has to clear the header's badge without a reload.
 *
 * Polled rather than pushed. The customer has no private broadcast channel to
 * authenticate against, and a reply is a thing someone reads within the
 * minute, not the second. The poll pauses while the tab is hidden.
 */

const POLL_MS = 60_000

const unread = ref(0)
let started = false
let timer: ReturnType<typeof setInterval> | null = null

async function refresh(): Promise<void> {
  try {
    unread.value = (await fetchUnreadMessageCount()).unread
  } catch {
    // Offline or signed out underneath us — keep the last number rather than
    // flashing the badge away.
  }
}

function start() {
  if (started) return
  started = true
  // Hidden in this build: no poll, so the badge stays at nought.
  if (!MESSAGING_ENABLED) return

  // Detached: the first caller may be a component that later unmounts, and
  // the badge has to keep counting after it does.
  effectScope(true).run(() => {
    const account = useCustomerAccount()

    watch(
      account.signedIn,
      (signedIn) => {
        if (timer !== null) {
          clearInterval(timer)
          timer = null
        }
        if (!signedIn) {
          unread.value = 0
          return
        }
        void refresh()
        timer = setInterval(() => {
          if (document.visibilityState !== 'hidden') void refresh()
        }, POLL_MS)
      },
      { immediate: true },
    )
  })
}

export function useUnreadMessages() {
  start()

  return {
    unread: computed(() => unread.value),
    refresh,
  }
}
