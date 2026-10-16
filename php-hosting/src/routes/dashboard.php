<?php
require_once __DIR__ . '/../counterRegistryV2.php';

/** Select exactly one counter projection, with cell-by-cell fallback for rows written before
 * the projection protocol was enabled. */
function selectCounterProjectionRows(array $rows, string|array $platform): array {
    if (is_array($platform)) {
        [$kind, $value] = $platform;
        if ($kind === 'country') return array_values(array_filter($rows, static fn($row) => ($row['segment_kind'] ?? '') === 'country' && ($row['segment_value'] ?? '') === $value));
        if ($kind === 'screen') return array_values(array_filter(selectCounterProjectionRows($rows, 'all'), static function($row) use ($value) {
            $d = json_decode($row['dims'], true) ?: [];
            return match ($row['metric'] ?? '') {
                'unique_window_v2', 'unique_hour_v2' => ($d['scope_kind'] ?? '') === 'screen' && ($d['scope_key'] ?? '') === $value,
                'screen_view_v2', 'screen_dwell_ms_v2', 'screen_dwell_sample_v2' => ($d['screen'] ?? '') === $value,
                'witness_window_v2' => true,
                default => false,
            };
        }));
        return [];
    }
    $global = array_values(array_filter($rows, static fn($row) => ($row['segment_kind'] ?? 'global') === 'global' && ($row['segment_value'] ?? 'all') === 'all'));
    if ($platform === 'all') return $global;
    $specific = array_values(array_filter($rows, static fn($row) => ($row['segment_kind'] ?? 'global') === 'platform' && ($row['segment_value'] ?? '') === $platform));
    $key = static fn($row) => implode("\0", [$row['metric'] ?? '', $row['day'] ?? '', $row['hour'] ?? '', $row['dims_hash'] ?? '']);
    $keys = array_fill_keys(array_map($key, $specific), true);
    $fallback = array_values(array_filter($global, static fn($row) => ($row['platform'] ?? '') === $platform && !isset($keys[$key($row)])));
    return array_merge($specific, $fallback);
}

function reportPlatformV2(string|array $selection): string { return is_array($selection) ? 'all' : $selection; }
function reportUniqueScopeV2(array $dims, string|array $selection): bool {
    $screen = is_array($selection) && $selection[0] === 'screen';
    return ($dims['scope_kind'] ?? '') === ($screen ? 'screen' : 'project') && ($dims['scope_key'] ?? '') === ($screen ? $selection[1] : 'all');
}
function validReportFilterV2(string $kind, string $value): bool {
    return match ($kind) {
        'platform' => in_array($value, ['all','android','ios','web','desktop','unknown'], true),
        'country' => $value === 'unknown' || preg_match('/^[A-Z]{2}$/D', $value) === 1,
        'screen' => preg_match('~^[A-Za-z0-9_.:/-]{1,128}$~D', $value) === 1 && !in_array($value, ['all','__exit__'], true),
        default => false,
    };
}

/** Count unique witnesses for one exact metric group/window. Old rows are handled conservatively
 * by using the largest per-day distinct count, never adding daily populations together. */
function reportMetricWitnessCountV2(PDO $db, string $projectId, string $metric, array $dims, DateTimeImmutable $start, DateTimeImmutable $end, string|array $platform, array $legacyRows,
    ?DateTimeImmutable $windowEnd = null, ?int $windowDays = null, ?DateTimeImmutable $observedStart = null, ?DateTimeImmutable $observedEnd = null): int {
    $checked = counterV2Validate($metric, $dims);
    if (!($checked['valid'] ?? false)) return 0;
    $windowEnd ??= $end; $days = $windowDays ?? ((int)$start->diff($end)->format('%a') + 1);
    $observedStart ??= $start; $observedEnd ??= $end;
    $stmt = $db->prepare("SELECT metric,day,hour,dims_hash,platform,segment_kind,segment_value,dims,n,contributors FROM counter_cells_v2 WHERE project_id=? AND metric='witness_window_v2' AND day BETWEEN ? AND ? AND hour=-1 AND (?='all' OR platform=?)");
    $stmt->execute([$projectId, $observedStart->format('Y-m-d'), $observedEnd->format('Y-m-d'), reportPlatformV2($platform), reportPlatformV2($platform)]);
    $witnesses = array_values(array_filter(selectCounterProjectionRows($stmt->fetchAll(PDO::FETCH_ASSOC), $platform), static function ($row) use ($checked, $metric, $days, $windowEnd): bool {
        $d = json_decode($row['dims'], true);
        return is_array($d) && ($d['target_metric'] ?? null) === $metric && ($d['target_hash'] ?? null) === $checked['hash'] &&
            (int)($d['days'] ?? 0) === $days && ($d['end_day'] ?? null) === $windowEnd->format('Y-m-d');
    }));
    if ($witnesses) return array_sum(array_map(static fn($row) => (int)$row['n'], $witnesses));
    $perDay = [];
    foreach ($legacyRows as $row) {
        $key = ($row['day'] ?? '') . "\0" . ($row['hour'] ?? -1);
        $perDay[$key] = ($perDay[$key] ?? 0) + (int)$row['contributors'];
    }
    return $perDay ? max($perDay) : 0;
}

/** Typed v2 report foundation. People uses the exact fixed-window contribution, never a sum
 * of daily People values. */
function handleProjectReportV2(string $projectId): void {
    requireAnalyticsAccess($projectId);
    try { jsonResponse(200, readProjectReportV2($projectId, $_GET)); }
    catch (InvalidArgumentException $e) { errorResponse(400, 'bad_request', $e->getMessage()); }
}

