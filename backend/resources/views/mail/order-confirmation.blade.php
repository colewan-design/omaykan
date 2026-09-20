@php
    $sans = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    $lineCell = "padding:10px 0; font-family:{$sans}; font-size:14px; line-height:21px; color:#1a1a1a; border-bottom:1px solid #e3e9e5;";
    $totalLabel = "padding:6px 0; font-family:{$sans}; font-size:14px; line-height:21px; color:#5b6b62;";
    $totalValue = "padding:6px 0; font-family:{$sans}; font-size:14px; line-height:21px; color:#1a1a1a; text-align:right;";
@endphp

<x-mail.layout
    :title="'Order ' . $order->ticket_number . ' confirmed'"
    :preview="'Order ' . $order->ticket_number . ' at ' . $store->name . ' — ' . $total"
>

    <h1 style="font-family:{{ $sans }}; font-size:22px; line-height:28px; font-weight:700; letter-spacing:-0.3px; margin:0 0 14px; color:#1a1a1a;">
        Thanks, {{ $customerName }} — {{ $store->name }} has your order.
    </h1>

    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 20px; color:#1a1a1a;">
        @if ($isDelivery)
            It is being prepared now, and you will see the rider's progress on the
            tracking page as it moves.
        @else
            It is being prepared now. Give the counter your order number when you
            arrive.
        @endif
    </p>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px; background-color:#f4f6f4; border-radius:12px;">
        <tr>
            <td style="padding:16px 18px; font-family:{{ $sans }}; font-size:14px; line-height:22px; color:#5b6b62;">
                Order number
                <div style="font-size:24px; line-height:30px; font-weight:700; letter-spacing:1px; color:#1a6b3c; padding-top:2px;">
                    {{ $order->ticket_number }}
                </div>
            </td>
        </tr>
    </table>

    <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px;">
        <tr>
            <td align="center" bgcolor="#1a6b3c" style="border-radius:999px;">
                <a href="{{ $trackUrl }}" style="display:inline-block; padding:13px 28px; font-family:{{ $sans }}; font-size:15px; font-weight:600; color:#ffffff; text-decoration:none; border-radius:999px;">
                    Track this order
                </a>
            </td>
        </tr>
    </table>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 16px;">
        @foreach ($lines as $line)
            <tr>
                <td style="{{ $lineCell }}">
                    {{ $line['name'] }}
                    <span style="color:#5b6b62;">&times; {{ $line['quantity'] }}</span>
                </td>
                <td style="{{ $lineCell }} text-align:right; white-space:nowrap;">
                    {{ $line['lineTotal'] }}
                </td>
            </tr>
        @endforeach
    </table>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        <tr>
            <td style="{{ $totalLabel }}">Subtotal</td>
            <td style="{{ $totalValue }}">{{ $subtotal }}</td>
        </tr>
        @if ($discount)
            <tr>
                <td style="{{ $totalLabel }}">{{ $discountLabel }}</td>
                <td style="{{ $totalValue }}">{{ $discount }}</td>
            </tr>
        @endif
        @if ($order->tax_cents > 0)
            <tr>
                <td style="{{ $totalLabel }}">Tax</td>
                <td style="{{ $totalValue }}">{{ $tax }}</td>
            </tr>
        @endif
        @if ($isDelivery)
            <tr>
                <td style="{{ $totalLabel }}">Delivery</td>
                <td style="{{ $totalValue }}">{{ $deliveryFee }}</td>
            </tr>
        @endif
        <tr>
            <td style="{{ $totalLabel }} padding-top:12px; font-size:16px; font-weight:700; color:#1a1a1a;">Total</td>
            <td style="{{ $totalValue }} padding-top:12px; font-size:16px; font-weight:700;">{{ $total }}</td>
        </tr>
    </table>

    @if ($isDelivery && $order->delivery_address)
        <p style="font-family:{{ $sans }}; font-size:14px; line-height:21px; margin:20px 0 0; color:#5b6b62;">
            Delivering to<br>
            <span style="color:#1a1a1a;">{{ $order->delivery_address }}</span>
        </p>
    @endif

    <p style="font-family:{{ $sans }}; font-size:14px; line-height:21px; margin:20px 0 0; color:#5b6b62;">
        Paying by {{ $order->payment_method === 'ewallet' ? 'e-wallet' : 'cash' }}
        {{ $isDelivery ? 'on delivery' : 'at the counter' }}.
    </p>

    <x-slot:footnote>
        <p style="margin:0 0 6px;">
            Sent because this address was given at checkout. Omaykan charges
            {{ $store->name }} no commission on this order.
        </p>
    </x-slot:footnote>

</x-mail.layout>
