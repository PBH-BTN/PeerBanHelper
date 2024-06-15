package com.ghostchu.peerbanhelper.module.impl.webapi.common;

/**
 * 标准消息返回体
 *
 * @param success 是否成功
 * @param message 消息
 * @param data    数据
 */
public record StdMsg(boolean success, String message, Object data) {
}
