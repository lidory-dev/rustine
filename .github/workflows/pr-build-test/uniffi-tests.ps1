param (
    [switch]$GenerateImmutableRecords,
    [switch]$OmitChecksums,
    [switch]$EnableJnaInterfaceMapping
);

$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

$testGroups = @($null, "extTypes", "futures");

try {
    $additionalArguments = @();
    foreach ($testGroup in $testGroups) {
        $basicTests = $null -eq $testGroup;
        $extTypes = "extTypes" -eq $testGroup;
        $futures = "futures" -eq $testGroup;
        if ($futures) { $additionalArguments += "--max-workers=1"; }
        try {
            ./gradlew build `
                $additionalArguments `
                "-Prustine.projects.gradleTests=false" `
                "-Prustine.projects.examples.app=false" `
                "-Prustine.projects.examples.audioCppApp=false" `
                "-Prustine.projects.examples.customTypes=false" `
                "-Prustine.projects.examples.tokioBlake3App=false" `
                "-Prustine.projects.examples.tokioBoringApp=false" `
                "-Prustine.projects.uniffiTests=$basicTests" `
                "-Prustine.projects.uniffiTests.extTypes=$extTypes" `
                "-Prustine.projects.uniffiTests.futures=$futures" `
                "-Prustine.projects.uniffiTests.generateImmutableRecords=$GenerateImmutableRecords" `
                "-Prustine.projects.uniffiTests.omitChecksums=$OmitChecksums" `
                "-Prustine.projects.uniffiTests.enableJnaInterfaceMapping=$EnableJnaInterfaceMapping";
        } finally {
            # Allow overwriting the test result to use the last one.
            # If the test fails, this will copy the result of the failing tests.
            ./.github/workflows/pr-build-test/copy-test-result.ps1;
            ./gradlew --stop;
            ./gradlew clean `
                "-Prustine.projects.gradleTests=false" `
                "-Prustine.projects.examples.app=false" `
                "-Prustine.projects.examples.audioCppApp=false" `
                "-Prustine.projects.examples.customTypes=false" `
                "-Prustine.projects.examples.tokioBlake3App=false" `
                "-Prustine.projects.examples.tokioBoringApp=false" `
                "-Prustine.projects.uniffiTests=$basicTests" `
                "-Prustine.projects.uniffiTests.extTypes=$extTypes" `
                "-Prustine.projects.uniffiTests.futures=$futures";
        }
    }
} finally {
    ./gradlew --stop;
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}