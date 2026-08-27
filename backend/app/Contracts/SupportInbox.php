<?php

namespace App\Contracts;

interface SupportInbox
{
    /**
     * @return array{messages: array<int, array<string, mixed>>, unreadCount: int}
     */
    public function listMessages(int $limit = 25): array;

    /**
     * @return array<string, mixed>|null
     */
    public function getMessage(string $id, bool $markSeen = true): ?array;
}
