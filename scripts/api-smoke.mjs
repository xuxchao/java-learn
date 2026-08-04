#!/usr/bin/env node
/**
 * 一键跑通 M2 鉴权 + M3 商品/库存/下单 全流程，并顺带验证主要错误码。
 *
 * 用法：
 *   node scripts/api-smoke.mjs
 *   node scripts/api-smoke.mjs --base http://127.0.0.1:9090
 *   BASE=http://127.0.0.1:9090 node scripts/api-smoke.mjs
 *
 * 前置：docker compose up -d && cd java-learn-app && mvn spring-boot:run
 * 依赖：Node >= 18（用内置 fetch，零 npm 依赖，不需要 npm install）
 *
 * 退出码：0 = 全部符合预期；1 = 有断言失败（可直接接进 CI）
 */

// ---------------------------------------------------------------- 配置
const argv = process.argv.slice(2);
const argBase = (() => {
  const i = argv.indexOf('--base');
  return i >= 0 ? argv[i + 1] : null;
})();
const BASE = (argBase || process.env.BASE || 'http://localhost:8080').replace(/\/+$/, '');

// 每次跑用带时间戳的用户名，避免 2001 用户名已存在
const USER = `smoke_${Math.floor(Date.now() / 1000)}`;
const PASS = '123456';

// ---------------------------------------------------------------- 输出
const COLOR = process.stdout.isTTY && !process.env.NO_COLOR;
const c = (code, s) => (COLOR ? `\x1b[${code}m${s}\x1b[0m` : s);
const cyan = (s) => c(36, s);
const green = (s) => c(32, s);
const red = (s) => c(31, s);
const gray = (s) => c(90, s);

// CJK 字符终端里占两格，直接用 length 算分隔线会偏短
const displayWidth = (s) => [...s].reduce((w, ch) => w + (/[\u1100-\u115F\u2E80-\uA4CF\uAC00-\uD7A3\uF900-\uFAFF\uFE30-\uFE4F\uFF00-\uFF60\uFFE0-\uFFE6]/.test(ch) ? 2 : 1), 0);
const step = (title) =>
  console.log(`\n${cyan(`── ${title} ${'─'.repeat(Math.max(2, 66 - displayWidth(title)))}`)}`);
const truncate = (s, n = 300) => (s.length > n ? `${s.slice(0, n)}…(${s.length}B)` : s);

let passed = 0;
const failures = [];

// ---------------------------------------------------------------- HTTP
let token = null;

async function api(method, path, { body, auth = true, timeout = 10_000 } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && token) headers['Authorization'] = `Bearer ${token}`;

  try {
    const res = await fetch(BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(timeout),
    });
    const text = await res.text();
    let json = null;
    try {
      json = JSON.parse(text);
    } catch {
      /* 非 JSON（比如 Tomcat 的 HTML 错误页），保留原文 */
    }
    return { ok: true, status: res.status, text, json, code: json?.code, data: json?.data,
      headers: Object.fromEntries(res.headers) };
  } catch (err) {
    return { ok: false, status: 0, text: String(err.message ?? err), json: null, code: undefined, data: undefined };
  }
}

/**
 * 发一次请求并断言业务 code，顺带把返回体打出来。
 * @param {number} opts.expectCode 期望的 Result.code，默认 0（成功）
 * @param {boolean} opts.quiet     只记断言不打印（调用方想自己排版时用）
 */
async function call(method, path, { expectCode = 0, body, auth = true, quiet = false } = {}) {
  const r = await api(method, path, { body, auth });
  const hit = r.code === expectCode;
  hit ? passed++ : failures.push(`${method} ${path} 期望 code=${expectCode}，实到 ${r.code ?? `HTTP ${r.status}`}`);

  if (!quiet) console.log(`  ${hit ? green('✓') : red('✗')} ${gray(`${method} ${path}`)} → ${truncate(r.text)}`);
  return r;
}

