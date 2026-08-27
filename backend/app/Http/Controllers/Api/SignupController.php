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

    /** No 0/O or 1/I/L: the owner reads this back to customers out loud. */
    private const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'businessName' => ['required', 'string', 'max:120'],
            'ownerFullName' => ['required', 'string', 'max:120'],
            // Required, not optional: it is the only way to reach an owner
            // after signup — for the welcome mail, and for anything to do with
            // the account they can no longer sign into.
            'email' => ['required', 'email', 'max:190'],
            'username' => ['required', 'string', 'max:60'],
            'password' => ['required', 'string', 'min:6'],
            'businessTypeLabel' => ['required', 'string', 'max:120'],
            'businessMode' => ['required', Rule::in(self::BUSINESS_MODES)],
            // Optional: the signup form stopped collecting payment when early
            // access was made free, but the field is still accepted so that
            // reinstating the step needs no API change.
            'gcashReference' => ['nullable', 'string', 'max:80'],
        ]);

        $username = strtolower(trim($validated['username']));
        $email = strtolower(trim($validated['email']));
        $businessTypeLabel = trim($validated['businessTypeLabel']);

        if ($businessTypeLabel === '') {
            throw ValidationException::withMessages([
                'businessTypeLabel' => 'Please tell us what kind of business you run.',
            ]);
        }

        if (User::query()->where('username', $username)->exists()) {
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

        $result = DB::transaction(function () use ($validated, $username, $email, $businessTypeLabel) {
            $businessName = trim($validated['businessName']);

            $organization = Organization::query()->create([
                'id' => (string) str()->uuid(),
                'name' => $businessName,
                'slug' => $this->uniqueSlug($businessName),
                'status' => 'active',
            ]);

            $storeCode = $this->uniqueStoreCode();

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
            $store->setPairingCode($storeCode);
            $store->save();

            $owner = User::query()->create([
                'name' => trim($validated['ownerFullName']),
                'username' => $username,
                'email' => $email,
                'password' => $validated['password'],
                'status' => 'active',
            ]);

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
            DB::afterCommit(fn () => $this->sendVerificationLink($owner));

            return compact('organization', 'store');
        });

        return response()->json([
            'organizationSlug' => $result['organization']->slug,
            'storeCode' => $result['store']->code,
            // The code the owner hands to customers. Returned once here
            // and also readable later from Settings > Online Store.
            'pairingCode' => $result['store']->public_store_code,
            'verificationRequired' => true,
            'message' => 'Check your email for a verification link before signing in.',
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

    private function uniqueStoreCode(): string
    {
        for ($attempt = 0; $attempt < 20; $attempt++) {
            $code = '';
            for ($i = 0; $i < 6; $i++) {
                $code .= self::CODE_ALPHABET[random_int(0, strlen(self::CODE_ALPHABET) - 1)];
            }

            if (! Store::query()->where('public_store_code', $code)->exists()) {
                return $code;
            }
        }

        abort(500, 'Could not generate a unique store code — please try again.');
    }
}
