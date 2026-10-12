$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

# Change the permission of build results if the user ID is provided
if ($null -ne ${env:RUSTINE_PR_BUILD_TEST_USER}) {
    & "chown" ${env:RUSTINE_PR_BUILD_TEST_USER} -R .;
}
