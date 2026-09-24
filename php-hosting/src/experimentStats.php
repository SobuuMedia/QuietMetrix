<?php

/**
 * Two-proportion z-test statistics, mirrored exactly by
 * servers/ktor/.../experiments/ExperimentStats.kt -- both backends must agree on a given
 * input's p-value to the vectors in experimentStatsTest.php.
 */

const EXPERIMENT_SIGNIFICANCE_LEVEL = 0.05;

/**
 * Two-proportion z-test comparing $c2/$n2 (challenger) against $c1/$n1 (baseline). Returns
 * null when the standard error is 0 (the pooled proportion is exactly 0 or 1, or either
 * sample is empty) -- there is nothing to compare. On success returns
 * ['z' => float, 'p_value' => float, 'significant' => bool].
 */
function experimentTwoProportionZTest(int $c1, int $n1, int $c2, int $n2): ?array {
    if ($n1 <= 0 || $n2 <= 0) return null;
    $p1 = $c1 / $n1;
    $p2 = $c2 / $n2;
    $pooled = ($c1 + $c2) / ($n1 + $n2);
    $se = sqrt($pooled * (1.0 - $pooled) * (1.0 / $n1 + 1.0 / $n2));
    if ($se == 0.0) return null;
    $z = ($p2 - $p1) / $se;
    $pValue = 2.0 * (1.0 - experimentNormalCdf(abs($z)));
    return ['z' => $z, 'p_value' => $pValue, 'significant' => $pValue < EXPERIMENT_SIGNIFICANCE_LEVEL];
}

/** Standard normal CDF via erf(). */
function experimentNormalCdf(float $x): float {
    return 0.5 * (1.0 + experimentErf($x / sqrt(2.0)));
}

/** Abramowitz & Stegun formula 7.1.26 approximation of the error function (max error ~1.5e-7). */
function experimentErf(float $x): float {
    $sign = $x < 0 ? -1.0 : 1.0;
    $ax = abs($x);
    $t = 1.0 / (1.0 + 0.3275911 * $ax);
    $y = 1.0 - (((((1.061405429 * $t - 1.453152027) * $t) + 1.421413741) * $t - 0.284496736) * $t + 0.254829592) * $t * exp(-$ax * $ax);
    return $sign * $y;
}
