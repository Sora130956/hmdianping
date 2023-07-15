package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.AutoInsert;
import com.hmdp.utils.JWTUtils;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        //验证手机号格式是否正确
        if(phoneInvalid){
            return Result.fail("手机格式不正确，发送失败。");
        }
        String code = RandomUtil.randomNumbers(6);
        //TIP 原本将code放入session的做法,在负载均衡的情况下,不同的tomcat服务器不共享session
        //TIP 所以将验证码信息存入redis。此处将login:code:手机号->验证码存入redis,特定的手机号只能从redis查出特定的验证码
        //TIP 设置验证码两分钟过期,防止redis中堆积过多的验证码
        stringRedisTemplate.opsForValue().set("login:code:"+phone,code,2, TimeUnit.MINUTES);
        log.debug("手机验证码为:{}",code);
        return Result.ok();
    }


    @Override
    public Result login(LoginFormDTO loginForm, HttpServletRequest httpServletRequest) {
        String phone = loginForm.getPhone();
        //TIP 引入redis来保存验证码和手机号之后,code需要从redis中获取。
        String code = stringRedisTemplate.opsForValue().get("login:code:" + phone);
        if(code==null)return Result.fail("未发送验证码！登陆失败。");
        if(code.equals(loginForm.getCode())){
            stringRedisTemplate.delete("login:code:"+phone);//TIP 登录之后,这个验证码就没用了
            LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
            qw.eq(User::getPhone,phone);
            User user = getOne(qw);
            if(user==null){
                user = new User();
                user.setPhone(phone);
                user = createUser(user);
            }
            UserDTO userDTO = new UserDTO();
            userDTO.setIcon(user.getIcon());
            userDTO.setId(user.getId());
            userDTO.setNickName(user.getNickName());
            Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO);
            //TIP 将userDTO转化为JWT的payload,并将其返回给前端,前端会将其存储在客户端的sessionStorage中。
            String jwtToken = JWTUtils.getJWTToken(userDTOMap,httpServletRequest);
            //在session中保存user，即设置用户的状态为已经登录 这里保存了整个user对象//session.setAttribute("user",user);
            return Result.ok(jwtToken);
        }
        return Result.fail("验证码错误！");
    }

    @AutoInsert
    public User createUser(User user){
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        log.debug("执行createUser方法");
        return user;
    }
}
