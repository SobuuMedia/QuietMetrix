<?php

function handleCountersV2(): void {
    checkOrigin();
    $projectId = requireApiKey();
    enforceLimit("counters:v2:{$projectId}", defined('RATE_LIMIT_RPS') ? (int)RATE_LIMIT_RPS : 10,
        defined('RATE_LIMIT_BURST') ? (int)RATE_LIMIT_BURST : 60);
    $ip = clientIp() ?? 'unknown';
    if (defined('INGEST_IP_ENABLED') && INGEST_IP_ENABLED && $ip !== 'unknown') {
        enforceLimit("counters:ip:{$ip}", defined('INGEST_IP_RPS') ? (int)INGEST_IP_RPS : 5,
            defined('INGEST_IP_BURST') ? (int)INGEST_IP_BURST : 60);
    }
    $body = getJsonBody();
    $batchId = $body['batch_id'] ?? null;
    $usesSchema2 = array_key_exists('schema', $body);
    $sdk = is_array($body['sdk'] ?? null) ? $body['sdk'] : [];
    $app = is_array($body['app'] ?? null) ? $body['app'] : [];
    if ($usesSchema2 && (($body['schema'] ?? null) !== 2 || !is_string($sdk['platform'] ?? null) || !is_string($sdk['version'] ?? null))) {
        errorResponse(400, 'invalid_schema', 'schema 2 requires sdk platform and version'); return;
    }
    $items = $usesSchema2 ? ($body['items'] ?? null) : ($body['counters'] ?? null);
    if (!is_string($batchId) || preg_match('/^[A-Za-z0-9_-]{16,64}$/', $batchId) !== 1) {
        errorResponse(400, 'invalid_batch_id', 'batch_id must contain 16-64 URL-safe characters'); return;
    }
    $today = new DateTimeImmutable('today', new DateTimeZone('UTC'));
    if (!is_array($items) || count($items) < 1 || count($items) > 512) { errorResponse(400, 'invalid_batch_size', 'counters must contain 1-512 items'); return; }
    $validated = [];
    foreach ($items as $item) {
        if (!is_array($item)) { errorResponse(400, 'invalid_counter_value', 'Each item must be an object'); return; }
        if ($usesSchema2) {
            $day = $item['day'] ?? null; $hour = $item['hour'] ?? null;
            $metric = $item['metric'] ?? null; $dims = $item['dimensions'] ?? null;
            $segment = $item['segment'] ?? null; $n = $item['n'] ?? null;
            if (!is_string($metric) || !is_array($dims) || !is_array($segment) ||
                !is_string($segment['kind'] ?? null) || !is_string($segment['value'] ?? null) ||
                !is_int($n) || $n < 1 || $n > 1000000000) {
                errorResponse(400, 'invalid_counter_value', 'Each schema-2 item requires day, metric, segment, dimensions, and positive n'); return;
            }
            $segmentKind = $segment['kind']; $segmentValue = $segment['value'];
            $contributors = in_array($metric, ['unique_window_v2','unique_hour_v2','retention_entry_v2','retention_return_v2','experiment_exposure_v2','experiment_goal_v2'], true) ? 1 : 0;
        } else {
            $day = $body['day'] ?? null; $hour = $body['hour'] ?? null;
            $metric = $item['m'] ?? null; $dims = $item['d'] ?? null; $n = $item['n'] ?? null;
            $segmentKind = 'global'; $segmentValue = 'all';
            $contributors = $item['contributors'] ?? null;
            if (!is_string($metric) || !is_array($dims) || !is_int($n) || $n < 1 || $n > 1000000000 ||
                !is_int($contributors) || !in_array($contributors, [0, 1], true)) {
                errorResponse(400, 'invalid_counter_value', 'Each legacy v2 item requires metric, dimensions, positive n, and contributors 0 or 1'); return;
            }
        }
        $date = is_string($day) ? DateTimeImmutable::createFromFormat('!Y-m-d', $day, new DateTimeZone('UTC')) : false;
        if ($date === false || $date->format('Y-m-d') !== $day) { errorResponse(400, 'invalid_day', 'day must be a real ISO UTC date'); return; }
        if ($date < $today->modify('-7 days') || $date > $today) { errorResponse(400, 'origin_day_out_of_range', 'day must be within the last seven UTC days'); return; }
        if ($hour !== null && (!is_int($hour) || $hour < 0 || $hour > 23)) { errorResponse(400, 'invalid_hour', 'hour must be null or 0-23 UTC'); return; }
        if (!counterV2ValidSegment($segmentKind, $segmentValue)) { errorResponse(400, 'invalid_segment', 'segment must be a supported single projection'); return; }
        $checked = counterV2Validate($metric, $dims);
        if (!$checked['valid']) { errorResponse(400, $checked['reason'], 'The v2 counter batch was rejected atomically'); return; }
        if (in_array($metric, ['unique_window_v2', 'witness_window_v2'], true)) {
            $end = DateTimeImmutable::createFromFormat('!Y-m-d', (string)($dims['end_day'] ?? ''), new DateTimeZone('UTC'));
            $entryCohortWitness = $metric === 'witness_window_v2' && ($dims['target_metric'] ?? null) === 'funnel_step_v2';
            if (!$end || ($entryCohortWitness ? ($end > $date || $date > $end->modify('+89 days')) : ($end < $date || $end > $date->modify('+89 days')))) { errorResponse(400, 'window_end_out_of_range', 'Window end day must be within 90 days of its activity or entry cohort'); return; }
        } elseif (isset($dims['entry_day']) && $dims['entry_day'] > $day) {
            errorResponse(400, 'entry_day_after_origin', 'entry_day cannot be after origin day'); return;
        }
        $validated[] = ['item' => ['day'=>$day,'hour'=>$hour,'m'=>$metric,'d'=>$dims,'n'=>$n,'contributors'=>$contributors,'segment_kind'=>$segmentKind,'segment_value'=>$segmentValue], 'canonical' => $checked['canonical'], 'hash' => $checked['hash']];
    }

    if (isset($sdk['version']) && (!is_string($sdk['version']) || strlen($sdk['version']) > 32)) { errorResponse(400, 'invalid_sdk_version', 'sdk.version exceeds 32 characters'); return; }
    $platform = in_array($sdk['platform'] ?? null, ['android','ios','web','desktop'], true) ? $sdk['platform'] : 'unknown';
    $appVersion = $app['version'] ?? '';
    if (!is_string($appVersion) || strlen($appVersion) > 32) { errorResponse(400, 'invalid_app_version', 'app.version exceeds 32 characters'); return; }
    $countryRaw = is_string($app['country'] ?? null) ? strtoupper(trim($app['country'])) : '';
    $country = preg_match('/^[A-Z]{2}$/', $countryRaw) === 1 ? $countryRaw : '  ';
    $payloadHash = $usesSchema2
        ? counterV2EnvelopePayloadHash($body['schema'], $sdk, $app, $validated)
        : counterV2PayloadHash($body['day'], $body['hour'] ?? null, $sdk, $app, $validated);
    $db = getDb();
    try {
        $db->beginTransaction();
        $projectLock = $db->prepare('SELECT id FROM projects WHERE id=? FOR UPDATE');
        $projectLock->execute([$projectId]);
        if ($projectLock->fetchColumn() === false) {
            $db->rollBack(); errorResponse(404, 'project_unavailable', 'Project no longer exists'); return;
        }
        $now = gmdate('Y-m-d\\TH:i:s\\Z');
        $expiresAt = gmdate('Y-m-d\\TH:i:s\\Z', time() + 14 * 86400);
        $db->prepare('DELETE FROM counter_batch_receipts_v2 WHERE expires_at < ?')->execute([$now]);
        $claim = bin2hex(random_bytes(16));
        $insertReceipt = $db->prepare('INSERT INTO counter_batch_receipts_v2 (project_id,batch_id,payload_hash,claim_token,accepted,created_at,expires_at) VALUES (?,?,?,?,0,?,?) ON DUPLICATE KEY UPDATE batch_id=batch_id');
        $insertReceipt->execute([$projectId, $batchId, $payloadHash, $claim, $now, $expiresAt]);
        $receiptStmt = $db->prepare('SELECT payload_hash,claim_token,accepted FROM counter_batch_receipts_v2 WHERE project_id=? AND batch_id=? FOR UPDATE');
        $receiptStmt->execute([$projectId, $batchId]);
        $receipt = $receiptStmt->fetch();
        if (!hash_equals($receipt['payload_hash'], $payloadHash)) {
            $db->rollBack(); errorResponse(409, 'batch_id_conflict', 'batch_id was already used with a different payload'); return;
        }
        if (!hash_equals($receipt['claim_token'], $claim)) {
            $db->commit(); jsonResponse(202, ['ok' => true, 'accepted' => (int)$receipt['accepted'], 'replayed' => true]); return;
        }
        $metricHashes = [];
        foreach ($validated as $cell) $metricHashes[$cell['item']['m']][counterV2CardinalityHash($cell['item']['m'], $cell['item']['d'])] = true;
        $maxCells = defined('COUNTERS_MAX_DISTINCT_CELLS_PER_METRIC') ? (int)COUNTERS_MAX_DISTINCT_CELLS_PER_METRIC : 500;
        foreach ($metricHashes as $metric => $hashes) {
            $existingStmt = $db->prepare('SELECT DISTINCT cardinality_hash FROM counter_cells_v2 WHERE project_id=? AND metric=? AND cardinality_hash IS NOT NULL');
            $existingStmt->execute([$projectId, $metric]);
            $known = array_fill_keys(array_map('trim', $existingStmt->fetchAll(PDO::FETCH_COLUMN)), true);
            $legacyStmt = $db->prepare('SELECT day,hour,platform,app_version,country,dims_hash,segment_kind,segment_value,dims FROM counter_cells_v2 WHERE project_id=? AND metric=? AND cardinality_hash IS NULL');
            $legacyStmt->execute([$projectId, $metric]);
            $backfill = $db->prepare('UPDATE counter_cells_v2 SET cardinality_hash=? WHERE project_id=? AND day=? AND hour=? AND metric=? AND platform=? AND app_version=? AND country=? AND dims_hash=? AND segment_kind=? AND segment_value=?');
            foreach ($legacyStmt->fetchAll(PDO::FETCH_ASSOC) as $row) {
                $decodedDims = json_decode($row['dims'], true);
                if (!is_array($decodedDims)) continue;
                $hash = counterV2CardinalityHash($metric, $decodedDims);
                $backfill->execute([$hash,$projectId,$row['day'],$row['hour'],$metric,$row['platform'],$row['app_version'],$row['country'],$row['dims_hash'],$row['segment_kind'],$row['segment_value']]);
                $known[$hash] = true;
            }
            if (count($known + $hashes) > $maxCells) {
                $db->rollBack(); errorResponse(400, 'cardinality_cap', 'The v2 batch exceeds the per-metric dimension cap'); return;
            }
        }
        $upsert = $db->prepare('INSERT INTO counter_cells_v2 (project_id,day,hour,metric,platform,sdk_version,app_version,country,dims_hash,cardinality_hash,dims,n,contributors,segment_kind,segment_value,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE n=n+VALUES(n),contributors=contributors+VALUES(contributors),sdk_version=VALUES(sdk_version),cardinality_hash=VALUES(cardinality_hash),updated_at=VALUES(updated_at)');
        $now = gmdate('Y-m-d\TH:i:s\Z');
        foreach ($validated as $cell) {
            $d = $cell['item']; ksort($d['d']);
            $upsert->execute([$projectId,$d['day'],$d['hour'] ?? -1,$d['m'],$platform,($sdk['version'] ?? 'unknown') ?: 'unknown',$appVersion,$country,$cell['hash'],counterV2CardinalityHash($d['m'], $d['d']),json_encode($d['d'], JSON_UNESCAPED_SLASHES),$d['n'],$d['contributors'],$d['segment_kind'],$d['segment_value'],$now]);
        }
        $updateReceipt = $db->prepare('UPDATE counter_batch_receipts_v2 SET accepted=? WHERE project_id=? AND batch_id=?');
        $updateReceipt->execute([count($validated), $projectId, $batchId]);
        $db->commit();
        jsonResponse(202, ['ok' => true, 'accepted' => count($validated), 'replayed' => false]);
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        throw $e;
    }
}