// ---------------------------------------------------------------- 主流程
async function main() {
  console.log(gray(`BASE = ${BASE}   USER = ${USER}`));

  // 0. 探活 ------------------------------------------------------------
  step('0. 探活 GET /demo/hello（公开接口）');
  const health = await api('GET', '/demo/hello', { auth: false, timeout: 5000 });
  if (!health.ok || health.status !== 200) {
    console.error(red(`\n应用没起来：${BASE} 无响应（${health.text}）`));
    console.error('先执行：docker compose up -d && cd java-learn-app && mvn spring-boot:run');
    process.exit(1);
  }
  console.log(`  ${green('✓')} ${health.text}`);
  passed++;

  // 1. 鉴权 ------------------------------------------------------------
  step(`1. 注册 POST /auth/register (user=${USER})`);
  await call('POST', '/auth/register', {
    body: { username: USER, password: PASS, role: 'USER' },
    auth: false,
  });

  step('2. 登录 POST /auth/login → 取 JWT');
  const login = await call('POST', '/auth/login', {
    body: { username: USER, password: PASS },
    auth: false,
  });
  token = typeof login.data === 'string' ? login.data : null;
  if (!token) {
    console.error(red('登录失败，拿不到 token，后续接口无法继续'));
    process.exit(1);
  }
  console.log(`  ${green(`TOKEN = ${token.slice(0, 32)}… (len=${token.length})`)}`);

  // 2. 商品 CRUD -------------------------------------------------------
  step('3. 新建商品 POST /products');
  const created = await call('POST', '/products', {
    body: { name: '机械键盘', price: 399.0, description: '87键 青轴' },
  });
  const pid = created.data?.id;
  if (!pid) {
    console.error(red('建商品失败，拿不到 id'));
    process.exit(1);
  }
  console.log(`  ${green(`PID = ${pid}`)}`);

  step(`4. 商品详情 GET /products/${pid}（首次应 MISS，回填缓存）`);
  await call('GET', `/products/${pid}`);

  step(`4b. 商品详情再次 GET /products/${pid}（应命中缓存 X-Cache: HIT）`);
  const hit = await api('GET', `/products/${pid}`);
  assert('第二次读命中缓存（X-Cache: HIT）',
    hit.headers?.['x-cache'] === 'HIT',
    `实到 X-Cache=${hit.headers?.['x-cache']}`);

  step(`5. 更新商品 PUT /products/${pid}`);
  await call('PUT', `/products/${pid}`, {
    body: { name: '机械键盘 Pro', price: 459.0, description: '87键 茶轴' },
  });

  step('6. 商品列表 GET /products');
  await call('GET', '/products');

  // 3. 库存 & 下单 -----------------------------------------------------
  step(`7. 初始化库存 POST /products/${pid}/stock (total=10)`);
  await call('POST', `/products/${pid}/stock`, { body: { total: 10 } });

  step(`8. 乐观锁下单 POST /products/${pid}/order (quantity=2)`);
  await call('POST', `/products/${pid}/order`, { body: { userId: 1, quantity: 2 } });

  step(`9. 悲观锁下单 POST /products/${pid}/order (quantity=3, FOR UPDATE)`);
  await call('POST', `/products/${pid}/order`, {
    body: { userId: 1, quantity: 3, lockType: 'PESSIMISTIC' },
  });

  step(`10. 查库存 GET /products/${pid}/stock`);
  const stock = await call('GET', `/products/${pid}/stock`);
  assert('库存扣减正确 available=10-2-3=5', stock.data?.available === 5, `实到 ${stock.data?.available}`);
  assert('扣减量=5（available = total - 5）', stock.data?.available === stock.data?.total - 5, `available=${stock.data?.available} total=${stock.data?.total}`);

  // 4. 错误码 ----------------------------------------------------------
  step('11. 错误码验证');
  const cases = [
    ['2003 未登录      ', 2003, 'GET', '/products', { auth: false }],
    ['2004 无权访问    ', 2004, 'GET', '/admin/panel', {}],
    ['2001 用户名已存在', 2001, 'POST', '/auth/register', { body: { username: USER, password: PASS, role: 'USER' }, auth: false }],
    ['2002 密码错误    ', 2002, 'POST', '/auth/login', { body: { username: USER, password: 'wrong' }, auth: false }],
    ['3001 商品不存在  ', 3001, 'GET', '/products/999999', {}],
    ['3003 库存已初始化', 3003, 'POST', `/products/${pid}/stock`, { body: { total: 5 } }],
    ['3004 库存不足    ', 3004, 'POST', `/products/${pid}/order`, { body: { userId: 1, quantity: 999 } }],
  ];
  for (const [label, expectCode, method, path, opts] of cases) {
    const r = await call(method, path, { ...opts, expectCode, quiet: true });
    const hit = r.code === expectCode;
    console.log(`  ${hit ? green('✓') : red('✗')} ${label} : ${truncate(r.text, 120)}`);
  }

  // 5. 并发冲突 --------------------------------------------------------
  step('12. 并发抢购（1 件库存 / 10 并发），观察 3005 乐观锁冲突');
  const limited = await call('POST', '/products', {
    body: { name: '限量商品', price: 9.9, description: '只有1件' },
    quiet: true,
  });
  const pid2 = limited.data?.id;
  await call('POST', `/products/${pid2}/stock`, { body: { total: 1 }, quiet: true });
  console.log(gray(`  限量商品 PID = ${pid2}，库存 1 件`));

  // 同时发出，不做任何串行化，逼出乐观锁冲突
  const results = await Promise.all(
    Array.from({ length: 10 }, () =>
      api('POST', `/products/${pid2}/order`, { body: { userId: 1, quantity: 1 } })
    )
  );
  const tally = results.reduce((acc, r) => {
    const k = r.code ?? `HTTP${r.status}`;
    acc[k] = (acc[k] ?? 0) + 1;
    return acc;
  }, {});
  const okCount = tally[0] ?? 0;
  console.log(
    `  成功 ${green(okCount)} 单 / 乐观锁冲突(3005) ${tally[3005] ?? 0} 单 / 库存不足(3004) ${tally[3004] ?? 0} 单`
  );
  const other = Object.entries(tally).filter(([k]) => !['0', '3004', '3005'].includes(k));
  if (other.length) console.log(`  ${red('其它返回：')}${JSON.stringify(Object.fromEntries(other))}`);

  assert('1 件库存最终只卖出 1 单（不超卖）', okCount === 1, `实际成功 ${okCount} 单`);
  assert('10 个请求都有明确结论', results.length === 10 && other.length === 0);

  step(`13. 删除商品 DELETE /products/${pid2}`);
  await call('DELETE', `/products/${pid2}`);

  // 收尾 ---------------------------------------------------------------
  console.log();
  if (failures.length === 0) {
    console.log(green(`全流程跑完：${passed} 项断言全部通过。商品 PID=${pid} 保留在库里，可继续手工调试。`));
  } else {
    console.log(red(`全流程跑完：${passed} 通过 / ${failures.length} 失败`));
    failures.forEach((f) => console.log(red(`  ✗ ${f}`)));
    process.exitCode = 1;
  }
}

function assert(desc, ok, detail = '') {
  ok ? passed++ : failures.push(`${desc}${detail ? `（${detail}）` : ''}`);
  console.log(`  ${ok ? green('✓') : red('✗')} ${desc}${ok || !detail ? '' : red(` ← ${detail}`)}`);
}

main().catch((err) => {
  console.error(red(`\n脚本异常终止：${err?.stack ?? err}`));
  process.exit(1);
});
