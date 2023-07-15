package com.hmdp.utils;

import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

public class GetUserIPHelper {
    public static String getRealIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
            //TIP 多次反向代理后会有多个IP值，第一个IP才是真实IP
            ip = ip.split(",")[0];
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;}
}
