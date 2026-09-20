@php
    $sans = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    // The three notices, as a table rather than three branches of markup: the
    // shell around them is identical and only the words change. Keeping them
    // adjacent is also the easiest way to check they tell one consistent story
    // when read in sequence, which is how a merchant will read them.
    $copy = [
        'renewal_due' => [
            'heading' => 'Your subscription renews soon',
            'lead' => 'Nothing is wrong — this is just a heads-up so the renewal is not a surprise.',
            'tone' => '#5b6b62',
        ],
        'past_due' => [
            'heading' => 'Your subscription needs renewing',
            'lead' => 'Your shop is still open and still taking orders. We have not changed anything.',
            'tone' => '#8a6d1f',
        ],
        'final_notice' => [
            'heading' => 'Your shop stops taking orders soon',
            'lead' => 'This is the last reminder before your shop stops accepting new orders.',
            'tone' => '#9c3328',
        ],
    ][$stage] ?? [
        'heading' => 'Your Omaykan subscription',
        'lead' => 'There is something to sort out with your subscription.',
        'tone' => '#5b6b62',
    ];
@endphp

<x-mail.layout
    :title="$copy['heading']"
    :preview="$copy['heading'] . ' — ' . $shopName"
>

    <h1 style="font-family:{{ $sans }}; font-size:22px; line-height:28px; font-weight:700; letter-spacing:-0.3px; margin:0 0 14px; color:#1a1a1a;">
        {{ $copy['heading'] }}, {{ $firstName }}.
    </h1>

    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 20px; color:#1a1a1a;">
        {{ $copy['lead'] }}
    </p>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 24px; background-color:#f4f6f4; border-radius:12px;">
        <tr>
            <td style="padding:16px 18px; font-family:{{ $sans }}; font-size:14px; line-height:22px; color:#1a1a1a;">
                <strong style="color:#5b6b62; font-weight:600;">Shop</strong><br>
                {{ $shopName }}
                <br><br>
                <strong style="color:#5b6b62; font-weight:600;">Monthly subscription</strong><br>
                &#8369;{{ $amount }}
                @if ($periodEndsAt)
                    <br><br>
                    <strong style="color:#5b6b62; font-weight:600;">
                        {{ $stage === 'renewal_due' ? 'Renews on' : 'Ran out on' }}
                    </strong><br>
                    {{ $periodEndsAt->format('j F Y') }}
                @endif
            </td>
        </tr>
    </table>

    @if ($stage !== 'renewal_due' && $graceEndsAt)
        <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 20px; color:{{ $copy['tone'] }};">
            @if ($stage === 'final_notice')
                <strong>{{ $shopName }} stops accepting new orders on {{ $graceEndsAt->format('j F Y') }}.</strong>
                Your sales history, products and staff are not affected, and everything
                comes straight back when the subscription is renewed.
            @else
                Your shop keeps trading as normal until
                <strong>{{ $graceEndsAt->format('j F Y') }}</strong>. Renewing before then
                means nothing changes at all.
            @endif
        </p>
    @endif

    {{-- No payment button, on purpose: there is no gateway and none planned
         (§6.3), so a link that looked like one would lead somewhere that
         cannot take the money. The path really is an email to a person. --}}
    <h2 style="font-family:{{ $sans }}; font-size:15px; line-height:22px; font-weight:600; margin:0 0 8px; color:#1a1a1a;">
        How to renew
    </h2>
    <p style="font-family:{{ $sans }}; font-size:15px; line-height:23px; margin:0 0 8px; color:#1a1a1a;">
        Reply to this email or write to
        <a href="mailto:{{ $supportEmail }}" style="color:#1a6b3c;">{{ $supportEmail }}</a>
        and we will sort it out with you directly. If you have already paid,
        ignore this — it can take us a day to match a transfer to a shop.
    </p>

    <x-slot:footnote>
        <p style="margin:0 0 6px;">
            You are getting this because you are the owner of {{ $shopName }} on Omaykan.
        </p>
    </x-slot:footnote>

</x-mail.layout>
