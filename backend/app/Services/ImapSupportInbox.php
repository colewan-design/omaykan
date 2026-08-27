<?php

namespace App\Services;

use App\Contracts\SupportInbox;
use Carbon\CarbonImmutable;
use RuntimeException;

class ImapSupportInbox implements SupportInbox
{
    public function listMessages(int $limit = 25): array
    {
        $limit = max(1, min($limit, 100));
        $imap = $this->openConnection();

        try {
            $uids = imap_sort($imap, SORTARRIVAL, 1, SE_UID, 'ALL') ?: [];
            $uids = array_slice(array_map('intval', $uids), 0, $limit);
            $unread = imap_search($imap, 'UNSEEN', SE_UID) ?: [];

            return [
                'messages' => array_values(array_filter(array_map(
                    fn (int $uid) => $this->summarizeMessage($imap, $uid),
                    $uids,
                ))),
                'unreadCount' => count($unread),
            ];
        } finally {
            imap_close($imap);
        }
    }

    public function getMessage(string $id, bool $markSeen = true): ?array
    {
        $uid = $this->normalizeUid($id);
        $imap = $this->openConnection();

        try {
            $overview = $this->fetchOverview($imap, $uid);

            if ($overview === null) {
                return null;
            }

            if ($markSeen) {
                imap_setflag_full($imap, (string) $uid, '\\Seen', ST_UID);
                $overview->seen = 1;
            }

            $msgno = imap_msgno($imap, $uid);
            $header = $msgno > 0 ? imap_headerinfo($imap, $msgno) : false;
            $body = $this->extractBody($imap, $uid);

            $from = $this->parseAddress((string) ($overview->from ?? ''));
            $replyTo = $this->parseHeaderReplyTo($header) ?? $from;
            $subject = $this->decodeHeader((string) ($overview->subject ?? ''));

            return [
                'id' => (string) $uid,
                'subject' => $subject !== '' ? $subject : '(No subject)',
                'fromName' => $from['name'],
                'fromEmail' => $from['email'],
                'replyToName' => $replyTo['name'],
                'replyToEmail' => $replyTo['email'],
                'receivedAt' => $this->normalizeDate((string) ($overview->date ?? '')),
                'snippet' => $this->snippetFrom($body),
                'body' => $body,
                'isRead' => (bool) ($overview->seen ?? false),
                'messageIdHeader' => trim((string) ($overview->message_id ?? '')),
                'references' => trim((string) ($overview->references ?? '')),
                'inReplyTo' => trim((string) ($overview->in_reply_to ?? '')),
            ];
        } finally {
            imap_close($imap);
        }
    }

    private function openConnection()
    {
        if (! function_exists('imap_open')) {
            throw new RuntimeException('Support inbox requires the PHP IMAP extension.');
        }

        $host = trim((string) config('services.support_inbox.host'));
        $port = (int) config('services.support_inbox.port');
        $username = trim((string) config('services.support_inbox.username'));
        $password = (string) config('services.support_inbox.password');
        $mailbox = trim((string) config('services.support_inbox.mailbox', 'INBOX'));

        if ($host === '' || $port <= 0 || $username === '' || $password === '' || $mailbox === '') {
            throw new RuntimeException('Support inbox is not configured. Set SUPPORT_INBOX_* in the server environment.');
        }

        $stream = @imap_open(
            $this->mailboxPath($host, $port, $mailbox),
            $username,
            $password,
            0,
            1,
            ['DISABLE_AUTHENTICATOR' => 'GSSAPI'],
        );

        if ($stream === false) {
            $error = trim((string) imap_last_error());
            throw new RuntimeException(
                'Support inbox could not be opened.'.($error !== '' ? ' '.$error : ''),
            );
        }

        return $stream;
    }

