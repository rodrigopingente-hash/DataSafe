$tests = @("AppTest", "DPTest", "GDPRComplianceTest", "GDPRErasureTest", "GDPRExportTest", "KAnonymityToolTest", "PatientContributionTest","DPIntegrationRepeatedPatientTest", "PolicyTest")

foreach ($t in $tests) {
    Write-Host "`n=================================================" -ForegroundColor Cyan
    Write-Host ">>> A executar o teste: $t" -ForegroundColor Yellow
    Write-Host "=================================================" -ForegroundColor Cyan
    
    mvn test "-Dtest=$t"
    
    Read-Host "Pressione [Enter] para continuar para o próximo teste..."
}