<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Mail\SellerSignupAlertMail;
use App\Mail\SellerWelcomeMail;
use App\Models\Organization;
use App\Models\OrganizationMembership;
use App\Models\Store;
use App\Models\StoreMembership;
use App\Models\Subscription;
use App\Models\User;
use App\Services\GoogleIdentity;
use App\Services\GoogleIdentityException;
use App\Services\GoogleIdentityVerifier;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Mail;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

/**
 * Public self-serve signup: creates an organization, its first store, the owner
 * account and a pending subscription in one shot, so a new merchant has a
 * working store immediately.
 *
 * Replaces api/signup.ts. Payment, when there is one, is a manual GCash
 * transfer verified later through PlatformAdminController — this endpoint only
 * records the reference. During early access the form sends none, and the
 * subscription is created pending with an empty reference.
 *
 * The whole thing runs in one transaction, which fixes a flaw in the Firestore
 * version: there, the auth user was created before the batch write, so a failed
 * batch orphaned the account with no cleanup. Here nothing is committed unless
 * all of it is.
 */
class SignupController extends Controller
{
    /** Placeholder until a real price exists. Not shown anywhere yet: the
     *  signup form collects no payment while Omaykan is in early access. */
    private const PLAN_ID = 'standard-monthly';

    private const PLAN_AMOUNT_CENTS = 49900;

    private const BUSINESS_MODES = ['coffee-shop', 'grocery', 'restaurant', 'nail-salon'];

    public function store(Request $request, GoogleIdentityVerifier $verifier): JsonResponse
    {
        // Two doors into the same signup. With `googleCredential` the owner's
        // name and address come from Google and there is no password to choose;
        // without it they type all four. The rules below are the intersection,
        // and the conditional half is checked after the credential is verified,
        // because what is required depends on what Google said.
        $validated = $request->validate([
            'businessName' => ['required', 'string', 'max:120'],
            'ownerFullName' => ['nullable', 'string', 'max:120'],
            // Required unless Google supplies it: it is the only way to reach
            // an owner after signup — for the welcome mail, and for anything to
            // do with the account they can no longer sign into.
            'email' => ['nullable', 'email', 'max:190'],
            'username' => ['nullable', 'string', 'max:60'],
            'password' => ['nullable', 'string', 'min:6'],
            'googleCredential' => ['nullable', 'string'],
            'businessTypeLabel' => ['required', 'string', 'max:120'],
            'businessMode' => ['required', Rule::in(self::BUSINESS_MODES)],
            // Optional: the signup form stopped collecting payment when early
            // access was made free, but the field is still accepted so that
            // reinstating the step needs no API change.
            'gcashReference' => ['nullable', 'string', 'max:80'],
        ]);

        $identity = isset($validated['googleCredential']) && $validated['googleCredential'] !== ''
            ? $this->verifiedIdentity($verifier, $validated['googleCredential'])
            : null;

        if ($identity === null) {
            // Only meaningful on the typed path — a Google signup has proved an
            // address and chosen no password, and asking for either would be
            // asking twice.
            $request->validate([
                'ownerFullName' => ['required', 'string', 'max:120'],
                'email' => ['required', 'email', 'max:190'],
                'username' => ['required', 'string', 'max:60'],
                'password' => ['required', 'string', 'min:6'],
            ]);
        }

        $username = isset($validated['username']) && $validated['username'] !== ''
            ? strtolower(trim($validated['username']))
            : null;
        $email = strtolower(trim($identity->email ?? $validated['email']));
        $ownerName = trim($identity->name ?? $validated['ownerFullName']);
        $businessTypeLabel = trim($validated['businessTypeLabel']);

        if ($businessTypeLabel === '') {
            throw ValidationException::withMessages([
                'businessTypeLabel' => 'Please tell us what kind of business you run.',
            ]);
        }

        if ($username !== null && User::query()->where('username', $username)->exists()) {
            throw ValidationException::withMessages([
                'username' => 'That username is already taken — try a different one.',
            ]);
        }

        // Checked here rather than left to the unique index, which would
        // surface as a 500. Same shape as the username check above.
        if (User::query()->where('email', $email)->exists()) {
            throw ValidationException::withMessages([
                'email' => 'That email address already has an Omaykan account.',
            ]);
        }

        $result = DB::transaction(function () use ($validated, $username, $email, $ownerName, $identity, $businessTypeLabel) {
            $businessName = trim($validated['businessName']);

            $organization = Organization::query()->create([
                'id' => (string) str()->uuid(),
                'name' => $businessName,
                'slug' => $this->uniqueSlug($businessName),
                'status' => 'active',
            ]);

            $store = new Store([
                'id' => (string) str()->uuid(),
                'organization_id' => $organization->id,
                'name' => $businessName,
                // The first store is always 'main'; multi-branch comes later.
                'code' => 'main',
                'address' => '',
                'business_mode' => $validated['businessMode'],
                'business_type_label' => $businessTypeLabel,
                'timezone' => 'Asia/Manila',
                'currency_code' => 'PHP',
                'status' => 'active',
            ]);
            $store->save();

            $owner = User::query()->create([
                'name' => $ownerName,
                'username' => $username,
                'email' => $email,
                'google_sub' => $identity?->sub,
                'avatar_url' => $identity?->picture,
                // Null, not a random hash, for a Google owner. There is no
                // password on this account and the column says so, which is
                // what lets `hasPassword()` tell the sign-in screen the truth.
                'password' => ($validated['password'] ?? '') !== '' ? $validated['password'] : null,
                'status' => 'active',
            ]);

            if ($identity !== null) {
                // Google has already proved they hold the mailbox. Sending them
                // to click a link in an address Google just vouched for would be
                // ceremony, not security — the same call CustomerAuthController
                // makes.
                $owner->forceFill(['email_verified_at' => now()])->save();
            }

            OrganizationMembership::query()->create([
                'organization_id' => $organization->id,
                'user_id' => $owner->id,
                'membership_role' => 'admin',
            ]);

            StoreMembership::query()->create([
                'store_id' => $store->id,
                'user_id' => $owner->id,
                'membership_role' => 'admin',
            ]);

            Subscription::query()->create([
                'id' => (string) str()->uuid(),
                'organization_id' => $organization->id,
                'status' => Subscription::STATUS_PENDING,
                'plan' => self::PLAN_ID,
                'amount_cents' => self::PLAN_AMOUNT_CENTS,
                // Empty string, not null: the column is NOT NULL, and a
                // subscription with no reference is exactly what an early-access
                // signup is — pending, with nothing yet to verify.
                'gcash_reference' => trim($validated['gcashReference'] ?? ''),
                'submitted_at' => now(),
            ]);

            // Told about only once the transaction commits, or the mail can
            // announce a store that a rollback removed — and, because the
            // queue is the database, a worker could pick the job up before the
            // rows it describes are visible. Matches the OrderPlaced broadcast.
            DB::afterCommit(fn () => $this->announce($owner, $organization, $store));

            if ($identity === null) {
                DB::afterCommit(fn () => $this->sendVerificationLink($owner));
            }

            return compact('organization', 'store', 'owner');
        });

        $needsVerification = ! $result['owner']->hasVerifiedEmail();

        return response()->json([
            'organizationSlug' => $result['organization']->slug,
            'storeCode' => $result['store']->code,
            'verificationRequired' => $needsVerification,
            'message' => $needsVerification
                ? 'Check your email for a verification link before signing in.'
                : 'Your store is ready — sign in with Google to open it.',
        ], 201);
    }

