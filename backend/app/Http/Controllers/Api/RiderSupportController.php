<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\JsonResponse;

/**
 * How a rider reaches a human.
 *
 * An endpoint rather than a constant compiled into the app, because the number
 * a rider calls when something has gone wrong on a road is the last thing that
 * should require a Play Store release to correct.
 *
 * Outside the approval gate, and that is the point: a rider whose application
 * was rejected for a reason they do not understand is *exactly* the person who
 * needs to be able to ask somebody, and gating support behind approval would
 * silence the people with the most to ask about.
 *
 * Anything null is simply absent — the app renders the channels it is given
 * and nothing else, so an unset SUPPORT_PHONE means no call button rather than
 * a button that dials nowhere.
 */
class RiderSupportController extends Controller
{
    public function show(): JsonResponse
    {
        return response()->json([
            'email' => config('support.email'),
            'phone' => config('support.phone'),
            'hours' => config('support.hours'),
        ]);
    }
}
