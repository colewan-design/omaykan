<?php

namespace App\Support;

/**
 * A password meant to be read aloud.
 *
 * Both places that generate one — resetting a merchant owner's login, and
 * creating an operator account — hand the result to a human who relays it by
 * chat or over the phone. So no 0/O and no 1/I/l: the alphabet is chosen for
 * transcription, not entropy density, and the length makes up the difference.
 *
 * Lifted out of PlatformAdminController, which had it as a private const, so
 * the two callers cannot drift apart on it.
 */
class ReadablePassword
{
    private const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';

    public static function generate(int $length = 12): string
    {
        $password = '';
        $max = strlen(self::ALPHABET) - 1;

        for ($i = 0; $i < $length; $i++) {
            $password .= self::ALPHABET[random_int(0, $max)];
        }

        return $password;
    }
}
