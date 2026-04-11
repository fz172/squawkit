---
name: Post-task codebase cleanup
description: After completing large features or refactors, run a cleanup pass on all changed files
type: feedback
---

After finishing a big job (large feature implementation, big refactor, multi-file changes), always perform a cleanup pass on all changed .kt files before committing.

**Why:** Inline fully-qualified class paths, trailing blank lines, and formatting inconsistencies accumulate during implementation and should be caught before commit.

**How to apply:** Before the final commit of a large task, scan all changed files for:
1. **Fully-qualified class references used inline** instead of importing (e.g. `kotlinx.coroutines.flow.flowOf(...)` → add `import flowOf` and use short name)
2. **Trailing blank lines** at end of files
3. **Extra blank lines** (double+ blank lines where single is expected)
4. **Import ordering** — `kotlin.*` before `kotlinx.*`, alphabetical within groups
5. **Other formatting issues** — inconsistent indentation, long lines that should wrap
