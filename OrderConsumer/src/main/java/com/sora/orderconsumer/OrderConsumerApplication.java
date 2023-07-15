package com.sora.orderconsumer;

import com.sora.orderconsumer.service.OrderConsumerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import java.io.IOException;

@SpringBootApplication
public class OrderConsumerApplication {

    @Autowired
    OrderConsumerService orderConsumerService;

    public static void main(String[] args) {
        SpringApplication.run(OrderConsumerApplication.class, args);
    }

    @PostConstruct
    private void consumeSeckillOrder() throws IOException {
        orderConsumerService.consumeSeckillOrder();//TIP 相当于新开一个线程去监听SECKILL_QUEUE,并消费其中的订单
    }
}
