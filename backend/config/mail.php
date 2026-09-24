<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Default Mailer
    |--------------------------------------------------------------------------
    |
    | This option controls the default mailer that is used to send all email
    | messages unless another mailer is explicitly specified when sending
    | the message. All additional mailers can be configured within the
    | "mailers" array. Examples of each type of mailer are provided.
    |
    */

    'default' => env('MAIL_MAILER', 'log'),

    /*
    |--------------------------------------------------------------------------
    | Mailer Configurations
    |--------------------------------------------------------------------------
    |
    | Here you may configure all of the mailers used by your application plus
    | their respective settings. Several examples have been configured for
    | you and you are free to add your own as your application requires.
    |
    | Laravel supports a variety of mail "transport" drivers that can be used
    | when delivering an email. You may specify which one you're using for
    | your mailers below. You may also add additional mailers if needed.
    |
    | Supported: "smtp", "sendmail", "mailgun", "ses", "ses-v2",
    |            "postmark", "resend", "log", "array",
    |            "failover", "roundrobin"
    |
    */

    'mailers' => [

        'smtp' => [
            'transport' => 'smtp',
            'scheme' => env('MAIL_SCHEME'),
            'url' => env('MAIL_URL'),
            'host' => env('MAIL_HOST', '127.0.0.1'),
            'port' => env('MAIL_PORT', 2525),
            'username' => env('MAIL_USERNAME'),
            'password' => env('MAIL_PASSWORD'),
            'timeout' => null,
            'local_domain' => env('MAIL_EHLO_DOMAIN', parse_url((string) env('APP_URL', 'http://localhost'), PHP_URL_HOST)),
        ],

        'ses' => [
            'transport' => 'ses',
        ],

        'postmark' => [
            'transport' => 'postmark',
            // 'message_stream_id' => env('POSTMARK_MESSAGE_STREAM_ID'),
            // 'client' => [
            //     'timeout' => 5,
            // ],
        ],

        'resend' => [
            'transport' => 'resend',
        ],

        'sendmail' => [
            'transport' => 'sendmail',
            'path' => env('MAIL_SENDMAIL_PATH', '/usr/sbin/sendmail -bs -i'),
        ],

        'log' => [
            'transport' => 'log',
            'channel' => env('MAIL_LOG_CHANNEL'),
        ],

        'array' => [
            'transport' => 'array',
        ],

        'failover' => [
            'transport' => 'failover',
            'mailers' => [
                'smtp',
                'log',
            ],
            'retry_after' => 60,
        ],

        'roundrobin' => [
            'transport' => 'roundrobin',
            'mailers' => [
                'ses',
                'postmark',
            ],
            'retry_after' => 60,
        ],

    ],

    /*
    |--------------------------------------------------------------------------
    | Global "From" Address
    |--------------------------------------------------------------------------
    |
    | You may wish for all emails sent by your application to be sent from
    | the same address. Here you may specify a name and address that is
    | used globally for all emails that are sent by your application.
    |
    */

    'from' => [
        // The sending identity: the mailbox that actually holds the SMTP
        // credentials above. Not the same thing as the address customers are
        // told to write to — that one is SUPPORT_EMAIL in
        // packages/shared/src/index.ts, and it is where replies go instead;
        // see 'reply_to' below. Splitting the two means the mailbox we send
        // through can change without moving the address printed all over the
        // app and the marketing site. They are the same mailbox today — the
        // default matches production, so an environment that sets only the
        // SMTP credentials sends as the account it authenticates with rather
        // than as an address the host will refuse.
        'address' => env('MAIL_FROM_ADDRESS', 'support@omaykan.com'),
        'name' => env('MAIL_FROM_NAME', env('APP_NAME', 'Omaykan')),
    ],

    /*
    |--------------------------------------------------------------------------
    | Global "Reply-To" Address
    |--------------------------------------------------------------------------
    |
    | Laravel has no global reply-to, so App\Mail\OmaykanMailable applies this
    | one to every message the app sends. Keeping it out of a noreply void is
    | the point: a reply should land somewhere a person reads.
    |
    | Set MAIL_REPLY_TO_ADDRESS empty to send no Reply-To header at all, which
    | makes replies go to the 'from' mailbox.
    |
    */

    'reply_to' => [
        'address' => env('MAIL_REPLY_TO_ADDRESS', 'support@omaykan.com'),
        'name' => env('MAIL_REPLY_TO_NAME', env('APP_NAME', 'Omaykan')),
    ],

    /*
    |--------------------------------------------------------------------------
    | Operations Inbox
    |--------------------------------------------------------------------------
    |
    | Where "a merchant just signed up" alerts land. Its own key rather than a
    | reuse of 'from' so the alerts can be pointed at a shared ops mailbox
    | later without changing what a customer sees in the From line.
    |
    | Empty disables the alerts; the merchant's own welcome mail still sends.
    |
    */

    'alerts_to' => env('MAIL_ALERTS_ADDRESS', 'support@omaykan.com'),

];
