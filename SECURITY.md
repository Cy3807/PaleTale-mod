# 安全政策 / Security

## 报告漏洞

请**不要**在公开 Issue 中披露可被利用的漏洞。请通过官网 [paletale.cn](https://www.paletale.cn) 公布的官方 QQ 群联系管理员私聊，或在 GitHub 仓库的 *Security → Report a vulnerability* 提交私密报告。我们会尽快确认。

## 威胁模型（给审计者）

- **令牌**：Minecraft `accessToken` 只传给本机 `MinecraftSessionService.joinServer`（Mojang）。网站只拿到一次性 challenge，并自行向 Mojang `hasJoined` 核验。
- **站点白名单**：`MarketApi.isAllowedSite` 只接受 `https://www.paletale.cn`、`https://preview.paletale.cn`、`https://pt.cy3807.com` 与本机 `http://localhost|127.0.0.1`；拒绝带用户信息/查询串/路径的地址，重定向默认不跟随。
- **服务器入口**：仅 `play.simmc.cn` 等已知入口及网站下发的至多 16 个入口会启用登录；入口列表只能从白名单站点读取。
- **被动读取**：Mixin 观察已处理的数据包（玩家列表、告示牌更新、转服包）；命令轮盘打开时屏蔽攻击/放置/破坏。
- **主动操作**（均由玩家点击触发，见 `Navigation`、`ExpeditionMenu`、`LandMenu`、`ChatChannelIcons`、`ClientCommandQueue`）：通过 `sendCommand` 发送服务器命令；打开服务器菜单后对容器发送 `handleInventoryMouseClick` / `ServerboundContainerClosePacket`。命令经 `CommandPacing` 限速、最多排队 16 条，并在连接切换时清空。这类行为可能被部分服务器的规则视为自动化，使用前请确认服务器规定。
- **反射**：仅用于可选联动（Xaero 地图、TownyMap、Gnetum），失败即降级。

### 已知风险与说明

1. **会话证明的信任**：任何「用 Mojang 会话做网站登录」的方案都意味着你信任该网站不会滥用 challenge。官方站由维护者运营；**请不要把此模组指向不受信任的后端**。
2. **自动更新（`ModUpdate` / `UpdateHelper`）**：玩家点击版本徽标后，模组从官方站取得短期签名 CDN 地址，下载后依次校验 SHA-256、`fabric.mod.json`（模组 ID/版本/游戏版本），以及**发布者离线 Ed25519 签名**（`UpdateTrust`，公钥内置于模组、私钥只在发布者本机），全部通过才会在游戏退出时由独立的小 Java 进程替换 JAR。网站或 CDN 被攻破时也无法推送未经签名的 JAR。签名流程见 `tools/mod-sign.mjs`。
   - 第三方 fork / 在 Modrinth、CurseForge 发布时，**应关闭此功能或换成自己的公钥**（这些平台通常不允许模组自行下载并替换自身）。
3. **注册时输入密码**：游戏内 `RegisterScreen`/`ChangePasswordScreen` 会把密码经 HTTPS 发给网站。请确认你在官方站运行的版本，并使用不与其他服务重复的密码。

## 支持版本

仅最新稳定版获得安全修复。
