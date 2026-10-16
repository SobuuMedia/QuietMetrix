<?php

/** Opt-in diagnostics after authorization for analytics GET or project DELETE. */
function enableOperatorErrorDiagnostics(array $session, string $method = 'GET'): void {
    if (in_array($method, ['GET', 'DELETE'], true)
        && ($_SERVER['REQUEST_METHOD'] ?? '') === $method
        && ($_SERVER['HTTP_X_QM_DIAGNOSTICS'] ?? '') === '1'
        && !isset($session['scopes'])
        && ($session['role'] ?? '') === 'admin'
        && !empty($session['platformOperator'])) {
        $GLOBALS['qm_operator_error_diagnostics'] = true;
    }
}

/** Never return raw exception text: SQL messages may contain credentials or row values. */
function operatorErrorDiagnostics(Throwable $error): array {
    $root = dirname(__DIR__) . DIRECTORY_SEPARATOR;
    $file = $error->getFile();
    $result = [
        'exception' => get_class($error),
        'file' => str_starts_with($file, $root) ? substr($file, strlen($root)) : basename($file),
        'line' => $error->getLine(),
        'php_version' => PHP_VERSION,
    ];
    if ($error instanceof PDOException) {
        $state = (string)($error->errorInfo[0] ?? $error->getCode());
        if (preg_match('/^[A-Z0-9]{5}$/D', $state)) $result['sqlstate'] = $state;
        $driverCode = (int)($error->errorInfo[1] ?? 0);
        $result['driver_code'] = $driverCode;
        $result['category'] = match ($driverCode) {
            1044, 1045, 1142, 1143 => 'database_permission',
            1054 => 'missing_column',
            1064 => 'sql_syntax',
            1146 => 'missing_table',
            2002, 2003, 2006, 2013 => 'database_connection',
            default => 'database_error',
        };
        $message = $error->getMessage();
        if ($driverCode === 1054 && preg_match("/Unknown column '([A-Za-z0-9_.$]+)'/", $message, $match)) {
            $result['column'] = $match[1];
        }
        if ($driverCode === 1146 && preg_match("/Table '([A-Za-z0-9_.$-]+)' doesn't exist/", $message, $match)) {
            $parts = explode('.', $match[1]);
            $result['table'] = end($parts);
        }
    } elseif (preg_match('/Call to undefined function ([A-Za-z0-9_\\\\]+)\(\)/', $error->getMessage(), $match)) {
        $result['function'] = $match[1];
    }
    // Compare these on-disk fingerprints with the local checkout to detect mixed uploads.
    $result['source_sha256'] = [];
    foreach (['index.php', 'src/auth.php', 'src/errorDiagnostics.php', 'src/bootstrap.php',
        'src/analyticsWindow.php', 'src/counters.php', 'src/routes/dashboard.php', 'src/routes/ai.php', 'src/routes/projects.php'] as $relative) {
        if (is_file($root . $relative) && is_readable($root . $relative)) {
            $result['source_sha256'][$relative] = hash_file('sha256', $root . $relative);
        }
    }
    return $result;
}
