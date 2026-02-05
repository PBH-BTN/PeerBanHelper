package com.ghostchu.peerbanhelper.util.ipdb;

import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class IPGeoData {
    @Nullable
    private CityData city;
    @Nullable
    private CountryData country;
    @Nullable
    private ASData as;
    @Nullable
    private NetworkData network;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static final class CityData {
        @Nullable
        private String name;

        @Nullable
        @JsonUtil.Hidden
        private Long iso;

        @Nullable
        @JsonUtil.Hidden
        private String cnProvince;

        @Nullable
        @JsonUtil.Hidden
        private String cnCity;

        @Nullable
        @JsonUtil.Hidden
        private String cnDistricts;

//        @AllArgsConstructor
//        @NoArgsConstructor
//        @Data
//        public static final class LocationData {
//            @Nullable
//            private Double latitude;
//            @Nullable
//            private Double longitude;
//            @Nullable
//            private String timeZone;
//            @Nullable
//            private Integer accuracyRadius;
//        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static final class CountryData {
        @Nullable
        @JsonUtil.Hidden
        private String name;
        @Nullable
        private String iso;

    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static final class ASData {
        @Nullable
        private Long number;
        @Nullable
        @JsonUtil.Hidden
        private String organization;
        @Nullable
        @JsonUtil.Hidden
        private String ipAddress;
        @Nullable
        @JsonUtil.Hidden
        private ASNetwork network;

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static final class ASNetwork {
            @Nullable
            private String ipAddress;
            @Nullable
            private Integer prefixLength;
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static final class NetworkData {
        @Nullable
        private String isp;
        @Nullable
        private String netType;
    }
}
