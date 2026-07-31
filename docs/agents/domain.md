# Domain Docs（领域文档）

工程类 skill 在探索代码库时，应如何消费本仓库的领域文档。

## 探索前，先读这些

- 仓库根目录的 **`CONTEXT.md`**，或
- 若根目录存在 **`CONTEXT-MAP.md`**——它会指向每个 context 各自的 `CONTEXT.md`，请读取与主题相关的每一个。
- **`docs/adr/`** —— 读取与你即将工作内容相关的 ADR。在多 context 仓库中，还需检查 `src/<context>/docs/adr/` 中的 context 级决策。

如果上述文件不存在，**静默继续**。不要提示其缺失，也不要主动建议创建。这些文件由 `/domain-modeling` skill（通过 `/grill-with-docs` 和 `/improve-codebase-architecture` 进入）在术语或决策真正确定时惰性创建。

## 文件结构

单 context 仓库（大多数仓库）：

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

多 context 仓库（根目录存在 `CONTEXT-MAP.md`）：

```
/
├── CONTEXT-MAP.md
├── docs/adr/                          ← 系统级决策
└── src/
    ├── ordering/
    │   ├── CONTEXT.md
    │   └── docs/adr/                  ← context 级决策
    └── billing/
        ├── CONTEXT.md
        └── docs/adr/
```

## 使用术语表中的词汇

当你的输出要命名某个领域概念（issue 标题、重构提案、假设、测试名）时，使用 `CONTEXT.md` 中定义的术语。不要漂移到术语表明确规避的同义词。

如果你需要的概念尚未收录进术语表，这是一个信号——要么你在发明项目并不使用的语言（重新考虑），要么确实存在空缺（为 `/domain-modeling` 记下）。

## 标记 ADR 冲突

如果你的输出与某条现有 ADR 相矛盾，应显式指出，而非静默覆盖：

> _与 ADR-0007（event-sourced orders）相矛盾——但值得重新开启，因为…_
