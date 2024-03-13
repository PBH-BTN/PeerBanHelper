package com.ghostchu.peerbanhelper.text;

public class Lang {
    public static String ERR_BUILD_NO_INFO_FILE = "错误：构建信息文件不存在";
    public static String ERR_CANNOT_LOAD_BUILD_INFO = "错误：无法加载构建信息文件";
    public static String MOTD = "PeerBanHelper v{} - by Ghost_chu";
    public static String LOADING_CONFIG = "正在加载配置文件……";
    public static String CONFIG_PEERBANHELPER = "已初始化目录结构，请配置相关文件后，重新启动 PeerBanHelper";
    public static String ERR_SETUP_CONFIGURATION = "错误：无法初始化配置文件结构";
    public static String DISCOVER_NEW_CLIENT = " + {} -> {} ({})";
    public static String ERR_INITIALIZE_BAN_PROVIDER_ENDPOINT_FAILURE = "错误：无法初始化 API 提供端点，Transmission 模块的封禁功能将不起作用";
    public static String WAIT_FOR_MODULES_STARTUP = "请等待功能模块初始化……";
    public static String MODULE_REGISTER = "[注册] {}";
    public static String ERR_CLIENT_LOGIN_FAILURE_SKIP = "登录到 {} ({}) 失败，跳过……";
    public static String ERR_UNEXPECTED_API_ERROR = "在处理 {} ({}) 的 API 操作时出现了一个非预期的错误";
    public static String PEER_UNBAN_WAVE = "[解封] 解除了 {} 个过期的对等体封禁";
    public static String ERR_UPDATE_BAN_LIST = "在更新 {} ({}) 的封禁列表时出现了一个非预期的错误";
    public static String BAN_PEER = "[封禁] {}, PeerId={}, ClientName={}, Progress={}, Uploaded={}, Downloaded={}, Reason={}";
    public static String CHECK_COMPLETED = "[完成] 已检查 {} 的 {} 个活跃 Torrent 和 {} 个对等体";
    public static String ERR_INVALID_RULE_SYNTAX = "规则 {} 的表达式无效，请检查是否存在拼写错误";
    public static String MODULE_CNB_MATCH_CLIENT_NAME = "匹配 ClientName (UserAgent): %s";
    public static String MODULE_IBL_MATCH_IP = "匹配 IP 规则: %s";
    public static String MODULE_PID_MATCH_PEER_ID = "匹配 PeerId 规则: %s";
    public static String MODULE_PCB_EXCESSIVE_DOWNLOAD = "客户端下载过量：种子大小：%d，上传给此对等体的总量：%d，最大允许的过量下载总量：%d";
    public static String MODULE_PCB_PEER_MORE_THAN_LOCAL_SKIP = "客户端进度：%.2f%%，实际进度：%.2f%%，客户端的进度多于本地进度，跳过检测";
    public static String MODULE_PCB_PEER_BAN_INCORRECT_PROGRESS = "客户端进度：%.2f%%，实际进度：%.2f%%，差值：%.2f%%";
    public static String MODULE_PCB_PEER_BAN_REWIND = "客户端进度：%.2f%%，实际进度：%.2f%%，上次记录进度：%.2f%%，本次进度：%.2f%%，差值：%.2f%%";
    public static String MODULE_AP_PEER_BAN_PING = "Peer 发送了 ICMP 响应包";
    public static String MODULE_AP_INVALID_RULE = "规则 {} 无效，请检查语法和拼写错误";
    public static String MODULE_AP_BAN_PEER_CODE = "Peer 的 HTTP(S) 响应返回了预期状态码：%s";
    public static String MODULE_AP_PEER_CODE = "Peer 的 HTTP(S) 响应返回了状态码：%s";
    public static String MODULE_AP_INCORRECT_TCP_TEST_PORT = "TCP 探测规则 %s 的端口号无效: %s";
    public static String MODULE_AP_BAN_PEER_TCP_TEST = "TCP 测试通过: %s";
    public static String MODULE_AP_TCP_TEST_PORT_FAIL = "TCP 探测目标失败: %s";
    public static String MODULE_AP_EXECUTE_EXCEPTION = "烘焙缓存时出错，请将下面的错误日志发送给开发者以协助修复此错误";
    public static String DOWNLOADER_QB_LOGIN_FAILED = "登录到 {} 失败：{} - {}: \n{}";
    public static String DOWNLOADER_QB_FAILED_REQUEST_TORRENT_LIST = "请求 Torrents 列表失败 - %d - %s";
    public static String DOWNLOADER_QB_FAILED_REQUEST_PEERS_LIST_IN_TORRENT = "请求 Torrent 的 Peers 列表失败 - %d - %s";
    public static String DOWNLOADER_QB_API_PREFERENCES_ERR = "qBittorrent 的首选项 API 返回了非 200 预期响应 - %d - %s";
    public static String DOWNLOADER_QB_FAILED_SAVE_BANLIST = "无法保存 {} ({}) 的 Banlist！{} - {}\n{}";
    public static String DOWNLOADER_TR_MOTD_WARNING = "[受限] 由于 Transmission 的 RPC-API 限制，PeerId 黑名单功能和 ProgressCheatBlocker 功能的过量下载模块不可用";
    public static String DOWNLOADER_TR_DISCONNECT_PEERS = "[重置] 正在断开 Transmission 上的 {} 个种子连接的对等体，以便应用 IP 屏蔽列表的更改";
    public static String DOWNLOADER_TR_INCORRECT_BANLIST_API_RESP = "设置 Transmission 的 BanList 地址时，返回非成功响应：{}。";
    public static String DOWNLOADER_TR_INCORRECT_SET_BANLIST_API_RESP = """
            无法应用 IP 黑名单到 Transmission，PBH 没有生效！
            请求 Transmission 更新 BanList 时，返回非成功响应。
            您是否正确映射了 PeerBanHelper 的外部交互端口，以便 Transmission 从 PBH 拉取 IP 黑名单？
            检查 Transmission 的 设置 -> 隐私 -> 屏蔽列表 中自动填写的 URL 是否正确，如果不正确，请在 PeerBanHelper 的 config.yml 中正确配置 server 部分的配置文件，确保 Transmission 能够正确连接到 IP 黑名单提供端点
            """;
    public static String ERR_CONFIG_DIRECTORY_INCORRECT = "初始化失败：config 不是一个目录。如果您正在使用 Docker，请确保其正确挂载。";


}
