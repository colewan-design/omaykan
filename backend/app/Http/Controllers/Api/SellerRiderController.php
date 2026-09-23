<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\ActsForAStore;
use App\Http\Controllers\Controller;
use App\Models\Order;
use App\Models\Rider;
use App\Models\StoreSavedRider;
use App\Services\StoreContext;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * The riders one shop keeps, and the ones it has worked with.
 *
 * ## There is no rider directory here, on purpose
 *
 * The obvious way to let a seller "choose their rider" is to hand the shop a
 * searchable list of every approved rider on the platform. That endpoint would
 * be a phone book of every delivery rider in the city, readable by anyone who
 * ever paired a till — including anyone who signs up as a merchant to get it.
 * The riders never agreed to that, and it is the sort of thing that is fine
 * until the day it is not.
 *
 * So a shop can reach exactly two sets of riders:
 *
 *   1. **The ones it saved.** Deliberate, one at a time.
 *   2. **The ones who have already delivered for it.** The shop learned that
 *      name and number when the rider turned up at the counter; showing it back
 *      to them discloses nothing new, and it is the list they actually want —
 *      "the guy who did the 6pm run yesterday".
 *
 * Adding a rider who is in neither set is done by *phone number*, not by
 * browsing: the shop types the number of someone they already know. If that
 * number belongs to an approved platform account the row links to it and the
 * rider gets orders in their app; if it does not, the row is saved off-platform
 * and the shop rings them, exactly as before. Either way the shop had to
 * already know the number, which is the property that makes this safe.
 */
class SellerRiderController extends Controller
{
    use ActsForAStore;

    /** Riders who have carried for this shop before, beyond the saved ones. */
    private const RECENT_LIMIT = 12;

    /**
     * Everything the assign-a-rider picker needs, in one response.
     *
     * Two lists, because they mean different things to the person choosing:
     * `saved` is "my riders", ordered by who I use most, and `recent` is
     * "people who have worked here", offered so that saving one is a tap.
     */
    public function index(Request $request): JsonResponse
    {
        $context = $this->storeContext($request);

        $saved = StoreSavedRider::query()
            ->with('rider')
            ->where('store_id', $context->storeId())
            // Most-used first, then most-recent. A shop's usual rider should be
            // at the top without anyone having ranked anything by hand.
            ->orderByDesc('times_used')
            ->orderByDesc('last_used_at')
            ->orderBy('name')
            ->get();

        $savedRiderIds = $saved->pluck('rider_id')->filter()->all();

        $workedHere = Order::query()
            ->where('store_id', $context->storeId())
            ->whereNotNull('rider_id')
            ->where('delivery_stage', 'delivered')
            ->distinct()
            ->pluck('rider_id');

        $recent = Rider::query()
            ->whereIn('id', $workedHere)
            ->whereNotIn('id', $savedRiderIds)
            ->where('status', Rider::STATUS_APPROVED)
            ->limit(self::RECENT_LIMIT)
            ->get();

        return response()->json([
            'saved' => $saved->map(fn (StoreSavedRider $row) => $row->toPickerArray())->values(),
            'recent' => $recent->map(fn (Rider $rider) => [
                'riderId' => $rider->id,
                'name' => $rider->name,
                'phone' => $rider->phone,
                'onPlatform' => true,
                'status' => $rider->status,
                'online' => $rider->isReportingPosition(),
            ])->values(),
        ]);
    }

    /**
     * Keep a rider on file.
     *
     * `riderId` is accepted only for a rider this shop has already worked with
     * — the "recent" list above — so this cannot be used to bind an arbitrary
     * account by guessing UUIDs. Everyone else is found by the phone number the
     * shop already has, and linked to an account only if one happens to match.
     */
    public function store(Request $request): JsonResponse
    {
        $context = $this->writableStoreContext($request);

        $validated = $request->validate([
            'riderId' => ['nullable', 'uuid'],
            'name' => ['required', 'string', 'max:120'],
            'phone' => ['nullable', 'string', 'max:40'],
            'note' => ['nullable', 'string', 'max:160'],
        ]);

        $phone = trim((string) ($validated['phone'] ?? '')) ?: null;
        $note = trim((string) ($validated['note'] ?? '')) ?: null;

        $rider = null;

        if (! empty($validated['riderId'])) {
            $rider = Rider::query()
                ->where('status', Rider::STATUS_APPROVED)
                ->find($validated['riderId']);

            abort_unless(
                $rider !== null && $this->hasWorkedForStore($rider, $context->storeId()),
                422,
                'Add that rider by their phone number — a shop can only pick from riders it has worked with.',
            );
        } elseif ($phone !== null) {
            // The link-by-number path. A miss is not an error: most riders a
            // shop saves are somebody's cousin with no account at all.
            $rider = $this->findApprovedByPhone($phone);
        }

        $row = StoreSavedRider::query()->updateOrCreate(
            $this->keyFor($context, $rider, $phone, trim($validated['name'])),
            [
                'name' => trim($validated['name']),
                'phone' => $phone ?? $rider?->phone,
                'note' => $note,
            ],
        );

        return response()->json(['savedRider' => $row->load('rider')->toPickerArray()], 201);
    }

