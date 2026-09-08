<?php

namespace Tests\Feature\Api;

use App\Models\Organization;
use App\Models\Store;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\SignsInStaff;
use Tests\TestCase;

class StaffRoleApiTest extends TestCase
{
    use SignsInStaff, RefreshDatabase;

    public function test_authenticated_device_can_list_roles(): void
    {
        $this->seed();
        $token = $this->staffToken();

        $this->withHeader('Authorization', "Bearer {$token}")
            ->getJson('/api/staff-roles')
            ->assertOk()
            ->assertJsonPath('roles.0.id', 'admin');
    }

    public function test_authenticated_device_can_sync_custom_roles(): void
    {
        $this->seed();
        $token = $this->staffToken();

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
        $token = $this->staffToken();

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

        $this->withHeader('Authorization', "Bearer {$token}")
            ->postJson('/api/staff-users', [
                'fullName' => 'Barista User',
                'username' => 'barista1',
                'password' => 'secret',
                'roleId' => 'barista',
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
