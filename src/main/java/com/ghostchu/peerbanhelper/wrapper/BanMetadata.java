package com.ghostchu.peerbanhelper.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BanMetadata implements Comparable<BanMetadata> {
    private UUID RANDOM_ID = UUID.randomUUID();
    private long banAt;
    private long unbanAt;
    private String description;

    @Override
    public int compareTo(BanMetadata o) {
        return this.RANDOM_ID.compareTo(o.RANDOM_ID);
    }
}
