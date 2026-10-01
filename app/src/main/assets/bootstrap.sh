#!/data/data/com.termux/files/usr/bin/bash
set -eu

INSTALL_ROOT="$HOME/.mobile-bot-ui"
LOG_FILE="$INSTALL_ROOT/bootstrap.log"
HOST_FILE="$INSTALL_ROOT/host.mjs"
PREDEFINED_SKILLS_FILE="$INSTALL_ROOT/generatedSkills.js"
LOCAL_AI_MODULE_FILE="$INSTALL_ROOT/localAi.js"
PI_RUNTIME_MODULE_FILE="$INSTALL_ROOT/piRuntime.js"
PHONE_CLIENT_FILE="$INSTALL_ROOT/phone-client.mjs"
AUTOMATION_CLIENT_FILE="$INSTALL_ROOT/automation-client.mjs"
DEVICE_TOKEN_FILE="$INSTALL_ROOT/device-token"
RESOLV_FILE="$INSTALL_ROOT/resolv.conf"
LOGIN_HELPER="$INSTALL_ROOT/codex-device-login.sh"
HOST_PID_FILE="$INSTALL_ROOT/host.pid"
STATUS_FILE="$INSTALL_ROOT/bootstrap.status"
ACTIVE_FILE="$INSTALL_ROOT/bootstrap.active"
BOOTSTRAP_ID='__BOOTSTRAP_ID__'
STAGE_ROOT="$INSTALL_ROOT/bootstrap-stage/$BOOTSTRAP_ID"

mkdir -p "$INSTALL_ROOT"
printf '%s %s %s %s\n' "$$" "$(cat /proc/sys/kernel/random/boot_id)" "$(awk '{print $22}' /proc/$$/stat)" "$BOOTSTRAP_ID" > "$ACTIVE_FILE"
exec 3>&1
exec >>"$LOG_FILE" 2>&1

CURRENT_PHASE=starting
write_status() {
  CURRENT_PHASE="$1"
  printf '%s:%s\n' "$BOOTSTRAP_ID" "$1" >"$STATUS_FILE.tmp"
  mv "$STATUS_FILE.tmp" "$STATUS_FILE"
  chmod 600 "$STATUS_FILE"
  printf 'bootstrap_phase time=%s id=%s phase=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$BOOTSTRAP_ID" "$1"
}

stop_runtime_pid() {
  target_pid="$1"
  stop_mode="${2:-graceful}"
  case "$target_pid" in
    *[!0-9]*|'') return 0 ;;
  esac
  if [ "$stop_mode" = force ]; then
    kill -KILL "$target_pid" 2>/dev/null || true
  else
    kill -TERM "$target_pid" 2>/dev/null || return 0
  fi
  # Do not rewrite an executable or shared object while its previous inode is
  # still mapped by llama-server. Android can otherwise crash on the first
  # later inference when it faults in an overwritten page.
  for _ in 1 2 3 4 5; do
    kill -0 "$target_pid" 2>/dev/null || return 0
    sleep 1
  done
  kill -KILL "$target_pid" 2>/dev/null || true
  for _ in 1 2 3 4 5; do
    kill -0 "$target_pid" 2>/dev/null || return 0
    sleep 1
  done
}

