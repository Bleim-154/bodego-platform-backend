# Captures the existing PostgreSQL password locally; never connects to PostgreSQL.
# No password arguments, defaults, logging, or JWT key generation.
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
if ($args.Count -ne 0 -or [Console]::IsInputRedirected) {
    throw 'Use an interactive local PowerShell console without arguments or redirected input.'
}

$privateRoot = 'C:\Users\yhose\AppData\Local\BodeGo\private'
$privateFile = Join-Path $privateRoot 'application.properties'
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$first = $null
$second = $null
$firstPtr = [IntPtr]::Zero
$secondPtr = [IntPtr]::Zero
$temporary = $null
$backup = $null

function Assert-PrivatePath([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Private path rejected.' }
    $acl = Get-Acl -LiteralPath $Path
    if (-not $acl.AreAccessRulesProtected -or $acl.Access.Count -eq 0) { throw 'Private permissions rejected.' }
    if ($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $sid.Value) { throw 'Private owner rejected.' }
    foreach ($entry in $acl.Access) {
        if ($entry.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -ne $sid.Value -or
            $entry.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow) {
            throw 'Private permissions rejected.'
        }
    }
}

try {
    Assert-PrivatePath $privateRoot
    Assert-PrivatePath $privateFile
    $existing = [IO.File]::ReadAllText($privateFile)
    $keys = @($existing -split '\r?\n' | Where-Object { $_ -ne '' } | ForEach-Object {
        if ($_ -notmatch '^([A-Z0-9_]+)=') { throw 'Unexpected private configuration format.' }
        $Matches[1]
    })
    $allowed = @('JWT_SECRET_BASE64','JWT_ISSUER','JWT_TTL','CORS_ALLOWED_ORIGINS','DB_PASSWORD')
    if (@($keys | Where-Object { $_ -notin $allowed }).Count -ne 0 -or
        @($keys | Select-Object -Unique).Count -ne $keys.Count -or
        'JWT_SECRET_BASE64' -notin $keys) { throw 'Unexpected private configuration keys.' }
    if ('DB_PASSWORD' -in $keys) { throw 'DB_PASSWORD is already configured; no automatic overwrite is allowed.' }

    $first = Read-Host 'Existing PostgreSQL password (hidden; no database connection)' -AsSecureString
    $second = Read-Host 'Repeat the existing PostgreSQL password (hidden)' -AsSecureString
    if ($first.Length -eq 0) { throw 'Empty password rejected.' }
    $firstPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($first)
    $secondPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($second)
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($firstPtr)
    $confirmation = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secondPtr)
    if (-not [string]::Equals($password,$confirmation,[StringComparison]::Ordinal)) { throw 'Password confirmation failed.' }

    # Escape every character as a Java-properties UTF-16 escape. This preserves
    # spaces, backslashes, punctuation, non-ASCII characters and surrogate pairs.
    $escaped = [Text.StringBuilder]::new()
    foreach ($character in $password.ToCharArray()) {
        [void]$escaped.Append(('\u{0:X4}' -f [int]$character))
    }
    $newContent = $existing.TrimEnd([char[]]"`r`n") + "`r`nDB_PASSWORD=" + $escaped.ToString() + "`r`n"
    $temporary = Join-Path $privateRoot ([IO.Path]::GetRandomFileName())
    $stream = [IO.File]::Open($temporary,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try {
        $payload = [Text.Encoding]::ASCII.GetBytes($newContent)
        $stream.Write($payload,0,$payload.Length)
    } finally {
        $stream.Dispose()
        if ($payload) { [Array]::Clear($payload,0,$payload.Length) }
    }
    $fileAcl = Get-Acl -LiteralPath $privateFile
    Set-Acl -LiteralPath $temporary -AclObject $fileAcl
    Assert-PrivatePath $temporary
    do {
        $backup = Join-Path $privateRoot ([IO.Path]::GetRandomFileName())
    } while (Test-Path -LiteralPath $backup)
    # Windows requires a backup path for File.Replace; keep it private and ephemeral.
    [IO.File]::Replace($temporary,$privateFile,$backup)
    $temporary = $null
    Assert-PrivatePath $privateFile
    Assert-PrivatePath $backup
    [IO.File]::Delete($backup)
    $backup = $null
    Write-Output 'DB_PASSWORD configured privately. No database connection was made.'
} catch {
    Write-Error 'Private configuration was not confirmed. Stop and review locally; no database operation was attempted.' -ErrorAction Continue
    exit 1
} finally {
    if ($firstPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($firstPtr) }
    if ($secondPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secondPtr) }
    if ($first) { $first.Dispose() }
    if ($second) { $second.Dispose() }
    if ($temporary -and (Test-Path -LiteralPath $temporary)) { Remove-Item -LiteralPath $temporary -Force }
    if ($backup -and (Test-Path -LiteralPath $backup)) { Remove-Item -LiteralPath $backup -Force }
    $password = $null
    $confirmation = $null
    $existing = $null
    $newContent = $null
    if ($escaped) { [void]$escaped.Clear() }
}
