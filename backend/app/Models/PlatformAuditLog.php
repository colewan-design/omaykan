<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * One operator action, as it happened.
 *
 * Append-only: nothing writes to a row after it is inserted and no endpoint
 * offers a way to change or remove one. `$timestamps = false` because there is
 * no `updated_at` to maintain — an audit row that can be updated is not an
 * audit row.
 *
 * Written through App\Support\PlatformAudit, never constructed by hand, so
 * every mutation records the same fields the same way.
 */
class PlatformAuditLog extends Model
{
    use HasUuids;

    public $timestamps = false;

    protected $fillable = [
        'platform_admin_id',
        'actor_email',
        'action',
        'subject_type',
        'subject_id',
        'context',
        'ip_address',
        'user_agent',
        'created_at',
    ];

    protected function casts(): array
    {
        return [
            'context' => 'array',
            'created_at' => 'datetime',
        ];
    }

    /** Null once the account is deleted; `actor_email` still names them. */
    public function admin(): BelongsTo
    {
        return $this->belongsTo(PlatformAdmin::class, 'platform_admin_id');
    }

    /**
     * @return array<string, mixed>
     */
    public function toPortalArray(): array
    {
        return [
            'id' => $this->id,
            'actorEmail' => $this->actor_email,
            'actorName' => $this->admin?->name,
            'action' => $this->action,
            'subjectType' => $this->subject_type,
            'subjectId' => $this->subject_id,
            // Cast to an object so an empty context is `{}` rather than
            // `[]`, which is what the client's type says it reads.
            'context' => (object) ($this->context ?? []),
            'ipAddress' => $this->ip_address,
            'createdAt' => $this->created_at?->toIso8601String(),
        ];
    }
}
