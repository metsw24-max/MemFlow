# MemFlow: High-Performance Off-Heap Storage & Analytics Engine

[![CI](https://github.com/metsw24-max/memflow-core/actions/workflows/build.yml/badge.svg)](https://github.com/metsw24-max/memflow-core/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](#)
[![Java Version](https://img.shields.io/badge/java-11%20%7C%2017%20%7C%2021-orange.svg)](#)
[![Platforms](https://img.shields.io/badge/platforms-Linux%20%7C%20Windows%20%7C%20macOS-lightgrey.svg)](#)

**MemFlow** is an ultra-low latency, zero-copy binary data parsing and log indexing engine built for low-latency Java environments. By shifting massive data operations entirely off-heap and managing direct native memory addresses via `sun.misc.Unsafe`, MemFlow completely bypasses standard JVM Garbage Collection overhead. It is designed to achieve microsecond-level throughput for high-frequency financial packet decoding and real-time big data log indexing.


## Key Features

- **GC-Free Native Buffers**: Low-level direct memory allocations wrapped in high-performance off-heap constructs.
- **Custom C-Style String Representation**: A custom off-heap Unicode character string implementation employing null-termination to avoid GC object pollution.
- **Ultra-Fast Binary Packet Decoding**: Zero-copy packet parser decoding high-throughput streaming byte packets in real-time.
- **Native Block Pooling Manager**: A recycling pool that leases and returns direct memory blocks to eliminate native allocation overhead.
- **Interactive Educational Demos**: Embedded CLI commands showcasing native memory bugs, performance leaks, and segmentation fault scenarios.

---

## Project Architecture

```
                                      +--------------------------+
                                      |         App CLI          |
                                      +------------+-------------+
                                                   |
                             +---------------------+---------------------+
                             v                                           v
               +---------------------------+               +---------------------------+
               |     LogRecordIndexer      |               |    BinaryPacketParser     |
               +-------------+-------------+               +------+-------------+------+
                             |                                    |             |
                             v                                    v             v
               +---------------------------+    +-------------------+   +-----------------+
               |       OffHeapString       |    |   OffHeapBuffer   |   | ChecksumValidator|
               +-------------+-------------+    +---------+---------+   +-----------------+
                             |                            |
                             +-------------+--------------+
                                           v
                              +--------------------------+
                              |       MemoryManager      |
                              +--------------------------+
```

---

## Getting Started

### Prerequisites
- **Java SE**: JDK 11 or higher (Java 17 / 21 fully supported).
- **Build Tool**: Apache Maven 3.6 or higher.

### JVM Access Flags
Modern JDKs (9+) restrict reflective access to internal classes such as `sun.misc.Unsafe`. To compile and run applications utilizing off-heap memory, you must configure the JVM to open access to the relevant internal packages.

Add the following command-line flags when launching the project manually:
```bash
--add-opens=jdk.unsupported/sun.misc=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
```
*Note: These flags are pre-integrated into the project's `.mvn/jvm.config` and the Maven Surefire `argLine`, so `mvn clean test` and `mvn exec:java` pick them up automatically.*

---

## Build & Testing Instructions

### 1. Build and Compile the Package
To compile all modules, resolve dependencies, and package the application jar, execute:
```bash
mvn clean package
```

### 2. Run the Automated Test Suite
To verify the core engine's architectural correctness:
```bash
mvn test
```
*Note: All standard unit tests are designed to pass successfully. Advanced security reproducers demonstrating low-level crash vulnerabilities are annotated with `@Disabled` to prevent automated build breakages, but can be enabled individually by researchers.*

### 3. Run the Test Suite Under JVM Sanitizer Flags
For deeper diagnostics — strict bytecode verification, native-memory tracking, and JNI usage checks — activate the `sanitizer` profile:
```bash
mvn -Psanitizer test
```
The sanitizer profile injects the closest Java equivalents to the C/C++ sanitizer family:
- `-Xcheck:jni` — strict validation of JNI calls
- `-Xverify:all` — full bytecode verification including bootclasspath
- `-XX:NativeMemoryTracking=detail` — per-call-site off-heap allocation tracking
- `-XX:+CrashOnOutOfMemoryError` + `-XX:+HeapDumpOnOutOfMemoryError` — fail fast and archive a heap dump on native OOM

This is the same profile used by the `JVM Sanitizer` job in CI.

---

## Continuous Integration

Every push and pull request is exercised across a 3 × 3 matrix:

| Platform        | JDK 11 | JDK 17 | JDK 21 |
|-----------------|:------:|:------:|:------:|
| Ubuntu (Linux)  | ✓      | ✓      | ✓      |
| Windows         | ✓      | ✓      | ✓      |
| macOS           | ✓      | ✓      | ✓      |

Three CI jobs run on every commit:

1. **Build & Test Matrix** — compile, test, and package on each OS / JDK pair (9 jobs).
2. **JVM Sanitizer** — re-runs the test suite under the strict JVM diagnostic flags described above. Surefire reports, heap dumps, and `hs_err_pid*.log` crash logs are uploaded as artifacts on failure.
3. **CLI Smoke Test** — exercises the `--run-indexer` demo end-to-end and verifies the exported index file is produced.

---

## Contributing

Are you ready to fix memory safety bugs? Check out [CONTRIBUTING.md](file:///d:/Digi_Java_Proj/CONTRIBUTING.md) to understand how you can select a vulnerability, write a test reproducer, implement a fix, and submit a Pull Request.
