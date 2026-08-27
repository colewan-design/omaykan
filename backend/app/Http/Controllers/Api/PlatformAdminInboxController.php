<?php

namespace App\Http\Controllers\Api;

use App\Contracts\SupportInbox;
use App\Http\Controllers\Controller;
use App\Mail\SupportInboxReplyMail;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Mail;
use RuntimeException;

class PlatformAdminInboxController extends Controller
{
    public function index(Request $request, SupportInbox $inbox): JsonResponse
    {
        $validated = $request->validate([
            'limit' => ['nullable', 'integer', 'min:1', 'max:100'],
        ]);

        return response()->json(
            $this->attempt(fn () => $inbox->listMessages($validated['limit'] ?? 25)),
        );
    }

    public function show(Request $request, string $messageId, SupportInbox $inbox): JsonResponse
    {
        $message = $this->attempt(fn () => $inbox->getMessage($messageId, true));

        abort_if($message === null, 404, 'That support message was not found.');

        return response()->json(['message' => $message]);
    }

    public function reply(Request $request, string $messageId, SupportInbox $inbox): JsonResponse
    {
        $validated = $request->validate([
            'subject' => ['nullable', 'string', 'max:190'],
            'message' => ['required', 'string', 'max:5000'],
        ]);

        $message = $this->attempt(fn () => $inbox->getMessage($messageId, false));

        abort_if($message === null, 404, 'That support message was not found.');

        $recipient = trim((string) ($message['replyToEmail'] ?: $message['fromEmail'] ?? ''));
        abort_if($recipient === '', 422, 'That message has no replyable sender address.');

        $subject = trim((string) ($validated['subject'] ?? ''));
        if ($subject === '') {
            $subject = $this->replySubject((string) ($message['subject'] ?? ''));
        }

        Mail::to($recipient)->queue(new SupportInboxReplyMail(
            subjectLine: $subject,
            messageBody: trim($validated['message']),
            inReplyTo: $this->replyHeader((string) ($message['messageIdHeader'] ?? ''), (string) ($message['inReplyTo'] ?? '')),
            references: $this->referencesHeader(
                (string) ($message['references'] ?? ''),
                (string) ($message['messageIdHeader'] ?? ''),
            ),
        ));

        Log::info('[support-inbox] replied', [
            'messageId' => $messageId,
            'to' => $recipient,
            'subject' => $subject,
        ]);

        return response()->json(['queued' => true, 'to' => $recipient, 'subject' => $subject]);
    }

    private function attempt(callable $operation): mixed
    {
        try {
            return $operation();
        } catch (RuntimeException $exception) {
            abort(500, $exception->getMessage());
        }
    }

    private function replySubject(string $subject): string
    {
        $subject = trim($subject);

        if ($subject === '') {
            return 'Re: your message to Omaykan support';
        }

        return preg_match('/^re:/i', $subject) === 1 ? $subject : 'Re: '.$subject;
    }

    private function replyHeader(string $messageId, string $inReplyTo): string
    {
        $messageId = trim($messageId);
        $inReplyTo = trim($inReplyTo);

        return $messageId !== '' ? $messageId : $inReplyTo;
    }

    private function referencesHeader(string $references, string $messageId): string
    {
        $tokens = preg_split('/\s+/', trim($references.' '.$messageId)) ?: [];
        $tokens = array_values(array_unique(array_filter($tokens, fn ($value) => trim((string) $value) !== '')));

        return implode(' ', $tokens);
    }
}
