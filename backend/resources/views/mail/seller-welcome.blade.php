@php
    $sans = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
@endphp

<x-mail.layout
    :title="'Your Omaykan store is ready'"
    :preview="$store->name . ' is set up and ready to take orders.'"
>

    <h1 style="font-family:{{ $sans }}; font-size:22px; line-height:28px; font-weight:700; letter-spacing:-0.3px; margin:0 0 14px; color:#1a1a1a;">
        Your store is ready, {{ $firstName }}.
    </h1>

    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 20px; color:#1a1a1a;">
        {{ $store->name }} is set up on Omaykan. You can open the register on any
        device, add your products, and start ringing up sales right away — no
        commission on anything you sell.
    </p>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px; background-color:#f4f6f4; border-radius:12px;">
        <tr>
            <td style="padding:16px 18px; font-family:{{ $sans }}; font-size:14px; line-height:22px; color:#1a1a1a;">
                <strong style="color:#5b6b62; font-weight:600;">Business</strong><br>
                {{ $store->name }} &middot; {{ $businessTypeLabel }}
                <br><br>
                <strong style="color:#5b6b62; font-weight:600;">Sign in as</strong><br>
                {{ $owner->username }}
            </td>
        </tr>
    </table>

    {{-- Bulletproof-ish button: a table cell with a background, so it renders
         as a button in clients that drop CSS on anchors. --}}
    <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px;">
        <tr>
            <td align="center" bgcolor="#1a6b3c" style="border-radius:999px;">
                <a href="{{ $appUrl }}" style="display:inline-block; padding:13px 28px; font-family:{{ $sans }}; font-size:15px; font-weight:600; color:#ffffff; text-decoration:none; border-radius:999px;">
                    Open your register
                </a>
            </td>
        </tr>
    </table>

    <h2 style="font-family:{{ $sans }}; font-size:15px; line-height:22px; font-weight:600; margin:0 0 8px; color:#1a1a1a;">
        Getting customers to your shop
    </h2>
    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 8px; color:#1a1a1a;">
        Your store has a short code that customers type into the Omaykan app to
        find you. It is in the register under <strong>Settings &rsaquo; Online
        Store</strong>, alongside the link you can post anywhere.
    </p>
    {{-- The code doubles as the till pairing secret until it is rotated, so it
         is shown in the app rather than mailed. See SellerWelcomeMail. --}}
    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0; color:#5b6b62;">
        Keep it to yourself until you are open — the same code is what pairs a
        new till to your store.
    </p>

    <x-slot:footnote>
        <p style="margin:0 0 6px;">
            You are getting this because {{ $store->name }} was signed up on Omaykan
            with this address.
        </p>
    </x-slot:footnote>

</x-mail.layout>
