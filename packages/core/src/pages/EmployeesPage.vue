<script setup lang="ts">
import {
  ArrowRight,
  BadgeCheck,
  BarChart3,
  Boxes,
  ChevronDown,
  Copy,
  Database,
  LockKeyhole,
  Package,
  Plus,
  Search,
  Settings,
  Shield,
  ShoppingCart,
  Trash2,
  UserCircle2,
  UserRoundPlus,
  Users,
} from '@lucide/vue'
import { computed, onMounted, ref, watch, type Component } from 'vue'
import AddEmployeeSheet from '@pos/core/components/AddEmployeeSheet.vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import ToggleSwitch from '@pos/core/components/ToggleSwitch.vue'
import { useAuthStore } from '@pos/core/stores/auth'
import { usePosStore } from '@pos/core/stores/pos'
import { appPageKeys, appPageLabel, maxDiscountPercentFor, type AppPageKey } from '@pos/shared/index'

const auth = useAuthStore()
const store = usePosStore()
const showAddEmployee = ref(false)
const showRoleCreate = ref(false)
const searchQuery = ref('')
const roleFilter = ref<'all' | string>('all')
const activeRoleId = ref('')
const newRoleName = ref('')

const basePermissionGroups: Array<{
  id: string
  name: string
  copy: string
  icon: Component
  pages: AppPageKey[]
}> = [
  {
    id: 'management',
    name: 'Management',
    copy: 'People and team management features.',
    icon: Users,
    pages: ['employees'],
  },
  {
    id: 'sales',
    name: 'Sales & orders',
    copy: 'Sales, orders, tables, and daily performance.',
    icon: ShoppingCart,
    pages: ['dashboard', 'sales', 'orders'],
  },
  {
    id: 'catalog',
    name: 'Catalog',
    copy: 'Products, customers, suppliers, and stock.',
    icon: Package,
    pages: ['products', 'suppliers', 'customers', 'inventory'],
  },
  {
    id: 'reports',
    name: 'Reports & integrations',
    copy: 'Reporting and connected business tools.',
    icon: BarChart3,
    pages: ['reports', 'integrations'],
  },
  {
    id: 'system',
    name: 'System',
    copy: 'Register and system configuration.',
    icon: Settings,
    pages: ['register', 'settings', 'diagnostics'],
  },
]

const permissionGroups = computed(() => basePermissionGroups.map((group) =>
  group.id === 'sales' && store.settings.businessMode === 'restaurant'
    ? { ...group, pages: [...group.pages, 'tables'] as AppPageKey[] }
    : group,
))
const visiblePageKeys = computed(() => permissionGroups.value.flatMap((group) => group.pages))
const expandedGroups = ref(basePermissionGroups.map((group) => group.id))

onMounted(() => {
  if (!auth.isReady) void auth.initialize()
})

const activeRole = computed(() => auth.roles.find((role) => role.id === activeRoleId.value) ?? null)
const assignableRoleOptions = computed(() =>
  auth.roles
    .filter((role) => role.id !== 'guest')
    .map((role) => ({ value: role.id, label: role.name })),
)
const isLockedSystemRole = computed(() => activeRole.value?.id === 'admin' || activeRole.value?.id === 'guest')

const filteredUsers = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase()
  return auth.users.filter((user) => {
    if (roleFilter.value !== 'all' && user.roleId !== roleFilter.value) return false
    if (!needle) return true
    const roleName = auth.roles.find((role) => role.id === user.roleId)?.name ?? user.roleId
    return [user.fullName, user.username, roleName].some((value) => value.toLowerCase().includes(needle))
  })
})

const totalPermissionGrants = computed(() =>
  auth.roles.reduce(
    (count, role) => count + appPageKeys.filter((page) => role.permissions[page]).length,
    0,
  ),
)
const activeRoleGrantCount = computed(() =>
  activeRole.value
    ? visiblePageKeys.value.filter((page) => activeRole.value?.permissions[page]).length + (activeRole.value.canManageStaff ? 1 : 0)
    : 0,
)
const staffManagerCount = computed(() => auth.roles.filter((role) => role.canManageStaff || role.id === 'admin').length)
const coveragePercent = computed(() => {
  if (!auth.roles.length || !appPageKeys.length) return 0
  return Math.round((totalPermissionGrants.value / (auth.roles.length * appPageKeys.length)) * 100)
})

const canDeleteActiveRole = computed(() =>
  !!activeRole.value
  && activeRole.value.id !== 'admin'
  && activeRole.value.id !== 'guest'
  && auth.roles.length > 1
  && !auth.users.some((user) => user.roleId === activeRole.value?.id),
)

