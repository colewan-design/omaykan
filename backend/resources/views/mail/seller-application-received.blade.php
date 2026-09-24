@php
    $sans = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
@endphp

<x-mail.layout
    :title="'We got your Omaykan application'"
    :preview="'Your application for ' . $application->business_name . ' is with our team.'"
>

    <h1 style="font-family:{{ $sans }}; font-size:22px; line-height:28px; font-weight:700; letter-spacing:-0.3px; margin:0 0 14px; color:#1a1a1a;">
        Thanks, {{ $firstName }} — we have it.
    </h1>

    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 20px; color:#1a1a1a;">
        Your application for <strong>{{ $application->business_name }}</strong> is with
        our team. Someone will call or message you on
        {{ $application->mobile }} to go through the details, and we build your
        online store for you from there — there is nothing else for you to set up.
    </p>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px; background-color:#f4f6f4; border-radius:12px;">
        <tr>
            <td style="padding:16px 18px; font-family:{{ $sans }}; font-size:14px; line-height:22px; color:#1a1a1a;">
                <strong style="color:#5b6b62; font-weight:600;">Business</strong><br>
                {{ $application->business_name }} &middot; {{ $application->business_category }}
                <br><br>
                <strong style="color:#5b6b62; font-weight:600;">Where</strong><br>
                {{ $application->address }}
            </td>
        </tr>
    </table>

    @if ($application->wants_founding)
        {{-- Only for the people who ticked the box, and phrased as a place in a
             queue rather than a number. The badge is assigned when an operator
             accepts the application; nothing here can promise one. --}}
        <h2 style="font-family:{{ $sans }}; font-size:15px; line-height:22px; font-weight:600; margin:0 0 8px; color:#1a1a1a;">
            About the founding-seller place
        </h2>
        <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 20px; color:#1a1a1a;">
            You asked to be one of the first 30 businesses in Baguio and
            La&nbsp;Trinidad. We will confirm your founding-seller number when
            your store is set up — it is yours permanently once it is issued.
        </p>
    @endif

    <h2 style="font-family:{{ $sans }}; font-size:15px; line-height:22px; font-weight:600; margin:0 0 8px; color:#1a1a1a;">
        What it costs
    </h2>
    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 8px; color:#1a1a1a;">
        Nothing to set up, and no commission on what you sell. The first 2&ndash;3
        months are free, and it is &#8369;199 a month after that.
    </p>
    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0; color:#5b6b62;">
        If anything changes about your business before we speak, just reply to
        this email.
    </p>

    <x-slot:footnote>
        <p style="margin:0 0 6px;">
            You are getting this because {{ $application->business_name }} was
            submitted to Omaykan with this address.
        </p>
    </x-slot:footnote>

</x-mail.layout>
