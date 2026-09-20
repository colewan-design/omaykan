<script setup lang="ts">
import {
  BadgeCheck,
  LockKeyhole,
  Plus,
  Search,
  Shield,
  Trash2,
  UserCircle2,
  Users,
} from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'
import AddEmployeeSheet from '@pos/core/components/AddEmployeeSheet.vue'
import AutocompleteSelect from '@pos/core/components/AutocompleteSelect.vue'
import ToggleSwitch from '@pos/core/components/ToggleSwitch.vue'
import { useAuthStore } from '@pos/core/stores/auth'
import { appPageKeys, appPageLabel, maxDiscountPercentFor, type AppPageKey } from '@pos/shared/index'

const auth = useAuthStore()
const showAddEmployee = ref(false)

function openAddEmployee() {
  auth.clearAuthError()
  showAddEmployee.value = true
}

onMounted(() => {
  if (!auth.isReady) {
    void auth.initialize()
  }
})

const searchQuery = ref('')
const roleFilter = ref<'all' | string>('all')
const activeRoleId = ref('')
const newRoleName = ref('')

const activeRole = computed(() => auth.roles.find((role) => role.id === activeRoleId.value) ?? null)
const roleOptions = computed(() => auth.roles.map((role) => ({ value: role.id, label: role.name })))
const assignableRoleOptions = computed(() =>
  auth.roles
    .filter((role) => role.id !== 'guest')
    .map((role) => ({ value: role.id, label: role.name })),
)
const roleFilterOptions = computed(() => [{ value: 'all', label: 'All roles' }, ...roleOptions.value])
const isLockedSystemRole = computed(() =>
  activeRole.value?.id === 'admin' || activeRole.value?.id === 'guest',
)

const filteredUsers = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase()

  return auth.users.filter((user) => {
    if (roleFilter.value !== 'all' && user.roleId !== roleFilter.value) {
      return false
    }

    if (!needle) {
      return true
    }

    return (
      user.fullName.toLowerCase().includes(needle)
      || user.username.toLowerCase().includes(needle)
      || user.roleId.toLowerCase().includes(needle)
    )
  })
})

const totalPermissionGrants = computed(() =>
  auth.roles.reduce(
    (count, role) => count + appPageKeys.filter((page) => role.permissions[page]).length,
    0,
  ),
)

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

    if (!roles.some((role) => role.id === activeRoleId.value)) {
      activeRoleId.value = roles[0].id
    }

    if (roleFilter.value !== 'all' && !roles.some((role) => role.id === roleFilter.value)) {
      roleFilter.value = 'all'
    }
  },
  { immediate: true, deep: true },
)

function formatMemberSince(value: string) {
  return new Intl.DateTimeFormat('en-PH', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(new Date(value))
}

function assignUserRole(userId: string, roleId: string) {
  if (!auth.canManageAccess) return
  void auth.updateUserRole(userId, roleId)
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
  })
}

function removeActiveRole() {
  if (!activeRole.value) return
  void auth.deleteRole(activeRole.value.id)
}
</script>

