# LiteTools Emoticon Sync Provider Development Plan

## 背景

LiteTools 已经使用普通文件目录和 Git 仓库管理本地表情。目标是在 QAuxiliary 中新增一个 LiteTools 表情 provider，让 QA 可以直接读取同一份 LiteTools 表情仓库，并在 QQ 原生表情面板中展示和发送表情。

本方案不把 LiteTools 仓库转换成 QA 旧的 `set.json` / `info.json` 私有格式，而是让 QA 直接消费 LiteTools 的目录协议。Git 同步内置到 QA 中，但自动任务只检查远端状态；实际同步必须由用户手动触发。

## 目标

- 新增 LiteTools 表情 provider。
- 支持用户配置 LiteTools 仓库本地目录。
- 支持读取多级目录表情包。
- 支持 `index.json` 排序。
- 支持 `icon.*` 作为表情包 Tab 图标。
- 内置 JGit。
- 自动检查 remote 是否有更新。
- 手动执行 clone / pull / commit / push。
- 使用表情后可更新 `recent.json`。

## 非目标

- 不迁移 QA 旧 `本地表情包` 数据。
- 不把 LiteTools 仓库复制或转换为 QA 旧格式。
- 不自动 pull / push。
- 不自动解决 Git 冲突。
- 初期不实现保存任意图片到 LiteTools 仓库。
- 初期不支持 SSH key，只考虑 HTTPS + token。

## 仓库目录协议

默认仓库目录建议为：

```text
/storage/self/primary/Android/media/com.tencent.mobileqq/LiteToolsEmoticons/
```

用户应可以在设置中修改该路径。

典型结构：

```text
repo-root/
  .git/
  recent.json
  PackA/
    index.json
    icon.png
    a.png
    b.gif
  PackB/
    SubPack/
      index.json
      c.webp
```

扫描规则：

- 从仓库根目录递归扫描。
- 跳过 `.git`、`.nomedia`。
- 跳过 `recent.json`、`index.json`、`sticker.json`。
- 支持图片扩展名：`.png`、`.jpg`、`.jpeg`、`.gif`、`.webp`。
- 每个包含图片文件的目录生成一个表情包。
- 多级目录使用相对路径作为 pack id，并统一使用 `/` 分隔，例如 `PackB/SubPack`。

排序规则：

- 如果目录中存在 `index.json`，优先按其中的 `name` 顺序展示存在的图片。
- 不在 `index.json` 中的图片追加到末尾。
- 如果没有 `index.json`，按文件名升序排序。

图标规则：

- 优先使用 `icon.*`。
- 如果没有 `icon.*`，使用第一张表情图片。

## 代码结构

复用现有 `cc.microblock.hook.DumpTelegramStickers` 中的 provider/hook 框架，新增 LiteTools 相关实现。

计划新增文件：

```text
app/src/main/java/cc/microblock/hook/litetools/LiteToolsStickerConfig.kt
app/src/main/java/cc/microblock/hook/litetools/LiteToolsStickerRepository.kt
app/src/main/java/cc/microblock/hook/litetools/LiteToolsEmoticonProvider.kt
app/src/main/java/cc/microblock/hook/litetools/LiteToolsStickerGitSync.kt
```

计划修改文件：

```text
app/src/main/java/cc/microblock/hook/DumpTelegramStickers.kt
app/build.gradle.kts
```

`app/build.gradle.kts` 增加：

```kotlin
implementation(libs.eclipse.jgit)
```

## 数据模型

```kotlin
data class LiteToolsStickerPack(
    val id: String,
    val title: String,
    val dir: File,
    val icon: File?,
    val items: List<LiteToolsStickerItem>
)

data class LiteToolsStickerItem(
    val packId: String,
    val name: String,
    val file: File,
    val time: Long?
)

data class LiteToolsRecentItem(
    val packageName: String,
    val name: String,
    val time: Long
)
```

## Provider 接入

新增：

```kotlin
class LiteToolsEmoticonProvider : ExtraEmoticonProvider()
```

provider id：

```text
LiteToolsEmoticonProvider
```

epId 使用 QA provider 格式：

```text
qa:LiteToolsEmoticonProvider:<encoded-pack-id>
```

`packId` 可能包含 `/`、空格或其他特殊字符，必须编码后再拼接到 `epId`。建议使用 URL-safe Base64。

每个表情仍构造 `FavoriteEmoticonInfo`：

```kotlin
val info = FavoriteEmoticonInfo.newInstance()
info.set("path", item.file.absolutePath)
info.set("actionData", "litetools:${pack.id}:${item.name}")
```

需要复用现有 hook：

