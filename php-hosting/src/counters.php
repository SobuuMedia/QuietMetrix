<?php

/**
 * Persists counter cells: (project, day, metric, platform, app_version, country, dims) -> n.
 * Mirrors servers/ktor/.../persistence/CounterRepository.kt; keep the two in sync. Nothing
 * here ever sees or stores an install identifier -- $isNewDevice is the SDK's own "first
 * flush of this cell today" flag, so counterReadCells()'s k-anonymity filter can work from a
 * running device count alone. See counterRegistry.php for the validation and canonicalization
 * every write passes through first.
 */

/**
 * Validates and upserts one counter delta. Returns ['applied' => true] or
 * ['applied' => false, 'reason' => string] (also true for the caller: the delta was
 * quarantined, not silently dropped).
 */
function counterUpsert(
    string $projectId,
    string $day,
    string $metric,
    array $dims,
    string $platform,
    string $appVersion,
    string $country,
    int $n,
    bool $isNewDevice,
    int $maxDistinctCellsPerMetric = 500
): array {
    $validation = counterValidate($metric, $dims);
    if (!$validation['valid']) {
        counterQuarantine($projectId, $metric, $dims, $n, $validation['reason'], null);
        return ['applied' => false, 'reason' => $validation['reason']];
    }
    $dimsHash = $validation['hash'];
    $deviceDelta = $isNewDevice ? 1 : 0;
    $db = getDb();

    $existsStmt = $db->prepare(
        'SELECT 1 FROM counters
          WHERE project_id = ? AND day = ? AND metric = ? AND platform = ? AND app_version = ? AND country = ? AND dims_hash = ?
          LIMIT 1'
    );
    $existsStmt->execute([$projectId, $day, $metric, $platform, $appVersion, $country, $dimsHash]);
    $exists = $existsStmt->fetchColumn() !== false;

    if (!$exists) {
        $countStmt = $db->prepare('SELECT COUNT(*) FROM counters WHERE project_id = ? AND metric = ?');
        $countStmt->execute([$projectId, $metric]);
        $distinctCells = (int)$countStmt->fetchColumn();
        if ($distinctCells >= $maxDistinctCellsPerMetric) {
            counterQuarantine($projectId, $metric, $dims, $n, 'cardinality_cap', "metric $metric already has $distinctCells distinct cells");
            return ['applied' => false, 'reason' => 'cardinality_cap'];
        }
    }

    $db->prepare(
        'INSERT INTO counters (project_id, day, metric, platform, app_version, country, dims_hash, dims, n, devices, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE n = n + VALUES(n), devices = devices + VALUES(devices), updated_at = VALUES(updated_at)'
    )->execute([
        $projectId, $day, $metric, $platform, $appVersion, $country, $dimsHash,
        json_encode($dims, JSON_UNESCAPED_UNICODE), $n, $deviceDelta, now(),
    ]);

    return ['applied' => true];
}

/**
 * Cells for $metric in [$from, $to] (inclusive, ISO date strings), grouped by dims and summed
 * across day/platform/app_version/country. A group is included only when its summed device
 * count is at least $k -- the k-anonymity gate. Every read path onto `counters` must go
 * through this, never a raw query.
 *
 * Returns a list of ['dims' => array, 'n' => int, 'devices' => int].
 */
function counterReadCells(string $projectId, string $metric, string $from, string $to, int $k = 5): array {
    $stmt = getDb()->prepare(
        'SELECT dims_hash, dims, n, devices
           FROM counters
          WHERE project_id = ? AND metric = ? AND day >= ? AND day <= ?'
    );
    $stmt->execute([$projectId, $metric, $from, $to]);

    $groups = [];
    while ($row = $stmt->fetch()) {
        $hash = $row['dims_hash'];
        if (!isset($groups[$hash])) {
            $groups[$hash] = ['dims' => $row['dims'], 'n' => 0, 'devices' => 0];
        }
        $groups[$hash]['n'] += (int)$row['n'];
        $groups[$hash]['devices'] += (int)$row['devices'];
    }

    $result = [];
    foreach ($groups as $group) {
        if ($group['devices'] < $k) continue;
        $result[] = [
            'dims' => json_decode($group['dims'], true) ?? [],
            'n' => $group['n'],
            'devices' => $group['devices'],
        ];
    }
    return $result;
}

/**
 * Per-day totals for $metric across all its dims, e.g. a "total events per day" trend.
 * Mirrors CounterRepository.dailyTotals -- keep the two in sync. The k-anonymity gate is
 * applied per (day, dims_hash) cell, not once against the whole range: a day whose own cell
 * falls below $k must not surface a day-level count for it, even if the range-wide total
 * would clear the bar. Cells for different dims on the same day (e.g. distinct event names)
 * are summed into that day's total.
 *
 * Returns a list of ['day' => string, 'n' => int], sorted by day.
 */
