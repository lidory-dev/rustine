$ErrorActionPreference = "Stop";
$PSNativeCommandUseErrorActionPreference = $true;

./.github/workflows/pr-build-test/environment.ps1;

${env:RUSTFLAGS} = "-D warnings";
try {
    cargo build --verbose;
    cargo test --verbose;
} finally {
    ./.github/workflows/pr-build-test/change-file-owner.ps1;
}