stop_local_ai_processes() {
  if command -v pgrep >/dev/null 2>&1; then
    for candidate_pid in $(pgrep -f 'llama-server.*--port 8769' 2>/dev/null || true); do
      [ "$candidate_pid" = "$$" ] && continue
      printf 'bootstrap_llama_stop pid=%s source=pgrep\n' "$candidate_pid"
      stop_runtime_pid "$candidate_pid" force
    done
  fi
  local_binary=$(readlink -f "$INSTALL_ROOT/local-ai/bin/llama-server" 2>/dev/null || true)
  for process_dir in /proc/[0-9]*; do
    [ -r "$process_dir/cmdline" ] || continue
    candidate_pid=${process_dir##*/}
    [ "$candidate_pid" = "$$" ] && continue
    candidate_command=$(tr '\000' ' ' <"$process_dir/cmdline" 2>/dev/null || true)
    candidate_executable=$(readlink -f "$process_dir/exe" 2>/dev/null || true)
    if { [ -n "$local_binary" ] && [ "$candidate_executable" = "$local_binary" ]; } \
      || [[ "$candidate_command" == *"/.mobile-bot-ui/local-ai/bin/llama-server"* ]]; then
      printf 'bootstrap_llama_stop pid=%s source=proc\n' "$candidate_pid"
      stop_runtime_pid "$candidate_pid" force
    fi
  done
}

stop_existing_host() {
  current_boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null || true)
  if [ -r "$HOST_PID_FILE" ]; then
    read -r recorded_pid recorded_boot_id <"$HOST_PID_FILE" || true
    case "${recorded_pid:-}" in
      *[!0-9]*|'') ;;
      *)
        if [ -z "${recorded_boot_id:-}" ] || [ "$recorded_boot_id" = "$current_boot_id" ]; then
          printf 'bootstrap_host_stop pid=%s source=pid_file\n' "$recorded_pid"
          stop_runtime_pid "$recorded_pid"
        fi
        ;;
    esac
  fi
  rm -f "$HOST_PID_FILE"
  for process_dir in /proc/[0-9]*; do
    [ -r "$process_dir/cmdline" ] || continue
    candidate_pid=${process_dir##*/}
    [ "$candidate_pid" = "$$" ] && continue
    candidate_command=$(tr '\000' ' ' <"$process_dir/cmdline" 2>/dev/null || true)
    if [[ "$candidate_command" == *"/.mobile-bot-ui/host.mjs"* ]]; then
      printf 'bootstrap_host_stop pid=%s source=proc\n' "$candidate_pid"
      stop_runtime_pid "$candidate_pid"
    fi
  done
}

finish_error() {
  result=$?
  failed_phase="$CURRENT_PHASE"
  printf 'bootstrap_error id=%s phase=%s exit=%s\n' "$BOOTSTRAP_ID" "$failed_phase" "$result"
  write_status failed
  printf 'bootstrap_failed phase=%s exit=%s\n' "$failed_phase" "$result" >&3
  rm -f "$ACTIVE_FILE"
  exit "$result"
}
trap finish_error EXIT

RUNTIME_SELECTION="${MOBILE_BOT_RUNTIME_SELECTION:-codex}"
case "$RUNTIME_SELECTION" in
  codex|pi-local) ;;
  *) RUNTIME_SELECTION=codex ;;
esac
printf 'bootstrap_start runtime=%s codex=%s pi=%s model=%s\n' "$RUNTIME_SELECTION" '__CODEX_VERSION__' '0.85.1' 'qwen3.5-0.8b-q4_k_m'
export DEBIAN_FRONTEND=noninteractive
RUNTIME_SELECTION="${MOBILE_BOT_RUNTIME_SELECTION:-codex}"
write_status checking

missing_packages=()
node --version >/dev/null 2>&1 || missing_packages+=(nodejs-lts)
proot --version >/dev/null 2>&1 || missing_packages+=(proot)
test -s "$PREFIX/etc/tls/cert.pem" || missing_packages+=(ca-certificates)
if [ "$RUNTIME_SELECTION" = "pi-local" ] && ! command -v curl >/dev/null 2>&1; then
  missing_packages+=(curl)
fi
if [ "$RUNTIME_SELECTION" = "pi-local" ] && ! command -v pgrep >/dev/null 2>&1; then
  missing_packages+=(procps)
fi

install_runtime_packages() {
  pkg update -y && pkg install --reinstall -y "${missing_packages[@]}"
}