watch(
  () => auth.roles,
  (roles) => {
    if (!roles.length) {
      activeRoleId.value = ''
      roleFilter.value = 'all'
      return
    }
    if (!roles.some((role) => role.id === activeRoleId.value)) activeRoleId.value = roles[0].id
    if (roleFilter.value !== 'all' && !roles.some((role) => role.id === roleFilter.value)) roleFilter.value = 'all'
  },
  { immediate: true, deep: true },
)

function openAddEmployee() {
  auth.clearAuthError()
  showAddEmployee.value = true
}

function roleDescription(roleId: string) {
  switch (roleId) {
    case 'admin': return 'Full access to all features and settings. Intended for store owners.'
    case 'manager': return 'Runs day-to-day operations and can coordinate the team.'
    case 'cashier': return 'Handles the register, sales, and customer orders.'
    case 'guest': return 'Starts with no access until an owner enables specific areas.'
    default: return 'Custom access configured for this store.'
  }
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('') || 'TM'
}

function avatarTone(id: string) {
  let hash = 0
  for (const char of id) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return hash % 5
}

function formatMemberSince(value: string) {
  return new Intl.DateTimeFormat('en-PH', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value))
}

function assignUserRole(userId: string, roleId: string) {
  if (auth.canManageAccess) void auth.updateUserRole(userId, roleId)
}

function renameActiveRole(name: string) {
  if (!activeRole.value || isLockedSystemRole.value) return
  void auth.renameRole(activeRole.value.id, name)
}

function updateRolePermission(page: AppPageKey, allowed: boolean) {
  if (!activeRole.value || isLockedSystemRole.value) return
  void auth.setRolePermission(activeRole.value.id, page, allowed)
}

function updateRoleDiscountLimit(event: Event) {
  if (!activeRole.value || activeRole.value.id === 'admin') return
  void auth.setRoleDiscountLimit(activeRole.value.id, Number((event.target as HTMLInputElement).value))
}

function updateRoleManageStaff(allowed: boolean) {
  if (!activeRole.value || isLockedSystemRole.value) return
  void auth.setRoleManageStaff(activeRole.value.id, allowed)
}

function addRole() {
  const nextName = newRoleName.value.trim()
  if (!nextName) return
  void auth.createRole(nextName).then((created) => {
    if (!created) return
    activeRoleId.value = nextName.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')
    newRoleName.value = ''
    showRoleCreate.value = false
  })
}

async function duplicateActiveRole() {
  const source = activeRole.value
  if (!source || !auth.canManageAccess) return

  const base = `${source.name} copy`
  let name = base
  let suffix = 2
  while (auth.roles.some((role) => role.name.toLowerCase() === name.toLowerCase())) name = `${base} ${suffix++}`

  const created = await auth.createRole(name)
  if (!created) return

  const id = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')
  for (const page of appPageKeys) {
    if (source.permissions[page]) await auth.setRolePermission(id, page, true)
  }
  if (auth.isOwner && source.canManageStaff) await auth.setRoleManageStaff(id, true)
  if (auth.isOwner) await auth.setRoleDiscountLimit(id, maxDiscountPercentFor(source))
  activeRoleId.value = id
}

function removeActiveRole() {
  if (activeRole.value) void auth.deleteRole(activeRole.value.id)
}

function toggleGroup(id: string) {
  expandedGroups.value = expandedGroups.value.includes(id)
    ? expandedGroups.value.filter((groupId) => groupId !== id)
    : [...expandedGroups.value, id]
}

function groupEnabled(group: (typeof basePermissionGroups)[number]) {
  if (!activeRole.value) return 0
  return group.pages.filter((page) => activeRole.value?.permissions[page]).length
    + (group.id === 'management' && activeRole.value.canManageStaff ? 1 : 0)
}

function groupTotal(group: (typeof basePermissionGroups)[number]) {
  return group.pages.length + (group.id === 'management' ? 1 : 0)
}
</script>

