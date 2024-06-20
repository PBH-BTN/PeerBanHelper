package com.ghostchu.peerbanhelper.alert;

/**
 * 警告信息
 *
 * @param id          相同内容和事件应当使用相同的 ID，避免重复推送
 * @param title       警告标题
 * @param description 警告内容
 * @param level       警告等级
 * @param timestamp   时间戳
 */
public record Alert(String id, String title, String description, AlertLevel level, long timestamp) {
}
