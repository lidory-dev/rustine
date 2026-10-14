$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

# The tasks below only need the included build-logic build. Don't configure the test and example
# projects, which every Gradle invocation below would configure again otherwise.
$arguments = @(
    "-Prustine.projects.gradleTests=false",
    "-Prustine.projects.uniffiTests=false",
    "-Prustine.projects.uniffiTests.extTypes=false",
    "-Prustine.projects.uniffiTests.futures=false",
    "-Prustine.projects.examples.app=false",
    "-Prustine.projects.examples.audioCppApp=false",
    "-Prustine.projects.examples.customTypes=false",
    "-Prustine.projects.examples.tokioBlake3App=false",
    "-Prustine.projects.examples.tokioBoringApp=false"
);

try {
    $projectNames = @(
        "rustine-gradle",
        "rustine-gradle-cargo",
        "rustine-gradle-rust",
        "rustine-gradle-uniffi"
    );
    foreach ($projectName in $projectNames) {
        & "./gradlew" ":build-logic:${projectName}:test" $arguments;
    }
    foreach ($projectName in $projectNames) {
        & "./gradlew" ":build-logic:${projectName}:apiCheck" $arguments;
    }
} finally {
    ./.github/workflows/pr-build-test/copy-test-result.ps1;
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}