/** Canonical, length-prefixed payload shared byte-for-byte with Ktor. */
function counterV2PayloadHash(string $day, ?int $hour, array $sdk, array $app, array $validated): string {
    $field = static function (?string $value): string { return ($value === null ? '-1:' : strlen($value) . ':' . $value) . ';'; };
    $canonical = $field($day) . $field($hour === null ? null : (string)$hour) .
        $field(is_string($sdk['platform'] ?? null) ? $sdk['platform'] : null) . $field(is_string($sdk['version'] ?? null) ? $sdk['version'] : null) .
        $field(is_string($app['version'] ?? null) ? $app['version'] : null) . $field(is_string($app['country'] ?? null) ? $app['country'] : null);
    usort($validated, static fn($a,$b) => [$a['item']['m'],$a['canonical'],$a['item']['n'],$a['item']['contributors']] <=> [$b['item']['m'],$b['canonical'],$b['item']['n'],$b['item']['contributors']]);
    $canonical .= $field((string)count($validated));
    foreach ($validated as $cell) {
        $item = $cell['item']; $canonical .= $field($item['m']) . $field((string)$item['n']) . $field((string)$item['contributors']);
        $dims = $item['d']; ksort($dims); $canonical .= $field((string)count($dims));
        foreach ($dims as $key => $value) $canonical .= $field((string)$key) . $field((string)$value);
    }
    return hash('sha256', $canonical);
}

