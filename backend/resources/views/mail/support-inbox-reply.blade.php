@php
    $sans = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
@endphp

<x-mail.layout
    :title="'Reply from Omaykan support'"
    :preview="'A member of the Omaykan support team replied to your message.'"
>
    <h1 style="font-family:{{ $sans }}; font-size:20px; line-height:26px; font-weight:700; letter-spacing:-0.3px; margin:0 0 12px; color:#1a1a1a;">
        Reply from Omaykan support
    </h1>

    <div style="font-family:{{ $sans }}; font-size:15px; line-height:24px; color:#1a1a1a; white-space:pre-line; margin:0;">
        {{ $messageBody }}
    </div>
</x-mail.layout>
