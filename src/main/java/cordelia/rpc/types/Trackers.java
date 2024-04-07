package cordelia.rpc.types;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public final class Trackers {

    private String announce;
    private Integer id;
    private String scrape;
    private String sitename;
    private Integer tier;

}
