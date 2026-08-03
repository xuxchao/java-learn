#!/usr/bin/env node
/**
 * 数据库清理脚本：清空（或删除）ecommerce 库里的业务表。
 *
 * 依赖：只要 docker 里的 java-learn-mysql 容器在跑即可，无需本机装 mysql 客户端、无需 npm 依赖。
 *
 * 用法：
 *   node scripts/db-reset.mjs                    # 交互确认后 TRUNCATE 所有表（清数据、保留表结构、自增 ID 归 1）
 *   node scripts/db-reset.mjs --yes              # 跳过确认，直接执行
 *   node scripts/db-reset.mjs --drop             # DROP TABLE（连表结构一起删，需重启应用让 schema.sql 重建）
 *   node scripts/db-reset.mjs --tables orders,stock   # 只处理指定表
 *   node scripts/db-reset.mjs --dry-run          # 只打印将要执行的 SQL，不落库
 *
 * 其他可选参数：
 *   --db <name>          目标库名，默认 ecommerce
 *   --container <name>   MySQL 容器名，默认 java-learn-mysql
 */

import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import readline from 'node:readline/promises';
import { stdin, stdout } from 'node:process';

const execFileAsync = promisify(execFile);

// ---------------------------------------------------------------- 参数解析
const argv = process.argv.slice(2);
const flag = (name) => argv.includes(`--${name}`);
const opt = (name, fallback) => {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback;
};

