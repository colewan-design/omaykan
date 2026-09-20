import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getPosRepository } from '@pos/core/services/runtime'
import {
  appPageKeys,
  defaultRoles,
  type AppPageKey,
  type AuthSession,
  type RoleDefinition,
  type UserAccount,
} from '@pos/shared/index'

const guestUserId = '__guest__'
const guestUsername = 'guest'
const guestDisplayName = 'Guest Demo'
const lockedRoleIds = new Set(['admin', 'guest'])

function clonePermissions(role: RoleDefinition): RoleDefinition {
  return {
    ...role,
    permissions: { ...role.permissions },
  }
}

function withAllPermissionKeys(role: RoleDefinition): RoleDefinition {
  return {
    ...role,
    permissions: Object.fromEntries(
      appPageKeys.map((page) => [page, Boolean(role.permissions[page])]),
    ) as Record<AppPageKey, boolean>,
  }
}

function applyBuiltInRoleUpgrades(role: RoleDefinition): RoleDefinition {
  if (role.id !== 'cashier') {
    return role
  }

  // Seller-style cashier accounts need the settings page for local
  // preferences like appearance and business profile.
  return {
    ...role,
    permissions: {
      ...role.permissions,
      settings: true,
    },
  }
}

function mergeRoles(savedRoles: RoleDefinition[]) {
  const roleMap = new Map(
    savedRoles.map((role) => {
      const normalizedRole = withAllPermissionKeys(clonePermissions(role))
      return [role.id, applyBuiltInRoleUpgrades(normalizedRole)]
    }),
  )

  for (const role of defaultRoles) {
    if (lockedRoleIds.has(role.id)) {
      roleMap.set(role.id, applyBuiltInRoleUpgrades(clonePermissions(role)))
      continue
    }

    if (!roleMap.has(role.id)) {
      roleMap.set(role.id, applyBuiltInRoleUpgrades(clonePermissions(role)))
    }
  }

  return Array.from(roleMap.values())
}

