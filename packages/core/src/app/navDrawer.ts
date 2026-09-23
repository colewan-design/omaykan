import type { InjectionKey } from 'vue'

/**
 * Opens the shell's mobile navigation drawer. AppShell provides it; pages that
 * replace the workspace topbar with a header of their own (Register) inject it
 * so their own menu button still reaches the one shared nav.
 */
export const OPEN_APP_NAV: InjectionKey<() => void> = Symbol('openAppNav')
