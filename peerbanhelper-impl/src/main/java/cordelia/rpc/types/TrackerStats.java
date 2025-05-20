package cordelia.rpc.types;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public final class TrackerStats {

    private Integer announceState;
    private String announce;
    private Integer downloadCount;
    private Boolean hasAnnounced;
    private Boolean hasScraped;
    private String host;
    private Integer id;
    private Boolean isBackup;
    private Integer lastAnnouncePeerCount;
    private String lastAnnounceResult;
    private Long lastAnnounceStartTime;
    private Boolean lastAnnounceSucceeded;
    private Long lastAnnounceTime;
    private Boolean lastAnnounceTimedOut;
    private String lastScrapeResult;
    private Long lastScrapeStartTime;
    private Boolean lastScrapeSucceeded;
    private Long lastScrapeTime;
    private Boolean lastScrapeTimedOut;
    private Integer leecherCount;
    private Long nextAnnounceTime;
    private Long nextScrapeTime;
    private Integer scrapeState;
    private String scrape;
    private Integer seederCount;
    private String sitename;
    private Integer tier;

}
