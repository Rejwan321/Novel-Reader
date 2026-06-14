# Powershell Verification Script for Snow Flakes Economy using curl.exe

Write-Host "--- 1. Registering a new standard reader account ---" -ForegroundColor Cyan
$regRes = curl.exe -s -X POST -H "Content-Type: application/x-www-form-urlencoded" -d "name=Test Reader&email=testreader@yuki.com&password=password123" "http://localhost:8080/api/auth/signup"
Write-Host "Registration Succeeded: $regRes" -ForegroundColor Green

Write-Host "`n--- 2. Logging in as standard reader ---" -ForegroundColor Cyan
# Save cookies in a file to maintain session
$cookieFile = "cookies.txt"
if (Test-Path $cookieFile) { Remove-Item $cookieFile }

$loginRes = curl.exe -s -c $cookieFile -X POST -H "Content-Type: application/x-www-form-urlencoded" -d "email=testreader@yuki.com&password=password123" "http://localhost:8080/api/auth/login"
Write-Host "Login Succeeded: $loginRes" -ForegroundColor Green

Write-Host "`n--- 3. Attempting to directly read Chapter 2.0 of Solo Leveling: Ragnarok (Paid Chapter, Price: 20) ---" -ForegroundColor Cyan
# We follow redirects but check if it redirected us to detail page with error
$readRes = curl.exe -s -b $cookieFile -L -w "%{url_effective}" -o temp_read.html "http://localhost:8080/novel/1/read/2.0"
Write-Host "Effective URL: $readRes" -ForegroundColor Green
if ($readRes -like "*error=purchase_required*") {
    Write-Host "Security Check Passed! Direct access redirected correctly to detail page." -ForegroundColor Green
} else {
    Write-Host "Security Check Failed: direct access was not prevented!" -ForegroundColor Red
}
if (Test-Path temp_read.html) { Remove-Item temp_read.html }

Write-Host "`n--- 4. Unlocking Chapter 2 (ID: 2) via purchase API ---" -ForegroundColor Cyan
$purchaseRes = curl.exe -s -b $cookieFile -c $cookieFile -X POST "http://localhost:8080/api/chapters/2/purchase"
Write-Host "Purchase Succeeded: $purchaseRes" -ForegroundColor Green

Write-Host "`n--- 5. Attempting to read Chapter 2.0 again after purchase ---" -ForegroundColor Cyan
$readRes2 = curl.exe -s -b $cookieFile -L -w "%{url_effective}" -o temp_read.html "http://localhost:8080/novel/1/read/2.0"
Write-Host "Effective URL after purchase: $readRes2" -ForegroundColor Green
if ($readRes2 -like "*/read/2.0") {
    Write-Host "Access Granted! Successfully landed on reader page." -ForegroundColor Green
} else {
    Write-Host "Access Denied: failed to view chapter even after purchase." -ForegroundColor Red
}
if (Test-Path temp_read.html) { Remove-Item temp_read.html }

Write-Host "`n--- 6. Admin Panel Testing: Login as Sakura Admin ---" -ForegroundColor Cyan
$adminCookieFile = "admin_cookies.txt"
if (Test-Path $adminCookieFile) { Remove-Item $adminCookieFile }
$adminLoginRes = curl.exe -s -c $adminCookieFile -X POST -H "Content-Type: application/x-www-form-urlencoded" -d "email=sakura@sakura.com&password=sakura002" "http://localhost:8080/api/auth/login"
Write-Host "Admin Login Succeeded: $adminLoginRes" -ForegroundColor Green

Write-Host "`n--- 7. Updating Reader's balance via Admin balance API ---" -ForegroundColor Cyan
$readerId = 3 # newly registered reader is 3
$balanceRes = curl.exe -s -b $adminCookieFile -X POST -H "Content-Type: application/x-www-form-urlencoded" -d "balance=150" "http://localhost:8080/api/admin/users/$readerId/balance"
Write-Host "Admin Balance Update Succeeded: $balanceRes" -ForegroundColor Green

Write-Host "`n--- 8. Verify reader's balance is updated ---" -ForegroundColor Cyan
$loginRes2 = curl.exe -s -b $cookieFile -X POST -H "Content-Type: application/x-www-form-urlencoded" -d "email=testreader@yuki.com&password=password123" "http://localhost:8080/api/auth/login"
Write-Host "Reader's New Balance: $loginRes2" -ForegroundColor Green

# Cleanup
if (Test-Path $cookieFile) { Remove-Item $cookieFile }
if (Test-Path $adminCookieFile) { Remove-Item $adminCookieFile }
