package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.databasent.mapper.java.PeerConnectionMetricsMapper;
import com.ghostchu.peerbanhelper.databasent.service.PeerConnectionMetricsService;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsEntity;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PeerConnectionMetricsDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Service
@Slf4j
public class PeerConnectionMetricsServiceImpl extends AbstractCommonService<PeerConnectionMetricsMapper, PeerConnectionMetricsEntity> implements PeerConnectionMetricsService {
	@Autowired
	private TransactionTemplate transactionTemplate;


	@Override
    public long getGlobalTotalConnectionsCount(@NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) {
		long total = 0;
		try {
            List<PeerConnectionMetricsEntity> entities = baseMapper.selectList(new LambdaQueryWrapper<PeerConnectionMetricsEntity>().between(PeerConnectionMetricsEntity::getTimeframeAt, startAt, endAt));
			for (PeerConnectionMetricsEntity entity : entities) {
				total += entity.getTotalConnections();
			}
		} catch (Exception e) {
			log.error("Failed to query global total connections count between {} and {}", startAt, endAt, e);
			Sentry.captureException(e);
		}
		return total;
	}

	@Override
    public List<PeerConnectionMetricsDTO> getMetricsSince(@NotNull OffsetDateTime sinceAt, @NotNull OffsetDateTime untilAt, @Nullable String downloader) {
		List<PeerConnectionMetricsDTO> result;

        LambdaQueryWrapper<PeerConnectionMetricsEntity> wrapper = new LambdaQueryWrapper<PeerConnectionMetricsEntity>()
                .between(PeerConnectionMetricsEntity::getTimeframeAt, sinceAt, untilAt);
		if (downloader != null && !downloader.isBlank()) {
            wrapper.eq(PeerConnectionMetricsEntity::getDownloader, downloader);
		}
		var entities = baseMapper.selectList(wrapper);

		// 使用Map来合并相同timeframeAt的记录
		Map<Timestamp, PeerConnectionMetricsDTO> mergedMap = new HashMap<>();

		for (PeerConnectionMetricsEntity entity : entities) {
			Timestamp timeframe = Timestamp.from(entity.getTimeframeAt().toInstant());
			PeerConnectionMetricsDTO dto = PeerConnectionMetricsDTO.from(entity);

			if (mergedMap.containsKey(timeframe)) {
				// 合并相同时间段的记录
				PeerConnectionMetricsDTO existing = mergedMap.get(timeframe);
				mergeMetricsDTO(existing, dto);
			} else {
				mergedMap.put(timeframe, dto);
			}
		}
		// 将合并后的结果按时间排序
		result = new ArrayList<>(mergedMap.values());
		result.sort((a, b) -> Long.compare(b.getKey(), a.getKey()));


		return result;
	}

	@Override
	public void saveAggregating(@NotNull List<PeerConnectionMetricsEntity> buffer, boolean overwrite) {
		for (PeerConnectionMetricsEntity peerConnectionMetricsEntity : buffer) {
            PeerConnectionMetricsEntity entityInDb = baseMapper.selectOne(new LambdaQueryWrapper<PeerConnectionMetricsEntity>().eq(PeerConnectionMetricsEntity::getTimeframeAt, peerConnectionMetricsEntity.getTimeframeAt()).eq(PeerConnectionMetricsEntity::getDownloader, peerConnectionMetricsEntity.getDownloader()));
			if (entityInDb != null) {
				if (overwrite) {
					peerConnectionMetricsEntity.setId(entityInDb.getId());
				} else {
					entityInDb.merge(peerConnectionMetricsEntity);
				}
			} else {
				entityInDb = peerConnectionMetricsEntity;
			}
			baseMapper.insertOrUpdate(entityInDb);
		}
	}

