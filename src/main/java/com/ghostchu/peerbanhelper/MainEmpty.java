package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.util.InfoHashUtil;

public final class MainEmpty {
    public static void main(String[] args) {
        // 什么都不做直接退出
        var id = InfoHashUtil.getHashedIdentifier("0be30e855da0076e8fa2a7f486c96d6ab2cf0e54");
        System.out.println(id);
    }
}
