<?php

namespace Tests\Feature\Api;

use App\Contracts\SupportInbox;
use App\Mail\SupportInboxReplyMail;
use App\Models\PlatformAdmin;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Mail;
use Symfony\Component\Mime\Email;
use Tests\TestCase;

class PlatformAdminInboxApiTest extends TestCase
{
    use RefreshDatabase;

    private const OPERATOR_PASSWORD = 'operator-password-1234';

    private PlatformAdmin $operator;

    private FakeSupportInbox $inbox;

    protected function setUp(): void
    {
        parent::setUp();

        $this->operator = PlatformAdmin::query()->create([
            'name' => 'Platform Operator',
            'email' => 'operator@example.test',
            'password' => self::OPERATOR_PASSWORD,
        ]);

        $this->inbox = new FakeSupportInbox([
            [
                'id' => '101',
                'subject' => 'Refund question',
                'fromName' => 'Ana Reyes',
                'fromEmail' => 'ana@example.test',
                'replyToName' => 'Ana Reyes',
                'replyToEmail' => 'ana-replies@example.test',
                'receivedAt' => '2026-08-27T08:00:00+08:00',
                'snippet' => 'Can someone help with an order refund?',
                'body' => "Can someone help with an order refund?\nOrder #123",
                'isRead' => false,
                'messageIdHeader' => '<msg-101@example.test>',
                'references' => '<prior@example.test>',
                'inReplyTo' => '',
            ],
            [
                'id' => '102',
                'subject' => 'GCash confirmation',
                'fromName' => 'Ben Cruz',
                'fromEmail' => 'ben@example.test',
                'replyToName' => 'Ben Cruz',
                'replyToEmail' => 'ben@example.test',
                'receivedAt' => '2026-08-26T15:00:00+08:00',
                'snippet' => 'Following up on my payment confirmation.',
                'body' => 'Following up on my payment confirmation.',
                'isRead' => true,
                'messageIdHeader' => '<msg-102@example.test>',
                'references' => '',
                'inReplyTo' => '',
            ],
        ]);

        $this->app->bind(SupportInbox::class, fn () => $this->inbox);
    }

    private function operatorToken(): string
    {
        return $this->postJson('/api/platform-admin/login', [
            'email' => $this->operator->email,
            'password' => self::OPERATOR_PASSWORD,
        ])->assertOk()->json('token');
    }

    public function test_operator_sign_in_is_required_to_read_the_support_inbox(): void
    {
        $this->postJson('/api/platform-admin/inbox')->assertStatus(401);
        $this->getJson('/api/platform-admin/inbox/101')->assertStatus(401);
        $this->postJson('/api/platform-admin/inbox/101/reply', ['message' => 'Hello'])->assertStatus(401);
    }

    public function test_the_support_inbox_lists_messages_and_unread_count(): void
    {
        $this->withToken($this->operatorToken())
            ->postJson('/api/platform-admin/inbox', ['limit' => 30])
            ->assertOk()
            ->assertJsonPath('unreadCount', 1)
            ->assertJsonPath('messages.0.id', '101')
            ->assertJsonPath('messages.0.fromEmail', 'ana@example.test')
            ->assertJsonPath('messages.1.isRead', true);
    }

    public function test_opening_a_message_returns_the_body_and_marks_it_seen(): void
    {
        $this->withToken($this->operatorToken())
            ->getJson('/api/platform-admin/inbox/101')
            ->assertOk()
            ->assertJsonPath('message.id', '101')
            ->assertJsonPath('message.replyToEmail', 'ana-replies@example.test')
            ->assertJsonPath('message.body', "Can someone help with an order refund?\nOrder #123");

        $this->assertSame([['id' => '101', 'markSeen' => true]], $this->inbox->getMessageCalls);
    }

    public function test_replying_to_a_message_queues_a_threaded_email(): void
    {
        Mail::fake();

        $this->withToken($this->operatorToken())
            ->postJson('/api/platform-admin/inbox/101/reply', [
                'message' => "We found the order and we're fixing it now.",
            ])
            ->assertOk()
            ->assertJsonPath('queued', true)
            ->assertJsonPath('to', 'ana-replies@example.test')
            ->assertJsonPath('subject', 'Re: Refund question');

        $this->assertSame([['id' => '101', 'markSeen' => false]], $this->inbox->getMessageCalls);

        Mail::assertQueued(SupportInboxReplyMail::class, function (SupportInboxReplyMail $mail) {
            $email = new Email();
            foreach ($mail->envelope()->using as $callback) {
                $callback($email);
            }

            return $mail->hasTo('ana-replies@example.test')
                && $mail->subjectLine === 'Re: Refund question'
                && $mail->messageBody === "We found the order and we're fixing it now."
                && $email->getHeaders()->get('In-Reply-To')?->getBodyAsString() === '<msg-101@example.test>'
                && $email->getHeaders()->get('References')?->getBodyAsString() === '<prior@example.test> <msg-101@example.test>';
        });
    }

    public function test_replying_to_a_missing_message_returns_not_found(): void
    {
        Mail::fake();

        $this->withToken($this->operatorToken())
            ->postJson('/api/platform-admin/inbox/999/reply', [
                'message' => 'Hello',
            ])
            ->assertStatus(404);

        Mail::assertNothingQueued();
    }
}

class FakeSupportInbox implements SupportInbox
{
    /** @var array<int, array<string, mixed>> */
    private array $messages;

    /** @var array<int, array{id: string, markSeen: bool}> */
    public array $getMessageCalls = [];

    /**
     * @param  array<int, array<string, mixed>>  $messages
     */
    public function __construct(array $messages)
    {
        $this->messages = $messages;
    }

    public function listMessages(int $limit = 25): array
    {
        $messages = array_slice($this->messages, 0, $limit);

        return [
            'messages' => array_map(fn (array $message) => [
                'id' => $message['id'],
                'subject' => $message['subject'],
                'fromName' => $message['fromName'],
                'fromEmail' => $message['fromEmail'],
                'receivedAt' => $message['receivedAt'],
                'snippet' => $message['snippet'],
                'isRead' => $message['isRead'],
            ], $messages),
            'unreadCount' => count(array_filter($this->messages, fn (array $message) => ! $message['isRead'])),
        ];
    }

    public function getMessage(string $id, bool $markSeen = true): ?array
    {
        $this->getMessageCalls[] = ['id' => $id, 'markSeen' => $markSeen];

        foreach ($this->messages as $index => $message) {
            if ($message['id'] !== $id) {
                continue;
            }

            if ($markSeen) {
                $this->messages[$index]['isRead'] = true;
                $message['isRead'] = true;
            }

            return $message;
        }

        return null;
    }
}