	@Override
	public List<PeerConnectionMetricsEntity> aggregating(@NotNull List<PeerConnectionMetricsTrackEntity> fullPeerSessions) {

		List<PeerConnectionMetricsEntity> buffer = new ArrayList<>();
		for (PeerConnectionMetricsTrackEntity peerSessionEntity : fullPeerSessions) {
            var entity = findOrCreateBuffer(buffer, peerSessionEntity.getTimeframeAt(), peerSessionEntity.getDownloader());
			entity.setTotalConnections(entity.getTotalConnections() + 1);
			var flags = peerSessionEntity.getLastFlags();
			if (flags != null) {
				PeerFlag f = new PeerFlag(flags);
				if (!f.isLocalConnection())
					entity.setIncomingConnections(entity.getIncomingConnections() + 1);
				if (f.isInteresting() && f.isRemoteChoked())
					entity.setRemoteRefuseTransferToClient(entity.getRemoteRefuseTransferToClient() + 1);
				if (f.isInteresting() && !f.isRemoteChoked())
					entity.setRemoteAcceptTransferToClient(entity.getRemoteAcceptTransferToClient() + 1);
				if (f.isRemoteInterested() && f.isChoked())
					entity.setLocalRefuseTransferToPeer(entity.getLocalRefuseTransferToPeer() + 1);
				if (f.isRemoteInterested() && !f.isChoked())
					entity.setLocalAcceptTransferToPeer(entity.getLocalAcceptTransferToPeer() + 1);
				if (!f.isRemoteChoked() && !f.isInteresting())
					entity.setLocalNotInterested(entity.getLocalNotInterested() + 1);
				if (!f.isChoked() && !f.isRemoteInterested())
					entity.setQuestionStatus(entity.getQuestionStatus() + 1);
				if (f.isOptimisticUnchoke())
					entity.setOptimisticUnchoke(entity.getOptimisticUnchoke() + 1);
				if (f.isFromDHT())
					entity.setFromDHT(entity.getFromDHT() + 1);
				else if (f.isFromPEX())
					entity.setFromPEX(entity.getFromPEX() + 1);
				else if (f.isFromLSD())
					entity.setFromLSD(entity.getFromLSD() + 1);
				else
					entity.setFromTrackerOrOther(entity.getFromTrackerOrOther() + 1);
				if (f.isRc4Encrypted())
					entity.setRc4Encrypted(entity.getRc4Encrypted() + 1);
				if (f.isPlainTextEncrypted())
					entity.setPlainTextEncrypted(entity.getPlainTextEncrypted() + 1);
				if (f.isUtpSocket())
					entity.setUtpSocket(entity.getUtpSocket() + 1);
				else
					entity.setTcpSocket(entity.getTcpSocket() + 1);
			}
		}
		return buffer;
	}

	@Override
	public void removeOutdatedData(OffsetDateTime beforeAt) {
		log.info(tlUI(Lang.CONNECTION_METRICS_SERVICE_CLEANING_UP));
        long deleted = 0;
        while (true) {
            // 每次循环在独立事务中执行，完成后释放连接
            Integer changes = transactionTemplate.execute(status ->
                baseMapper.delete(new LambdaQueryWrapper<PeerConnectionMetricsEntity>()
                    .le(PeerConnectionMetricsEntity::getTimeframeAt, beforeAt)
                    .last("LIMIT 300"))
            );
            if (changes == null || changes <= 0) {
                break;
            }
            deleted += changes;
        }
		log.info(tlUI(Lang.CONNECTION_METRICS_SERVICE_CLEANED_UP, deleted));
	}


	private void mergeMetricsDTO(PeerConnectionMetricsDTO target, PeerConnectionMetricsDTO source) {
		target.setTotalConnections(target.getTotalConnections() + source.getTotalConnections());
		target.setIncomingConnections(target.getIncomingConnections() + source.getIncomingConnections());
		target.setRemoteRefuseTransferToClient(target.getRemoteRefuseTransferToClient() + source.getRemoteRefuseTransferToClient());
		target.setRemoteAcceptTransferToClient(target.getRemoteAcceptTransferToClient() + source.getRemoteAcceptTransferToClient());
		target.setLocalRefuseTransferToPeer(target.getLocalRefuseTransferToPeer() + source.getLocalRefuseTransferToPeer());
		target.setLocalAcceptTransferToPeer(target.getLocalAcceptTransferToPeer() + source.getLocalAcceptTransferToPeer());
		target.setLocalNotInterested(target.getLocalNotInterested() + source.getLocalNotInterested());
		target.setQuestionStatus(target.getQuestionStatus() + source.getQuestionStatus());
		target.setOptimisticUnchoke(target.getOptimisticUnchoke() + source.getOptimisticUnchoke());
		target.setFromDHT(target.getFromDHT() + source.getFromDHT());
		target.setFromPEX(target.getFromPEX() + source.getFromPEX());
		target.setFromLSD(target.getFromLSD() + source.getFromLSD());
		target.setFromTrackerOrOther(target.getFromTrackerOrOther() + source.getFromTrackerOrOther());
		target.setRc4Encrypted(target.getRc4Encrypted() + source.getRc4Encrypted());
		target.setPlainTextEncrypted(target.getPlainTextEncrypted() + source.getPlainTextEncrypted());
		target.setUtpSocket(target.getUtpSocket() + source.getUtpSocket());
		target.setTcpSocket(target.getTcpSocket() + source.getTcpSocket());
	}

	@NotNull
    private PeerConnectionMetricsEntity findOrCreateBuffer(List<PeerConnectionMetricsEntity> buffer, OffsetDateTime timestamp, String downloader) {
		for (PeerConnectionMetricsEntity peerConnectionMetricsEntity : buffer) {
			if (peerConnectionMetricsEntity.getTimeframeAt().equals(timestamp) && peerConnectionMetricsEntity.getDownloader().equals(downloader)) {
				return peerConnectionMetricsEntity;
			}
		}
		PeerConnectionMetricsEntity entity = new PeerConnectionMetricsEntity();
		entity.setTimeframeAt(OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault()));
		entity.setDownloader(downloader);
		buffer.add(entity);
		return entity;
	}

}
