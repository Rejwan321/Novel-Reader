# Powershell Verification Script for Reader Ratings Feature (Isolate and Robust)

# Create sessions
$sessionAdmin = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$sessionUser2 = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Login as Admin
Write-Output "1. Logging in as Admin..."
$loginRes1 = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body @{ email = "sakura@sakura.com"; password = "sakura002" } -WebSession $sessionAdmin
Write-Output "Admin login success: $($loginRes1.success)"

# 2. Create a temporary test story series
Write-Output "`n2. Creating temporary test story..."
$addStoryBody = @{
    title = "Temporary Test Story for Ratings"
    author = "Test Author"
    description = "Test Description"
    coverUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?w=800"
    type = "NOVEL"
    genre = "Action"
    rating = 0.0
    status = "ONGOING"
}
$addStoryRes = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/add" -Method Post -Body $addStoryBody -WebSession $sessionAdmin
$novelId = $addStoryRes.novel.id
Write-Output "Created story with ID: $novelId"

# 3. Submit Rating of 5 stars for the new story as Admin
Write-Output "`n3. Admin submitting 5 stars for new story..."
$rateRes1 = Invoke-RestMethod -Uri "http://localhost:8080/api/novels/$novelId/rate" -Method Post -Body @{ ratingValue = 5 } -WebSession $sessionAdmin
Write-Output "Response success: $($rateRes1.success)"
Write-Output "Response message: $($rateRes1.message)"
Write-Output "New Rating: $($rateRes1.newRating)"

if ($rateRes1.success -ne $true -or $rateRes1.newRating -ne 5.0) {
    Write-Error "Error: Initial rating submission did not equal 5.0!"
    # Clean up before exit
    Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/$novelId" -Method Delete -WebSession $sessionAdmin
    exit 1
}

# 4. Admin updates rating to 1 star
Write-Output "`n4. Admin updating rating to 1 star..."
$rateRes1Update = Invoke-RestMethod -Uri "http://localhost:8080/api/novels/$novelId/rate" -Method Post -Body @{ ratingValue = 1 } -WebSession $sessionAdmin
Write-Output "Response success: $($rateRes1Update.success)"
Write-Output "New Rating: $($rateRes1Update.newRating)"

if ($rateRes1Update.newRating -ne 1.0) {
    Write-Error "Error: Rating update did not equal 1.0!"
    # Clean up before exit
    Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/$novelId" -Method Delete -WebSession $sessionAdmin
    exit 1
}

# 5. Login as User 2 (editor@yuki.com)
Write-Output "`n5. Logging in as User 2..."
$loginRes2 = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body @{ email = "editor@yuki.com"; password = "editor123" } -WebSession $sessionUser2
Write-Output "User 2 login success: $($loginRes2.success)"

# 6. User 2 submits rating of 5 stars
Write-Output "`n6. User 2 submitting 5 stars..."
$rateRes2 = Invoke-RestMethod -Uri "http://localhost:8080/api/novels/$novelId/rate" -Method Post -Body @{ ratingValue = 5 } -WebSession $sessionUser2
Write-Output "Response success: $($rateRes2.success)"
Write-Output "New Rating: $($rateRes2.newRating)"

# Average of 1 and 5 should be 3.0
if ($rateRes2.newRating -ne 3.0) {
    Write-Error "Error: Average did not compute to 3.0! Got $($rateRes2.newRating)"
    # Clean up before exit
    Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/$novelId" -Method Delete -WebSession $sessionAdmin
    exit 1
}

# 7. Clean up by deleting the temporary story
Write-Output "`n7. Cleaning up temporary test story..."
$deleteRes = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/$novelId" -Method Delete -WebSession $sessionAdmin
Write-Output "Delete status: $($deleteRes.success)"

Write-Output "`nReader Ratings verification PASSED!"
