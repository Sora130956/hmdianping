package com.hmdp.utils;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisUtil {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;//TIP SpringBoot的web组件已经整合了Jackson,所以可以直接@Autowired

    private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(32,33,1,TimeUnit.MINUTES,new ArrayBlockingQueue<>(33));

    private class DelayDeleteCache implements Runnable{
        private String redisKey=null;

        public DelayDeleteCache(String aRedisKey){
            this.redisKey=aRedisKey;
        }
        @Override
        public void run() {
            try {
                log.info("start delay delete cache sleep");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.info("after sleep,delete cache");
            stringRedisTemplate.delete(redisKey);
        }
    }

    private class ReCreateRedisKey implements Runnable{
        private String reidsKey=null;
        private String lockKey=null;
        private IService service;
        private Long queryId;
        @Override
        public void run() {
            //TIP 在之前维护了一个线程池的工具类中新开一个私有内部类,实现Runnable,run方法实现新开一个线程去重建缓存的功能
            if(queryId==null){
                throw new NullPointerException();
            }
            log.info("id为"+queryId+"的商铺的缓存过期了,新开一个线程来重建缓存。");
            Object obj = service.getById(queryId);
            RedisHotObject redisHotObject = new RedisHotObject();
            System.out.println("service查询出的obj的类型"+obj.getClass());
            redisHotObject.setData(obj);
            redisHotObject.setExprieTime(LocalDateTime.now().plusHours(1));
            log.info("重建后的过期时间为"+redisHotObject.getExprieTime());
            try {
                stringRedisTemplate.opsForValue().set(reidsKey,objectMapper.writeValueAsString(redisHotObject));
                stringRedisTemplate.delete(lockKey);//TIP 重建缓存完毕之后要释放互斥锁
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        public ReCreateRedisKey(String aRedisKey,String lockKey,IService aService,Long id){
            this.reidsKey=aRedisKey;
            this.lockKey=lockKey;
            this.service=aService;
            this.queryId=id;
        }
    }

    public boolean deleteCacheByDelay(String redisKey){
        threadPool.execute(new DelayDeleteCache(redisKey));
        return true;
    }

    public boolean recreateRedisKey(String aRedisKey,String lockKey,IService aService,Long id){
        threadPool.execute(new ReCreateRedisKey(aRedisKey,lockKey,aService,id));
        return true;
    }

    private class RemoveSoldOutSignal implements Runnable{

        private Long voucherId;

        @Override
        public void run() {
            log.info("延迟一段时间再去清除售完标志");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            VoucherOrderServiceImpl.removeSoldOutSignal(voucherId);
        }

        RemoveSoldOutSignal(Long voucherId){
            this.voucherId=voucherId;
        }
    }

    public boolean removeSoldOutSignal(Long voucherId){
        threadPool.execute(new RemoveSoldOutSignal(voucherId));
        return true;
    }
}
