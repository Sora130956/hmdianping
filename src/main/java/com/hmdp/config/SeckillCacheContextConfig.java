package com.hmdp.config;


import com.sora.cache.CacheContext;
import com.sora.cache.CacheContextBuilder;
import com.sora.exception.CacheRuntimeException;
import com.sora.strategy.evict.CacheEvictConsts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


public class SeckillCacheContextConfig {

    private static CacheContext<String,Long> seckillCacheContext;

    static{
        try {
            seckillCacheContext = CacheContextBuilder.startBuilding().evictType(CacheEvictConsts.LRU).build();
        } catch (CacheRuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public static CacheContext<String,Long> getSeckillCacheContext(){
        return seckillCacheContext;
    }

}
