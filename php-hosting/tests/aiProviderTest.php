<?php
require_once __DIR__.'/../src/ai.php';
/** Fixture assertions fail loudly without live provider calls. */
function expectAi(bool $condition): void { if (!$condition) throw new RuntimeException('Provider fixture assertion failed'); }
$fixtures=[
    'openai'=>'{"choices":[{"message":{"role":"assistant","content":null,"reasoning_content":"opaque","tool_calls":[{"id":"c1","type":"function","function":{"name":"sessions","arguments":"{}"}}]}}],"usage":{"prompt_tokens":100,"completion_tokens":20,"prompt_tokens_details":{"cached_tokens":30}}}',
    'gemini'=>'{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"id":"c1","name":"sessions","args":{}},"thoughtSignature":"signed"}]}}],"usageMetadata":{"promptTokenCount":100,"candidatesTokenCount":10,"thoughtsTokenCount":10,"cachedContentTokenCount":30}}',
    'anthropic'=>'{"content":[{"type":"thinking","thinking":"opaque","signature":"signed"},{"type":"tool_use","id":"c1","name":"sessions","input":{}}],"usage":{"input_tokens":70,"output_tokens":20,"cache_read_input_tokens":30}}'
];
foreach ($fixtures as $kind=>$fixture) {
    $config=aiConfig(['QM_AI_PROVIDER'=>$kind,'QM_AI_MODEL'=>'fixture','QM_AI_API_KEY'=>'secret']);
    $messages=aiMessages($config,'system',[],'question');
    $reply=aiProvider($config,'system',$messages,true,10,static fn()=>[200,$fixture]);
    expectAi($reply['usage']===['input'=>100,'output'=>20,'cache'=>30]);
    expectAi($reply['calls'][0]['name']==='sessions');
    aiContinue($config,$messages,$reply,[['total'=>20]]);
    expectAi(str_contains(json_encode($messages),$kind==='openai' ? 'opaque' : 'signed'));
    expectAi(str_contains(json_encode($messages),'c1'));
    foreach ([400,401,429,500] as $status) {
        try { aiProvider($config,'system',$messages,true,10,static fn()=>[$status,'tools unsupported']); throw new LogicException('Expected failure'); }
        catch (RuntimeException $e) { expectAi($e->getMessage()===($status===400 ? 'ai_tools_unsupported' : 'ai_provider_failed')); }
    }
    try { aiDecode($kind,[]); throw new LogicException('Expected malformed failure'); } catch (RuntimeException $e) {}
    try { aiProvider($config,'s',$messages,true,1,static function() { throw new RuntimeException('timeout'); }); throw new LogicException('Expected timeout'); } catch (RuntimeException $e) { expectAi($e->getMessage()==='timeout'); }
}
$flat=str_replace('"prompt_tokens_details":{"cached_tokens":30}','"prompt_cache_hit_tokens":30',$fixtures['openai']);
expectAi(aiDecode('openai',json_decode($flat,true))['usage']===['input'=>100,'output'=>20,'cache'=>30]);
echo "AI provider fixtures passed\n";
