package com.ghostchu.peerbanhelper.text

object Lang {
    @JvmField
    var ERR_BUILD_NO_INFO_FILE: String = "错误：构建信息文件不存在"

    @JvmField
    var ERR_CANNOT_LOAD_BUILD_INFO: String = "错误：无法加载构建信息文件"

    @JvmField
    var MOTD: String = "PeerBanHelper v{} - by Ghost_chu"

    @JvmField
    var LOADING_CONFIG: String = "正在加载配置文件……"

    @JvmField
    var CONFIG_PEERBANHELPER: String =
        "已初始化目录结构，相关文件已放置在运行目录的 data 文件夹下，请配置相关文件后，再重新启动 PeerBanHelper"

    @JvmField
    var ERR_SETUP_CONFIGURATION: String = "错误：无法初始化配置文件结构"

    @JvmField
    var DISCOVER_NEW_CLIENT: String = " + {} -> {} ({})"

    @JvmField
    var ERR_INITIALIZE_BAN_PROVIDER_ENDPOINT_FAILURE: String =
        "错误：无法初始化 API 提供端点，Transmission 模块的封禁功能将不起作用"

    @JvmField
    var WAIT_FOR_MODULES_STARTUP: String = "请等待功能模块初始化……"

    @JvmField
    var MODULE_REGISTER: String = "[注册] {}"

    @JvmField
    var MODULE_UNREGISTER: String = "[解注册] {}"

    @JvmField
    var ERR_CLIENT_LOGIN_FAILURE_SKIP: String = "登录到 {} ({}) 失败，跳过……"

    @JvmField
    var ERR_UNEXPECTED_API_ERROR: String = "在处理 {} ({}) 的 API 操作时出现了一个非预期的错误"

    @JvmField
    var PEER_UNBAN_WAVE: String = "[解封] 解除了 {} 个过期的对等体封禁"

    @JvmField
    var ERR_UPDATE_BAN_LIST: String = "在更新 {} ({}) 的封禁列表时出现了一个非预期的错误"

    @JvmField
    var BAN_PEER: String =
        "[封禁] {}, PeerId={}, ClientName={}, Progress={}, Uploaded={}, Downloaded={}, Torrent={}, Reason={}"

    @JvmField
    var CHECK_COMPLETED: String = "[完成] 已检查 {} 的 {} 个活跃 Torrent 和 {} 个对等体"

    @JvmField
    var ERR_INVALID_RULE_SYNTAX: String = "规则 {} 的表达式无效，请检查是否存在拼写错误"

    @JvmField
    var MODULE_CNB_MATCH_CLIENT_NAME: String = "匹配 ClientName (UserAgent): %s"

    @JvmField
    var MODULE_IBL_MATCH_IP: String = "匹配 IP 规则: %s"

    @JvmField
    var MODULE_PID_MATCH_PEER_ID: String = "匹配 PeerId 规则: %s"

    @JvmField
    var MODULE_PCB_EXCESSIVE_DOWNLOAD: String =
        "客户端下载过量：种子大小：%d，上传给此对等体的总量：%d，最大允许的过量下载总量：%d"

    @JvmField
    var MODULE_PCB_PEER_MORE_THAN_LOCAL_SKIP: String =
        "客户端进度：%.2f%%，实际进度：%.2f%%，客户端的进度多于本地进度，跳过检测"

    @JvmField
    var MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS: String = "客户端进度：%.2f%%，实际进度：%.2f%%，差值：%.2f%%"

    @JvmField
    var MODULE_PCB_PEER_BAN_REWIND: String =
        "客户端进度：%.2f%%，实际进度：%.2f%%，上次记录进度：%.2f%%，本次进度：%.2f%%，差值：%.2f%%"

    @JvmField
    var MODULE_PCB_SKIP_UNKNOWN_SIZE_TORRENT: String = "种子大小未知"

    @JvmField
    var MODULE_AP_PEER_BAN_PING: String = "Peer 发送了 ICMP 响应包"

    @JvmField
    var MODULE_AP_INVALID_RULE: String = "规则 {} 无效，请检查语法和拼写错误"

    @JvmField
    var MODULE_AP_BAN_PEER_CODE: String = "Peer 的 HTTP(S) 响应返回了预期状态码：%s"

    @JvmField
    var MODULE_AP_PEER_CODE: String = "Peer 的 HTTP(S) 响应返回了状态码：%s"

    @JvmField
    var MODULE_AP_INCORRECT_TCP_TEST_PORT: String = "TCP 探测规则 %s 的端口号无效: %s"

    @JvmField
    var MODULE_AP_BAN_PEER_TCP_TEST: String = "TCP 测试通过: %s"

    @JvmField
    var MODULE_AP_TCP_TEST_PORT_FAIL: String = "TCP 探测目标失败: %s"

    @JvmField
    var MODULE_AP_EXECUTE_EXCEPTION: String = "烘焙缓存时出错，请将下面的错误日志发送给开发者以协助修复此错误"

