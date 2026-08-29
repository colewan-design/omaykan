<?php

namespace Tests\Feature;

use Tests\TestCase;

/**
 * The file that makes the reset link open the app instead of the browser.
 *
 * Worth tests despite being nine lines of JSON: Android's verifier is silent
 * about failures, so a malformed file shows up as "the link stopped opening the
 * app" weeks later with nothing in any log.
 */
class AssetLinksTest extends TestCase
{
    public function test_it_publishes_the_configured_fingerprints(): void
    {
        config([
            'services.android.package' => 'com.omaykan.storefront',
            'services.android.sha256_fingerprints' => ['ab:cd', 'ef:01'],
        ]);

        $this->getJson('/.well-known/assetlinks.json')
            ->assertOk()
            ->assertJsonCount(1)
            ->assertJsonPath('0.relation.0', 'delegate_permission/common.handle_all_urls')
            ->assertJsonPath('0.target.namespace', 'android_app')
            ->assertJsonPath('0.target.package_name', 'com.omaykan.storefront')
            ->assertJsonPath('0.target.sha256_cert_fingerprints', ['AB:CD', 'EF:01']);
    }

    /** Unconfigured is an unverified domain, not a 500. */
    public function test_no_fingerprints_is_an_empty_statement_list(): void
    {
        config(['services.android.sha256_fingerprints' => []]);

        $this->getJson('/.well-known/assetlinks.json')
            ->assertOk()
            ->assertExactJson([]);
    }

    public function test_blank_entries_are_dropped(): void
    {
        config([
            'services.android.package' => 'com.omaykan.storefront',
            'services.android.sha256_fingerprints' => ['  ', 'ab:cd', ''],
        ]);

        $this->getJson('/.well-known/assetlinks.json')
            ->assertOk()
            ->assertJsonPath('0.target.sha256_cert_fingerprints', ['AB:CD']);
    }

    /** The verifier checks the content type and refuses anything else. */
    public function test_it_is_served_as_json(): void
    {
        $this->get('/.well-known/assetlinks.json')
            ->assertOk()
            ->assertHeader('Content-Type', 'application/json');
    }
}
