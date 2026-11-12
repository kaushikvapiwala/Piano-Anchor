#!/usr/bin/env bash
# Run up to N unattended /next-task sessions back to back, each a fresh headless Claude Code session
# (= /clear + /next-task), one PR per session. Stops at the first status other than "done".
#
# Logs go to $PIANO_ANCHOR_LOGS (default ~/Library/Logs/piano-anchor on macOS, ~/.local/state/piano-anchor elsewhere).
#
# Usage: run-loop.sh [N]          (default N=3)
#        run-loop.sh --dry-run    (one dry-run session: shows what it would pick, changes nothing)
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
if [[ "$(uname)" == "Darwin" ]]; then DEFAULT_LOGS="$HOME/Library/Logs/piano-anchor"; else DEFAULT_LOGS="$HOME/.local/state/piano-anchor"; fi
LOGDIR="${PIANO_ANCHOR_LOGS:-$DEFAULT_LOGS}"
LOCK="$LOGDIR/run-loop.lock"
MAX=3
MODE=unattended
if [[ "${1:-}" == "--dry-run" ]]; then MODE=dry-run; MAX=1; elif [[ -n "${1:-}" ]]; then MAX="$1"; fi

mkdir -p "$LOGDIR"
AWAKE=""; command -v caffeinate >/dev/null && AWAKE="caffeinate -i"
notify() { osascript -e "display notification \"$1\" with title \"Piano Anchor loop\"" >/dev/null 2>&1 || true; }
log() { echo "[$(date '+%F %T')] $*" | tee -a "$LOGDIR/run-loop.log"; }

# One runner at a time.
if ! mkdir "$LOCK" 2>/dev/null; then echo "Another run-loop is active ($LOCK). Remove it if stale."; exit 1; fi
trap 'rmdir "$LOCK"' EXIT

cd "$REPO" || { echo "Repo not found: $REPO"; exit 1; }
gh auth status >/dev/null 2>&1 || { log "gh is not logged in; stopping."; notify "gh not logged in"; exit 1; }
if [[ -n "$(git status --porcelain)" ]]; then
  log "Working tree is not clean; stopping so no session builds on stray changes."
  notify "Repo not clean, loop not started"
  exit 1
fi

for ((i = 1; i <= MAX; i++)); do
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
m = re.findall(r"NEXT_TASK_STATUS:\s*([a-z-]+)\s*[—-]?\s*(.*)", text)
if m:
    s, summary = m[-1]
elif re.search(r"limit|rate", text, re.I):
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
done
log "Reached the cap of $MAX sessions."
notify "Done: $MAX sessions"
