<?php

/** Run from requests and a one-minute CLI cron; expiry also runs while the site is idle. */
function dataMaintenance(PDO $db, bool $force = false): int {
    if (!(int)$db->query("SELECT GET_LOCK('qm_data_maintenance',0)")->fetchColumn()) return 0;
    $key = hash('sha256', 'qm_data_maintenance');
    try {
        $q=$db->prepare('SELECT expires_at FROM request_rate_buckets WHERE bucket_key=?'); $q->execute([$key]);
        if (!$force && (int)$q->fetchColumn() > time()) return 0;
        $db->beginTransaction(); $deleted=0;
        $q=$db->prepare('DELETE FROM auth_sessions WHERE expires_at<=?'); $q->execute([time()]); $deleted+=$q->rowCount();
        $q=$db->prepare('DELETE FROM request_rate_buckets WHERE expires_at<=?'); $q->execute([time()]); $deleted+=$q->rowCount();
        $q=$db->prepare('DELETE FROM counter_batch_receipts_v2 WHERE expires_at<=?'); $q->execute([now()]); $deleted+=$q->rowCount();
        $q=$db->prepare('DELETE FROM counter_batch_receipts WHERE created_at<?'); $q->execute([gmdate('Y-m-d\TH:i:s\Z',time()-14*86400)]); $deleted+=$q->rowCount();
        $q=$db->prepare('DELETE FROM counters_quarantine WHERE quarantined_at<?'); $q->execute([gmdate('Y-m-d\TH:i:s\Z',time()-7*86400)]); $deleted+=$q->rowCount();
        $db->exec("UPDATE counters_quarantine SET payload='{}',quarantine_detail=NULL");
        $configured=getenv('QM_RETENTION_DAYS');
        foreach ($db->query('SELECT id,plan_id FROM projects')->fetchAll() as $project) {
            $days=$configured !== false && $configured !== '' ? (int)$configured
                : (cloudMode() ? (['hobby'=>90,'startup'=>365,'business'=>730][(string)($project['plan_id'] ?? '')] ?? 30) : 0);
            if ($days<=0) continue;
            $cutoff=gmdate('Y-m-d',time()-($days-1)*86400);
            foreach (['counters','counter_cells_v2','event_counts_daily'] as $table) {
                $q=$db->prepare("DELETE FROM $table WHERE project_id=? AND day<?"); $q->execute([$project['id'],$cutoff]); $deleted+=$q->rowCount();
            }
            $q=$db->prepare("SELECT day,hour,metric,platform,app_version,country,dims_hash,segment_kind,segment_value,dims FROM counter_cells_v2 WHERE project_id=? AND metric IN ('unique_window_v2','witness_window_v2','funnel_step_v2','retention_entry_v2','retention_return_v2')");
            $q->execute([$project['id']]);
            foreach ($q->fetchAll() as $row) {
                $d=json_decode($row['dims'],true);
                $expiredEntry=isset($d['entry_day']) && $d['entry_day'] < $cutoff;
                $expiredWindow=isset($d['end_day'],$d['days']) && gmdate('Y-m-d',strtotime($d['end_day'].' UTC')-((int)$d['days']-1)*86400) < $cutoff;
                if (!$expiredEntry && !$expiredWindow) continue;
                $delete=$db->prepare("DELETE FROM counter_cells_v2 WHERE project_id=? AND metric=? AND day=? AND hour=? AND platform=? AND app_version=? AND country=? AND dims_hash=? AND segment_kind=? AND segment_value=?");
                $delete->execute([$project['id'],$row['metric'],$row['day'],$row['hour'],$row['platform'],$row['app_version'],$row['country'],$row['dims_hash'],$row['segment_kind'],$row['segment_value']]); $deleted+=$delete->rowCount();
            }
            $legacy=$db->prepare("SELECT day,platform,app_version,country,dims_hash,dims FROM counters WHERE project_id=? AND metric='unique_window_v2'");
            $legacy->execute([$project['id']]);
            foreach ($legacy->fetchAll() as $row) {
                $d=json_decode($row['dims'],true);
                if (!isset($d['end_day'],$d['days']) || gmdate('Y-m-d',strtotime($d['end_day'].' UTC')-((int)$d['days']-1)*86400) >= $cutoff) continue;
                $delete=$db->prepare("DELETE FROM counters WHERE project_id=? AND metric='unique_window_v2' AND day=? AND platform=? AND app_version=? AND country=? AND dims_hash=?");
                $delete->execute([$project['id'],$row['day'],$row['platform'],$row['app_version'],$row['country'],$row['dims_hash']]); $deleted+=$delete->rowCount();
            }
        }
        $db->prepare('INSERT INTO request_rate_buckets(bucket_key,attempts,expires_at) VALUES (?,0,?) ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)')->execute([$key,time()+60]);
        $db->commit(); return $deleted;
    } catch (Throwable $e) { if ($db->inTransaction()) $db->rollBack(); throw $e; }
    finally { $db->query("SELECT RELEASE_LOCK('qm_data_maintenance')"); }
}