if [ "${#missing_packages[@]}" -gt 0 ]; then
write_status runtime_packages
if ! install_runtime_packages; then
  # Community mirrors selected by Termux can become temporarily unavailable.
  # Retry once against the official Cloudflare-backed Termux repository. Keep
  # the official source after a successful recovery so the next update does
  # not immediately return to the mirror that just failed.
  TERMUX_MAIN_SOURCES="$PREFIX/etc/apt/sources.list"
  TERMUX_MAIN_SOURCES_BACKUP="$INSTALL_ROOT/sources.list.before-bootstrap-retry"
  if [ -f "$TERMUX_MAIN_SOURCES" ]; then
    cp "$TERMUX_MAIN_SOURCES" "$TERMUX_MAIN_SOURCES_BACKUP"
  else
    rm -f "$TERMUX_MAIN_SOURCES_BACKUP"
  fi
  printf 'bootstrap_package_mirror_retry source=packages-cf.termux.dev\n'
  write_status runtime_packages_retry
  mkdir -p "$(dirname "$TERMUX_MAIN_SOURCES")"
  printf '%s\n' \
    'deb https://packages-cf.termux.dev/apt/termux-main stable main' \
    >"$TERMUX_MAIN_SOURCES"
  if ! install_runtime_packages; then
    if [ -f "$TERMUX_MAIN_SOURCES_BACKUP" ]; then
      mv "$TERMUX_MAIN_SOURCES_BACKUP" "$TERMUX_MAIN_SOURCES"
    fi
    exit 1
  fi
  rm -f "$TERMUX_MAIN_SOURCES_BACKUP"
fi
else
  printf 'bootstrap_runtime_reused\n'
fi

if [ "$RUNTIME_SELECTION" = "pi-local" ]; then
  write_status pi
  if pi --version >/dev/null 2>&1; then
    printf 'bootstrap_pi_reused version=%s\n' '0.85.1'
  else
    npm install --global --ignore-scripts "@earendil-works/pi-coding-agent@0.85.1"
  fi
  # npm's POSIX launcher points at /usr/bin/env, which is not present on
  # Android. Replace it with a Termux-native wrapper so both bootstrap checks
  # and the Host can invoke Pi reliably.
  PI_JS="$PREFIX/lib/node_modules/@earendil-works/pi-coding-agent/dist/bundle/cli.js"
  test -f "$PI_JS"
  rm -f "$PREFIX/bin/pi"
  cat >"$PREFIX/bin/pi" <<EOF
