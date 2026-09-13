# TLM 1.5.3 通用烹饪任务链专项审计

## 1. 文档目的

本文合并记录以下两轮只读源码审查的结论：

1. “通用烹饪任务链没有随 TLM 1.5.3 一起统一升级”的根因调查。
2. 在该根因之外，对任务状态、设备认领、烹饪中枢、配方缓存、旧兼容任务和
   测试覆盖进行的遗留问题检索。

本文是后续集中修复、提交说明和回归测试的事实依据，不代表所列问题已经修复。
报告只记录源码证据和建议边界；本次记录没有修改任务实现、版本号、tag 或测试实例。

## 2. 审计基线

| 项目 | 当前值 |
| --- | --- |
| 仓库 | `DemSum/MaidsoulKitchen` |
| 分支 | `brewery/1.21.1-slim` |
| 审计 HEAD | `6bb4045e0b247eec013f972ca29c81a12360349f` |
| 审计日期 | 2026-09-12 |
| Minecraft | 1.21.1 |
| TLM 编译/最低运行基线 | 1.5.3 |
| 正式 MSK 来源基线 | 1.21.1 beta 0.1.4 sources |
| 架构参考 | `Wall-ev/MaidsoulKitchen:1.20.1-1.0-dev` |

相关历史检查点：

- `a2d4d2eb`：`Refactor cooking work and walk targets`，引入通用烹饪双坐标。
- `4554e99f`：`Migrate slim to TLM 1.5.3 crop API`，提升编译和运行基线。
- `25040fc1`：浆果任务改用 TLM 1.5.3 当前可达性接口。
- `34162b5b`：合并过一轮通用目标搜索与轮换尝试。
- `6bb4045e`：回退工作期移动限制，恢复此前烹饪行为，同时也恢复了通用手写 BFS。

## 3. 执行摘要

当前问题不能归结为 FD 厨锅、DrinkBeer 酒桶或蒸笼各自的单点兼容错误。
根本边界是：项目虽然把依赖、特殊作物 API、女仆物品栏调用和蒸笼搜索提升到了
TLM 1.5.3，但**通用烹饪任务链仍保留自维护的节点遍历、旧配方管理器和缺少任务
归属的共享坐标状态**。

因此，目前同时存在三种不同演进阶段的实现：

1. 通用厨锅、酒桶等使用 `MaidCookMoveTask + ReachableCookDeviceSearch` 手写 BFS。
2. 蒸笼使用 TLM 1.5.3 官方 `MaidPathFindingBFS`，但保留自己的多层候选规则和仓储层。
3. 浆果任务通过 TLM 1.5.3 官方共享可达性入口运行。

这会造成编译、纯几何单元测试和开发服务器启动均通过，但实机中出现通用任务不扫描、
女仆选择错误设备、多个女仆争抢同一设备、配置更新延迟以及中枢不同任务看到不同物品
等问题。

集中修复不能只替换一行 BFS 调用。最低可靠范围应同时覆盖：

- TLM 1.5.3 官方 BFS 生命周期；
- 任务 UID 所有权；
- 烹饪移动目标与其他移动目标的区分；
- 多女仆设备认领和释放；
- 配方/过滤缓存失效；
- 中枢逻辑输入视图统一；
- 真实 EntityMaid/TLM 寻路集成测试。

## 4. 首轮审查：通用任务链没有统一升级到 TLM 1.5.3

### 4.1 已确认根因：通用任务仍使用手写 NodeEvaluator BFS

[`ReachableCookDeviceSearch`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/common/ai/ReachableCookDeviceSearch.java)
直接获取导航的 `NodeEvaluator`，自行建立 `PathNavigationRegion`、队列、已访问集合和
邻居数组，再手动调用 `prepare/getStart/getNeighbors/done`。

该实现源于 `a2d4d2eb` 的双坐标重构。当时它用“从可达站立节点反查相邻厨具”解决了
女仆站上厨具的问题，但它没有完整复用 TLM 1.5.3 `MaidPathFindingBFS` 对搜索生命周期、
导航评估器和缓存型评估器的处理。`6bb4045e` 回退移动限制时又恢复了这条实现路径。

影响：

