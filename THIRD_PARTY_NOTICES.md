# 第三方声明 / Third-party notices

| 组件 | 用途 | 许可 |
|---|---|---|
| Fabric Loader / Fabric API / Fabric Loom | 加载与构建 | Apache-2.0 |
| Minecraft（Mojang 官方混淆映射） | 编译期引用，不随源码分发 | [Minecraft EULA](https://www.minecraft.net/eula) / Mojang 映射许可 |
| Gson 2.13.2 | JSON | Apache-2.0 |
| Cloth Config、Mod Menu | 可选，仅编译期引用（`modCompileOnly`） | Unlicense / MIT |
| Xaero's Minimap / World Map、TownyMap、Gnetum | 可选运行期联动，仅通过反射调用，不含其代码 | 各自许可（不随本仓库分发） |
| 内置字体 `ui.ttf`（家族名 *Paletale Serif*） | 界面字体 | SIL OFL 1.1，源自 Adobe *Source Han Serif*（Reserved Font Name "Source"，已改名）。完整许可见 `src/client/resources/assets/paletale/font/OFL-NotoSansSC.txt` |

`src/test/java/xaero/**` 为本项目自行编写的**测试桩**（仅声明被调用的构造器/枚举），不含 Xaero 原始代码。

“Minecraft”是 Mojang Synergies AB 的商标。本项目与 Mojang、Microsoft 无关。