function counterDailyTotals(string $projectId, string $metric, string $from, string $to, int $k = 5): array {
    $stmt = getDb()->prepare(
        'SELECT day, dims_hash, n, devices
           FROM counters
          WHERE project_id = ? AND metric = ? AND day >= ? AND day <= ?'
    );
    $stmt->execute([$projectId, $metric, $from, $to]);

    $groups = [];
    while ($row = $stmt->fetch()) {
        $key = $row['day'] . '|' . $row['dims_hash'];
        if (!isset($groups[$key])) {
            $groups[$key] = ['day' => $row['day'], 'n' => 0, 'devices' => 0];
        }
        $groups[$key]['n'] += (int)$row['n'];
        $groups[$key]['devices'] += (int)$row['devices'];
    }

    $byDay = [];
    foreach ($groups as $group) {
        if ($group['devices'] < $k) continue;
        $byDay[$group['day']] = ($byDay[$group['day']] ?? 0) + $group['n'];
    }

    $result = [];
    foreach ($byDay as $day => $n) {
        $result[] = ['day' => (string)$day, 'n' => $n];
    }
    usort($result, static fn(array $a, array $b): int => $a['day'] <=> $b['day']);
    return $result;
}

/**
 * A coarse per-platform breakdown of $metric's total volume -- groups by the platform column
 * (a first-class column on `counters`, populated from the SDK's own DeviceContext.platform),
 * not by dims. Mirrors CounterRepository.totalsByPlatform. Same per-cell k-anonymity gate as
 * counterDailyTotals(): a (platform, dims_hash) cell below $k contributes nothing, even
 * blank/unknown platform values (an empty platform string is dropped, not shown as an
 * "unknown" bucket).
 *
 * Returns a list of ['platform' => string, 'n' => int], sorted by n descending.
 */
function counterTotalsByPlatform(string $projectId, string $metric, string $from, string $to, int $k = 5): array {
    $stmt = getDb()->prepare(
        'SELECT platform, dims_hash, n, devices
           FROM counters
          WHERE project_id = ? AND metric = ? AND day >= ? AND day <= ?'
    );
    $stmt->execute([$projectId, $metric, $from, $to]);

    $groups = [];
    while ($row = $stmt->fetch()) {
        $key = $row['platform'] . '|' . $row['dims_hash'];
        if (!isset($groups[$key])) {
            $groups[$key] = ['platform' => $row['platform'], 'n' => 0, 'devices' => 0];
        }
        $groups[$key]['n'] += (int)$row['n'];
        $groups[$key]['devices'] += (int)$row['devices'];
    }

    $byPlatform = [];
    foreach ($groups as $group) {
        if ($group['devices'] < $k) continue;
        if ($group['platform'] === '') continue;
        $byPlatform[$group['platform']] = ($byPlatform[$group['platform']] ?? 0) + $group['n'];
    }

    $result = [];
    foreach ($byPlatform as $platform => $n) {
        $result[] = ['platform' => (string)$platform, 'n' => $n];
    }
    usort($result, static fn(array $a, array $b): int => $b['n'] <=> $a['n']);
    return $result;
}

function counterCountQuarantined(string $projectId): int {
    $stmt = getDb()->prepare('SELECT COUNT(*) FROM counters_quarantine WHERE project_id = ?');
    $stmt->execute([$projectId]);
    return (int)$stmt->fetchColumn();
}

function counterQuarantine(string $projectId, string $metric, array $dims, int $n, string $reason, ?string $detail): void {
    $payload = json_encode(['metric' => $metric, 'dims' => $dims, 'n' => $n], JSON_UNESCAPED_UNICODE);
    getDb()->prepare(
        'INSERT INTO counters_quarantine (project_id, payload, quarantine_reason, quarantine_detail, quarantined_at)
         VALUES (?, ?, ?, ?, ?)'
    )->execute([$projectId, $payload, $reason, $detail, now()]);
}

/**
 * Clamps a device-reported local day (ISO yyyy-mm-dd) into [today - 2, today], falling back
 * to today for a malformed string. Mirrors CounterIngestProcessor.clampDay -- clock skew and
 * brief offline queuing are normal, not rejected.
 */
function counterClampDay(string $raw, string $today): string {
    $parsed = DateTime::createFromFormat('!Y-m-d', $raw);
    if ($parsed === false) return $today;

    $todayDt = new DateTime($today);
    $earliestDt = (clone $todayDt)->modify('-2 days');

    if ($parsed > $todayDt) return $today;
    if ($parsed < $earliestDt) return $earliestDt->format('Y-m-d');
    return $parsed->format('Y-m-d');
}
