<?php

use App\Models\CustomerAccount;
use App\Models\PlatformAdmin;
use App\Models\Rider;
use App\Models\User;

return [

    /*
    |--------------------------------------------------------------------------
    | Authentication Defaults
    |--------------------------------------------------------------------------
    |
    | This option defines the default authentication "guard" and password
    | reset "broker" for your application. You may change these values
    | as required, but they're a perfect start for most applications.
    |
    */

    'defaults' => [
        'guard' => env('AUTH_GUARD', 'web'),
        'passwords' => env('AUTH_PASSWORD_BROKER', 'users'),
    ],

    /*
    |--------------------------------------------------------------------------
    | Authentication Guards
    |--------------------------------------------------------------------------
    |
    | Next, you may define every authentication guard for your application.
    | Of course, a great default configuration has been defined for you
    | which utilizes session storage plus the Eloquent user provider.
    |
    | All authentication guards have a user provider, which defines how the
    | users are actually retrieved out of your database or other storage
    | system used by the application. Typically, Eloquent is utilized.
    |
    | Supported: "session"
    |
    */

    'guards' => [
        'web' => [
            'driver' => 'session',
            'provider' => 'users',
        ],

        /*
         * Shoppers. Its own guard rather than an ability on the default
         * sanctum guard, so `auth:sanctum` — which every seller and sync route
         * uses — can never resolve a customer, whatever abilities their token
         * was minted with. Reached as `auth:customer`.
         */
        'customer' => [
            'driver' => 'sanctum',
            'provider' => 'customers',
        ],

        /*
         * Riders. A third identity, separate from both of the above: a rider
         * works across every shop on the platform, so they must never resolve
         * on `auth:sanctum` (store staff and paired devices), and they are not
         * shoppers either. Reached as `auth:rider`.
         */
        'rider' => [
            'driver' => 'sanctum',
            'provider' => 'riders',
        ],

        /*
         * Platform operators. A fourth identity, and the one with the widest
         * reach: an operator acts across every tenant on the platform. That is
         * exactly why it is its own guard — `auth:sanctum` (store staff and
         * paired devices) must never resolve an operator, and an operator
         * token must never be what lets someone through a seller or sync
         * route. Reached as `auth:platform`, always paired with the
         * `platform.active` middleware. Replaces the shared secret that used
         * to gate the operator dashboard.
         */
        'platform' => [
            'driver' => 'sanctum',
            'provider' => 'platform_admins',
        ],
    ],

    /*
    |--------------------------------------------------------------------------
    | User Providers
    |--------------------------------------------------------------------------
    |
    | All authentication guards have a user provider, which defines how the
    | users are actually retrieved out of your database or other storage
    | system used by the application. Typically, Eloquent is utilized.
    |
    | If you have multiple user tables or models you may configure multiple
    | providers to represent the model / table. These providers may then
    | be assigned to any extra authentication guards you have defined.
    |
    | Supported: "database", "eloquent"
    |
    */

    'providers' => [
        'users' => [
            'driver' => 'eloquent',
            'model' => env('AUTH_MODEL', User::class),
        ],

        'customers' => [
            'driver' => 'eloquent',
            'model' => CustomerAccount::class,
        ],

        'riders' => [
            'driver' => 'eloquent',
            'model' => Rider::class,
        ],

        'platform_admins' => [
            'driver' => 'eloquent',
            'model' => PlatformAdmin::class,
        ],

        // 'users' => [
        //     'driver' => 'database',
        //     'table' => 'users',
        // ],
    ],

    /*
    |--------------------------------------------------------------------------
    | Resetting Passwords
    |--------------------------------------------------------------------------
    |
    | These configuration options specify the behavior of Laravel's password
    | reset functionality, including the table utilized for token storage
    | and the user provider that is invoked to actually retrieve users.
    |
    | The expiry time is the number of minutes that each reset token will be
    | considered valid. This security feature keeps tokens short-lived so
    | they have less time to be guessed. You may change this as needed.
    |
    | The throttle setting is the number of seconds a user must wait before
    | generating more password reset tokens. This prevents the user from
    | quickly generating a very large amount of password reset tokens.
    |
    */

    'passwords' => [
        'users' => [
            'provider' => 'users',
            'table' => env('AUTH_PASSWORD_RESET_TOKEN_TABLE', 'password_reset_tokens'),
            'expire' => 60,
            'throttle' => 60,
        ],

        // Its own token table: the shared one is keyed by email alone, and a
        // shop owner and a shopper using the same address would overwrite each
        // other's reset token.
        'customers' => [
            'provider' => 'customers',
            'table' => 'customer_password_reset_tokens',
            'expire' => 60,
            'throttle' => 60,
        ],
    ],

    /*
    |--------------------------------------------------------------------------
    | Password Confirmation Timeout
    |--------------------------------------------------------------------------
    |
    | Here you may define the number of seconds before a password confirmation
    | window expires and users are asked to re-enter their password via the
    | confirmation screen. By default, the timeout lasts for three hours.
    |
    */

    'password_timeout' => env('AUTH_PASSWORD_TIMEOUT', 10800),

];
