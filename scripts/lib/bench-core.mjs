/**
 * 抢购压测公共内核：被 bench-optimistic.mjs / bench-pessimistic.mjs 复用。
 *
 * 这里只做一件事：把「N 件库存 / M 个用户同时开抢」这个场景跑出来，
 * 并把耗时与结果分布如实打印出来，供乐观锁 vs 悲观锁横向对比。
 *
 * 依赖：Node >= 18（内置 fetch / performance），零 npm 依赖。
 */

// ============================================================ 输出工具
const COLOR = process.stdout.isTTY && !process.env.NO_COLOR;
const c = (code, s) => (COLOR ? `\x1b[${code}m${s}\x1b[0m` : s);
export const cyan = (s) => c(36, s);
export const green = (s) => c(32, s);
export const red = (s) => c(31, s);
export const yellow = (s) => c(33, s);
export const gray = (s) => c(90, s);
export const bold = (s) => c(1, s);

// CJK 在终端占两格，用 length 算分隔线会偏短
const displayWidth = (s) =>
  [...s].reduce(
    (w, ch) =>
      w +
      (/[\u1100-\u115F\u2E80-\uA4CF\uAC00-\uD7A3\uF900-\uFAFF\uFE30-\uFE4F\uFF00-\uFF60\uFFE0-\uFFE6]/.test(ch)
        ? 2
        : 1),
    0,
  );
export const step = (title) =>
  console.log(`\n${cyan(`── ${title} ${'─'.repeat(Math.max(2, 68 - displayWidth(title)))}`)}`);

const ms = (n) => `${n.toFixed(1)} ms`;
const pad = (s, n) => String(s).padStart(n);

// ============================================================ 参数解析
export function parseArgs(argv) {
  const val = (name, fallback) => {
    const i = argv.indexOf(`--${name}`);
    if (i < 0) return fallback;
    const v = argv[i + 1];
    return v === undefined || v.startsWith('--') ? fallback : v;
  };
  const flag = (name) => argv.includes(`--${name}`);

  return {
    base: (val('base', process.env.BASE || 'http://localhost:8080')).replace(/\/+$/, ''),
    stock: Number(val('stock', 30)),
    users: Number(val('users', 100)),
    qty: Number(val('qty', 1)),
    retry: Number(val('retry', 0)),   // 乐观锁客户端重试次数（悲观锁用不上）
    noWarmup: flag('no-warmup'),
    keep: flag('keep'),               // 保留压测商品，默认压完删掉
  };
}

// ============================================================ HTTP
let TOKEN = null;
let BASE = '';

async function api(method, path, { body, auth = true, timeout = 60_000 } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && TOKEN) headers['Authorization'] = `Bearer ${TOKEN}`;

  const t0 = performance.now();
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
      /* 非 JSON（Tomcat HTML 错误页等），保留原文 */
    }
    return {
      ok: true,
      status: res.status,
      text,
      code: json?.code,
      data: json?.data,
      msg: json?.message ?? json?.msg,
      cost: performance.now() - t0,
    };
  } catch (err) {
    return {
      ok: false,
      status: 0,
      text: String(err?.message ?? err),
      code: undefined,
      data: undefined,
      msg: String(err?.message ?? err),
      cost: performance.now() - t0,
    };
  }
}

// ============================================================ 准备阶段
async function healthCheck() {
  const r = await api('GET', '/demo/hello', { auth: false, timeout: 5000 });
  if (!r.ok || r.status !== 200) {
    console.error(red(`\n应用没起来：${BASE} 无响应（${r.text}）`));
    console.error('先执行：docker compose up -d && cd java-learn-app && mvn spring-boot:run');
    process.exit(1);
  }
}

async function login() {
  // 压测用的账号带时间戳，避免 2001 用户名已存在
  const user = `bench_${Date.now().toString(36)}`;
  await api('POST', '/auth/register', {
    body: { username: user, password: '123456', role: 'USER' },
    auth: false,
  });
  const r = await api('POST', '/auth/login', {
    body: { username: user, password: '123456' },
    auth: false,
  });
  if (typeof r.data !== 'string') {
    console.error(red(`登录失败，拿不到 token：${r.text}`));
    process.exit(1);
  }
  TOKEN = r.data;
  return user;
}

