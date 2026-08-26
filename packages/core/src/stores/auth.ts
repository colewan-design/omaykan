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

    const [savedUsers, savedRoles, savedSession] = await Promise.all([
      repository.loadUsers(),
      repository.loadRoles(),
      repository.loadSession(),
    ])

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
   * Returns what happened rather than a bare boolean: an account can now be
   * created successfully and still not be signed in, because the server wants
   * the email address verified first. The caller has to tell those apart -
   * routing into the app on a null session lands on a screen every request
   * fails from.
   */
  async function register(input: {
    fullName: string
    username: string
    email: string
    password: string
  }): Promise<{ ok: false } | { ok: true; verificationRequired: boolean; message?: string }> {
    clearAuthError()

    const fullName = input.fullName.trim()
    const username = input.username.trim().toLowerCase()
    const email = input.email.trim().toLowerCase()
    const password = input.password.trim()

    if (!fullName || !username || !email || !password) {
      authError.value = 'Complete all registration fields.'
      return { ok: false }
    }

    // Only the obvious mistake, to spare a round trip. The server validates the
    // address properly and its message wins when the two disagree.
    if (!email.includes('@')) {
      authError.value = "That doesn't look like an email address."
      return { ok: false }
    }

    if (users.value.some((user) => user.username === username)) {
      authError.value = 'That username is already in use.'
      return { ok: false }
    }

    const result = await repository.registerUser({ fullName, username, email, password })
    if (!result) {
      authError.value = 'Unable to create that account. That username or email may already be taken.'
      return { ok: false }
    }

    users.value = await repository.loadUsers()

    // Left alone when there is no session: signing in is the next step, and it
    // is gated on the verification link.
    if (result.session) {
      session.value = result.session
    }

    return {
      ok: true,
      verificationRequired: result.verificationRequired,
      message: result.message,
    }
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
    register,
    loginAsGuest,
    logout,
    createStaffAccount,
    updateUserRole,
    createRole,
    renameRole,
    setRolePermission,
    setRoleManageStaff,
    deleteRole,
  }
})
