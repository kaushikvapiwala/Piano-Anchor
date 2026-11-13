#!/usr/bin/env bash
# Run up to N unattended /next-task sessions back to back, each a fresh headless Claude Code session
# (= /clear + /next-task), one PR per session. Stops at the first status other than "done".
# Between sessions it waits (up to PR_WAIT_MIN minutes) for open PRs to merge, so the next session starts
# from a main that already has the previous PR and its "Next up" edit.
#
# Logs go to $PIANO_ANCHOR_LOGS (default ~/Library/Logs/piano-anchor on macOS, ~/.local/state/piano-anchor elsewhere).
#
# Usage: run-loop.sh [N]          (default N=3)
#        run-loop.sh --dry-run    (one dry-run session: shows what it would pick, changes nothing)
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
if [[ "$(uname)" == "Darwin" ]]; then DEFAULT_LOGS="$HOME/Library/Logs/piano-anchor"; else DEFAULT_LOGS="$HOME/.local/state/piano-anchor"; fi
LOGDIR="${PIANO_ANCHOR_LOGS:-$DEFAULT_LOGS}"
[[ "$LOGDIR" == /* ]] || LOGDIR="$PWD/$LOGDIR"
PR_WAIT_MIN="${PR_WAIT_MIN:-45}"
LOCK="$LOGDIR/run-loop.lock"
MAX=3
MODE=unattended
if [[ "${1:-}" == "--dry-run" ]]; then MODE=dry-run; MAX=1; elif [[ -n "${1:-}" ]]; then MAX="$1"; fi
[[ "$MAX" =~ ^[1-9][0-9]*$ ]] || { echo "Usage: run-loop.sh [N] | --dry-run   (N: positive integer)"; exit 2; }

mkdir -p "$LOGDIR"
AWAKE=""; command -v caffeinate >/dev/null && AWAKE="caffeinate -i"
# The message goes in as an argument, never into the script text: it can contain model-written quotes.
notify() {
  osascript -e 'on run argv' -e 'display notification (item 1 of argv) with title "Piano Anchor loop"' \
    -e 'end run' "$1" >/dev/null 2>&1 || true
}
log() { echo "[$(date '+%F %T')] $*" | tee -a "$LOGDIR/run-loop.log"; }

# One runner at a time.
if ! mkdir "$LOCK" 2>/dev/null; then echo "Another run-loop is active ($LOCK). Remove it if stale."; exit 1; fi
trap 'rmdir "$LOCK"' EXIT

cd "$REPO" || { echo "Repo not found: $REPO"; exit 1; }
gh auth status >/dev/null 2>&1 || { log "gh is not logged in; stopping."; notify "gh not logged in"; exit 1; }

# Wait for this account's open PRs to merge; if one is still open after PR_WAIT_MIN, the next session's
# reconcile step makes fixing it that session's task.
wait_for_prs() {
  local open
  for ((m = 0; m < PR_WAIT_MIN; m++)); do
    open=$(gh pr list --state open --author @me --json number --jq length 2>/dev/null) || open=1
    [[ "$open" == "0" ]] && return
    sleep 60
  done
  log "PRs still open after $PR_WAIT_MIN min; the next session reconciles them."
}

for ((i = 1; i <= MAX; i++)); do
  if [[ -n "$(git status --porcelain)" ]]; then
    log "Working tree is not clean; stopping so no session builds on stray changes."
    notify "Repo not clean, loop stopped before session $i"
    exit 1
  fi
  out="$LOGDIR/session-$(date '+%Y%m%d-%H%M%S').json"
  log "Session $i/$MAX ($MODE) starting -> $out"
  # caffeinate (macOS) keeps the machine awake while the session runs; each claude -p is a brand-new session.
  # shellcheck disable=SC2086 # empty when caffeinate is missing; bash 3.2 has no safe empty arrays under set -u
  $AWAKE claude -p "/next-task $MODE" \
    --model opus --permission-mode auto --output-format json >"$out" 2>>"$LOGDIR/run-loop.log"
  code=$?

  status=$(python3 -I - "$out" <<'EOF'
import json, re, sys
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print("error|no JSON output (crash or usage limit?)"); sys.exit()
text = d.get("result") or ""
# Tolerate markdown around the status word, e.g. **NEXT_TASK_STATUS:** `done`.
m = re.findall(r"NEXT_TASK_STATUS:[*`\s]*([a-z-]+)[*`]*\s*[—-]?\s*(.*)", text)
if m:
    s, summary = m[-1]
elif re.search(r"\b(usage|rate) limit|limit reached", text, re.I):
    s, summary = "error", "usage limit: " + text.strip()[:120]
else:
    s, summary = "error", "no status line: " + text.strip()[-120:]
cost = d.get("total_cost_usd")
print(f"{s}|{summary.strip()} (turns {d.get('num_turns')}, est. ${cost:.2f})" if cost is not None else f"{s}|{summary.strip()}")
EOF
)
  st="${status%%|*}"; msg="${status#*|}"
  log "Session $i: exit $code, status $st: $msg"

  if [[ "$st" != "done" || "$MODE" == "dry-run" ]]; then
    notify "$st: $msg"
    exit 0
  fi
  ((i < MAX)) && wait_for_prs
done
log "Reached the cap of $MAX sessions."
notify "Done: $MAX sessions"