<template>
  <div class="emp-page">
    <header class="emp-header">
      <div>
        <h1 class="emp-title">Employees</h1>
        <p class="emp-copy">Staff access and role control for this register.</p>
      </div>
      <div class="emp-badge" :class="{ 'emp-badge--locked': !auth.canManageAccess }">
        <Shield :size="16" />
        <span>{{ auth.canManageAccess ? 'Admin controls enabled' : 'View-only access' }}</span>
      </div>
    </header>

    <section class="emp-metrics" aria-label="Team overview">
      <article class="emp-metric surface-panel">
        <span class="emp-metric__icon emp-metric__icon--green"><Users :size="22" /></span>
        <div><p>Team members</p><strong>{{ auth.users.length }}</strong><small>Accounts on this store</small></div>
      </article>
      <article class="emp-metric surface-panel">
        <span class="emp-metric__icon emp-metric__icon--green"><Database :size="22" /></span>
        <div><p>Roles configured</p><strong>{{ auth.roles.length }}</strong><small>{{ auth.roles.map((role) => role.name).join(', ') }}</small></div>
      </article>
      <article class="emp-metric surface-panel">
        <span class="emp-metric__icon emp-metric__icon--purple"><LockKeyhole :size="21" /></span>
        <div><p>Permission grants</p><strong>{{ totalPermissionGrants }}</strong><small>{{ coveragePercent }}% of available access</small></div>
      </article>
      <article class="emp-metric surface-panel">
        <span class="emp-metric__icon emp-metric__icon--green"><UserCircle2 :size="22" /></span>
        <div><p>Staff managers</p><strong>{{ staffManagerCount }} / {{ auth.roles.length }}</strong><small>Roles allowed to manage staff</small></div>
      </article>
    </section>

    <section class="emp-layout">
      <div class="emp-left">
        <article class="emp-panel surface-panel emp-directory">
          <div class="emp-panel__head">
            <span class="emp-panel__icon"><UserRoundPlus :size="20" /></span>
            <div class="emp-panel__heading">
              <h2>Team directory</h2>
              <p>Manage staff accounts, roles, and access to this register.</p>
            </div>
            <button v-if="auth.canManageAccess" class="emp-primary" type="button" @click="openAddEmployee">
              <Plus :size="17" /><span>Add employee</span>
            </button>
          </div>

          <div class="emp-toolbar">
            <label class="emp-search">
              <Search :size="16" aria-hidden="true" />
              <input v-model="searchQuery" type="search" placeholder="Search name, username, or role…" aria-label="Search employees" />
            </label>
            <label class="emp-filter">
              <span class="sr-only">Filter by role</span>
              <select v-model="roleFilter" aria-label="Filter by role">
                <option value="all">All roles</option>
                <option v-for="role in auth.roles" :key="role.id" :value="role.id">{{ role.name }}</option>
              </select>
              <ChevronDown :size="15" aria-hidden="true" />
            </label>
          </div>

          <div class="emp-table" role="table" aria-label="Team members">
            <div class="emp-table__head" role="row">
              <span role="columnheader">Employee</span>
              <span role="columnheader">Role</span>
              <span role="columnheader">Status</span>
              <span role="columnheader">Member since</span>
            </div>
            <div v-if="filteredUsers.length" class="emp-table__body">
              <div v-for="user in filteredUsers" :key="user.id" class="emp-row" role="row" data-employee-row>
                <div class="emp-person" role="cell">
                  <span class="emp-avatar" :class="`emp-avatar--${avatarTone(user.id)}`">{{ initials(user.fullName) }}</span>
                  <span class="emp-person__copy">
                    <span><strong>{{ user.fullName }}</strong><em v-if="auth.currentUser?.id === user.id">You</em></span>
                    <small>@{{ user.username }}</small>
                  </span>
                </div>
                <div role="cell" data-label="Role">
                  <AutocompleteSelect
                    class="emp-row__select"
                    :model-value="user.roleId"
                    label="Assign role"
                    :disabled="!auth.canManageAccess"
                    :options="assignableRoleOptions"
                    @update:model-value="assignUserRole(user.id, $event)"
                  />
                </div>
                <div role="cell" data-label="Status">
                  <span class="emp-status" :class="{ 'emp-status--online': auth.currentUser?.id === user.id }">
                    <i />{{ auth.currentUser?.id === user.id ? 'Online' : 'Enabled' }}
                  </span>
                </div>
                <div class="emp-joined" role="cell" data-label="Member since">{{ formatMemberSince(user.createdAt) }}</div>
              </div>
            </div>
            <div v-else class="emp-empty">No team members match the current filters.</div>
          </div>

          <footer class="emp-directory__footer">
            <span>Showing {{ filteredUsers.length }} of {{ auth.users.length }} employees</span>
            <span v-if="searchQuery || roleFilter !== 'all'" class="emp-filtered">Filters applied</span>
          </footer>
        </article>

        <article class="emp-panel surface-panel emp-insights">
          <div class="emp-panel__head emp-panel__head--compact">
            <span class="emp-panel__icon"><Boxes :size="20" /></span>
            <div class="emp-panel__heading"><h2>Staffing insights</h2><p>Quick overview of account coverage and access.</p></div>
          </div>
          <div class="emp-insights__grid">
            <div><span class="emp-insight__icon emp-insight__icon--blue"><BadgeCheck :size="18" /></span><strong>{{ auth.users.length }} active account{{ auth.users.length === 1 ? '' : 's' }}</strong><small>Ready to sign in</small><ArrowRight :size="16" /></div>
            <div><span class="emp-insight__icon"><Database :size="18" /></span><strong>{{ auth.roles.length }} role{{ auth.roles.length === 1 ? '' : 's' }} configured</strong><small>Reusable access profiles</small><ArrowRight :size="16" /></div>
            <div><span class="emp-insight__icon"><Shield :size="18" /></span><strong>{{ coveragePercent }}% permission coverage</strong><small>Across every role</small><ArrowRight :size="16" /></div>
          </div>
        </article>
      </div>

      <article class="emp-panel surface-panel emp-roles">
        <div class="emp-panel__head emp-panel__head--role">
          <span class="emp-panel__icon emp-panel__icon--purple"><LockKeyhole :size="20" /></span>
          <div class="emp-panel__heading"><h2>Role permissions</h2><p>Configure what this role can access and manage.</p></div>
          <div class="emp-access-count"><BadgeCheck :size="18" /><span><strong>{{ activeRoleGrantCount }} of {{ visiblePageKeys.length + 1 }} enabled</strong><small>{{ isLockedSystemRole ? 'System role' : 'Custom role' }}</small></span></div>
        </div>

        <div class="emp-role-tabs" role="tablist" aria-label="Roles">
          <button
            v-for="role in auth.roles"
            :key="role.id"
            class="emp-role-tab"
            :class="{ 'emp-role-tab--active': activeRoleId === role.id }"
            type="button"
            role="tab"
            data-role-tab
            :aria-selected="activeRoleId === role.id"
            @click="activeRoleId = role.id"
          >{{ role.name }}</button>
          <button v-if="auth.canManageAccess" class="emp-role-tab emp-role-tab--new" type="button" @click="showRoleCreate = !showRoleCreate"><Plus :size="14" /> New role</button>
        </div>

        <div v-if="showRoleCreate && auth.canManageAccess" class="emp-role-create">
          <input v-model="newRoleName" class="emp-input" type="text" placeholder="New role name…" @keydown.enter.prevent="addRole" />
          <button class="emp-secondary" type="button" @click="addRole"><Plus :size="15" /> Add role</button>
        </div>

        <div v-if="activeRole" class="emp-role-detail">
          <label class="emp-field"><span>Role name</span><input :value="activeRole.name" class="emp-input" type="text" :disabled="!auth.canManageAccess || isLockedSystemRole" @change="renameActiveRole(($event.target as HTMLInputElement).value)" /></label>
          <div class="emp-field"><span>Role description</span><p class="emp-description">{{ roleDescription(activeRole.id) }}</p></div>

          <label v-if="auth.isOwner && activeRole.id !== 'admin'" class="emp-discount emp-discount--standalone"><span>Discount limit</span><input :value="maxDiscountPercentFor(activeRole)" type="number" min="0" max="100" step="1" :aria-label="`Discount limit for ${activeRole.name}, percent`" @change="updateRoleDiscountLimit" /><b>%</b></label>

          <section v-for="group in permissionGroups" :key="group.id" class="emp-permission-group">
            <button class="emp-permission-group__head" type="button" :aria-expanded="expandedGroups.includes(group.id)" @click="toggleGroup(group.id)">
              <span class="emp-permission-group__icon"><component :is="group.icon" :size="19" /></span>
              <span><strong>{{ group.name }}</strong><small>{{ group.copy }}</small></span>
              <b>{{ groupEnabled(group) }} of {{ groupTotal(group) }}</b>
              <ChevronDown :size="16" :class="{ 'is-open': expandedGroups.includes(group.id) }" />
            </button>
            <div v-if="expandedGroups.includes(group.id)" class="emp-permission-group__items" :class="`emp-permission-group__items--${group.id}`">
              <div v-for="page in group.pages" :key="page" class="emp-permission">
                <span>{{ appPageLabel(page) }}</span>
                <ToggleSwitch :model-value="activeRole.permissions[page]" :ariaLabel="`Allow ${activeRole.name} to access ${appPageLabel(page)}`" :disabled="!auth.canManageAccess || isLockedSystemRole" @update:model-value="(value) => updateRolePermission(page, value)" />
              </div>
              <div v-if="group.id === 'management'" class="emp-permission">
                <span>Team & access</span>
                <ToggleSwitch :model-value="!!activeRole.canManageStaff" :ariaLabel="`Let ${activeRole.name} manage staff`" :disabled="!auth.isOwner || isLockedSystemRole" @update:model-value="updateRoleManageStaff" />
              </div>
            </div>
          </section>

          <footer v-if="auth.canManageAccess" class="emp-role-actions">
            <button class="emp-danger" type="button" :disabled="!canDeleteActiveRole" @click="removeActiveRole"><Trash2 :size="15" /> Delete role</button>
            <button class="emp-secondary" type="button" @click="duplicateActiveRole"><Copy :size="15" /> Duplicate role</button>
            <span>Changes save automatically</span>
          </footer>
        </div>

        <p v-if="auth.authError" class="emp-error">{{ auth.authError }}</p>
      </article>
    </section>

    <AddEmployeeSheet v-if="showAddEmployee" :role-options="assignableRoleOptions" @close="showAddEmployee = false" @saved="showAddEmployee = false" />
  </div>
