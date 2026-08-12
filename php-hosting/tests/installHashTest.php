<?php

/**
 * Unit tests for the funnel/analytics install-hash primitives — the salted hash stored in
 * `events.install_hash`. This is deliberately a SEPARATE salt/hash from the Stage 2
 * abuse-defense pipeline (`install_meta.anonymous_id_hash`, keyed by `projects.install_salt`):
 * that salt rotates on API-key regeneration, but funnel/retention history must survive a
 * rotation, so `projects.analytics_salt` never does. See docs/security/publishable-api-key.md.
 *
 * Run: php php-hosting/tests/installHashTest.php   (exit 0 = pass, 1 = fail)
 */

require_once __DIR__ . '/../src/installs.php';

$failures = 0;
function check(string $label, $expected, $actual): void {
    global $failures;
    if ($expected === $actual) {
        echo "  ok: $label\n";
        return;
    }
    $failures++;
    echo "FAIL: $label\n";
    echo '    expected: ' . var_export($expected, true) . "\n";
    echo '    actual:   ' . var_export($actual, true) . "\n";
}

// hashInstallId() is the shared salted-hash primitive used by both the abuse-defense hash
// and the analytics install_hash — only the salt differs between the two use sites.
check('hashInstallId is deterministic for the same salt + id', true,
    hashInstallId('salt-a', 'qm_aid_x') === hashInstallId('salt-a', 'qm_aid_x'));

check('hashInstallId produces a 64-char hex digest', 64,
    strlen(hashInstallId('salt-a', 'qm_aid_x')));

check('hashInstallId differs across salts for the same raw id', true,
    hashInstallId('salt-a', 'qm_aid_x') !== hashInstallId('salt-b', 'qm_aid_x'));

check('hashInstallId differs across raw ids for the same salt', true,
    hashInstallId('salt-a', 'qm_aid_x') !== hashInstallId('salt-a', 'qm_aid_y'));

check('hashInstallId never contains the raw id as a substring', false,
    str_contains(hashInstallId('salt-a', 'qm_aid_super_distinctive_value'), 'qm_aid_super_distinctive_value'));

echo "\n";
if ($failures > 0) {
    echo "$failures assertion(s) failed\n";
    exit(1);
}
echo "all install-hash assertions passed\n";
