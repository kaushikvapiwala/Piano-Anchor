#!/bin/sh
# Fails if the index contains a gitlink (mode 160000): a nested repository or worktree that was staged by
# accident. We use no submodules, so any gitlink is a mistake (it happened once with an agent worktree).
# Used by the pre-commit hook and CI.
set -eu
links="$(git ls-files --stage | awk '$1 == "160000" { print $4 }')"
if [ -n "$links" ]; then
  echo "error: nested git repositories (gitlinks) are staged; remove them with 'git rm --cached <path>':" >&2
  echo "$links" >&2
  exit 1
fi
