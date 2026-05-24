# Contributing to MemFlow

Thank you for choosing to contribute to **MemFlow**! 

MemFlow is a unique open-source project designed to help developers and security engineers understand low-level off-heap memory management and safety in Java. By using `sun.misc.Unsafe`, we demonstrate low-latency architectures and their accompanying vulnerabilities.

This guide will walk you through our architectural structure, outline our code quality standards, and provide step-by-step instructions on submitting your first Pull Request!

---

## Codebase Architecture

MemFlow is composed of several core modules under `src/main/java/com/memflow/core`:

1. **`UnsafeHolder`**: Centralized reflection wrapper exposing direct `sun.misc.Unsafe` functionality. Used for native memory operations.
2. **`OffHeapBuffer`**: Direct off-heap memory buffer managing raw pointers, memory copies, and manual deallocations. Supports bulk transfers, in-place fills, and zero-copy slices.
3. **`OffHeapString`**: C-style Unicode direct memory string implementation leveraging null-termination (`\0`) to avoid Garbage Collection.
4. **`BinaryPacketParser`**: High-performance streaming parser that decodes binary protocol packets directly from memory addresses, including batch and checksum-verified variants.
5. **`LogRecordIndexer`**: Log lookup index mapping search record hashes directly to native off-heap memory addresses.
6. **`MemoryManager`**: Allocation pooling manager leasing and reclaiming reusable direct memory blocks to minimize GC pause costs.
7. **`ChecksumValidator`**: 32-bit CRC computation and verification utility used by the packet parser to confirm payload integrity before publication.

---

## Contributing Process

We welcome newcomer contributors! The goal of our repository is for **each contributor to identify, reproduce, and fix exactly one memory safety bug or logical crash issue** in the codebase.

Follow this standard workflow to submit your fix:

### Step 1: Select a Memory Safety or Logic Bug
Explore the codebase to discover the hidden vulnerabilities. Read through the core modules listed above, study the JVM behavior around `sun.misc.Unsafe`, and look for any place where bounds, lifecycles, arithmetic, or concurrency could go wrong.

When you find something you believe is a bug, open a GitHub issue describing the vulnerability, its root cause, and a reproduction approach before opening a PR.

---

### Step 2: Write a Failing JUnit Test Case
Before modifying the implementation:
1. Open the test class that matches the module containing the bug. Tests are organized one-class-per-module under `src/test/java/com/memflow/core/`:
   - `OffHeapBufferTest` — direct off-heap buffer operations, fills, slices.
   - `OffHeapStringTest` — null-terminated off-heap string handling.
   - `BinaryPacketParserTest` — binary packet decoding, length validation, integer handling.
   - `ChecksumValidatorTest` — CRC32 computation and verification.
   - `LogRecordIndexerTest` — log indexing, slot resolution, export.
   - `MemoryManagerTest` — pool leasing and reclamation.
2. Find the corresponding `@Disabled` reproducer test or write a new test case that isolates the bug.
3. Remove the `@Disabled` annotation.
4. Run the test suite:
   ```bash
   mvn test
   ```
5. Confirm that the test **fails** (or crashes the JVM if it reproduces a native segmentation fault). This proves you can reproduce the vulnerability!

---

### Step 3: Implement the Fix
1. Edit the core class containing the bug to add appropriate bounds verification, address null checks, thread synchronization, or proper resource closures.
2. Ensure you follow standard code formatting rules and preserve surrounding comments.

---

### Step 4: Verify the Fix
1. Run the test suite again:
   ```bash
   mvn test
   ```
2. Confirm that your newly enabled test case **passes successfully** and the project builds.
3. Test the built-in CLI demos to ensure they execute safely and provide clear diagnostics:
   ```bash
   mvn clean package
   mvn exec:java -Dexec.args="--run-indexer"
   ```

---

### Step 5: Submit a Pull Request
1. Commit your changes:
   ```bash
   git add .
   git commit -m "Fix memory safety issue: Resolve use-after-free in MemoryManager buffer pooling"
   ```
2. Push your changes to your fork and submit a Pull Request.
3. Maintain professional communication during the PR review process. We will review your code changes, execute automated tests, and provide constructive feedback!

Thank you for contributing to the MemFlow ecosystem!