async function createProduct(name, price, total) {
  const p = await api('POST', '/products', { body: { name, price, description: name } });
  const pid = p.data?.id;
  if (!pid) {
    console.error(red(`建商品失败：${p.text}`));
    process.exit(1);
  }
  const s = await api('POST', `/products/${pid}/stock`, { body: { total } });
  if (s.code !== 0) {
    console.error(red(`初始化库存失败：${s.text}`));
    process.exit(1);
  }
  return pid;
}

/**
 * 预热：JVM 的 JIT、Hikari 连接池、MyBatis 语句缓存、undici 连接复用都需要热身。
 * 不预热的话第一批请求会明显偏慢，把耗时统计带偏（尤其 max 和 P99）。
 * 用一个独立的预热商品，不消耗正式压测的库存。
 */
async function warmup(lockType, rounds = 8) {
  const pid = await createProduct(`预热商品_${Date.now()}`, 1.0, rounds + 5);
  const costs = [];
  for (let i = 0; i < rounds; i++) {
    const r = await api('POST', `/products/${pid}/order`, {
      body: { userId: 1, quantity: 1, lockType },
    });
    costs.push(r.cost);
  }
  await api('DELETE', `/products/${pid}`);
  return { first: costs[0], last: costs[costs.length - 1] };
}

// ============================================================ 统计
const percentile = (sorted, p) =>
  sorted.length === 0 ? 0 : sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];

const stats = (arr) => {
  if (arr.length === 0) return null;
  const s = [...arr].sort((a, b) => a - b);
  return {
    n: s.length,
    min: s[0],
    max: s[s.length - 1],
    avg: s.reduce((a, b) => a + b, 0) / s.length,
    p50: percentile(s, 50),
    p90: percentile(s, 90),
    p99: percentile(s, 99),
  };
};

/** 等宽分桶的 ASCII 直方图，用来看耗时是"均匀分布"还是"阶梯状排队" */
function histogram(values, buckets = 12, width = 34) {
  if (values.length === 0) return [];
  const min = Math.min(...values);
  const max = Math.max(...values);
  if (max - min < 1e-6) return [`  ${ms(min)} │${'█'.repeat(width)} ${values.length}`];

  const size = (max - min) / buckets;
  const counts = new Array(buckets).fill(0);
  for (const v of values) counts[Math.min(buckets - 1, Math.floor((v - min) / size))]++;
  const peak = Math.max(...counts);

  return counts.map((cnt, i) => {
    const lo = min + i * size;
    const hi = lo + size;
    const bar = '█'.repeat(Math.round((cnt / peak) * width));
    return `  ${pad(lo.toFixed(0), 6)}~${pad(hi.toFixed(0), 6)} ms │${bar.padEnd(width)} ${pad(cnt, 3)}`;
  });
}

// ============================================================ 压测主体
/**
 * @param {'OPTIMISTIC'|'PESSIMISTIC'} lockType
 */
