package com.ghostchu.peerbanhelper.util;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class URLUtil {
    /**
     * 在指定url后追加参数
     *
     * @param url  UR类地址
     * @param data 参数集合 key = value
     * @return 新 URL
     */
    public static String appendUrl(String url, Map<String, Object> data) {
        String newUrl = url;
        StringBuilder param = new StringBuilder();
        for (String key : data.keySet()) {
            param.append(key).append("=").append(data.get(key).toString()).append("&");
        }
        String paramStr = param.toString();
        paramStr = paramStr.substring(0, paramStr.length() - 1);
        if (newUrl.contains("?")) {
            newUrl += "&" + paramStr;
        } else {
            newUrl += "?" + paramStr;
        }
        return newUrl;
    }

    /**
     * 获取指定url中的某个参数
     *
     * @param url  URl 地址
     * @param name 参数名称
     * @return 参数值
     */
    public static String getParamByUrl(String url, String name) {
        url += "&";
        String pattern = "(\\?|&){1}#{0,1}" + name + "=[a-zA-Z0-9]*(&{1})";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(url);
        if (m.find()) {
            return m.group(0).split("=")[1].replace("&", "");
        } else {
            return null;
        }
    }

    public static String getParentName(URI uri) {
        try {
            // 查找文件路径中的上一级目录
            String[] pathSegments = uri.toString().split("/");
            if (pathSegments.length > 1) {
                return pathSegments[pathSegments.length - 2];
            } else {
                return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}
