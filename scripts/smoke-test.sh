#!/usr/bin/env bash
#
# Smoke test: deploy edilmiş uygulamayı gerçek HTTP üzerinden uçtan uca doğrular.
#
# Deploy'un başarılı olması ("context açıldı") davranışın doğru olduğu anlamına gelmez;
# bu script Spring MVC + Jackson + springdoc + Bean Validation + transaction +
# stored procedure (JNDI datasource) + GlobalExceptionHandler zincirinin tamamını
# kısa bir CRUD senaryosuyla çalıştırır. Staging gate'inin uygulama ayağıdır
# (platform tarafı: ../zeus-fw/scripts/verify-staging.sh — bkz. gelistirmeler/14-smoke-test.md).
#
# Kullanım:
#   ./scripts/smoke-test.sh                                            # lokal WildFly (8080)
#   ./scripts/smoke-test.sh http://localhost:8180/spring-wildfly-arch  # staging (port offset)
#   SERVER_LOG=/path/server.log ./scripts/smoke-test.sh <url>
#     (verilirse test sırasında loga düşen yeni ERROR / *Error satırları da denetlenir —
#      istek atılmayan yollardaki sessiz linkage sorunlarını yakalar)
#
# Çıkış kodu: 0 = tüm adımlar geçti, 1 = en az bir adım başarısız.
#
set -euo pipefail

BASE_URL="${1:-http://localhost:8080/spring-wildfly-arch}"
BASE_URL="${BASE_URL%/}"
API="${BASE_URL}/api/products"

BODY="$(mktemp)"
trap 'rm -f "${BODY}"' EXIT

FAIL=0
STEP=0
HTTP_STATUS=""

# HTTP isteği at: req <METHOD> <URL> [JSON gövde] → durum kodu HTTP_STATUS'a, gövde BODY dosyasına
req() {
    local method="$1" url="$2" data="${3:-}"
    local args=(-s -o "${BODY}" -w '%{http_code}' -X "${method}" --max-time 15)
    [[ -n "${data}" ]] && args+=(-H 'Content-Type: application/json' -d "${data}")
    HTTP_STATUS="$(curl "${args[@]}" "${url}" 2>/dev/null || echo "000")"
}

# Sonucu doğrula: check <beklenen-status> <açıklama> [gövdede aranan ERE deseni]
check() {
    local expected="$1" desc="$2" needle="${3:-}"
    STEP=$((STEP + 1))
    if [[ "${HTTP_STATUS}" != "${expected}" ]]; then
        echo "❌ [${STEP}] ${desc} — beklenen HTTP ${expected}, gelen ${HTTP_STATUS}"
        echo "     gövde: $(head -c 300 "${BODY}" | tr '\n' ' ')"
        FAIL=1
        return 0
    fi
    if [[ -n "${needle}" ]] && ! grep -Eq "${needle}" "${BODY}"; then
        echo "❌ [${STEP}] ${desc} — HTTP ${expected} ama gövdede '${needle}' bulunamadı"
        echo "     gövde: $(head -c 300 "${BODY}" | tr '\n' ' ')"
        FAIL=1
        return 0
    fi
    echo "✅ [${STEP}] ${desc}"
}

echo ">> Smoke test: ${BASE_URL}"

# SERVER_LOG verildiyse başlangıç satır sayısını al (test sonrası yalnız YENİ satırlar taranır)
LOG_START=0
if [[ -n "${SERVER_LOG:-}" && -r "${SERVER_LOG}" ]]; then
    LOG_START="$(wc -l < "${SERVER_LOG}" | tr -d ' ')"
fi

# --- 1) OpenAPI belgesi: Spring MVC + Jackson + springdoc zinciri tek istekte ---
req GET "${BASE_URL}/v3/api-docs"
check 200 "GET /v3/api-docs (OpenAPI)" '"openapi"'

# --- 2) Liste: repository → stored procedure → REF CURSOR yolu ---
req GET "${API}"
check 200 "GET /api/products (liste)"

# --- 3) CRUD round-trip (validation, transaction, SP çağrıları, 404 ProblemDetail) ---
NAME="smoke-$(date +%s)-$$"

req POST "${API}" "{\"name\":\"${NAME}\",\"price\":9.99,\"stock\":5}"
check 201 "POST /api/products (create)" '"id"'

ID="$(sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "${BODY}" | head -n1)"
if [[ -z "${ID}" ]]; then
    # id alınamadıysa kalan CRUD adımlarının anlamı yok — erken çık
    echo "❌ create yanıtından id çıkarılamadı; kalan CRUD adımları atlandı." >&2
    exit 1
fi

req GET "${API}/${ID}"
check 200 "GET /api/products/${ID} (okuma)" "${NAME}"

req PUT "${API}/${ID}" "{\"name\":\"${NAME}-upd\",\"price\":19.99,\"stock\":7}"
check 200 "PUT /api/products/${ID} (güncelleme)" "${NAME}-upd"

# Geçersiz gövde: Bean Validation → GlobalExceptionHandler → 400
req POST "${API}" '{"name":"","price":-1}'
check 400 "POST geçersiz gövde (validation → 400)"

req DELETE "${API}/${ID}"
check 204 "DELETE /api/products/${ID} (silme)"

# Silinen kayıt: ResourceNotFoundException → ProblemDetail 404
req GET "${API}/${ID}"
check 404 "GET silinen kayıt (404 ProblemDetail)" '"status"[[:space:]]*:[[:space:]]*404'

# --- 4) Log taraması (SERVER_LOG verildiyse): test sırasında düşen yeni hatalar ---
if [[ -n "${SERVER_LOG:-}" && -r "${SERVER_LOG}" ]]; then
    STEP=$((STEP + 1))
    NEW_ERRORS="$(tail -n "+$((LOG_START + 1))" "${SERVER_LOG}" \
        | grep -E ' ERROR |(NoClassDefFound|NoSuchMethod|NoSuchField|AbstractMethod|Linkage)Error' || true)"
    if [[ -n "${NEW_ERRORS}" ]]; then
        echo "❌ [${STEP}] server.log taraması — test sırasında yeni hata satırları düştü:"
        echo "${NEW_ERRORS}" | head -n 10 | sed 's/^/     /'
        FAIL=1
    else
        echo "✅ [${STEP}] server.log taraması — yeni ERROR/LinkageError yok"
    fi
fi

echo ""
if [[ "${FAIL}" != "0" ]]; then
    echo "❌ SMOKE TEST BAŞARISIZ: ${BASE_URL}" >&2
    exit 1
fi
echo "✅ SMOKE TEST GEÇTİ: ${BASE_URL}"