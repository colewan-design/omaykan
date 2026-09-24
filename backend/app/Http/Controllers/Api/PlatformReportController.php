<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Services\PlatformInsights;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;

/** One internally consistent, export-ready marketplace report snapshot. */
class PlatformReportController extends Controller
{
    private const RANGES = ['today', '7', '30', '365', 'all'];

    public function __invoke(Request $request, PlatformInsights $insights): JsonResponse
    {
        $validated = $request->validate([
            'range' => ['nullable', Rule::in(self::RANGES)],
        ]);

        $range = (string) ($validated['range'] ?? '30');
        $to = Carbon::now()->endOfDay();
        $earliestOrder = $range === 'all' ? Order::query()->withTrashed()->min('created_at') : null;
        $from = match ($range) {
            'today' => $to->copy()->startOfDay(),
            '7' => $to->copy()->subDays(6)->startOfDay(),
            '30' => $to->copy()->subDays(29)->startOfDay(),
            '365' => $to->copy()->subDays(364)->startOfDay(),
            'all' => $earliestOrder
                ? Carbon::parse($earliestOrder)->startOfDay()
                : $to->copy()->startOfDay(),
        };

        return response()->json([
            'window' => [
                'from' => $from->toDateString(),
                'to' => $to->toDateString(),
                'range' => $range,
                'days' => $from->diffInDays($to) + 1,
            ],
            'headline' => $insights->reportHeadline($from, $to),
            'series' => $insights->reportSeries($from, $to),
            'paymentsByMethod' => $insights->paymentsByMethod($from, $to),
            'salesByBusinessMode' => $insights->salesByBusinessMode($from, $to),
            'topProducts' => $insights->topProducts($from, $to),
            'ordersByHour' => $insights->paidOrdersByHour($from, $to),
            'recentTransactions' => $insights->recentTransactions($from, $to),
            'sourceNote' => 'Paid, non-voided orders recorded by Omaykan in the selected period.',
        ]);
    }
}