#!$PREFIX/bin/bash
exec "$PREFIX/bin/node" "$PI_JS" "\$@"
EOF
  chmod 700 "$PREFIX/bin/pi"
  write_status local_engine
  # The normal llama-server is owned by the Host. Stop the parent before any
  # native file repair so its close handler tears the engine down first.
  stop_existing_host
  # Stop the engine before installing or repairing native files. Keeping a
  # running mmap while cp truncates a .so caused a reproducible SIGSEGV on the
  # next inference during an in-place app update.
  stop_local_ai_processes
  LOCAL_AI_ROOT="$INSTALL_ROOT/local-ai"
  LOCAL_AI_ARCHIVE="$INSTALL_ROOT/llama-b10516-bin-android-arm64.tar.gz"
  LOCAL_AI_TMP="$INSTALL_ROOT/llama-b10516-extract-$$"
  mkdir -p "$LOCAL_AI_ROOT/bin" "$LOCAL_AI_ROOT/lib"
  if [ ! -x "$LOCAL_AI_ROOT/bin/llama-server" ]; then
    rm -rf "$LOCAL_AI_TMP"
    curl -L --fail --retry 3 --connect-timeout 20 --max-time 600 \
      -o "$LOCAL_AI_ARCHIVE" \
      'https://github.com/ggml-org/llama.cpp/releases/download/b10516/llama-b10516-bin-android-arm64.tar.gz'
    printf '%s  %s\n' \
      '1d2f78c13ec4a6197506288ba0aa0853d71c1b3048ff771ea37791be7f591cc6' \
      "$LOCAL_AI_ARCHIVE" | sha256sum -c -
    mkdir -p "$LOCAL_AI_TMP"
    tar -xzf "$LOCAL_AI_ARCHIVE" -C "$LOCAL_AI_TMP"
    test -x "$LOCAL_AI_TMP/llama-b10516/llama-server"
    cp "$LOCAL_AI_TMP/llama-b10516/llama-server" "$LOCAL_AI_ROOT/bin/llama-server"
    find "$LOCAL_AI_TMP/llama-b10516" -maxdepth 1 -type f -name '*.so' -exec cp {} "$LOCAL_AI_ROOT/lib/" \;
    # llama.cpp's Android release discovers dynamically loaded CPU backends
    # beside the executable. Keep the common libraries in lib/ for normal
    # linking and copy the CPU backend variants into bin/ for discovery.
    find "$LOCAL_AI_TMP/llama-b10516" -maxdepth 1 -type f -name 'libggml-cpu*.so' -exec cp {} "$LOCAL_AI_ROOT/bin/" \;
    chmod 700 "$LOCAL_AI_ROOT/bin/llama-server"
    rm -rf "$LOCAL_AI_TMP" "$LOCAL_AI_ARCHIVE"
  else
    printf 'bootstrap_llama_reused version=%s\n' 'b10516'
  fi
  # Repair installations created by an earlier bootstrap that kept CPU
  # backends only in lib/. This is idempotent and avoids another 78 MB
  # archive download.
  find "$LOCAL_AI_ROOT/lib" -maxdepth 1 -type f -name 'libggml-cpu*.so' -exec cp {} "$LOCAL_AI_ROOT/bin/" \;
  chmod 700 "$LOCAL_AI_ROOT/bin"/libggml-cpu*.so 2>/dev/null || true
  if [ ! -s "$INSTALL_ROOT/runtime-settings.json" ]; then
    printf '{"selected":"pi-local","updatedAt":"%s"}\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >"$INSTALL_ROOT/runtime-settings.json"
    chmod 600 "$INSTALL_ROOT/runtime-settings.json"
  fi
else
  CODEX_JS="$PREFIX/lib/node_modules/@openai/codex/bin/codex.js"
  write_status codex_check
  codex_check_exit=0
  existing_codex_version=$(timeout 15 codex --version 2>/dev/null) || codex_check_exit=$?
  case "$codex_check_exit" in
    124|125|137|143)
      printf 'bootstrap_codex_check_uncertain exit=%s; refusing_reinstall\n' "$codex_check_exit"
      exit 1
      ;;
  esac
  if [[ "$codex_check_exit" == 0 && "$existing_codex_version" =~ ^codex-cli\ [0-9]+\.[0-9]+\.[0-9]+ ]]; then
    printf 'bootstrap_codex_reused version=%s\n' "$existing_codex_version"
  else
  write_status codex
  npm install --global "@openai/codex@__CODEX_VERSION__"
  # npm running on Android skips the linux-only optional dependency even though
  # the Codex launcher explicitly supports process.platform === "android".
  # Install the statically linked ARM64 musl package and keep it pinned.
  npm install --global --force \
    "@openai/codex-linux-arm64@npm:@openai/codex@__CODEX_VERSION__-linux-arm64"

  printf '%s\n' 'nameserver 1.1.1.1' 'nameserver 8.8.8.8' >"$RESOLV_FILE"
  chmod 600 "$RESOLV_FILE"
  rm -f "$PREFIX/bin/codex"
  cat >"$PREFIX/bin/codex" <<EOF
#!$PREFIX/bin/bash
export SSL_CERT_FILE="$PREFIX/etc/tls/cert.pem"
exec "$PREFIX/bin/proot" -b "$RESOLV_FILE:/etc/resolv.conf" \
  "$PREFIX/bin/node" "$CODEX_JS" "\$@"
EOF
  chmod 700 "$PREFIX/bin/codex"
  fi
  if [ ! -s "$INSTALL_ROOT/runtime-settings.json" ]; then
    printf '{"selected":"codex","updatedAt":"%s"}\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >"$INSTALL_ROOT/runtime-settings.json"
    chmod 600 "$INSTALL_ROOT/runtime-settings.json"
  fi
