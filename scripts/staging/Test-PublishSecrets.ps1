param([Parameter(Mandatory=$true)][string[]]$Roots)
$ErrorActionPreference='Stop'
# Read-only heuristic preflight. Never prints a matching value; not a full secret-scanner certification.
$rules=@{
    PRIVATE_KEY='-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----'
    ACCESS_TOKEN='(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16})'
    JWT='eyJ[A-Za-z0-9_-]{12,}\.eyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{16,}'
    JSON_PASSWORD='(?i)"(?:password|passwordHash|secret|token)"\s*:\s*"[^"\r\n$<{][^"\r\n]*"'
    URL_CREDENTIALS='(?:jdbc:)?postgres(?:ql)?://[^\s/:"''<>]+:[^\s@"''<>]+@'
    CONFIG_SECRET='(?im)^\s*(?:spring\.datasource\.password|DB_PASSWORD|JWT_SECRET_BASE64)\s*[:=]\s*["'']?(?!\$|<|\s*$)[A-Za-z0-9+/!][^\r\n]*'
}
$findings=0
foreach($rootInput in $Roots) {
    $root=(Resolve-Path -LiteralPath $rootInput).Path
    if(Test-Path -LiteralPath (Join-Path $root '.git')) {
        $listed=(& git -C $root ls-files --cached --others --exclude-standard -z) -join "`n"
        if($LASTEXITCODE -ne 0) {throw 'PUBLISH_FILE_LIST_FAILED'}
        $files=@($listed -split [char]0 | Where-Object {$_} | ForEach-Object {Join-Path $root $_})
        # Inspect diff history without printing content or commit messages.
        $history=(& git -C $root log --all --format= --no-ext-diff --unified=0 -- '*.properties' '*.json' '*.yml' '*.yaml' '*.ps1' '*.ts') -join "`n"
        if($LASTEXITCODE -ne 0) {throw 'PUBLISH_HISTORY_SCAN_FAILED'}
        foreach($rule in $rules.Keys) {
            if([regex]::IsMatch(($history -replace '(?m)^\+',''),$rules[$rule])) {
                Write-Output "REVIEW_HISTORY $root RULE=$rule (value suppressed)"; $findings++
            }
        }
        $history=$null
    } else {
        $listed=(& rg --files --hidden --no-require-git -0 -g '!.git/**' $root) -join "`n"
        if($LASTEXITCODE -gt 1) {throw 'PUBLISH_FILE_LIST_FAILED'}
        $files=@($listed -split [char]0 | Where-Object {$_})
    }
    foreach($file in $files) {
        if(-not (Test-Path -LiteralPath $file -PathType Leaf)) {continue}
        $relative=$file.Substring($root.Length).TrimStart('\','/').Replace('\','/')
        if($relative -match '(^|/)(\.idea|\.vscode|\.codex|\.agents)(/|$)|(^|/)(\.mcp\.json|\.npmrc|\.pgpass|pgpass\.conf|\.env(?:\..*)?)$|\.(dump|backup|pem|key|log)$') {
            Write-Output "REVIEW_FILE $relative (private/artifact candidate)"; $findings++
        }
        if((Get-Item -LiteralPath $file).Length -gt 2MB -or $relative -notmatch '\.(java|ts|js|mjs|json|properties|ya?ml|ps1|md|xml|sql|txt)$') {continue}
        $content=[IO.File]::ReadAllText($file)
        foreach($rule in $rules.Keys) {
            foreach($match in [regex]::Matches($content,$rules[$rule])) {
                $line=1+([regex]::Matches($content.Substring(0,$match.Index),"`n")).Count
                Write-Output "REVIEW $relative`:$line RULE=$rule (value suppressed)"; $findings++
            }
        }
    }
    Write-Output "CANDIDATES_CHECKED $root FILES=$($files.Count)"
}
if($findings -gt 0) {Write-Output "REVIEW_REQUIRED COUNT=$findings; do not publish until reviewed";exit 1}
Write-Output 'HEURISTIC_CHECK_PASSED; manually review staged files and run a redacted history scanner before push'
