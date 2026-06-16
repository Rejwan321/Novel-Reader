# Powershell Verification Script for Sales and Economic Analytics Dashboard

$sessionAdmin = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Login as Sakura Admin
Write-Output "1. Logging in as Admin..."
$loginResAdmin = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body @{ email = "sakura@sakura.com"; password = "sakura002" } -WebSession $sessionAdmin
Write-Output "Admin Login success: $($loginResAdmin.success)"

# 2. Get Admin Analytics Summary
Write-Output "`n2. Retrieving Admin Analytics Summary..."
$summaryResAdmin = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/analytics/summary" -Method Get -WebSession $sessionAdmin

# Verify Admin summary contains expected keys
if ($null -ne $summaryResAdmin.totalSales -and $null -ne $summaryResAdmin.totalRevenue -and $null -ne $summaryResAdmin.salesByEditor) {
    Write-Output "Success: Admin summary contains 'totalSales', 'totalRevenue', and admin-only 'salesByEditor'."
    Write-Output "Total Sales: $($summaryResAdmin.totalSales)"
    Write-Output "Total Revenue: $($summaryResAdmin.totalRevenue)"
    Write-Output "Active Readers: $($summaryResAdmin.activeReaders)"
    Write-Output "Number of Stories: $($summaryResAdmin.salesByStory.Count)"
} else {
    Write-Error "Error: Admin summary response is missing expected fields!"
    exit 1
}

# 3. Get Admin User Analytics
Write-Output "`n3. Retrieving User Economical Analysis..."
$usersResAdmin = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/analytics/users" -Method Get -WebSession $sessionAdmin

if ($usersResAdmin.Count -gt 0 -and $null -ne $usersResAdmin[0].totalSpent -and $null -ne $usersResAdmin[0].chaptersUnlocked) {
    Write-Output "Success: User Economical Analysis contains $($usersResAdmin.Count) records with spent and unlock statistics."
    Write-Output "First User: $($usersResAdmin[0].name), Balance: $($usersResAdmin[0].balance), Spent: $($usersResAdmin[0].totalSpent), Unlocked: $($usersResAdmin[0].chaptersUnlocked)"
} else {
    Write-Error "Error: User Economical Analysis response is missing expected fields!"
    exit 1
}

# 4. Login as Yuki Editor
Write-Output "`n4. Logging in as Editor (Yuki)..."
$sessionEditor = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginResEditor = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body @{ email = "editor@yuki.com"; password = "editor123" } -WebSession $sessionEditor
Write-Output "Editor Login success: $($loginResEditor.success)"

# 5. Get Editor Analytics Summary
Write-Output "`n5. Retrieving Editor Analytics Summary..."
$summaryResEditor = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/analytics/summary" -Method Get -WebSession $sessionEditor

# Verify Editor summary is scoped (no salesByEditor)
if ($null -eq $summaryResEditor.salesByEditor) {
    Write-Output "Success: Editor summary correctly excludes 'salesByEditor' data."
    Write-Output "Editor Scoped Stories Count: $($summaryResEditor.salesByStory.Count)"
} else {
    Write-Error "Error: Editor summary leaked admin-only 'salesByEditor' data!"
    exit 1
}

# 6. Try to access Admin-only User Analytics as Editor (expecting 403 Forbidden)
Write-Output "`n6. Attempting to access User Economical Analysis as Editor (should fail)..."
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/analytics/users" -Method Get -WebSession $sessionEditor
    Write-Error "Error: Editor was allowed to access admin-only user analytics!"
    exit 1
} catch [System.Net.WebException] {
    $statusCode = $_.Exception.Response.StatusCode
    if ($statusCode -eq "Forbidden") {
        Write-Output "Success: Access to user analytics was correctly denied (403 Forbidden)."
    } else {
        Write-Error "Error: Request failed with unexpected status code: $statusCode"
        exit 1
    }
}

Write-Output "`nAnalytics Dashboard verification PASSED!"
