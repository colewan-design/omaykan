# Sign in with Google

One backend endpoint, two front ends, three OAuth clients in the Google
console. This is what to create, where each value goes, and the handful of
things that are easy to get wrong.

## What it is

`POST /api/customer/auth/google` takes one field — `credential`, a Google **ID
token** — and answers with the same `{account, token}` envelope
`/api/customer/login` returns. Both front ends produce that one artefact:

| Front end | How it gets an ID token |
|---|---|
| Web storefront | Google Identity Services button hands one straight to the browser |
| Android app | Custom Tabs authorization-code flow with PKCE, then exchanges the code at Google's token endpoint |

The server trusts none of it on arrival. `App\Services\GoogleIdentityVerifier`
checks the RS256 signature against Google's published certificates, that `iss`
is Google, that `aud` is one of *our* client ids, and that the token has not
expired — before anything is looked up. See
`backend/tests/Feature/Api/CustomerGoogleAuthApiTest.php`, which signs its own
tokens and exercises the refusals.

**Google sign-in sends no verification email.** Google has already proved the
shopper holds the mailbox, and the endpoint refuses any token whose
`email_verified` is not true. The mail that *is* sent — password-account
verification, password resets — comes from `MAIL_REPLY_TO_ADDRESS`, which is
`support@omaykan.com`; see `App\Notifications\CustomerEmailVerification`.

## The Google Cloud Console, once

Do this from the Google account that should own the project. It can be an
ordinary personal account — Google does not require a Workspace account to own
an OAuth app, and nothing about the consent screen shows the owner's address to
shoppers. What shoppers see is the **app name** and the **support email**, and
both of those are set below.

### 1. The project and the consent screen

1. Create a project (name it Omaykan).
2. **APIs & Services → OAuth consent screen**, User type **External**.
3. Fill in:
   - **App name**: `Omaykan` — this is the "to continue to Omaykan" line on the
     consent page and it is the only name the shopper reads.
   - **User support email**: `support@omaykan.com`
   - **Developer contact information**: `support@omaykan.com`
   - **App logo**: the Omaykan mark. A logo makes the screen look like it
     belongs to a business rather than to a stranger's side project.
   - **Application home page**: `https://omaykan.com`
   - **Authorised domain**: `omaykan.com`

   The support email is the one requirement worth being careful about. Google
   requires it to be an address the signed-in owner controls — either the
   account's own address or a Google Group they own. Since it must read
   `support@omaykan.com`, either sign in as that mailbox's Google account, or
   add it as a verified alternate on the owning account, or make a Google Group
   at that address with the owner as a manager. Any of the three satisfies
   Google and all three show the shopper the same thing.

4. **Scopes**: `openid`, `.../auth/userinfo.email`,
   `.../auth/userinfo.profile`. Nothing else. Those three are non-sensitive, so
   the app needs no Google verification review and no annual re-verification.
   Adding any Gmail, Drive or Contacts scope changes that immediately.

5. While the app is in **Testing**, only listed test users can sign in and
   tokens expire after seven days. **Publish** it before shipping to real
   shoppers; with only non-sensitive scopes, publishing is a button, not a
   review.

### 2. Three OAuth clients

**Credentials → Create credentials → OAuth client ID.** You need three,
because Google keys a web client on its origin and an Android client on the
package name *and* the signing certificate — so the debug APK is a different
app to Google than the release APK.

| # | Type | Settings |
|---|---|---|
| 1 | **Web application** | Authorised JavaScript origins: `https://omaykan.com`, plus `http://localhost:5173` for local dev. No redirect URI is needed — the button returns an assertion, not a code. |
| 2 | **Android** | Package name `com.omaykan.storefront`, SHA-1 of the **release** keystore |
| 3 | **Android** | Package name `com.omaykan.storefront.debug`, SHA-1 of the **debug** keystore |

Get the SHA-1 fingerprints with:

