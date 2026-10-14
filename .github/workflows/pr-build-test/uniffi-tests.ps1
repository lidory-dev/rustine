param (
    [switch]$GenerateImmutableRecords,
    [switch]$OmitChecksums,
    [switch]$EnableJnaInterfaceMapping
);

$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

$testGroups = @($null, "extTypes", "futures");

# The groups run one after another without stopping the Gradle daemon or cleaning in between, so
# they share the Gradle daemon, the Rust build outputs and the installed bindgen.
try {
    $additionalArguments = @();
    foreach ($testGroup in $testGroups) {
        $basicTests = $null -eq $testGroup;
        $extTypes = "extTypes" -eq $testGroup;
        $futures = "futures" -eq $testGroup;
        if ($futures) { $additionalArguments += "--max-workers=1"; }
        # `check` doesn't compile the metadata of the common source sets, the only compilations that
        # check the generated common code against the common API of its dependencies (the platform
        # compilations see the platform API). ext-types and ext-types-proc-macro are left out: their
        # metadata compilations need the metadata of every source set of :examples:custom-types,
        # whose cinterops build the Rust library for native targets the tests don't run.
        $metadataTasks = if ($basicTests) {
            @("compileCommonMainKotlinMetadata", "compileWebMainKotlinMetadata")
        } elseif ($extTypes) {
            @(
                ":examples:custom-types:compileCommonMainKotlinMetadata",
                ":tests:uniffi:ext-types:custom-types:compileCommonMainKotlinMetadata",
                ":tests:uniffi:ext-types:uniffi-one:compileCommonMainKotlinMetadata",
                ":tests:uniffi:ext-types:sub-lib:compileCommonMainKotlinMetadata",
                ":tests:uniffi:ext-types:http-headermap:compileCommonMainKotlinMetadata"
            )
        } else {
            @("compileCommonMainKotlinMetadata")
        };
        try {
            # `check` runs the tests without assembling the release artifacts the tests don't use.
            # The release cargo checks are skipped as well, as the tests only use debug builds.
            ./gradlew check `
                $metadataTasks `
                $additionalArguments `
                "-Prustine.projects.skipReleaseCargoChecks=true" `
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
        }
    }
} finally {
    ./gradlew --stop;
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}
