package com.omaykan.storefront.core.model

/**
 * The signed-in shopper.
 *
 * Carries the saved addresses and payment methods as well as the profile,
 * because checkout prefills from all three. The addresses earn their place
 * twice over: this app has no geocoder, so an address the shopper types can
 * never produce a pin — but one they saved on the web storefront already has
 * one, and that pin is what lets the server quote a real delivery fee instead
 * of falling back to the shop's flat rate.
 *
 * None of it is a requirement. A guest still checks out by typing three fields,
 * which stays the app's first-class case (mobile-plan.md §5, fact 3); an
 * account just means they are typed already.
 */
data class CustomerAccount(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    /** Google's portrait, when they signed in with it. Blank otherwise. */
    val avatarUrl: String,
    val googleLinked: Boolean,
    /**
     * False for an account that has only ever signed in with Google.
     *
     * Two screens turn on it: setting a first password asks for no current one,
     * and the email cannot be changed here at all until there is one, because
     * Google's copy is what the next sign-in arrives with.
     */
    val hasPassword: Boolean = false,
    val preferences: CustomerPreferences = CustomerPreferences(),
    /** Oldest first with the default lifted to the top — the server sorts. */
    val addresses: List<SavedAddress> = emptyList(),
    val paymentMethods: List<SavedPaymentMethod> = emptyList(),
) {
    /** A Google-only account: the one shape where email is not ours to change. */
    val emailManagedByGoogle: Boolean get() = googleLinked && !hasPassword

    /**
     * What checkout should open on: the one marked default, else the oldest.
     *
     * Never null-safe by accident — an account with no saved address is the
     * normal case for someone who has only ever ordered from this phone.
     */
    val preferredAddress: SavedAddress? get() = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()

    val preferredPayment: SavedPaymentMethod?
        get() = paymentMethods.firstOrNull { it.isDefault } ?: paymentMethods.firstOrNull()
}

/**
 * What a picker should do when a line is not on the shelf.
 *
 * Asked in the account rather than at checkout, where every extra question
 * costs an order — the same call the web portal makes.
 */
enum class Substitution(val wire: String, val label: String, val detail: String) {
    Call("call", "Call me", "Ring before deciding anything."),
    BestMatch("best-match", "Pick the closest thing", "Nearest equivalent, same kind of item."),
    Refund("refund", "Just leave it out", "Refund the line and bring the rest."),
    ;

    companion object {
        /** Anything unknown reads as Call — the most cautious of the three. */
        fun fromWire(value: String): Substitution =
            entries.firstOrNull { it.wire == value } ?: Call
    }
}

/**
 * The four settings on the account.
 *
 * Defaults match the server's, so an account saved before a preference existed
 * reads the way the server reads it rather than as off.
 */
data class CustomerPreferences(
    val emailUpdates: Boolean = true,
    val smsUpdates: Boolean = false,
    val marketingEmails: Boolean = false,
    val substitutions: Substitution = Substitution.Call,
)

/**
 * A delivery address the shopper has saved.
 *
 * [lat] and [lng] are null for one typed without a pin. The order endpoint
 * rejects half a coordinate, so [pinned] is the only safe way to ask.
 */
data class SavedAddress(
    val id: String,
    val label: String,
    val line1: String,
    val barangay: String,
    val city: String,
    /** Gate colour, landmark, which door. What the rider actually navigates by. */
    val notes: String,
    val lat: Double?,
    val lng: Double?,
    val isDefault: Boolean,
) {
    val pinned: Boolean get() = lat != null && lng != null

    /**
     * Just the street part, for the address box.
     *
     * The notes are deliberately left out: they are the landmark, they get
     * their own field on the checkout screen, and folding them in here would
     * mean a shopper correcting a house number had to pick their own landmark
     * back out of the middle of a sentence.
     */
    val oneLine: String
        get() = listOf(line1, barangay, city)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
}

data class SavedPaymentMethod(
    val id: String,
    val kind: PaymentPreference,
    /** The e-wallet number. Blank for cash; nothing in this app charges it. */
    val detail: String,
    val isDefault: Boolean,
)

/** Signed out until proven otherwise; `Restoring` is the one-disk-read gap. */
sealed interface SessionState {
    /** A stored token is being traded for the account it belongs to. */
    data object Restoring : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val account: CustomerAccount) : SessionState
}