<template>
  <div class="emp-page">

    <!-- ── Page header ──────────────────────────────────────────────────────── -->
    <div class="emp-header">
      <div>
        <h1 class="emp-title">Employees</h1>
        <p class="emp-copy">Staff access and role control for this register.</p>
      </div>
      <div class="emp-badge" :class="{ 'emp-badge--locked': !auth.canManageAccess }">
        <Shield :size="15" />
        <span>{{ auth.canManageAccess ? 'Admin controls enabled' : 'View-only access' }}</span>
      </div>
    </div>

    <!-- ── Metric cards ─────────────────────────────────────────────────────── -->
    <section class="emp-metrics">
      <article class="emp-metric surface-panel">
        <div class="emp-metric__icon emp-metric__icon--blue"><Users :size="18" /></div>
        <div>
          <p class="emp-metric__label">Team members</p>
          <strong class="emp-metric__value">{{ auth.users.length }}</strong>
        </div>
      </article>

      <article class="emp-metric surface-panel">
        <div class="emp-metric__icon emp-metric__icon--green"><BadgeCheck :size="18" /></div>
        <div>
          <p class="emp-metric__label">Roles configured</p>
          <strong class="emp-metric__value">{{ auth.roles.length }}</strong>
        </div>
      </article>

      <article class="emp-metric surface-panel">
        <div class="emp-metric__icon emp-metric__icon--purple"><LockKeyhole :size="18" /></div>
        <div>
          <p class="emp-metric__label">Permission grants</p>
          <strong class="emp-metric__value">{{ totalPermissionGrants }}</strong>
        </div>
      </article>
    </section>

    <!-- ── Two-column layout ────────────────────────────────────────────────── -->
    <section class="emp-layout">

      <!-- Team directory -->
      <article class="emp-card surface-panel">
        <div class="emp-card__head emp-card__head--row">
          <div>
            <p class="emp-eyebrow">Team directory</p>
            <h2 class="emp-card__title">Accounts on this register</h2>
          </div>
          <button
            v-if="auth.canManageAccess"
            class="segment-button emp-add-button"
            type="button"
            @click="openAddEmployee"
          >
            <Plus :size="15" />
            <span>Add employee</span>
          </button>
        </div>

        <div class="emp-toolbar">
          <label class="emp-search">
            <Search :size="15" class="emp-search__icon" aria-hidden="true" />
            <input
              v-model="searchQuery"
              class="emp-search__input"
              type="search"
              placeholder="Search name, username, or role…"
              aria-label="Search employees"
            />
          </label>
          <AutocompleteSelect
            v-model="roleFilter"
            class="emp-toolbar__select"
            label="Filter by role"
            :options="roleFilterOptions"
          />
        </div>

        <div v-if="filteredUsers.length" class="emp-list">
          <div v-for="user in filteredUsers" :key="user.id" class="emp-row">
            <div class="emp-row__avatar">
              <UserCircle2 :size="20" />
            </div>
            <div class="emp-row__body">
              <div class="emp-row__name-row">
                <strong class="emp-row__name">{{ user.fullName }}</strong>
                <span v-if="auth.currentUser?.id === user.id" class="emp-chip">You</span>
              </div>
              <p class="emp-row__sub">@{{ user.username }} · Joined {{ formatMemberSince(user.createdAt) }}</p>
            </div>
            <AutocompleteSelect
              class="emp-row__select"
              :model-value="user.roleId"
              label="Assign role"
              :disabled="!auth.canManageAccess"
              :options="assignableRoleOptions"
              @update:model-value="assignUserRole(user.id, $event)"
            />
          </div>
        </div>

        <div v-else class="emp-empty">
          No team members match the current filters.
        </div>
      </article>

      <!-- Role permissions -->
      <article class="emp-card surface-panel">
        <div class="emp-card__head">
          <p class="emp-eyebrow">Role permissions</p>
          <h2 class="emp-card__title">Page access by role</h2>
        </div>

        <div class="emp-role-tabs">
          <button
            v-for="role in auth.roles"
            :key="role.id"
            class="segment-button"
            :class="{ active: activeRoleId === role.id }"
            type="button"
            @click="activeRoleId = role.id"
          >{{ role.name }}</button>
        </div>

        <div v-if="auth.canManageAccess" class="emp-role-create">
          <input
            v-model="newRoleName"
            class="sheet-input"
            type="text"
            placeholder="New role name…"
            @keydown.enter.prevent="addRole"
          />
          <button class="segment-button emp-role-create__btn" type="button" @click="addRole">
            <Plus :size="15" />
            <span>Add</span>
          </button>
        </div>

        <div v-if="activeRole" class="emp-role-detail">
          <label class="emp-field">
            <span class="emp-field__label">Role name</span>
            <input
              :value="activeRole.name"
              class="sheet-input"
              type="text"
              :disabled="!auth.canManageAccess || isLockedSystemRole"
              @blur="renameActiveRole(($event.target as HTMLInputElement).value)"
              @keydown.enter.prevent="renameActiveRole(($event.target as HTMLInputElement).value)"
            />
          </label>

          <p v-if="isLockedSystemRole" class="emp-helper">
            Built-in system roles are locked so owner access and guest access cannot be weakened by mistake.
          </p>

          <div v-if="auth.isOwner" class="emp-permissions emp-permissions--staff">
            <div class="emp-perm-row">
              <span class="emp-perm-row__label">
                Can manage staff
                <span class="emp-perm-row__hint">Add employees, assign roles, edit non-locked roles</span>
              </span>
              <ToggleSwitch
                :model-value="!!activeRole.canManageStaff"
                :ariaLabel="`Let ${activeRole.name} manage staff`"
                :disabled="isLockedSystemRole"
                @update:model-value="updateRoleManageStaff"
              />
            </div>
            <!-- Money authority, so owner-only like the switch above. Admin is
                 always 100% and is not offered. -->
            <div v-if="activeRole.id !== 'admin'" class="emp-perm-row">
              <span class="emp-perm-row__label">
                Discount limit
                <span class="emp-perm-row__hint">The most this role can take off a sale on its own, in %</span>
              </span>
              <input
                :value="maxDiscountPercentFor(activeRole)"
                class="sheet-input emp-discount-limit"
                type="number"
                min="0"
                max="100"
                step="1"
                :aria-label="`Discount limit for ${activeRole.name}, percent`"
                @change="updateRoleDiscountLimit"
              />
            </div>
          </div>

          <div class="emp-permissions">
            <div v-for="page in appPageKeys" :key="page" class="emp-perm-row">
              <span class="emp-perm-row__label">{{ appPageLabel(page) }}</span>
              <ToggleSwitch
                :model-value="activeRole.permissions[page]"
                :ariaLabel="`Allow ${activeRole.name} to access ${appPageLabel(page)}`"
                :disabled="!auth.canManageAccess || isLockedSystemRole"
                @update:model-value="(value) => updateRolePermission(page, value)"
              />
            </div>
          </div>

          <button
            v-if="auth.canManageAccess"
            class="settings-remove-button emp-role-detail__delete"
            type="button"
            :disabled="!canDeleteActiveRole"
            @click="removeActiveRole"
          >
            <Trash2 :size="15" />
            <span>Delete role</span>
          </button>
        </div>

        <p v-if="auth.authError" class="emp-error">{{ auth.authError }}</p>
      </article>
    </section>

    <AddEmployeeSheet
      v-if="showAddEmployee"
      :role-options="assignableRoleOptions"
      @close="showAddEmployee = false"
      @saved="showAddEmployee = false"
    />
  </div>
