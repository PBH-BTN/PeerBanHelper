package com.ghostchu.peerbanhelper.wrapper;

import com.maxmind.geoip2.model.CityResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeoWrapper {
    private String iso;
    private String countryRegion;
    private String city;
    private Double latitude;
    private Double longitude;
    private Integer accuracyRadius;

    public GeoWrapper(@NotNull CityResponse cityResponse) {
        if (cityResponse.getCountry() != null) {
            this.iso = cityResponse.getCountry().getIsoCode();
            this.countryRegion = cityResponse.getCountry().getName();
        }
        if (cityResponse.getCity() != null) {
            this.city = cityResponse.getCity().getName();
        }
        if (cityResponse.getLocation() != null) {
            this.latitude = cityResponse.getLocation().getLatitude();
            this.longitude = cityResponse.getLocation().getLongitude();
            this.accuracyRadius = cityResponse.getLocation().getAccuracyRadius();
        }
    }
}