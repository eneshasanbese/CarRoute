#!/usr/bin/env bash
# OSRM verisini bir kez hazırlar. Docker Desktop çalışıyor olmalı.
#
# İmaj tek başına yetmez: imaj motordur, harita verisi içinde yoktur. Bu script
# OSM eksraktını indirir ve motorun okuyabileceği .osrm dosyalarına çevirir.
#
# ÖNEMLİ: extract / partition / customize / routed adımlarının HEPSİ aynı imajla
# çalışmalı. OSRM'in ürettiği .osrm dosya biçimi sürüme bağlı; farklı sürümlerle
# hazırlanan veriyi osrm-routed "unsupported file version" diyerek reddeder.
# Elinizde hazır bir imaj varsa adını OSRM_IMAGE ile verin:
#
#   OSRM_IMAGE=osrm/osrm-backend:v5.22.0 bash scripts/prepare-osrm.sh
#
# Türkiye eksraktı ~500 MB indirir, işlerken birkaç GB disk ve RAM ister.
set -euo pipefail

OSRM_IMAGE="${OSRM_IMAGE:-osrm/osrm-backend}"
OSRM_DATASET="${OSRM_DATASET:-turkey-latest}"
PBF_URL="${PBF_URL:-https://download.geofabrik.de/europe/${OSRM_DATASET}.osm.pbf}"

DATA_DIR="$(cd "$(dirname "$0")/.." && pwd)/osrm/data"
PBF_NAME="${OSRM_DATASET}.osm.pbf"

mkdir -p "$DATA_DIR"

# Git Bash / MSYS altında Docker'a Windows biçiminde yol vermek gerekir; ayrıca
# MSYS_NO_PATHCONV olmadan "/data" gibi container içi yollar da Windows yoluna
# çevrilip bozulur.
if command -v cygpath >/dev/null 2>&1; then
  MOUNT_DIR="$(cygpath -w "$DATA_DIR")"
  export MSYS_NO_PATHCONV=1
  export MSYS2_ARG_CONV_EXCL='*'
else
  MOUNT_DIR="$DATA_DIR"
fi

echo "==> İmaj    : $OSRM_IMAGE"
echo "==> Veri seti: $OSRM_DATASET"
echo "==> Dizin   : $MOUNT_DIR"

if ! docker image inspect "$OSRM_IMAGE" >/dev/null 2>&1; then
  echo "==> İmaj yerelde yok, indiriliyor..."
  docker pull "$OSRM_IMAGE"
fi

echo "==> OSRM sürümü:"
docker run --rm "$OSRM_IMAGE" osrm-routed --version || true

cd "$DATA_DIR"

if [ ! -f "$PBF_NAME" ]; then
  echo "==> OSM eksraktı indiriliyor (~500 MB): $PBF_URL"
  curl -L -o "$PBF_NAME" "$PBF_URL"
else
  echo "==> $PBF_NAME zaten var, indirme atlanıyor."
fi

run_osrm() {
  docker run --rm -t -v "${MOUNT_DIR}:/data" "$OSRM_IMAGE" "$@"
}

echo "==> osrm-extract (en uzun adım, 10-30 dk sürebilir)"
run_osrm osrm-extract -p /opt/car.lua "/data/${PBF_NAME}"

echo "==> osrm-partition"
run_osrm osrm-partition "/data/${OSRM_DATASET}.osrm"

echo "==> osrm-customize"
run_osrm osrm-customize "/data/${OSRM_DATASET}.osrm"

# docker compose aynı imajı ve veri adını kullansın diye kaydediyoruz.
cat > "$(dirname "$DATA_DIR")/osrm.env" <<ENV
OSRM_IMAGE=${OSRM_IMAGE}
OSRM_DATASET=${OSRM_DATASET}
ENV

echo
echo "Hazır. Şimdi:"
echo "  docker compose --env-file osrm/osrm.env up -d osrm"
