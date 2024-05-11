package com.ghostchu.peerbanhelper.ipdb.ipproxy;

public class ProxyResult {
    public int Is_Proxy;
    public String Proxy_Type;
    public String Country_Short;
    public String Country_Long;
    public String Region;
    public String City;
    public String ISP;
    public String Domain;
    public String Usage_Type;
    public String ASN;
    public String AS;
    public String Last_Seen;
    public String Threat;
    public String Provider;

    ProxyResult() {

    }

    @Override
    public String toString() {
        return "ProxyResult{" +
                "Is_Proxy=" + Is_Proxy +
                ", Proxy_Type='" + Proxy_Type + '\'' +
                ", Country_Short='" + Country_Short + '\'' +
                ", Country_Long='" + Country_Long + '\'' +
                ", Region='" + Region + '\'' +
                ", City='" + City + '\'' +
                ", ISP='" + ISP + '\'' +
                ", Domain='" + Domain + '\'' +
                ", Usage_Type='" + Usage_Type + '\'' +
                ", ASN='" + ASN + '\'' +
                ", AS='" + AS + '\'' +
                ", Last_Seen='" + Last_Seen + '\'' +
                ", Threat='" + Threat + '\'' +
                ", Provider='" + Provider + '\'' +
                '}';
    }
}