```bash
# release — the keystore named in apps/mobile-android/keystore.properties
keytool -list -v -keystore /path/to/omaykan-release.jks -alias <your alias>

# debug — the one Android Studio generates
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

There is **no client secret anywhere in this feature.** The web button returns
a signed assertion rather than a code, and the Android app is a public OAuth
client whose code exchange is protected by PKCE instead. Google does not issue
a secret for Android clients at all.

### 3. Where the values go

Backend — `backend/.env`:

```dotenv
GOOGLE_CLIENT_ID=<client 1, the Web client>
GOOGLE_ANDROID_CLIENT_ID=<client 2, release Android>
GOOGLE_EXTRA_CLIENT_IDS=<client 3, debug Android>   # empty in production
```

Web — `apps/web/.env.production` (and `.env` for local):

```dotenv
VITE_GOOGLE_CLIENT_ID=<client 1, the same Web client id>
```

Android — read at build time by `apps/mobile-android/app/build.gradle.kts`, from
the environment or from `~/.gradle/gradle.properties`, whichever has it (the
environment wins):

```bash
OMAYKAN_GOOGLE_ANDROID_CLIENT_ID=<client 2>          # release
OMAYKAN_GOOGLE_ANDROID_CLIENT_ID_DEBUG=<client 3>    # debug
```

**The debug variable was documented here from the start and never read.** Until
2026-09-08 `build.gradle.kts` set `GOOGLE_OAUTH_CLIENT_ID` once, in
`defaultConfig`, from the release variable alone — so the debug APK carried the
release client id and Google refused its token as issued for a different app.
The debug build type now overrides it, falling back to the release id when the
debug one is unset.

**Every one of these may be left blank, and blank is a supported state.** The
backend endpoint then refuses everything, the storefront ships no Google script
and shows no button, and the app's Account tab shows no button either. Email
and password sign-in on the web, and guest checkout everywhere, are unaffected.

## Account linking, and why it is safe

`CustomerAuthController::resolveGoogleAccount` matches in this order:

1. **By `google_sub`** — Google's stable subject id. Not by email: Google lets
   someone change the address on their account, and matching on email would
   eventually hand one shopper's order history to whoever inherits their old
   address.
2. **By email**, linking Google onto an existing password account. This is the
   dangerous case and it is gated: the endpoint refuses any token whose
   `email_verified` is false, so reaching this line means Google has vouched for
   the mailbox. That is the same claim as clicking the link in our own
   verification mail, made by the only other party able to check. The existing
   password keeps working; linking adds a door, it does not replace one.
3. **Neither** — a new account, verified, with `password` null.

### Accounts with no password

The `password` column is nullable now. Three places had to learn that:

- `POST /api/customer/login` tells a Google-only shopper to use the button
  rather than giving the neutral "email and password don't match", which they
  could never satisfy.
- `PATCH /api/customer/account/password` drops the `currentPassword`
  requirement when there is no current password — it is a *set*, not a change.
- `PATCH /api/customer/account/email` refuses outright for a Google-only
  account. Google holds that address; changing it here would be undone by the
  next sign-in, or worse, would stop matching and mint a second account.

The account payload carries `hasPassword` and `googleLinked` so a client can
tell which of these it is looking at.

## The Android flow, specifically

Custom Tabs and a hand-rolled PKCE exchange — **not** Credential Manager, not
the Google Identity SDK, no `google-services.json`, no Play Services. That
follows [mobile-plan.md](./mobile-plan.md) §3 and §8: the app depends on no part
of Play Services, so it works on a phone that has none, and there is no Firebase
hook for anyone to fall back into.

Not a WebView either. A WebView asking for a Google password is exactly what a
phishing page looks like; Google blocks the flow inside one, and the shopper
gets no address bar to check.

The pieces:

| File | Job |
|---|---|
| `core/auth/GoogleAuthFlow.kt` | Generates the PKCE verifier and `state`, opens the Custom Tab, reads the redirect |
| `core/auth/GoogleAuthRedirectActivity.kt` | Catches `com.omaykan.storefront:/oauth2redirect`, dismisses the tab, hands the URI over |
| `core/network/GoogleOAuthApi.kt` | Exchanges the code at Google for an ID token |
| `core/data/AccountRepository.kt` | Posts the ID token to our API, stores the session |
| `core/auth/CustomerTokenStore.kt` | The Sanctum token, in EncryptedSharedPreferences |
| `feature/account/AccountScreen.kt` | The Account tab |

Two things there are load-bearing and easy to break:

- **The redirect activity is exported.** Anything on the phone can fire an
  intent at that scheme. The `state` check in `readRedirect` is the whole
  defence, and `GoogleAuthRedirectTest` is what keeps it honest — an unsolicited
  redirect carrying a valid-looking code is refused.
- **A 401 from our API clears the stored token** (`CustomerAuthInterceptor`).
  Without that, a stale token turns `POST /api/online-orders` — which takes a
  bearer token as *optional* — into a 401, and a shopper who cannot sign in
  cannot order at all. Guest checkout must never depend on the session.

## Trying it locally

```bash
# backend
cd backend && php artisan migrate && php artisan serve

# web
cd apps/web && npm run dev     # http://localhost:5173, listed as an origin on client 1

# android, against the host loopback as the emulator sees it
cd apps/mobile-android
OMAYKAN_GOOGLE_ANDROID_CLIENT_ID_DEBUG=<client 3> ./gradlew installDebug
```

`http://10.0.2.2:8000` is fine for the app's API base — the OAuth redirect
never goes through it. Google only ever redirects to the custom scheme, which
is why this works with no public URL for the backend.

## Tests

```bash
cd backend && php artisan test --filter CustomerGoogleAuthApiTest
cd apps/mobile-android && ./gradlew :app:testDebugUnitTest
```
