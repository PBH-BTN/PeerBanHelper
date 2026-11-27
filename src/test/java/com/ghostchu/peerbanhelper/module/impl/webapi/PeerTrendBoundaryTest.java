package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.driver.sqlite.OffsetDateTimeTypeHandlerForSQLite;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.SimpleOffsetDateTimeIntKVDTO;
import lombok.Data;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeerTrendBoundaryTest {
    @Data
    @TableName("events")
    public static class Event {
        private long id;
        private OffsetDateTime seen;
        private String downloader;
    }

    interface EventMapper extends BaseMapper<Event> {}

    @Test
    void indexedAndStreamedCountsAgreeAcrossGapsFiltersAndInclusiveEndpoints() throws Exception {
        var dataSource = new UnpooledDataSource("org.sqlite.JDBC", "jdbc:sqlite::memory:", null, null);
        var configuration = new MybatisConfiguration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.getTypeHandlerRegistry().register(OffsetDateTime.class, new OffsetDateTimeTypeHandlerForSQLite());
        configuration.addMapper(EventMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        var first = LocalDate.of(2024, 2, 20).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        var end = first.plusDays(4);
        try (var session = factory.openSession()) {
            var connection = session.getConnection();
            try (var create = connection.createStatement()) {
                create.execute("CREATE TABLE events(id INTEGER PRIMARY KEY, seen INTEGER, downloader TEXT)");
                create.execute("CREATE INDEX seen_at ON events(seen)");
            }
            try (var insert = connection.prepareStatement("INSERT INTO events(seen, downloader) VALUES (?, ?)")) {
                for (var time : List.of(first.minusSeconds(1), first, first.plusHours(1), end, end.plusSeconds(1))) {
                    for (var client : List.of("a", "b")) {
                        insert.setLong(1, time.toInstant().toEpochMilli());
                        insert.setString(2, client);
                        insert.executeUpdate();
                    }
                }
            }
            var mapper = session.getMapper(EventMapper.class);
            for (boolean indexed : List.of(true, false)) {
                var filtered = PBHChartController.dailyTrend(mapper, Event::getSeen, Event::getDownloader, "a", first, end, indexed);
                assertEquals(List.of(new SimpleOffsetDateTimeIntKVDTO(first, 2), new SimpleOffsetDateTimeIntKVDTO(end, 1)), filtered);
                var all = PBHChartController.dailyTrend(mapper, Event::getSeen, Event::getDownloader, null, first, end, indexed);
                assertEquals(List.of(new SimpleOffsetDateTimeIntKVDTO(first, 4), new SimpleOffsetDateTimeIntKVDTO(end, 2)), all);
            }
        }
    }

    @Test
    void splitsAtBothAnOffsetChangeAndTheFollowingMidnight() {
        var zone = ZoneId.of("America/New_York");
        assertEquals(OffsetDateTime.parse("2024-11-03T01:00-05:00"),
                PBHChartController.nextTrendBoundary(OffsetDateTime.parse("2024-11-03T01:15-04:00"), zone));
        assertEquals(OffsetDateTime.parse("2024-11-04T00:00-05:00"),
                PBHChartController.nextTrendBoundary(OffsetDateTime.parse("2024-11-03T01:15-05:00"), zone));
        assertEquals(OffsetDateTime.parse("2024-03-10T03:00-04:00"),
                PBHChartController.nextTrendBoundary(OffsetDateTime.parse("2024-03-10T01:15-05:00"), zone));
    }
}
