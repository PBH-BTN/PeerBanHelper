package com.ghostchu.peerbanhelper.btn.protocol.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.protocol.message.clientside.ClientSideHandshakeData;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.MqttVersion;
import org.dromara.mica.mqtt.core.client.IMqttClientConnectListener;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.tio.core.ChannelContext;

import java.util.concurrent.Executors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class BtnMqttClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private MqttClient mqtt;
    private String ip;
    private int port;
    private String username;
    private String password;
    private String clientId;
    private String topicPrefix;

    // /v2/btn/<clientId>/setup - 初始化、握手、上下线 // QOS = 2
    // /v2/btn/<clientId>/messaging - 消息通知推送 // QOS = 2
    // /v2/btn/<clientId>/streamBanHistory - 封禁记录提交串流 // QOS = 0
    // /v2/btn/<clientId>/streamConnectionHistory - 访问记录提交串流 // QOS = 0
    // /v2/btn/<clientId>/streamSwarmTracking - Peer 跟踪串流 // QOS = 0

    // /v2/monitor/torrent/<torrentIdentifier>/swarm - 监听指定种子识别符下的 Peers 实时活动 // QOS = 0
    // /v2/monitor/torrent/<torrentIdentifier>/banActivity - 监听指定种子识别符下的封禁活动 // QOS = 0
    // /v2/monitor/torrent/<torrentIdentifier>/connectionActivity - 监听指定种子识别符下的访问活动 // QOS = 0

    // /v2/monitor/peer/<peerIp>/swarm - 监听指定 IP 下的所有种子识别符上的 Peers 实时活动 // QOS = 0
    // /v2/monitor/peer/<peerIp>/banActivity - 监听指定 IP 下的所有种子识别符上的被封禁活动 // QOS = 0
    // /v2/monitor/peer/<peerIp>/connectionActivity - 监听指定 IP 下的所有种子识别符上的访问活动 // QOS = 0

    public void setup(String ip, int port, String username, String password, String clientId) {
        this.ip = ip;
        this.port = port;
        this.username = username;
        this.password = password;
        this.clientId = clientId;
        this.topicPrefix = "/btn/" + clientId;
    }

    public void start() {
        this.mqtt = MqttClient.create()
                .ip(ip)                // mqtt 服务端 ip 地址
                .port(port)                     // 默认：1883
                .username(username)              // 账号
                .password(password)             // 密码
                .version(MqttVersion.MQTT_5)    // 默认：3_1_1
                .clientId(clientId)             // 非常重要务必手动设置，一般设备 sn 号，默认：MICA-MQTT- 前缀和 36进制的纳秒数
                .readBufferSize(8092)            // 消息一起解析的长度，默认：为 8092 （mqtt 消息最大长度）
                .maxBytesInMessage(10 * 1024 * 1024)   // 最大包体长度,如果包体过大需要设置此参数，默认为： 10M (10*1024*1024)
                .keepAliveSecs(120)             // 默认：60s
                .timeout(10)                    // 超时时间，t-io 配置，可为 null，为 null 时，t-io 默认为 5
                .reconnect(true)                // 是否重连，默认：true
                .retryCount(Integer.MAX_VALUE)
                .reInterval(30 * 1000)               // 重连重试时间，reconnect 为 true 时有效，t-io 默认为：5000
                .mqttExecutor(Executors.newVirtualThreadPerTaskExecutor())
                .willMessage(builder -> {
                    builder.topic(topicPrefix + "/setup").messageText("down");    // 遗嘱消息
                })
                .connectListener(new IMqttClientConnectListener() {
                    @Override
                    public void onConnected(ChannelContext context, boolean isReconnect) {
                        handleConnected(mqtt, context, isReconnect);
                        if (isReconnect) {
                            log.info(tlUI(Lang.BTN_MQTT_RECONNECTED));
                        } else {
                            log.info(tlUI(Lang.BTN_MQTT_CONNECTED));
                        }
                    }

                    @Override
                    public void onDisconnect(ChannelContext channelContext, Throwable throwable, String remark, boolean isRemove) {
                        handleDisconnected(mqtt, channelContext, throwable, remark, isRemove);
                        log.info(tlUI(Lang.BTN_MQTT_DISCONNECTED, remark), throwable);
                    }
                })
                .connect();                 // 同步连接，也可以使用 connect()，可以避免 broker 没启动照成启动卡住。
    }

    private void handleDisconnected(MqttClient mqtt, ChannelContext channelContext, Throwable throwable, String remark, boolean isRemove) {

    }

    @SneakyThrows
    private void handleConnected(MqttClient/**/ mqtt, ChannelContext context, boolean isReconnect) {
        mqtt.unSubscribe(topicPrefix + "/#",
                        "/btn/universal/#")
                .subscribe(topicPrefix + "/#", MqttQoS.QOS1, (ctx, topic, message, payload) -> {

                })
                .subscribe("/btn/universal/#", MqttQoS.QOS1, (ctx, topic, message, payload) -> {

                });
        ClientSideHandshakeData clientSideHandshakeData = new ClientSideHandshakeData(
                "PeerBanHelper",
                Main.getMeta().getVersion() + " (" + Main.getMeta().getAbbrev() + ")",
                Main.getUserAgent(),
                Main.DEF_LOCALE
        );
        mqtt.publish(topicPrefix + "/status", objectMapper.writeValueAsBytes(clientSideHandshakeData), MqttQoS.QOS2);
    }

}
