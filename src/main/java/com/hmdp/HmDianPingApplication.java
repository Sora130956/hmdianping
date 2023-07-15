package com.hmdp;

import cn.hutool.bloomfilter.BloomFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.SeckillCacheContextConfig;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.ShopBloomFilter;
import com.sora.cache.CacheContext;
import com.sora.exception.CacheRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.zookeeper.data.Stat;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_TIME_KEY;

@MapperScan("com.hmdp.mapper")
@SpringBootApplication
@Slf4j
@EnableTransactionManagement
@EnableAspectJAutoProxy(exposeProxy = true)
public class HmDianPingApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(HmDianPingApplication.class, args);
        ShopBloomFilter bloomFilter = applicationContext.getBean(ShopBloomFilter.class);
        ISeckillVoucherService seckillVoucherService = applicationContext.getBean(ISeckillVoucherService.class);
        StringRedisTemplate stringRedisTemplate = applicationContext.getBean(StringRedisTemplate.class);
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
        //TIP 在项目启动时 新开一个线程去异步地初始化布隆过滤器、以及将秒杀券的库存量缓存到redis
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                bloomFilter.init();
                List<SeckillVoucher> list = seckillVoucherService.list();
                for(SeckillVoucher voucher : list){
                    //TIP 将秒杀券的库存量缓存到redis
                    Long voucherId = voucher.getVoucherId();
                   /* try {
                        stringRedisTemplate.opsForValue().setIfAbsent(SECKILL_STOCK_KEY+voucherId,objectMapper.writeValueAsString(voucher.getStock()));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }*/
                    CacheContext<String, Long> seckillCacheContext = SeckillCacheContextConfig.getSeckillCacheContext();
                    try {
                        seckillCacheContext.put(SECKILL_STOCK_KEY+voucherId, Long.valueOf(voucher.getStock()));
                    } catch (CacheRuntimeException e) {
                        throw new RuntimeException(e);
                    }
                    //TIP 将秒杀券的开始时间缓存到redis,其中过期时间为秒杀券的销售截止时间。
                    //TIP 在业务流程中,先从redis查出开始时间来判断是否开始销售,如果查不出来说明销售截止了。
                    LocalDateTime endTime = voucher.getEndTime();
                    LocalDateTime now = LocalDateTime.now();
                    Duration duration = Duration.between(now, endTime);
                    long seconds = duration.getSeconds();
                    LocalDateTime beginTime = voucher.getBeginTime();
                    if(now.isBefore(endTime)){
                        //TIP 现在在截止时间之前,说明还在卖,把开始时间存入redis供之后的业务逻辑使用
                        try {
                            //TIP 将秒杀券的开始时间缓存到redis,其中过期时间为秒杀券的销售截止时间。
                            stringRedisTemplate.opsForValue().setIfAbsent(SECKILL_TIME_KEY+voucherId,objectMapper.writeValueAsString(beginTime),seconds, TimeUnit.SECONDS);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                log.info("秒杀券缓存完毕");
            }
        });
        thread.start();
    }

    @Autowired
    CuratorFramework zookeeperClient;

    @PostConstruct
    private void initService(){
        //TIP 保证/soldoutsign结点存在
        try {
            zookeeperClient.create().forPath("/soldoutsign");
        } catch (Exception e) {
            log.info("/soldoutsign结点已创建");
        }

        //TIP 创建监听对象,监听zookeeper中的/soldoutsign结点下的子节点
        PathChildrenCache pathChildrenCache = new PathChildrenCache(zookeeperClient,"/soldoutsign",true);

        //TIP 绑定监听器
        pathChildrenCache.getListenable().addListener(new PathChildrenCacheListener() {
            //TIP 当监听对象发现zookeeper中被监听的结点的子节点发生变化时就会运行监听器的childEvent函数
            @Override
            public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent) throws Exception {
                //TIP 处理字符串得到是哪个voucherId的售完标志结点被创建/删除,同步到本地内存级别的售完标志ConcurrentHashMap
                String path = pathChildrenCacheEvent.getData().getPath();
                String voucherIdStr = path.substring(path.lastIndexOf("/") + 1);
                Long voucherId = Long.valueOf(voucherIdStr);
                switch (pathChildrenCacheEvent.getType()){
                    case CHILD_ADDED:{
                        log.info("收到zookeeper的通知,创建售完标志"+voucherId);
                        VoucherOrderServiceImpl.createSoldOutSignal(voucherId);
                        break;
                    }
                    case CHILD_REMOVED:{
                        log.info("收到zookeeper的通知,删除售完标志"+voucherId);
                        VoucherOrderServiceImpl.removeSoldOutSignal(voucherId);
                        break;
                    }
                    default:
                        break;
                }
            }
        });

        //TIP 开始监听
        try {
            pathChildrenCache.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}


















