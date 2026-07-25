package com.ghostchu.peerbanhelper.util.dns;

public enum DNSRoute {
    DEFAULT("default"),
    PTR("ptr"),
    BTN("btn"),
    RULE_SUB("rule_sub");
    
    private final String routeName;
    
    DNSRoute(String routeName){
        this.routeName = routeName;
    }

    public String getRouteName() {
        return routeName;
    }
}
