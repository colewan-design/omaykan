<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\PlatformInsights;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;

/**
 * The same marketplace as the dashboard, over a window the operator chooses
 * and broken down by what sold and where it went.
 *
 * The dashboard answers "how are we doing"; this answers "doing what, and
 * where". Both read {@see PlatformInsights}, so the totals agree by
 * construction — the one thing that matters most about a second screen of
 * numbers is that it does not contradict the first.
 *
 * Behind `auth:platform` with the rest of the operator tools; see routes/api.php.
 */
class PlatformAnalyticsController extends Controller
{
    /** Windows offered, in days. */
    private const RANGES = [7, 30, 90, 365];

    public function __invoke(Request $request, PlatformInsights $insights): JsonResponse
    {
        $validated = $request->validate([
            'days' => ['nullable', Rule::in(self::RANGES)],
        ]);

        $days = (int) ($validated['days'] ?? 30);
        $to = Carbon::now()->endOfDay();
        $from = $to->copy()->subDays($days - 1)->startOfDay();

        return response()->json([
            'window' => [
                'from' => $from->toDateString(),
                'to' => $to->toDateString(),
                'days' => $days,
            ],
            'headline' => $insights->headline($from, $to),
            'series' => $insights->dailySeries($from, $to),
            'salesByCategory' => $insights->salesByCategory($from, $to),
            'ordersByLocation' => $insights->ordersByLocation($from, $to),
            'returningCustomers' => $insights->returningCustomers($from, $to),
            'topProducts' => $insights->topProducts($from, $to),
            'ordersByHour' => $insights->ordersByHour($from, $to),
        ]);
    }
}
