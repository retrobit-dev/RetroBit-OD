#!/usr/bin/env bash
# Jalankan task Gradle; kalau gagal, keluarkan baris error sebagai annotation
# (bisa dibaca lewat API check-run annotations) dan simpan log lengkap ke /tmp.
set -uo pipefail

task="$1"
log="/tmp/gradle-${task}.log"

echo "==> gradle ${task}"
if gradle --no-daemon --console=plain --stacktrace "${task}" 2>&1 | tee "${log}"; then
  echo "==> ${task} OK"
  exit 0
fi

echo "::group::baris error"
grep -nE "^e: |^w: .*(unresolved|expecting|expected)|error:|FAILURE:|Execution failed|Caused by:" "${log}" \
  | head -n 40 \
  | while IFS= read -r line; do
      esc="${line//%/%25}"
      echo "::error::${esc:0:900}"
    done
echo "::endgroup::"
exit 1
