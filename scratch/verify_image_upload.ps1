# Powershell Verification Script for Content Image Uploads

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Login as Admin
Write-Output "1. Logging in as Admin..."
$loginBody = @{ email = "sakura@sakura.com"; password = "sakura002" }
$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $loginBody -WebSession $session
Write-Output "Login status: $($loginRes.success)"

# 2. Prepare a dummy image file to upload
Write-Output "`n2. Preparing dummy image file..."
$dummyFilePath = Join-Path $PSScriptRoot "dummy-upload.jpg"
# We write dummy bytes that look like a JPEG structure (minimum header or just dummy content)
[byte[]]$jpegBytes = 0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x60, 0x00, 0x60, 0x00, 0x00, 0xFF, 0xD9
[System.IO.File]::WriteAllBytes($dummyFilePath, $jpegBytes)
Write-Output "Created dummy image file at: $dummyFilePath"

# 3. Perform file upload to the new /api/admin/upload-image endpoint
Write-Output "`n3. Uploading image to /api/admin/upload-image..."

# We construct the multipart form-data upload manually using Invoke-WebRequest or curl.exe to avoid複雜의 PowerShell multipart build
# Since curl.exe is available and standard, we can use it to easily upload files using -F
$cookieFile = "admin_cookies_tmp.txt"
if (Test-Path $cookieFile) { Remove-Item $cookieFile }

# First, log in with curl to get session cookie
$loginResCurl = curl.exe -s -c $cookieFile -X POST -H "Content-Type: application/x-www-form-urlencoded" -d "email=sakura@sakura.com&password=sakura002" "http://localhost:8080/api/auth/login"

# Second, perform image upload
$uploadRes = curl.exe -s -b $cookieFile -F "file=@$dummyFilePath" "http://localhost:8080/api/admin/upload-image"
Write-Output "Upload response: $uploadRes"

# Clean up cookie file
if (Test-Path $cookieFile) { Remove-Item $cookieFile }

# Parse response
$responseObj = $uploadRes | ConvertFrom-Json
$uploadedUrl = $responseObj.url

if ($responseObj.success -eq $true -and $uploadedUrl -like "/uploads/*") {
    Write-Output "Success: Upload returned successful URL: $uploadedUrl"
} else {
    Write-Error "Error: Upload failed or returned invalid response!"
    if (Test-Path $dummyFilePath) { Remove-Item $dummyFilePath }
    exit 1
}

# 4. Verify file exists physically on disk in static/uploads
Write-Output "`n4. Verifying file exists on disk..."
$fileName = Split-Path $uploadedUrl -Leaf
$userDir = Get-Location
$expectedPaths = @(
    Join-Path $userDir "src/main/resources/static/uploads/$fileName"
    Join-Path $userDir "target/classes/static/uploads/$fileName"
)

$foundOnDisk = $false
foreach ($path in $expectedPaths) {
    if (Test-Path $path) {
        Write-Output "Success: Found uploaded file on disk at: $path"
        $foundOnDisk = $true
    }
}

# Cleanup dummy file
if (Test-Path $dummyFilePath) { Remove-Item $dummyFilePath }

if ($foundOnDisk) {
    Write-Output "`nImage upload and storage verification PASSED!"
} else {
    Write-Error "Error: Uploaded file not found on disk in target folders!"
    exit 1
}