const DB = opt('db', process.env.DB_NAME || 'ecommerce');
const CONTAINER = opt('container', process.env.MYSQL_CONTAINER || 'java-learn-mysql');
const MODE = flag('drop') ? 'DROP' : 'TRUNCATE';
const DRY_RUN = flag('dry-run');
const AUTO_YES = flag('yes') || flag('y');
const ONLY = opt('tables', '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

// ---------------------------------------------------------------- 输出小工具
const color = (c, s) => `\x1b[${c}m${s}\x1b[0m`;
const red = (s) => color(31, s);
const green = (s) => color(32, s);
const yellow = (s) => color(33, s);
const cyan = (s) => color(36, s);
const gray = (s) => color(90, s);

/**
 * 在容器里执行 SQL。
 * -N 去掉表头，-B 用 TAB 分隔，方便按行解析。
 */
async function sql(statement, { raw = false } = {}) {
  const args = ['exec', CONTAINER, 'mysql', '-uroot', '-proot', '-N', '-B', DB, '-e', statement];
  try {
    const { stdout: out } = await execFileAsync('docker', args, { encoding: 'utf8' });
    return raw ? out : out.trim();
  } catch (e) {
    // mysql 客户端总会往 stderr 写一行密码警告，只有真出错时 exit code 才非 0
    const msg = (e.stderr || e.message || '').replace(/mysql: \[Warning\][^\n]*\n?/g, '').trim();
    throw new Error(msg || e.message);
  }
}

async function ensureContainerRunning() {
  try {
    const { stdout: out } = await execFileAsync('docker', [
      'inspect', '-f', '{{.State.Running}}', CONTAINER,
    ], { encoding: 'utf8' });
    if (out.trim() !== 'true') throw new Error('not running');
  } catch {
    console.error(red(`✗ 容器 ${CONTAINER} 没在运行。`));
    console.error(gray('  先执行：docker compose up -d mysql'));
    process.exit(1);
  }
}

/** 列出库里所有 BASE TABLE，并查出各自真实行数（table_rows 对 InnoDB 只是估算，不可靠）。 */
async function listTables() {
  const out = await sql(
    `SELECT table_name FROM information_schema.tables
     WHERE table_schema='${DB}' AND table_type='BASE TABLE' ORDER BY table_name;`,
  );
  let names = out ? out.split('\n').map((s) => s.trim()).filter(Boolean) : [];

  if (ONLY.length) {
    const missing = ONLY.filter((t) => !names.includes(t));
    if (missing.length) {
      console.error(red(`✗ 这些表在 ${DB} 里不存在：${missing.join(', ')}`));
      console.error(gray(`  库里现有：${names.join(', ') || '(空)'}`));
      process.exit(1);
    }
    names = ONLY;
  }
  if (!names.length) return [];

  const counts = await sql(
    names.map((t) => `SELECT '${t}' AS t, COUNT(*) AS c FROM \`${t}\``).join(' UNION ALL '),
  );
  const rows = new Map(
    counts.split('\n').filter(Boolean).map((line) => {
      const [t, c] = line.split('\t');
      return [t, Number(c)];
    }),
  );
  return names.map((name) => ({ name, rows: rows.get(name) ?? 0 }));
}

// ---------------------------------------------------------------- 主流程
async function main() {
  console.log(cyan(`\n── 数据库清理  ${DB} @ ${CONTAINER}  模式=${MODE}${DRY_RUN ? ' (dry-run)' : ''} ──\n`));

  await ensureContainerRunning();

  const tables = await listTables();
  if (!tables.length) {
    console.log(yellow(`库 ${DB} 里没有表，无需处理。`));
    return;
  }

  const width = Math.max(...tables.map((t) => t.name.length));
  const total = tables.reduce((s, t) => s + t.rows, 0);
  console.log('将要处理的表：');
  for (const t of tables) {
    console.log(`  ${t.name.padEnd(width)}  ${gray(`${t.rows} 行`)}`);
  }
  console.log(gray(`  ${'-'.repeat(width + 12)}`));
  console.log(`  ${'合计'.padEnd(width - 2)}  ${gray(`${total} 行`)}\n`);

  const statements = [
    'SET FOREIGN_KEY_CHECKS = 0;',
    ...tables.map((t) => (MODE === 'DROP' ? `DROP TABLE \`${t.name}\`;` : `TRUNCATE TABLE \`${t.name}\`;`)),
    'SET FOREIGN_KEY_CHECKS = 1;',
  ];

  if (DRY_RUN) {
    console.log(yellow('dry-run，以下 SQL 不会执行：\n'));
    statements.forEach((s) => console.log(`  ${gray(s)}`));
    return;
  }

  // 破坏性操作，默认要人确认一次
  console.log(
    MODE === 'DROP'
      ? red('⚠  DROP 会连表结构一起删掉，数据不可恢复。')
      : yellow('⚠  TRUNCATE 会清空全部数据并把自增 ID 重置为 1，不可恢复。'),
  );
  if (!AUTO_YES) {
    const rl = readline.createInterface({ input: stdin, output: stdout });
    const answer = (await rl.question(`确认继续？输入 ${cyan('yes')} 执行，其他任意键取消： `)).trim();
    rl.close();
    if (answer !== 'yes') {
      console.log(gray('\n已取消，未做任何改动。'));
      return;
    }
  }

  await sql(statements.join('\n'));

  console.log(green(`\n✓ 完成：${tables.length} 张表已${MODE === 'DROP' ? '删除' : '清空'}（原共 ${total} 行）。`));

  // 验证结果
  if (MODE === 'DROP') {
    const left = await listTablesQuiet();
    console.log(gray(`  库中剩余表：${left.length ? left.join(', ') : '(空)'}`));
    console.log(yellow('\n下一步：重启应用，schema.sql 会自动重建这些表。'));
    console.log(gray('  cd java-learn-app && mvn spring-boot:run'));
  } else {
    const after = await sql(
      tables.map((t) => `SELECT COUNT(*) FROM \`${t.name}\``).join(' UNION ALL '),
    );
    const remain = after.split('\n').filter(Boolean).reduce((s, n) => s + Number(n), 0);

    // 注意：information_schema 的统计信息默认缓存 24h（information_schema_stats_expiry=86400），
    // 直接查 AUTO_INCREMENT 会拿到 TRUNCATE 之前的陈旧值，必须先把该会话的缓存过期时间设为 0。
    const autoInc = await sql(
      `SET SESSION information_schema_stats_expiry = 0;
       SELECT CONCAT(table_name, '=', AUTO_INCREMENT) FROM information_schema.tables
       WHERE table_schema='${DB}' AND table_name IN (${tables.map((t) => `'${t.name}'`).join(',')})
       ORDER BY table_name;`,
    );

    console.log(gray(`  复核：当前总行数 = ${remain}`));
    console.log(gray(`  自增值：${autoInc.split('\n').filter(Boolean).join('  ')}`));
    console.log(gray('  表结构与索引保留，应用无需重启。'));
  }
}

async function listTablesQuiet() {
  const out = await sql(
    `SELECT table_name FROM information_schema.tables
     WHERE table_schema='${DB}' AND table_type='BASE TABLE' ORDER BY table_name;`,
  );
  return out ? out.split('\n').map((s) => s.trim()).filter(Boolean) : [];
}

main().catch((e) => {
  console.error(red(`\n✗ 执行失败：${e.message}`));
  process.exit(1);
});
