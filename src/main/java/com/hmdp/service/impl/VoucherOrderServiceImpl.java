package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.OrderInfoDeliver;
import com.hmdp.utils.RedisIDGenerater;
import com.hmdp.utils.RedisUtil;
import com.hmdp.utils.UserHolder;
import com.sora.cache.CacheContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import static com.hmdp.utils.RedisConstants.*;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIDGenerater redisIDGenerater;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CuratorFramework zookeeperClient;

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    RedissonClient redissonClient;

    @Resource(name = "userBloomFilter")
    RBloomFilter userBloomFilter;

    @Autowired
    OrderInfoDeliver orderInfoDeliver;

    @Autowired
    CacheContext seckillCacheContext;


    //TIP 引入内存级别的售完标志:秒杀券id->售完标志
    private static ConcurrentHashMap<Long,Boolean> soldOutMap = new ConcurrentHashMap<>();


    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //TIP 通过内存级别的售完标志来判断库存是否不足,来避免走redis级别的缓存
        if(soldOutMap.containsKey(voucherId)){
            return Result.fail("库存不足");
        }

        //TIP 如果用户下过单则直接返回
        if(userBloomFilter.contains(UserHolder.getUser().getId()+":"+voucherId)){
            return Result.fail("不允许重复下单！");
        }


        //TIP 还是应该先判断活动开没开始再去预减库存！因为引入了售完标志,之前的就算库存不足也会走redis的问题已经解决了。
        //TIP 而且活动没开始就去减库存！还白白浪费redis资源,甚至于并发量很大的话,还会把售完标志置为true！导致业务流程变得复杂！
        String voucherTimeKey = SECKILL_TIME_KEY+voucherId;
        String voucherTimeStr = stringRedisTemplate.opsForValue().get(voucherTimeKey);
        LocalDateTime beginTime=null;
        if(voucherTimeStr!=null){
            try {
                beginTime = objectMapper.readValue(voucherTimeStr, LocalDateTime.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }else{
            return Result.fail("秒杀已经结束");
        }
        if(beginTime.isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }

        RLock lock = redissonClient.getLock(SECKILL_USER_LOCK + UserHolder.getUser());
        boolean getLock = true;
        try {
            getLock = lock.tryLock();//TIP 用户获得自己的锁 如果获取失败说明该用户的订单已经在处理中,直接返回。
            if(getLock){
                VoucherOrderServiceImpl voucherOrderService = (VoucherOrderServiceImpl)AopContext.currentProxy();
                return voucherOrderService.secKill(voucherId);
            }else{
                return Result.fail("不允许重复下单");
            }
        }catch (Exception e){
            System.out.println("下单时出现异常");
        }finally {
            if(getLock){
                lock.unlock();
            }
        }
        return Result.fail("下单时出现异常");
    }

    @Transactional
    public Result secKill(Long voucherId){
        //TIP 布隆过滤器实现一人一单 判断该用户是否曾经下过单
        boolean hasOrdered = userBloomFilter.contains(UserHolder.getUser().getId()+":"+voucherId);
        if(hasOrdered){
            return Result.fail("不允许重复下单！");
        }

        //TIP redis预减库存,不走数据库,返回减库存后的库存量
        //TIP 由于redis是单线程模型,所以所有线程会串行化地去预扣减库存,不会存在两个线程并发地读到同一个库存值的情况
        //TIP 也就是,每个线程跑到这一步,stock的值对每个线程来说是唯一的
        String voucherStockKey = SECKILL_STOCK_KEY + voucherId;
        Long stock = stringRedisTemplate.opsForValue().decrement(voucherStockKey);
        if(stock<0){
            //TIP 如果预扣减之后库存量小于0说明库存量不足,回滚redis的预扣减操作,返回提示信息
            //TIP 就是库存不够,没买到票！
            stringRedisTemplate.opsForValue().increment(voucherStockKey);
            createSoldOutSignal(voucherId);//TIP 设置售完标志
            try {
                //TIP 在业务方法中,将对售完标志的删除、创建操作通知到zookeeper,让tomcat集群中的其他机器能够同步售完标志
                zookeeperClient.create().forPath("/soldoutsign/"+voucherId);
                log.info("让zookeeper创建售完标志/soldoutsign/"+voucherId);
            } catch (Exception ex) {
                log.info("zookeeper已存在售完标志/soldoutsign/"+voucherId);
            }
            return Result.fail("库存不足");
        }
        Long orderId = null;
        try{
            //TIP 到这一步说明库存量充足,将订单发送到BlockingQueue中,通过OrderInfoDeliver中的channel来将消息发送到MQ中
            orderId = orderInfoDeliver.postVoucherId(voucherId);
        }catch (Exception e){
            //TIP 就算抛异常也不应该恢复redis中的库存量,因为消息可能已经被投递到消息队列了
            return Result.fail("订单创建失败！");
        }


        //TIP 走到这一步说明去数据库扣减库存,并且消息已经投递到消息队列了,将用户id:商品id(因为是某个用户对某个秒杀商品只能下一单)加入布隆过滤器,返回订单号
        userBloomFilter.add(UserHolder.getUser().getId()+":"+voucherId);
        return Result.ok(orderId);
    }

    public static boolean removeSoldOutSignal(Long voucherId){
        log.info("移除售完标志"+voucherId);
        soldOutMap.remove(voucherId);
        return true;
    }

    public static boolean createSoldOutSignal(Long voucherId){
        soldOutMap.put(voucherId,true);
        log.info("创建售完标志"+voucherId);
        return true;
    }
}
