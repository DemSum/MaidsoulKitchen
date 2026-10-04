# lezizijiang2 1.21.1 功能候选审查

## 1. 文档目的

本文暂存对 `lezizijiang2/MaidsoulKitchen:1.21.1-dev-lezizijiang2`
的只读审查结果。候选分支用于寻找值得原生重写的功能，不作为可直接合并的
`0.1.4` 发布基线。

当前后续范围只包含：

1. 农夫乐事砧板残留材料继续加工。
2. 配方计划保存 `RecipeHolder`。
3. 熔炉支持烹饪中枢与黑白名单过滤规则。

除非维护者重新确认，其他候选功能均不进入本轮实现。

## 2. 来源与基线判断

- 候选分支：<https://github.com/lezizijiang2/MaidsoulKitchen/tree/1.21.1-dev-lezizijiang2>
- 候选分支检查点：`df54c339689398c85a628ae7b6de455d81ae3b71`
- 候选检查点日期：2025-05-30。
- 正式 `0.1.4` sources 导入分支：`vendor/official-0.1.4`。
- 正式 sources 导入提交：`a22b503a4f49937712118fbb83b83dfc50f92be7`。
- 完整发布来源和校验值见 [`docs/upstream-sync/0.1.4.md`](../upstream-sync/0.1.4.md)。

候选检查点虽然声明 `mod_version=0.1.4`，但其源码树与正式发布 sources
快照存在 182 个文件、`+2550/-11233` 的差异。因此不能将版本字符串当作
源码等价证明，也不能把该分支整体 merge 或 cherry-pick 到 slim。

相对当前 `1.21.1neo`，候选分支还缺少或会回退以下既有能力：

- 通用烹饪双坐标和一次性可达侧面 BFS。
- `WORK_POS` 清理约束和多女仆工作锁。
- DrinkBeer 1.4.1 原生适配。
- Kaleidoscope Cookery 蒸笼、过滤和中枢仓储链。
- 静态兼容清单及当前瘦身后的兼容加载机制。

## 3. 选定候选一：砧板残留材料继续加工

### 3.1 候选来源

