package com.omaykan.rider.core.network

import com.omaykan.rider.core.network.dto.AdvanceRequestDto
import com.omaykan.rider.core.network.dto.AssignmentEnvelopeDto
import com.omaykan.rider.core.network.dto.BoardDto
import com.omaykan.rider.core.network.dto.EarningsDto
import com.omaykan.rider.core.network.dto.ForgotPasswordRequestDto
import com.omaykan.rider.core.network.dto.LoginRequestDto
import com.omaykan.rider.core.network.dto.MessageDto
import com.omaykan.rider.core.network.dto.MyDeliveriesDto
import com.omaykan.rider.core.network.dto.PasswordChangeRequestDto
import com.omaykan.rider.core.network.dto.PositionAckDto
import com.omaykan.rider.core.network.dto.PositionRequestDto
import com.omaykan.rider.core.network.dto.RatingsDto
import com.omaykan.rider.core.network.dto.ProfileUpdateRequestDto
import com.omaykan.rider.core.network.dto.ReleasedDto
import com.omaykan.rider.core.network.dto.RiderEnvelopeDto
import com.omaykan.rider.core.network.dto.RiderSessionDto
import com.omaykan.rider.core.network.dto.SupportDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Every endpoint this app talks to, and no others.
 *
 * All of them sit under `/api/rider`, on the `rider` guard. That guard is
 * separate from `sanctum` (staff and devices) and `customer` precisely so a
 * token minted here can never satisfy a seller or sync route however it is
 * sent — see config/auth.php. Nothing in this interface takes a store id: a
 * rider works across every shop, and the scoping is by rider, not by tenant.
 *
 * The rate limits are noted where they shape the UI. Registration's 4/min is
 * the tight one, which is why that screen submits on a button press and
 * validates everything it can before spending one.
 */
interface RiderApi {

    /**
     * 4/min, the hardest limit on the public API — this creates an account
     * *and* accepts two file uploads.
     *
     * Answers 201 with a token even though the rider is `pending`. That is
     * deliberate on the server's side: without one, somebody who closed the app
     * after signing up would have no way back to their own status.
     */
    @Multipart
    @POST("api/rider/register")
    suspend fun register(
        @Part("name") name: RequestBody,
        @Part("email") email: RequestBody,
        @Part("phone") phone: RequestBody,
        @Part("password") password: RequestBody,
        // Laravel's `confirmed` rule looks for exactly this snake_case name.
        // Everything else is camelCase because that is what the controller
        // validates.
        @Part("password_confirmation") passwordConfirmation: RequestBody,
        @Part("licenseNumber") licenseNumber: RequestBody,
        @Part("plateNumber") plateNumber: RequestBody,
        // The bike. Optional on the server so that a client which predates the
        // column keeps registering successfully, but this one always sends a
        // type because its form always asks for one.
        @Part("vehicleType") vehicleType: RequestBody,
        @Part("vehicleMake") vehicleMake: RequestBody,
        @Part("vehicleModel") vehicleModel: RequestBody,
        @Part("vehicleColor") vehicleColor: RequestBody,
        @Part licenseImage: MultipartBody.Part,
        @Part plateImage: MultipartBody.Part,
    ): RiderSessionDto

    /**
     * 10/min.
     *
     * A rejected or suspended rider signs in successfully — the 403 comes later
     * from the approval gate, not from here. Being told why, on their own
     * status screen, is the only route they have back to an operator.
     */
    @POST("api/rider/login")
    suspend fun login(@Body request: LoginRequestDto): RiderSessionDto

