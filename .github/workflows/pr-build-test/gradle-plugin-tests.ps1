$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

try {
    $projectNames = @(
        "rustine-gradle",
        "rustine-gradle-cargo",
        "rustine-gradle-rust",
        "rustine-gradle-uniffi"
    );
    foreach ($projectName in $projectNames) {
        & "./gradlew" ":build-logic:${projectName}:test";
    }
    foreach ($projectName in $projectNames) {
        & "./gradlew" ":build-logic:${projectName}:apiCheck";
    }
} finally {
    ./.github/workflows/pr-build-test/copy-test-result.ps1;
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}
