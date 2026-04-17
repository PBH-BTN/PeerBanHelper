
package com.ghostchu.peerbanhelper.databasent.service;

import com.ghostchu.peerbanhelper.util.helpstatus.CanDirty;
import org.apache.ibatis.executor.BatchResult;

import java.util.List;

public interface CommonCanDirtyService<T extends CanDirty> extends CommonService<T> {
    /**
     * 仅保存和更新脏对象，并在创建新记录时回填主键
     * @param t element
     * @return id-filled element
     */
    boolean saveOrUpdateIfDirtyWithIdRefill(T t);

    /**
     * 批量保存和更新脏对象，此操作不会在创建新记录时回填主键
     * @param t elements
     * @return batch results
     */
    List<BatchResult> saveOrUpdateIfDirty(List<T> t);
}
