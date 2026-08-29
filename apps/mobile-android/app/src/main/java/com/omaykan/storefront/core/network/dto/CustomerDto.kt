package com.omaykan.storefront.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The customer account endpoints.
 *
 * One envelope shape comes back from every one of them — sign-in, `me`, and
 * every profile mutation — which is why there is a single SessionDto and a
 * single CustomerAccountDto rather than one per call. The server does that on
 * purpose (see CustomerAccount::toStorefrontArray) so a client never has to
 * merge a partial update into what it already had.
 *
 * Addresses and payment methods are modelled now because checkout reads them:
 * a saved address carries the pin the server quotes the delivery fee from, and
 * this app has no geocoder, so it is the only way a phone order gets a real fee
 * instead of the shop's flat rate.
 */

/** POST /api/customer/login. */
@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

/**
 * POST /api/customer/register.
 *
 * `password_confirmation` is snake_case because Laravel's `confirmed` rule
 * looks for exactly that field name, and it is sent rather than collected: a
 * second password box on a phone keyboard is a way to mistype twice, and the
 * screen offers a reveal toggle instead.
 */
@Serializable
data class RegisterRequestDto(
    val name: String,
    val email: String,
    val phone: String? = null,
    val password: String,
    @SerialName("password_confirmation")
    val passwordConfirmation: String,
)

/**
 * The reply to register — and note what is *not* in it: a token.
 *
 * Registering does not sign anybody in. The server sends a verification link
 * and refuses `login` until it has been clicked, so the screen's next move is
 * to say "check your email", not to open the shop.
 */
@Serializable
data class RegistrationDto(
    val account: CustomerAccountDto,
    val verificationRequired: Boolean = true,
    val message: String = "",
)

/** POST /api/customer/forgot-password. Deliberately says the same thing either way. */
@Serializable
data class ForgotPasswordRequestDto(
    val email: String,
)

/**
 * POST /api/customer/reset-password — the emailed token, redeemed.
 *
 * Answers with a session rather than a message: someone who has just proved
 * they hold the mailbox should not be bounced back to a sign-in form. The
 * server also clears every other token on the account, so a reset signs the
 * shopper out of every device except the one that did it.
 */
@Serializable
data class ResetPasswordRequestDto(
    val token: String,
    val email: String,
    val password: String,
    @SerialName("password_confirmation")
    val passwordConfirmation: String,
)

@Serializable
data class MessageDto(
    val message: String = "",
)

/** POST /api/customer/auth/google — the ID token, and nothing else. */
@Serializable
data class GoogleSignInRequestDto(
    val credential: String,
)

/** The reply from any endpoint that mints a session. `token` is the bearer. */
@Serializable
data class SessionDto(
    val token: String,
    val account: CustomerAccountDto,
)

/** GET /api/customer/me and every profile write. */
@Serializable
data class AccountEnvelopeDto(
    val account: CustomerAccountDto,
)

@Serializable
data class CustomerAccountDto(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val avatarUrl: String = "",
    val googleLinked: Boolean = false,
    /**
     * False for an account that has only ever signed in with Google. It is what
     * the credentials screen decides between "change your password" and "set
     * one" on, and what makes the email field read-only.
     */
    val hasPassword: Boolean = false,
    val preferences: CustomerPreferencesDto = CustomerPreferencesDto(),
    /** Oldest first with the default lifted to the top — the server sorts. */
    val addresses: List<SavedAddressDto> = emptyList(),
    val paymentMethods: List<SavedPaymentMethodDto> = emptyList(),
)

/**
 * A delivery address the shopper has saved.
 *
 * `lat` and `lng` are the useful part. They are null for an address typed
 * without a pin, and present for one saved from a device location — and the
 * order endpoint rejects half a coordinate, so they travel together or not at
 * all.
 */
@Serializable
data class SavedAddressDto(
    val id: String,
    /** The shopper's own name for the place — "Home", "Mum's". */
    val label: String = "",
    val line1: String = "",
    val barangay: String = "",
    val city: String = "",
    /** Gate colour, landmark, which door. What the rider actually reads. */
    val notes: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val isDefault: Boolean = false,
)

