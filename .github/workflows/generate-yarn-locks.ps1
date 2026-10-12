$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

for ($idx = 0; $idx -lt 8; ++$idx) {
    $gradleTests = if (($idx -band 1) -ne 0) { "true" } else { "false" };
    $uniffiTests = if (($idx -band 2) -ne 0) { "true" } else { "false" };
    $examples =    if (($idx -band 4) -ne 0) { "true" } else { "false" };

    ./gradlew :kotlinUpgradeYarnLock :kotlinWasmUpgradeYarnLock `
        "-Prustine.projects.gradleTests=$gradleTests" `
        "-Prustine.projects.uniffiTests=$uniffiTests" `
        "-Prustine.projects.examples=$examples";
}
