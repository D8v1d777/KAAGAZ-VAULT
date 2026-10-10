# Android Release Security Skill

## Purpose
Make privacy and security claims about the shipped artifact, not merely source code.

## Required workflow
- Review dependency purpose, maintenance status, license, transitive dependencies, permissions, network behavior, and SDK/minSdk compatibility before adoption.
- Pin versions using the repository's chosen dependency-management approach; avoid floating versions and unreviewed repositories.
- Keep secrets and signing keys out of Git and CI logs. Use least-privilege CI permissions and protect release credentials.
- Audit the merged manifest and final APK/AAB for INTERNET permission, exported components, backup settings, cleartext traffic, debuggable flags, trackers, and unexpected SDKs.
- Confirm backup/data-extraction rules match the key recovery and privacy design.
- Generate a dependency inventory/SBOM when practical and retain release provenance and build/test logs.
- Verify release build configuration and signing behavior; never include real documents in screenshots, test reports, or artifacts.

## FOSS offline-build gate
The FOSS build must have no INTERNET permission in the merged manifest and final APK. Source inspection alone is insufficient. Any optional online variant must be clearly separated, consented, and documented; it must not silently alter the FOSS build.

## Acceptance criteria
- CI checks and manual artifact-audit commands are documented.
- Dependency and license changes have recorded rationale.
- Release claims link to evidence and list untested devices/SDKs.
