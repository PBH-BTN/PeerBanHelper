# PBH Plus 许可证

负责人 parity，应用启动和路由装配 root。Java 参考为 `pbhplus/LicenseParser`、`LicenseManager`、`LocalKeyManager`、`BasicLicenseBackend`、`RevolvableLicenseBackend`、两个撤销验证器、`RSAUtils`、`PBHPlusController` 及 `JavalinWebContainer` 的 `Role.PBH_PLUS` 检查。

## 已实现

- `pbh-plus::LicenseService::load` 接受 `pbh-plus-key` 中的许可证文本。只接收通过现有官方公钥或指定本机硬件对应旧公钥验证的 RSA-1024 PKCS#1 Type 1 数据。限制长度，逐块验证填充与分隔，再解析完整 JSON、`verifyMagic` 和版本。许可列表使用原文本去掉外层空白后的 SHA-256 标识去重。公开状态和诊断不含原许可证文本。
- V1 保留缺省 `afdian` 类型、`basic` 和 `paid`、开始时间下界。V2 只采用验证后数据中的功能列表。已存在的本机钥匙仅允许同一硬件的 V2 `local` 许可证，且仅允许 `basic` 和约 15 天窗口。所有许可证都经过验证后才参与权限判断。
- 状态为 `VALID`、`NOT_STARTED`、`EXPIRED` 或 `REVOKED`。生效、过期边界均含端点，撤销优先。状态对象保留 `enabledFeatures`、`licenses` 及各条目的 `licenseId`、`version`、`status`、`data`。功能检查随时间变动，使用当前有效的许可证集合。
- 按 Java 同一字段映射核对内置撤销 CSV 的五个 SHA-256 列。已有本机 RSA 签名的远端撤销缓存按 Java 文件名算法读取。有效缓存已证明撤销的记录继续禁用，等后续经过验证的上游状态确认再解除，避免离线后恢复已知撤销权限。
- `PlusRuntime` 在 `serve` 和扫描 HTTP 中提供状态、导入和删除。导入只写入验证通过的有效或尚未生效许可证，删除按许可证 ID。演示模式禁止写入。并发写入串行读取当前 YAML，保留其余配置值，原子替换后发布新许可证快照。保存 YAML 时重排注释。应用对已接入的 Plus 图表路由应用 `basic` 门控，返回原契约 402。
- Windows 通过 SMBIOS Type 1 UUID 匹配本机公钥文件，解析 2.6 及以上版本的字节序。Linux 读取固件 sysfs 或 `dmidecode`，macOS 用 IOKit 的 `ioreg`，FreeBSD 首选 `kenv`，再读 `kern.hostuuid`。拒绝全零、全 1 和不能验证为 UUID 的共享占位标识。取不到设备 UUID 且现有配置含安装 ID 时，可验证与该安装 ID 对应的 Java 兜底钥匙。MAC 兜底和其他平台的 OSHI 方法尚未复现。[Windows SMBIOS API](https://learn.microsoft.com/en-us/windows/win32/api/sysinfoapi/nf-sysinfoapi-getsystemfirmwaretable) · [WMI UUID 来源](https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/win32-computersystemproduct)
- Rust 提供已认证的 `claimRenewFreeLicenseCaptcha` 与 `renewFreeLicense`。沿用 32 随机字节、SHA-1 五位工作量证明和一小时过期。错误证明可重试，正确证明只能提交一次。超大 nonce 拒绝，缓存达到 4096 项时淘汰最早挑战。Java 的挑战在成功后仍可重复使用，因此同一挑战的重放响应有差异。
- 试用在共享配置锁内重读许可证并检查 `basic`。仅没有有效 `basic` 时才签发 15 天本机 `basic`，验签后与配置一同发布。沿用旧 PKCS#8 私钥与 X.509 公钥文件，设备没有钥匙时按需生成。私钥存在时可补建缺失的同源公钥，缺失私钥或公私钥不匹配时保留现有文件并拒绝签发。复用发布版 `FREE_LICENSE_SOURCE`、`FREE_LICENSE_DESCRIPTION` 和持久许可证状态。
- `pbh-plus::remote` 对接 Java 的 `download` 和 `checkRevoke` 请求。签名解析失败时用输入识别码查找，返回密钥仍须本地验签及确认时间窗口和撤销状态。撤销检查按 Java 字段逐项发送 SHA-256，省略缺失字段和电邮。连接使用固定 HTTPS 服务、15 秒时限、有限响应体及禁止重定向，日志与错误仅保留状态码和错误类别。
- 启动后异步检查并按约 24 小时刷新，在有效 Java 签名缓存内跳过请求。上游失败、响应不完整及缓存写入失败均保留上一次已验证状态。已确认的撤销继续生效，直到可信的更新明确解除，内置撤销优先。每个线上结果绑定到对应许可证 ID，旧结果不能覆盖更新证据，本机试用不参与查询。设备密钥可用时，按 Java 缓存格式签名并原子替换，尚未生成密钥的本机可在首次保存撤销缓存时生成同源密钥。

## 待完成

- 旧版 Java MAC 兜底及非 Windows 平台的整机识别尚待同机验证。在线服务仅使用回环夹具测试，实际许可服务、生产许可证和代理配置未验。

## 验证

`cargo test -p pbh-plus --offline` 通过 7 项，`cargo clippy -p pbh-plus --all-targets --locked -- -D warnings` 通过。覆盖真实官方公钥的载入和拒绝不受信任的夹具、V1/V2 功能时间窗口、损坏 RSA 填充、设备绑定、篡改撤销缓存、内置 CSV 字段映射和 SMBIOS 字节序。测试签发器仅用于隔离夹具。

`cargo test -p peerbanhelper plus_api::tests --locked` 通过 2 项，覆盖无效密钥的配置连续性、HTTP 402 以及同目录原子替换。用临时数据目录和回环拒绝代理启动 Java 参考版，经原有认证与 PoW 挑战生成同机免费试用。Rust 直接读取 Java 的配置及设备公钥，整个许可证状态响应与 Java 相同。随后通过 Rust HTTP 删除、重新导入同一许可证并重启，验证失效、生效及持久状态。未使用官方付费许可证或连接生产撤销服务。

新增原生试用后，库测试 9 项和严格 Clippy 已通过，新增验证包括挑战过期及单次使用、准确有效期、过期再签发、旧钥匙复用、设备不匹配及密钥文件异常。应用定向测试 3 项通过，覆盖未登录请求 403、错误证明、两个有效证明并发仅一次提交，以及锁内保留无关配置。完整应用构建通过，Windows 回环 HTTP 使用两条独立挑战并发提交，Rust 仅签发一份，拒绝旧证明重放及已激活时的新试用。Java 参考版和重启后的 Rust 对原生签发的许可证返回完全一致的状态，Java 未替换原生生成的本机钥匙文件。整个过程使用临时数据和回环拒绝代理。

在线协议通过 11 项库测试、严格 Clippy 和 4 项应用定向测试。回环夹具覆盖请求字段哈希、查询编码、HTTP 204 和 404、禁止跳转和错误脱敏。验证 Java 兼容签名缓存读写、明文缓存拒绝、磁盘不可用时保留内存状态、503 或不完整响应后保留撤销，以及后续成功结果恢复权限。应用测试验证下载后仍需本地验签，验证失败不改配置，已签发的本机试用不触发撤销请求。未连接真实的许可服务。
