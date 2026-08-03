# API curl 速查（M1 / M2 / M3）

> Base URL：`http://localhost:8080`
> 统一返回体：`{"code":0,"message":"success","data":...,"timestamp":...}`，`code=0` 为成功。
> 下面所有命令都在 **Git Bash / Linux / macOS** 下可直接复制执行。Windows CMD/PowerShell 见文末「Windows 注意事项」。

## 0. 前置

```bash
# 起基础设施（MySQL / Redis / RabbitMQ）
docker compose up -d

# 起应用
sh .mvnlocal.sh -B install -DskipTests
cd java-learn-app && sh ../.mvnlocal.sh -B spring-boot:run
```

一键跑通全流程可直接执行：`bash scripts/api-smoke.sh`

---

## 1. 公开接口（无需登录）

拦截器放行 `/auth/**` 与 `/demo/**`。

### 健康探针 / M1 演示

```bash
curl -s http://localhost:8080/demo/hello
```

### 注册

`role` 可传 `USER` 或 `ADMIN`，不传默认 `USER`。

```bash
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"123456","role":"USER"}'
```

### 登录（拿 token）

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"123456"}'
```

返回：`{"code":0,"message":"success","data":"eyJhbGciOiJIUzUxMiJ9...","timestamp":...}`
`data` 就是 JWT，有效期 1 小时。

### 把 token 存进环境变量（后续命令都要用）

本机没装 `jq`，用 `sed` 提取即可：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"123456"}' \
  | sed -n 's/.*"data":"\([^"]*\)".*/\1/p')

echo "$TOKEN"
```

装了 `jq` 的话更干净：`... | jq -r .data`

---

## 2. 商品 CRUD（M3，需登录）

以下全部需要 `Authorization: Bearer $TOKEN`。

### 新建商品

```bash
curl -s -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"机械键盘","price":399.00,"description":"87键 青轴"}'
```

返回里的 `data.id` 就是商品 ID，后面用它。存起来：

```bash
PID=$(curl -s -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"机械键盘","price":399.00,"description":"87键 青轴"}' \
  | sed -n 's/.*"id":\([0-9]*\).*/\1/p')

echo "PID=$PID"
```

### 商品列表

```bash
curl -s http://localhost:8080/products -H "Authorization: Bearer $TOKEN"
```

### 商品详情

```bash
curl -s http://localhost:8080/products/$PID -H "Authorization: Bearer $TOKEN"
```

### 更新商品

```bash
curl -s -X PUT http://localhost:8080/products/$PID \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"机械键盘 Pro","price":459.00,"description":"87键 茶轴"}'
```

### 删除商品

```bash
curl -s -X DELETE http://localhost:8080/products/$PID -H "Authorization: Bearer $TOKEN"
```

---

## 3. 库存（M3）

### 初始化库存（`available = total`，只能初始化一次）

```bash
curl -s -X POST http://localhost:8080/products/$PID/stock \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"total":10}'
```

### 查询库存（可观察 `available` 与 `version` 变化）

```bash
curl -s http://localhost:8080/products/$PID/stock -H "Authorization: Bearer $TOKEN"
```

---

## 4. 下单扣库存（M3 核心）

`lockType` 可选，缺省 `OPTIMISTIC`。

### 乐观锁下单（默认，靠 `version` 字段 CAS）

```bash
curl -s -X POST http://localhost:8080/products/$PID/order \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":1,"quantity":2}'
```

### 悲观锁下单（`SELECT ... FOR UPDATE` 行锁）

```bash
curl -s -X POST http://localhost:8080/products/$PID/order \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":1,"quantity":3,"lockType":"PESSIMISTIC"}'
```

每次下单成功后 `stock.available` 减少、`version` +1，可用上面的查库存接口验证。

### 并发压一把，观察乐观锁冲突（code 3005）

10 个并发同时抢 1 件库存，只会有 1 个成功，其余报 `库存并发冲突，请重试`：

```bash
# 先造一个只有 1 件库存的商品
PID2=$(curl -s -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"限量商品","price":9.9,"description":"只有1件"}' \
  | sed -n 's/.*"id":\([0-9]*\).*/\1/p')

curl -s -X POST http://localhost:8080/products/$PID2/stock \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"total":1}' > /dev/null

for ((i = 1; i <= 10; i++)); do
  curl -s -X POST http://localhost:8080/products/$PID2/order \
    -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
    -d '{"userId":1,"quantity":1}' &
done
wait
```

> 部分 Git Bash 环境里没有 `seq`，所以这里用 bash 自带的 `((...))` 循环。
> 同理，Git Bash 下 `curl -o /dev/null` 会返回 exit 23（写目标失败），要静默丢弃输出请用 shell 重定向 `> /dev/null`。

---

## 5. 鉴权相关（M2）

### 管理员专属接口（USER 角色访问会被拒）

```bash
curl -s http://localhost:8080/admin/panel -H "Authorization: Bearer $TOKEN"
# USER 角色 → {"code":2004,"message":"无权访问该资源"}
```

拿 ADMIN token 再试：

```bash
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"boss","password":"123456","role":"ADMIN"}'

ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"boss","password":"123456"}' \
  | sed -n 's/.*"data":"\([^"]*\)".*/\1/p')

curl -s http://localhost:8080/admin/panel -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## 6. 错误码速查（实测返回）

| 场景 | 命令要点 | 返回 |
| --- | --- | --- |
| 不带 token 访问 `/products` | 去掉 `Authorization` 头 | `2003 未登录或登录已过期` |
| USER 访问 `/admin/panel` | 用 USER token | `2004 无权访问该资源` |
| 用户名重复注册 | 重复调 `/auth/register` | `2001 用户名已存在` |
| 密码错误 | 改 `/auth/login` 的 password | `2002 用户名或密码错误` |
| 商品不存在 | `GET /products/999999` | `3001 商品不存在` |
| 未初始化库存就下单 | 新商品直接下单 | `3002 库存未初始化` |
| 重复初始化库存 | 对同一商品调两次 `/stock` | `3003 库存已初始化` |
| 库存不足 | `quantity` 超过 `available` | `3004 库存不足` |
| 乐观锁冲突 | 并发下单同一商品 | `3005 库存并发冲突，请重试` |

对应的一句话复现命令：

```bash
curl -s http://localhost:8080/products                                            # 2003
curl -s http://localhost:8080/products/999999 -H "Authorization: Bearer $TOKEN"   # 3001
curl -s -X POST http://localhost:8080/products/$PID/order \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"userId":1,"quantity":999}'                                                # 3004
curl -s -X POST http://localhost:8080/products/$PID/stock \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"total":5}'                                                                # 3003（第二次调）
```

---

## Windows 注意事项

- **推荐用 Git Bash 执行**，上面的命令原样可用。
- **CMD / PowerShell** 下单引号不生效，JSON 里的双引号要转义，很难写。变通办法：把 JSON 存成文件再 `-d @file.json`：

  ```powershell
  curl -s -X POST http://localhost:8080/products `
    -H "Content-Type: application/json" `
    -H "Authorization: Bearer $env:TOKEN" `
    -d '@product.json'
  ```

- PowerShell 里 `curl` 默认是 `Invoke-WebRequest` 的别名，要用真 curl 得写 `curl.exe`。
