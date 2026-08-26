<?php

namespace App\Http\Controllers\Api\Platform;

use App\Http\Controllers\Controller;
use App\Models\PlatformAdmin;
use App\Models\PlatformAuditLog;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Reading the record of what operators have done.
 *
 * Read-only, and there is no write, update or delete endpoint anywhere for
 * this table — by design. An audit trail an operator can edit answers "who
 * did this" with whatever the last person to touch it wanted it to say.
 *
 * Every operator can read the whole log, including their own rows and an
 * owner's. Mutual visibility is the point: this is what replaced one shared
 * secret that nobody could be held to.
 */
class AuditLogController extends Controller
{
    private const PER_PAGE = 50;

    public function index(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'actor' => ['nullable', 'string', 'max:255'],
            // Prefix, so 'organization' catches suspended, reactivated and
            // deleted without the client having to know the full vocabulary.
            'action' => ['nullable', 'string', 'max:120'],
            'subject' => ['nullable', 'string', 'max:255'],
            'from' => ['nullable', 'date'],
            'to' => ['nullable', 'date'],
            'page' => ['nullable', 'integer', 'min:1'],
        ]);

        $query = PlatformAuditLog::query()
            ->with('admin')
            ->orderByDesc('created_at');

        if (($actor = trim($filters['actor'] ?? '')) !== '') {
            $query->where('actor_email', 'like', '%'.$actor.'%');
        }

        if (($action = trim($filters['action'] ?? '')) !== '') {
            $query->where('action', 'like', $action.'%');
        }

        if (($subject = trim($filters['subject'] ?? '')) !== '') {
            $query->where('subject_id', $subject);
        }

        if (isset($filters['from'])) {
            $query->where('created_at', '>=', $filters['from']);
        }

        if (isset($filters['to'])) {
            // Inclusive of the whole named day: a date filter that silently
            // drops today's rows reads as missing data.
            $query->where('created_at', '<=', $filters['to'].' 23:59:59');
        }

        $page = $query->paginate(self::PER_PAGE);

        return response()->json([
            'logs' => collect($page->items())
                ->map(fn (PlatformAuditLog $log) => $log->toPortalArray())
                ->values(),
            'page' => $page->currentPage(),
            'lastPage' => $page->lastPage(),
            'total' => $page->total(),
            // Lets the filter offer the real vocabulary rather than a
            // hardcoded list that drifts as actions are added.
            'actors' => PlatformAdmin::query()->orderBy('email')->pluck('email'),
            'actions' => PlatformAuditLog::query()
                ->distinct()->orderBy('action')->pluck('action'),
        ]);
    }
}
