# Rider UI refresh

Reference: `C:/Users/ASUS/Downloads/da4bbeb4d00d6c5cf36fcb4a92602a24.webp`.
The built-in image generation tool produced `concept.png` before implementation.

## Design brief

Create a native Android Omaykan rider concept with complete Home and Your account screens. Use fresh green curved headers, white bodies, delicate borders, compact rounded green actions, and a white bottom bar. Preserve the rider greeting, plate, shift status, earned fees, cash to collect, record, recent deliveries, and existing four tabs. Account has a centered initial avatar, actual name/email, menu rows for Edit profile, Account information, Change password, and Sign out. No invented account features, fake photographs, or backend metrics.

## Implementation

- Flat #258348 header with a shallow curved bottom, light status-bar icons, white light-mode background, and 12dp cards.
- Pale green earnings and pale amber cash cards keep the financial meanings distinct; dark mode keeps deeper grounds and light text.
- Profile overview opens the existing forms. Save state, validation, API calls, field restrictions, and password behavior remain in the original view model.
- Both Android Back and the panel back control return to the profile overview.
- Home and account content scroll, with navigation kept outside the scroll area.
- All four destinations have visible labels and outlined inactive icons. Record cards use compact horizontal rows.
- Money cards stack below 340dp screen width or above 1.15 font scale; longer account panel headings reserve space for Back.
- Existing uncommitted rider work was retained.

## Intentional adaptations from the concept

The user supplied an inspiration image, not a pixel-for-pixel clone request. Existing rider labels, initial avatar, location sharing, financial explanations, and delivery data take precedence over invented mockup details. Home keeps its existing greeting rather than adding a second brand heading. Native Material icons replace the concept's drawn icons. Green is deeper than the supplied reference to keep small white text legible. No customer shopping features were added.

## Verification

Native Android emulator and ADB/UI Automator are used because this is a Kotlin/Compose app, not a browser surface. The debug account preview renders the production AccountScreen with a fixture profile; the home gallery renders the production HomeContent, sharing control, and tab bar with fixture data. Preview actions do not represent authenticated backend verification.

Visually inspected the reference, generated concept, and native screenshots with view_image: header curve, palette, typography, card borders/radii, menu spacing/icons, primary action, and bottom navigation. Account menu copy matches the planned labels; home retains production copy as documented above.
