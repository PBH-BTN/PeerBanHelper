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

        public CityData merge(CityData other, boolean overwrite) {
            if (other.name != null) {
                this.name = this.name == null ? other.name : (overwrite ? other.name : this.name);
            }
            if (other.iso != null) {
                this.iso = this.iso == null ? other.iso : (overwrite ? other.iso : this.iso);
            }
            if (other.cnProvince != null) {
                this.cnProvince = this.cnProvince == null ? other.cnProvince : (overwrite ? other.cnProvince : this.cnProvince);
            }
            if (other.cnCity != null) {
                this.cnCity = this.cnCity == null ? other.cnCity : (overwrite ? other.cnCity : this.cnCity);
            }
            if (other.cnDistricts != null) {
                this.cnDistricts = this.cnDistricts == null ? other.cnDistricts : (overwrite ? other.cnDistricts : this.cnDistricts);
            }
            return this;
        }
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

        public CountryData merge(CountryData other, boolean overwrite) {
            if (other.name != null) {
                this.name = this.name == null ? other.name : (overwrite ? other.name : this.name);
            }
            if (other.iso != null) {
                this.iso = this.iso == null ? other.iso : (overwrite ? other.iso : this.iso);
            }
            return this;
        }

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

        public ASData mergeFrom(ASData other, boolean overwrite) {
            if (other.number != null) {
                this.number = this.number == null ? other.number : (overwrite ? other.number : this.number);
            }
            if (other.organization != null) {
                this.organization = this.organization == null ? other.organization : (overwrite ? other.organization : this.organization);
            }
            if (other.ipAddress != null) {
                this.ipAddress = this.ipAddress == null ? other.ipAddress : (overwrite ? other.ipAddress : this.ipAddress);
            }
            if (other.network != null) {
                this.network = this.network == null ? other.network : this.network.mergeFrom(other.network, overwrite);
            }
            return this;
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Data
        public static final class ASNetwork {
            @Nullable
            private String ipAddress;
            @Nullable
            private Integer prefixLength;

            public ASNetwork mergeFrom(ASNetwork other, boolean overwrite) {
                if (other.ipAddress != null) {
                    this.ipAddress = this.ipAddress == null ? other.ipAddress : (overwrite ? other.ipAddress : this.ipAddress);
                }
                if (other.prefixLength != null) {
                    this.prefixLength = this.prefixLength == null ? other.prefixLength : (overwrite ? other.prefixLength : this.prefixLength);
                }
                return this;
            }
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

        public NetworkData mergeFrom(NetworkData other, boolean overwrite) {
            if (other.isp != null) {
                this.isp = this.isp == null ? other.isp : (overwrite ? other.isp : this.isp);
            }
            if (other.netType != null) {
                this.netType = this.netType == null ? other.netType : (overwrite ? other.netType : this.netType);
            }
            return this;
        }
    }

    public IPGeoData mergeFrom(IPGeoData other, boolean overwrite) {
        if (other.city != null) {
            this.city = this.city == null ? other.city : this.city.merge(other.city, overwrite);
        }
        if (other.country != null) {
            this.country = this.country == null ? other.country : this.country.merge(other.country, overwrite);
        }
        if (other.as != null) {
            this.as = this.as == null ? other.as : this.as.mergeFrom(other.as, overwrite);
        }
        if (other.network != null) {
            this.network = this.network == null ? other.network : this.network.mergeFrom(other.network, overwrite);
        }
        return this;
    }
}