@Serializable
data class SavedPaymentMethodDto(
    val id: String,
    /** "cash" or "ewallet" — the only two the order endpoint accepts. */
    val kind: String = "cash",
    /** The e-wallet number. Empty for cash, and nothing here charges it. */
    val detail: String = "",
    val isDefault: Boolean = false,
)

/**
 * Google's own token endpoint. Not our API.
 *
 * The app exchanges its authorization code here rather than posting the code to
 * our backend, so that only one kind of artefact ever reaches
 * /api/customer/auth/google: an ID token, exactly like the one the web
 * storefront's button produces. One payload, one verification path.
 *
 * There is no client secret in this exchange. The app is a public OAuth client
 * and the `code_verifier` is what proves the code is being redeemed by whoever
 * requested it — see GoogleAuthFlow.
 */
@Serializable
data class GoogleTokenResponseDto(
    @SerialName("id_token")
    val idToken: String = "",
)

/**
 * The four settings the account carries.
 *
 * Defaults match the server's own (`CustomerAccount::resolvedPreferences`), so
 * an account saved before a preference existed reads as the server would read
 * it rather than as false.
 */
@Serializable
data class CustomerPreferencesDto(
    val emailUpdates: Boolean = true,
    val smsUpdates: Boolean = false,
    val marketingEmails: Boolean = false,
    /** "call", "best-match" or "refund" — what a picker does with a gap. */
    val substitutions: String = "call",
)

/**
 * PATCH /api/customer/account.
 *
 * Every field is optional and omitted fields are left alone — the server merges
 * preferences over what is stored rather than replacing the object, so sending
 * one toggle does not blank the other three.
 */
@Serializable
data class UpdateProfileRequestDto(
    val name: String? = null,
    val phone: String? = null,
    val preferences: CustomerPreferencesDto? = null,
)

/**
 * PATCH /api/customer/account/email.
 *
 * The current password is required, and a Google-only account is refused
 * outright with a 422 on `email`: its address is Google's to change, and moving
 * ours would either be undone on the next sign-in or mint a second account.
 */
@Serializable
data class UpdateEmailRequestDto(
    val email: String,
    val currentPassword: String,
)

/**
 * PATCH /api/customer/account/password.
 *
 * [currentPassword] is null for a Google-only account setting its first one —
 * there is nothing to prove, and the session doing the asking is itself the
 * proof. Succeeding signs every *other* device out.
 */
@Serializable
data class UpdatePasswordRequestDto(
    val currentPassword: String? = null,
    val password: String,
    @SerialName("password_confirmation")
    val passwordConfirmation: String,
)

/**
 * POST and PATCH /api/customer/addresses[/{id}].
 *
 * `lat`/`lng` are all-or-nothing — the same pairing rule the order endpoint
 * uses, because half a coordinate quietly changes the delivery quote.
 *
 * `isDefault` is only ever sent true. The server will not clear the flag: a
 * list without a default is not a state it allows, so the way to move it is to
 * make a different address the default.
 */
@Serializable
data class SavedAddressRequestDto(
    val label: String? = null,
    val line1: String? = null,
    val barangay: String? = null,
    val city: String? = null,
    val notes: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val isDefault: Boolean? = null,
)

/** POST /api/customer/payment-methods. `detail` is required for an e-wallet. */
@Serializable
data class SavedPaymentMethodRequestDto(
    val kind: String? = null,
    val detail: String? = null,
    val isDefault: Boolean? = null,
)

/**
 * The reply to creating an address: the whole account, plus which row is new.
 *
 * The whole account because saving one address can move the default off
 * another, so a reply carrying only the new row would leave the client patching
 * up a list it no longer knows the shape of.
 */
@Serializable
data class AddressCreatedDto(
    val account: CustomerAccountDto,
    val addressId: String = "",
)
