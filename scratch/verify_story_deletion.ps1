# Powershell Verification Script for Story Deletion using web request session

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Login as Admin
Write-Output "1. Logging in as Admin..."
$loginBody = @{ email = "sakura@sakura.com"; password = "sakura002" }
$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $loginBody -WebSession $session
Write-Output "Login status: $($loginRes.success)"

# 2. Create a temporary story (Novel)
Write-Output "`n2. Creating a temporary story series..."
$storyBody = @{
    title = "Temporary Story Series for Deletion Test"
    author = "Test Author"
    description = "This story is created to test programmatic cascade deletion."
    coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600"
    type = "NOVEL"
    genre = "Test, Deletion"
    rating = 4.5
    status = "ONGOING"
}
$storyRes = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/add" -Method Post -Body $storyBody -WebSession $session
$storyId = $storyRes.novel.id
Write-Output "Created story ID: $storyId"

# 3. Add a temporary chapter to this story
Write-Output "`n3. Adding a temporary chapter to story $storyId..."
$chapterBody = @{
    novelId = $storyId
    title = "Chapter One: The Genesis"
    chapterNumber = 1.0
    content = "This is a temporary chapter content."
    price = 0
}
$chapterRes = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/chapters/add" -Method Post -Body $chapterBody -WebSession $session
$chapterId = $chapterRes.chapter.id
Write-Output "Created chapter ID: $chapterId"

# 4. Verify chapter exists in the story's chapters list
Write-Output "`n4. Verifying chapter exists in the list..."
$chapters = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/$storyId/chapters" -Method Get -WebSession $session
$found = $chapters | Where-Object { $_.id -eq $chapterId }
if ($found) {
    Write-Output "Success: Found chapter with ID $chapterId in list for story $storyId!"
} else {
    Write-Error "Error: Created chapter not found!"
    exit 1
}

# 5. Delete the story
Write-Output "`n5. Deleting story with ID $storyId..."
$deleteRes = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/$storyId" -Method Delete -WebSession $session
Write-Output "Delete response: $($deleteRes | ConvertTo-Json -Depth 2)"

# 6. Verify story and chapters are deleted
Write-Output "`n6. Verifying story and chapters are deleted..."
$chaptersAfter = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/$storyId/chapters" -Method Get -WebSession $session
if ($null -eq $chaptersAfter -or $chaptersAfter.Count -eq 0) {
    Write-Output "Success: Story and its chapters were successfully cascade deleted!"
} else {
    Write-Error "Error: Chapters are still present after deleting the story! Count: $($chaptersAfter.Count)"
    exit 1
}