function readProjectReportV2(string $projectId, array $query): array {
    $range = $query['range'] ?? '7d';
    $days = ['today' => 1, '7d' => 7, '30d' => 30, '90d' => 90][$range] ?? null;
    if ($days === null) { throw new InvalidArgumentException('range must be today, 7d, 30d, or 90d'); }
    $today = new DateTimeImmutable('today', new DateTimeZone('UTC'));
    $anchorRaw = $query['anchor'] ?? $today->format('Y-m-d');
    $anchor = DateTimeImmutable::createFromFormat('!Y-m-d', $anchorRaw, new DateTimeZone('UTC'));
    if (!$anchor || $anchor->format('Y-m-d') !== $anchorRaw || $anchor > $today) { throw new InvalidArgumentException('anchor must be a real UTC date no later than today'); }
    $platform = $query['platform'] ?? 'all';
    if (!in_array($platform, ['all','android','ios','web','desktop'], true)) { throw new InvalidArgumentException('Invalid platform'); }
    $kind = $query['filter_kind'] ?? 'platform'; $value = $query['filter_value'] ?? $platform;
    if (!validReportFilterV2($kind, $value)) throw new InvalidArgumentException('Invalid report filter');
    $platform = $kind === 'platform' ? $value : [$kind, $value];
    $compareRaw = $query['compare'] ?? 'false';
    if (!in_array($compareRaw, ['true', 'false'], true)) { throw new InvalidArgumentException('compare must be true or false'); }
    $compare = $compareRaw === 'true';
    $from = $range === 'today' ? $anchor : $anchor->modify('-' . ($days - 1) . ' days');
    $db = getDb();
    $peopleForWindow = static function (DateTimeImmutable $end, int $length) use ($db, $projectId, $platform): array {
        $start = $end->modify('-' . ($length - 1) . ' days');
        $stmt = $db->prepare("SELECT metric,day,hour,dims_hash,platform,segment_kind,segment_value,dims,n,contributors FROM counter_cells_v2 WHERE project_id=? AND metric='unique_window_v2' AND hour=-1 AND day BETWEEN ? AND ? AND (?='all' OR platform=?)");
        $stmt->execute([$projectId, $start->format('Y-m-d'), $end->format('Y-m-d'), reportPlatformV2($platform), reportPlatformV2($platform)]);
        $rows = [];
        foreach (selectCounterProjectionRows($stmt->fetchAll(PDO::FETCH_ASSOC), $platform) as $row) {
            $dims = json_decode($row['dims'], true);
            if (is_array($dims) && reportUniqueScopeV2($dims, $platform) &&
                (int)($dims['days'] ?? 0) === $length && ($dims['end_day'] ?? null) === $end->format('Y-m-d')) $rows[] = $row;
        }
        $value = array_sum(array_map(static fn($row) => (int)$row['n'], $rows));
        $witnesses = array_sum(array_map(static fn($row) => (int)$row['contributors'], $rows));
        return !$rows
            ? ['value' => null, 'state' => 'insufficient_data', 'unit' => 'installations', 'source' => 'v2']
            : ($witnesses < 5
                ? ['value' => null, 'state' => 'suppressed', 'unit' => 'installations', 'source' => 'v2']
                : ['value' => $value, 'state' => $value === 0 ? 'zero' : 'reported', 'unit' => 'installations', 'source' => 'v2', 'contributors' => $witnesses]);
    };
    $people = $peopleForWindow($anchor, $days);
    $activityCells = static function (string $metric, DateTimeImmutable $start, DateTimeImmutable $end, string $resolution, ?int $hour = null) use ($db, $projectId, $platform): array {
        $hourCondition = $resolution === 'today' ? ($hour === null ? 'hour >= 0' : 'hour = ?') : 'hour = -1';
        $stmt = $db->prepare("SELECT metric,day,hour,dims_hash,platform,segment_kind,segment_value,dims,n,contributors FROM counter_cells_v2 WHERE project_id=? AND metric=? AND day BETWEEN ? AND ? AND $hourCondition AND (?='all' OR platform=?)");
        $params = [$projectId, $metric, $start->format('Y-m-d'), $end->format('Y-m-d')];
        if ($resolution === 'today' && $hour !== null) $params[] = $hour;
        array_push($params, reportPlatformV2($platform), reportPlatformV2($platform));
        $stmt->execute($params);
        return selectCounterProjectionRows($stmt->fetchAll(PDO::FETCH_ASSOC), $platform);
    };
    $measureFromRows = static function (array $rows, array $eligiblePeople, string $unit, string $metric, DateTimeImmutable $start, DateTimeImmutable $end) use ($db, $projectId, $platform): array {
        if (!$rows) return ['value' => null, 'state' => 'insufficient_data', 'unit' => $unit, 'source' => 'v2'];
        if (($eligiblePeople['state'] ?? null) !== 'reported') return ['value' => null, 'state' => $eligiblePeople['state'], 'unit' => $unit, 'source' => 'v2'];
        $groups = [];
        foreach ($rows as $row) {
            $dims = json_decode($row['dims'], true);
            if (!is_array($dims)) continue;
            $checked = counterV2Validate($metric, $dims);
            if (($checked['valid'] ?? false) === true) $groups[$checked['hash']][] = $row;
        }
        $value = 0; $eligibleGroups = 0;
        $days = (int)$start->diff($end)->format('%a') + 1;
        foreach ($groups as $group) {
            $dims = json_decode($group[0]['dims'], true);
            if (!is_array($dims)) continue;
            $checked = counterV2Validate($metric, $dims);
            if (!($checked['valid'] ?? false)) continue;
            $contributors = reportMetricWitnessCountV2($db, $projectId, $metric, $dims, $start, $end, $platform, $group);
            if ($contributors >= 5) { $eligibleGroups++; $value += array_sum(array_map(static fn($r) => (int)$r['n'], $group)); }
        }
        if ($eligibleGroups === 0) return ['value' => null, 'state' => 'suppressed', 'unit' => $unit, 'source' => 'v2'];
        return ['value' => $value, 'state' => $value === 0 ? 'zero' : 'reported', 'unit' => $unit, 'source' => 'v2', 'contributors' => $eligiblePeople['value']];
    };
    $activityMeasure = static fn(string $metric, string $unit): array => $measureFromRows($activityCells($metric, $from, $anchor, $range), $people, $unit, $metric, $from, $anchor);
    $durationMeasure = static function(DateTimeImmutable $start, DateTimeImmutable $end, ?int $hour = null) use ($activityCells, $range, $db, $projectId, $platform): array {
        $durationRows=$activityCells('visit_duration_ms_v2',$start,$end,$range,$hour);
        $completionRows=$activityCells('visit_complete_v2',$start,$end,$range,$hour);
        $samples=array_sum(array_column($completionRows,'n'));
        $empty=['value'=>null,'state'=>'insufficient_data','unit'=>'milliseconds','source'=>'v2'];
        if (!$durationRows || $samples===0) return $empty;
        $completionDims=json_decode($completionRows[0]['dims'],true) ?: [];
        if ($samples<5 || reportMetricWitnessCountV2($db,$projectId,'visit_complete_v2',$completionDims,$start,$end,$platform,$completionRows)<5 ||
            reportMetricWitnessCountV2($db,$projectId,'visit_duration_ms_v2',[],$start,$end,$platform,$durationRows)<5) return array_merge($empty,['state'=>'suppressed']);
        return ['value'=>intdiv((int)array_sum(array_column($durationRows,'n')),(int)$samples),'state'=>'reported','unit'=>'milliseconds','source'=>'v2','contributors'=>$samples];
    };
    $visitDuration=$durationMeasure($from,$anchor);
    $overview = [
        'people' => $people,
        'visits' => $activityMeasure('visit_start_v2', 'visits'),
        'events' => $activityMeasure('event_v2', 'events'),
        'screen_views' => $activityMeasure('screen_view_v2', 'views'),
        'crashes' => $activityMeasure('crash_v2', 'crashes'),
        'visit_duration' => $visitDuration,
    ];
    $makePeopleBins = static function (string $binRange, DateTimeImmutable $start, DateTimeImmutable $end, ?int $hourLimit = null) use ($today, $db, $projectId, $platform, $peopleForWindow): array {
        $bins = [];
        if ($binRange === 'today') {
        $lastHour = $hourLimit ?? ($start->format('Y-m-d') === $today->format('Y-m-d') ? (int)gmdate('G') : 23);
        for ($hour = 0; $hour <= $lastHour; $hour++) {
            $hourQuery = $db->prepare("SELECT metric,day,hour,dims_hash,platform,segment_kind,segment_value,dims,n,contributors FROM counter_cells_v2 WHERE project_id=? AND metric='unique_hour_v2' AND day=? AND hour=? AND (?='all' OR platform=?)");
            $hourQuery->execute([$projectId, $start->format('Y-m-d'), $hour, reportPlatformV2($platform), reportPlatformV2($platform)]);
            $rows = array_values(array_filter(selectCounterProjectionRows($hourQuery->fetchAll(PDO::FETCH_ASSOC), $platform), static function ($row) use ($platform): bool {
                $dims = json_decode($row['dims'], true);
                return is_array($dims) && reportUniqueScopeV2($dims, $platform);
            }));
            $witnesses = array_sum(array_map(static fn($row) => (int)$row['contributors'], $rows));
            $measure = !$rows
                ? ['value' => null, 'state' => 'insufficient_data', 'unit' => 'installations', 'source' => 'v2']
                : ($witnesses < 5
                    ? ['value' => null, 'state' => 'suppressed', 'unit' => 'installations', 'source' => 'v2']
                    : ['value' => array_sum(array_map(static fn($row) => (int)$row['n'], $rows)), 'state' => array_sum(array_map(static fn($row) => (int)$row['n'], $rows)) === 0 ? 'zero' : 'reported', 'unit' => 'installations', 'source' => 'v2', 'contributors' => $witnesses]);
            $bins[] = ['start_day' => $start->format('Y-m-d'), 'end_day' => $start->format('Y-m-d'), 'hour' => $hour, 'measures' => ['people' => $measure]];
        }
        } elseif ($binRange === '90d') {
        for ($binStart = $start; $binStart <= $end;) {
            $binEnd = min($binStart->modify('+6 days'), $end);
            $length = (int)$binStart->diff($binEnd)->format('%a') + 1;
            $bins[] = ['start_day' => $binStart->format('Y-m-d'), 'end_day' => $binEnd->format('Y-m-d'), 'hour' => null, 'measures' => ['people' => $peopleForWindow($binEnd, $length)]];
            $binStart = $binEnd->modify('+1 day');
        }
        } else {
        for ($day = $start; $day <= $end; $day = $day->modify('+1 day')) {
            $date = $day->format('Y-m-d');
            $bins[] = ['start_day' => $date, 'end_day' => $date, 'hour' => null, 'measures' => ['people' => $peopleForWindow($day, 1)]];
        }
        }
        return $bins;
    };
    $peopleBins = $makePeopleBins($range, $from, $anchor);
    $previousTo = $from->modify('-1 day');
    $previousFrom = $range === 'today' ? $previousTo : $previousTo->modify('-' . ($days - 1) . ' days');
    $previousHourLimit = $range === 'today' && $anchor->format('Y-m-d') === $today->format('Y-m-d') ? (int)gmdate('G') : null;
    $comparisonPeopleBins = $compare ? $makePeopleBins($range, $previousFrom, $previousTo, $previousHourLimit) : [];
    $makeActivityBins = static function (string $metric, string $key, string $unit, array $bins) use ($activityCells, $measureFromRows, $range): array {
        return array_map(static function (array $bin) use ($metric, $key, $unit, $activityCells, $measureFromRows, $range): array {
            $start = new DateTimeImmutable($bin['start_day'], new DateTimeZone('UTC'));
            $end = new DateTimeImmutable($bin['end_day'], new DateTimeZone('UTC'));
            $rows = $activityCells($metric, $start, $end, $range, $bin['hour'] ?? null);
            $bin['measures'][$key] = $measureFromRows($rows, $bin['measures']['people'], $unit, $metric, $start, $end);
            return $bin;
        }, $bins);
    };
    $durationForBins = static function(array $bins) use ($durationMeasure): array {
        return array_map(static function(array $bin) use ($durationMeasure): array {
            $bin['measures']['visit_duration']=$durationMeasure(new DateTimeImmutable($bin['start_day'],new DateTimeZone('UTC')),new DateTimeImmutable($bin['end_day'],new DateTimeZone('UTC')),$bin['hour'] ?? null);
            return $bin;
        },$bins);
    };
    $charts = [
        'people' => $peopleBins,
        'visits' => $makeActivityBins('visit_start_v2', 'visits', 'visits', $peopleBins),
        'events' => $makeActivityBins('event_v2', 'events', 'events', $peopleBins),
        'screen_views' => $makeActivityBins('screen_view_v2', 'views', 'views', $peopleBins),
        'crashes' => $makeActivityBins('crash_v2', 'crashes', 'crashes', $peopleBins),
        'visit_duration' => $durationForBins($peopleBins),
    ];
    $comparisonCharts = !$compare ? (object)[] : [
        'people' => $comparisonPeopleBins,
        'visits' => $makeActivityBins('visit_start_v2', 'visits', 'visits', $comparisonPeopleBins),
        'events' => $makeActivityBins('event_v2', 'events', 'events', $comparisonPeopleBins),
        'screen_views' => $makeActivityBins('screen_view_v2', 'views', 'views', $comparisonPeopleBins),
        'crashes' => $makeActivityBins('crash_v2', 'crashes', 'crashes', $comparisonPeopleBins),
        'visit_duration' => $durationForBins($comparisonPeopleBins),
    ];
    // Always provide the prior-period People measure for the Overview headline. Detailed
    // comparison series and retention cohorts remain gated by the compare flag.
    $comparison = [
        'people' => $range === 'today'
            ? ['value' => null, 'state' => 'unsupported', 'unit' => 'installations', 'source' => 'v2']
            : $peopleForWindow($from->modify('-1 day'), $days),
    ];
    if ($compare) $comparison['visit_duration'] = $range === 'today' ? ['value'=>null,'state'=>'unsupported','unit'=>'milliseconds','source'=>'v2'] : $durationMeasure($previousFrom,$previousTo);
    $coverageQuery = $db->prepare('SELECT MIN(day) AS oldest_day, MAX(day) AS newest_day, MAX(updated_at) AS last_received_at, MAX(hour >= 0) AS has_hourly, GROUP_CONCAT(DISTINCT sdk_version ORDER BY sdk_version) AS sdk_versions FROM counter_cells_v2 WHERE project_id=? AND day BETWEEN ? AND ?');
    $coverageQuery->execute([$projectId, $from->format('Y-m-d'), $anchor->format('Y-m-d')]);
    $coverage = $coverageQuery->fetch(PDO::FETCH_ASSOC) ?: [];
    $legacy = $db->prepare('SELECT 1 FROM counters WHERE project_id=? LIMIT 1');
    $legacy->execute([$projectId]);
    $legacyPresent = $legacy->fetchColumn() !== false;
    $v2Present = $coverage['newest_day'] !== null;
    $coverageSource = $v2Present && $legacyPresent ? 'mixed' : ($v2Present ? 'v2' : ($legacyPresent ? 'legacy' : 'v2'));
    return [
        'scope' => ['range' => $range, 'anchor' => $anchor->format('Y-m-d'), 'platform' => reportPlatformV2($platform), 'filter_kind' => $kind, 'filter_value' => $value, 'timezone' => 'UTC', 'compare' => $compareRaw],
        'generated_at' => gmdate('Y-m-d\\TH:i:s\\Z'),
        'last_received_at' => $coverage['last_received_at'] ?: null,
        'coverage' => ['source' => $coverageSource, 'resolution' => ($range === 'today' && (int)($coverage['has_hourly'] ?? 0) === 1 ? 'hourly' : 'daily'), 'oldest_day' => $coverage['oldest_day'] ?: null, 'newest_day' => $coverage['newest_day'] ?: null, 'sdk_versions' => array_values(array_filter(explode(',', $coverage['sdk_versions'] ?? ''), static fn($v) => $v !== '' && $v !== 'unknown')), 'legacy_present' => $legacyPresent],
        'overview' => $overview, 'charts' => $charts, 'comparison_charts' => $comparisonCharts, 'comparison' => $comparison,
        'flow' => [
            'screen_views' => $activityMeasure('screen_view_v2', 'views'),
            'transitions' => $activityMeasure('screen_transition_v2', 'transitions'),
        ],
        'retention' => reportRetentionMeasuresV2($db, $projectId, $from, $anchor, $platform),
        'retention_cohorts' => reportRetentionCohortsV2($db, $projectId, $from, $anchor, $platform),
        'comparison_retention_cohorts' => $compare ? reportRetentionCohortsV2($db, $projectId, $previousFrom, $previousTo, $platform) : [],
        'definitions' => reportDefinitionCatalogV2($db, $projectId, $from, $anchor, $platform),
    ];
}

