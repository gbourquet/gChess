#!/bin/bash
# Script to add MIT License headers to Kotlin source files

set -e

# License header to add
read -r -d '' LICENSE_HEADER << 'EOF' || true
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

EOF

# Function to check if file already has a license header
has_license_header() {
    local file="$1"
    head -n 5 "$file" | grep -q "Copyright (c)" && return 0 || return 1
}

# Function to add license header to a file
add_header() {
    local file="$1"

    # Skip if already has header
    if has_license_header "$file"; then
        echo "⏭️  Skipping $file (already has license header)"
        return
    fi

    # Create temporary file with header + original content
    {
        echo "$LICENSE_HEADER"
        cat "$file"
    } > "$file.tmp"

    # Replace original file
    mv "$file.tmp" "$file"
    echo "✅ Added license header to $file"
}

# Find all Kotlin files in src/main (excluding generated code)
echo "🔍 Finding Kotlin source files..."

find src/main/kotlin -name "*.kt" -type f \
    -not -path "*/generated/*" \
    -not -path "*/build/*" | while read -r file; do
    add_header "$file"
done

echo ""
echo "✨ Done! License headers added to source files."
echo "💡 To verify: git diff src/"
