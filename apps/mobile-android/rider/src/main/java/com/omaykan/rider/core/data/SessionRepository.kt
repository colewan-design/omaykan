package com.omaykan.rider.core.data

import com.omaykan.rider.core.auth.RiderSessionStore
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.core.model.VehicleType
import com.omaykan.rider.core.model.RiderStatus
import com.omaykan.rider.core.model.SessionState
import com.omaykan.rider.core.network.ApiCaller
import com.omaykan.rider.core.network.ApiException
import com.omaykan.rider.core.network.RiderApi
import com.omaykan.rider.core.network.dto.LoginRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two document photos, as bytes and a content type.
 *
 * Deliberately not a `Uri`. Reading a content URI is Android's problem and it
 * belongs to the screen that got the URI from the photo picker — this layer
 * takes what came out of it. That keeps the repository testable without a
 * ContentResolver, and it means the *size* of what is about to be uploaded is
 * known before the call rather than discovered by the server's 8MB rule.
 */
data class DocumentUpload(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
) {
    /*
     * A ByteArray in a data class breaks equals/hashCode — the generated ones
     * compare by reference — so they are written out. Nothing in the app
     * compares two uploads today; the override is here so nothing quietly does
     * the wrong thing when something does.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentUpload) return false

        return bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType &&
            fileName == other.fileName
    }

    override fun hashCode(): Int =
        (bytes.contentHashCode() * 31 + mimeType.hashCode()) * 31 + fileName.hashCode()
}

/** Everything registration asks for, in one argument. */
data class Registration(
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val licenseNumber: String,
    val plateNumber: String,
    /**
     * The bike. Type always has a value because the form always shows a
     * selection; the three descriptive fields are blank when the rider left
     * them blank, and blank reaches the server as a cleared column.
     */
    val vehicleType: VehicleType = VehicleType.Motorcycle,
    val vehicleMake: String = "",
    val vehicleModel: String = "",
    val vehicleColor: String = "",
    val licenseImage: DocumentUpload,
    val plateImage: DocumentUpload,
)

/**
 * Who is signed in, and whether the platform will let them work.
 *
 * ## Why a person signs in here and a device pairs in :seller
 *
 * The seller app pairs with a shop's code because the seller API's order routes
 * accept a `Device` and nothing else. The rider API is the opposite: every
 * route under `/api/rider` resolves a `Rider` on its own Laravel guard, the
 * token is minted against a personal account, and the work is scoped to *that
 * rider* rather than to a tenant. There is no code to pair with and there
 * should not be — a job board that any phone holding a shop's code could read
 * would be a job board any shop could raid.
 *
 * ## Why "signed in" and "allowed to work" are two questions
 *
 * A pending, rejected or suspended rider signs in perfectly well and then meets
 * `EnsureRiderIsApproved`, which answers 403 with the account's status. That is
 * by design on the server — being told why, on their own screen, is the only
 * route a rejected applicant has back to an operator — and it is why
 * [SessionState] splits the signed-in half in two rather than carrying a flag.
 *
 * [applyGate] is the other half of that. Any call anywhere in the app can come
 * back Gated, including mid-shift when an operator suspends someone who is
 * holding a phone, and every repository routes that one exception back here so
 * the screen changes underneath them instead of a board quietly failing to
 * refresh.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: RiderApi,
    private val caller: ApiCaller,
    private val store: RiderSessionStore,
) {
    /**
     * Signed in only when both halves are present.
     *
     * A token with no profile would leave the app unable to say whether to show
     * a board or a waiting screen; a profile with no token would show a rider's
     * own name above a list that 401s. They are written and cleared together in
     * one file, so the second is unreachable today — the conjunction is here so
     * it stays unreachable if that ever changes.
     */
    val session: Flow<SessionState> = combine(store.token, store.rider) { token, rider ->
        when {
            token == null || rider == null -> SessionState.SignedOut
            rider.status == RiderStatus.Approved -> SessionState.Working(rider)
            else -> SessionState.Gated(rider)
        }
    }

    /**
     * Create an account and sign in with it.
     *
     * Answers 201 with a token for an account that cannot yet do anything, and
     * the app keeps it: without one, a rider who closed the app after signing
     * up would have no way back to their own status and would try to register
     * again — against an email the server has already taken.
     *
     * Never retried. A retry that the first attempt actually completed is a
     * second identity document on the private disk and a "that email is taken"
     * error against the account just created.
     */
    suspend fun register(input: Registration): RiderProfile {
        val session = caller.call {
            api.register(
                name = input.name.trim().asTextPart(),
                email = input.email.trim().asTextPart(),
                phone = input.phone.trim().asTextPart(),
                password = input.password.asTextPart(),
                // The server's `confirmed` rule compares the two. The app has
                // already checked they match, so sending the same value twice
                // is honest rather than a duplication: it is the second field
                // on the form, and it agreed.
                passwordConfirmation = input.password.asTextPart(),
                licenseNumber = input.licenseNumber.trim().asTextPart(),
                plateNumber = input.plateNumber.trim().asTextPart(),
                vehicleType = input.vehicleType.wire.asTextPart(),
                vehicleMake = input.vehicleMake.trim().asTextPart(),
                vehicleModel = input.vehicleModel.trim().asTextPart(),
                vehicleColor = input.vehicleColor.trim().asTextPart(),
                licenseImage = input.licenseImage.asFilePart("licenseImage"),
                plateImage = input.plateImage.asFilePart("plateImage"),
            )
        }

        return session.rider.toModel().also { store.save(session.token, it) }
    }

    suspend fun signIn(email: String, password: String): RiderProfile {
        val session = caller.call {
            api.login(LoginRequestDto(email = email.trim(), password = password))
        }

        return session.rider.toModel().also { store.save(session.token, it) }
    }

    /**
     * Ask the server what this account's status actually is.
     *
     * The one call a gated rider can make, and the only way an approval reaches
     * a phone — there is no push, so the status screen makes this on a timer
     * and on every return to the foreground.
     *
     * A failure is swallowed on purpose. This is a background correction to a
     * cached answer that is almost always right; a rider in a lift does not
     * need an error about it, and the cached profile is still the best thing to
     * show. Everything that is *not* recoverable this way — a 401 — has already
     * been handled by the interceptor, which cleared the token.
     */
    suspend fun refresh(): RiderProfile? {
        // No token, nothing to refresh. Called unconditionally at launch, and
        // without this it would spend a request — and a 401 the interceptor
        // then reacts to — every time somebody opens the app to a sign-in
        // screen.
        if (store.currentToken() == null) return null

        return runCatching {
            caller.call { api.me() }.rider.toModel().also(store::saveRider)
        }.getOrNull()
    }

    /**
     * Fold the approval gate's 403 back into the session.
     *
     * Called from every repository that can meet one. The status is the
     * server's, so a rider suspended between two taps is on their status screen
     * by the third — reading the operator's note, rather than staring at a red
     * banner over a board.
     *
     * Returns true when it handled the exception, so callers can `if
     * (sessions.applyGate(e)) return` and not also show an error for a screen
     * that is being replaced.
     */
    fun applyGate(e: ApiException): Boolean {
        if (e !is ApiException.Gated) return false

        val current = store.rider.value ?: return true

        store.saveRider(
            current.copy(
                status = e.status,
                // The gate's note is the operator's own sentence and the newer
                // of the two. Kept only when it says something: a middleware
                // that sends null must not erase the reason a rejected rider is
                // reading on screen.
                reviewNote = e.reviewNote ?: current.reviewNote,
            ),
        )

        return true
    }

    /**
     * Sign this phone out.
     *
     * The server call first, because it is the half that can fail: it retires
     * this one token and leaves the rider's other phones signed in. Its failure
     * is ignored — a rider in a basement tapping Sign out must end up signed
     * out, and a token that outlives the app's copy of it is a far smaller
     * problem than an app that refuses to let go of a phone somebody is handing
     * back.
     */
    suspend fun signOut() {
        runCatching { caller.call { api.logout() } }
        store.clear()
    }
}

/**
 * A plain form field in a multipart body.
 *
 * No content type, deliberately. Naming one — `text/plain` — makes OkHttp add a
 * charset parameter to the part header, and PHP reads a part with a
 * Content-Type as a file rather than a field. The result is a validator
 * complaining that `name` is required against a request that plainly contains
 * it, which is a genuinely miserable afternoon.
 */
private fun String.asTextPart(): RequestBody = toRequestBody()

/**
 * Internal rather than private since the account screen gained an uploader of
 * its own: registration and the avatar route are the same multipart shape, and
 * a second copy of three lines is how the two drift apart.
 */
internal fun DocumentUpload.asFilePart(field: String): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        field,
        fileName,
        bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
    )
