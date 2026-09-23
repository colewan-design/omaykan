<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * Messages between a shopper and a shop.
 *
 * ## One conversation per shop and customer
 *
 * Not one per order. The first thing most people want to ask a shop is
 * whether it has something, which is before there is an order to hang it on,
 * and a shop answering the same customer in six threads because they have
 * ordered six times is six places to lose the answer. A message can still
 * *name* an order — `conversation_messages.order_id` — which is how "about
 * order #0042" survives without the order owning the thread.
 *
 * The unique index on (store_id, customer_account_id) is what makes "open the
 * conversation with this shop" a lookup rather than a search, and what turns a
 * double-tap on Send into one row instead of two threads.
 *
 * ## Unread as counters, not read-at timestamps
 *
 * `customer_unread` and `store_unread` count the other side's messages each
 * side has not opened. The obvious alternative — a read-at timestamp compared
 * against each message's created_at — breaks at second precision: a reply
 * landing in the same second the customer opened the thread reads as already
 * seen, and nobody is ever told about it. A counter has no such window.
 *
 * ## Why messages have an integer id
 *
 * Everything else here is a UUID, but a thread has to come back in the order
 * it was written, and two messages sent in the same second tie on created_at.
 * An auto-incrementing id is the tiebreak that cannot tie. It is never a
 * capability — every read goes through the conversation, which is scoped to
 * the customer or the store asking — so there is nothing to gain from it being
 * unguessable.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('conversations', function (Blueprint $table) {
            $table->uuid('id')->primary();

            $table->foreignUuid('organization_id')->constrained()->cascadeOnDelete();
            $table->foreignUuid('store_id')->constrained()->cascadeOnDelete();
            $table->foreignUuid('customer_account_id')
                ->constrained('customer_accounts')->cascadeOnDelete();

            $table->timestamp('last_message_at')->nullable();
            $table->unsignedInteger('customer_unread')->default(0);
            $table->unsignedInteger('store_unread')->default(0);

            $table->timestamps();

            $table->unique(['store_id', 'customer_account_id']);
            // Both inboxes list newest first.
            $table->index(['store_id', 'last_message_at']);
            $table->index(['customer_account_id', 'last_message_at']);
        });

        Schema::create('conversation_messages', function (Blueprint $table) {
            $table->id();

            $table->foreignUuid('conversation_id')->constrained()->cascadeOnDelete();
            // 'customer' or 'store'. Which *person* at the store is `user_id`.
            $table->string('sender', 16);
            // The member of staff who wrote a store message. Null for the
            // customer's own, and kept null rather than cascading when that
            // person leaves — the shop said it, whoever typed it.
            $table->foreignUuid('user_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignUuid('order_id')->nullable()->constrained('orders')->nullOnDelete();

            $table->text('body');

            $table->timestamps();

            $table->index(['conversation_id', 'id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('conversation_messages');
        Schema::dropIfExists('conversations');
    }
};
