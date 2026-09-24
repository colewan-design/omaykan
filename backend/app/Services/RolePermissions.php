<?php

namespace App\Services;

use App\Models\PosRole;

/**
 * Which pages of the back office a role may use — the server's reading of the
 * permissions the till already enforces on screen.
 *
 * ## Why roles and not a rank
 *
 * Owners build their own roles in the till (Employees → Roles), each a set of
 * page permissions, and `roles` stores them per organization. A guard that
 * said "managers only" would overrule a shop that deliberately gave its head
 * cashier the Products page. So the question asked here is the till's own:
 * does this role have *that page*?
 *
 * ## Why there are defaults
 *
 * Signup creates no role rows. They appear only once an owner saves the roles
 * screen, so a fresh shop has none, and its roles are whatever the till ships
 * with. DEFAULTS is that list. **Keep it in step with `defaultRoles` in
 * packages/shared/src/index.ts** — same idea as ShopSubdomain::RESERVED, and
 * for the same reason: two copies of one rule, in two languages, with nothing
 * but this comment keeping them honest.
 */
class RolePermissions
{
    /** Every page the till knows. `appPageKeys` in packages/shared. */
    public const PAGES = [
        'dashboard', 'sales', 'orders', 'products', 'customers', 'suppliers',
        'employees', 'inventory', 'tables', 'reports', 'integrations',
        'register', 'settings', 'diagnostics',
    ];

    /** `defaultRoles` in packages/shared. Admin is not here: it can do everything. */
    public const DEFAULTS = [
        'manager' => [
            'dashboard', 'sales', 'orders', 'products', 'customers', 'suppliers',
            'employees', 'inventory', 'reports', 'register', 'settings', 'tables',
        ],
        'cashier' => ['dashboard', 'sales', 'orders', 'register', 'settings', 'tables'],
        'guest' => [],
    ];

    /**
     * How much each built-in role may take off a sale on its own, in percent,
     * when the shop has not said. `DEFAULT_DISCOUNT_LIMITS` in packages/shared.
     * A role nobody has heard of may give nothing.
     */
    public const DEFAULT_DISCOUNT_LIMITS = [
        'manager' => 100,
        'cashier' => 0,
        'guest' => 0,
    ];

    public function maxDiscountPercent(string $organizationId, string $roleKey): int
    {
        if ($roleKey === 'admin') {
            return 100;
        }

        $saved = PosRole::query()
            ->where('organization_id', $organizationId)
            ->where('role_key', $roleKey)
            ->whereNull('deleted_at')
            ->value('max_discount_percent');

        return $saved !== null ? (int) $saved : (self::DEFAULT_DISCOUNT_LIMITS[$roleKey] ?? 0);
    }

    /**
     * Whether `$roleKey`, in this organization, may use `$page`.
     *
     * Admin always may — it is the owner's role and the till locks it. A role
     * with a saved row answers from the row. Anything else falls back to the
     * built-in list, and a role nobody has heard of gets nothing.
     */
    public function allows(string $organizationId, string $roleKey, string $page): bool
    {
        if ($roleKey === 'admin') {
            return true;
        }

        $saved = PosRole::query()
            ->where('organization_id', $organizationId)
            ->where('role_key', $roleKey)
            ->whereNull('deleted_at')
            ->value('permissions');

        if (is_array($saved)) {
            return ($saved[$page] ?? false) === true;
        }

        return in_array($page, self::DEFAULTS[$roleKey] ?? [], true);
    }
}
