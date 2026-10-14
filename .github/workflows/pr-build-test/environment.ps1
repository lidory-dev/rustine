$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

# The "Configure Gradle for the runner" step of the workflow sets the JVM arguments of the Gradle
# daemon in the gradle.properties of the Gradle user home. ORG_GRADLE_PROJECT_ variables can't set
# them or turn the daemon off: they only set project properties.
if ($IsMacOS) {
    $currentArchitecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture;
    if ($currentArchitecture -eq [System.Runtime.InteropServices.Architecture]::X64) {
        # Disable Gradle configuration cache on Intel macOS
        ${env:GRADLE_OPTS} = "-Dorg.gradle.configuration-cache=false";
    }
}
