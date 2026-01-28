package com.ghostchu.peerbanhelper.databasent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PeerBanCount {
	private String peerIp;
	private long count;
}