export async function runLockBench({ lockType, title, note }) {
  const opt = parseArgs(process.argv.slice(2));
  BASE = opt.base;
  const isOptimistic = lockType === 'OPTIMISTIC';

  console.log(bold(cyan(`\n${title}`)));
  console.log(
    gray(
      `BASE=${BASE}  库存=${opt.stock}  并发用户=${opt.users}  每人买=${opt.qty} 件  锁=${lockType}` +
        (isOptimistic ? `  客户端重试=${opt.retry}` : ''),
    ),
  );

  // ---------- 0. 准备 ----------
  step('准备阶段');
  await healthCheck();
  const user = await login();
  console.log(`  ${green('✓')} 登录成功（压测账号 ${user}，100 个用户共用此 token）`);

  if (!opt.noWarmup) {
    const w = await warmup(lockType);
    console.log(
      `  ${green('✓')} 预热 8 次下单：首次 ${ms(w.first)} → 末次 ${ms(w.last)} ` +
        gray('(JIT / 连接池 / 语句缓存已热)'),
    );
  } else {
    console.log(`  ${yellow('!')} 已跳过预热（--no-warmup），首批请求会明显偏慢`);
  }

  const pid = await createProduct(`抢购商品_${lockType}_${Date.now()}`, 99.0, opt.stock);
  console.log(`  ${green('✓')} 压测商品 PID=${pid}，库存 ${opt.stock} 件`);

  // ---------- 1. 发压 ----------
  step(`开抢：${opt.users} 个用户同时下单`);

  // 发令枪：先把 N 个协程都挂在同一个 Promise 上，再一次性放行，
  // 保证请求是"同时"发出的，而不是被循环逐个拉开。
  let fire;
  const gate = new Promise((resolve) => {
    fire = resolve;
  });

  const sendAt = [];   // 每个请求真正发出的时刻，用来度量发压窗口有多紧
  const tasks = Array.from({ length: opt.users }, (_, i) => {
    const userId = i + 1;
    return (async () => {
      await gate;
      const t0 = performance.now();
      sendAt.push(t0);

      let r;
      let attempts = 0;
      // 乐观锁允许客户端重试：这才是乐观锁的正确用法，
      // 服务端遇冲突直接抛 3005，是否重试由调用方决定。
      const maxAttempts = isOptimistic ? opt.retry + 1 : 1;
      do {
        attempts++;
        r = await api('POST', `/products/${pid}/order`, {
          body: { userId, quantity: opt.qty, lockType },
        });
      } while (r.code === 3005 && attempts < maxAttempts);

      return { userId, attempts, cost: performance.now() - t0, code: r.code, status: r.status, text: r.text };
    })();
  });

  // 让所有协程都跑到 await gate 上，再开枪
  await new Promise((r) => setImmediate(r));
  await new Promise((r) => setTimeout(r, 30));

  const wallStart = performance.now();
  fire();
  const results = await Promise.all(tasks);
  const wallCost = performance.now() - wallStart;

  // ---------- 2. 结果分布 ----------
  const tally = results.reduce((acc, r) => {
    const k = r.code ?? `HTTP${r.status}`;
    acc[k] = (acc[k] ?? 0) + 1;
    return acc;
  }, {});
  const okCount = tally[0] ?? 0;
  const conflict = tally[3005] ?? 0;
  const notEnough = tally[3004] ?? 0;
  const others = Object.entries(tally).filter(([k]) => !['0', '3004', '3005'].includes(k));

  step('抢购结果');
  console.log(`  成功下单        ${green(pad(okCount, 4))} 单   ${gray(`(卖出 ${okCount * opt.qty} 件)`)}`);
  console.log(`  库存不足 3004   ${pad(notEnough, 4)} 单`);
  console.log(
    `  乐观锁冲突 3005 ${conflict > 0 ? yellow(pad(conflict, 4)) : pad(conflict, 4)} 单` +
      (isOptimistic ? '' : gray('   ← 悲观锁下应恒为 0')),
  );
  if (others.length) {
    console.log(`  ${red('其它返回')}        ${JSON.stringify(Object.fromEntries(others))}`);
    const sample = results.find((r) => ![0, 3004, 3005].includes(r.code));
    if (sample) console.log(`  ${gray(`样例：${sample.text.slice(0, 200)}`)}`);
  }
  if (isOptimistic && opt.retry > 0) {
    const totalAttempts = results.reduce((s, r) => s + r.attempts, 0);
    const retried = results.filter((r) => r.attempts > 1).length;
    console.log(
      `  ${gray(`客户端重试：${retried} 个用户重试过，共发出 ${totalAttempts} 次请求（放大 ${(totalAttempts / opt.users).toFixed(2)}x）`)}`,
    );
  }

  // ---------- 3. 耗时 ----------
  const all = results.map((r) => r.cost);
  const okCosts = results.filter((r) => r.code === 0).map((r) => r.cost);
  const failCosts = results.filter((r) => r.code !== 0).map((r) => r.cost);
  const a = stats(all);
  const o = stats(okCosts);
  const f = stats(failCosts);
  const spread = sendAt.length ? Math.max(...sendAt) - Math.min(...sendAt) : 0;

  step('耗时');
  console.log(`  ${bold('墙钟总耗时')}      ${bold(green(ms(wallCost)))}   ${gray(`(从第一个请求发出到最后一个响应返回)`)}`);
  console.log(`  发压窗口        ${ms(spread)}   ${gray('(首尾请求发出的时间差，越小说明并发越齐)')}`);
  console.log(`  吞吐            ${((opt.users / wallCost) * 1000).toFixed(1)} req/s`);
  console.log(`  平均每单        ${ms(wallCost / Math.max(1, okCount))}   ${gray('(墙钟 / 成功单数)')}`);
  console.log();
  console.log(gray('  单请求耗时     min      avg      P50      P90      P99      max'));
  const row = (label, s) =>
    s
      ? console.log(
          `  ${label.padEnd(12)} ${pad(s.min.toFixed(1), 7)}  ${pad(s.avg.toFixed(1), 7)}  ` +
            `${pad(s.p50.toFixed(1), 7)}  ${pad(s.p90.toFixed(1), 7)}  ${pad(s.p99.toFixed(1), 7)}  ${pad(s.max.toFixed(1), 7)}`,
        )
      : console.log(`  ${label.padEnd(12)} ${gray('(无样本)')}`);
  row(`全部(${a.n})`, a);
  row(`成功(${o?.n ?? 0})`, o);
  row(`失败(${f?.n ?? 0})`, f);

  console.log();
  console.log(gray('  耗时分布直方图：'));
  histogram(all).forEach((l) => console.log(gray(l)));

  // ---------- 4. 一致性校验 ----------
  step('一致性校验');
  const stockNow = await api('GET', `/products/${pid}/stock`);
  const available = stockNow.data?.available;
  console.log(`  ${gray(`最终库存：available=${available}`)}`);

  const failures = [];
  const check = (desc, ok, detail = '') => {
    ok ? null : failures.push(`${desc}${detail ? `（${detail}）` : ''}`);
    console.log(`  ${ok ? green('✓') : red('✗')} ${desc}${ok || !detail ? '' : red(` ← ${detail}`)}`);
  };

  const sold = okCount * opt.qty;
  check('没有超卖（卖出量 ≤ 初始库存）', sold <= opt.stock, `卖出 ${sold} / 库存 ${opt.stock}`);
  check(
    '库存账实相符（available = 初始 - 卖出）',
    available === opt.stock - sold,
    `期望 ${opt.stock - sold}，实到 ${available}`,
  );
  check('每个请求都有明确结论（无超时/5xx）', others.length === 0);
  if (!isOptimistic) {
    check('悲观锁下无版本冲突（3005 = 0）', conflict === 0, `实到 ${conflict}`);
    check(
      '库存被抢空（悲观锁串行执行，不该有剩余）',
      available === 0,
      `实到 ${available}，若 > 0 说明并发用户数不足以抢空库存`,
    );
  }

  // ---------- 5. 收尾 ----------
  if (!opt.keep) {
    await api('DELETE', `/products/${pid}`);
    console.log(gray(`\n已删除压测商品 PID=${pid}（--keep 可保留）。订单数据仍在 orders 表，可用 node scripts/db-reset.mjs 清理。`));
  } else {
    console.log(gray(`\n压测商品 PID=${pid} 已保留。`));
  }

  if (note) {
    console.log();
    note({ okCount, conflict, notEnough, wallCost, stats: a, okStats: o, opt });
  }

  if (failures.length) {
    console.log(red(`\n校验未通过 ${failures.length} 项：`));
    failures.forEach((x) => console.log(red(`  ✗ ${x}`)));
    process.exitCode = 1;
  } else {
    console.log(green('\n校验全部通过。'));
  }
}