function reportRetentionCohortsV2(PDO $db, string $projectId, DateTimeImmutable $from, DateTimeImmutable $to, string|array $platform): array {
    $stmt = $db->prepare("SELECT metric,day,hour,dims_hash,platform,segment_kind,segment_value,dims,n,contributors FROM counter_cells_v2 WHERE project_id=? AND metric IN ('retention_entry_v2','retention_return_v2') AND day BETWEEN ? AND ? AND hour=-1 AND (?='all' OR platform=?)");
    $stmt->execute([$projectId, $from->format('Y-m-d'), $to->format('Y-m-d'), reportPlatformV2($platform), reportPlatformV2($platform)]);
    $entries = [];
    $returns = [];
    foreach (selectCounterProjectionRows($stmt->fetchAll(PDO::FETCH_ASSOC), $platform) as $row) {
        $dims = json_decode($row['dims'], true);
        if (!is_array($dims)) continue;
        $entry = $dims['entry_day'] ?? null;
        if (!is_string($entry) || $entry < $from->format('Y-m-d') || $entry > $to->format('Y-m-d')) continue;
        if ($row['metric'] === 'retention_entry_v2') $entries[$entry][] = $row;
        else $returns[] = [$row, $entry, (int)($dims['age_day'] ?? 0)];
    }
    ksort($entries, SORT_STRING);
    $result = [];
    foreach ($entries as $entry => $group) {
        $size = array_sum(array_map(static fn($row) => (int)$row['n'], $group));
        $sizeWitnesses = array_sum(array_map(static fn($row) => (int)$row['contributors'], $group));
        $sizeMeasure = $sizeWitnesses < 5
            ? ['value'=>null,'state'=>'suppressed','unit'=>'installations','source'=>'v2']
            : ['value'=>$size,'state'=>$size === 0 ? 'zero' : 'reported','unit'=>'installations','source'=>'v2','contributors'=>$sizeWitnesses];
        $dayMeasures = [];
        foreach ([1,3,7,14,30] as $age) {
            $mature = (new DateTimeImmutable($entry, new DateTimeZone('UTC')))->modify("+{$age} days") <= $to;
            if (!$mature) { $dayMeasures[(string)$age] = ['value'=>null,'state'=>'not_mature','unit'=>'installations','source'=>'v2']; continue; }
            if ($sizeWitnesses < 5) { $dayMeasures[(string)$age] = ['value'=>null,'state'=>'suppressed','unit'=>'installations','source'=>'v2']; continue; }
            $matching = array_values(array_filter($returns, static fn($pair) => $pair[1] === $entry && $pair[2] === $age));
            $count = array_sum(array_map(static fn($pair) => (int)$pair[0]['n'], $matching));
            $witnesses = array_sum(array_map(static fn($pair) => (int)$pair[0]['contributors'], $matching));
            if ($count > $size || ($count > 0 && $witnesses < 5)) $dayMeasures[(string)$age] = ['value'=>null,'state'=>'suppressed','unit'=>'installations','source'=>'v2'];
            else $dayMeasures[(string)$age] = ['value'=>$count,'state'=>$count === 0 ? 'zero' : 'reported','unit'=>'installations','source'=>'v2','contributors'=>$sizeWitnesses,'rate'=>$size > 0 ? $count / $size : 0.0];
        }
        $result[] = ['entry_day'=>$entry,'size'=>$sizeMeasure,'days'=>$dayMeasures];
    }
    return $result;
}