</template>

<style scoped>
.emp-page {
  --emp-green: #39df72;
  --emp-green-deep: #0d5b2d;
  --emp-panel: color-mix(in srgb, var(--bg-elevated) 88%, transparent);
  --emp-border: color-mix(in srgb, var(--separator) 80%, transparent);
  display: grid;
  gap: 14px;
  padding: 4px 0 24px;
}

.surface-panel { border: 1px solid var(--emp-border); border-radius: 16px; background: var(--emp-panel); box-shadow: var(--shadow-sm); }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; }

.emp-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; }
.emp-title { margin: 0; color: var(--text-primary); font-size: 25px; font-weight: 750; line-height: 1.15; letter-spacing: -0.025em; }
.emp-copy { margin: 5px 0 0; color: var(--text-secondary); font-size: 13px; line-height: 1.4; }
.emp-badge { display: inline-flex; align-items: center; gap: 9px; min-height: 38px; padding: 0 17px; border: 1px solid color-mix(in srgb, var(--success) 28%, transparent); border-radius: 999px; background: color-mix(in srgb, var(--success) 12%, transparent); color: var(--success); font-size: 12px; font-weight: 650; white-space: nowrap; }
.emp-badge--locked { border-color: color-mix(in srgb, var(--warning) 30%, transparent); background: color-mix(in srgb, var(--warning) 12%, transparent); color: var(--warning); }

