package com.hmdp.config;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.GetUserIPHelper;
import com.hmdp.utils.JWTUtils;
import com.hmdp.utils.UserHolder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.plugin.Intercepts;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Random;

@Slf4j
public class LoginCheckIntercepter implements HandlerInterceptor {

    private String testmode="TRUE";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(testmode.equals("TRUE")){
            log.info("跳过登录权限校验");
            UserDTO userDTO = new UserDTO();
            Random random = new Random();
            long id = random.nextLong();
            id = id%5000;//TIP 模拟5000个用户
            id = Math.abs(id);
            userDTO.setId(id);
            userDTO.setNickName("testUser");
            UserHolder.saveUser(userDTO);
            return true;
        }
        //TIP 从请求头中获得JWT token
        String JWTtoken = request.getHeader("authorization");
        if(JWTtoken==null||JWTtoken.length()==0){
            return false;
        }
        DecodedJWT decodedJWT = JWTUtils.decode(JWTtoken);
        String ip = decodedJWT.getClaim("IP").asString();
        String realIP = GetUserIPHelper.getRealIP(request);
        log.info("令牌中的用户ip地址:"+ip);
        log.info("用户的实际ip地址:"+realIP);
        if(ip==null||ip.length()==0||!ip.equals(realIP)){
            //TIP 如果IP与token中的IP不一致,要求用户重新登陆
            return false;
        }
        Long id = Long.valueOf(decodedJWT.getClaim("id").asString());
        String nickName = decodedJWT.getClaim("nickName").asString();
        String icon = decodedJWT.getClaim("icon").asString();
        UserDTO user = new UserDTO(id,nickName,icon);
        log.info("用户信息"+user.toString());
        if(id==null){
            //TIP 如果id为空,则判定为未登录状态,不允许访问controller
            return false;
        }
        //TIP 解析JWT token,将payload封装成userDTO对象
        //TIP 将用户信息存入threadLocal,放行
        UserHolder.saveUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //TIP afterCompletion在试图的渲染结束之后执行,通常用于清理资源
        //TIP 由于请求结束后,线程会返回tomcat线程池,在此之前我们应该把threadLocal中的用户信息清理掉
        UserHolder.removeUser();
    }
}
