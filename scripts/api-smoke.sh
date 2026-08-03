#!/usr/bin/env bash
#
# 一键跑通 M2 鉴权 + M3 商品/库存/下单 全流程，并顺带验证主要错误码。
#
# 用法：
#   bash scripts/api-smoke.sh              # 打到 localhost:8080
#   BASE=http://127.0.0.1:9090 bash scripts/api-smoke.sh
#
# 前置：docker compose up -d && cd java-learn-app && mvn spring-boot:run
#
set -u

BASE="${BASE:-http://localhost:8080}"
# 每次跑用带时间戳的用户名，避免 2001 用户名已存在
USER="smoke_$(date +%s)"
PASS="123456"

cyan()  { printf '\033[36m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
step()  { echo; cyan "── $* ────────────────────────────────"; }

# 从 {"code":0,...,"data":"xxx",...} 中取字符串 data（不依赖 jq）
pick_str_data() { sed -n 's/.*"data":"\([^"]*\)".*/\1/p'; }
# 从返回体中取第一个 "id":123
pick_id()       { sed -n 's/.*"id":\([0-9]*\).*/\1/p'; }

# ---------------------------------------------------------------- 0. 探活
# 注意：Git Bash 下 curl 的 `-o /dev/null` 会返回 exit 23（写目标失败），
# 所以这里靠"响应体是否为空"判断存活，不用 -f/-o。
step "0. 探活 GET /demo/hello（公开接口）"
HEALTH=$(curl -s --max-time 5 "$BASE/demo/hello" || true)
if [ -z "$HEALTH" ]; then
  echo "应用没起来：$BASE 无响应。先执行 cd java-learn-app && mvn spring-boot:run" >&2
  exit 1
fi
echo "$HEALTH"

# ---------------------------------------------------------------- 1. 鉴权
step "1. 注册 POST /auth/register  (user=$USER)"
curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\",\"role\":\"USER\"}"; echo

step "2. 登录 POST /auth/login → 取 JWT"
TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | pick_str_data)
if [ -z "$TOKEN" ]; then echo "登录失败，拿不到 token" >&2; exit 1; fi
green "TOKEN = ${TOKEN:0:32}... (len=${#TOKEN})"

AUTH=(-H "Authorization: Bearer $TOKEN")
JSON=(-H "Content-Type: application/json")

# ---------------------------------------------------------------- 2. 商品 CRUD
step "3. 新建商品 POST /products"
RESP=$(curl -s -X POST "$BASE/products" "${JSON[@]}" "${AUTH[@]}" \
  -d '{"name":"机械键盘","price":399.00,"description":"87键 青轴"}')
echo "$RESP"
PID=$(echo "$RESP" | pick_id)
green "PID = $PID"

step "4. 商品详情 GET /products/$PID"
curl -s "$BASE/products/$PID" "${AUTH[@]}"; echo

step "5. 更新商品 PUT /products/$PID"
curl -s -X PUT "$BASE/products/$PID" "${JSON[@]}" "${AUTH[@]}" \
  -d '{"name":"机械键盘 Pro","price":459.00,"description":"87键 茶轴"}'; echo

step "6. 商品列表 GET /products"
curl -s "$BASE/products" "${AUTH[@]}"; echo

# ---------------------------------------------------------------- 3. 库存 & 下单
step "7. 初始化库存 POST /products/$PID/stock  (total=10)"
curl -s -X POST "$BASE/products/$PID/stock" "${JSON[@]}" "${AUTH[@]}" \
  -d '{"total":10}'; echo

step "8. 乐观锁下单 POST /products/$PID/order  (quantity=2)"
curl -s -X POST "$BASE/products/$PID/order" "${JSON[@]}" "${AUTH[@]}" \
  -d '{"userId":1,"quantity":2}'; echo

step "9. 悲观锁下单 POST /products/$PID/order  (quantity=3, FOR UPDATE)"
curl -s -X POST "$BASE/products/$PID/order" "${JSON[@]}" "${AUTH[@]}" \
  -d '{"userId":1,"quantity":3,"lockType":"PESSIMISTIC"}'; echo

step "10. 查库存 GET /products/$PID/stock  (期望 available=5, version=2)"
curl -s "$BASE/products/$PID/stock" "${AUTH[@]}"; echo

# ---------------------------------------------------------------- 4. 错误码
step "11. 错误码验证"
printf '  2003 未登录        : '; curl -s "$BASE/products"; echo
printf '  2004 无权访问      : '; curl -s "$BASE/admin/panel" "${AUTH[@]}"; echo
printf '  2001 用户名已存在  : '; curl -s -X POST "$BASE/auth/register" "${JSON[@]}" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\",\"role\":\"USER\"}"; echo
printf '  2002 密码错误      : '; curl -s -X POST "$BASE/auth/login" "${JSON[@]}" \
  -d "{\"username\":\"$USER\",\"password\":\"wrong\"}"; echo
printf '  3001 商品不存在    : '; curl -s "$BASE/products/999999" "${AUTH[@]}"; echo
printf '  3003 库存已初始化  : '; curl -s -X POST "$BASE/products/$PID/stock" "${JSON[@]}" "${AUTH[@]}" \
  -d '{"total":5}'; echo
printf '  3004 库存不足      : '; curl -s -X POST "$BASE/products/$PID/order" "${JSON[@]}" "${AUTH[@]}" \
  -d '{"userId":1,"quantity":999}'; echo

# ---------------------------------------------------------------- 5. 并发冲突
step "12. 并发抢购（1 件库存 / 10 并发），观察 3005 乐观锁冲突"
PID2=$(curl -s -X POST "$BASE/products" "${JSON[@]}" "${AUTH[@]}" \
  -d '{"name":"限量商品","price":9.9,"description":"只有1件"}' | pick_id)
curl -s -X POST "$BASE/products/$PID2/stock" "${JSON[@]}" "${AUTH[@]}" \
  -d '{"total":1}' > /dev/null

# 结果落到 target/ 下（已被 .gitignore 忽略，mvn clean 会带走，不用手动删）
TMP="$(cd "$(dirname "$0")/.." && pwd)/java-learn-app/target/smoke-$$"
mkdir -p "$TMP"
for ((i = 1; i <= 10; i++)); do
  curl -s -X POST "$BASE/products/$PID2/order" "${JSON[@]}" "${AUTH[@]}" \
    -d '{"userId":1,"quantity":1}' > "$TMP/$i.json" &
done
wait
count() { grep -l "$1" "$TMP"/*.json 2>/dev/null | wc -l | tr -d ' '; }
echo "  成功 $(count '"code":0') 单 / 乐观锁冲突(3005) $(count '"code":3005') 单 / 库存不足(3004) $(count '"code":3004') 单"
echo "  明细目录：$TMP"

step "13. 删除商品 DELETE /products/$PID2"
curl -s -X DELETE "$BASE/products/$PID2" "${AUTH[@]}"; echo

echo
green "全流程跑完。商品 PID=$PID 保留在库里，可继续手工调试。"
