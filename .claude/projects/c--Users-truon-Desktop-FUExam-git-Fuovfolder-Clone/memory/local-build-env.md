---
name: local-build-env
description: Local toolchain limits — no Maven, only JDK 11, so backend can't be compiled here
metadata:
  type: project
---

On this Windows dev machine: **no `mvn` on PATH and no Maven wrapper** in `backend/`; only `JDK 11` is installed (`C:\Program Files\Java\jdk-11.0.25`, `JAVA_HOME` points there). The project requires **Java 21**, so backend modules **cannot be compiled or tested locally** — backend changes must be built/verified in the user's own environment (or CI).

Frontends DO verify locally: `npx tsc --noEmit` works in both `Fuexam` and `Fuexam-admin`. ESLint v9 is installed but the project has no `eslint.config.js` (legacy `.eslintrc`), so `npx eslint`/`next lint` fail — rely on `tsc` for FE verification.

**How to apply:** When touching backend Java, write changes carefully matching existing patterns and tell the user it needs a build on their side; don't claim it compiles.
