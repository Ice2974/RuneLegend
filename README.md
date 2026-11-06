# RuneLegend-Core

RuneLegend-Core 是一个面向《圣符传说》第三方关卡开发者的 Fabric 模组。它在 Fabric 端上复刻了圣符传说的核心战斗系统——令牌、格挡体力，并提供游戏流程控制等功能。\
关卡作者可以直接通过命令、数据包或 Java API 搭建自己的圣符传说关卡，而不必自己实现这些机制。

《圣符传说》原是 EaseCation 服务器上的四人合作 PvE 剧情玩法，背景与规则可参考[官方 Wiki](https://wiki.easecation.net/%E5%9C%A3%E7%AC%A6%E4%BC%A0%E8%AF%B4)。\
**本项目是独立的社区第三方实现，与 EaseCation 官方无关。**

## 功能一览

- **游戏流程控制**：`WAITING`/ `RUNNING` 两种状态。只有等待状态能更换令牌装备；每次状态切换都会清空回合内的令牌运行状态，开始新局会推进回合计数。状态、回合数和修订号保存在世界存档中，单人退出重进或服务器重启后不会自动回到等待状态。
- **令牌系统**：每名玩家 3 个令牌槽，装备持久化到世界存档。内置 4 种令牌（见下表），全部由服务器权威判定，死亡、断线与回合重开时自动清理运行时效果。动作令牌可以相应基于 MC 原版的 `blocks_attacks` 组件的格挡。
- **体力系统**：格挡持续消耗体力，体力耗尽自动收剑，不足 10% 时无法起手。数值全部在服务器结算，客户端只负责显示。
- **客户端 HUD**：准星下方展示令牌槽位图标（就绪 / 触发 / 生效 / 冷却倒计时）与体力百分比，无需资源包即可使用。

## 内置令牌

| ID | 名称 | 效果 |
| --- | --- | --- |
| `runelegend:sharpened_tip` | 强化刃尖 | 直接近战命中 50% 概率额外造成 2 HP 真伤 |
| `runelegend:soul_siphon` | 灵魂摄取 | 击杀敌对目标 5% 概率永久增加 2 点最大生命 |
| `runelegend:echo` | 残响 | 击杀敌对目标 50% 概率在其位置留下残响，持续 8 秒，每秒对残响半径 5 格内的敌对目标造成 2 HP 真伤 |
| `runelegend:immediate_counter` | 即刻反击 | 成功格挡后，随后 8 秒内直接近战每击额外造成 3 HP 真伤，冷却 12 秒 |

## 命令

所有命令都在 `/runelegend` 下。`token_id` 省略命名空间时默认为 `runelegend:`。

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/runelegend list` | 所有玩家 | 列出全部已注册令牌 |
| `/runelegend info <token_id>` | 所有玩家 | 查看令牌描述 |
| `/runelegend status` | 所有玩家 | 查看自己的体力与已装备令牌（含冷却剩余） |
| `/runelegend equip <token_id>` | 所有玩家 | 装备令牌，仅等待状态可用 |
| `/runelegend unequip <token_id>` | 所有玩家 | 卸下令牌，仅等待状态可用 |
| `/runelegend clear` | 所有玩家 | 清空自己的令牌，仅等待状态可用 |
| `/runelegend game status` | 所有玩家 | 查看游戏状态 |
| `/runelegend game start` | `runelegend:game`（默认权限等级 2） | 开始游戏 |
| `/runelegend game stop` | `runelegend:game`（默认权限等级 2） | 停止游戏并重置回合 |
| `/runelegend admin <player> …` | `runelegend:admin`（默认权限等级 3） | 以其他玩家为目标执行 `status` / `equip` / `unequip` / `clear` |

## 关卡开发指南

### 快速开始

1. 服务端与所有客户端安装本模组和 Fabric API（模组在握手阶段强制要求客户端安装）。
2. 玩家在等待状态用 `/runelegend equip` 配置令牌，或由管理员用 `/runelegend admin` 统一配装。
3. 用 `/runelegend game start` 开始游戏（可直接放进命令方块）。
4. 关卡结束时用 `/runelegend game stop` 停止：回合与令牌运行状态重置、体力回满，玩家可以重新配装进入下一轮。

### 通过数据包定制

模组内置的数据标签都可以用自己的数据包覆盖或追加：

| 标签 | 类型 | 作用 | 默认值 |
| --- | --- | --- | --- |
| `runelegend:token_enemies` | 实体类型 | 令牌的“敌对目标”判定范围 | 空；为空时回退为原版 `Enemy` 生物 |
| `runelegend:bosses` | 实体类型 | Boss 判定 | `minecraft:ender_dragon`、`minecraft:wither` |
| `runelegend:ghost_family` | 实体类型 | 鬼魅生物判定 | 空 |
| `runelegend:sword_guardable` | 伤害类型 | 可以被剑格挡的伤害 | 近战、箭、蜂刺、爆炸等 7 种原版伤害 |
| `runelegend:sword_guard_bypasses` | 伤害类型 | 绕过剑格挡的伤害 | `#minecraft:bypasses_armor`、`#minecraft:bypasses_invulnerability`、`runelegend:token_damage` |

例如给自己的自定义实体开启令牌效果：将下面的文件放进数据包的 `data/runelegend/tags/entity_type/token_enemies.json`（内置标签均为 `replace: false`，可与数据包合并）：

```json
{
  "replace": false,
  "values": ["mymod:ghost_warrior"]
}
```

### 通过 Java API 集成

其他服务端模组可以直接控制游戏流程：

```java
import com.runelegend.api.RuneLegendApi;

// 必须在服务器线程调用（例如 Fabric 事件回调中），否则抛出异常
RuneLegendApi api = RuneLegendApi.get(server);

if (!api.getRules().running()) {
    api.startGame();   // 进入 RUNNING，回合数 +1，清空令牌运行状态
}

// RuleSnapshot 记录了状态、回合数与修订号（revision）
long round = api.getRules().round();
```

令牌框架本身也是可扩展的：每个令牌是一个 `TokenEffectHandler`，由 `TokenRegistry` 统一管理事件分发（近战、受击、击杀、箭、格挡、生命变化）与运行状态，可参考 `src/main/java/com/runelegend/token/effect` 中的内置实现。当前版本尚未提供运行时注册入口，自定义令牌需要在本仓库内实现并重新构建。

## 格挡与战斗机制

- 体力上限 20000（HUD 以百分比显示），起手格挡至少需要 2000（10%）。
- 格挡期间每刻消耗 125（每秒 2500），满体力可连续格挡约 8 秒，耗尽时自动收剑。
- 停止格挡 0.5 秒后开始恢复，每刻 100（每秒 2000），从 0 回满约需 10 秒。
- 格挡方向判定：近战取伤害来源位置，弹射物按其飞行轨迹反推，爆炸取爆心；方向不正确时按原版逻辑正常受伤。
- 如果不想使用盾牌，可以通过命令给予玩家不可破坏并且能格挡的铁剑，示例：
```
/give @s iron_sword[minecraft:unbreakable={},blocks_attacks={disable_cooldown_scale:0,damage_reductions:[{base:0,factor:0.7}]}]
```
- 如果需要还原基岩版战斗机制，可以将玩家的 `attack_speed` 调高：
```
/attribute @s attack_speed base set 23
```

## HUD 说明

- 令牌图标显示在屏幕底部中央上方：绿色边框表示就绪，橙色表示刚刚触发，青色表示效果生效中，灰色表示不可用；冷却中的图标会变暗并显示剩余秒数。
- 体力低于满值或正在格挡时，图标行上方显示体力百分比。
- 可用 JVM 属性 `-Drunelegend.hudOffset=<0-100>` 向上微调 HUD 位置，避免与其他模组 HUD 重叠。

## 网络与存储

- 协议握手在配置阶段完成并校验随机数，防止旧版本或伪造客户端混入；协议当前版本为 2。
- 令牌与体力状态采用“快照 + 增量”同步：常态只发送变化部分（体力最快每 4 刻一次），客户端检测到丢包失序会自动请求重同步，服务端每秒最多响应一次。
- 游戏状态保存在主世界维度数据目录的 `data/runelegend/game_rules.dat`（schema 1）。存档没有该文件时默认 `WAITING`、回合 0；令牌冷却与临时效果仍按回合运行数据处理，不跨重启保留。
- 令牌配置保存在主世界维度数据目录的 `data/runelegend/player_tokens.dat`（schema 1）。文件无法读取时进入只读模式：玩家可正常游玩，但写入会被拒绝并在日志中报告，不会用空数据覆盖已有记录。

## 从源码构建

```bash
git clone https://github.com/Ice2974/RuneLegend.git
cd RuneLegend
./gradlew build
```

- 需要 JDK 25（Gradle 工具链会自动选择），构建产物在 `build/libs/`，包含 sources jar。
- `./gradlew test` 只运行单元测试（`src/test`，JUnit 5）。
- `src/gametest` 是 Fabric GameTest 套件，覆盖令牌效果、格挡与 HUD 同步，通过 `build.gradle` 的 `fabricApi { configureTests { … } }` 启用。

## 依赖

| 名称 | 类型 | 链接 |
| - | - | - |
| Fabric API | 必须 | [Modrinth](https://modrinth.com/mod/fabric-api) |

## 版本支持

| 游戏版本 | 开发状态 | 最后支持版本 |
| --- | --- | --- |
| 26.3 | 维护中 |  |
