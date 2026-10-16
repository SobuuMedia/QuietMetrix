<?php
require_once __DIR__ . '/../src/routes/ai.php';
function cohortForAi(int $size, ?float $rate, string $state='reported'): array {
    return ['size'=>['value'=>$size,'state'=>'reported'], 'days'=>['1'=>['value'=>$rate===null?null:(int)($size*$rate),'rate'=>$rate,'state'=>$state]]];
}
$summary=aiVisibleRetentionSummary([cohortForAi(100,.8),cohortForAi(900,.2),cohortForAi(1000,null,'suppressed')]);
if (abs($summary['day_1']['rate']-.26)>1e-10 || $summary['day_1']['contributors']!==1000 || $summary['day_7']['state']!=='insufficient_data') {
    fwrite(STDERR,"FAIL: AI must use dashboard visible cohort weights\n"); exit(1);
}
echo "aiReportAdapterTest passed\n";
