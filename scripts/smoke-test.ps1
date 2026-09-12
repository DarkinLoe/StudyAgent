param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Username = 'smoke',
    [string]$Password = 'smoke123456'
)

# StudyAgent 一键冒烟测试：验证核心链路（不含需要真实 AI Key 的对话接口）
# 用法：先启动应用，然后执行  .\scripts\smoke-test.ps1
# 认证：先用测试账号登录（不存在则自动注册），后续请求携带 Bearer token

function Invoke-AuthApi($path, $payload) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes(($payload | ConvertTo-Json))
    return Invoke-RestMethod -Uri "$BaseUrl$path" -Method Post `
        -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 30
}

$auth = $null
try {
    $auth = Invoke-AuthApi '/api/auth/login' @{ username = $Username; password = $Password }
} catch {
    $auth = Invoke-AuthApi '/api/auth/register' @{ username = $Username; password = $Password; nickname = '冒烟测试' }
}
if (-not $auth -or -not $auth.data.token) {
    Write-Host '登录/注册失败，冒烟测试终止' -ForegroundColor Red
    exit 1
}
Write-Host "已登录：$($auth.data.username)（user #$($auth.data.userId)）" -ForegroundColor Green

$headers = @{ 'Authorization' = "Bearer $($auth.data.token)" }
$script:failures = 0

function Section($title) {
    Write-Host "`n=== $title ===" -ForegroundColor Cyan
}

function Check($name, $condition, $detail = '') {
    if ($condition) {
        Write-Host "  [PASS] $name $detail" -ForegroundColor Green
    }
    else {
        Write-Host "  [FAIL] $name $detail" -ForegroundColor Red
        $script:failures++
    }
}

function GetJson($path) {
    try { return Invoke-RestMethod -Uri "$BaseUrl$path" -Headers $headers -TimeoutSec 30 }
    catch { Write-Host "  [ERR ] GET $path -> $($_.Exception.Message)" -ForegroundColor Yellow; return $null }
}

function PostJson($path, $obj) {
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 6))
        return Invoke-RestMethod -Uri "$BaseUrl$path" -Method Post -Headers $headers `
            -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 60
    }
    catch { Write-Host "  [ERR ] POST $path -> $($_.Exception.Message)" -ForegroundColor Yellow; return $null }
}

Section '健康检查'
$health = GetJson '/actuator/health'
Check 'actuator health = UP' ($null -ne $health -and $health.status -eq 'UP') "status=$($health.status)"

Section 'RAG 检索（无 Embedding 能力时应关键词降级，不报 500）'
$search = GetJson '/api/rag/search?q=%E7%9F%A9%E9%98%B5'
Check '检索接口返回成功码' ($null -ne $search -and $search.code -eq 0) "hits=$($search.data.Count)"

Section '学习计划与今日学习（走 Redis 缓存）'
$plan = (PostJson '/api/plans' @{
        title      = "冒烟计划 $(Get-Date -Format HHmmss)"
        description = 'smoke test'
        startDate  = (Get-Date).ToString('yyyy-MM-dd')
        endDate    = (Get-Date).AddDays(7).ToString('yyyy-MM-dd')
    }).data
Check '创建学习计划' ($null -ne $plan.id) "planId=$($plan.id)"

$task = (PostJson "/api/plans/$($plan.id)/tasks" @{
        subject        = 'RAG'
        content        = '学习检索链路与降级'
        plannedDate    = (Get-Date).ToString('yyyy-MM-dd')
        plannedStart   = '09:00:00'
        plannedMinutes = 60
    }).data
Check '添加计划任务' ($null -ne $task.id) "taskId=$($task.id)"

$today1 = (GetJson '/api/plans/today').data
$today2 = (GetJson '/api/plans/today').data
Check '今日学习可读且两次一致' ($null -ne $today1 -and $today1.Count -eq $today2.Count) "count=$($today1.Count)"

Section '题库 / 刷题 / 错题本'
$bank = (PostJson '/api/banks' @{ name = "冒烟题库 $(Get-Date -Format HHmmss)"; description = 'smoke' }).data
Check '创建题库' ($null -ne $bank.id) "bankId=$($bank.id)"

$question = (PostJson '/api/questions' @{
        bankId     = $bank.id
        type       = 'SINGLE_CHOICE'
        stem       = 'RAG 中 Retrieval 的作用是？'
        optionsJson = '["生成答案","检索相关资料","训练模型"]'
        answer     = 'B'
        explanation = '检索用于取回相关资料'
        difficulty = 2
        tagsJson   = '["RAG"]'
    }).data
Check '创建题目' ($null -ne $question.id) "questionId=$($question.id)"

$before = (GetJson '/api/wrongs/pending-count').data
$practice = (PostJson "/api/questions/$($question.id)/practice" @{ userAnswer = 'A' }).data
Check '答错自动判分' ($null -ne $practice -and $practice.correct -eq $false -and $practice.autoJudged -eq $true) `
    "expected=$($practice.expectedAnswer)"
$after = (GetJson '/api/wrongs/pending-count').data
Check '错题待复习数 +1' ($after -eq ($before + 1)) "before=$before after=$after"

Section '学习分析与提醒'
$stats = (GetJson '/api/analytics/learning').data
Check '学习统计可读' ($null -ne $stats.planTotal) `
    "plans=$($stats.planTotal) tasks=$($stats.taskTotal) wrongs=$($stats.wrongTotal)"
$unread = (GetJson '/api/reminders/unread-count').data
Check '未读提醒可读' ($null -ne $unread.count) "count=$($unread.count)"

if ($script:failures -gt 0) {
    Write-Host "`n$($script:failures) 项失败" -ForegroundColor Red
    exit 1
}
Write-Host "`n全部通过 ✅" -ForegroundColor Green
exit 0
