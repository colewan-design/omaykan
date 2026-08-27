<?php

namespace App\Providers;

use App\Contracts\SupportInbox;
use App\Services\ImapSupportInbox;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        $this->app->singleton(SupportInbox::class, ImapSupportInbox::class);
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        //
    }
}
