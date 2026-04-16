
package com.ghostchu.peerbanhelper.databasent.service;

import com.ghostchu.peerbanhelper.util.helpstatus.CanDirty;
import org.apache.ibatis.executor.BatchResult;

import java.util.List;

public interface CommonCanDirtyService<T extends CanDirty> extends CommonService<T> {
    boolean saveOrUpdateIfDirty(T t);
    List<BatchResult> saveOrUpdateIfDirty(List<T> t);
}