    private function mailboxPath(string $host, int $port, string $mailbox): string
    {
        $flags = ['/imap'];
        $encryption = strtolower(trim((string) config('services.support_inbox.encryption', 'ssl')));

        if ($encryption === 'ssl') {
            $flags[] = '/ssl';
        } elseif ($encryption === 'tls') {
            $flags[] = '/tls';
        } elseif ($encryption !== '' && $encryption !== 'none') {
            throw new RuntimeException('SUPPORT_INBOX_ENCRYPTION must be one of ssl, tls, or none.');
        }

        if (! (bool) config('services.support_inbox.validate_cert', true)) {
            $flags[] = '/novalidate-cert';
        }

        return sprintf('{%s:%d%s}%s', $host, $port, implode('', $flags), $mailbox);
    }

    private function summarizeMessage($imap, int $uid): ?array
    {
        $overview = $this->fetchOverview($imap, $uid);

        if ($overview === null) {
            return null;
        }

        $subject = $this->decodeHeader((string) ($overview->subject ?? ''));
        $from = $this->parseAddress((string) ($overview->from ?? ''));
        $body = $this->extractBody($imap, $uid);

        return [
            'id' => (string) $uid,
            'subject' => $subject !== '' ? $subject : '(No subject)',
            'fromName' => $from['name'],
            'fromEmail' => $from['email'],
            'receivedAt' => $this->normalizeDate((string) ($overview->date ?? '')),
            'snippet' => $this->snippetFrom($body),
            'isRead' => (bool) ($overview->seen ?? false),
        ];
    }

    private function fetchOverview($imap, int $uid): ?object
    {
        $result = imap_fetch_overview($imap, (string) $uid, FT_UID);

        if (! is_array($result) || ! isset($result[0]) || ! is_object($result[0])) {
            return null;
        }

        return $result[0];
    }

    private function normalizeUid(string $id): int
    {
        if (! ctype_digit($id) || (int) $id <= 0) {
            throw new RuntimeException('Invalid support inbox message id.');
        }

        return (int) $id;
    }

    private function normalizeDate(string $value): ?string
    {
        if (trim($value) === '') {
            return null;
        }

        try {
            return CarbonImmutable::parse($value)->toIso8601String();
        } catch (\Throwable) {
            return null;
        }
    }

    private function decodeHeader(string $value): string
    {
        if (trim($value) === '') {
            return '';
        }

        $parts = imap_mime_header_decode($value);

        if (! is_array($parts) || $parts === []) {
            return trim($value);
        }

        return trim(implode('', array_map(
            fn ($part) => property_exists($part, 'text') ? (string) $part->text : '',
            $parts,
        )));
    }

    /**
     * @return array{name: string, email: string}
     */
    private function parseAddress(string $value): array
    {
        if (trim($value) === '') {
            return ['name' => 'Unknown sender', 'email' => ''];
        }

        $addresses = imap_rfc822_parse_adrlist($value, '');
        $first = is_array($addresses) ? ($addresses[0] ?? null) : null;

        if (! is_object($first)) {
            return ['name' => trim($value), 'email' => ''];
        }

        $email = '';
        if (
            isset($first->mailbox, $first->host)
            && $first->mailbox !== 'UNKNOWN'
            && $first->host !== '.SYNTAX-ERROR.'
        ) {
            $email = strtolower(trim($first->mailbox.'@'.$first->host));
        }

        $name = isset($first->personal) ? $this->decodeHeader((string) $first->personal) : '';
        if ($name === '') {
            $name = $email !== '' ? $email : trim($value);
        }

        return ['name' => $name, 'email' => $email];
    }

    /**
     * @return array{name: string, email: string}|null
     */
    private function parseHeaderReplyTo(object|false $header): ?array
    {
        if (! is_object($header) || ! isset($header->reply_to) || ! is_array($header->reply_to)) {
            return null;
        }

        $first = $header->reply_to[0] ?? null;
        if (! is_object($first) || ! isset($first->mailbox, $first->host)) {
            return null;
        }

        $email = strtolower(trim($first->mailbox.'@'.$first->host));
        $name = isset($first->personal) ? $this->decodeHeader((string) $first->personal) : $email;

        return ['name' => $name, 'email' => $email];
    }

