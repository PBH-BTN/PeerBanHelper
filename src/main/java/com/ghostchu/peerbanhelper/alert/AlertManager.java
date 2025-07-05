package com.ghostchu.peerbanhelper.alert;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.jetbrains.annotations.NotNull;

public interface AlertManager {

    /**
     * 移除已发布的警报
     * @param identifier 标识符
     */
    void markAlertAsRead(@NotNull String identifier);

    /**
     * 检查指定标识符的警报是否存在且处于未读状态
     * @param identifier 标识符
     */
    boolean identifierAlertExists(@NotNull String identifier);

    /**
     * 检查指定标识符的警报是否存在，无论是否已读
     * @param identifier 标识符
     */
    boolean identifierAlertExistsIncludeRead(@NotNull String identifier);

    /**
     * 发布警报
     *
     * @param push       是否使用推送渠道进行推送？否则只有 WebUI 能看到
     * @param level      事件等级
     * @param identifier 事件标识符，同一个表示符在同一时间只能有一个未读的存在，如果已经有一个未读的相同标识符的警报存在，新的警报将被忽略; null 则随机生成一个
     * @param title      警报标题
     * @param content    警报内容
     */
    void publishAlert(boolean push, @NotNull AlertLevel level, @NotNull String identifier, @NotNull TranslationComponent title, @NotNull TranslationComponent content);

}