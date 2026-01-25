package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ghostchu.peerbanhelper.databasent.dto.PeerBanCount;
import com.ghostchu.peerbanhelper.databasent.dto.TorrentCount;
import com.ghostchu.peerbanhelper.databasent.dto.UniversalFieldNumResult;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HistoryMapper extends BaseMapper<HistoryEntity> {
	IPage<PeerBanCount> getBannedIpsWithFilter(IPage<PeerBanCount> page, @Param("filter") String filter);

	IPage<PeerBanCount> getBannedIpsWithoutFilter(IPage<PeerBanCount> page);

	long countDistinctIpWithFilter(@Param("filter") String filter);

	long countDistinctIp();

	List<UniversalFieldNumResult> countField(
			@Param("field") String field,
			@Param("percentFilter") double percentFilter,
			@Param("downloader") String downloader,
			@Param("substringLength") Integer substringLength);

	List<UniversalFieldNumResult> sumField(
			@Param("field") String field,
			@Param("percentFilter") double percentFilter,
			@Param("downloader") String downloader,
			@Param("substringLength") Integer substringLength);

    List<TorrentCount> countByTorrentIds(@Param("torrentIds") List<Long> torrentIds);
}