function reportDefinitionCatalogV2(PDO $db, string $projectId, DateTimeImmutable $from, DateTimeImmutable $to, string|array $platform): array {
    $stmt = $db->prepare("SELECT metric,day,hour,dims_hash,platform,segment_kind,segment_value,dims,contributors FROM counter_cells_v2 WHERE project_id=? AND metric IN ('screen_view_v2','event_v2') AND day BETWEEN ? AND ? AND hour=-1 AND (?='all' OR platform=?)");
    $stmt->execute([$projectId, $from->format('Y-m-d'), $to->format('Y-m-d'), reportPlatformV2($platform), reportPlatformV2($platform)]);
    $totals = ['screens' => [], 'events' => []];
    $rows = selectCounterProjectionRows($stmt->fetchAll(PDO::FETCH_ASSOC), $platform);
    $groups = [];
    foreach ($rows as $row) {
        $dims = json_decode($row['dims'], true);
        if (!is_array($dims)) continue;
        $target = $row['metric'] === 'screen_view_v2' ? 'screens' : 'events';
        $label = $dims[$target === 'screens' ? 'screen' : 'name'] ?? null;
        if (!is_string($label) || $label === '') continue;
        $checked = counterV2Validate($row['metric'], $dims);
        if (($checked['valid'] ?? false) === true) $groups[$target][$label][$checked['hash']][] = [$row, $dims];
    }
    foreach ($groups as $target => $labels) {
        foreach ($labels as $label => $groupsByDims) {
            foreach ($groupsByDims as $cells) {
                $row = $cells[0][0]; $dims = $cells[0][1]; $metric = $row['metric'];
                $cellRows = array_map(static fn($pair) => $pair[0], $cells);
                if (reportMetricWitnessCountV2($db, $projectId, $metric, $dims, $from, $to, $platform, $cellRows) >= 5) $totals[$target][$label] = true;
            }
        }
    }
    foreach ($totals as $key => $labels) {
        $labels = array_keys($labels);
        sort($labels, SORT_STRING);
        $totals[$key] = $labels;
    }
    return $totals;
}

