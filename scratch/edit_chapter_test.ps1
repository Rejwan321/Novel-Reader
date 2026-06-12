# Powershell script to test editing Chapter 12 via Admin REST API

$cookieFile = "admin_cookies.txt"
if (Test-Path $cookieFile) { Remove-Item $cookieFile }

Write-Host "Logging in as Sakura Admin..."
$adminLoginRes = curl.exe -s -c $cookieFile -X POST -H "Content-Type: application/x-www-form-urlencoded" -d "email=sakura@sakura.com&password=sakura002" "http://localhost:8080/api/auth/login"
Write-Host "Admin Login: $adminLoginRes"

Write-Host "Editing Chapter 12..."
$editRes = curl.exe -s -b $cookieFile -X POST -H "Content-Type: application/x-www-form-urlencoded" `
    -d "title=paid&chapterNumber=2.0&content=it's a paid content.&price=10" `
    "http://localhost:8080/api/admin/chapters/edit/12"
Write-Host "Edit Result: $editRes"

if (Test-Path $cookieFile) { Remove-Item $cookieFile }
