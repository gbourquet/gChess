# Scripts

Collection of utility scripts for the gChess project.

## add-license-headers.sh

Adds MIT License headers to all Kotlin source files in `src/main/kotlin`.

### Usage

```bash
./scripts/add-license-headers.sh
```

### What it does

- Scans all `.kt` files in `src/main/kotlin`
- Skips files that already have a license header
- Adds the MIT License header at the top of each file
- Excludes generated code (build directories)

### Re-running

The script is **idempotent** - it's safe to run multiple times. Files with existing headers are automatically skipped.

### Verification

After running, verify the changes:

```bash
git diff src/main/kotlin
```

### License Header Format

The script adds the following header:

```kotlin
/*
 * Copyright (c) 2024-2025 Guillaume Bourquet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
```

## Future Scripts

Additional utility scripts can be added here for:
- Code generation
- Database migration helpers
- Testing utilities
- Deployment automation
