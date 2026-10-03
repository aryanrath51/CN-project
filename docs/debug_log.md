# Debug & Issue Log

## Issue 1: BufferOverflowException during Packet Serialization
**Symptom**: `PacketTest` cases failing with `null` exception messages during deserialization/serialization.
**Root Cause**: `Packet.HEADER_SIZE` was defined as `20`, but the actual fields summed to `22` bytes (due to the 8-byte timestamp). The `ByteBuffer` was under-allocated.
**Fix**: Updated `Packet.HEADER_SIZE` to `22`. Tests passed.

## Issue 2: Compilation Failure on Windows (Encoding)
**Symptom**: `javac` threw "unmappable character for encoding windows-1252" errors on `ChannelEmulatorMain.java`.
**Root Cause**: Windows `javac` defaults to `windows-1252`, which choked on Unicode box-drawing characters (`╔════╗`).
**Fix**: 
1. Replaced Unicode box art with standard ASCII (`+====+`).
2. Compiled using the `-encoding UTF-8` flag explicitly.

## Issue 3: Compilation Failure in Tests (Javadoc Globs)
**Symptom**: `javac` threw "class, interface, enum, or record expected" in test files.
**Root Cause**: Javadoc comments contained the command `src/**/*.java`. The `*/` sequence prematurely closed the block comment, causing syntax errors.
**Fix**: Removed `**/*` patterns from comments, listing specific directories instead (e.g., `src/packet/*.java`).
