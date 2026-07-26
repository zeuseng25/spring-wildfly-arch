#!/usr/bin/env bash
#
# Build + Deploy: uygulamayı paketler (WAR) ve WildFly'a deploy eder.
#
# Kullanım:
#   ./scripts/deploy.sh             # build + deploy
#   ./scripts/deploy.sh --no-build  # mevcut target/*.war'ı deploy et (build atla)
#   ./scripts/deploy.sh --start     # WildFly ayakta değilse standalone.sh ile başlat
#   (bayraklar birlikte/sıra bağımsız kullanılabilir: --no-build --start)
#
# WildFly konumu env ile override edilebilir:
#   WILDFLY_HOME=/path/to/wildfly ./scripts/deploy.sh
#
set -euo pipefail

# --- Ayarlar ---
WILDFLY_HOME="${WILDFLY_HOME:-/Users/omer/workspaces/intellij/wildfy-27/wildfly-27.0.1.Final}"
WAR_NAME="spring-wildfly-arch.war"            # deploy edilen sabit isim (context: /spring-wildfly-arch)
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_DIR="${WILDFLY_HOME}/standalone/deployments"

cd "${PROJECT_DIR}"

# --- Argümanlar (sıra bağımsız) ---
NO_BUILD=0
START=0
for arg in "$@"; do
    case "${arg}" in
        --no-build) NO_BUILD=1 ;;
        --start)    START=1 ;;
        *) echo "HATA: bilinmeyen argüman: ${arg}" >&2
           echo "      Kullanım: ./scripts/deploy.sh [--no-build] [--start]" >&2
           exit 1 ;;
    esac
done

# --- Ön kontroller ---
if [[ ! -d "${DEPLOY_DIR}" ]]; then
    echo "HATA: WildFly deployments dizini yok: ${DEPLOY_DIR}" >&2
    echo "      WILDFLY_HOME doğru mu? (şu an: ${WILDFLY_HOME})" >&2
    exit 1
fi

# WAR ince (lib'siz) olduğundan çalışması com.zeus module'üne bağlı.
if [[ ! -f "${WILDFLY_HOME}/modules/com/zeus/main/module.xml" ]]; then
    echo "UYARI: com.zeus module bulunamadı. Önce module'ü kurun (platform scripti):" >&2
    echo "       ( cd ../zeus-fw && ./scripts/install-zeus-module.sh )   (sonra WildFly'ı yeniden başlatın)" >&2
fi

# --- 1) Build ---
if [[ "${NO_BUILD}" != "1" ]]; then
    echo ">> Build: ./maven.sh clean package"
    ./maven.sh clean package
else
    echo ">> Build atlandı (--no-build)"
fi

# --- 1b) Module kapsam denetimi (PLATFORM scripti) ---
# App'in runtime bağımlılıkları com.zeus module'de var mı? Yoksa WildFly'da NoClassDefFound
# olmadan ÖNCE burada dur. (SKIP_COVERAGE=1 ile atlanabilir.) Module yoksa zaten yukarıda uyarıldı.
COVERAGE_SCRIPT="${PROJECT_DIR}/../zeus-fw/scripts/verify-module-coverage.sh"
if [[ "${SKIP_COVERAGE:-0}" != "1" && -f "${WILDFLY_HOME}/modules/com/zeus/main/module.xml" && -x "${COVERAGE_SCRIPT}" ]]; then
    "${COVERAGE_SCRIPT}" "${PROJECT_DIR}"
fi

# --- WAR'ı bul (glob; *.war.original zaten *.war ile eşleşmez) ---
WAR_FILE=""
for f in target/*.war; do
    [[ -f "${f}" ]] && { WAR_FILE="${f}"; break; }
done
if [[ -z "${WAR_FILE}" ]]; then
    echo "HATA: target/ altında WAR bulunamadı. Önce build çalıştırın." >&2
    exit 1
fi
echo ">> WAR: ${WAR_FILE}"

# --- 2) Deploy: eski marker'ları temizle, kopyala ---
rm -f "${DEPLOY_DIR}/${WAR_NAME}.deployed" \
      "${DEPLOY_DIR}/${WAR_NAME}.failed" \
      "${DEPLOY_DIR}/${WAR_NAME}.isdeploying" 2>/dev/null || true
cp "${WAR_FILE}" "${DEPLOY_DIR}/${WAR_NAME}"
echo ">> Kopyalandı: ${DEPLOY_DIR}/${WAR_NAME}"

# --- 3) WildFly ayakta mı? ---
WF_DOWN=0
if ! curl -s -o /dev/null --max-time 2 http://localhost:9990 && ! pgrep -f "jboss.home.dir=${WILDFLY_HOME}" >/dev/null 2>&1; then
    WF_DOWN=1
fi

if [[ "${WF_DOWN}" == "1" ]]; then
    if [[ "${START}" == "1" ]]; then
        # --start: ayakta WildFly yoksa standalone.sh'ı arka planda başlat
        STANDALONE="${WILDFLY_HOME}/bin/standalone.sh"
        if [[ ! -x "${STANDALONE}" ]]; then
            echo "HATA: standalone.sh bulunamadı/çalıştırılamıyor: ${STANDALONE}" >&2
            exit 1
        fi
        BOOT_LOG="${WILDFLY_HOME}/standalone/log/deploy-start-console.out"
        echo ">> WildFly çalışmıyor → başlatılıyor (--start): ${STANDALONE}"
        nohup "${STANDALONE}" > "${BOOT_LOG}" 2>&1 &
        echo ">> standalone.sh başlatıldı (PID $!). Boot log: ${BOOT_LOG}"
        # marker bekleme döngüsüne düş (boot + deploy scanner WAR'ı alacak)
    else
        echo ">> WildFly çalışmıyor görünüyor. WAR deployments'a kondu; sunucu açılınca deploy edilecek."
        echo "   Başlatmak için: ${WILDFLY_HOME}/bin/standalone.sh   (veya: ./scripts/deploy.sh --start)"
        exit 0
    fi
fi

# Soğuk başlatmada (--start) boot + deploy daha uzun sürebilir → bekleme süresini uzat
WAIT_ITERS=60
[[ "${WF_DOWN}" == "1" && "${START}" == "1" ]] && WAIT_ITERS=150
echo ">> Deploy bekleniyor (en çok $((WAIT_ITERS * 2)) sn)..."
for _ in $(seq 1 "${WAIT_ITERS}"); do
    if [[ -f "${DEPLOY_DIR}/${WAR_NAME}.deployed" ]]; then
        echo "✅ DEPLOY BAŞARILI: http://localhost:8080/spring-wildfly-arch/"
        exit 0
    fi
    if [[ -f "${DEPLOY_DIR}/${WAR_NAME}.failed" ]]; then
        echo "❌ DEPLOY BAŞARISIZ. Detay:" >&2
        cat "${DEPLOY_DIR}/${WAR_NAME}.failed" >&2
        exit 1
    fi
    sleep 2
done

echo "⚠ Zaman aşımı: deploy marker görünmedi. WildFly loglarına bakın:" >&2
echo "   ${WILDFLY_HOME}/standalone/log/server.log" >&2
exit 1
