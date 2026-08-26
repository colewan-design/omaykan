<?php

namespace App\Support;

use App\Models\PlatformAdmin;
use App\Models\PlatformAuditLog;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Http\Request;

/**
 * The one way an operator action gets written down.
 *
 * Every mutating endpoint in the portal calls this exactly once. It replaces
 * the old `Log::info('[platform-admin] …')` lines, which recorded no actor and
 * could not be queried — the whole reason named operator accounts exist is so
 * that "who suspended this tenant" has an answer, and a file full of anonymous
 * log lines is not one.
 *
 * A static helper rather than an injected service because there is nothing to
 * configure and nothing to swap: it takes a request and writes a row.
 */
class PlatformAudit
{
    /**
     * @param  Model|string|null  $subject  A model (its class basename and key
     *                                      are recorded) or a plain string id.
     * @param  array<string, mixed>  $context
     */
    public static function record(
        Request $request,
        string $action,
        Model|string|null $subject = null,
        array $context = [],
        ?string $subjectType = null,
    ): PlatformAuditLog {
        $admin = $request->user();

        [$type, $id] = match (true) {
            $subject instanceof Model => [class_basename($subject), (string) $subject->getKey()],
            is_string($subject) => [$subjectType, $subject],
            default => [$subjectType, null],
        };

        return PlatformAuditLog::create([
            'platform_admin_id' => $admin instanceof PlatformAdmin ? $admin->id : null,
            // Snapshotted so the row still names someone after the account is
            // deleted. 'unknown' can only happen if this is ever called
            // outside the guard, which would itself be the bug.
            'actor_email' => $admin instanceof PlatformAdmin ? $admin->email : 'unknown',
            'action' => $action,
            'subject_type' => $subjectType ?? $type,
            'subject_id' => $id,
            'context' => $context === [] ? null : $context,
            'ip_address' => $request->ip(),
            // Truncated: this is a diagnostic, and a hostile client can send a
            // very long header.
            'user_agent' => mb_substr((string) $request->userAgent(), 0, 512) ?: null,
            'created_at' => now(),
        ]);
    }
}