/** Canonical schema-2 envelope hash. Ktor uses the same length-prefixed field sequence. */
function counterV2EnvelopePayloadHash(int $schema, array $sdk, array $app, array $validated): string {
    $field = static function (?string $value): string { return ($value === null ? '-1:' : strlen($value) . ':' . $value) . ';'; };
    $canonical = $field((string)$schema) . $field($sdk['platform'] ?? null) . $field($sdk['version'] ?? null) .
        $field($app['version'] ?? null) . $field($app['country'] ?? null);
    usort($validated, static fn($a,$b) => [
        $a['item']['day'], $a['item']['hour'] ?? -1, $a['item']['m'], $a['item']['segment_kind'],
        $a['item']['segment_value'], $a['canonical'], $a['item']['n'],
    ] <=> [
        $b['item']['day'], $b['item']['hour'] ?? -1, $b['item']['m'], $b['item']['segment_kind'],
        $b['item']['segment_value'], $b['canonical'], $b['item']['n'],
    ]);
    $canonical .= $field((string)count($validated));
    foreach ($validated as $cell) {
        $item = $cell['item'];
        $canonical .= $field($item['day']) . $field(($item['hour'] ?? null) === null ? null : (string)$item['hour']) .
            $field($item['m']) . $field($item['segment_kind']) . $field($item['segment_value']) .
            $field((string)$item['n']) . $field((string)count($item['d']));
        $dims = $item['d']; ksort($dims);
        foreach ($dims as $key => $value) $canonical .= $field((string)$key) . $field((string)$value);
    }
    return hash('sha256', $canonical);
}

function counterV2ValidSegment(string $kind, string $value): bool {
    return match ($kind) {
        'global' => $value === 'all',
        'platform' => in_array($value, ['android','ios','web','desktop','unknown'], true),
        'country' => $value === 'unknown' || in_array($value, explode(' ', 'AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW'), true),
        'device_class' => in_array($value, ['phone','tablet','desktop','unknown'], true),
        default => false,
    };
}
