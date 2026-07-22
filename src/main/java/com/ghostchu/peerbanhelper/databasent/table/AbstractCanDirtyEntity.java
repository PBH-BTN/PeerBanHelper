package com.ghostchu.peerbanhelper.databasent.table;

import com.ghostchu.peerbanhelper.util.helpstatus.CanDirty;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class AbstractCanDirtyEntity implements CanDirty {
    private transient boolean dirty = false;

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }
}
