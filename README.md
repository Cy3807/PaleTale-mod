# Paletale 苍白档案模组 / Paletale Companion Mod

> 非官方 Minecraft 客户端模组，与 Mojang / Microsoft 无关。
> *NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.*

Paletale 苍白档案模组，基于 SMC 服务器。它是 [苍白档案](https://www.paletale.cn) 的游戏内客户端模组：在游戏里查询网站公开的物品、玩家、广告、商店与价格，绑定箱子商店，主城导航，收藏玩家上下线提醒等。

- 平台：Fabric，**仅客户端**（服务器无需安装）
- 版本：Minecraft 1.21.11（主线）；1.21.8 兼容构建见 `build-1.21.8.sh`
- 依赖：Java 21、Fabric Loader ≥ 0.16、Fabric API；可选 Cloth Config + Mod Menu（设置界面）、Xaero 小地图/世界地图（路线联动）
- 许可：源码 [MIT](LICENSE)；美术、名称、内置数据另行说明，见 [ASSETS.md](ASSETS.md)

## 安装（玩家）

1. 从[官方发布渠道](https://www.paletale.cn)下载对应游戏版本的 JAR，放入 `mods/`。
2. 进入游戏后按 **H** 或输入 `/paletale` 打开。按键可在「选项 → 控制 → 按键绑定 → PaleTale」修改。

## 隐私与安全（请先读）

模组会连接苍白档案网站，并**在你登录后**上传部分游戏内信息。具体上传什么、如何关闭，见 [PRIVACY.md](PRIVACY.md)。
安全模型与漏洞报告方式见 [SECURITY.md](SECURITY.md)。

要点：
- 登录使用 Mojang 官方会话验证；**Minecraft 访问令牌只交给本机 Authlib，不会发给 PaleTale**。
- 只在 SMC 服务器入口（如 `play.simmc.cn`）启用登录；单人世界与其他服务器不会触发。
- 被动读取：已加载的告示牌、玩家列表、转服包、Boss 栏/Tab 文本等客户端已有数据。
- **主动操作（仅在你使用对应功能时）**：导航传送、远征世界切换会替你发送服务器命令（如 `/m`），并在**隐藏的服务器菜单**里点击对应条目；领地/频道图标功能会打开并读取服务器菜单后关闭。命令有节流队列。**不会**自动攻击、挖掘、放置，也不会在你没触发时操作背包。若你所在服务器禁止此类自动化，请在设置中关闭相关功能或不要使用导航。

## 从源码构建

```sh
./gradlew build            # 构建 + 回归测试
./gradlew runClient        # 开发客户端
./gradlew runClientGameTest
```

需要 JDK 21。构建输出在 `build/libs/`。参与方式见 [CONTRIBUTING.md](CONTRIBUTING.md)。

### 指向自己的后端

默认连接官方站。开发时可用 JVM 参数 `-Dpaletale.site=local` 指向 `http://127.0.0.1:3000`。出于安全，客户端只允许固定的官方域名与 localhost；若要自建后端，需要修改 `SiteConfig` / `MarketApi.isAllowedSite` 中的白名单并自行承担账号安全责任。后端 API 为私有实现，**本仓库不含服务端代码**。

> 注意：自动更新功能只会从官方站下载官方构建。自行修改的 fork 请在发布前关闭或改写 `ModUpdate`，见 [SECURITY.md](SECURITY.md)。

## 关于本仓库

本仓库是发布时从维护者的私有仓库同步出来的**源码快照**，内容对应已发布的版本，之后的开发提交不会逐条出现在这里；历史记录从首次公开开始。暂不接受代码 PR，欢迎提 Issue。

## English summary

Paletale Companion (Paletale 苍白档案模组) is a **client-side-only Fabric mod** built around the SMC Minecraft server. It shows data published on the Paletale website (items, players, market ads, shops/prices) in-game, binds chest-shop signs, offers city navigation, and notifies you when favorited players go online. It talks to `https://www.paletale.cn` only. Login uses Mojang's session service; your Minecraft access token never leaves your machine. See `PRIVACY.md` for exactly what is uploaded, `SECURITY.md` for the threat model, and `ASSETS.md` for artwork/data licensing (the MIT license covers source code only). The backend is not part of this repository.
