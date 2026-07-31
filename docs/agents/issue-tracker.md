# Issue tracker: Local Markdown（本地 Markdown 问题跟踪器）

本仓库的 issue 与 spec（spec 也可称为 PRD）以 markdown 文件形式存放在 `.scratch/` 目录下。

## 约定（Conventions）

- 每个功能一个目录：`.scratch/<feature-slug>/`
- spec 文件为 `.scratch/<feature-slug>/spec.md`
- 实现类 issue 一个 ticket 一个文件，路径为 `.scratch/<feature-slug>/issues/<NN>-<slug>.md`，编号从 `01` 开始——不要合并成单个 ticket 文件
- 分拣状态记录在每个 issue 文件顶部的 `Status:` 行（标签字符串见 `triage-labels.md`）
- 评论与对话历史追加到文件末尾的 `## Comments` 标题下

## 当某个 skill 说"发布到 issue tracker"

在 `.scratch/<feature-slug>/` 下新建一个文件（若目录不存在则先创建）。

## 当某个 skill 说"获取相关 ticket"

读取对应路径的文件。用户通常会直接给出路径或 issue 编号。

## Wayfinding 操作（导航操作）

供 `/wayfinder` 使用。**map** 是一个文件，每个 ticket 对应一个 **child** 文件。

- **Map（地图）**：`.scratch/<effort>/map.md` —— 存放 Notes / Decisions-so-far / Fog 主体内容。
- **Child ticket（子 ticket）**：`.scratch/<effort>/issues/NN-<slug>.md`，从 `01` 开始编号，正文包含问题。`Type:` 行记录 ticket 类型（`research`/`prototype`/`grilling`/`task`）；`Status:` 行记录 `claimed`/`resolved`。
- **Blocking（阻塞）**：文件顶部一条 `Blocked by: NN, NN` 行。当它列出的所有文件都 `resolved` 时，该 ticket 解除阻塞。
- **Frontier（前沿）**：扫描 `.scratch/<effort>/issues/`，找出 open、未阻塞、未认领的文件；编号最小的优先。
- **Claim（认领）**：设置 `Status: claimed` 并保存，再进行任何工作。
- **Resolve（解决）**：在 `## Answer` 标题下追加答案，设置 `Status: resolved`，然后向 `map.md` 的 Decisions-so-far 追加一条 context 指针（要点 + 链接）。
