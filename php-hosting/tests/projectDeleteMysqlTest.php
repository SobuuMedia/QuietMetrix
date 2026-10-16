<?php

/** Opt-in route regression using its own disposable MySQL database. */
$host = getenv('BOOTSTRAP_TEST_HOST');
if (!$host) { echo "skipped: set BOOTSTRAP_TEST_HOST for project deletion MySQL integration\n"; exit(0); }
$user = getenv('BOOTSTRAP_TEST_USER') ?: 'root';
$pass = getenv('BOOTSTRAP_TEST_PASS') ?: '';
$server = new PDO("mysql:host=$host;charset=utf8mb4", $user, $pass, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$database = 'qm_delete_' . bin2hex(random_bytes(6));
$server->exec("CREATE DATABASE `$database` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
define('DB_HOST', $host); define('DB_NAME', $database); define('DB_USER', $user); define('DB_PASS', $pass); define('DB_CHARSET', 'utf8mb4');
require_once __DIR__ . '/../src/db.php';
require_once __DIR__ . '/../src/helpers.php';
require_once __DIR__ . '/../src/errorDiagnostics.php';
require_once __DIR__ . '/../src/routes/projects.php';
function requireSession(): array { return ['role' => 'admin', 'platformOperator' => true]; }
function requireAdmin(array $session): void {}
function deleteCheck(bool $ok, string $message): void { if (!$ok) throw new RuntimeException($message); }
function deleteRequest(string $id): array {
    ob_start();
    try { handleProjectsDelete($id); return [http_response_code(), json_decode(ob_get_contents(), true, 512, JSON_THROW_ON_ERROR)]; }
    finally { ob_end_clean(); }
}
$db = getDb();
$_SERVER['REQUEST_METHOD'] = 'DELETE';
try {
    $schema = preg_replace('/--[^\r\n]*/', '', file_get_contents(__DIR__ . '/../schema.sql'));
    foreach (explode(';', $schema) as $sql) if (trim($sql) !== '') $db->exec($sql);
    $db->prepare('INSERT INTO users(id,email,password_hash,created_at) VALUES(?,?,?,?)')->execute(['owner', 'owner@example.test', 'fixture', now()]);
    $db->prepare('INSERT INTO projects(id,name,owner_user_id,api_key_hash,created_at) VALUES(?,?,?,?,?)')->execute(['keep', 'Keep', 'owner', tokenHash('keep'), now()]);
    foreach (['neither', 'operations_only', 'cache_only', 'both'] as $tables) {
        foreach (explode(';', file_get_contents(__DIR__ . '/../ai-schema.sql')) as $sql) if (trim($sql) !== '') $db->exec($sql);
        $db->exec('DELETE FROM ai_summary_cache'); $db->exec('DELETE FROM ai_operations');
        foreach (['delete', 'keep'] as $id) {
            $db->prepare('INSERT INTO ai_operations(operation_id,account_id,project_id,month,fingerprint,state,reserved_cost,created_at,result_expires) VALUES(?,?,?,?,?,?,?,?,?)')
                ->execute([$id, 'owner', $id, '2026-10-01', 'fixture', 'success', 0, time(), time() + 900]);
            $db->prepare('INSERT INTO ai_summary_cache(cache_key,operation_id,result) VALUES(?,?,?)')->execute([$id, $id, '{"answer":"fixture"}']);
        }
        if (in_array($tables, ['neither', 'cache_only'], true)) $db->exec('DROP TABLE ai_operations');
        if (in_array($tables, ['neither', 'operations_only'], true)) $db->exec('DROP TABLE ai_summary_cache');
        $db->prepare('INSERT INTO projects(id,name,owner_user_id,api_key_hash,created_at) VALUES(?,?,?,?,?)')->execute(['delete', 'Delete', 'owner', tokenHash('delete'), now()]);
        $db->prepare('INSERT INTO project_members(project_id,user_id,role,added_at) VALUES(?,?,?,?)')->execute(['delete', 'owner', 'viewer', now()]);
        $db->exec("INSERT INTO usage_counters(project_id,period,events_count) VALUES('delete','202610',5)");
        deleteCheck(deleteRequest('delete') === [200, ['ok' => true]], "$tables: deletion failed");
        deleteCheck((int)$db->query("SELECT COUNT(*) FROM projects WHERE id='delete'")->fetchColumn() === 0, "$tables: project remains");
        foreach (['project_members', 'usage_counters'] as $table) {
            deleteCheck((int)$db->query("SELECT COUNT(*) FROM $table WHERE project_id='delete'")->fetchColumn() === 0, "$tables: $table remains");
        }
        if (tableExists($db, 'ai_operations')) {
            deleteCheck($db->query('SELECT project_id FROM ai_operations')->fetchAll(PDO::FETCH_COLUMN) === ['keep'], "$tables: AI operations were not scoped to the deleted project");
            if (tableExists($db, 'ai_summary_cache')) deleteCheck($db->query('SELECT operation_id FROM ai_summary_cache')->fetchAll(PDO::FETCH_COLUMN) === ['keep'], 'AI cache cleanup was not scoped to the deleted project');
        }
        deleteCheck((int)$db->query("SELECT COUNT(*) FROM projects WHERE id='keep'")->fetchColumn() === 1, "$tables: other project was deleted");
        deleteCheck(deleteRequest('delete')[0] === 404, "$tables: missing project must return 404");
    }
    // A database error must roll back AI cleanup along with project deletion.
    $db->prepare('INSERT INTO projects(id,name,owner_user_id,api_key_hash,created_at) VALUES(?,?,?,?,?)')->execute(['rollback', 'Rollback', 'owner', tokenHash('rollback'), now()]);
    $db->exec("INSERT INTO ai_operations(operation_id,account_id,project_id,month,fingerprint,state,reserved_cost,created_at,result_expires) VALUES('rollback','owner','rollback','2026-10-01','fixture','success',0,0,0)");
    $db->exec("INSERT INTO ai_summary_cache(cache_key,operation_id,result) VALUES('rollback','rollback','{}')");
    $db->exec("CREATE TRIGGER reject_delete BEFORE DELETE ON projects FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
    try { deleteRequest('rollback'); throw new LogicException('Database failure was ignored'); }
    catch (PDOException $error) { deleteCheck($error->getCode() === '45000', 'Unexpected database failure'); }
    foreach (['projects' => 'id', 'ai_operations' => 'operation_id', 'ai_summary_cache' => 'cache_key'] as $table => $column) {
        deleteCheck((int)$db->query("SELECT COUNT(*) FROM $table WHERE $column='rollback'")->fetchColumn() === 1, "$table: cleanup was not rolled back");
    }
    deleteCheck(!$db->inTransaction(), 'Failed deletion left an open transaction');
    echo "Project deletion passed: optional AI tables, scoped cleanup, cascades, missing projects and atomic rollback.\n";
} finally {
    if ($db->inTransaction()) $db->rollBack();
    $server->exec("DROP DATABASE `$database`");
}