/** Mature entry cohorts only; a missing return row is a true zero only for a mature cohort. */
function reportRetentionMeasuresV2(PDO $db, string $projectId, DateTimeImmutable $from, DateTimeImmutable $to, string|array $platform): array {
    $stmt = $db->prepare("SELECT metric,day,hour,dims_hash,platform,segment_kind,segment_value,dims,n,contributors FROM counter_cells_v2 WHERE project_id=? AND metric IN ('retention_entry_v2','retention_return_v2') AND day BETWEEN ? AND ? AND hour=-1 AND (?='all' OR platform=?)");
    $stmt->execute([$projectId, $from->format('Y-m-d'), $to->format('Y-m-d'), reportPlatformV2($platform), reportPlatformV2($platform)]);
    $entries = [];
    $returns = [];
    foreach (selectCounterProjectionRows($stmt->fetchAll(PDO::FETCH_ASSOC), $platform) as $row) {
        $dims = json_decode($row['dims'], true);
        if (!is_array($dims)) continue;
        $entry = $dims['entry_day'] ?? null;
        if (!is_string($entry) || $entry < $from->format('Y-m-d') || $entry > $to->format('Y-m-d')) continue;
        if ($row['metric'] === 'retention_entry_v2') $entries[] = [$row, $entry];
        else $returns[] = [$row, $entry, (int)($dims['age_day'] ?? 0)];
    }
    $result = [];
    foreach ([1,3,7,14,30] as $age) {
        $mature = array_values(array_filter($entries, static fn($pair) => (new DateTimeImmutable($pair[1], new DateTimeZone('UTC')))->modify("+{$age} days") <= $to));
        $key = 'day_' . $age;
        if (!$entries) { $result[$key] = ['value'=>null,'state'=>'insufficient_data','unit'=>'installations','source'=>'v2']; continue; }
        if (!$mature) { $result[$key] = ['value'=>null,'state'=>'not_mature','unit'=>'installations','source'=>'v2']; continue; }
        $denominator = array_sum(array_map(static fn($pair) => (int)$pair[0]['contributors'], $mature));
        if ($denominator < 5) { $result[$key] = ['value'=>null,'state'=>'suppressed','unit'=>'installations','source'=>'v2']; continue; }
        $matureDays = array_fill_keys(array_map(static fn($pair) => $pair[1], $mature), true);
        $matching = array_values(array_filter($returns, static fn($pair) => $pair[2] === $age && isset($matureDays[$pair[1]])));
        $numerator = array_sum(array_map(static fn($pair) => (int)$pair[0]['n'], $matching));
        $witnesses = array_sum(array_map(static fn($pair) => (int)$pair[0]['contributors'], $matching));
        if ($numerator > $denominator || ($numerator > 0 && $witnesses < 5)) $result[$key] = ['value'=>null,'state'=>'suppressed','unit'=>'installations','source'=>'v2'];
        else $result[$key] = ['value'=>$numerator,'state'=>$numerator === 0 ? 'zero' : 'reported','unit'=>'installations','source'=>'v2','contributors'=>$denominator,'rate'=>$numerator / $denominator];
    }
    return $result;
}