主要参考提交：
[`1270cadf`](https://github.com/lezizijiang2/MaidsoulKitchen/commit/1270cadf)
及其后续版本中的 `TaskFdCuttingBoard`。

候选实现增加了以下行为：

- 砧板已经存在物品时，检查是否仍有可用切割配方。
- 在女仆有效物品栏中寻找匹配工具。
- 找到工具后仍允许选中该砧板并继续调用农夫乐事的加工逻辑。

### 3.2 当前 slim 缺口

当前 `TaskFdCuttingBoard.shouldMoveTo` 只在砧板为空且存在待执行配方计划时
返回 `true`。因此玩家、任务切换或异常中断留下的可切割材料不会被重新接管。

当前 `MaidCuttingMakeTask` 已有周期性交替放料/使用工具的基本流程，但没有
为“砧板启动时已经有材料”的状态建立工具和 `processItem` 状态。

### 3.3 原生实现约束

- 保留当前 `ReachableCookDeviceSearch`、双坐标和 `WORK_POS`，不移植候选的
  旧坐标搜索。
- 只在砧板物品匹配有效 `CuttingBoardRecipe` 且女仆确实持有匹配工具时选择目标。
- 工具查找使用当前统一的女仆可用物品栏适配层，兼容 TLM 1.1.13/1.5.3。
- 主手/副手交换必须是无损事务：先模拟回收原手持物，再装备工具；不能用
  静默丢弃或无条件生成掉落物作为正常路径。
- 开始工作后再次验证方块实体、砧板物品和工具；目标变化时清理工作记忆。
- 保留普通空砧板的原有取材、放料和加工链。
- 不改变现有任务 UID、数据键和 UI 条目。

### 3.4 验收场景

- 空砧板的原有完整流程不变。
- 砧板上有可加工材料且女仆有正确工具时继续加工。
- 有材料但无工具、工具不匹配或材料无配方时不认领目标。
- 搜索后材料被替换、取走或砧板被破坏时安全清理。
- 女仆背包接近满载时不丢失原手持物、工具或材料。
- 中断、任务切换和女仆重载后不保留失效的 `WORK_POS`。

## 4. 选定候选二：配方计划保存 `RecipeHolder`

### 4.1 候选来源

主要参考提交：

- [`2b1fee03`](https://github.com/lezizijiang2/MaidsoulKitchen/commit/2b1fee03)
- [`271eeb6f`](https://github.com/lezizijiang2/MaidsoulKitchen/commit/271eeb6f)
- [`0ceaae73`](https://github.com/lezizijiang2/MaidsoulKitchen/commit/0ceaae73)

候选分支引入 `MaidRecipe<R>`，把 `RecipeHolder<R>` 与本次分配的材料计划一起
保存，避免执行阶段只持有 recipe value 后再次反查 holder。

### 4.2 当前 slim 缺口

当前 `MaidRecipesManager` 的主要集合保存 `R`，过滤时从
`ICookTask#getRecipeHolders()` 丢弃了 ID/holder 信息。需要 recipe identity 的任务
只能再次查询或依赖 recipe value，既增加查找，也不利于熔炉按配方类型和 ID
执行黑白名单。

### 4.3 候选实现的问题

候选补丁仍会针对每个 recipe 使用 stream 扫描全部 holders，最坏情况仍为
`O(n²)`；因此只采用“计划持有 holder”的设计，不原样复制查找过程。

### 4.4 推荐原生设计

- 配方读取入口始终保留 `RecipeHolder<R>`，先按 holder ID 应用黑白名单。
- 一个计划至少保存：`RecipeHolder<R>`、已选择的材料及数量、必要的任务附加数据。
- 每次计划重建只遍历一次 holders；禁止在 recipe 比较器或单配方循环内重复调用
  `getRecipeHolders(level).stream().filter(...)`。
- 计划只在当前任务周期内缓存；任务规则、有效物品栏、中枢绑定或配方集合变化时
  清空并重建。
- 资源重载后不得继续使用旧 holder。
- 保留现有公开方法的过渡适配，先迁移熔炉和需要精确 recipe identity 的任务，
  再决定是否收敛旧 `Pair` 接口。
- 新模型应使用一个明确的内部 record/class，不能保留候选分支中重复的状态模型。

### 4.5 验收场景

- 白名单与黑名单严格按 `RecipeHolder.id()` 生效。
- 同产物或内容相似但 ID 不同的配方不会串用。
- 多次生成生产批次不会重新全表反查 holder。
- 物品栏或中枢内容变化时计划正确失效。
- 配方数据包重载后计划重建。
- 既有厨锅、砧板、酒桶和其他任务行为不变。

## 5. 选定候选三：熔炉支持烹饪中枢与过滤规则

### 5.1 候选来源

主要参考提交：
[`575a9fa4`](https://github.com/lezizijiang2/MaidsoulKitchen/commit/575a9fa4)。

候选实现增加了按熔炉实际 `RecipeType` 组织材料计划，并让熔炉输入和输出进入
烹饪中枢路径。它还把普通熔炉、烟熏炉和高炉纳入同一管理器。

### 5.2 当前 slim 缺口

- 当前 `TaskFurnace#getRecipesManager` 明确覆盖 `enableHub()` 并返回 `false`。
- 当前原料选择直接遍历女仆背包，并实时调用 `RecipeManager#getRecipeFor`。
- 中枢黑白名单不能完整约束熔炉、烟熏炉和高炉的实际配方类型。
- 输出虽然写入 manager 的输出 inventory，但 manager 在熔炉任务中被禁止启用中枢。

### 5.3 不采用候选整体实现的原因

候选新增了专用 `MaidFurnaceMoveTask`，并在配方准备阶段再次扫描世界中的熔炉。
这会绕过当前统一的双坐标一次 BFS，并把设备搜索和配方规划重新耦合。因此只参考
其“按目标炉型选配方”和“中枢输入输出”语义。

### 5.4 推荐原生设计

- 保留通用 `MaidCookMoveTask` 和 `ReachableCookDeviceSearch`。
- 通过实际候选炉子的 accessor 获取 `SMELTING`、`SMOKING` 或 `BLASTING`
  recipe type，再询问 `RecipeHolder` 计划是否有该炉型可执行配方。
- 黑白名单先按 holder ID 过滤，然后再按炉型和当前库存分配材料。
- 原料从 `MaidRecipesManager#getInputInv()` 进入目标炉；产物写入
  `getOutputInv()`，并使用现有通用任务完成后的回仓流程。
- 第一阶段维持候选分支的保守语义：燃料仍由女仆可用物品栏提供；是否允许从
  中枢原料仓自动取燃料应作为单独决定，避免燃料选择和配方材料分配互相消耗。
- 目标炉已有错误输入且未工作时，只在能够无损退回对应输入 inventory 时取回。
- 输出 inventory 无空间时不得抽取成品。
- 保留熔炉经验记录，不因自动抽取而静默清空或重复结算。
- 不新增第二套移动任务或世界范围炉型扫描。

### 5.5 验收场景

- 普通熔炉只执行 smelting 配方。
- 烟熏炉只执行 smoking 配方。
- 高炉只执行 blasting 配方。
- 白名单只允许指定 ID；黑名单可靠排除指定 ID。
- 无中枢时维持当前女仆背包流程。
- 有中枢时从原料仓取材并把成品送入成品仓。
- 缺燃料、输出槽/仓库满、炉内错误材料、炉子被破坏时无物品损失。
- 多台不同炉型、多名女仆和任务切换时不串用计划或工作目标。
- 既有经验记录和燃料容器行为不回退。

## 6. 实施顺序

推荐顺序：

1. 先将内部配方计划升级为保存 `RecipeHolder`，建立熔炉所需的可靠基础。
2. 独立实现砧板残留材料接管，并验证不会影响普通砧板流程。
3. 在现有通用移动架构上实现熔炉的中枢与过滤支持。
4. 每一阶段分别执行单元测试、`clean build`、开发服务器启动和对应游戏内矩阵；
   不把三个改动压成一个难以回退的提交。

## 7. 本轮明确排除

- Kitchen Karrot 任务恢复。
- Cuisine Delight 成品立即回仓。
- 配方排序 UI 和库存最少优先策略。
- Handcrafted、Tom's Storage 等新增仓储兼容。
- 候选分支的低一格站位与旧寻路实现。
- 依赖版本、版本号或 tag 更新。
- 整体同步、merge 或 cherry-pick 候选分支。

本文件是候选审查和范围记录，不代表上述三项已经实施或通过游戏内验证。
