<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Third Party Services
    |--------------------------------------------------------------------------
    |
    | This file is for storing the credentials for third party services such
    | as Mailgun, Postmark, AWS and more. This file provides the de facto
    | location for this type of information, allowing packages to have
    | a conventional file to locate the various service credentials.
    |
    */

    'postmark' => [
        'key' => env('POSTMARK_API_KEY'),
    ],

    'resend' => [
        'key' => env('RESEND_API_KEY'),
    ],

    'ses' => [
        'key' => env('AWS_ACCESS_KEY_ID'),
        'secret' => env('AWS_SECRET_ACCESS_KEY'),
        'region' => env('AWS_DEFAULT_REGION', 'us-east-1'),
    ],

    /*
     * Sign in with Google, for the storefront and the Android app.
     *
     * Two client ids because the Google console insists on two clients: a Web
     * client for the storefront button, and an Android client keyed on the
     * package name and signing certificate for the app. A token carries the id
     * of the client that minted it in `aud`, and GoogleIdentityVerifier accepts
     * either — but only these, which is what stops a token minted for some
     * other app being replayed here.
     *
     * `extra_client_ids` is the debug build's escape hatch: the debug APK has a
     * different applicationId and a different signing certificate, so it is a
     * third client in the console. Comma-separated, empty in production.
     *
     * There is no client secret. Neither flow needs one — the web button
     * returns a signed assertion rather than a code, and the app is a public
     * client using PKCE, which is what protects the exchange instead.
     */
    'google' => [
        'client_id' => env('GOOGLE_CLIENT_ID'),
        'android_client_id' => env('GOOGLE_ANDROID_CLIENT_ID'),
        'extra_client_ids' => array_values(array_filter(array_map(
            'trim',
            explode(',', (string) env('GOOGLE_EXTRA_CLIENT_IDS', '')),
        ))),
    ],

    /*
     * The Android customer app, as this domain vouches for it.
     *
     * Only what the Digital Asset Links file needs: the package name and the
     * SHA-256 fingerprints of the certificates it may be signed with. Two are
     * normal — debug and release are different apps to Android — and both can
     * be read off a keystore with `keytool -list -v -keystore <file>`.
     *
     * Empty is a supported state: the reset link still works, Android just
     * asks which app should open it. See AssetLinksController.
     */
    'android' => [
        'package' => env('ANDROID_APP_PACKAGE', 'com.omaykan.storefront'),
        'sha256_fingerprints' => array_values(array_filter(array_map(
            'trim',
            explode(',', (string) env('ANDROID_APP_SHA256_FINGERPRINTS', '')),
        ))),
    ],

    'slack' => [
        'notifications' => [
            'bot_user_oauth_token' => env('SLACK_BOT_USER_OAUTH_TOKEN'),
            'channel' => env('SLACK_BOT_USER_DEFAULT_CHANNEL'),
        ],
    ],

    /*
     * Firebase Cloud Messaging, for the storefront app's order notifications:
     * the path to a service-account key file (Firebase console → Project
     * settings → Service accounts). Kept out of the repo. Blank sends no push.
     */
    'firebase' => [
        'credentials' => env('FIREBASE_CREDENTIALS'),
    ],

    /*
     * Operator dashboard mailbox access. This is the inbox behind
     * support@omaykan.com, read over IMAP so the portal can show incoming
     * messages and reply to them.
     */
    'support_inbox' => [
        'host' => env('SUPPORT_INBOX_HOST'),
        'port' => env('SUPPORT_INBOX_PORT', 993),
        'username' => env('SUPPORT_INBOX_USERNAME'),
        'password' => env('SUPPORT_INBOX_PASSWORD'),
        'mailbox' => env('SUPPORT_INBOX_MAILBOX', 'INBOX'),
        'encryption' => env('SUPPORT_INBOX_ENCRYPTION', 'ssl'),
        'validate_cert' => env('SUPPORT_INBOX_VALIDATE_CERT', true),
    ],

];
