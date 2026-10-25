# 无界算核 (Neo Eco Cpu Ex)

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-3C8527?style=flat-square)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.244%2B-EA7B2B?style=flat-square)
![AE2](https://img.shields.io/badge/AE2-19.2.18%2B-2E7D32?style=flat-square)
![Neo ECO AE](https://img.shields.io/badge/Neo%20ECO%20AE-21.2.9-7B1FA2?style=flat-square)
![Author](https://img.shields.io/badge/Author-Famgan-4A90E2?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

> **AE2 的算力终点，从 8 EB 开始，到这里为止。**
>
> **无界算核**（Neo Eco Cpu Ex）是面向 Neo ECO AE 的终极算力扩展。它把计算字节池从 `long` 推进到任意精度，让超过 `Long.MAX_VALUE` 的合成订单不再因为容量溢出而失败。

原版 AE2 与 Neo ECO AE 的容量、订单和合成计划最终都落在 64 位整数上。8 EB 在大型整合包里并不是遥不可及的数字，而是一个会突然打断终局自动化的天花板。本模组拆掉这层天花板，同时保留边界值、订单队列和界面显示的可预期行为。

**作者：** Famgan

## 核心功能

- **无限容量，越过 8 EB 天花板**  
  计算字节池使用任意精度整数。超过 `Long.MAX_VALUE` 的合成计划可以继续参与计算，不会再提示“无法执行不完整的合成计划”。

- **超大订单也能排队**  
  仅因缺料而无法执行的巨型计划，不再被直接拒绝，而是转入 `WAITING_MATERIALS` 父订单。材料补齐后自动重新规划，超大合成可以像普通订单一样等待资源。

- **精确边界与防溢出保护**  
  底层使用精确的 BigInt 运算，接口边界用专用哨兵值隔离溢出。真实容量恰好等于 `Long.MAX_VALUE` 时仍正常显示 `8 EB`；只有严格超过上限才显示 `无限`。已用与占用显示 `无意义`，不会把普通满配误判成无限。

- **全界面统一适配**  
  计算控制器、AE2 CPU 列表和合成确认页共用同一套溢出显示规则，容量、空闲与已用状态保持一致。

## 依赖项

| 必需依赖 | 版本 |
| :-- | :-- |
| **Minecraft** | **1.21.1** |
| **NeoForge** | **21.1.244+** |
| **Neo ECO AE Extension** | **21.2.9** |
| **Applied Energistics 2** | **19.2.18+** |

## 安装

安装 NeoForge 与上表依赖，将本模组的 jar 放入客户端和服务端的 `mods` 目录，并保持两端版本一致。

## ⚠️ 注意事项与兼容性

> **安装或升级前，请先备份存档。**  
> 超大订单可能长时间占用服务端计算资源。没有可恢复备份的存档，不建议直接安装。

- **客户端与服务端都需要安装**，并保持版本一致。
- 本模组会修改 Neo ECO AE 与 AE2 的合成计划、容量计算和界面逻辑。
- 与其他修改 AE2 或 Neo ECO AE 界面的模组可能冲突。遇到问题时请附上模组列表和 `latest.log`。
- 升级前移除旧 jar，避免重复加载。
- AE2 或 Neo ECO AE 大版本更新后，请先确认本模组已适配。

<details>
<summary>技术说明</summary>

本模组通过 Mixin 介入 Neo ECO AE 的容量计算、订单准入和 UI 数据边界，并使用 `LongArrayBigInt` 完成精确的任意精度字节运算。原版要求 `long` 的接口保留饱和值，显示层使用专用哨兵区分真实的 `8 EB` 与超过上限的无限容量。超大缺料计划会转入 `WAITING_MATERIALS` 父订单，并在同步与渲染路径上处理容量溢出。

</details>