@php
    $sans = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    $cell = "padding:8px 0; font-family:{$sans}; font-size:14px; line-height:21px; color:#1a1a1a; border-bottom:1px solid #e3e9e5;";
    $label = "padding:8px 16px 8px 0; font-family:{$sans}; font-size:14px; line-height:21px; color:#5b6b62; border-bottom:1px solid #e3e9e5; white-space:nowrap; vertical-align:top;";
@endphp

<x-mail.layout
    :title="'New seller application'"
    :preview="$application->business_name . ' applied — ' . $remaining . ' of ' . $limit . ' founding places left.'"
    :support="false"
>

    <h1 style="font-family:{{ $sans }}; font-size:20px; line-height:26px; font-weight:700; letter-spacing:-0.3px; margin:0 0 6px; color:#1a1a1a;">
        {{ $application->business_name }} wants to join
    </h1>
    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 20px; color:#5b6b62;">
        @if ($application->wants_founding)
            Asked for a founding-seller place. {{ $remaining }} of {{ $limit }} still unissued.
        @else
            Did not tick the founding-seller box — a normal setup.
        @endif
    </p>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        <tr>
            <td style="{{ $label }}">Business</td>
            <td style="{{ $cell }}">{{ $application->business_name }}</td>
        </tr>
        <tr>
            <td style="{{ $label }}">Category</td>
            <td style="{{ $cell }}">{{ $application->business_category }}</td>
        </tr>
        <tr>
            <td style="{{ $label }}">Owner</td>
            <td style="{{ $cell }}">{{ $application->owner_name }}</td>
        </tr>
        <tr>
            <td style="{{ $label }}">Mobile</td>
            {{-- A tel: link, because this is answered by ringing them back. --}}
            <td style="{{ $cell }}"><a href="tel:{{ preg_replace('/[^0-9+]/', '', $application->mobile) }}" style="color:#1a6b3c;">{{ $application->mobile }}</a></td>
        </tr>
        <tr>
            <td style="{{ $label }}">Email</td>
            <td style="{{ $cell }}"><a href="mailto:{{ $application->email }}" style="color:#1a6b3c;">{{ $application->email }}</a></td>
        </tr>
        @if ($application->social_url)
            <tr>
                <td style="{{ $label }}">Social</td>
                {{-- Printed, not linked: the form accepts "@handle" as readily as
                     a URL, and half of these are not addresses at all. --}}
                <td style="{{ $cell }}">{{ $application->social_url }}</td>
            </tr>
        @endif
        <tr>
            <td style="{{ $label }}">Address</td>
            <td style="{{ $cell }}">{{ $application->address }}</td>
        </tr>
        <tr>
            <td style="{{ $label }}">Delivers</td>
            <td style="{{ $cell }}">{{ $application->offers_delivery ? 'Yes, already' : 'Not yet' }}</td>
        </tr>
        <tr>
            <td style="{{ $label }}">Applied</td>
            <td style="{{ $cell }}">{{ $application->created_at?->timezone('Asia/Manila')->format('j M Y, g:ia') }} (Asia/Manila)</td>
        </tr>
    </table>

    <h2 style="font-family:{{ $sans }}; font-size:15px; line-height:22px; font-weight:600; margin:24px 0 8px; color:#1a1a1a;">
        What they sell
    </h2>
    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0; color:#1a1a1a; white-space:pre-line;">{{ $application->products_description }}</p>

    <x-slot:footnote>
        <p style="margin:0 0 6px;">
            Replying to this reaches {{ $application->owner_name }} directly.
        </p>
        <p style="margin:0;">
            The founding number is issued when the application is accepted, not now.
        </p>
    </x-slot:footnote>

</x-mail.layout>