    /** Rename, renumber, or re-note one. The link to an account is not editable. */
    public function update(Request $request, StoreSavedRider $savedRider): JsonResponse
    {
        $context = $this->writableStoreContext($request);
        $this->authorizeSavedRider($savedRider, $context);

        $validated = $request->validate([
            'name' => ['sometimes', 'required', 'string', 'max:120'],
            'phone' => ['sometimes', 'nullable', 'string', 'max:40'],
            'note' => ['sometimes', 'nullable', 'string', 'max:160'],
        ]);

        // Only the keys actually sent are touched: PATCHing a note must not
        // blank the phone number the shop set last week.
        foreach ($validated as $key => $value) {
            $savedRider->{$key} = $key === 'name'
                ? trim((string) $value)
                : (trim((string) $value) ?: null);
        }

        $savedRider->save();

        return response()->json(['savedRider' => $savedRider->load('rider')->toPickerArray()]);
    }

    /**
     * Forget a rider.
     *
     * Only the shop's own row goes. Orders they already carried keep the name
     * and number recorded on them — that is the shop's delivery history, and
     * removing someone from a picker must not rewrite it.
     */
    public function destroy(Request $request, StoreSavedRider $savedRider): JsonResponse
    {
        $context = $this->writableStoreContext($request);
        $this->authorizeSavedRider($savedRider, $context);

        $savedRider->delete();

        return response()->json(['deleted' => true]);
    }

    /**
     * What makes two saves the same rider rather than two.
     *
     * A platform account is keyed on its id, which is what the unique index on
     * the table enforces. An off-platform rider cannot be — `rider_id` is null
     * and Postgres treats nulls as distinct — so the number is the key instead:
     * saving the same number twice edits the row rather than growing a second
     * copy of the person in the picker.
     *
     * With no number at all, the name is the key. That is a weaker rule and it
     * is the right one: two riders a shop has saved as "Jun" with no way to
     * reach either are the same entry as far as anyone at that counter is
     * concerned, and the alternative — keying on nothing — would let one
     * nameless-number save silently rename an unrelated one.
     *
     * @return array<string, mixed>
     */
    private function keyFor(StoreContext $context, ?Rider $rider, ?string $phone, string $name): array
    {
        if ($rider !== null) {
            return ['store_id' => $context->storeId(), 'rider_id' => $rider->id];
        }

        return $phone !== null
            ? ['store_id' => $context->storeId(), 'rider_id' => null, 'phone' => $phone]
            : ['store_id' => $context->storeId(), 'rider_id' => null, 'phone' => null, 'name' => $name];
    }

    private function hasWorkedForStore(Rider $rider, string $storeId): bool
    {
        return Order::query()
            ->where('store_id', $storeId)
            ->where('rider_id', $rider->id)
            ->exists();
    }

    /**
     * An approved rider whose number matches, comparing digits only.
     *
     * "0917 555 1234", "+639175551234" and "09175551234" are one person to
     * everybody except a string comparison. The last ten digits are compared
     * rather than the whole number, so a leading 0 and a +63 country code
     * agree with each other.
     */
    private function findApprovedByPhone(string $phone): ?Rider
    {
        $digits = preg_replace('/\D+/', '', $phone) ?? '';

        if (strlen($digits) < 10) {
            return null;
        }

        $tail = substr($digits, -10);

        return Rider::query()
            ->where('status', Rider::STATUS_APPROVED)
            ->get(['id', 'name', 'phone', 'status'])
            ->first(fn (Rider $rider) => str_ends_with(
                preg_replace('/\D+/', '', (string) $rider->phone) ?? '',
                $tail,
            ));
    }

    private function authorizeSavedRider(StoreSavedRider $savedRider, StoreContext $context): void
    {
        abort_unless(
            $savedRider->store_id === $context->storeId(),
            403,
            'That rider belongs to another shop.',
        );
    }

}
