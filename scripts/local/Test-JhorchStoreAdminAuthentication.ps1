# Interactive local check only. No credential arguments, persistence or JWT output.
param()

$ErrorActionPreference = 'Stop'

function Invoke-BodegoAuthProbe {
    param(
        [System.Net.Http.HttpClient]$Client,
        [string]$Email,
        [string]$Password
    )
    $result = [pscustomobject][ordered]@{
        LoginHttpStatus = $null
        MeHttpStatus = $null
        AuthenticationSuccessful = $false
        RoleMatchesAdmin = $false
        BusinessIdMatches1 = $false
    }
    $loginRequest = $null
    $loginResponse = $null
    $meRequest = $null
    $meResponse = $null
    $json = $null
    $loginBody = $null
    $login = $null
    $token = $null
    $meBody = $null
    $identity = $null
    try {
        if ([string]::IsNullOrWhiteSpace($Email) -or $Email.Length -gt 254 -or
            [string]::IsNullOrWhiteSpace($Password) -or $Password.Length -gt 72 -or
            [Text.Encoding]::UTF8.GetByteCount($Password) -gt 72) { return $result }

        # Hardcoded IPv4 loopback only. No URI/base-address parameters or redirects.
        $loginRequest = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Post, 'http://127.0.0.1:8091/api/v1/auth/login')
        $json = @{ email = $Email; password = $Password } | ConvertTo-Json -Compress
        $loginRequest.Content = [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
        $loginResponse = $Client.SendAsync($loginRequest).GetAwaiter().GetResult()
        $result.LoginHttpStatus = [int]$loginResponse.StatusCode
        if ($result.LoginHttpStatus -ne 200) { return $result }

        $loginBody = $loginResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $login = $loginBody | ConvertFrom-Json
        $tokenProperty = $login.PSObject.Properties['accessToken']
        $typeProperty = $login.PSObject.Properties['tokenType']
        if ($null -eq $tokenProperty -or $tokenProperty.Value -isnot [string] -or
            $null -eq $typeProperty -or $typeProperty.Value -cne 'Bearer') { return $result }
        $token = $tokenProperty.Value
        if ($token.Length -gt 8192 -or $token -cnotmatch '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$') { return $result }

        # Trust the authenticated /me response, not locally decoded JWT claims.
        $meRequest = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Get, 'http://127.0.0.1:8091/api/v1/auth/me')
        $meRequest.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
        $meResponse = $Client.SendAsync($meRequest).GetAwaiter().GetResult()
        $result.MeHttpStatus = [int]$meResponse.StatusCode
        if ($result.MeHttpStatus -ne 200) { return $result }

        $meBody = $meResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $identity = $meBody | ConvertFrom-Json
        $roleProperty = $identity.PSObject.Properties['role']
        $businessProperty = $identity.PSObject.Properties['businessId']
        $userProperty = $identity.PSObject.Properties['userId']
        if ($null -eq $roleProperty -or $roleProperty.Value -isnot [string] -or
            $null -eq $businessProperty -or
            -not ($businessProperty.Value -is [int] -or $businessProperty.Value -is [long]) -or
            $null -eq $userProperty -or
            -not ($userProperty.Value -is [int] -or $userProperty.Value -is [long]) -or
            $userProperty.Value -le 0 -or $businessProperty.Value -le 0) { return $result }

        $result.AuthenticationSuccessful = $true
        $result.RoleMatchesAdmin = $roleProperty.Value -ceq 'ADMIN'
        $result.BusinessIdMatches1 = $businessProperty.Value -eq 1
    } catch {
        # Never emit exceptions, response bodies, headers, credentials or JWTs.
        $result.AuthenticationSuccessful = $false
        $result.RoleMatchesAdmin = $false
        $result.BusinessIdMatches1 = $false
    } finally {
        foreach ($resource in @($meResponse, $meRequest, $loginResponse, $loginRequest)) {
            if ($null -ne $resource) { try { $resource.Dispose() } catch {} }
        }
        $json = $null
        $loginBody = $null
        $login = $null
        $token = $null
        $meBody = $null
        $identity = $null
        $Email = $null
        $Password = $null
    }
    return $result
}

# Do not prompt or send any request from an unattended/redirected invocation.
if ($args.Count -ne 0 -or [Console]::IsInputRedirected -or [Console]::IsOutputRedirected) {
    [pscustomobject][ordered]@{
        LoginHttpStatus = $null
        MeHttpStatus = $null
        AuthenticationSuccessful = $false
        RoleMatchesAdmin = $false
        BusinessIdMatches1 = $false
    }
    return
}

$secureEmail = $null
$securePassword = $null
$emailPointer = [IntPtr]::Zero
$passwordPointer = [IntPtr]::Zero
$handler = $null
$client = $null
$emailValue = $null
$passwordValue = $null
$outcome = [pscustomobject][ordered]@{
    LoginHttpStatus = $null
    MeHttpStatus = $null
    AuthenticationSuccessful = $false
    RoleMatchesAdmin = $false
    BusinessIdMatches1 = $false
}
try {
    Add-Type -AssemblyName System.Net.Http
    # Email is hidden too, to keep personal data out of console transcripts.
    $secureEmail = Read-Host 'ADMIN email (hidden)' -AsSecureString
    $securePassword = Read-Host 'ADMIN password (hidden; not the PostgreSQL password)' -AsSecureString
    $emailPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureEmail)
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $emailValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($emailPointer)
    $passwordValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseProxy = $false
    $handler.UseCookies = $false
    $handler.UseDefaultCredentials = $false
    $handler.PreAuthenticate = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(15)
    $client.MaxResponseContentBufferSize = 65536
    $outcome = Invoke-BodegoAuthProbe -Client $client -Email $emailValue -Password $passwordValue
} catch {
    # The only output is the safe summary below, never $_ or an HTTP response.
} finally {
    if ($null -ne $client) { try { $client.Dispose() } catch {} }
    if ($null -ne $handler) { try { $handler.Dispose() } catch {} }
    if ($emailPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($emailPointer) }
    if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    if ($null -ne $secureEmail) { $secureEmail.Dispose() }
    if ($null -ne $securePassword) { $securePassword.Dispose() }
    $emailValue = $null
    $passwordValue = $null
}
$outcome
