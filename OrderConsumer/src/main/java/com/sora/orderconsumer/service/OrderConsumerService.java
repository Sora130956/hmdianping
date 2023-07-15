package com.sora.orderconsumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import com.sora.orderconsumer.pojo.OrderInfo;
import com.sora.orderconsumer.pojo.VoucherOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.security.auth.Destroyable;
import java.io.IOException;
import java.time.LocalDateTime;

import static com.sora.orderconsumer.config.RabbitMQConfig.SECKILL_QUEUE;

@Service
public class OrderConsumerService implements Destroyable {
    @Autowired
    Connection connection;

    @Autowired
    ISeckillVoucherService seckillVoucherService;

    @Autowired
    IVoucherOrderService voucherOrderService;

    @Autowired
    ObjectMapper objectMapper;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    private static String SECKILL_ORDERIDSET_KEY = "seckill:orderidset:";

    public void consumeSeckillOrder() throws IOException {
        Channel channel = connection.createChannel();
        //TIP 设置消费者最多能维持的未ack的消息的个数
        channel.basicQos(64);
        Consumer consumer = new DefaultConsumer(channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                //TIP 在consumer订阅某个队列后,当rabbitMQ推送消息给当前消费者时,会调用这个回调函数
                OrderInfo orderInfo = objectMapper.readValue(body, OrderInfo.class);
                long size = stringRedisTemplate.opsForSet().add(SECKILL_ORDERIDSET_KEY, String.valueOf(orderInfo.getOrderId()));
                if(size==0){
                    //TIP 通过redis的set数据结构+全局唯一id来保证消息的幂等性,保证同一订单不会重复扣减库存、重复生成订单
                    //TODO 但是应该不用加分布式锁吧？因为多个线程来执行set的add操作时,应该只有一个线程能执行成功。而且就算因为某些原因出错了,生成订单时也会直接报错
                    channel.basicNack(envelope.getDeliveryTag(),false,false);//TODO 这里应该返回nack,而且这个消息不能重新入队
                    return;
                }
                //TIP 先生成订单再去扣减库存,这样如果重复生成订单,由于id具有唯一约束,生成订单会失败,直接报错。
                VoucherOrder voucherOrder = new VoucherOrder();
                voucherOrder.setVoucherId(orderInfo.getVoucherId());
                voucherOrder.setCreateTime(LocalDateTime.now());
                voucherOrder.setUserId(orderInfo.getUserId());
                voucherOrder.setId(orderInfo.getOrderId());
                voucherOrderService.save(voucherOrder);
                //TIP 扣减库存、生成订单
                seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id",orderInfo.getVoucherId()).update();
                channel.basicAck(envelope.getDeliveryTag(),false);//TIP 消费者手动确认,表示已经处理完消息,消息可以从MQ的队列中删除了
            }
        };
        //TIP 采用推模式,使consumer订阅SECKILL_QUEUE
        //TIP channel.basicConsume(SECKILL_QUEUE, consumer)方法会启动一个新的线程来消费指定队列中的消息。
        //TIP 它会注册一个消费者对象（consumer）来处理从队列中接收到的消息，当有消息到达队列时，消费者对象的回调函数会被触发，从而实现消息的消费。
        //TIP 在消费者一直存在的情况下，它会一直监听该队列，直到取消订阅或者连接关闭。
        //TIP 需要注意的是，在使用basicConsume方法时，需要保证连接对象（channel）是活跃状态，否则将无法接收到队列中的消息。
        channel.basicConsume(SECKILL_QUEUE,false,consumer);//TIP 通过第二个参数来关闭自动确认
    }
}
