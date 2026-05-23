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
Explore the codebase to discover the hidden vulnerabilities. Our current catalog of issues includes:

**Allocation & Pointer Hygiene**
- **Integer Overflow on Allocation**: Multiplications leading to small native memory allocations but large recorded capacities.
- **Integer Overflow on Byte-Count Aggregation**: Pool-wide totals computed with `int` arithmetic that silently wraps for large pools.
- **Double Free / Bad Pointer Management**: Releasing a native pointer multiple times or referencing freed direct addresses.
- **Uninitialized Memory Usage**: Accessing raw off-heap memory blocks before zeroing them out, leaking garbage bytes.
- **Aliasing via Sliced Views**: Sub-range views over a parent buffer share the same native allocation; releasing either side leaves the other dangling.
- **Slice Allocation Leak**: Construction path for sliced views silently abandons a freshly malloc'd block.

**Buffer & String Safety**
- **Off-by-One Write Overflows**: Allocating exact character lengths and writing the null-terminating character out-of-bounds.
- **Buffer Overflow (strcpy)**: Writing incoming strings without checking destination capacity limits.
- **Unbounded Append**: `OffHeapString.append` writes past the configured capacity and never grows the underlying buffer.
- **Logic Bug in Empty-String Length**: Capacity-constructed off-heap strings advertise a logical length equal to capacity instead of zero.
- **Negative Index Acceptance**: Bulk transfer routines do not reject negative source/destination indexes, enabling writes before the buffer's base address.
- **Bounds Check Integer Overflow**: `index + length` upper-bound checks wrap around for large values, defeating the guard.
- **Silent Range No-Op**: `OffHeapBuffer.fill` accepts inverted ranges and reports success despite performing no work.

**Packet & Stream Parsing**
- **Array Out-Of-Bounds (Heartbleed over-read)**: Reading packet payloads past stream buffer bounds due to missing length validation.
- **Integer Underflow (JVM Crash)**: Negative packet length indicators causing huge copy memory operations and native segmentation faults.
- **Negative Offset Read**: `parsePayload` accepts negative offsets, reading native memory before the stream buffer's base address.
- **Payload Buffer Leak on Validation Failure**: Routing buffer is allocated before the size check, leaking native memory on malformed packets.
- **Batch Cursor Overflow**: `parseBatch` advances its cursor with unchecked addition, wrapping into negative territory on crafted payload lengths.

**Checksum & Integrity**
- **Broken CRC Verification**: `ChecksumValidator.verify` short-circuits on any matching byte instead of the full 32-bit comparison, accepting corrupt packets.
- **CRC Byte-Count Overflow**: Buffer-wide CRC computation multiplies capacity by element size as `int`, producing incorrect totals for large buffers.

**Index & File Handling**
- **Null Pointer Dereference (JVM Crash)**: Dereferencing raw pointer `0L` inside index lookups.
- **Weak Input Sanitization (Path Traversal)**: Export filenames are not stripped of `../` sequences before file creation.
- **Header Injection in Export**: Index names are written verbatim into the export header, allowing embedded newlines to forge additional metadata lines.
- **Improper File Handling**: Native resource descriptor leaks occurring during directory export exceptions.
- **Idempotency Violation in Close**: Repeated `LogRecordIndexer.close()` invocations double-free every populated slot.
- **Orphaned Wrapper Finalizer**: `LogRecordIndexer.indexRecord` drops the `OffHeapString` wrapper while retaining its native address — the wrapper's finalizer can revisit the freed pointer.
- **Hash Slot Collision**: `lookupByHash` reduces hash keys with `%` and falls through to an unguarded slot read on collisions or unmapped slots.

**Pool & Concurrency**
- **Use-After-Free (UAF)**: Modifying active pool buffers via stale client references.
- **Foreign Buffer Acceptance**: `MemoryManager.returnBuffer` accepts any buffer reference equal to a pooled slot, with no ownership tracking.
- **Concurrent Lease Logic Race**: Non-thread-safe index modifications under concurrent leasing.
- **Unsynchronized Shutdown**: `shutdown` may free buffers concurrently being leased to other threads.
- **Pool Peek Bypasses Lease State**: `peekBuffer` exposes pooled buffers regardless of in-use flags, enabling silent UAF.

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
