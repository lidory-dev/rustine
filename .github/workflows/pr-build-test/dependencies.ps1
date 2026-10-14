$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

. (Join-Path $PSScriptRoot "retry.ps1");

if ($IsWindows) {
    # Use the MSYS2 preinstalled on the GitHub-hosted Windows runners (not on PATH) instead of
    # Chocolatey, whose community repository often fails with HTTP 503/504.
    $msys2Directory = "C:\msys64";
    # - perl: required by :examples:tokio-blake3-app to use Perl to build OpenSSL
    # - mingw-w64-x86_64-gcc: MinGW
    # - mingw-w64-x86_64-nasm: required by :tests:gradle:android-linking
    # -y refreshes the package database first, as the one of the runner image may refer to package
    # versions the mirrors don't have anymore.
    Invoke-WithRetry "Installing the MSYS2 packages" {
        & "$msys2Directory\usr\bin\bash.exe" -lc "pacman -Sy --needed --noconfirm perl mingw-w64-x86_64-gcc mingw-w64-x86_64-nasm";
    };
    # Check if OpenSSL will complain about backslashes with the perl shipped with MSYS2
    & "$msys2Directory\usr\bin\perl.exe" -MFile::Spec::Functions=rel2abs,abs2rel -e "abs2rel(rel2abs('.')) =~ m{\\\\} and die 'This perl does not produce Unix-like paths';"
    & "$msys2Directory\mingw64\bin\gcc.exe" --version;
    & "$msys2Directory\mingw64\bin\nasm.exe" -v;
    # Prepend MSYS2 Perl to PATH, so it takes precedence over the Strawberry Perl of the runner image,
    # and the MinGW tools (GCC, NASM). Each line of GITHUB_PATH is prepended to PATH, so the last one
    # comes first: the native MinGW tools take precedence over the MSYS2 tools of the same name
    # (e.g. zstd, which the cache actions run).
    "$msys2Directory\usr\bin" | Out-File -FilePath $env:GITHUB_PATH -Encoding utf8 -Append;
    "$msys2Directory\mingw64\bin" | Out-File -FilePath $env:GITHUB_PATH -Encoding utf8 -Append;
} elseif ($IsMacOS) {
    # The workflow sets HOMEBREW_NO_AUTO_UPDATE so `brew install` doesn't update Homebrew first, which
    # downloads from GitHub and fails from time to time.
    Invoke-WithRetry "Installing MinGW-w64" {
        brew install mingw-w64;
    };

    # Workaround for #205 and #206.
    # Manually install nightly Rust for watchOS and tvOS.
    # To fix the nightly toolchain to a specific version, remove the pre-installed one.
    rustup uninstall nightly;
    # Install 1.90.0-nightly (a00149764 2025-07-14), a nightly of the Rust version in
    # rust-toolchain.toml. UniFFI 0.32 and its dependencies require Rust 1.88 or higher.
    $version = "nightly-2025-07-15";
    $osArch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture;
    $rustTriplet = switch ($osArch) {
        "X64" { "x86_64-apple-darwin" }
        "Arm64" { "aarch64-apple-darwin" }
        default { $null }
    };
    Invoke-WithRetry "Installing the $version Rust toolchain" {
        rustup install $version;
    };
    Invoke-WithRetry "Installing rust-src for the $version Rust toolchain" {
        rustup component add rust-src --toolchain $version;
    };
    # Use the version-fixed nightly as the default nightly toolchain.
    ln -s "$HOME/.rustup/toolchains/$version-$rustTriplet" "$HOME/.rustup/toolchains/nightly-$rustTriplet";
    Remove-Variable version;
} elseif ($IsLinux) {
    Invoke-WithRetry "Updating the APT package lists" {
        sudo apt-get update;
    };
    Invoke-WithRetry "Installing MinGW-w64" {
        sudo apt-get install -y mingw-w64;
    };
}
