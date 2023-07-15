package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisIDGenerater;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@SpringBootTest
@Transactional
class HmDianPingApplicationTests {

    @Autowired
    IUserService userService;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedisIDGenerater redisIDGenerater;


    @Test
    public void testLocalDateTime(){
        LocalDateTime now = LocalDateTime.now();
        long timeStamp = now.toEpochSecond(ZoneOffset.UTC);//TIP 获得现在到1970年1月1日0时0分0秒相差的秒数（时间戳）
        System.out.println(timeStamp);//1682526280
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime parse = LocalDateTime.parse("2023-01-01 00:00:00",dateTimeFormatter);
        long startTimeStamp = parse.toEpochSecond(ZoneOffset.UTC);//TIP 获得开业时间到1970年1月1日0时0分0秒相差的秒数（时间戳）
        System.out.println(startTimeStamp);//1672531200
        System.out.println(timeStamp-startTimeStamp);//TIP 9995080 redisID中需要的时间戳（现在距离开业时间的时间戳）
    }

    @Test
    public void testRedisIDGenerater(){
        for(int i=0;i<1234;i++){
            long id = redisIDGenerater.getID("hm:yhq:");
            System.out.println(id);
        }
    }
}
