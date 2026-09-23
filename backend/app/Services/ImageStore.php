<?php

namespace App\Services;

use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

/**
 * Turning a data URL into a file we are willing to keep.
 *
 * The till and the seller app both hold pictures as data URLs, because that
 * is what a browser's FileReader and Android's encoder hand over. This is the
 * one place such a string is decoded, checked and written — the shop photo
 * (StoreImageController) and product photos (ProductImageController, and
 * SyncController pulling inline photos out of product rows) all come through
 * here, so there is one set of rules about what counts as an image.
 *
 * The string is treated as hostile until the decoded bytes have described
 * themselves as an image of a type we accept. The declared mime alone is worth
 * nothing: a PHP file relabelled `data:image/png` would pass that check.
 *
 * Files land on the private `local` disk and are streamed back by a
 * controller; there is no public directory to browse.
 */
class ImageStore
{
    /** Declared type => the extension it is stored under. */
    public const ALLOWED = [
        'image/jpeg' => 'jpg',
        'image/png' => 'png',
        'image/webp' => 'webp',
    ];

    /** 3MB decoded. A phone photo fits; a payload meant to hurt does not. */
    public const MAX_BYTES = 3145728;

    public static function isDataUrl(?string $value): bool
    {
        return is_string($value) && str_starts_with(ltrim($value), 'data:');
    }

    /**
     * Decode, check and store. Returns the stored path, e.g.
     * `product-images/0b7c….jpg`.
     *
     * @throws ImageRejected with a sentence fit to show the person who sent it
     */
    public function store(string $dataUrl, string $directory): string
    {
        [$mime, $bytes] = $this->decode($dataUrl);

        $path = trim($directory, '/').'/'.Str::uuid().'.'.self::ALLOWED[$mime];
        Storage::disk('local')->put($path, $bytes);

        return $path;
    }

    public function forget(?string $path): void
    {
        if ($path !== null && $path !== '') {
            Storage::disk('local')->delete($path);
        }
    }

    /**
     * @return array{0: string, 1: string} the mime type and the raw bytes
     *
     * @throws ImageRejected
     */
    private function decode(string $dataUrl): array
    {
        if (preg_match('#^data:(image/[a-z0-9.+-]+);base64,(.+)$#is', trim($dataUrl), $matches) !== 1) {
            throw new ImageRejected('That image could not be read.');
        }

        $mime = strtolower($matches[1]);
        if (! isset(self::ALLOWED[$mime])) {
            throw new ImageRejected('Use a JPEG, PNG, or WebP image.');
        }

        // Strict: base64_decode() otherwise silently skips anything that is
        // not base64 and returns a shorter string rather than failing.
        $bytes = base64_decode($matches[2], true);
        if ($bytes === false || $bytes === '') {
            throw new ImageRejected('That image could not be read.');
        }

        if (strlen($bytes) > self::MAX_BYTES) {
            throw new ImageRejected('That image is too large — keep it under 3MB.');
        }

        $info = @getimagesizefromstring($bytes);
        if ($info === false || ($info['mime'] ?? null) !== $mime) {
            throw new ImageRejected('That file is not an image.');
        }

        return [$mime, $bytes];
    }
}
