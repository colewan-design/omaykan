<?php

namespace App\Models;

use App\Http\Controllers\Api\StoreImageController;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\HasOne;
use Illuminate\Support\Facades\DB;

/**
 * One shopper talking to one shop.
 *
 * See the migration for why this is per shop rather than per order, and why
 * unread is a pair of counters.
 */
class Conversation extends Model
{
    use HasUuids;

    public const SENDER_CUSTOMER = 'customer';
    public const SENDER_STORE = 'store';

    /** Long enough for a real question, short enough to stay a message. */
    public const MAX_BODY = 2000;

    protected $fillable = [
        'organization_id',
        'store_id',
        'customer_account_id',
    ];

    protected function casts(): array
    {
        return [
            'last_message_at' => 'datetime',
            'customer_unread' => 'integer',
            'store_unread' => 'integer',
        ];
    }

    public function store(): BelongsTo
    {
        return $this->belongsTo(Store::class);
    }

    public function customerAccount(): BelongsTo
    {
        return $this->belongsTo(CustomerAccount::class);
    }

    public function messages(): HasMany
    {
        return $this->hasMany(ConversationMessage::class);
    }

    /** For the inbox preview line. By id, not created_at — see the migration. */
    public function latestMessage(): HasOne
    {
        return $this->hasOne(ConversationMessage::class)->latestOfMany('id');
    }

    /**
     * Append a message, and move both unread counters.
     *
     * The other side gains one. The sender's own goes to zero: replying to a
     * thread is proof of having read it, and a shop that answers from the
     * list without opening the thread should not be left with a badge for
     * the message it just answered.
     */
    public function post(string $sender, string $body, ?User $author = null, ?Order $order = null): ConversationMessage
    {
        $message = DB::transaction(function () use ($sender, $body, $author, $order) {
            $message = $this->messages()->create([
                'sender' => $sender,
                'user_id' => $sender === self::SENDER_STORE ? $author?->getKey() : null,
                'order_id' => $order?->getKey(),
                'body' => $body,
            ]);

            [$theirs, $mine] = $sender === self::SENDER_CUSTOMER
                ? ['store_unread', 'customer_unread']
                : ['customer_unread', 'store_unread'];

            // A single UPDATE with an increment, not read-add-save: two
            // messages arriving together must both count.
            static::query()->whereKey($this->getKey())->increment($theirs, 1, [
                $mine => 0,
                'last_message_at' => $message->created_at,
            ]);

            return $message;
        });

        $this->refresh();

        return $message;
    }

    /**
     * Clear one side's unread count.
     *
     * Called *before* the thread is read back, never after: a message landing
     * between the two then shows up in the thread and still counts as unread,
     * which is a badge that clears on the next poll. The other order would be
     * a message counted as read that was never on screen.
     */
    public function markReadBy(string $side): void
    {
        $column = $side === self::SENDER_CUSTOMER ? 'customer_unread' : 'store_unread';

        static::query()->whereKey($this->getKey())->toBase()->update([$column => 0]);

        $this->setAttribute($column, 0);
        $this->syncOriginalAttribute($column);
    }

    /**
     * The customer's inbox row.
     *
     * The shop is named the way the directory names it, so the portal can link
     * back to its shelves without a second lookup.
     *
     * @return array<string, mixed>
     */
    public function toCustomerSummary(): array
    {
        $this->loadMissing('store.organization', 'latestMessage.order');
        $store = $this->store;

        return [
            'id' => $this->id,
            'store' => [
                'id' => $store?->id,
                'name' => $store?->name ?? 'Shop',
                'orgSlug' => $store?->organization?->slug,
                'storeCode' => $store?->code,
                'imageUrl' => $store ? StoreImageController::urlFor($store) : null,
            ],
            'lastMessage' => $this->latestMessage?->toCustomerArray(),
            'lastMessageAt' => $this->last_message_at?->toIso8601String(),
            'unreadCount' => (int) $this->customer_unread,
        ];
    }

    /**
     * The shop's inbox row.
     *
     * The customer's name and nothing else. Their email and phone are the
     * customer's to give — they can type either into a message — and a shop
     * that has only ever been asked "do you have eggs" has no reason to hold
     * them.
     *
     * @return array<string, mixed>
     */
    public function toStoreSummary(): array
    {
        $this->loadMissing('customerAccount', 'latestMessage.order', 'latestMessage.author');

        return [
            'id' => $this->id,
            'customer' => [
                'name' => $this->customerAccount?->name ?? 'Customer',
            ],
            'lastMessage' => $this->latestMessage?->toStoreArray(),
            'lastMessageAt' => $this->last_message_at?->toIso8601String(),
            'unreadCount' => (int) $this->store_unread,
        ];
    }
}
