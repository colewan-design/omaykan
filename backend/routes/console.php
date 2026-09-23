<?php

use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;

Artisan::command('inspire', function () {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

/*
 * The scheduler. Nothing ran on one before this, and the server needs the
 * standard Laravel cron line for any of it to happen — see deployment.md,
 * "Scheduler". Each entry is safe to miss: it catches up the next time.
 */
Schedule::command('products:sweep-images')->weekly()->sundays()->at('03:00');

// Points past their expiry date are written off, oldest first. See Loyalty::expire.
Schedule::command('loyalty:expire')->dailyAt('02:30');

/*
 * Lapsed subscriptions move to past_due, and the three notices in §6.4 go out.
 *
 * Early enough that a merchant reads the mail with their morning, late enough
 * that it is not competing with the nightly jobs above. The status machine
 * runs whatever `billing.enforce` says; the mail is gated on `billing.dunning`
 * and both ship off.
 */
Schedule::command('billing:advance-subscriptions')->dailyAt('06:00');
