$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginBody = @{ email = "sakura@sakura.com"; password = "sakura002" }
$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $loginBody -WebSession $session
$novels = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/stories/6/chapters" -Method Get -WebSession $session
$novels | ConvertTo-Json -Depth 5
