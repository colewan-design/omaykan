@props(['title' => null, 'preview' => null, 'support' => true])

{{--
  The shell every Omaykan message renders inside.

  Written the way email has to be written rather than the way the app is: a
  table for layout and styles inlined on each element, because Outlook ignores
  flexbox and Gmail strips <style> blocks. The palette is the marketing green
  from .auth-page in packages/core/src/styles/app.css, hard-coded here since a
  mail client has no access to our custom properties.

  Pinned to the light scheme on purpose. Clients that auto-invert dark mode
  make a mess of a half-declared palette, and this is a brand surface — the
  same call the sign-in screen makes.
--}}
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="light">
    <meta name="supported-color-schemes" content="light">
    <title>{{ $title ?? config('app.name', 'Omaykan') }}</title>
</head>
<body style="margin:0; padding:0; width:100%; background-color:#f4f6f4; color:#1a1a1a; -webkit-font-smoothing:antialiased;">

@if ($preview)
    {{-- The line the inbox shows next to the subject. Hidden in the body itself. --}}
    <div style="display:none; max-height:0; overflow:hidden; opacity:0; color:transparent; height:0; width:0;">
        {{ $preview }}
    </div>
@endif

<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f6f4;">
    <tr>
        <td align="center" style="padding:32px 16px;">

            <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%; max-width:600px;">

                <tr>
                    <td style="padding:0 8px 20px;">
                        <span style="font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif; font-size:20px; font-weight:700; letter-spacing:-0.4px; color:#1a6b3c;">
                            Omaykan
                        </span>
                    </td>
                </tr>

                <tr>
                    <td style="background-color:#ffffff; border:1px solid #e3e9e5; border-radius:16px; padding:32px;">
                        {{ $slot }}
                    </td>
                </tr>

                <tr>
                    <td style="padding:20px 8px 0; font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif; font-size:12px; line-height:19px; color:#5b6b62;">
                        {{ $footnote ?? '' }}
                        @if ($support && config('mail.reply_to.address'))
                            <p style="margin:0;">
                                Questions? Just reply, or write to
                                <a href="mailto:{{ config('mail.reply_to.address') }}" style="color:#1a6b3c;">{{ config('mail.reply_to.address') }}</a>.
                            </p>
                        @endif
                    </td>
                </tr>

            </table>

        </td>
    </tr>
</table>

</body>
</html>
