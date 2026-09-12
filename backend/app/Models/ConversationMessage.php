<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * One line in a Conversation.
 *
 * Two renderings because the two readers are owed different things: the shop
 * sees which of its staff wrote a reply, so a manager can tell who told a
 * customer what; the customer sees the shop, because that is who they are
 * talking to and a cashier's name is not theirs to hand out.
 */
class ConversationMessage extends Model
{
    protected $fillable = [
        'conversation_id',
        'sender',
        'user_id',
        'order_id',
        'body',
    ];

    public function conversation(): BelongsTo
    {
        return $this->belongsTo(Conversation::class);
    }

    public function author(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    public function order(): BelongsTo
    {
        return $this->belongsTo(Order::class);
    }

    /** @return array<string, mixed> */
    public function toCustomerArray(): array
    {
        return [
            'id' => $this->id,
            'from' => $this->sender,
            'body' => $this->body,
            'order' => $this->orderReference(),
            'createdAt' => $this->created_at?->toIso8601String(),
        ];
    }

    /** @return array<string, mixed> */
    public function toStoreArray(): array
    {
        // First name only, the way the rest of the app greets staff.
        $author = $this->sender === Conversation::SENDER_STORE
            ? (trim(explode(' ', trim((string) $this->author?->name))[0]) ?: null)
            : null;

        return [
            ...$this->toCustomerArray(),
            'authorName' => $author,
        ];
    }

    /**
     * The ticket number, which is what both sides say out loud. Null once the
     * order is gone — a soft-deleted order falls out of the relation and the
     * message keeps its words.
     *
     * @return array<string, string>|null
     */
    private function orderReference(): ?array
    {
        $order = $this->relationLoaded('order') ? $this->getRelation('order') : $this->order;

        return $order === null ? null : [
            'id' => $order->id,
            'ticketNumber' => $order->ticket_number,
        ];
    }
}
