# 更新日志 / Changelog

## 0.2.0 beta — 2026-11-03

Minecraft 1.21.1 · NeoForge

### UI 进一步跟进 1.20.1

- 改进统一烹饪入口的厨具选择、搜索、翻页及白名单/黑名单操作。
- 修复设置同步后页面仍显示旧选项的问题，保留当前搜索文本、页码与光标状态。
- 改进普通厨具、KC 蒸笼/汤锅及农业设置页面的连续操作与保存。
- 补齐相关厨具配置入口，修复 Cloth 配置完成后未保存到磁盘的问题。
- 修复 YHC 发酵配方成品图标空白，复用对应瓶装物品显示成品名称。

### Kaleidoscope Cookery 汤锅

- 完善汤锅任务对汤底、食材、锅盖和盛取容器的处理。
- 修复多汤锅工作中非首项无法正常执行的问题，支持多个工作锅轮换。
- 支持揭开半锅已有锅盖、补料后盖回，复用同一只锅盖。
- 支持使用女仆主手持有的锅盖，保留原生锅盖耐久与回收行为。

### 其他修复

- 改进工作设置的安全读取，以及已识别旧设置向当前格式的一次迁移；无法继续的旧工作安全停用并提示重新配置。
- 恢复甜浆果、发光浆果识别与原生采收，以及现有 YHC/L2 Harvester 的专用采收规则。
- 修复不同女仆或取消规则后仍沿用其他采收规则的问题，删除无用途的规则实例集合。
- 保留现有 TLM 1.5.3 适配、中枢及饰品栏识别、多层蒸笼、DrinkBeer 和寻路改进。

### 使用说明

- 联机时客户端与服务端应同时更新到此版本。
- 厨具注册开关按现有方式保存并重启生效。
- 本次未改变既有 Minecraft、NeoForge、TLM 或附属模组的依赖版本声明。

### English summary

- Bring the cooking settings UI and interactions closer to the 1.20.1 design, including selection, filtering, search, paging, and synchronized settings.
- Fix Cloth configuration saving and YHC fermentation result icons.
- Improve KC stockpot cooking, multi-pot rotation, partial-pot lid reuse, and main-hand lid handling.
- Improve saved-setting migration and safe loading, restore vanilla/L2 harvesting, and isolate each maid's harvesting rules.
- Keep the existing dependency declarations and tested platform adaptations. Update both client and server for multiplayer.
