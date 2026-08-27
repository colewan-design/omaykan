@php
    $sans = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
@endphp

<x-mail.layout
    :title="$store?->name ? 'Message about ' . $store->name : 'Message from Omaykan'"
    :preview="'A member of the Omaykan team sent you a message.'"
>
    <h1 style="font-family:{{ $sans }}; font-size:20px; line-height:26px; font-weight:700; letter-spacing:-0.3px; margin:0 0 12px; color:#1a1a1a;">
        Hi {{ $recipient->name ?: $recipient->username }},
    </h1>

    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 20px; color:#5b6b62;">
        A member of the Omaykan team sent this from the platform admin portal{{ $store?->name ? ' about ' . $store->name : '' }}.
    </p>

    <div style="font-family:{{ $sans }}; font-size:15px; line-height:24px; color:#1a1a1a; white-space:pre-line; margin:0 0 20px;">
        {{ $messageBody }}
    </div>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-top:1px solid #e3e9e5; padding-top:16px;">
        <tr>
            <td style="padding-top:16px; font-family:{{ $sans }}; font-size:13px; line-height:20px; color:#5b6b62;">
                Organization: {{ $organization->name }}<br>
                @if ($store?->name)
                    Store: {{ $store->name }}<br>
                @endif
                Username: {{ $recipient->username }}
            </td>
        </tr>
    </table>
</x-mail.layout>
