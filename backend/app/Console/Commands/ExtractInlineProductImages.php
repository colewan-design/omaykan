<?php

namespace App\Console\Commands;

use App\Http\Controllers\Api\ProductImageController;
use App\Models\Product;
use App\Services\ImageRejected;
use App\Services\ImageStore;
use Illuminate\Console\Command;

/**
 * Move product photos stored as base64 in the product row out into files.
 *
 * From 2026-09-13 until sync started doing this on arrival, the till's
 * photographed products were saved with their picture inline in
 * `image_url` and `photo_urls`. This rewrites those rows once. New pushes are
 * handled by SyncController::withStoredImages and never need it.
 *
 * A photo that will not decode is left exactly as it was and reported: better
 * a bloated row than a product that has lost its picture. Idempotent — a row
 * with no data URL left in it is skipped — so running it twice is harmless.
 * `--dry-run` first, against production.
 *
 * See documentation/merchant-features.md §4.
 */
class ExtractInlineProductImages extends Command
{
    protected $signature = 'products:extract-inline-images {--dry-run : Report what would change and write nothing}';

    protected $description = 'Move base64 product photos out of product rows into stored files';

    public function handle(ImageStore $images): int
    {
        $dryRun = (bool) $this->option('dry-run');
        $moved = 0;
        $rows = 0;
        $unreadable = [];

        Product::withTrashed()
            ->where(fn ($q) => $q->where('image_url', 'like', 'data:%')->orWhere('photo_urls', 'like', '%data:%'))
            ->orderBy('id')
            ->each(function (Product $product) use ($images, $dryRun, &$moved, &$rows, &$unreadable) {
                $changed = false;

                $keep = function (?string $value) use ($images, $dryRun, $product, &$moved, &$changed, &$unreadable): ?string {
                    if (! ImageStore::isDataUrl($value)) {
                        return $value;
                    }

                    if ($dryRun) {
                        $moved++;

                        return $value;
                    }

                    try {
                        $url = ProductImageController::urlFor($images->store($value, ProductImageController::DIRECTORY));
                    } catch (ImageRejected $e) {
                        $unreadable[] = "{$product->id} ({$e->getMessage()})";

                        return $value;
                    }

                    $moved++;
                    $changed = true;

                    return $url;
                };

                $imageUrl = $keep($product->image_url);
                $gallery = array_map($keep, $product->photo_urls ?? []);

                $rows++;

                if ($changed) {
                    $product->forceFill([
                        'image_url' => $imageUrl,
                        'photo_urls' => $gallery === [] ? null : $gallery,
                    ])->saveQuietly();
                }
            });

        $this->info(($dryRun ? '[dry run] ' : '')."Products with inline photos: {$rows}. Photos ".($dryRun ? 'to move' : 'moved').": {$moved}.");

        if ($unreadable !== []) {
            $this->warn('Left inline, would not decode: '.implode('; ', $unreadable));
        }

        return self::SUCCESS;
    }
}