/** GET /api/v1/projects/{id}/aggregates?range=7d → top events, daily counts. */
/**
 * "Ship what maps cleanly": top_events, screen_durations (bucket-approximated), platforms,
 * and daily totals all read from counters. top_screens is approximated from
 * screen_transition (no standalone "screen viewed" counter exists). countries/device_classes
 * are not tracked as counter dimensions yet, so those fields are empty rather than silently
 * faked; offline is always 0 for the same reason. Mirrors
 * servers/ktor/.../routes/DashboardRoutes.kt's /aggregates handler.
 */
function handleProjectAggregates(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $seconds = windowSeconds(365);
    $window = requestAnalyticsWindow($seconds);
    $toDay = $window['to_day'];
    $fromDay = $window['from_day'];

    $eventCells = counterReadCells($projectId, 'event', $fromDay, $toDay);
    usort($eventCells, static fn(array $a, array $b): int => $b['n'] <=> $a['n']);
    $topEvents = array_map(
        static fn(array $c): array => ['event_name' => $c['dims']['name'] ?? null, 'count' => $c['n']],
        array_slice($eventCells, 0, 20),
    );

    $transitionCells = counterReadCells($projectId, 'screen_transition', $fromDay, $toDay);
    $topScreens = array_map(
        static fn(array $r): array => ['screen' => $r['screen'], 'count' => $r['n']],
        overviewCounterTopScreens($transitionCells, 20),
    );

    $dwellCells = counterReadCells($projectId, 'screen_dwell', $fromDay, $toDay);
    $screenDurations = array_map(
        static fn(array $r): array => ['screen' => $r['screen'], 'count' => $r['count'], 'avg_ms' => $r['avg_ms'], 'total_ms' => $r['total_ms']],
        array_slice(overviewCounterScreenDurations($dwellCells), 0, 20),
    );

    $dailyTotals = counterDailyTotals($projectId, 'event', $fromDay, $toDay);
    $daily = array_map(
        static fn(array $r): array => ['day' => $r['day'], 'total' => $r['n'], 'offline_total' => 0],
        $dailyTotals,
    );

    $platforms = array_map(
        static fn(array $r): array => ['name' => $r['platform'], 'count' => $r['n']],
        counterTotalsByPlatform($projectId, 'event', $fromDay, $toDay),
    );

    $peopleDays = $window['days'] <= 7 ? max(1, (int)$window['days']) : ($window['days'] <= 30 ? 30 : 90);
    $peopleFrom = (new DateTimeImmutable($toDay))->modify('-' . ($peopleDays - 1) . ' days')->format('Y-m-d');
    $people = null;
    foreach (counterReadCells($projectId, 'unique_window_v2', $peopleFrom, $toDay) as $cell) {
        if (($cell['dims']['scope_kind'] ?? null) === 'project' &&
            ($cell['dims']['scope_key'] ?? null) === 'all' &&
            ($cell['dims']['days'] ?? null) === (string)$peopleDays &&
            ($cell['dims']['end_day'] ?? null) === $toDay) {
            $people = $cell['n'];
            break;
        }
    }
    $countries = $people !== null
        ? counterReadUniqueWindowByFacet($projectId, $peopleFrom, $toDay, $peopleDays, $toDay, 'country')
        : [];
    if ($people !== null) {
        $platforms = counterReadUniqueWindowByFacet($projectId, $peopleFrom, $toDay, $peopleDays, $toDay, 'platform');
    }
    $deviceClasses = $people !== null
        ? counterReadUniqueWindowByFacet($projectId, $peopleFrom, $toDay, $peopleDays, $toDay, 'device_class')
        : [];

    $valueCells = counterReadCells($projectId, 'value', $fromDay, $toDay);
    $topValues = array_map(
        static fn(array $r): array => ['name' => $r['name'], 'total_minor_units' => $r['total_minor_units']],
        array_slice(valueCounterAnalyze($valueCells), 0, 10),
    );

    $totalEvents = array_sum(array_column($eventCells, 'n'));
    $errors = array_sum(array_map(
        static fn(array $c): int => in_array(strtolower($c['dims']['name'] ?? ''), ['error', 'crash', 'exception'], true) ? $c['n'] : 0,
        $eventCells,
    ));

    jsonResponse(200, [
        'window_days'    => $window['days'],
        'people'         => $people,
        'totals'         => [
            'events'  => $totalEvents,
            'offline' => 0,
            'errors'  => $errors,
        ],
        'top_events'       => $topEvents,
        'top_screens'      => $topScreens,
        'screen_durations' => $screenDurations,
        'top_values'       => $topValues,
        'daily'            => $daily,
        'countries'      => $countries,
        'platforms'      => $platforms,
        'device_classes' => $deviceClasses,
    ]);
}

