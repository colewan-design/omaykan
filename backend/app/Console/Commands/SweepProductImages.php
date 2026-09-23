<?php

namespace App\Console\Commands;

use App\Http\Controllers\Api\ProductImageController;
use App\Models\Product;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Storage;

/**
 * Delete product photo files that no product points at any more.
 *
 * A replaced or removed photo leaves its file behind, and so does an upload
 * whose product was never saved — the seller app uploads first and saves
 * second. Scheduled weekly in routes/console.php.
 *
 * Only files older than a day: an upload from a minute ago is very likely a
 * product form that has not been saved *yet*. Soft-deleted products still
 * count as pointing at their photo, because a restored product should come
 * back with its picture.
 */
class SweepProductImages extends Command
{
    protected $signature = 'products:sweep-images {--dry-run : Report what would be deleted and delete nothing}';

    protected $description = 'Delete product photo files no product references';

    private const GRACE_SECONDS = 86400;

    public function handle(): int
    {
        $disk = Storage::disk('local');
        $dryRun = (bool) $this->option('dry-run');
        $cutoff = now()->getTimestamp() - self::GRACE_SECONDS;
        $deleted = 0;

        foreach ($disk->files(ProductImageController::DIRECTORY) as $path) {
            if ($disk->lastModified($path) > $cutoff) {
                continue;
            }

            $name = basename($path);

            $referenced = Product::withTrashed()
                ->where(fn ($q) => $q->where('image_url', 'like', "%{$name}")->orWhere('photo_urls', 'like', "%{$name}%"))
                ->exists();

            if ($referenced) {
                continue;
            }

            $deleted++;
            if (! $dryRun) {
                $disk->delete($path);
            }
        }

        $this->info(($dryRun ? '[dry run] Would delete' : 'Deleted')." {$deleted} unreferenced product photo(s).");

        return self::SUCCESS;
    }
}
