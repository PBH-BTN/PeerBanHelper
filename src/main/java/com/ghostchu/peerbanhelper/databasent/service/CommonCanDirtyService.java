
package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.util.helpstatus.CanDirty;

public interface CommonCanDirtyService<T extends CanDirty> extends CommonService<T> {
    boolean saveOrUpdateIfDirty(T t);
}