/** GET /api/v1/projects/{id}/transitions?range=7d → screen-to-screen flow. */
/**
 * Reads from the screen_transition counter (see counters.php / counterRegistry.php) instead
 * of the raw events table -- the device itself already reduced navigation to A->B pairs (see
 * ScreenTracker.kt's enter()), so there is no per-session trail to walk here any more.
 * Mirrors servers/ktor/.../routes/DashboardRoutes.kt's /transitions handler.
 */
function handleProjectTransitions(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $seconds = windowSeconds(90);
    $window = requestAnalyticsWindow($seconds);
    $toDay = $window['to_day'];
    $fromDay = $window['from_day'];

    $platform = (string)($_GET['platform'] ?? 'all');
    if (!in_array($platform, ['all', 'android', 'ios', 'web', 'desktop', 'unknown'], true)) {
        jsonResponse(400, ['error' => 'invalid_platform']);
        return;
    }
    $v2 = counterReadTransitionsV2($projectId, $fromDay, $toDay, $platform);
    $cells = $v2['present'] ? $v2['cells'] : counterReadTransitionsLegacy($projectId, $fromDay, $toDay, $platform);
    $result = [];
    foreach ($cells as $cell) {
        $from = $cell['dims']['from'] ?? null;
        $to = $cell['dims']['to'] ?? null;
        if ($from === null || $to === null) continue;
        $result[] = ['from_screen' => (string)$from, 'to_screen' => (string)$to, 'count' => $cell['n']];
    }
    usort($result, static fn(array $a, array $b): int => $b['count'] <=> $a['count']);
    $result = array_slice($result, 0, 50);

    jsonResponse(200, ['transitions' => $result]);
}

/**
 * GET /api/v1/projects/{id}/search?days=30 -> per-screen zero-result search rate.
 *
 * The search query itself is never sent (see the SDK's trackSearch) -- only whether it came
 * back empty. Mirrors servers/ktor/.../routes/DashboardRoutes.kt's /search handler.
 */
function handleProjectSearch(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $window = requestAnalyticsWindow(windowSeconds(90));
    $toDay = $window['to_day'];
    $fromDay = $window['from_day'];

    $searchCells = counterReadCells($projectId, 'search', $fromDay, $toDay);
    $zeroResultCells = counterReadCells($projectId, 'search_zero_result', $fromDay, $toDay);
    $screens = array_map(
        static fn(array $r): array => ['screen' => $r['screen'], 'total' => $r['total'], 'zero_result' => $r['zero_result'], 'rate' => $r['rate']],
        searchCounterAnalyze($searchCells, $zeroResultCells),
    );

    jsonResponse(200, ['screens' => $screens]);
}