</template>

<style scoped>
.emp-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-5);
  padding: var(--space-4) 0 var(--space-8);
}

.surface-panel {
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-xl);
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

/* ── Page header ─────────────────────────────────────────────────────────── */

.emp-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.emp-title {
  margin: 0;
  font: var(--type-title1);
}

.emp-copy {
  max-width: 64ch;
  margin: var(--space-2) 0 0;
  color: var(--text-secondary);
}

.emp-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  height: 36px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--success) 12%, transparent);
  color: var(--success);
  font: var(--type-caption);
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.emp-badge--locked {
  background: color-mix(in srgb, var(--warning) 14%, transparent);
  color: var(--warning);
}

/* ── Metrics ─────────────────────────────────────────────────────────────── */

.emp-metrics {
  display: grid;
  gap: var(--space-4);
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.emp-metric {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
}

.emp-metric__icon {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
}

.emp-metric__icon--blue   { background: color-mix(in srgb, var(--accent)  14%, transparent); color: var(--accent); }
.emp-metric__icon--green  { background: color-mix(in srgb, var(--success) 14%, transparent); color: var(--success); }
.emp-metric__icon--purple { background: color-mix(in srgb, #af52de        14%, transparent); color: #af52de; }

.emp-metric__label {
  margin: 0 0 2px;
  color: var(--text-secondary);
  font: var(--type-caption);
}

.emp-metric__value {
  font: var(--type-title2);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

/* ── Layout ──────────────────────────────────────────────────────────────── */

.emp-layout {
  display: grid;
  gap: var(--space-4);
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  align-items: start;
}

.emp-card {
  padding: var(--space-5);
  display: grid;
  gap: var(--space-4);
}

/* ── Card head ───────────────────────────────────────────────────────────── */

.emp-eyebrow {
  margin: 0 0 var(--space-1);
  color: var(--text-secondary);
  font: var(--type-caption);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.emp-card__title {
  margin: 0;
  font: var(--type-headline);
}

.emp-card__head--row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.emp-add-button {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-4);
}

/* ── Toolbar ─────────────────────────────────────────────────────────────── */

.emp-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.emp-search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 44px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  background: var(--fill);
  cursor: text;
}

.emp-search__icon {
  flex: none;
  color: var(--text-tertiary);
}

.emp-search__input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  outline: none;
  color: var(--text-primary);
  font: var(--type-subhead);
}

.emp-search__input::placeholder {
  color: var(--text-tertiary);
}

/* ── Employee list ───────────────────────────────────────────────────────── */

.emp-list {
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.emp-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 0.5px solid var(--separator);
  transition: background var(--dur-fast) var(--ease-out);
}

.emp-row:last-child {
  border-bottom: none;
}

.emp-row:hover {
  background: var(--fill);
}

.emp-row__avatar {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  display: grid;
  place-items: center;
}

.emp-row__body {
  flex: 1;
  min-width: 0;
}

.emp-row__name-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: 3px;
}

.emp-row__name {
  font: var(--type-subhead);
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.emp-row__sub {
  margin: 0;
  font: var(--type-caption);
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.emp-chip {
  flex: none;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  font: var(--type-caption);
  font-weight: 600;
}

.emp-row__select {
  flex: none;
  width: 130px;
}

.emp-empty {
  padding: var(--space-6) 0;
  color: var(--text-secondary);
  text-align: center;
  font: var(--type-subhead);
}

/* ── Role tabs + create ──────────────────────────────────────────────────── */

.emp-role-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.emp-role-create {
  display: flex;
  gap: var(--space-2);
}

.emp-role-create__btn {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-4);
}

/* ── Role detail ─────────────────────────────────────────────────────────── */

.emp-role-detail {
  display: grid;
  gap: var(--space-4);
}

.emp-field {
  display: grid;
  gap: var(--space-2);
}

.emp-field__label {
  font: var(--type-caption);
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.emp-helper {
  margin: 0;
  color: var(--text-secondary);
  font: var(--type-caption);
}

/* ── Permissions list ────────────────────────────────────────────────────── */

.emp-permissions {
  border: 0.5px solid var(--separator);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.emp-perm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: 10px var(--space-4);
  border-bottom: 0.5px solid var(--separator);
  transition: background var(--dur-fast) var(--ease-out);
}

.emp-perm-row:last-child {
  border-bottom: none;
}

.emp-perm-row__label {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font: var(--type-subhead);
  color: var(--text-primary);
}

.emp-perm-row__hint {
  font: var(--type-caption);
  color: var(--text-secondary);
}

.emp-permissions--staff {
  border-color: var(--accent);
}

.emp-role-detail__delete {
  justify-self: start;
}

.emp-error {
  margin: 0;
  color: var(--danger);
  font: var(--type-caption);
}

/* ── Responsive ──────────────────────────────────────────────────────────── */

@media (max-width: 1100px) {
  .emp-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .emp-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .emp-metrics {
    grid-template-columns: 1fr 1fr;
  }

  .emp-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .emp-row {
    flex-wrap: wrap;
  }

  .emp-row__select {
    width: 100%;
  }

  .emp-card {
    padding: var(--space-4);
  }

  .emp-perm-row {
    padding: var(--space-2) var(--space-3);
  }
}

/* The discount limit sits where a toggle would; a toggle is 44px wide. */
.emp-discount-limit {
  width: 76px;
  text-align: right;
}
</style>
