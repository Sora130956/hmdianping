package com.hmdp.config;


import com.sora.cache.CacheContext;
import com.sora.cache.CacheContextBuilder;
import com.sora.exception.CacheRuntimeException;
import com.sora.strategy.evict.CacheEvictConsts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeckillCacheContextConfig {

    @Bean
    public CacheContext<String,Integer> secKillCacheContext() throws CacheRuntimeException {
        return CacheContextBuilder.startBuilding()
                .evictType(CacheEvictConsts.LRU)
                .build();
    }

}
