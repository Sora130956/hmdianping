package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisHotObject;
import com.hmdp.utils.RedisUtil;
import com.hmdp.utils.ShopBloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    ObjectMapper objectMapper;//TIP SpringBoot的web组件已经整合了Jackson,所以可以直接@Autowired

    @Autowired
    ShopBloomFilter shopBloomFilter;

    public Result queryById(Long id){
        //TIP 在按id查询商铺之前,先使用布隆过滤器判断该id是否存在,防止缓存穿透
        if(!shopBloomFilter.exists(id)){
            return Result.fail("该商铺id不存在！");
        }
        //TIP 在业务层中封装一个方法来实现走redis缓存的查询方法 这个方法实现了缓存写策略
        //TIP 如果redis中没有缓存目标数据（没有命中缓存） 则去数据库查出数据再缓存到redis中
        //TIP 如果命中缓存,直接返回缓存数据
        //TIP 在业务层中,将查询shop的方法都更换成这个方法,以后页面发请求时走这个方法,就使用了缓存
        String shopJSON = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        if(StrUtil.isNotBlank(shopJSON)){
            Shop shop = null;
            try {
                shop = objectMapper.readValue(shopJSON, Shop.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            return Result.ok(shop);
        }else{
            Shop shop = this.getById(id);
            try {
                stringRedisTemplate.opsForValue().set("cache:shop:"+id,objectMapper.writeValueAsString(shop));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            return Result.ok(shop);
        }
    }

    public Result queryHotShop(Long id){
        String redisKey = "cache:shop:"+id;
        String lockKey = "cache:hotShopLock:" + id;
        String hotShop = stringRedisTemplate.opsForValue().get(redisKey);//TIP 由于使用的是逻辑过期方法来解决缓存击穿,所以理论上一定能命中缓存
        if(StringUtils.isEmpty(hotShop)){
            redisUtil.recreateRedisKey(redisKey,lockKey,this,id);//TODO DELETE
            return Result.fail("商铺不存在！");
        }
        try {
            RedisHotObject redisHotObject = objectMapper.readValue(hotShop, RedisHotObject.class);
            //TIP JSON字符串反序列化后应该得到一个对象,但由于这里用于接收对象的类型时Object,由于没有指定类型,用LinkedHashMap来存储JSON中的的每一个字段
            Object shop = redisHotObject.getData();
            LocalDateTime exprieTime = redisHotObject.getExprieTime();
            System.out.println(exprieTime);
            if(exprieTime.isBefore(LocalDateTime.now())){
                //TIP 如果过期时间在当前时间之前,则已经过期了,需要尝试获得互斥锁然后新开一个线程去重建缓存
                Boolean getLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1");
                if(getLock){
                    log.info("获得锁成功,新开一个线程去重建缓存");
                    //TIP doubleCheck缓存是否过期。因为有可能运行到这一步时缓存刚重建完,锁刚刚释放。
                    //TIP 如果doubleCheck发现确实没过期,释放锁并返回数据
                    Object newShop = checkIfNotExpire(redisKey);
                    if(newShop!=null){
                        log.info("doublecheck发现刚重建完缓存");
                        stringRedisTemplate.delete(lockKey);//TIP 释放锁！！！！！！！
                        return Result.ok(newShop);
                    }
                    //TIP 获得锁成功,新开一个线程去重建缓存,然后返回过期数据
                    redisUtil.recreateRedisKey(redisKey,lockKey,this,id);
                    log.info("任务成功提交到新线程,返回过期数据");
                    return Result.ok(shop);
                }else{
                    //TIP 获得锁失败,说明已经有一个线程在做重建缓存的操作了,直接返回过期数据
                    return Result.ok(shop);
                }
            }else{
                //TIP 数据未过期,直接返回
                return Result.ok(shop);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Object checkIfNotExpire(String redisKey){
        String hotShop = stringRedisTemplate.opsForValue().get(redisKey);
        RedisHotObject redisHotObject = null;
        try {
            redisHotObject = objectMapper.readValue(hotShop, RedisHotObject.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Object shop = redisHotObject.getData();
        LocalDateTime exprieTime = redisHotObject.getExprieTime();
        if(exprieTime.isBefore(LocalDateTime.now())){
            System.out.println("!!!判断过期了");
            return null;
        }else{
            return shop;
        }
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("商店id不存在");
        }
        String redisKey = "cache:shop:"+id;
        stringRedisTemplate.delete(redisKey);
        this.updateById(shop);
        redisUtil.deleteCacheByDelay(redisKey);
        log.info("已提交双删任务到线程池");
        return Result.ok();
    }

    @Override
    public Result saveShop(Shop shop) {
        //TIP 先更新布隆过滤器再存shop
        //TIP 此处应该先save再获得商铺的id,因为Shop实体类的id字段设置为type = IdType.AUTO,在save之后,其id字段会被填充。传来的shop参数本身是不带id字段的。
        this.save(shop);
        shopBloomFilter.add(shop.getId());
        return Result.ok(shop.getId());
    }
}
