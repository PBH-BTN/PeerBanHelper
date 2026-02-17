package com.ghostchu.peerbanhelper.platform.impl.win32.workingset.jna;

import lombok.Getter;

/**
 * 工作集管理相关异常
 */
@Getter
public class WorkingSetException extends Exception {

    /**
     * -- GETTER --
     *  获取Windows错误代码
     *
     * @return Windows错误代码，如果没有则返回0
     */
    private final int errorCode;
    
    /**
     * 构造函数
     * 
     * @param message 异常消息
     */
    public WorkingSetException(String message) {
        super(message);
        this.errorCode = 0;
    }
    
    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param cause 原因异常
     */
    public WorkingSetException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
    }
    
    /**
     * 构造函数
     * 
     * @param message 异常消息
     * @param errorCode Windows错误代码
     */
    public WorkingSetException(String message, int errorCode) {
        super(message + " (错误代码: " + errorCode + ")");
        this.errorCode = errorCode;
    }

}