.emp-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.emp-metric { min-height: 92px; padding: 13px 15px; display: flex; align-items: flex-start; gap: 14px; overflow: hidden; }
.emp-metric__icon, .emp-panel__icon, .emp-insight__icon, .emp-permission-group__icon { display: grid; place-items: center; flex: none; border-radius: 11px; background: color-mix(in srgb, var(--success) 18%, transparent); color: var(--success); }
.emp-metric__icon { width: 48px; height: 48px; }
.emp-metric__icon--purple, .emp-panel__icon--purple { background: color-mix(in srgb, #a855f7 18%, transparent); color: #b96cff; }
.emp-metric p, .emp-metric small { margin: 0; color: var(--text-secondary); }
.emp-metric p { font-size: 12px; line-height: 1.3; }
.emp-metric strong { display: block; margin: 1px 0; color: var(--text-primary); font-size: 22px; line-height: 1.05; font-variant-numeric: tabular-nums; }
.emp-metric small { display: block; max-width: 230px; overflow: hidden; font-size: 10px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }

.emp-layout { display: grid; grid-template-columns: minmax(0, 1.28fr) minmax(430px, 1fr); gap: 14px; align-items: start; }
.emp-left { display: grid; gap: 14px; min-width: 0; }
.emp-panel { min-width: 0; overflow: hidden; }
.emp-panel__head { display: flex; align-items: center; gap: 13px; min-height: 74px; padding: 13px 15px; border-bottom: 1px solid var(--emp-border); }
.emp-panel__head--compact { border-bottom: 0; padding-bottom: 5px; }
.emp-panel__icon { width: 44px; height: 44px; }
.emp-panel__heading { flex: 1; min-width: 0; }
.emp-panel__heading h2 { margin: 0; color: var(--text-primary); font-size: 16px; font-weight: 720; line-height: 1.25; }
.emp-panel__heading p { margin: 3px 0 0; color: var(--text-secondary); font-size: 11px; line-height: 1.35; }

.emp-primary, .emp-secondary, .emp-danger { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 38px; padding: 0 15px; border-radius: 10px; font-size: 12px; font-weight: 680; cursor: pointer; }
.emp-primary { border: 0; background: var(--emp-green); color: #062310; box-shadow: 0 0 24px color-mix(in srgb, var(--emp-green) 20%, transparent); }
.emp-primary:hover { filter: brightness(1.06); }
.emp-secondary { border: 1px solid var(--emp-border); background: var(--fill); color: var(--text-primary); }
.emp-danger { border: 1px solid color-mix(in srgb, var(--danger) 70%, transparent); background: color-mix(in srgb, var(--danger) 7%, transparent); color: var(--danger-600); }
.emp-danger:disabled { opacity: .38; cursor: not-allowed; }

.emp-toolbar { display: grid; grid-template-columns: minmax(0, 1fr) 130px; gap: 10px; padding: 12px 15px; border-bottom: 1px solid var(--emp-border); }
.emp-search, .emp-filter { position: relative; display: flex; align-items: center; gap: 9px; height: 38px; padding: 0 12px; border: 1px solid var(--emp-border); border-radius: 9px; background: var(--fill); color: var(--text-tertiary); }
.emp-search input, .emp-filter select { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--text-primary); font: inherit; font-size: 11px; }
.emp-search input::placeholder { color: var(--text-tertiary); }
.emp-filter select { appearance: none; cursor: pointer; }
.emp-filter svg { position: absolute; right: 10px; pointer-events: none; }

.emp-table { margin: 0 15px; border: 1px solid var(--emp-border); border-radius: 11px; overflow: visible; }
.emp-table__head, .emp-row { display: grid; grid-template-columns: minmax(210px, 2fr) minmax(118px, .9fr) minmax(92px, .65fr) minmax(100px, .8fr); align-items: center; column-gap: 12px; }
.emp-table__head { min-height: 38px; padding: 0 13px; border-bottom: 1px solid var(--emp-border); color: var(--text-tertiary); font-size: 9px; font-weight: 650; letter-spacing: .045em; text-transform: uppercase; }
.emp-row { min-height: 62px; padding: 7px 13px; border-bottom: 1px solid var(--emp-border); color: var(--text-secondary); font-size: 11px; }
.emp-row:last-child { border-bottom: 0; }
.emp-row:hover { background: color-mix(in srgb, var(--fill) 70%, transparent); }
.emp-person { display: flex; align-items: center; gap: 10px; min-width: 0; }
.emp-avatar { display: grid; place-items: center; flex: none; width: 38px; height: 38px; border-radius: 50%; background: linear-gradient(135deg, #166534, #22c55e); color: white; font-size: 11px; font-weight: 750; box-shadow: inset 0 0 0 1px rgba(255,255,255,.12); }
.emp-avatar--1 { background: linear-gradient(135deg, #6d28d9, #c084fc); }
.emp-avatar--2 { background: linear-gradient(135deg, #1d4ed8, #60a5fa); }
.emp-avatar--3 { background: linear-gradient(135deg, #be185d, #fb7185); }
.emp-avatar--4 { background: linear-gradient(135deg, #0e7490, #67e8f9); color: #06212a; }
.emp-person__copy { display: grid; min-width: 0; gap: 2px; }
.emp-person__copy > span { display: flex; align-items: center; gap: 7px; min-width: 0; }
.emp-person__copy strong { overflow: hidden; color: var(--text-primary); font-size: 11px; font-weight: 680; text-overflow: ellipsis; white-space: nowrap; }
.emp-person__copy small { color: var(--text-secondary); font-size: 10px; }
.emp-person__copy em { padding: 2px 6px; border-radius: 999px; background: color-mix(in srgb, var(--success) 22%, transparent); color: var(--success); font-size: 8px; font-style: normal; font-weight: 750; }
.emp-row__select { width: 112px; }
.emp-row__select :deep(.acselect__trigger) { height: 32px; min-height: 32px; border-radius: 999px; background: color-mix(in srgb, var(--success) 15%, transparent); color: var(--success); font-size: 10px; font-weight: 650; }
.emp-status { display: inline-flex; align-items: center; gap: 6px; width: max-content; padding: 5px 8px; border-radius: 999px; background: color-mix(in srgb, var(--text-tertiary) 12%, transparent); color: var(--text-secondary); font-size: 9px; font-weight: 650; }
.emp-status i { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.emp-status--online { background: color-mix(in srgb, var(--success) 15%, transparent); color: var(--success); }
.emp-joined { font-variant-numeric: tabular-nums; }
.emp-empty { padding: 28px 16px; color: var(--text-secondary); font-size: 12px; text-align: center; }
.emp-directory__footer { display: flex; align-items: center; justify-content: space-between; min-height: 52px; padding: 0 15px; color: var(--text-secondary); font-size: 10px; }
.emp-filtered { color: var(--success); }

.emp-insights { padding-bottom: 13px; }
.emp-insights__grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; padding: 8px 15px 0; }
.emp-insights__grid > div { position: relative; display: grid; grid-template-columns: 32px minmax(0, 1fr) auto; grid-template-rows: auto auto; align-items: center; gap: 2px 10px; min-height: 88px; padding: 11px; border: 1px solid var(--emp-border); border-radius: 11px; background: color-mix(in srgb, var(--fill) 45%, transparent); }
.emp-insight__icon { grid-row: 1 / 3; width: 32px; height: 32px; }
.emp-insight__icon--blue { background: color-mix(in srgb, #3b82f6 18%, transparent); color: #60a5fa; }
.emp-insights__grid strong { color: var(--text-primary); font-size: 11px; line-height: 1.3; }
.emp-insights__grid small { color: var(--text-secondary); font-size: 9px; }
.emp-insights__grid > div > svg { grid-column: 3; grid-row: 1 / 3; color: var(--text-tertiary); }

.emp-roles { display: grid; }
.emp-panel__head--role { min-height: 74px; }
.emp-access-count { display: flex; align-items: center; gap: 8px; padding: 7px 10px; border: 1px solid color-mix(in srgb, var(--success) 45%, transparent); border-radius: 10px; background: color-mix(in srgb, var(--success) 8%, transparent); color: var(--success); }
.emp-access-count > span { display: grid; }
.emp-access-count strong { font-size: 9px; line-height: 1.2; white-space: nowrap; }
.emp-access-count small { color: var(--text-secondary); font-size: 8px; }
.emp-role-tabs { display: flex; flex-wrap: wrap; gap: 7px; padding: 7px 14px 6px; }
.emp-role-tab { display: inline-flex; align-items: center; gap: 5px; min-height: 32px; padding: 0 14px; border: 1px solid var(--emp-border); border-radius: 999px; background: var(--fill); color: var(--text-secondary); font-size: 11px; font-weight: 600; cursor: pointer; }
.emp-role-tab--active { border-color: var(--emp-green); background: color-mix(in srgb, var(--emp-green) 10%, transparent); color: var(--text-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--emp-green) 13%, transparent); }
.emp-role-tab--new { padding: 0 10px; border-style: dashed; background: transparent; color: var(--success); }
.emp-role-create { display: grid; grid-template-columns: 1fr auto; gap: 8px; padding: 0 14px 8px; }
.emp-role-detail { display: grid; gap: 6px; padding: 0 14px 10px; }
.emp-field { display: grid; gap: 4px; }
.emp-field > span { color: var(--text-secondary); font-size: 9px; font-weight: 650; text-transform: uppercase; letter-spacing: .04em; }
.emp-input, .emp-description { box-sizing: border-box; width: 100%; height: 34px; min-height: 0; margin: 0; padding: 6px 10px; border: 1px solid var(--emp-border); border-radius: 8px; background: var(--fill); color: var(--text-primary); font: inherit; font-size: 10px; }
.emp-input:disabled { opacity: .72; }
.emp-description { color: var(--text-secondary); line-height: 1.45; }

.emp-authority { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 9px; padding: 9px 10px; border: 1px solid color-mix(in srgb, var(--success) 35%, transparent); border-radius: 10px; background: color-mix(in srgb, var(--success) 5%, transparent); }
.emp-authority__copy { display: flex; align-items: center; gap: 8px; color: var(--success); }
.emp-authority__copy > span { display: grid; }
.emp-authority__copy strong { color: var(--text-primary); font-size: 10px; }
.emp-authority__copy small { color: var(--text-secondary); font-size: 8px; }
.emp-discount { grid-column: 1 / -1; display: flex; align-items: center; gap: 7px; padding-top: 7px; border-top: 1px solid var(--emp-border); color: var(--text-secondary); font-size: 9px; }
.emp-discount input { width: 54px; margin-left: auto; padding: 5px; border: 1px solid var(--emp-border); border-radius: 7px; background: var(--fill); color: var(--text-primary); text-align: right; }
.emp-discount--standalone { min-height: 35px; padding: 5px 9px; border: 1px solid var(--emp-border); border-radius: 9px; background: color-mix(in srgb, var(--fill) 45%, transparent); }

.emp-permission-group { border: 1px solid var(--emp-border); border-radius: 10px; overflow: hidden; }
.emp-permission-group__head { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto 16px; align-items: center; gap: 8px; width: 100%; min-height: 43px; padding: 5px 8px; border: 0; background: color-mix(in srgb, var(--fill) 45%, transparent); color: var(--text-primary); text-align: left; cursor: pointer; }
.emp-permission-group__icon { width: 30px; height: 30px; border-radius: 8px; }
.emp-permission-group__head > span:nth-child(2) { display: grid; gap: 1px; }
.emp-permission-group__head strong { font-size: 10px; text-transform: capitalize; }
.emp-permission-group__head small { color: var(--text-secondary); font-size: 8px; }
.emp-permission-group__head b { color: var(--success); font-size: 9px; font-weight: 650; white-space: nowrap; }
.emp-permission-group__head > svg { color: var(--text-tertiary); transition: transform var(--dur-fast) var(--ease-out); }
.emp-permission-group__head > svg.is-open { transform: rotate(180deg); }
.emp-permission-group__items { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-top: 1px solid var(--emp-border); }
.emp-permission-group__items--sales, .emp-permission-group__items--system { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.emp-permission { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 31px; padding: 3px 8px; border-right: 1px solid var(--emp-border); border-bottom: 1px solid var(--emp-border); color: var(--text-primary); font-size: 8px; }
.emp-permission-group__items--sales .emp-permission:nth-child(3n),
.emp-permission-group__items--system .emp-permission:nth-child(3n),
.emp-permission-group__items:not(.emp-permission-group__items--management):not(.emp-permission-group__items--sales):not(.emp-permission-group__items--system) .emp-permission:nth-child(even),
.emp-permission:last-child { border-right: 0; }
.emp-permission:nth-last-child(-n + 2) { border-bottom: 0; }
.emp-permission-group__items--sales .emp-permission,
.emp-permission-group__items--system .emp-permission { border-bottom: 0; }
.emp-permission :deep(.toggle-switch__input),
.emp-permission :deep(.toggle-switch__track) { width: 38px; height: 22px; }
.emp-permission :deep(.toggle-switch__track) { padding: 2px; }
.emp-permission :deep(.toggle-switch__knob) { width: 18px; height: 18px; }
.emp-permission :deep(.toggle-switch__input:checked + .toggle-switch__track .toggle-switch__knob) { transform: translateX(16px); }

.emp-role-actions { display: flex; align-items: center; gap: 8px; padding-top: 2px; }
.emp-role-actions span { margin-left: auto; color: var(--text-tertiary); font-size: 8px; }
.emp-role-actions .emp-secondary, .emp-role-actions .emp-danger { min-height: 34px; padding: 0 11px; font-size: 10px; }
.emp-error { margin: 0 14px 14px; color: var(--danger-600); font-size: 10px; }

@media (max-width: 1280px) {
  .emp-layout { grid-template-columns: minmax(0, 1fr); }
  .emp-left { grid-template-columns: minmax(0, 1.4fr) minmax(300px, .8fr); align-items: start; }
  .emp-insights__grid { grid-template-columns: 1fr; }
}

@media (max-width: 900px) {
  .emp-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .emp-left { grid-template-columns: 1fr; }
}

@media (max-width: 680px) {
  .emp-page { gap: 12px; }
  .emp-header { align-items: flex-start; flex-direction: column; }
  .emp-title { font-size: 23px; }
  .emp-badge { min-height: 34px; }
  .emp-metrics { grid-template-columns: 1fr 1fr; gap: 8px; }
  .emp-metric { min-height: 82px; padding: 11px; gap: 9px; }
  .emp-metric__icon { width: 38px; height: 38px; }
  .emp-metric strong { font-size: 19px; }
  .emp-metric small { display: none; }
  .emp-panel__head { align-items: flex-start; flex-wrap: wrap; }
  .emp-panel__heading { flex-basis: calc(100% - 60px); }
  .emp-primary { width: 100%; }
  .emp-toolbar { grid-template-columns: 1fr; }
  .emp-table__head { display: none; }
  .emp-row { grid-template-columns: 1fr auto; gap: 10px; padding: 12px; }
  .emp-row > [role='cell'] { min-width: 0; }
  .emp-row > [role='cell']:not(.emp-person)::before { content: attr(data-label); display: block; margin-bottom: 4px; color: var(--text-tertiary); font-size: 8px; text-transform: uppercase; }
  .emp-person { grid-column: 1 / -1; }
  .emp-joined { text-align: right; }
  .emp-insights__grid { grid-template-columns: 1fr; }
  .emp-panel__head--role { display: grid; grid-template-columns: 44px minmax(0, 1fr); }
  .emp-access-count { grid-column: 1 / -1; }
  .emp-role-tabs { flex-wrap: nowrap; overflow-x: auto; }
  .emp-role-tab { flex: none; }
  .emp-permission-group__items,
  .emp-permission-group__items--sales,
  .emp-permission-group__items--system { grid-template-columns: 1fr; }
  .emp-permission, .emp-permission:nth-child(even), .emp-permission:nth-last-child(-n + 2) { border-right: 0; border-bottom: 1px solid var(--emp-border); }
  .emp-permission:last-child { border-bottom: 0; }
  .emp-role-actions { flex-wrap: wrap; }
  .emp-role-actions span { width: 100%; margin: 0; }
}
</style>
