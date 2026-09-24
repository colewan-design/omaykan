<?php

namespace App\Services;

/**
 * What counts as a shop's own address, `<slug>.omaykan.com`.
 *
 * **This is the PHP copy of an answer that also lives in TypeScript** —
 * RESERVED_SHOP_SUBDOMAINS, isShopSubdomainLabel and slugFromShopHost in
 * packages/shared/src/index.ts. Same idea as RolePermissions: the browser has
 * to know it to build a link, the server has to know it to read one, and the
 * two must agree or a shop gets an address it cannot be reached at. Change
 * one, change the other.
 *
 * It used to be duplicated twice inside this application as well —
 * SignupController held its own list of reserved labels — and there are now
 * two callers, so it is one class instead: signup refusing a slug, and
 * ShopShellController deciding which shop a request is for.
 */
class ShopSubdomain
{
    /**
     * Labels that are the platform's, never a shop's.
     *
     * A shop called "App" is not given `app.omaykan.com` — that is the till —
     * and signup suffixes it rather than handing it out (see
     * SignupController::uniqueSlug).
     */
    public const RESERVED = [
        'www', 'api', 'app', 'admin', 'mail', 'smtp', 'imap', 'pop', 'ftp', 'cdn',
        'static', 'assets', 'shop', 'shops', 'store', 'stores', 'rider', 'riders',
        'seller', 'sellers', 'help', 'support', 'status', 'blog', 'docs', 'dev',
        'staging', 'test', 'demo', 'platform', 'dashboard', 'account', 'cart',
        'checkout', 'reverb', 'ws', 'omaykan',
    ];

    /** Where shops live — blank where this deployment has no shop domain. */
    public static function rootDomain(): string
    {
        return strtolower(trim((string) config('shops.root_domain')));
    }

    public static function isReserved(string $label): bool
    {
        return in_array($label, self::RESERVED, true);
    }

    /** A slug that can stand as one DNS label: lowercase, digits, inner hyphens, ≤ 63. */
    public static function isLabel(string $label): bool
    {
        return preg_match('/^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/', $label) === 1
            && ! self::isReserved($label);
    }

    /**
     * The slug a host names, or '' for the apex, `www`, and anything else that
     * is not one shop's address.
     *
     * Deliberately strict about depth: `a.b.omaykan.com` is not a shop, and
     * treating it as one would let any name be invented under a shop's
     * certificate. A port is stripped because `getHost()` on a development
     * server carries one.
     */
    public static function slugFromHost(string $host, ?string $rootDomain = null): string
    {
        $root = $rootDomain !== null ? strtolower(trim($rootDomain)) : self::rootDomain();
        $host = rtrim(strtolower(trim($host)), '.');
        $host = explode(':', $host)[0];

        if ($root === '' || ! str_ends_with($host, '.'.$root)) {
            return '';
        }

        $label = substr($host, 0, -(strlen($root) + 1));

        if (str_contains($label, '.')) {
            return '';
        }

        return self::isLabel($label) ? $label : '';
    }
}