    /**
     * Both sides of a signup: the merchant hears that their store is ready,
     * and the operators hear that there is a pending subscription to verify.
     *
     * Failures are swallowed deliberately. A signup that has already committed
     * must not be reported as a failure because a mailbox was unreachable —
     * the merchant would retry and hit "that username is taken" on their own
     * account. Both mails are queued, so this only catches a queue that cannot
     * be written to; the queue's own retries cover a flaky SMTP host.
     */
    private function announce(User $owner, Organization $organization, Store $store): void
    {
        try {
            Mail::to($owner->email)->queue(new SellerWelcomeMail($owner, $organization, $store));

            $alertsTo = config('mail.alerts_to');

            if (is_string($alertsTo) && trim($alertsTo) !== '') {
                Mail::to($alertsTo)->queue(new SellerSignupAlertMail($owner, $organization, $store));
            }
        } catch (\Throwable $e) {
            report($e);
        }
    }

    private function sendVerificationLink(User $owner): void
    {
        try {
            $owner->sendEmailVerificationNotification();
        } catch (\Throwable $e) {
            report($e);
        }
    }

    /**
     * Slugs are derived from the business name, so two merchants with the same
     * name collide. Suffix until free.
     */
    private function uniqueSlug(string $businessName): string
    {
        $base = str()->slug($businessName) ?: 'store';
        $candidate = $base;

        for ($attempt = 2; $attempt <= 50; $attempt++) {
            if (! Organization::query()->where('slug', $candidate)->exists()) {
                return $candidate;
            }

            $candidate = "{$base}-{$attempt}";
        }

        throw ValidationException::withMessages([
            'businessName' => 'Could not allocate a store ID — try a slightly different business name.',
        ]);
    }

    /**
     * The Google identity behind a signup, or a validation error.
     *
     * An address Google will not call verified is refused: a Workspace domain
     * that never completed verification would otherwise be a way to found an
     * organization on a mailbox nobody has proved they can read.
     */
    private function verifiedIdentity(GoogleIdentityVerifier $verifier, string $credential): GoogleIdentity
    {
        try {
            $identity = $verifier->verify($credential);
        } catch (GoogleIdentityException $e) {
            // Reported so a misconfigured client id or an unreachable Google
            // shows up in the log as itself rather than as a wave of merchants
            // saying signup is broken.
            report($e);

            throw ValidationException::withMessages(['googleCredential' => $e->getMessage()]);
        }

        if (! $identity->emailVerified) {
            throw ValidationException::withMessages([
                'googleCredential' => 'That Google account has an unverified email address. Verify it with Google first.',
            ]);
        }

        return $identity;
    }
}
