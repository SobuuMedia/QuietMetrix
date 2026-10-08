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
        // The cap limits distinct DIMS COMBINATIONS (dims_hash) per metric, not rows. A
        // single combination produces one row per (day, platform, app_version, country), so
        // counting rows here would trip the cap on a single popular metric within weeks even
        // with no new combinations. Only check the cap when this dims_hash has never been
        // seen before for this metric (on any day/platform/version/country).
        $hashSeenStmt = $db->prepare(
            'SELECT 1 FROM counters WHERE project_id = ? AND metric = ? AND dims_hash = ? LIMIT 1'
        );
        $hashSeenStmt->execute([$projectId, $metric, $dimsHash]);
        $hashSeenBefore = $hashSeenStmt->fetchColumn() !== false;

        if (!$hashSeenBefore) {
            $countStmt = $db->prepare(
                'SELECT COUNT(DISTINCT dims_hash) FROM counters WHERE project_id = ? AND metric = ?'
            );
            $countStmt->execute([$projectId, $metric]);
            $distinctCombinations = (int)$countStmt->fetchColumn();
            if ($distinctCombinations >= $maxDistinctCellsPerMetric) {
                counterQuarantine($projectId, $metric, $dims, $n, 'cardinality_cap', "metric $metric already has $distinctCombinations distinct dims combinations");
                return ['applied' => false, 'reason' => 'cardinality_cap'];
            }
        }
    }

    $upsertSql = $db->getAttribute(PDO::ATTR_DRIVER_NAME) === 'sqlite'
        ? 'INSERT INTO counters (project_id, day, metric, platform, app_version, country, dims_hash, dims, n, devices, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(project_id, day, metric, platform, app_version, country, dims_hash)
           DO UPDATE SET n = n + excluded.n, devices = devices + excluded.devices, updated_at = excluded.updated_at'
        : 'INSERT INTO counters (project_id, day, metric, platform, app_version, country, dims_hash, dims, n, devices, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON DUPLICATE KEY UPDATE n = n + VALUES(n), devices = devices + VALUES(devices), updated_at = VALUES(updated_at)';
    $db->prepare($upsertSql)->execute([
        $projectId, $day, $metric, $platform, $appVersion, $country, $dimsHash,
        json_encode($dims, JSON_UNESCAPED_UNICODE), $n, $deviceDelta, now(),
    ]);

    return ['applied' => true];
}

