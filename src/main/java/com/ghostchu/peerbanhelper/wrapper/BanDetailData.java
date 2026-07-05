package com.ghostchu.peerbanhelper.wrapper;

import com.ghostchu.peerbanhelper.text.TranslationComponent;

/**
 * 封禁扩展详细信息
 * 主要是用于避免将所有数据加载到内存中
 */
public record BanDetailData(String context, TranslationComponent rule, TranslationComponent description,
                            StructuredData<String,Object> structuredData) {
}
