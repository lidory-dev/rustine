# Dot-source this script to define Invoke-WithRetry.

# Runs $ScriptBlock until it succeeds, at most 4 times, waiting 15, 30, then 60 seconds between the
# attempts, and rethrows the error of the last attempt. Use it for the steps that download something
# (package managers, toolchains), whose servers fail from time to time. A native command exiting
# with a non-zero code counts as a failure. The output of $ScriptBlock is passed through, so the
# script block must not output anything before it fails if the caller keeps the output.
function Invoke-WithRetry {
    param(
        [Parameter(Mandatory = $true, Position = 0)]
        [string]$Description,
        [Parameter(Mandatory = $true, Position = 1)]
        [scriptblock]$ScriptBlock
    );

    # Make failing native commands throw, whatever the preferences of the caller are.
    $ErrorActionPreference = "Stop";
    $PSNativeCommandUseErrorActionPreference = $true;

    # The local variables are prefixed so they don't hide the caller's variables used by $ScriptBlock.
    $retryDelaysInSeconds = @(15, 30, 60);
    $retryAttemptCount = $retryDelaysInSeconds.Length + 1;
    for ($retryAttempt = 1; ; ++$retryAttempt) {
        try {
            $global:LASTEXITCODE = 0;
            & $ScriptBlock;
            # Catches the native commands that failed without throwing.
            if ($LASTEXITCODE -ne 0) {
                throw "The last native command exited with code $LASTEXITCODE.";
            }
            return;
        } catch {
            if ($retryAttempt -ge $retryAttemptCount) {
                Write-Host "$Description failed after $retryAttemptCount attempts.";
                throw;
            }
            $retryDelay = $retryDelaysInSeconds[$retryAttempt - 1];
            Write-Host "$Description failed (attempt $retryAttempt of $retryAttemptCount): $_";
            Write-Host "Retrying in $retryDelay seconds...";
            Start-Sleep -Seconds $retryDelay;
        }
    }
}
