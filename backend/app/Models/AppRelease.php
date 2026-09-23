<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

/**
 * One published build of one sideloaded app.
 *
 * There is no owning organization and no guard: a release is a fact about the
 * platform's own software, published from the console and read by anyone
 * holding the app. See the migration for why history is kept.
 */
class AppRelease extends Model
{
    use HasUuids;

    /** The Android customer app. The only slug in use today. */
    public const STOREFRONT_ANDROID = 'storefront-android';

    protected $fillable = [
        'slug',
        'version_code',
        'version_name',
        'apk_url',
        'notes',
        'published_at',
    ];

    protected function casts(): array
    {
        return [
            'version_code' => 'integer',
            'published_at' => 'datetime',
        ];
    }

    public function setSlugAttribute(?string $value): void
    {
        $this->attributes['slug'] = $value === null ? null : strtolower(trim($value));
    }

    /**
     * What the app should be running, or null if nothing is published for it.
     *
     * Highest version_code wins rather than most recently published, so
     * un-publishing a bad build genuinely rolls back to the one before instead
     * of leaving the newest `published_at` pointing at a withdrawn release. A
     * `published_at` in the future is scheduled, not live.
     */
    public static function current(string $slug): ?self
    {
        return static::query()
            ->where('slug', strtolower(trim($slug)))
            ->whereNotNull('published_at')
            ->where('published_at', '<=', now())
            ->orderByDesc('version_code')
            ->first();
    }

    /**
     * The update-check payload.
     *
     * Deliberately thin, and deliberately says nothing about whether an update
     * is *needed*: the client compares this against its own
     * PackageInfo.longVersionCode. The server has no idea what is installed on
     * the phone asking, and a server that guessed would be wrong on every
     * sideloaded downgrade.
     *
     * @return array<string, mixed>
     */
    public function toClientArray(): array
    {
        return [
            'slug' => $this->slug,
            'versionCode' => (int) $this->version_code,
            'versionName' => $this->version_name,
            'apkUrl' => $this->apk_url,
            'notes' => $this->notes,
            'publishedAt' => $this->published_at?->toIso8601String(),
        ];
    }
}