    private function extractBody($imap, int $uid): string
    {
        $structure = imap_fetchstructure($imap, (string) $uid, FT_UID);

        if (! is_object($structure)) {
            return '';
        }

        $parts = $this->collectTextParts($imap, $uid, $structure);
        $plain = trim(implode("\n\n", $parts['plain']));

        if ($plain !== '') {
            return $plain;
        }

        return $this->htmlToText(implode("\n\n", $parts['html']));
    }

    /**
     * @return array{plain: array<int, string>, html: array<int, string>}
     */
    private function collectTextParts($imap, int $uid, object $part, ?string $partNumber = null): array
    {
        $collected = ['plain' => [], 'html' => []];

        if (isset($part->parts) && is_array($part->parts) && $part->parts !== []) {
            foreach ($part->parts as $index => $child) {
                if (! is_object($child)) {
                    continue;
                }

                $childPartNumber = $partNumber === null ? (string) ($index + 1) : $partNumber.'.'.($index + 1);
                $childBodies = $this->collectTextParts($imap, $uid, $child, $childPartNumber);
                $collected['plain'] = [...$collected['plain'], ...$childBodies['plain']];
                $collected['html'] = [...$collected['html'], ...$childBodies['html']];
            }

            return $collected;
        }

        if ($this->partIsAttachment($part)) {
            return $collected;
        }

        $type = strtoupper((string) ($part->subtype ?? ''));
        $data = $this->decodeBody(
            $this->fetchPartBody($imap, $uid, $partNumber),
            (int) ($part->encoding ?? 0),
        );

        if (trim($data) === '') {
            return $collected;
        }

        if ((int) ($part->type ?? -1) === 0 && $type === 'PLAIN') {
            $collected['plain'][] = trim($data);
        } elseif ((int) ($part->type ?? -1) === 0 && $type === 'HTML') {
            $collected['html'][] = trim($data);
        }

        return $collected;
    }

    private function partIsAttachment(object $part): bool
    {
        $disposition = strtolower((string) ($part->disposition ?? ''));
        if (in_array($disposition, ['attachment', 'inline'], true) && $this->partHasFilename($part)) {
            return true;
        }

        return false;
    }

    private function partHasFilename(object $part): bool
    {
        foreach (['ifdparameters' => 'dparameters', 'ifparameters' => 'parameters'] as $if => $key) {
            if (! isset($part->{$if}) || (int) $part->{$if} !== 1 || ! isset($part->{$key}) || ! is_array($part->{$key})) {
                continue;
            }

            foreach ($part->{$key} as $parameter) {
                if (! is_object($parameter) || ! isset($parameter->attribute)) {
                    continue;
                }

                $attribute = strtolower((string) $parameter->attribute);
                if (in_array($attribute, ['filename', 'name'], true)) {
                    return true;
                }
            }
        }

        return false;
    }

    private function fetchPartBody($imap, int $uid, ?string $partNumber): string
    {
        if ($partNumber === null) {
            return (string) imap_body($imap, (string) $uid, FT_UID | FT_PEEK);
        }

        return (string) imap_fetchbody($imap, (string) $uid, $partNumber, FT_UID | FT_PEEK);
    }

    private function decodeBody(string $body, int $encoding): string
    {
        return match ($encoding) {
            ENCBASE64 => base64_decode($body, true) ?: '',
            ENCQUOTEDPRINTABLE => quoted_printable_decode($body),
            default => $body,
        };
    }

    private function htmlToText(string $html): string
    {
        if (trim($html) === '') {
            return '';
        }

        $html = preg_replace('/<(br|\/p|\/div|\/li|\/tr|\/h[1-6])[^>]*>/i', "\n", $html) ?? $html;
        $text = strip_tags($html);
        $text = html_entity_decode($text, ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $text = preg_replace("/\r\n?/", "\n", $text) ?? $text;
        $text = preg_replace("/\n{3,}/", "\n\n", $text) ?? $text;

        return trim($text);
    }

    private function snippetFrom(string $body): string
    {
        $normalized = preg_replace('/\s+/', ' ', trim($body)) ?? trim($body);

        if ($normalized === '') {
            return 'No preview available.';
        }

        return mb_strimwidth($normalized, 0, 160, '…');
    }
}
