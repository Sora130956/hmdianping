package com.hmdp.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserBloomFilterConfig {

    @Autowired
    RedissonClient redissonClient;

    @Bean("userBloomFilter")
    public RBloomFilter getUserBloomFilter(){
        RBloomFilter<Object> userBloomFilter = redissonClient.getBloomFilter("seckillUserList");
        userBloomFilter.tryInit(10000L,0.0001);//TIP 预计数据量为一万条数据,允许0.1%的误差,大小为907KB
        return userBloomFilter;
    }
}