- `EmoticonPanelController.getPanelDataList`
- `EmoticonTabAdapter.generateTabUrl`
- `EmotionPanelViewPagerAdapter.getEmotionPanelData` 或 `queryEmoticonsByPackageIdFromDB`
- `FavoriteEmoticonInfo.getDrawable`

## 配置项

基础配置：

- 是否启用 LiteTools 表情 provider。
- 本地仓库路径。
- 面板列数。
- 预览质量。
- 是否隐藏 QQ 自带表情面板。

Git 配置：

- remote URL。
- branch，默认 `main`。
- 用户名，可选。
- token / password。
- commit author name。
- commit author email。
- commit message，默认 `Update local emoticons`。
- 自动检查 remote 更新开关。
- 自动检查间隔。

状态展示：

- 未初始化。
- 已同步。
- 远端有更新。
- 本地有未提交变更。
- ahead / behind 数量。
- 最近一次检查时间。
- 最近一次错误。

操作按钮：

- 检查仓库。
- Clone。
- 检查更新。
- 手动同步。

## Git 行为

Android 上不依赖系统 `git` 命令，使用 JGit。

自动检查只允许做只读远端检查：

1. 打开本地仓库。
2. 执行 fetch。
3. 比较 `HEAD` 与 `refs/remotes/origin/<branch>`。
4. 记录 ahead / behind 状态。
5. 刷新设置页状态。

自动检查不得修改工作区，不得自动 pull，不得自动 commit，不得自动 push。

手动同步流程：

1. 获取同步锁，避免并发同步。
2. 如果目录不存在或为空，执行 clone。
3. 如果目录存在且包含 `.git`，打开仓库。
4. 如果目录存在但不是 Git 仓库，停止并提示用户。
5. 执行 pull/rebase 等价流程。
6. 重新扫描表情仓库。
7. 执行 `add -A`。
8. 如果存在变更，提交 commit。
9. push 到 remote。
10. 重新扫描 provider 并刷新面板。

冲突策略：

- rebase 或 merge 冲突时停止。
- 记录错误状态。
- 不自动删除、覆盖或修复冲突文件。
- 提示用户使用 Git 客户端手动处理。

## recent.json

发送 LiteTools 表情后更新仓库根目录下的 `recent.json`。

格式：

```json
{
  "version": 1,
  "items": [
    {
      "package": "PackA",
      "name": "a.png",
      "time": 1710000000000
    }
  ]
}
```

更新规则：

1. 仅处理 `actionData` 以 `litetools:` 开头的表情。
2. 从 `actionData` 解析 pack id 和文件名。
3. 读取现有 `recent.json`。
4. 删除同 `package + name` 的旧记录。
5. 新记录插入头部。
6. 最多保留 100 条。
7. 写回 `recent.json`。
8. 不自动 push，等待用户手动同步。

## 实施阶段

### Phase 1: Provider 只读展示

- 新增 repository parser。
- 新增 LiteTools provider。
- 支持配置本地目录。
- 支持多级目录、`index.json`、`icon.*`。
- 能在 QQ 原生表情面板展示并发送。

验收：

- PC 端 LiteTools 仓库复制到 Android 后，QA 能显示所有表情包。
- 多级目录能显示为多个 Tab。
- `index.json` 排序生效。
- `icon.*` 或第一张图片作为 Tab 图标。
- 点击表情能发送。

### Phase 2: JGit 手动同步

- 引入 JGit。
- 支持配置 remote、branch、token。
- 支持 clone。
- 支持检查更新。
- 支持手动同步。

验收：

- 空目录可 clone remote。
- remote 有新提交时能显示 behind 状态。
- 用户点击手动同步后能拉取远端更新。
- 本地 `recent.json` 修改后可 commit/push。

### Phase 3: recent.json 写入

- hook `FavoriteEmoticonInfo.send`。
- 写入 LiteTools 格式 `recent.json`。
- 手动同步时提交 recent 变更。

验收：

- QA 使用表情后，`recent.json` 更新。
- 手动同步后，PC LiteTools 能看到 QA 的最近使用记录。

### Phase 4: 保存图片到 LiteTools 仓库

- 新增保存入口。
- 复制或下载原图到目标 pack。
- 文件名使用 `<md5>.<ext>`。
- 更新目标目录 `index.json`。
- 手动同步时提交新图片和索引。

验收：

- QA 保存图片后，PC pull 可见。
- 重复图片不生成重复文件。

## 风险

- `Android/media` 目录在不同系统和 Android 版本上的访问限制不同。
- JGit 会增加 APK 体积。
- JGit 行为与命令行 Git 不完全一致，需要重点测试认证、rebase 和冲突状态。
- GIF/WebP 预览可能有性能问题。
- `index.json` 容易产生跨端冲突，初期不自动解决。
- token 存储需要避免明文暴露，后续应评估 Android Keystore。