- 在真实 TLM 导航器上可能无法得到与官方搜索一致的节点结果。
- 测试只验证坐标函数，无法发现评估器生命周期问题。
- 每次 TLM 更改导航内部细节时，MSK 的复制逻辑都可能再次漂移。

### 4.2 搜索语义与 TLM 官方语义不一致

通用搜索要求落脚节点和设备：

- 位于同一 Y 层；
- 仅四向相邻；
- 两者都在限制区域和搜索边界内。

这与 TLM 任务通常使用 `canPathReach(devicePos)` 或
`canPathReach(devicePos.above())` 判断候选目标的语义不同。高厨具、非完整方块、
楼层边缘和设备上方可达等布局会得到不同结果。

双坐标模型本身仍应保留：`WORK_POS` 是真实设备坐标，`WALK_TARGET` 是落脚坐标。
需要替换的是搜索实现和候选适配方式，而不是退回“设备坐标同时作为落脚点”。

### 4.3 `WALK_TARGET` 会阻止烹饪扫描

[`MaidCookMoveTask`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/common/ai/MaidCookMoveTask.java#L30)
和蒸笼移动任务都要求 `WALK_TARGET` 不存在才会启动。空闲移动、日程移动或休息行为
占据该记忆时，即使工作范围内已有待操作设备，烹饪扫描也可能被延后。

这不能通过禁止全部游荡解决。后续应明确区分：

- 烹饪任务自己写入的 `WALK_TARGET`；
- TLM 日程、休息或随机移动写入的目标；
- 已认领工作设备后需要恢复的补充寻路目标。

### 4.4 蒸笼已经使用官方 BFS，但不能原样取代其候选规则

[`SteamerApproachSearch`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/kaleidoscopecookery/SteamerApproachSearch.java)
已经直接创建 TLM 1.5.3 `MaidPathFindingBFS`，并在 `finally` 中调用 `finish()`。

但是，蒸笼会从一个可达落脚节点检查 `0、±1、±2、±3` 层的蒸笼，以覆盖 KC 1.4.1
最多四层的热传播和交互范围。集中修复应统一“遍历引擎、生命周期、认领和状态写入”，
同时保留不同任务自己的设备候选适配器；不能把蒸笼强制收缩为通用同层四向规则。

## 5. 第二轮审查：状态与任务调度遗留问题

### 5.1 [高] 共享工作坐标没有任务所有者

[`MkMemories`](../../src/main/java/com/github/wallev/maidsoulkitchen/init/MkMemories.java#L16)
只注册 `DESTROY_POS`、`WORK_POS` 和 `COOK_WALK_POS`，没有保存当前工作目标所属的
任务 UID。

[`MaidRunOneMixin`](../../src/main/java/com/github/wallev/maidsoulkitchen/mixin/compat/touhoulittlemaid/MaidRunOneMixin.java#L29)
只检查女仆当前是否属于任意 `ICookTargetTask`。从 FD 厨锅切换到蒸笼，或从蒸笼切换
到另一个烹饪任务时，旧目标可能因为“新任务仍是烹饪任务”而被保留。

源码层面已确认这是状态模型缺口；它与“蒸笼任务女仆转而定位 FD 厨锅”的实机现象
高度相关，但仍需任务切换集成测试建立一一对应的运行证据。

建议：在统一状态中保存任务 UID/assignment owner；读取、补路、制作和清理时均校验
确切 UID，而不是只校验任务接口类型。

### 5.2 [高] 通用任务没有多女仆设备认领

[`CookWorkLocks`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/common/ai/CookWorkLocks.java)
目前由蒸笼使用；通用 `MaidCookMoveTask` 搜索成功时不会认领设备。

后果：

- 多名女仆可以同时选中同一厨锅或酒桶。
- 16 个并排酒桶等密集布局中，多名女仆可能集中竞争 BFS 首个候选。
- 同一设备的输入、容器和成品可能被不同女仆交叉处理。
- 其他同样可用的设备可能长期得不到服务。

统一搜索应执行“候选验证 -> 原子认领 -> 写入工作状态”。任务停止、目标失效、任务
切换、女仆卸载和服务器停止时都必须释放；超时只能作为异常兜底。

### 5.3 [中高] 第一可用候选没有操作优先级和公平性

当前搜索遇到第一个 `shouldMoveTo` 为真的设备就结束，没有区分：

1. 已有成品需要取出；
2. 缺碗、杯子或其他输出容器；
3. 有返回桶/错误输入需要回收；
4. 空设备等待投料；
5. 正在加工、暂时无需交互。

路径恢复后，这一策略仍可能反复服务最近的低优先级设备。建议任务暴露轻量级动作类型
或优先级，并在同一 BFS 距离层内优先处理成品和容器，再处理投料。不得为了全局排序而
对每个设备分别创建路径。

### 5.4 [中] 工作锁生命周期仍不完整

现有锁保存 600 tick，过期记录只在下一次 `tryClaim` 时清扫。没有独立的女仆卸载、
任务切换或服务器停止清理入口。弱服务器键可以降低永久内存泄漏风险，但异常中断仍可
让设备最多阻塞约 30 秒。

任务所有者状态与通用认领完成后，应同步补齐显式生命周期；不要单独继续堆叠超时补丁。

## 6. 配方、过滤与烹饪中枢遗留问题

### 6.1 [高] 通用黑白名单和配方缓存不会可靠失效

[`MaidRecipesManager.initTaskData`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/common/inventory/MaidRecipesManager.java#L179)
只在 `lastTaskRule` 或 `recipeIds` 为 `null` 时读取任务配置。模式、名单和数据包配方
集合改变后，`rec` 中的配方值不会主动重建。

另外：

- 有未消费的 `recipesIngredients` 时，管理器会在重新检查配置前直接返回。
- 配方数据包重载后没有看到刷新 holder/计划的资源重载钩子。
- 库存快照只比较物品类型和数量，没有比较 1.21.1 data components。
- 当前计划保存 recipe value `R`，过早丢弃 `RecipeHolder.id()`。

这会出现“UI 已更新，但女仆仍执行旧规则”“同物品不同组件未触发重算”以及重载后继续
使用旧配方计划等现象。

建议：内部计划保存 `RecipeHolder<R>`；用任务数据 revision、recipe reload revision
和完整物品组件指纹驱动失效。配置网络包成功保存后应使当前任务计划失效，但不应通过
整只女仆频繁 `refreshBrain` 实现。

### 6.2 [高] 简化后的中枢存在两种逻辑输入视图

[`BagType.INPUT_VALS`](../../src/main/java/com/github/wallev/maidsoulkitchen/inventory/container/item/BagType.java#L15)
把四个旧输入区段合并为逻辑输入区。通用
[`CookBagInventory`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/common/inventory/CookBagInventory.java#L120)
也确实组合读取这些区段。

但蒸笼使用的
[`CulinaryHubWorkStorage.ingredients`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/common/inventory/CulinaryHubWorkStorage.java#L55)
只返回 `BagType.INGREDIENT`，即最前面的 27 格。

因此旧中枢后三个输入区段中的物品：

- 通用任务可见；
- 蒸笼不可见；
- UI 简化后用户难以理解为何同一“原料区”行为不同。

后续应提供唯一的 logical input handler，通用任务与蒸笼共同使用，同时保留旧物品数据
区段和槽位编号，避免破坏存档。

### 6.3 [中] 绑定箱子变化只通过延迟重扫被发现

`MaidRecipesManager.isLastCookInv()` 主要比较女仆或中枢内部库存快照，没有把所有绑定
箱子的变化纳入当前计划版本。新材料放入绑定箱子后，管理器可能经过多轮空检查才重新
执行 `mapChestIngredient()`。

该问题更接近响应延迟而非永久失效。修复时应使用低频、有限成本的绑定库存版本/摘要，
不能退回每 tick 全箱扫描。

### 6.4 [中] 通用配置网络包的校验弱于蒸笼

蒸笼过滤包限制列表大小、过滤未知配方 ID，并验证女仆所有权。通用
[`SetCookDataC2SPackage`](../../src/main/java/com/github/wallev/maidsoulkitchen/network/message/SetCookDataC2SPackage.java)
和
[`ActionCookDataRecC2SPackage`](../../src/main/java/com/github/wallev/maidsoulkitchen/network/message/ActionCookDataRecC2SPackage.java)
虽然验证所有权，但没有验证：

- mode 是否为合法枚举值；
- data key 是否存在并属于当前候选任务；
- recipe ID 是否合法且属于该任务配方类型；
- 列表大小和重复项。

异常或旧数据可能把 mode 置为无法识别的字符串，随后得到空配方集合并静默停工。

## 7. 恢复通用任务后会重新暴露的旧兼容缺陷

这些问题不一定由 TLM 1.5.3 迁移直接引入，但在通用任务恢复工作后会重新进入实际执行
路径，不能以“寻路已修复”作为发布稳定的充分条件。

### 7.1 [严重] Barbeque's Delight 盆任务完成状态异常

[`MaidBasinMakeTask`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/barbequesdelight/MaidBasinMakeTask.java#L102)
保存了配方 `time`，但 tick 阶段没有使用它作为完成条件。只要配方仍能匹配，代码会每
5 tick 组装并尝试插入一次输出，也没有在成功后清空本轮输入状态。

风险包括重复产物、持续执行以及输出满时静默丢弃插入余量。该任务应单独建立最小复现，
在确认第三方设备真实状态语义后修复，不能只添加一次布尔保护。

### 7.2 [高] 多条输出/容器路径不是无损事务

已确认的源码路径：

- [`TaskFurnace`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/minecraft/TaskFurnace.java#L179)
  先清空炉子输出槽，再忽略目标库存的插入余量。
- [`MaidCuisineMakeTask`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/cuisine/MaidCuisineMakeTask.java#L196)
  消耗盘子并重置锅具，但忽略成品插入余量。
- [`TaskYhcTeaKettle`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/youkaishomecoming/TaskYhcTeaKettle.java#L180)
  在交互前拆出水资源；交互 PASS、异常或背包已满时没有完整回滚。
- [`TaskYhcFermentationTank`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/youkaishomecoming/TaskYhcFermentationTank.java#L309)
  部分返回容器插入路径忽略 remainder。
- [`BerryHandler`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/farm/handler/berry/BerryHandler.java#L50)
  先把原工具槽清零，再交给 FakePlayer；失败或返回物无法插入时缺少完整回滚。

统一原则应为：先模拟目标容量，再执行源提取/设备清空；执行后检查 remainder；失败时
回源，回源失败才在明确位置生成掉落物并记录诊断。不得先清空源再假设插入必定成功。

### 7.3 [中高] FD 砧板不能接管残留材料

[`TaskFdCuttingBoard.shouldMoveTo`](../../src/main/java/com/github/wallev/maidsoulkitchen/task/cook/farmersdelight/TaskFdCuttingBoard.java#L64)
只在砧板为空且已有新材料计划时选择目标。玩家放入、任务中断或异常留下的可切割材料
不会被重新接管。

该缺口已在 `lezizijiang2-feature-audit.md` 中记录，仍未实施。

### 7.4 [中高] 熔炉尚未完成中枢、炉型与过滤统一

当前熔炉任务仍没有按实际普通熔炉/烟熏炉/高炉的 recipe type 建立可靠
`RecipeHolder` 计划，中枢支持也未完成。输出满时的无损提取问题见 7.2。

应在配方计划升级后实现，不能新增第二套世界扫描或专用移动架构。

## 8. 兼容保留项和维护债务

### 8.1 API v2 保留但应冻结

`src/main/java/com/github/wallev/maidsoulkitchen/api/task/v2/` 当前没有项目内部调用者，
但可能有第三方二进制或源码依赖。根据维护者决定，不删除该目录和旧公开接口。

建议策略：

- 标记为 legacy/frozen；
- 不让新任务继续接入；
- 为关键公开签名增加兼容测试；
- 若内部实现重构，使用适配层而不是让 v2 与新状态模型共同拥有运行状态。

### 8.2 旧任务数据键继续保留，但活动任务清单需要标识

`DataRegister` 和配置中仍存在当前 `TaskRegister` 没有注册实现的 FR、MD、BNC、CP、KK
等任务数据键或开关。为旧存档兼容可以保留，但文档和配置应明确区分：

- 当前活动任务；
- 兼容保留数据键；
- 未来候选任务；
- 已停用且没有运行实现的条目。

在有数据迁移方案前不得直接删除旧键。

### 8.3 FakePlayer 创建缺少显式服务端保护

[`EntityMaidMixin.tlmk$initFakePlayer`](../../src/main/java/com/github/wallev/maidsoulkitchen/mixin/compat/touhoulittlemaid/EntityMaidMixin.java#L34)
直接把 `level()` 转为 `ServerLevel`。现有调用大多应发生在服务端，但接口自身没有保护。
这属于需要验证的风险，尚未发现客户端实机崩溃证据。

## 9. 文档与测试偏差

### 9.1 现有成果文档的结论已经过期

`docs/maintenance/current-work-results.md` 当前写有：

- 自维护旧版寻路遍历已删除；
- 通用双坐标完整清理已经成立；
- slim 已统一使用 TLM 1.5.3 官方 BFS。

这些描述对浆果和蒸笼成立，但对通用 `MaidCookMoveTask` 不成立。本文取代上述内容中
有关“通用搜索已完成官方 BFS 迁移”的结论。待源码修复和实机验证完成后，再更新成果
文档和 README；不能现在把计划项写成已完成。

`docs/maintenance/lezizijiang2-feature-audit.md` 中“保留当前
ReachableCookDeviceSearch”的建议也已被本次实机诊断和源码审查推翻。

### 9.2 当前测试无法覆盖真实故障

现有测试主要覆盖：

- 坐标几何函数；
- 纯状态枚举；
- 兼容清单结构；
- 类文件中是否出现 TLM 1.5.3 API 符号；
- 蒸笼层高和热传播纯逻辑。

尚缺：

- 真实 `EntityMaid` 导航器和 TLM `MaidPathFindingBFS`；
- 缓存型 `NodeEvaluator` 生命周期；
- 日程/游荡 `WALK_TARGET` 与烹饪扫描竞争；
- FD 厨锅、酒桶、蒸笼之间的任务切换；
- 多名女仆和 16 个并排设备的认领分配；
- 配方黑白名单在线修改和数据包重载；
- 中枢旧输入区段及绑定箱子变化；
- 输出仓满载时的无损事务；
- 女仆和区块重载后的状态、锁和计划清理。

因此“单元测试、clean build、服务器启动通过”只能证明编译和加载基线，不足以证明
通用烹饪任务可用。

## 10. 推荐集中修复顺序

### P0：冻结边界与建立失败复现

- 保持当前版本号和 tag 不变。
- 不修改两个 CurseForge 实例中的 JAR。
- 记录 FD 厨锅、DrinkBeer 酒桶、蒸笼各一个最小失败布局。
- 增加只在开发环境启用的目标搜索/认领诊断，避免常规运行刷日志。

### P1：统一官方 BFS 遍历层

- 删除通用任务的手写 `NodeEvaluator` BFS。
- 建立一个薄的 TLM 1.5.3 `MaidPathFindingBFS` 适配器，统一 `finish()` 生命周期。
- 允许任务提供候选设备映射：通用设备、设备上方语义、多层蒸笼等。
- 一次遍历中检查多个设备，禁止逐设备完整寻路和每 tick BFS。

### P2：统一工作状态与设备认领

- 状态至少包含 `taskUid`、`workPos`、`walkPos`，锁记录 maid UUID。
- `LOOK_TARGET/TARGET_POS/DESTROY_POS/WORK_POS` 始终指向真实设备。
- `WALK_TARGET/COOK_WALK_POS` 指向实际落脚点。
- 严格验证任务所有权、设备类型、目标仍需工作和锁所有者。
- 区分烹饪移动目标与普通日程/游荡目标。
- 为多设备增加动作优先级和同级公平策略。

### P3：修复配方计划和中枢一致性

- 内部计划保存 `RecipeHolder` 和任务/配置/重载 revision。
- 通用配置包完成枚举、任务键和配方 ID 校验。
- 中枢提供唯一 logical input handler，覆盖全部旧输入区段。
- 以低频版本/摘要检测绑定箱子变化，不进行每 tick 全量扫描。

### P4：补齐原 MSK 功能性缺口

- FD 砧板残留材料继续加工。
- 熔炉按炉型过滤并支持中枢。
- 逐项修复输出、容器和 FakePlayer 交互的无损事务。
- 单独审查 BBQ 盆任务的计时、消费和完成状态。

### P5：集成验证

- 单元测试、`clean test build`、开发服务器启动。
- 在 `D:\overwolf\curseforge\minecraft\Instances\1.21.1tests` 由维护者手动替换 JAR
  并执行测试矩阵。
- 测试分支稳定后，再由维护者决定是否进入
  `D:\overwolf\curseforge\minecraft\Instances\0828Maid` 正式实例验证。
- 实机完成前，不更新版本号、不移动 tag、不删除 Public 临时坐标 Mixin。

## 11. 必须覆盖的回归矩阵

### 搜索和坐标

- 普通单方块厨具、有朝向和无朝向厨具。
- 高厨具、设备上方有楼层、设备后方一格有墙。
- 四周部分阻挡、只剩一个合法侧面、完全不可达。
- 移动途中 `WALK_TARGET` 存在、被 TLM 正常消费或中断。
- 目标被破坏、卸载、替换或变成无需操作状态。

### 任务状态

- FD 厨锅 -> 蒸笼 -> DrinkBeer 连续切换。
- 工作、休息、日程、随机小范围移动之间切换。
- 女仆重载、区块卸载重载和服务器重启。
- 验证任何任务都不能继承其他任务的 `WORK_POS`。

### 多设备与多女仆

- 16 个并排 DrinkBeer 酒桶。
- 多个 FD 厨锅同时处于成品、缺碗、空锅和加工中状态。
- 两名及以上女仆同时寻找同类设备。
- 验证认领分散、成品优先、无重复投料且不存在长期饥饿。

### 中枢与配方

- 六行旧逻辑输入区的每一行都能被通用任务和蒸笼读取。
- 绑定普通箱、兼容存储和满载输出箱。
- 在线切换黑/白名单、添加和删除配方 ID。
- 数据包重载后旧计划失效。
- 女仆无需靠近绑定箱即可通过中枢 item handler 工作，但仍需靠近真实厨具交互。

### 无损事务

- 女仆背包满、中枢输出满、绑定输出仓满。
- 容器不足、容器返回、交互 PASS 和 FakePlayer 抛出异常。
- 任一失败都不能删除输入、成品、杯碗、桶、盘子或工具。

## 12. 预计涉及文件（尚未修改）

集中修复预计主要涉及：

- `task/cook/common/ai/MaidCookMoveTask.java`
- `task/cook/common/ai/ReachableCookDeviceSearch.java`（预计删除或降为纯适配器）
- `task/cook/common/ai/CookTargetMemory.java`
- `task/cook/common/ai/CookWorkLocks.java`
- `task/cook/common/ai/MaidCookPathingTask.java`
- `mixin/compat/touhoulittlemaid/MaidRunOneMixin.java`
- `init/MkMemories.java`
- `task/cook/common/inventory/MaidRecipesManager.java`
- `task/cook/common/inventory/CookBagInventory.java`
- `task/cook/common/inventory/CulinaryHubWorkStorage.java`
- 通用配置网络包及对应测试
- 蒸笼候选适配层和通用/蒸笼集成测试

任务专属事务修复应拆分成后续独立提交，避免与核心寻路重构形成一个难以定位、难以
回退的大提交。

## 13. 当前结论与状态

- **已确认：** 通用烹饪任务没有完整迁移到 TLM 1.5.3 官方 BFS。
- **已确认：** 共享坐标没有任务 UID，通用任务没有设备认领。
- **已确认：** 通用配方缓存缺少可靠失效，中枢输入视图不一致。
- **已确认：** 多条旧兼容任务存在忽略插入余量或失败不回滚的源码路径。
- **高度相关但仍需实机定因：** 串任务目标、16 酒桶竞争和部分原 MSK 任务停工的每个
  具体现象与上述缺口之间的对应关系。
- **本报告未实施修复。** 后续应按 P0-P5 顺序集中处理，避免继续为单个厨具叠加互相
  覆盖的小补丁。
