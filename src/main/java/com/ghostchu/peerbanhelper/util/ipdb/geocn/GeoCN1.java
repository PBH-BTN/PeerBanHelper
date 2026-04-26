package com.ghostchu.peerbanhelper.util.ipdb.geocn;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.ipdb.IPGeoData;
import com.maxmind.db.*;
import io.sentry.Sentry;
import lombok.Getter;
import lombok.ToString;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class GeoCN1 implements AutoCloseable{

    private final Reader reader;

    public GeoCN1(File geoCNMmdb, NodeCache nodeCache) throws IOException {
        this.reader = new Reader(geoCNMmdb, nodeCache);
    }

    public IPGeoData query(InetAddress address) throws IOException {
        try {
            CNLookupResult cnLookupResult = reader.get(address, CNLookupResult.class);
            if (cnLookupResult == null) {
                return null;
            }
            IPGeoData ipGeoData = new IPGeoData();
            // City Data
            IPGeoData.CityData cityResponse = new IPGeoData.CityData();
            String cityName = (cnLookupResult.getProvince() + " " + cnLookupResult.getCity() + " " + cnLookupResult.getDistricts()).trim();
            if (!cityName.isBlank()) {
                cityResponse.setName(cityName);
            }
            Integer code = null;
            if (cnLookupResult.getProvinceCode() != null) {
                code = cnLookupResult.getProvinceCode().intValue();
            }
            if (cnLookupResult.getCityCode() != null) {
                code = cnLookupResult.getCityCode().intValue();
            }
            if (cnLookupResult.getDistrictsCode() != null) {
                code = cnLookupResult.getDistrictsCode().intValue();
            }
            cityResponse.setIso(Long.parseLong("86" + code));
            cityResponse.setCnProvince(cnLookupResult.getProvince());
            cityResponse.setCnCity(cnLookupResult.getCity());
            cityResponse.setCnDistricts(cnLookupResult.getDistricts());
            ipGeoData.setCity(cityResponse);
            // Network Data
            IPGeoData.NetworkData networkData = new IPGeoData.NetworkData();
            if (cnLookupResult.getIsp() != null && !cnLookupResult.getIsp().isBlank()) {
                networkData.setIsp(cnLookupResult.getIsp());
            }
            if (cnLookupResult.getNet() != null && !cnLookupResult.getNet().isBlank()) {
                TranslationComponent component = new TranslationComponent(cnLookupResult.getNet());
                switch (cnLookupResult.getNet()) {
                    case "宽带" -> new TranslationComponent(Lang.NET_TYPE_WIDEBAND);
                    case "基站" -> new TranslationComponent(Lang.NET_TYPE_BASE_STATION);
                    case "政企专线" -> new TranslationComponent(Lang.NET_TYPE_GOVERNMENT_AND_ENTERPRISE_LINE);
                    case "业务平台" -> new TranslationComponent(Lang.NET_TYPE_BUSINESS_PLATFORM);
                    case "骨干网" -> new TranslationComponent(Lang.NET_TYPE_BACKBONE_NETWORK);
                    case "IP专网" -> new TranslationComponent(Lang.NET_TYPE_IP_PRIVATE_NETWORK);
                    case "网吧" -> new TranslationComponent(Lang.NET_TYPE_INTERNET_CAFE);
                    case "物联网" -> new TranslationComponent(Lang.NET_TYPE_IOT);
                    case "数据中心" -> new TranslationComponent(Lang.NET_TYPE_DATACENTER);
                }
                networkData.setNetType(tlUI(component));
            }
            ipGeoData.setNetwork(networkData);
            return ipGeoData;
        } catch (Exception e) {
            Sentry.captureException(e);
            return null;
        }
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }

    @Getter
    @ToString
    public static class CNLookupResult {
        private final String isp;
        private final String net;
        private final String province;
        private final Long provinceCode;
        private final String city;
        private final Long cityCode;
        private final String districts;
        private final Long districtsCode;

        @MaxMindDbConstructor
        public CNLookupResult(
                @MaxMindDbParameter(name = "isp") String isp,
                @MaxMindDbParameter(name = "net") String net,
                @MaxMindDbParameter(name = "province") String province,
                @MaxMindDbParameter(name = "provinceCode") Object provinceCode,
                @MaxMindDbParameter(name = "city") String city,
                @MaxMindDbParameter(name = "cityCode") Object cityCode,
                @MaxMindDbParameter(name = "districts") String districts,
                @MaxMindDbParameter(name = "districtsCode") Object districtsCode
        ) {
            this.isp = isp;
            this.net = net;
            this.province = province;
            this.provinceCode = provinceCode != null ? Long.parseLong(provinceCode.toString()) : null;
            this.city = city;
            this.cityCode = cityCode != null ? Long.parseLong(cityCode.toString()) : null;
            this.districts = districts;
            this.districtsCode = districtsCode != null ? Long.parseLong(districtsCode.toString()) : null;
        }
    }

}