    /**
     * Ask for a reset link. 5/min.
     *
     * Public — this is the door for somebody who cannot sign in. The reply is
     * the same sentence whether or not the address belongs to a rider, so the
     * screen shows the server's words rather than claiming a mail was sent.
     *
     * The link itself opens the **web portal**, not this app: there is no deep
     * link registered here, and a reset token that only one installed app could
     * spend would strand a rider who read the mail on a laptop.
     */
    @POST("api/rider/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequestDto): MessageDto

    /**
     * Outside the approval gate, so a pending rider can read their own status.
     *
     * Also the app's heartbeat: the server stamps `last_seen_at` on every rider
     * call, and this is the one the status screen can make while waiting.
     */
    @GET("api/rider/me")
    suspend fun me(): RiderEnvelopeDto

    /** Signs out this phone only. Other phones stay signed in. */
    @POST("api/rider/logout")
    suspend fun logout(): ReleasedDto

    /**
     * Change a name, a phone number or a plate.
     *
     * Outside the approval gate, with `/me` and `/logout`, and deliberately: a
     * rider waiting on review is the one most likely to be correcting the phone
     * number an operator is about to ring, and a suspended rider must still be
     * able to fix their details. Neither can see a job either way.
     *
     * Only the fields sent are written. The email and the licence number are
     * not accepted at all — see ProfileUpdateRequestDto.
     */
    @PATCH("api/rider/me")
    suspend fun updateProfile(@Body request: ProfileUpdateRequestDto): RiderEnvelopeDto

    /**
     * Replace the rider's photograph.
     *
     * Outside the approval gate with `/me`: a pending rider filling in their
     * profile while they wait is the normal case, and the photo is shown to
     * nobody until they are carrying an order — which the gate prevents.
     *
     * 422 on anything that does not decode as an image, so a renamed file
     * cannot become an avatar.
     */
    @Multipart
    @POST("api/rider/avatar")
    suspend fun uploadAvatar(@Part photo: MultipartBody.Part): RiderEnvelopeDto

    /**
     * Take the photo down.
     *
     * The server clears the column before deleting the file, so this returning
     * successfully means the photo has stopped resolving everywhere it was
     * shown — which is what the rider asked for.
     */
    @DELETE("api/rider/avatar")
    suspend fun deleteAvatar(): RiderEnvelopeDto

    /**
     * What customers have scored this rider, and the twenty most recent.
     *
     * Behind `rider.approved` with earnings, and for the same reason: a score
     * is a fact about work, and an account never allowed to work has none.
     *
     * The customer who left a rating is not in the payload. That is deliberate
     * server-side — see RiderRating::toRiderArray.
     */
    @GET("api/rider/ratings")
    suspend fun ratings(): RatingsDto

    /**
     * How to reach a human.
     *
     * Outside the approval gate on purpose: a rejected rider who does not
     * understand why is the person most in need of somebody to ask, and gating
     * support behind approval would silence exactly them.
     */
    @GET("api/rider/support")
    suspend fun support(): SupportDto

    /**
     * Change the password, current one required.
     *
     * Retires every *other* token on the account and leaves this one alive. A
     * rider tightening their own security mid-shift must not be thrown onto the
     * sign-in screen for it — 422 on `currentPassword` when it is wrong.
     */
    @PATCH("api/rider/password")
    suspend fun updatePassword(@Body request: PasswordChangeRequestDto): RiderEnvelopeDto

    /**
     * Unclaimed delivery orders, platform-wide, newest first, capped at 40.
     *
     * Behind `rider.approved`. Carries no customer name, phone or street
     * address — see DeliveryOffer.
     */
    @GET("api/rider/board")
    suspend fun board(): BoardDto

    /** This rider's own work: on the road, then the last 30 finished. */
    @GET("api/rider/deliveries")
    suspend fun mine(): MyDeliveriesDto

    /**
     * What the work has come to: today, this week, this month, all of it, and a
     * fortnight of daily totals.
     *
     * Aggregated server-side rather than summed here, and that is the point of
     * it existing: `mine()` returns the last thirty finished jobs, so anything
     * this app added up itself stopped being true at job thirty-one.
     *
     * Days are bucketed in Asia/Manila, not UTC — the application runs in UTC,
     * which would put a day boundary at 8am in Baguio and split a night shift
     * across two of them.
     */
    @GET("api/rider/earnings")
    suspend fun earnings(): EarningsDto

    /**
     * Claim a job.
     *
     * 409 when another rider got there first — the server claims with a
     * conditional UPDATE, so exactly one of two simultaneous taps wins and the
     * other is told plainly. 422 for an order that is for pickup.
     */
    @POST("api/rider/deliveries/{orderId}/accept")
    suspend fun accept(@Path("orderId") orderId: String): AssignmentEnvelopeDto

    /**
     * assigned → picked_up → delivered.
     *
     * The target stage is named rather than inferred, so a double-tap on a slow
     * connection cannot skip a step. 422 when the order is not at the stage
     * that move starts from.
     */
    @POST("api/rider/deliveries/{orderId}/stage")
    suspend fun advance(
        @Path("orderId") orderId: String,
        @Body request: AdvanceRequestDto,
    ): AssignmentEnvelopeDto

    /**
     * Give a job back to the board.
     *
     * 422 once it has been picked up: the food is physically with the rider by
     * then, and handing it back is a phone call, not a button.
     */
    @POST("api/rider/deliveries/{orderId}/release")
    suspend fun release(@Path("orderId") orderId: String): ReleasedDto

    /**
     * Where this rider is.
     *
     * 60/min, which is six times the app's own ten-second cadence — headroom
     * for the burst that follows a tunnel, and nowhere near enough for a
     * runaway loop to matter.
     *
     * Behind `rider.approved` like the board: a pending rider has no deliveries
     * and therefore nobody to tell.
     */
    @POST("api/rider/position")
    suspend fun position(@Body request: PositionRequestDto): PositionAckDto

    /**
     * Stop reporting, and forget the last fix.
     *
     * Called when the rider turns sharing off and on the way out of sign-out.
     * The server nulls the columns rather than letting the last position sit
     * there going stale — somebody who says stop should be gone from the
     * database, not merely gone from the map.
     */
    @DELETE("api/rider/position")
    suspend fun stopSharingPosition(): PositionAckDto
}
