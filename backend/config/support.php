<?php

/*
|--------------------------------------------------------------------------
| Who a person in trouble can reach
|--------------------------------------------------------------------------
|
| Read by the rider app, which is the only surface where this is urgent
| rather than administrative: a rider on a road at night, holding somebody
| else's food and somebody else's cash, currently has no way to reach anyone
| from inside the app.
|
| The email mirrors SUPPORT_EMAIL in packages/shared/src/index.ts, which
| remains the canonical copy for everything web-side — this is the same
| address, readable from PHP, not a second opinion about what it is.
|
| The phone has **no default on purpose**. A hard-coded placeholder would
| ship a dead number to the one screen where a dead number is worst; with
| this null the app hides the call button entirely and offers the email
| instead. Set SUPPORT_PHONE in the environment to turn it on.
|
*/

return [
    'email' => env('SUPPORT_EMAIL', 'support@omaykan.com'),

    'phone' => env('SUPPORT_PHONE'),

    /*
     * Free text shown under the buttons. Kept here rather than in the app so
     * that "we answer 8am-10pm" can change without a Play Store release.
     */
    'hours' => env('SUPPORT_HOURS'),
];