export const useAuthStore = defineStore('auth', () => {
  const repository = getPosRepository()

  const users = ref<UserAccount[]>([])
  const roles = ref<RoleDefinition[]>(defaultRoles.map(clonePermissions))
  const session = ref<AuthSession | null>(null)
  const isReady = ref(false)
  const authError = ref('')
  /**
   * Whether sign-in can reach the backend. Read once at [initialize] and used
   * to decide whether to offer Google at all: on a local-only register the
   * button would load Google's script, take a real sign-in off the person, and
   * then have nowhere to send it.
   */
  const remoteAuthReady = ref(false)

  const guestAccount = computed<UserAccount | null>(() => {
    const guestRole = roles.value.find((role) => role.id === 'guest')
    if (!guestRole) {
      return null
    }

    return {
      id: guestUserId,
      fullName: guestDisplayName,
      username: guestUsername,
      passwordHash: '',
      roleId: guestRole.id,
      createdAt: '',
    }
  })

  const currentUser = computed(() => {
    if (session.value?.userId === guestUserId) {
      return guestAccount.value
    }

    return users.value.find((user) => user.id === session.value?.userId) ?? null
  })

  const currentRole = computed(() =>
    roles.value.find((role) => role.id === currentUser.value?.roleId) ?? null,
  )

  const hasUsers = computed(() => users.value.length > 0)
  // True owner — this stays admin-only and can't be delegated. Gates
  // Integrations/Diagnostics and any action that touches the admin role.
  const isOwner = computed(() => currentRole.value?.id === 'admin')
  // Staff management (add employees, assign roles, edit non-locked roles) —
  // delegable per-role via canManageStaff, so a Manager can hold it without
  // being an owner.
  const canManageAccess = computed(() => isOwner.value || currentRole.value?.canManageStaff === true)
  const guestRole = computed(() => roles.value.find((role) => role.id === 'guest') ?? null)
  const canUseGuestAccess = computed(() =>
    appPageKeys.some((page) => Boolean(guestRole.value?.permissions[page])),
  )

  const accessiblePages = computed(() =>
    appPageKeys.filter((page) => currentRole.value?.permissions[page]),
  )

  const firstAccessiblePage = computed<AppPageKey | null>(() => {
    if (currentRole.value?.id === 'cashier' && accessiblePages.value.includes('register')) {
      return 'register'
    }

    return accessiblePages.value[0] ?? null
  })

  async function initialize() {
    if (isReady.value) {
      return
    }

    const [savedUsers, savedRoles, savedSession, remoteAuth] = await Promise.all([
      repository.loadUsers(),
      repository.loadRoles(),
      repository.loadSession(),
      repository.remoteAuthAvailable(),
    ])

    remoteAuthReady.value = remoteAuth
    users.value = savedUsers
    roles.value = mergeRoles(savedRoles.length > 0 ? savedRoles : defaultRoles)
    session.value = savedSession

    const persistedRoles = JSON.stringify(savedRoles)
    const normalizedRoles = JSON.stringify(roles.value)
    if (savedRoles.length > 0 && persistedRoles !== normalizedRoles) {
      await repository.saveRoles(roles.value)
    }

    if (savedSession && savedSession.userId !== guestUserId && !savedUsers.some((user) => user.id === savedSession.userId)) {
      session.value = null
      await repository.saveSession(null)
    }

    if (session.value?.userId === guestUserId && !canUseGuestAccess.value) {
      session.value = null
      await repository.saveSession(null)
    }

    isReady.value = true
  }

  function clearAuthError() {
    authError.value = ''
  }

  function canAccess(page: AppPageKey) {
    return Boolean(currentRole.value?.permissions[page])
  }

  async function login(username: string, password: string) {
    clearAuthError()
    let result: Awaited<ReturnType<typeof repository.loginUser>>

    try {
      result = await repository.loginUser(username, password)
    } catch (err) {
      authError.value = err instanceof Error ? err.message : 'Unable to sign you in.'
      return false
    }

    if (!result) {
      authError.value = 'Incorrect username or password.'
      return false
    }

    users.value = await repository.loadUsers()
    session.value = result.session
    return true
  }

  /**
   * Sign in with a Google ID token.
   *
   * Unlike [login] there is no local fallback and no account created on the
   * way in: the token only means anything to the backend, and the backend only
   * returns a session for an account that already has a membership at this
   * shop. That makes every refusal it gives worth repeating word for word —
   * "ask an admin to add you in Staff" is an instruction the person can act on,
   * and flattening it to "incorrect username or password" would strand them.
   */
  async function loginWithGoogle(credential: string) {
    clearAuthError()
    let result: Awaited<ReturnType<typeof repository.loginUserWithGoogle>>

    try {
      result = await repository.loginUserWithGoogle(credential)
    } catch (err) {
      authError.value = err instanceof Error ? err.message : 'Unable to sign you in.'
      return false
    }

    if (!result) {
      authError.value = 'Google sign-in needs online sync turned on for this register.'
      return false
    }

    users.value = await repository.loadUsers()
    session.value = result.session
    return true
  }

  async function register(input: {
    fullName: string
    username: string
    password: string
  }) {
    clearAuthError()

    const fullName = input.fullName.trim()
    const username = input.username.trim().toLowerCase()
    const password = input.password.trim()

    if (!fullName || !username || !password) {
      authError.value = 'Complete all registration fields.'
      return false
    }

    if (users.value.some((user) => user.username === username)) {
      authError.value = 'That username is already in use.'
      return false
    }

    // Thrown, not returned null, when the till is online: there is no
    // self-registration route behind a shop any more, and the refusal names
    // the way in that does exist. Repeated word for word for the same reason
    // [loginWithGoogle] repeats its own — "ask an admin to add you in Staff"
    // is an instruction, and "unable to create that account" is a dead end.
    let result: Awaited<ReturnType<typeof repository.registerUser>>

    try {
      result = await repository.registerUser({ fullName, username, password })
    } catch (err) {
      authError.value = err instanceof Error ? err.message : 'Unable to create that account.'
      return false
    }

    if (!result) {
      authError.value = 'Unable to create that account.'
      return false
    }

    users.value = await repository.loadUsers()
    session.value = result.session
    return true
  }

  async function loginAsGuest() {
    clearAuthError()

    if (!roles.value.some((role) => role.id === 'guest') || !canUseGuestAccess.value) {
      authError.value = 'Guest access is disabled for this app.'
      return false
    }

    session.value = {
      userId: guestUserId,
      signedInAt: new Date().toISOString(),
    }
    await repository.saveSession(session.value)
    return true
  }

  async function logout() {
    session.value = null
    await repository.saveSession(null)
  }

  async function createStaffAccount(input: {
    fullName: string
    username: string
    password: string
    roleId: string
  }) {
    if (!canManageAccess.value) {
      return false
    }

    clearAuthError()

    const fullName = input.fullName.trim()
    const username = input.username.trim().toLowerCase()
    const password = input.password.trim()
    const roleId = input.roleId.trim()

    if (!fullName || !username || !password || !roleId) {
      authError.value = 'Complete all fields to add an employee.'
      return false
    }

    if (users.value.some((user) => user.username === username)) {
      authError.value = 'That username is already in use.'
      return false
    }

    try {
      await repository.createStaffAccount({ fullName, username, password, roleId })
    } catch (err) {
      authError.value = err instanceof Error ? err.message : 'Unable to create that account.'
      return false
    }

    users.value = await repository.loadUsers()
    return true
  }

  async function updateUserRole(userId: string, roleId: string) {
    if (!canManageAccess.value) {
      return
    }

    // A delegated (non-owner) manager can reassign any non-admin role, but
    // granting/revoking the admin role itself is owner-only — otherwise a
    // delegated manager could promote themselves (or anyone) to full owner.
    const target = users.value.find((user) => user.id === userId)
    const touchesAdminRole = roleId === 'admin' || target?.roleId === 'admin'
    if (touchesAdminRole && !isOwner.value) {
      return
    }

    await repository.updateUserRole(userId, roleId)
    users.value = await repository.loadUsers()
  }

  // Whether a role can itself manage staff is owner-only to grant — a
  // delegated manager can manage staff, but can't hand that same power to
  // someone else (only the real owner decides who else becomes a manager).
  async function setRoleManageStaff(roleId: string, allowed: boolean) {
    if (!isOwner.value || lockedRoleIds.has(roleId)) {
      return
    }

    roles.value = roles.value.map((role) =>
      role.id === roleId ? { ...role, canManageStaff: allowed } : role,
    )
    await repository.saveRoles(roles.value)
  }

  /**
   * How much this role may take off a sale on its own, as a percentage.
   *
   * Owner-only, like the staff power above: a discount limit is authority over
   * the shop's money. Admin is not editable — it is always 100 — and the
   * server keeps the same number (roles.max_discount_percent) and checks every
   * synced sale against it.
   */
  async function setRoleDiscountLimit(roleId: string, percent: number) {
    if (!isOwner.value || roleId === 'admin') {
      return
    }

    const limit = Math.min(100, Math.max(0, Math.round(Number.isFinite(percent) ? percent : 0)))
    roles.value = roles.value.map((role) =>
      role.id === roleId ? { ...role, maxDiscountPercent: limit } : role,
    )
    await repository.saveRoles(roles.value)
  }

  async function createRole(name: string) {
    if (!canManageAccess.value) {
      return false
    }

    const trimmedName = name.trim()
    if (!trimmedName) {
      authError.value = 'Role name is required.'
      return false
    }

    const id = trimmedName.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')
    if (!id || roles.value.some((role) => role.id === id)) {
      authError.value = 'Choose a unique role name.'
      return false
    }

    const role: RoleDefinition = {
      id,
      name: trimmedName,
      permissions: Object.fromEntries(
        appPageKeys.map((page) => [page, false]),
      ) as Record<AppPageKey, boolean>,
    }

    roles.value = [...roles.value, role]
    await repository.saveRoles(roles.value)
    return true
  }

  async function renameRole(roleId: string, name: string) {
    if (!canManageAccess.value || lockedRoleIds.has(roleId)) {
      return
    }

    const trimmedName = name.trim()
    if (!trimmedName) {
      return
    }

    roles.value = roles.value.map((role) =>
      role.id === roleId ? { ...role, name: trimmedName } : role,
    )
    await repository.saveRoles(roles.value)
  }

  async function setRolePermission(roleId: string, page: AppPageKey, allowed: boolean) {
    if (!canManageAccess.value || lockedRoleIds.has(roleId)) {
      return
    }

    roles.value = roles.value.map((role) =>
      role.id === roleId
        ? {
            ...role,
            permissions: {
              ...role.permissions,
              [page]: allowed,
            },
          }
        : role,
    )
    await repository.saveRoles(roles.value)
  }

  async function deleteRole(roleId: string) {
    if (!canManageAccess.value || roles.value.length <= 1 || roleId === 'admin' || roleId === 'guest') {
      return false
    }

    if (users.value.some((user) => user.roleId === roleId)) {
      authError.value = 'Reassign users before deleting that role.'
      return false
    }

    roles.value = roles.value.filter((role) => role.id !== roleId)
    await repository.saveRoles(roles.value)
    return true
  }

  return {
    users,
    roles,
    session,
    isReady,
    authError,
    remoteAuthReady,
    currentUser,
    currentRole,
    hasUsers,
    isOwner,
    canManageAccess,
    canUseGuestAccess,
    accessiblePages,
    firstAccessiblePage,
    initialize,
    clearAuthError,
    canAccess,
    login,
    loginWithGoogle,
    register,
    loginAsGuest,
    logout,
    createStaffAccount,
    updateUserRole,
    createRole,
    renameRole,
    setRolePermission,
    setRoleManageStaff,
    setRoleDiscountLimit,
    deleteRole,
  }
})
