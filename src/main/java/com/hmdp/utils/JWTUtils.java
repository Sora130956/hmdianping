package com.hmdp.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import java.util.Calendar;
import java.util.Map;

@Slf4j
public class JWTUtils {
    private static final String SECRETKEY = "Sora626";

    //TIP 使用指定payload来创建JWTToken
    public static String getJWTToken(Map<String,Object> payload, HttpServletRequest httpServletRequest){
        //TIP 指定JWT过期时间为1天
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        JWTCreator.Builder builder = JWT.create();
        //TIP 构建payload
        payload.forEach((k,v) -> builder.withClaim(k,v.toString()));
        //TIP 将用户的ip地址加入负载中
        String realIP = GetUserIPHelper.getRealIP(httpServletRequest);
        builder.withClaim("IP",realIP);
        //TIP 使用对称加密对header和payload签名
        String token = builder.withExpiresAt(calendar.getTime())
                .sign(Algorithm.HMAC256(SECRETKEY));
        try {
            //TIP 在颁发JWT token之前,先用AES对称加密算法将token加密
            log.info("getJWTToken AES加密前的token"+token);
            token = AES.encrypt(token);
            log.info("getJWTToken AES加密后的token"+token);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return token;
    }

    //TIP 解析JWTtoken
    public static DecodedJWT decode(String token){
        //TIP 在解析JWT token时,先用AES对称加密算法将token解密
        try {
            log.info("DecodedJWT AES解密前的token"+token);
            token = AES.decrypt(token);
            log.info("DecodedJWT AES解密后的token"+token);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(SECRETKEY)).build();
        DecodedJWT decodedJWT = jwtVerifier.verify(token);
        return decodedJWT;
    }
}
