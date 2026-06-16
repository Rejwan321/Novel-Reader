$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Login as Admin
Write-Output "1. Logging in as Admin..."
$loginBody = @{ email = "sakura@sakura.com"; password = "sakura002" }
$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $loginBody -WebSession $session
Write-Output "Login status: $($loginRes.success)"
Write-Output "Admin user balance: $($loginRes.user.balance)"

# 2. Test Edit Chapter API
Write-Output "`n2. Editing chapter 12..."
$editBody = @{
    title = "paid edited content"
    chapterNumber = 2.0
    content = "it's a paid content. edited!"
    price = 15
}
$editRes = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/chapters/edit/12" -Method Post -Body $editBody -WebSession $session
Write-Output "Edit chapter response: $($editRes | ConvertTo-Json -Depth 2)"

# 3. Create a temporary chapter
Write-Output "`n3. Creating a temporary chapter..."
$addBody = @{
    novelId = 6
    title = "Temp Chapter"
    chapterNumber = 3.0
    content = "temporary content"
    price = 0
}
$addRes = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/chapters/add" -Method Post -Body $addBody -WebSession $session
$newChapId = $addRes.chapter.id
Write-Output "Created chapter ID: $newChapId"

# 4. Check that new chapter exists
Write-Output "`n4. Fetching chapters to verify creation..."
$chapters = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/6/chapters" -Method Get -WebSession $session
$found = $chapters | Where-Object { $_.id -eq $newChapId }
if ($found) {
    Write-Output "Success: Found temporary chapter with ID $newChapId in list!"
} else {
    Write-Output "Error: Created chapter not found in list!"
}

# 5. Delete the temporary chapter
Write-Output "`n5. Deleting temporary chapter with ID $newChapId..."
$deleteRes = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/chapters/$newChapId" -Method Delete -WebSession $session
Write-Output "Delete response: $($deleteRes | ConvertTo-Json -Depth 2)"

# 6. Verify chapter is deleted
Write-Output "`n6. Fetching chapters to verify deletion..."
$chaptersAfter = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/6/chapters" -Method Get -WebSession $session
$foundAfter = $chaptersAfter | Where-Object { $_.id -eq $newChapId }
if (-not $foundAfter) {
    Write-Output "Success: Temporary chapter was successfully deleted!"
} else {
    Write-Output "Error: Chapter with ID $newChapId is still present after deletion!"
}