/** Canonical, backend-independent digest for an SDK batch receipt. */
function counterBatchPayloadHash(array $body): string {
    $canonical = '';
    $field = static function (?string $value) use (&$canonical): void {
        $canonical .= $value === null ? '-1:' : strlen($value) . ':' . $value;
        $canonical .= ';';
    };
    $sdk = is_array($body['sdk'] ?? null) ? $body['sdk'] : [];
    $app = is_array($body['app'] ?? null) ? $body['app'] : [];
    $field(is_string($body['day'] ?? null) ? $body['day'] : null);
    $field(is_string($sdk['platform'] ?? null) ? $sdk['platform'] : null);
    $field(is_string($sdk['version'] ?? null) ? $sdk['version'] : null);
    $field(is_string($app['version'] ?? null) ? $app['version'] : null);
    $field(is_string($app['country'] ?? null) ? $app['country'] : null);
    $items = is_array($body['counters'] ?? null) ? $body['counters'] : [];
    $field((string)count($items));
    foreach ($items as $item) {
        $item = is_array($item) ? $item : [];
        $field(is_string($item['m'] ?? null) ? $item['m'] : null);
        $field((string)(int)($item['n'] ?? 0));
        $field((string)(int)($item['u'] ?? 0));
        $dims = is_array($item['d'] ?? null) ? $item['d'] : [];
        ksort($dims, SORT_STRING);
        $field((string)count($dims));
        foreach ($dims as $key => $value) {
            $field((string)$key);
            $field(is_string($value) ? $value : json_encode($value, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        }
    }
    return hash('sha256', $canonical);
}

/** Applies a receipt-bearing batch once, in the same transaction as all counter writes. */
function counterApplyBatchOnce(
    string $projectId,
    string $batchId,
    string $payloadHash,
    string $claimToken,
    string $day,
    string $platform,
    string $appVersion,
    string $country,
    array $items,
    int $maxDistinctCellsPerMetric = 500
): array {
    $db = getDb();
    $db->beginTransaction();
    try {
        $sqlite = $db->getAttribute(PDO::ATTR_DRIVER_NAME) === 'sqlite';
        $claimSql = $sqlite
            ? 'INSERT INTO counter_batch_receipts (project_id,batch_id,payload_hash,claim_token,accepted,quarantined,created_at) VALUES (?,?,?,?,0,0,?) ON CONFLICT(project_id,batch_id) DO NOTHING'
            : 'INSERT IGNORE INTO counter_batch_receipts (project_id,batch_id,payload_hash,claim_token,accepted,quarantined,created_at) VALUES (?,?,?,?,0,0,?)';
        $claim = $db->prepare($claimSql);
        $claim->execute([$projectId, $batchId, $payloadHash, $claimToken, now()]);
        if ($claim->rowCount() === 0) {
            $readSql = 'SELECT payload_hash,claim_token,accepted,quarantined FROM counter_batch_receipts WHERE project_id=? AND batch_id=?' . ($sqlite ? '' : ' FOR UPDATE');
            $read = $db->prepare($readSql);
            $read->execute([$projectId, $batchId]);
            $receipt = $read->fetch();
            if (!$receipt || !hash_equals((string)$receipt['payload_hash'], $payloadHash)) {
                $db->rollBack();
                return ['conflict' => true];
            }
            if (hash_equals((string)$receipt['claim_token'], $claimToken)) {
                // Defensive only: a generated claim token collision is not a replay.
                $db->rollBack();
                return ['conflict' => true];
            }
            $db->commit();
            return ['conflict' => false, 'replayed' => true, 'accepted' => (int)$receipt['accepted'], 'quarantined' => (int)$receipt['quarantined']];
        }

        $validations = [];
        $hasInvalid = false;
        foreach ($items as $item) {
            $item = is_array($item) ? $item : [];
            $metric = (string)($item['m'] ?? '');
            $dims = is_array($item['d'] ?? null) ? $item['d'] : [];
            $validation = counterValidate($metric, $dims);
            $validations[] = $validation;
            if (!$validation['valid']) $hasInvalid = true;
        }

        $accepted = 0;
        $quarantined = 0;
        if ($hasInvalid) {
            foreach ($items as $index => $item) {
                $item = is_array($item) ? $item : [];
                counterQuarantine(
                    $projectId,
                    (string)($item['m'] ?? ''),
                    is_array($item['d'] ?? null) ? $item['d'] : [],
                    (int)($item['n'] ?? 0),
                    $validations[$index]['valid'] ? 'batch_invalid' : $validations[$index]['reason'],
                    'Batch rejected atomically because at least one item was invalid'
                );
            }
            $quarantined = count($items);
        } else {
            foreach ($items as $item) {
                $item = is_array($item) ? $item : [];
                $result = counterUpsert(
                    $projectId, $day, (string)$item['m'], $item['d'] ?? [], $platform, $appVersion, $country,
                    (int)$item['n'], (int)($item['u'] ?? 0) === 1, $maxDistinctCellsPerMetric
                );
                if ($result['applied']) $accepted++; else $quarantined++;
            }
        }
        $update = $db->prepare('UPDATE counter_batch_receipts SET accepted=?,quarantined=? WHERE project_id=? AND batch_id=?');
        $update->execute([$accepted, $quarantined, $projectId, $batchId]);
        $db->commit();
        return ['conflict' => false, 'replayed' => false, 'accepted' => $accepted, 'quarantined' => $quarantined];
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        throw $e;
    }
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
        $groups[$hash]['devices'] = max($groups[$hash]['devices'], (int)$row['devices']);
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

/** Transition cells filtered by platform before the contributor threshold is applied. */
function counterReadTransitionsLegacy(string $projectId, string $from, string $to, string $platform = 'all', int $k = 5): array {
    $stmt = getDb()->prepare('SELECT dims_hash,dims,n,devices FROM counters WHERE project_id=? AND metric=? AND day>=? AND day<=?'.($platform === 'all' ? '' : ' AND platform=?'));
    $params = [$projectId, 'screen_transition', $from, $to]; if ($platform !== 'all') $params[] = $platform;
    $stmt->execute($params); $groups = [];
    while ($row = $stmt->fetch()) { $h = $row['dims_hash']; $groups[$h] ??= ['dims'=>$row['dims'],'n'=>0,'devices'=>0]; $groups[$h]['n'] += (int)$row['n']; $groups[$h]['devices'] = max($groups[$h]['devices'], (int)$row['devices']); }
    return array_values(array_map(static fn($g) => ['dims'=>json_decode($g['dims'], true) ?: [], 'n'=>$g['n'], 'devices'=>$g['devices']], array_filter($groups, static fn($g) => $g['devices'] >= $k)));
}

/** Returns [present, cells]; present prevents legacy fallback from bypassing v2 k-gating. */
function counterReadTransitionsV2(string $projectId, string $from, string $to, string $platform = 'all', int $k = 5): array {
    require_once __DIR__ . '/routes/dashboard.php';
    $sql = 'SELECT metric,day,hour,dims_hash,platform,segment_kind,segment_value,dims,n,contributors FROM counter_cells_v2 WHERE project_id=? AND metric=? AND day>=? AND day<=? AND hour=-1'.($platform === 'all' ? '' : ' AND platform=?');
    $stmt = getDb()->prepare($sql); $params = [$projectId, 'screen_transition_v2', $from, $to]; if ($platform !== 'all') $params[] = $platform; $stmt->execute($params);
    $rows = selectCounterProjectionRows($stmt->fetchAll(PDO::FETCH_ASSOC), $platform); $groups=[]; $present=(bool)$rows;
    foreach ($rows as $row) { $d=json_decode((string)$row['dims'],true) ?: []; if (!isset($d['from'],$d['to'])) continue; $v=counterV2Validate('screen_transition_v2',$d); if (!$v['valid']) continue; $key=$v['canonical']; $groups[$key] ??= ['dims'=>$d,'rows'=>[]]; $groups[$key]['rows'][]=$row; }
    $cells=[]; $db=getDb(); $start=new DateTimeImmutable($from,new DateTimeZone('UTC')); $end=new DateTimeImmutable($to,new DateTimeZone('UTC'));
    foreach($groups as $g) { $witnesses=reportMetricWitnessCountV2($db,$projectId,'screen_transition_v2',$g['dims'],$start,$end,$platform,$g['rows']); if($witnesses >= $k) $cells[]=['dims'=>['from'=>$g['dims']['from'],'to'=>$g['dims']['to']],'n'=>array_sum(array_map(static fn($r)=>(int)$r['n'],$g['rows'])),'devices'=>$witnesses]; }
    return ['present'=>$present,'cells'=>$cells];
}

/** Revision-pinned v2 experiment totals, aggregated across platform/app partitions before k-gating. */
function counterReadExperimentCellsV2(string $projectId, string $metric, string $from, string $to, string $experimentKey, int $revision, int $k = 5): array {
    $stmt = getDb()->prepare(
        "SELECT dims,n,contributors FROM counter_cells_v2
          WHERE project_id=? AND metric=? AND day>=? AND day<=? AND hour=-1 AND segment_kind='global' AND segment_value='all'"
    );
    $stmt->execute([$projectId, $metric, $from, $to]);
    $groups = [];
    while ($row = $stmt->fetch()) {
        $dims = json_decode((string)$row['dims'], true) ?: [];
        if (($dims['exp'] ?? null) !== $experimentKey || (string)($dims['revision'] ?? '') !== (string)$revision) continue;
        $variant = $dims['variant'] ?? null;
        if (!in_array($variant, ['a', 'b'], true)) continue;
        $groups[$variant] ??= ['n' => 0, 'contributors' => 0];
        $groups[$variant]['n'] += (int)$row['n'];
        $groups[$variant]['contributors'] += (int)$row['contributors'];
    }
    $cells = [];
    foreach ($groups as $variant => $group) {
        if ($group['contributors'] < $k) continue;
        $cells[] = [
            'dims' => ['exp' => $experimentKey, 'rev' => (string)$revision, 'variant' => $variant],
            'n' => $group['n'], 'devices' => $group['contributors'],
        ];
    }
    return $cells;
}

/** One country or platform projection for an exact fixed-window installation count. */
function counterReadUniqueWindowByFacet(string $projectId, string $from, string $to, int $days, string $endDay, string $facet, int $k = 5, ?PDO $connection = null): array {
    if (!in_array($facet, ['country', 'platform', 'device_class'], true)) return [];
    $connection ??= getDb();
    try {
        $v2 = $connection->prepare("SELECT project_id,metric,day,hour,dims_hash,platform,country,app_version,segment_kind,segment_value,dims,n,contributors FROM counter_cells_v2 WHERE project_id=? AND metric='unique_window_v2' AND hour=-1 AND day BETWEEN ? AND ?");
        $v2->execute([$projectId, $from, $to]);
        $v2Rows = $v2->fetchAll(PDO::FETCH_ASSOC);
    } catch (PDOException) {
        // Older installations and isolated legacy fixtures may not have the additive v2 table.
        $v2Rows = [];
    }
    $rows = [];
    foreach ($v2Rows as $row) {
        $dims = json_decode((string)$row['dims'], true);
        if (is_array($dims) && ($dims['scope_kind'] ?? null) === 'project' && ($dims['scope_key'] ?? null) === 'all' &&
            (int)($dims['days'] ?? 0) === $days && ($dims['end_day'] ?? null) === $endDay) $rows[] = $row;
    }
    if ($rows) {
        $projected = array_values(array_filter($rows, static fn($row) => ($row['segment_kind'] ?? '') === $facet));
        $specific = [];
        foreach ($projected as $row) $specific[implode("\0", [$row['day'],$row['dims_hash'],$row['platform'],$row['app_version'],$row['country']])] = true;
        $legacy = [];
        if ($facet !== 'device_class') foreach ($rows as $row) {
            if (($row['segment_kind'] ?? 'global') !== 'global') continue;
            $key = implode("\0", [$row['day'],$row['dims_hash'],$row['platform'],$row['app_version'],$row['country']]);
            if (isset($specific[$key])) continue;
            $row['segment_value'] = $facet === 'platform' ? $row['platform'] : (trim((string)$row['country']) === '' ? 'unknown' : $row['country']);
            $legacy[] = $row;
        }
        $groups = [];
        foreach (array_merge($projected, $legacy) as $row) {
            $name = (string)($row['segment_value'] ?? 'unknown');
            $groups[$name] ??= ['n'=>0,'contributors'=>0];
            $groups[$name]['n'] += (int)$row['n']; $groups[$name]['contributors'] += (int)$row['contributors'];
        }
        $result=[]; foreach ($groups as $name=>$group) if ($group['contributors'] >= $k) $result[]=['name'=>$name,'count'=>$group['n']];
        usort($result, static fn(array $a,array $b): int => $b['count'] <=> $a['count']);
        return $result;
    }
    if ($facet === 'device_class') return [];
    $stmt = $connection->prepare(
        'SELECT platform,country,dims,n,devices FROM counters
          WHERE project_id=? AND metric=? AND day>=? AND day<=?'
    );
    $stmt->execute([$projectId, 'unique_window_v2', $from, $to]);
    $groups = [];
    while ($row = $stmt->fetch()) {
        $dims = json_decode((string)$row['dims'], true) ?: [];
        if (($dims['scope_kind'] ?? null) !== 'project' || ($dims['scope_key'] ?? null) !== 'all' ||
            ($dims['days'] ?? null) !== (string)$days || ($dims['end_day'] ?? null) !== $endDay) continue;
        $name = (string)$row[$facet];
        if ($name === '') $name = 'unknown';
        if (!isset($groups[$name])) $groups[$name] = ['n' => 0, 'devices' => 0];
        $groups[$name]['n'] += (int)$row['n'];
        $groups[$name]['devices'] += (int)$row['devices'];
    }
    $result = [];
    foreach ($groups as $name => $group) {
        if ($group['devices'] >= $k) $result[] = ['name' => $name, 'count' => $group['n']];
    }
    usort($result, static fn(array $a, array $b): int => $b['count'] <=> $a['count']);
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
        $groups[$key]['devices'] = max($groups[$key]['devices'], (int)$row['devices']);
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
        $groups[$key]['devices'] = max($groups[$key]['devices'], (int)$row['devices']);
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
    $payload = json_encode(['metric' => '[redacted]', 'dimension_count' => count($dims)]);
    getDb()->prepare(
        'INSERT INTO counters_quarantine (project_id, payload, quarantine_reason, quarantine_detail, quarantined_at)
         VALUES (?, ?, ?, ?, ?)'
    )->execute([$projectId, $payload, $reason, null, now()]);
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