fi
write_status mobile_bot_files

# Keep the one-time code inside the login process. The helper streams Codex
# output through a private FIFO, extracts the code in memory and opens the
# verification URL on the phone. It never uses the clipboard or a log file.
cat >"$LOGIN_HELPER" <<'LOGIN_EOF'
#!/data/data/com.termux/files/usr/bin/bash
set -u

PREFIX=/data/data/com.termux/files/usr
INSTALL_ROOT="$HOME/.mobile-bot-ui"
LOGIN_FIFO="$INSTALL_ROOT/codex-login.$$.fifo"
LOGIN_PID=''

cleanup() {
  rm -f "$LOGIN_FIFO"
}
trap cleanup EXIT INT TERM

mkfifo "$LOGIN_FIFO"
chmod 600 "$LOGIN_FIFO"
"$PREFIX/bin/codex" login --device-auth >"$LOGIN_FIFO" 2>&1 &
LOGIN_PID=$!

browser_opened=false
while IFS= read -r line; do
  printf '%s\n' "$line"
  if [ "$browser_opened" = false ]; then
    clean_line=$(printf '%s' "$line" | sed $'s/\\033\\[[0-9;]*m//g')
    code=$(printf '%s' "$clean_line" | grep -Eo '[A-Z0-9]{4}-[A-Z0-9]{5}' | head -n 1 || true)
    if [ -n "$code" ]; then
      /system/bin/am start \
        -a android.intent.action.VIEW \
        -d "https://auth.openai.com/codex/device?user_code=$code" \
        >/dev/null 2>&1 || true
      browser_opened=true
    fi
  fi
done <"$LOGIN_FIFO"

wait "$LOGIN_PID"
LOGIN_EOF
chmod 700 "$LOGIN_HELPER"

install_staged_file() {
  source_file="$STAGE_ROOT/$1"
  target_file="$2"
  mode="$3"
  test -f "$source_file"
  cp "$source_file" "$target_file.tmp"
  chmod "$mode" "$target_file.tmp"
  mv "$target_file.tmp" "$target_file"
}

install_staged_file host.mjs "$HOST_FILE" 700
install_staged_file generatedSkills.js "$PREDEFINED_SKILLS_FILE" 600
install_staged_file localAi.js "$LOCAL_AI_MODULE_FILE" 600
install_staged_file piRuntime.js "$PI_RUNTIME_MODULE_FILE" 600
install_staged_file themePackages.js "$INSTALL_ROOT/themePackages.js" 600
install_staged_file locale.js "$INSTALL_ROOT/locale.js" 600
install_staged_file skillWorkshop.js "$INSTALL_ROOT/skillWorkshop.js" 600
install_staged_file phone-client.mjs "$PHONE_CLIENT_FILE" 700
install_staged_file automation-client.mjs "$AUTOMATION_CLIENT_FILE" 700
install_staged_file device-token "$DEVICE_TOKEN_FILE" 600
rm -rf "$INSTALL_ROOT/bootstrap-stage"

# Stop any earlier same-UID Host, including a process restored from an AVD
# snapshot whose ps representation may not be stable yet. The local path calls
# these before native file writes too; repeating them here is idempotent.
stop_existing_host
stop_local_ai_processes

write_status checking
node --version
if [ "$RUNTIME_SELECTION" = "pi-local" ]; then
  pi --version
  export LD_LIBRARY_PATH="$INSTALL_ROOT/local-ai/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
  "$INSTALL_ROOT/local-ai/bin/llama-server" --version >/dev/null
else
  codex --version
fi
write_status complete
rm -f "$ACTIVE_FILE"
trap - EXIT
printf 'bootstrap_ok host=__HOST_VERSION__ runtime=%s\n' "$RUNTIME_SELECTION" >&3
