package cordelia.rpc.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.NoSuchElementException;

@Getter
@RequiredArgsConstructor
public enum Status {
    STOPPED(0),
    QUEUED_TO_VERIFY(1),
    VERIFYING(2),
    QUEUED_TO_DOWNLOAD(3),
    DOWNLOADING(4),
    QUEUED_TO_SEED(5),
    SEEDING(6);

    private final int idx;

    public static Status fromIdx(int idx) {
        for (Status value : values()) {
            if (value.getIdx() == idx) return value;
        }
        throw new NoSuchElementException();
    }
}
