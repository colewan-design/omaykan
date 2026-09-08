package com.omaykan.rider.core.data

import com.omaykan.rider.core.auth.RiderSessionStore
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.core.network.ApiCaller
import com.omaykan.rider.core.network.RiderApi
import com.omaykan.rider.core.network.dto.ForgotPasswordRequestDto
import com.omaykan.rider.core.network.dto.PasswordChangeRequestDto
import com.omaykan.rider.core.network.dto.ProfileUpdateRequestDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a rider can change about themselves, and the way back in when they
 * cannot sign in at all.
 *
 * Separate from [SessionRepository] for the split the server draws: that one is
 * about getting a token, this one is about the account behind it. They share
 * the store, because every write here answers with the whole rider and the
 * stored copy is what the header, the status screen and the gate all render
 * from — two copies is how they start disagreeing.
 *
 * None of these calls sits behind the approval gate. That is the server's
 * decision and it is the right one: a rider waiting on review is the one most
 * likely to be correcting the phone number an operator is about to ring, and a
 * suspended rider must still be able to change a password they think somebody
 * else has.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val api: RiderApi,
    private val caller: ApiCaller,
    private val store: RiderSessionStore,
) {
    /**
     * Save a name, a phone number, a plate — or any subset of the three.
     *
     * Nulls are omitted from the JSON body, and the server validates each field
     * with `sometimes`, so an unchanged field is left alone rather than blanked.
     * The stored profile is replaced with the server's answer rather than with
     * what was typed: the plate comes back upper-cased, and a screen showing the
     * lower-case version the rider typed would disagree with the shop's.
     */
    suspend fun updateProfile(
        name: String? = null,
        phone: String? = null,
        plateNumber: String? = null,
    ): RiderProfile {
        val rider = caller.call {
            api.updateProfile(ProfileUpdateRequestDto(name = name, phone = phone, plateNumber = plateNumber))
        }.rider.toModel()

        store.saveRider(rider)

        return rider
    }

    /**
     * Change the password.
     *
     * The server retires every other token on the account and leaves this one
     * alive, so nothing here has to sign the phone out — and nothing here
     * should, because the rider may be halfway through a delivery.
     *
     * A wrong current password comes back as a 422 on `currentPassword`, which
     * the screen puts under that field rather than at the top of the form.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): RiderProfile {
        val rider = caller.call {
            api.updatePassword(
                PasswordChangeRequestDto(
                    currentPassword = currentPassword,
                    password = newPassword,
                    passwordConfirmation = newPassword,
                ),
            )
        }.rider.toModel()

        store.saveRider(rider)

        return rider
    }

    /**
     * Ask for a reset link, and hand back the server's sentence.
     *
     * The message is returned rather than swallowed because it is the same one
     * whether or not the address belongs to a rider — "if that email has a rider
     * account, a reset link is on its way" — and paraphrasing it into "sent!"
     * would turn a deliberately ambiguous answer into a claim that is sometimes
     * false. The platform's riders are a small, knowable set; a screen that said
     * "no such account" would be a way to enumerate it.
     *
     * The link opens the web portal. This app registers no deep link for it, and
     * should not: a reset only one installed app could spend would strand a
     * rider who opened the mail on a laptop.
     */
    suspend fun requestPasswordReset(email: String): String =
        caller.call { api.forgotPassword(ForgotPasswordRequestDto(email = email.trim())) }.message
}
