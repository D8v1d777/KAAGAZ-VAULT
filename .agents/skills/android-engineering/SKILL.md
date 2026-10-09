---
name: android-engineering
description: Design and implement maintainable native Android features in Kotlin and Jetpack Compose for KAAGAZ VAULT.
---

# Android Engineering Skill

## Before coding
- Inspect module structure, Gradle/AGP/Kotlin versions, minSdk, dependency catalog, conventions, and current tests.
- Confirm compatibility against the declared minSdk and the actual device/API requirements. Do not assume a library supports API 21.
- Consult current official Android documentation for APIs whose behavior or lifecycle matters.

## Architecture
- Use a layered design: Compose UI → ViewModel/state holder → use case/domain logic where complexity warrants it → repository → data sources.
- Model UI as immutable state and explicit user actions; collect flows lifecycle-aware.
- Keep camera, OCR, encryption, database, file access, and scheduling behind interfaces at meaningful boundaries.
- Use coroutines and Flow appropriately; never block the main thread with image processing, OCR, database work, or cryptography.
- Design for process death, interrupted work, storage pressure, and device rotation.
- Prefer platform APIs and stable Jetpack components over custom reinventions.

## Compose quality
- Keep composables focused and mostly declarative; put business rules outside composables.
- Use stable keys for lists, state hoisting where useful, lifecycle-aware collection, and previews for important states.
- Include empty, loading, permission-denied, failure, and retry states.
- Provide semantics/content descriptions, adequate touch targets, scalable text, and contrast.
- Avoid one giant screen file, deeply nested conditionals, magic numbers, and unnecessary state duplication.

## Delivery
- Add tests for ViewModels, repositories, domain rules, and important UI behavior.
- Check recomposition/performance for large document lists and image previews.
- Explain trade-offs and API compatibility. Do not invent build results or claim unsupported devices work.
