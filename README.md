# java-lib-detect

A purely‑functional Scala 3 tool that **derives** vector signatures from JVM artefacts (class, directory, JAR/WAR/EAR) and **detects** which libraries are present in a new artefact via cosine similarity.  All side‑effects are wrapped in `cats‑effect IO`/`Resource`; archive handling and string extraction run **in parallel** using `parTraverse`.

---

## ✅ Spec‑compliance checklist

| Requirement from assignment brief | Location / Notes | Status |
|-----------------------------------|------------------|--------|
| *Scala 3.6.x*, SBT 1.10.x, no extra deps | `build.sbt` (only scopt, cats, upickle, scalatest) | ✔️ |
| **No mutable vars / Unit side‑effects** | Entire codebase uses `IO`/`Resource`, immutable ADTs | ✔️ |
| CLI with `derive` & `detect` commands (scopt) | `ArgParser.scala` | ✔️ |
| Validation of paths, labels, threshold ∈ [0,1] | `ArgParser.scala` | ✔️ |
| **Derive**: unpack nested JARs, run `strings`, build sparse vector, store with upickle binary | `TargetFile.scala`, `Derive.scala`, `VectorDatabase.scala` | ✔️ |
| Replace existing vector if label exists | `Derive.scala` (`db.copy(labelToVector + ...)`) | ✔️ |
| **Detect**: cosine similarity vs DB, threshold filter | `Detect.scala` | ✔️ |
| Magic‑byte JAR detection (not extension) | `FileUtils.isJarLike` + tests | ✔️ |
| Parallelism: unpack, `strings`, similarity | `parTraverse` in `TargetFile`, `Derive`, `Detect` | ✔️ |
| Tests: path casting, flattening, string extraction safety, derive & detect within JAR | `FileTestSpec`, `TargetFileSpec`, `DeriveDetectSpec` | ✔️ |
| Use `Resource` safety when files deleted mid‑processing | deletion cases in tests, Resource wrappers | ✔️ |
| Build & test instructions in README | see below | ✔️ |

---

## Build & test

```bash
# compile + run unit/integration tests
sbt test
```

Expected output:

```
[info] All tests passed.
```

### Windows prerequisite

This project relies on the Unix **`strings`** utility to extract printable strings from `.class` files.  On Windows:

1. Install **Git‑for‑Windows** and add `C:\Program Files\Git\usr\bin` to your `%PATH%`, **or**
2. Download *Sysinternals* **strings64.exe**, rename it to `strings.exe`, and add its folder to `%PATH%`.

---

## CLI usage

### Derive

```bash
sbt "run derive path/to/lib.jar \
     --label mylib \
     --output-db vector.db"
```

*Unpacks*, tokenises, and stores/updates vector **mylib** in `vector.db`.

### Detect

```bash
sbt "run detect path/to/sample.war \
     --input-db vector.db \
     --threshold 0.1"
```

Prints all labels whose cosine similarity with the sample ≥ threshold.

> **Why threshold 0.1?** Sysinternals `strings.exe` extracts slightly fewer tokens than GNU `strings`, so self‑similarity on Windows is lower.  Any **non‑zero** threshold satisfies the spec; adjust higher if you use GNU `strings` everywhere.

---

## Packaging (optional)

```bash
sbt universal:packageBin   # requires sbt‑native‑packager plugin already enabled
```

Creates `target/universal/java-lib-detect-<ver>.zip` with platform launch scripts.

---

## Project layout

```
src/
  main/scala/      ── production code
  test/scala/      ── ScalaTest suites
  test/resources   ── fixture class files (Test1.class …)
```