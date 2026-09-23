<?php

namespace Database\Seeders;

use App\Models\Rider;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Storage;

/**
 * A rider who can sign in, and one who cannot yet.
 *
 * Until this file existed there was no way to open `:rider` on a fresh database
 * at all. The app's own route in is `POST /api/rider/register`, which needs two
 * photographs and then leaves the account `pending` until an operator approves
 * it — a sensible front door for a real rider and a wall for anybody trying to
 * see the job board on a laptop. The one approved rider that did exist on this
 * machine had been made by hand in a console, with a password nobody wrote
 * down, which is exactly the state a seeder is for.
 *
 * `updateOrCreate` rather than `firstOrCreate`, and that is the whole point:
 * re-running this resets the password to the one documented below. A demo
 * credential that might have been changed is a demo credential you have to go
 * and check, which is no better than not having one.
 *
 * Two accounts, because `:rider` is two different apps depending on the answer
 * to one question — see EnsureRiderIsApproved and the SessionState split in
 * rider/README.md. The pending one is the only way to see the waiting screen
 * without suspending the account you are signed in with.
 *
 * Local and testing only. DatabaseSeeder chains it under the same environment
 * gate as DemoSellerSeeder: a live install must not grow an approved rider
 * whose password is in a public repository, and a rider token reaches real
 * customers' addresses and phone numbers.
 */
class RiderSeeder extends Seeder
{
    use WithoutModelEvents;

    /** The same password DemoSellerSeeder gives every demo account. */
    private const PASSWORD = 'password';

    /** Where RiderAuthController::storeDocument puts the real ones. */
    private const DOCUMENT_DIRECTORY = 'rider-documents';

    /**
     * A 16x16 grey PNG, written once per seeded document.
     *
     * `license_image_path` and `plate_image_path` are NOT NULL — a real rider
     * cannot register without photographing both — so a seeded rider has to
     * name a file, and naming one that is not on the private disk would put a
     * broken image in the operator's review screen rather than an obviously
     * placeholder one. Small and grey is the point: nobody should mistake it
     * for a licence.
     */
    private const PLACEHOLDER_PNG = 'iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAJUlEQVR4nGP8//8/A27AhEduFEgDMOKJJUZGRjxyIzGcRoE0AAB5DQQEfAIcCwAAAABJRU5ErkJggg==';

    public function run(): void
    {
        Rider::updateOrCreate(
            ['email' => 'rider@example.com'],
            [
                'name' => 'Ramon Delacruz',
                'phone' => '09170001234',
                'password' => Hash::make(self::PASSWORD),
                'license_number' => 'N01-23-456789',
                'plate_number' => 'BGO-1234',
                'license_image_path' => $this->placeholderDocument('ramon-license'),
                'plate_image_path' => $this->placeholderDocument('ramon-plate'),
                'status' => Rider::STATUS_APPROVED,
                'reviewed_at' => now(),
            ],
        );

        Rider::updateOrCreate(
            ['email' => 'rider-pending@example.com'],
            [
                'name' => 'Bea Tolentino',
                'phone' => '09170005678',
                'password' => Hash::make(self::PASSWORD),
                'license_number' => 'N02-34-567890',
                'plate_number' => 'BGO-5678',
                'license_image_path' => $this->placeholderDocument('bea-license'),
                'plate_image_path' => $this->placeholderDocument('bea-plate'),
                'status' => Rider::STATUS_PENDING,
                'reviewed_at' => null,
            ],
        );

        $this->command?->info(sprintf(
            'Riders: rider@example.com (approved) and rider-pending@example.com (pending), password "%s".',
            self::PASSWORD,
        ));
    }

    /**
     * A named placeholder on the private disk, written only if it is missing.
     *
     * Named rather than random — RiderAuthController hashes real uploads so an
     * attacker-chosen filename never reaches the disk, but a seeder has no
     * attacker and a stable name means re-running this leaves one file per
     * document instead of a new one every time.
     */
    private function placeholderDocument(string $name): string
    {
        $path = self::DOCUMENT_DIRECTORY."/seed-{$name}.png";

        if (! Storage::disk('local')->exists($path)) {
            Storage::disk('local')->put($path, base64_decode(self::PLACEHOLDER_PNG));
        }

        return $path;
    }
}
