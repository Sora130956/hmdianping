package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDGenerater {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    private static final long START_TIME_STAMP = 1672531200;//TIP 2023-01-01 00:00:00到1970年1月1日0时0分0秒相差的秒数（开业时间的时间戳）

    public long getID(String prefix){
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String today = now.format(dateTimeFormatter);//TIP 获得今天的年月日
        String redisKey = prefix+today;//TIP 与业务前缀拼接,形成当前业务今天的商品ID的key名。
        //TIP 使其对应的value自增1,得到返回值,得到商品序列号。如果今天还没有创建这个key,则increment会自动创建
        Long seq = stringRedisTemplate.opsForValue().increment(redisKey);
        long nowTimeStamp = now.toEpochSecond(ZoneOffset.UTC);//TIP 获得今天到1970年1月1日想差的描述（今天的时间戳）
        return seq|((nowTimeStamp-START_TIME_STAMP)<<32);//TIP 用今天的时间戳减去开业的时间戳,与商品序列号拼接成当前商品的ID
    }
}
