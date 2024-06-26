package com.ghostchu.peerbanhelper.alert;

import org.jetbrains.annotations.NotNull;

/**
 * 警告信息
 *
 * @param id          相同内容和事件应当使用相同的 ID，避免重复推送
 * @param title       警告标题
 * @param description 警告内容
 * @param level       警告等级
 * @param timestamp   时间戳
 */
public record Alert(@NotNull String id,
                    @NotNull String title,
                    @NotNull String description,
                    @NotNull AlertLevel level,
                    long timestamp) {
}
