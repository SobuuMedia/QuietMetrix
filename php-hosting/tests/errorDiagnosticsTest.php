<?php
require_once __DIR__ . '/../src/errorDiagnostics.php';

function diagnosticCheck(bool $condition, string $message): void {
    if (!$condition) throw new RuntimeException($message);
}

$operator = ['role'=>'admin', 'platformOperator'=>true];
foreach ([
    ['GET', '1', $operator, true],
    ['GET', '', $operator, false],
    ['POST', '1', $operator, false],
    ['DELETE', '1', $operator, false],
    ['GET', '1', ['role'=>'admin', 'platformOperator'=>false], false],
    ['GET', '1', ['role'=>'reviewer', 'platformOperator'=>true], false],
    ['GET', '1', ['role'=>'admin', 'platformOperator'=>true, 'scopes'=>['analytics:read']], false],
    ['GET', '1', [], false],
] as [$method, $header, $session, $allowed]) {
    unset($GLOBALS['qm_operator_error_diagnostics']);
    $_SERVER['REQUEST_METHOD'] = $method;
    $_SERVER['HTTP_X_QM_DIAGNOSTICS'] = $header;
    enableOperatorErrorDiagnostics($session);
    diagnosticCheck(($GLOBALS['qm_operator_error_diagnostics'] ?? false) === $allowed, 'Diagnostics authorization failed');
}

foreach ([
    [$operator, true],
    [['role'=>'admin', 'platformOperator'=>false], false],
    [['role'=>'reviewer', 'platformOperator'=>true], false],
    [['role'=>'admin', 'platformOperator'=>true, 'scopes'=>['projects:read']], false],
    [[], false],
] as [$session, $allowed]) {
    unset($GLOBALS['qm_operator_error_diagnostics']);
    $_SERVER['REQUEST_METHOD'] = 'DELETE';
    $_SERVER['HTTP_X_QM_DIAGNOSTICS'] = '1';
    enableOperatorErrorDiagnostics($session, 'DELETE');
    diagnosticCheck(($GLOBALS['qm_operator_error_diagnostics'] ?? false) === $allowed, 'Deletion diagnostics authorization failed');
}

$error = new PDOException("SQLSTATE[42S22]: Column not found: 1054 Unknown column 'devices' in 'field list'");
$error->errorInfo = ['42S22',1054,"Unknown column 'devices' in 'field list'"];
$report = operatorErrorDiagnostics($error);
diagnosticCheck($report['sqlstate'] === '42S22' && $report['driver_code'] === 1054 && $report['column'] === 'devices', 'Missing-column diagnosis failed');
diagnosticCheck($report['category'] === 'missing_column', 'Missing-column category failed');
diagnosticCheck($report['source_sha256']['index.php'] === hash_file('sha256', __DIR__ . '/../index.php'), 'Source fingerprint failed');

$error = new PDOException("Table 'private_database.counters' doesn't exist");
$error->errorInfo = ['42S02',1146,$error->getMessage()];
$report = operatorErrorDiagnostics($error);
diagnosticCheck($report['table'] === 'counters' && !str_contains(json_encode($report), 'private_database'), 'Database name leaked');

$error = new PDOException("Access denied for user 'private_user'@'private_host' with password private_password");
$error->errorInfo = ['28000',1045,$error->getMessage()];
$report = operatorErrorDiagnostics($error);
diagnosticCheck($report['category'] === 'database_permission', 'Permission diagnosis failed');
diagnosticCheck(!str_contains(json_encode($report), 'private_'), 'Database credentials leaked');

$report = operatorErrorDiagnostics(new TypeError('Sensitive row value private_value'));
diagnosticCheck($report['exception'] === 'TypeError' && !str_contains(json_encode($report), 'private_value'), 'Runtime error text leaked');
$report = operatorErrorDiagnostics(new Error('Call to undefined function missingDeploymentFunction()'));
diagnosticCheck(($report['function'] ?? '') === 'missingDeploymentFunction', 'Missing-function diagnosis failed');
echo "Operator diagnostics passed: access restrictions, SQL classification, source fingerprints, and sensitive-message redaction.\n";
