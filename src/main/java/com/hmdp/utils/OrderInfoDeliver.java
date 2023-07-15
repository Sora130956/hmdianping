package com.hmdp.utils;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.OrderInfo;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import java.io.IOException;
import java.util.Collection;
import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;

import static com.hmdp.config.RabbitMQConfig.SECKILL_EXCHANGE;
import static com.hmdp.config.RabbitMQConfig.SECKILL_ROUTING_KEY;

@Component
public class OrderInfoDeliver implements Destroyable {
    @Autowired
    RedisIDGenerater redisIDGenerater;

    private BlockingQueue<OrderInfo> orderInfos = new LinkedBlockingQueue<>();

    public OrderInfoDeliver(@Autowired Connection connection, @Autowired ObjectMapper objectMapper) throws IOException {
        Runnable runnable = new Runnable() {


            Channel channel = connection.createChannel();
            private SortedMap<Long,OrderInfo> unconfirmOrderMap = new ConcurrentSkipListMap();//TIP 需要在服务端维持deliveryTag->订单数据


            @Override
            public void run() {

                try {
                    channel.confirmSelect();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }


                channel.addConfirmListener(new ConfirmListener() {
                    @Override
                    public void handleAck(long deliveryTag, boolean multiple) throws IOException {
                        if(multiple){
                            unconfirmOrderMap.headMap(deliveryTag+1).clear();//TODO deliveryTag+1对吗？
                        }else{
                            unconfirmOrderMap.remove(deliveryTag);
                        }
                    }

                    @Override
                    public void handleNack(long deliveryTag, boolean multiple) throws IOException {
                        try {
                            Thread.sleep(500);
                            if(multiple){
                                SortedMap<Long, OrderInfo> resendOrderInfos = unconfirmOrderMap.headMap(deliveryTag + 1);
                                Collection<OrderInfo> infos = resendOrderInfos.values();
                                for (OrderInfo o : infos){
                                    channel.basicPublish(SECKILL_EXCHANGE,SECKILL_ROUTING_KEY, MessageProperties.PERSISTENT_TEXT_PLAIN,objectMapper.writeValueAsBytes(o));
                                }
                            }else{
                                OrderInfo orderInfo = unconfirmOrderMap.get(deliveryTag);
                                channel.basicPublish(SECKILL_EXCHANGE,SECKILL_ROUTING_KEY, MessageProperties.PERSISTENT_TEXT_PLAIN,objectMapper.writeValueAsBytes(orderInfo));
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });


                while (true){
                    try {
                        OrderInfo orderInfo = orderInfos.take();
                        long deliveryTag = channel.getNextPublishSeqNo();
                        channel.basicPublish(SECKILL_EXCHANGE,SECKILL_ROUTING_KEY, MessageProperties.PERSISTENT_TEXT_PLAIN,objectMapper.writeValueAsBytes(orderInfo));
                        unconfirmOrderMap.put(deliveryTag,orderInfo);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }


            }
        };


        Thread thread = new Thread(runnable);
        thread.start();

    }

    public long postVoucherId(Long voucherId) throws InterruptedException {
        long orderId = redisIDGenerater.getID("order");
        OrderInfo orderInfo = new OrderInfo(voucherId,UserHolder.getUser().getId(), orderId);
        orderInfos.put(orderInfo);
        return orderId;
    }

}
