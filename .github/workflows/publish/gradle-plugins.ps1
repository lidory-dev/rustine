$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

$projectNames = @(
    "rustine-gradle",
    "rustine-gradle-cargo",
    "rustine-gradle-rust",
    "rustine-gradle-uniffi"
);

foreach ($projectName in $projectNames) {
    & "./gradlew" ":build-logic:${projectName}:publishToMavenCentral" `
        "--no-configuration-cache";
}