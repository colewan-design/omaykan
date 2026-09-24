<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\PlatformInsights;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

/**
 * The first screen an operator sees: what the marketplace did lately.
 *
 * Read-only, and deliberately one request. Four tiles, a line, a breakdown and
 * a short list is a single glance, so it should also be a single round trip —
 * a dashboard assembled from six calls shows its seams while it loads, and on
 * a phone on mobile data it shows them for a while.
 *
 * Behind `auth:platform` with the rest of the operator tools; see routes/api.php.
 */
class PlatformOverviewController extends Controller
{
    public function __invoke(Request $request, PlatformInsights $insights): JsonResponse
    {
        $validated = $request->validate([
            'days' => ['sometimes', 'integer', 'in:7,30,90'],
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
            'byProgress' => $insights->byProgress(),
            'recentOrders' => $insights->recentOrders(),
        ]);
    }
}
