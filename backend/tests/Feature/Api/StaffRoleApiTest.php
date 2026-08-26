<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\Store;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class StaffRoleApiTest extends TestCase
{
    use RefreshDatabase;

    private function pairDevice(): string
    {
        $response = $this->postJson('/api/device-sessions', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'pairingCode' => '123456',
            'deviceName' => 'Counter 1',
            'platform' => 'web',
            'appVersion' => '0.1.0',
        ])->assertOk();

        return $response->json('token');
    }

    public function test_authenticated_device_can_list_roles(): void
    {
        $this->seed();
        $token = $this->pairDevice();

        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/staff-roles')
            ->assertOk()
            ->assertJsonPath('roles.0.id', 'admin');
    }

    public function test_authenticated_device_can_sync_custom_roles(): void
    {
        $this->seed();
        $token = $this->pairDevice();

        $payload = [
            'roles' => [
                [
                    'id' => 'admin',
                    'name' => 'Admin',
                    'permissions' => [
                        'register' => true,
                        'orders' => true,
                        'products' => true,
                        'analytics' => true,
                        'settings' => true,
                        'diagnostics' => true,
                    ],
                ],
                [
                    'id' => 'barista',
                    'name' => 'Barista',
                    'permissions' => [
                        'register' => true,
                        'orders' => true,
                        'products' => false,
                        'analytics' => false,
                        'settings' => false,
                        'diagnostics' => false,
                    ],
                ],
                [
                    'id' => 'guest',
                    'name' => 'Guest',
                    'permissions' => [
                        'register' => true,
                        'orders' => true,
                        'products' => true,
                        'analytics' => true,
                        'settings' => true,
                        'diagnostics' => false,
                    ],
                ],
            ],
        ];

        $this->withHeader('Authorization', "Bearer {$token}")
            ->putJson('/api/staff-roles', $payload)
            ->assertOk()
            ->assertJsonFragment([
                'id' => 'barista',
                'name' => 'Barista',
            ]);

        $organization = Organization::query()->where('slug', 'demo-coffee')->firstOrFail();

        $this->assertDatabaseHas('roles', [
            'organization_id' => $organization->id,
            'role_key' => 'barista',
            'name' => 'Barista',
        ]);
    }

    public function test_custom_role_can_be_assigned_to_a_user(): void
    {
        $this->seed();
        $token = $this->pairDevice();

        $this->withHeader('Authorization', "Bearer {$token}")
            ->putJson('/api/staff-roles', [
                'roles' => [
                    [
                        'id' => 'admin',
                        'name' => 'Admin',
                        'permissions' => [
                            'register' => true,
                            'orders' => true,
                            'products' => true,
                            'analytics' => true,
                            'settings' => true,
                            'diagnostics' => true,
                        ],
                    ],
                    [
                        'id' => 'barista',
                        'name' => 'Barista',
                        'permissions' => [
                            'register' => true,
                            'orders' => true,
                            'products' => false,
                            'analytics' => false,
                            'settings' => false,
                            'diagnostics' => false,
                        ],
                    ],
                    [
                        'id' => 'guest',
                        'name' => 'Guest',
                        'permissions' => [
                            'register' => true,
                            'orders' => true,
                            'products' => true,
                            'analytics' => true,
                            'settings' => true,
                            'diagnostics' => false,
                        ],
                    ],
                ],
            ])->assertOk();

        // Registering takes an email and answers 201 without a session now -
        // the account has to verify before it can sign in. This test only needs
        // the account to exist so its role can be changed, which is unaffected.
        $this->postJson('/api/staff-register', [
            'organizationSlug' => 'demo-coffee',
            'storeCode' => 'main',
            'fullName' => 'Barista User',
            'username' => 'barista1',
            'email' => 'barista1@example.com',
            'password' => 'secret',
        ])->assertCreated();

        $user = User::query()->where('username', 'barista1')->firstOrFail();
        $organization = Organization::query()->where('slug', 'demo-coffee')->firstOrFail();
        $store = Store::query()->where('organization_id', $organization->id)->where('code', 'main')->firstOrFail();

        $this->withHeader('Authorization', "Bearer {$token}")
            ->patchJson("/api/staff-users/{$user->id}/role", [
                'roleId' => 'barista',
            ])
            ->assertOk()
            ->assertJsonPath('user.roleId', 'barista');

        $this->assertDatabaseHas('organization_memberships', [
            'organization_id' => $organization->id,
            'user_id' => $user->id,
            'membership_role' => 'barista',
        ]);

        $this->assertDatabaseHas('store_memberships', [
            'store_id' => $store->id,
            'user_id' => $user->id,
            'membership_role' => 'barista',
        ]);
    }
}
