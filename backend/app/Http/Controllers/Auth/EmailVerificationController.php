<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use App\Models\CustomerAccount;
use App\Models\User;
use Illuminate\Auth\Events\Verified;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class EmailVerificationController extends Controller
{
    public function verifyCustomer(Request $request, string $id, string $hash): RedirectResponse
    {
        $account = CustomerAccount::query()->findOrFail($id);

        abort_unless(hash_equals((string) $hash, sha1($account->getEmailForVerification())), 403);

        if ($account->markEmailAsVerified()) {
            event(new Verified($account));
        }

        return redirect()->away(rtrim((string) config('app.customer_account_url'), '/').'?verified=1');
    }

    public function verifySeller(Request $request, string $id, string $hash): RedirectResponse
    {
        $user = User::query()->findOrFail($id);

        abort_unless(hash_equals((string) $hash, sha1($user->getEmailForVerification())), 403);

        if ($user->markEmailAsVerified()) {
            event(new Verified($user));
        }

        return redirect()->away(rtrim((string) config('app.url'), '/').'/app?verified=1');
    }
}
