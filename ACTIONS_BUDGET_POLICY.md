# GitHub Actions budget protection — November 4, 2026

The account received a 90%-of-3,000-minutes warning four days into the billing cycle. Builds are manually dispatched only, including the Android 16 proof option. Do not use GitHub Actions to debug routine code edits; run checks locally first, and consolidate changes.

Before running a paid-minute workflow, obtain explicit permission to run the named job, describing purpose, maximum duration (currently 45 minutes) and expected usage. Development or release permission alone is not authorization for unlimited builds. Retain current app code, signed outputs and essential data; historical runs do not refund usage when removed.

## Current-release-only publication

The owner no longer requires previous APK rollback versions. A manual Till Counter build must never publish unless `publish_release=true` and `confirm_version` exactly matches the source version. The current tagged GitHub Release is not overwritten. When the new signed release passes all proof gates, the release process can delete obsolete prior tagged releases while preserving the current release and source. The workflow's short-lived proof artifact expires after three days. No GitHub Actions job may be dispatched without owner-approved usage; never use CI to debug edits.