/**
 * GET /api/v1/projects/{id}/friction?days=30 -> per-screen rage-tap count.
 *
 * Mirrors servers/ktor/.../routes/DashboardRoutes.kt's /friction handler. See the SDK's
 * RageTapDetector/FrictionCounterBridge for how this is captured on-device.
 */
function handleProjectFriction(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $window = requestAnalyticsWindow(windowSeconds(90));
    $toDay = $window['to_day'];
    $fromDay = $window['from_day'];

    $frictionCells = counterReadCells($projectId, 'friction', $fromDay, $toDay);
    $screens = array_map(
        static fn(array $r): array => ['screen' => $r['screen'], 'rage_taps' => $r['rage_taps']],
        frictionCounterAnalyze($frictionCells),
    );

    jsonResponse(200, ['screens' => $screens]);
}

/**
 * GET /api/v1/projects/{id}/sessions?range=7d → session analytics.
 *
 * Reads from the session{bucket} counter (see counters.php / sessionCounterAnalyze.php)
 * instead of grouping the raw events table by session_id -- the device itself already
 * reduced a session to one bucketed length (see the SDK's SessionTracker). No avg_events
 * field: no counter ties an event count to a session under aggregate-only ingest, so it
 * would be a permanently-fake number rather than an honest gap. Mirrors
 * servers/ktor/.../routes/DashboardRoutes.kt's /sessions handler.
 */
function handleProjectSessions(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $seconds = windowSeconds(90);
    $window = requestAnalyticsWindow($seconds);
    $toDay = $window['to_day'];
    $fromDay = $window['from_day'];

    $cells = counterReadCells($projectId, 'session', $fromDay, $toDay);
    $analysis = sessionCounterAnalyze($cells);
    $dailySessions = array_map(
        static fn(array $r): array => ['day' => $r['day'], 'total' => $r['n']],
        counterDailyTotals($projectId, 'session', $fromDay, $toDay),
    );

    jsonResponse(200, [
        'total_sessions'     => $analysis['total_sessions'],
        'avg_duration_sec'   => $analysis['avg_duration_sec'],
        'daily_sessions'     => $dailySessions,
    ]);
}

/** GET /api/v1/projects/{id}/retention?days=30 → cohort retention matrix. */
/**
 * Reads from the retention counter (see counters.php / counterRegistry.php) instead of
 * inferring cohorts from session first-seen dates -- the device itself already knows its
 * first-launch date and reports day="0" (cohort join) and each "at least N days later" mark
 * it reaches (see the SDK's RetentionReporter). Mirrors
 * servers/ktor/.../routes/DashboardRoutes.kt's /retention handler.
 */
function handleProjectRetention(string $projectId): void {
    requireAnalyticsAccess($projectId);

    $seconds = windowSeconds(90);
    $window = requestAnalyticsWindow($seconds);
    $toDay = $window['to_day'];
    $fromDay = $window['from_day'];

    $counterCells = counterReadCells($projectId, 'retention', $fromDay, $toDay);
    $cells = [];
    foreach ($counterCells as $c) {
        $cohort = $c['dims']['cohort'] ?? null;
        $day = isset($c['dims']['day']) ? (int)$c['dims']['day'] : null;
        if ($cohort === null || $day === null) continue;
        $started = reportedCohortStart($cohort);
        if ($started === null || $started < $fromDay || $started > $toDay) continue;
        $cells[] = ['cohort' => $cohort, 'day' => $day, 'n' => $c['n']];
    }

    $cohorts = retentionCounterAnalyze($cells);

    // Null (not 0.0) means "this project hasn't configured activationEvent" -- distinct from
    // a real 0% rate. The server can't see the SDK's config, so it approximates: no
    // activation{cohort} cells anywhere in the queried window is read as "not tracked" for
    // every cohort in this response, rather than a suspicious all-zero row.
    $activationCounterCells = counterReadCells($projectId, 'activation', $fromDay, $toDay);
    $activationCells = [];
    foreach ($activationCounterCells as $c) {
        $cohort = $c['dims']['cohort'] ?? null;
        if ($cohort === null) continue;
        $activationCells[] = ['cohort' => $cohort, 'n' => $c['n']];
    }
    $activationRateByCohort = [];
    if (!empty($activationCells)) {
        $cohortSizes = [];
        foreach ($cohorts as $r) { $cohortSizes[$r['cohort']] = $r['size']; }
        foreach (activationCounterAnalyze($activationCells, $cohortSizes) as $r) {
            $activationRateByCohort[$r['cohort']] = $r['rate'];
        }
    }

    $result = array_map(static function (array $r) use ($activationRateByCohort): array {
        return [
            'cohort_date' => $r['cohort'],
            'size' => $r['size'],
            'day1' => $r['day1'],
            'day3' => $r['day3'],
            'day7' => $r['day7'],
            'day14' => $r['day14'],
            'day30' => $r['day30'],
            'activation_rate' => $activationRateByCohort[$r['cohort']] ?? null,
        ];
    }, $cohorts);

    jsonResponse(200, ['cohorts' => $result]);
}

/** Legacy alias uses the same optional AI policy and existing answer/source response shape. */
function handleProjectAsk(string $projectId): void {
    require_once __DIR__ . '/ai.php';
    handleAi($projectId, 'ask', true);
}
