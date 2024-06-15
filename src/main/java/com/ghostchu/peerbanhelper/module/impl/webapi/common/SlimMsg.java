package com.ghostchu.peerbanhelper.module.impl.webapi.common;

/**
 * 简化消息返回体
 *
 * @param success 是否成功
 * @param message 消息
 */
public record SlimMsg(boolean success, String message) {
}
