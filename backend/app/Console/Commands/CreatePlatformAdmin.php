<?php

namespace App\Console\Commands;

use App\Models\PlatformAdmin;
use Illuminate\Console\Command;
use Illuminate\Support\Str;

/**
 * The only way an account gains cross-tenant access.
 *
 * Console rather than an endpoint on purpose — see PlatformAdminAuthController.
 * Creating one of these is a deliberate act performed on the server by someone
 * who already has the server, which is the property that makes the absence of
 * a password-reset endpoint acceptable.
 */
class CreatePlatformAdmin extends Command
{
    protected $signature = 'platform-admin:create
        {email : The operator\'s email address, used as the login handle}
        {--name= : Display name (defaults to the part before the @)}
        {--password= : Set an exact password instead of generating one}
        {--reset : Allow updating an existing account rather than failing}
        {--disable : Revoke this account\'s access and delete its tokens}
        {--enable : Restore a disabled account}';

    protected $description = 'Create, reset, disable or re-enable a platform operator account';

    /** No 0/O or 1/I/l — this gets read back off a screen and typed by hand. */
    private const PASSWORD_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';

    public function handle(): int
    {
        $email = strtolower(trim((string) $this->argument('email')));

        if (! filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $this->error("\"{$email}\" is not a valid email address.");

            return self::FAILURE;
        }

        $existing = PlatformAdmin::findByEmail($email);

        if ($this->option('disable') || $this->option('enable')) {
            return $this->setDisabled($existing, $email, (bool) $this->option('disable'));
        }

        // Refusing by default rather than silently overwriting: a typo in the
        // email of an account that already exists would otherwise reset a
        // working operator's password without anyone noticing.
        if ($existing !== null && ! $this->option('reset')) {
            $this->error("An operator account already exists for {$email}.");
            $this->line('Pass --reset to set a new password on it.');

            return self::FAILURE;
        }

        $password = (string) ($this->option('password') ?: $this->generatePassword());

        if (strlen($password) < 12) {
            $this->error('A platform operator password must be at least 12 characters.');

            return self::FAILURE;
        }

        $name = (string) ($this->option('name') ?: Str::of($email)->before('@')->headline());

        $admin = $existing ?? new PlatformAdmin(['email' => $email]);
        $admin->fill([
            'name' => $name,
            'password' => $password,
            'disabled_at' => null,
        ])->save();

        // Any session that was open under the old password stops working. On a
        // --reset this is the point of the exercise; on a fresh account it is
        // a no-op.
        $admin->tokens()->delete();

        $this->newLine();
        $this->info($existing === null ? 'Operator account created.' : 'Operator password reset.');
        $this->table(['Field', 'Value'], [
            ['Name', $admin->name],
            ['Email', $admin->email],
            ['Password', $password],
        ]);

        if (! $this->option('password')) {
            $this->warn('This password is shown once and is not recoverable. Store it in a password manager now.');
        }

        $this->line('Sign in at /platform-admin.');
        $this->newLine();

        return self::SUCCESS;
    }

    private function setDisabled(?PlatformAdmin $admin, string $email, bool $disable): int
    {
        if ($admin === null) {
            $this->error("No operator account for {$email}.");

            return self::FAILURE;
        }

        $admin->forceFill(['disabled_at' => $disable ? now() : null])->save();

        if ($disable) {
            // Deleted here as well as refused by the middleware, so access ends
            // with this command rather than with the next request.
            $admin->tokens()->delete();
            $this->info("Disabled {$admin->email} and signed it out everywhere.");
        } else {
            $this->info("Re-enabled {$admin->email}. It must sign in again.");
        }

        return self::SUCCESS;
    }

    private function generatePassword(int $length = 20): string
    {
        $alphabet = self::PASSWORD_ALPHABET;
        $password = '';

        for ($i = 0; $i < $length; $i++) {
            $password .= $alphabet[random_int(0, strlen($alphabet) - 1)];
        }

        return $password;
    }
}
