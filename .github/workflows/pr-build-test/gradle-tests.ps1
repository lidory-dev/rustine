$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

try {
    # Keep `build`, as these tests check the release artifacts as well (R8, JNI library packaging).
    # Only the `cargo check` of the release builds, whose outputs nothing uses, is skipped.
    ./gradlew build `
        "-Prustine.projects.skipReleaseCargoChecks=true" `
        "-Prustine.projects.uniffiTests=false" `
        "-Prustine.projects.uniffiTests.extTypes=false" `
        "-Prustine.projects.uniffiTests.futures=false" `
        "-Prustine.projects.examples.app=false" `
        "-Prustine.projects.examples.audioCppApp=false" `
        "-Prustine.projects.examples.customTypes=false" `
        "-Prustine.projects.examples.tokioBlake3App=false" `
        "-Prustine.projects.examples.tokioBoringApp=false";
} finally {
    ./.github/workflows/pr-build-test/copy-test-result.ps1;
    ./gradlew --stop;
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}
