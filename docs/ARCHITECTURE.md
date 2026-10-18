# Kaagaz Vault — Architecture proposal

## Current repository state
The original main branch contained only a minimal README heading, with no application framework, package manifest, server, database schema, or test setup. The Phase 1 dashboard is therefore a dependency-free HTML/CSS/JavaScript prototype, not an integration into an existing app stack.

## Proposed production shape

- **Frontend:** React + TypeScript + Vite, introduced only after the UI prototype and behavior are reviewed. Keep the interface component-based, responsive, and keyboard-accessible.
- **Identity:** managed authentication with email verification, recovery flow, session expiry, and secure sign-out. Do not invent a custom password store.
- **Metadata:** PostgreSQL table for document metadata, owner ID, category, generated object key, content type, size, timestamps, and favourite state. Every user-owned row must be isolated by row-level authorization.
- **Binary storage:** private object bucket; never public-by-default for identity, health, financial, or legal documents. Issue short-lived access links only after authorization.
- **Upload pipeline:** server-side size and content checks; allowlist extensions and MIME/content signatures; generated object names; quotas; safe error handling. Consider malware scanning or content-disarm workflows if the hosting and budget allow.
- **Secrets:** keep privileged server keys outside frontend code. Frontend keys are only publishable keys paired with correct authorization policies.
- **Operations:** backups and recovery drills, dependency updates, least-privilege access, security logging that avoids document contents and credentials.

## Backend options to evaluate

### Option A — Supabase (fastest initial path)
Auth + Postgres + private Storage + row-level security can reduce infrastructure work. The team must still author and test restrictive policies, configure file size/type limits, and keep service-role credentials server-side. A publishable key alone does not secure a table or bucket; RLS policies are required. Confirm current plan quotas, regional availability, retention, and costs before committing.

References:
- Storage access control: https://supabase.com/docs/guides/storage/security/access-control
- Private/public bucket model: https://supabase.com/docs/guides/storage/buckets/fundamentals
- Secure data guidance: https://supabase.com/docs/guides/database/secure-data

### Option B — Custom API + PostgreSQL + private object storage
More control over validation, audit, encryption and deployment; also more operational burden. Choose only if the product needs controls that the managed option cannot satisfy or the team can operate it responsibly.

## File upload security requirements
Use OWASP's file upload guidance as the baseline:
- allowlist only the formats the product supports;
- validate content, not only the filename or client-supplied Content-Type;
- generate storage keys server-side and never use raw user filenames as object paths;
- set file-size and account-quota limits;
- require authentication and per-object authorization;
- store outside public web roots / in private buckets;
- consider malware scanning or content disarm/reconstruction for relevant document formats;
- protect upload endpoints against CSRF where applicable.

Reference: https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html

## Security and privacy decisions still required
1. Is this a personal single-user vault, a multi-user SaaS, or shared family/workspaces?
2. What threat model is required: standard managed encryption and access control, or client-side end-to-end encryption where the service cannot read files?
3. Which document formats and maximum file sizes must be supported?
4. Which jurisdictions, data-residency requirements, retention rules, and deletion guarantees apply?
5. Is sharing needed? If yes, sharing must be explicit, scoped, revocable where feasible, and time-limited.
6. What is the expected budget and tolerance for vendor lock-in?

## Critical distinction
Encryption at rest and TLS are not the same as end-to-end encryption. If the service can decrypt files, administrators or a compromised service may be able to access content. Do not market the vault as zero-knowledge or end-to-end encrypted unless client-side key management, recovery, sharing, metadata leakage, and threat-model tests support that claim.

## Architecture acceptance gates
- Document owner isolation tested for metadata and file operations.
- Private bucket verified; no anonymous download path.
- Upload allowlist, content validation, file-size limits, generated object keys, and quota enforcement tested server-side.
- No service-role/secret key in built frontend assets or repository history.
- Authentication recovery, account deletion, data export, backup/restore, and access-denied behavior tested.
- Security and privacy claims match the actual implementation.

## References
- OWASP File Upload Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html
- OWASP Input Validation Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html
- Supabase Storage and database security docs listed above.
