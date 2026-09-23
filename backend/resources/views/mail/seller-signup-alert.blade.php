@php
    $sans = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    $cell = "padding:8px 0; font-family:{$sans}; font-size:14px; line-height:21px; color:#1a1a1a; border-bottom:1px solid #e3e9e5;";
    $label = "padding:8px 16px 8px 0; font-family:{$sans}; font-size:14px; line-height:21px; color:#5b6b62; border-bottom:1px solid #e3e9e5; white-space:nowrap; vertical-align:top;";
@endphp

<x-mail.layout
    :title="'New Omaykan signup'"
    :preview="$store->name . ' just signed up — subscription is pending.'"
    :support="false"
>

    <h1 style="font-family:{{ $sans }}; font-size:20px; line-height:26px; font-weight:700; letter-spacing:-0.3px; margin:0 0 6px; color:#1a1a1a;">
        {{ $store->name }} just signed up
    </h1>
    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 20px; color:#5b6b62;">
        The subscription is pending until it is verified in the platform admin.
    </p>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        <tr>
            <td style="{{ $label }}">Business</td>
            <td style="{{ $cell }}">{{ $store->name }}</td>
        </tr>
        <tr>
            <td style="{{ $label }}">Type</td>
            <td style="{{ $cell }}">{{ $store->business_mode }}</td>
        </tr>
        <tr>
            <td style="{{ $label }}">Owner</td>
            <td style="{{ $cell }}">{{ $owner->name }}</td>
        </tr>
        <tr>
            <td style="{{ $label }}">Email</td>
            <td style="{{ $cell }}"><a href="mailto:{{ $owner->email }}" style="color:#1a6b3c;">{{ $owner->email }}</a></td>
        </tr>
        <tr>
            <td style="{{ $label }}">Username</td>
            <td style="{{ $cell }}">{{ $owner->username }}</td>
        </tr>
        {{-- The slug is the handle everything else is looked up by, and it is
             public anyway — it is in every storefront URL. --}}
        <tr>
            <td style="{{ $label }}">Org slug</td>
            <td style="{{ $cell }}">{{ $organization->slug }}</td>
        </tr>
        <tr>
            <td style="{{ $label }}">Signed up</td>
            <td style="{{ $cell }}">{{ $owner->created_at?->timezone($store->timezone)->format('j M Y, g:ia') }} ({{ $store->timezone }})</td>
        </tr>
    </table>

    <x-slot:footnote>
        <p style="margin:0 0 6px;">
            Replying to this reaches {{ $owner->name }} directly.
        </p>
    </x-slot:footnote>

</x-mail.layout>
