# Distribution Guide

## Overview

This guide explains how to distribute your MCP server to others without requiring them to install all dependencies.

## What Changed

### Before
- Used `tsc` (TypeScript compiler) which only transpiles code
- Output was **readable JavaScript** (not minified)
- Users needed to run `npm install` to get dependencies
- Dependencies: `@modelcontextprotocol/sdk`, `oracledb`, `zod`

### After
- Added `esbuild` bundler
- Output is **minified and bundled**
- Most dependencies are included in the bundle
- Only `oracledb` needs to be installed separately (it's a native addon)

## Build Commands

```bash
# Original build (TypeScript only, readable)
npm run build

# New bundled build (minified + dependencies included)
npm run build:bundle
```

## Distribution Options

### Option 1: Bundled JS + oracledb (RECOMMENDED)

**What to send:**
```
dist/db-mcp.bundle.js
```

**User needs to:**
1. Install Node.js
2. Install only `oracledb`: `npm install oracledb`
3. Run: `node dist/db-mcp.bundle.js`

**Advantages:**
- ✅ Smallest package (single file)
- ✅ Users only install one native dependency
- ✅ Code is minified/protected

**Note:** `oracledb` CANNOT be bundled because it's a native C++ addon.

---

### Option 2: Full Package with node_modules

**What to send:**
```
dist/db-mcp.bundle.js
node_modules/
package.json
```

**User needs to:**
1. Install Node.js
2. Run: `node dist/db-mcp.bundle.js`

**Advantages:**
- ✅ Zero npm commands needed
- ✅ Works immediately

**Disadvantages:**
- ❌ Large package size (includes all node_modules)
- ❌ Platform-specific (oracledb has native binaries)

---

### Option 3: With package.json (Hybrid)

**What to send:**
```
dist/db-mcp.bundle.js
package.json (minimal version)
```

**Create minimal package.json:**
```json
{
  "name": "oracle-mcp-server",
  "version": "0.2.0",
  "type": "module",
  "dependencies": {
    "oracledb": "^6.9.0"
  }
}
```

**User needs to:**
1. Install Node.js
2. Run: `npm install`
3. Run: `node dist/db-mcp.bundle.js`

---

## Why isn't it uglified with tsc?

`tsc` (TypeScript compiler) is **NOT a bundler**. It only:
- ❌ Transpiles TypeScript → JavaScript
- ❌ Does NOT bundle dependencies
- ❌ Does NOT minify/uglify code
- ❌ Does NOT include external packages

To bundle and minify, you need a **bundler** like:
- ✅ **esbuild** (what we're using - fast & simple)
- ✅ webpack
- ✅ rollup
- ✅ parcel

## Native Dependencies Issue

`oracledb` is a **native addon** (compiled C++ code) that:
- Cannot be bundled into JavaScript
- Must be installed via npm
- Contains platform-specific binaries (Windows, Linux, macOS)

This is why we use `--external:oracledb` in the build command.

## Verification

Check the bundle was created successfully:
```bash
# Windows PowerShell
Get-Item dist\db-mcp.bundle.js

# Check size (should be ~242 KB)
(Get-Item dist\db-mcp.bundle.js).Length / 1KB
```

## Security Note

The bundled code is minified but not truly obfuscated. For additional protection, consider:
- Using a JavaScript obfuscator (e.g., `javascript-obfuscator`)
- Implementing license validation
- Using code signing