    @JvmField
    var MODULE_AP_SSL_CONTEXT_FAILURE: String = "初始化 SSLContext 时出错"

    @JvmField
    var DOWNLOADER_QB_LOGIN_FAILED: String = "登录到 {} 失败：{} - {}: \n{}"

    @JvmField
    var DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST: String = "请求 Torrents 列表失败 - %d - %s"

    @JvmField
    var DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT: String = "请求 Torrent 的 Peers 列表失败 - %d - %s"

    @JvmField
    var DOWNLOADER_QB_API_PREFERENCES_ERR: String = "qBittorrent 的首选项 API 返回了非 200 预期响应 - %d - %s"

    @JvmField
    var DOWNLOADER_QB_FAILED_SAVE_BANLIST: String = "无法保存 {} ({}) 的 Banlist！{} - {}\n{}"

    @JvmField
    var DOWNLOADER_TR_MOTD_WARNING: String =
        "[受限] 由于 Transmission 的 RPC-API 限制，PeerId 黑名单功能和 ProgressCheatBlocker 功能的过量下载模块不可用"

    @JvmField
    var DOWNLOADER_TR_DISCONNECT_PEERS: String =
        "[重置] 正在断开 Transmission 上的 {} 个种子连接的对等体，以便应用 IP 屏蔽列表的更改"

    @JvmField
    var DOWNLOADER_TR_INCORRECT_BANLIST_API_RESP: String = "设置 Transmission 的 BanList 地址时，返回非成功响应：{}。"

    @JvmField
    var DOWNLOADER_TR_INCORRECT_SET_BANLIST_API_RESP: String = """
            无法应用 IP 黑名单到 Transmission，PBH 没有生效！
            请求 Transmission 更新 BanList 时，返回非成功响应。
            您是否正确映射了 PeerBanHelper 的外部交互端口，以便 Transmission 从 PBH 拉取 IP 黑名单？
            检查 Transmission 的 设置 -> 隐私 -> 屏蔽列表 中自动填写的 URL 是否正确，如果不正确，请在 PeerBanHelper 的 config.yml 中正确配置 server 部分的配置文件，确保 Transmission 能够正确连接到 IP 黑名单提供端点
            
            """.trimIndent()

    @JvmField
    var DOWNLOADER_TR_INVALID_RESPONSE: String = "[错误] Transmission 返回无效 JSON 响应: {}"

    @JvmField
    var DOWNLOADER_TR_UPDATED_BLOCKLIST: String = "[响应] Transmission 屏蔽列表已更新成功，现在包含 {} 条规则"

    @JvmField
    var DOWNLOADER_TR_KNOWN_INCOMPATIBILITY: String =
        "[错误] 您正在使用的 Transmission 版本 %s 与 PeerBanHelper 不兼容: %"

    @JvmField
    var DOWNLOADER_TR_INCOMPATIBILITY_BANAPI: String = "当前版本存在封禁 API 的已知问题，请升级至 3.0-20 或更高版本"

    @JvmField
    var ERR_CONFIG_DIRECTORY_INCORRECT: String =
        "初始化失败：config 不是一个目录。如果您正在使用 Docker，请确保其正确挂载。"

    @JvmField
    var WEB_ENDPOINT_REGISTERED: String = "[注册] WebAPI 端点已注册：{}"

    @JvmField
    var PBH_SHUTTING_DOWN: String = "[退出] 正在退出，请等待我们完成剩余的工作……"
    var ARB_ERROR_TO_CONVERTING_IP: String = "IP 地址 %s 既不是 IPV4 地址也不是 IPV6 地址。"

    @JvmField
    var ARB_BANNED: String = "IP 地址 %s 与另一个已封禁的 IP 地址 %s 处于同一封禁区间 %s 内，执行连锁封禁操作。"

    @JvmField
    var DATABASE_SETUP_FAILED: String = "[错误] 数据库初始化失败"

    @JvmField
    var DATABASE_BUFFER_SAVED: String = "[保存] 已保存 {} 条内存缓存的封禁日志到数据库，用时 {}ms"

    @JvmField
    var DATABASE_SAVE_BUFFER_FAILED: String = "[错误] 刷写内存缓存的封禁日志时出现了 SQL 错误，未保存的数据已被丢弃"

    @JvmField
    var WEB_BANLOGS_INTERNAL_ERROR: String = "[错误] 读取封禁日志时遇到非预期错误"
    var PERSIST_DISABLED: String = "[禁用] Persist 持久化数据存储已在此服务器上被禁用"

    @JvmField
    var BOOTSTRAP_FAILED: String = "[错误] PeerBanHelper 启动失败，遇到致命错误，请检查控制台日志"

    @JvmField
    var DATABASE_FAILURE: String =
        "[错误] 无法连接到持久化数据存储数据库，请检查是否同时启动了多个 PBH 示例？（如果 SQLite 数据库损坏，请删除它，PBH 将会重新生成新的数据库文件）"
}
