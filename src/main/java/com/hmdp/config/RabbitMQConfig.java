package com.hmdp.config;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

@Configuration
public class RabbitMQConfig implements Destroyable {

    private static Channel channel = null;
    public static String SECKILL_EXCHANGE = "seckillExchange";
    public static String SECKILL_QUEUE = "seckillQueue";
    public static String SECKILL_ROUTING_KEY = "seckill";
    private static final String IP_ADDRESS = "192.168.93.132";
    private static final int PORT = 5672;
    private Connection connection;

    @Bean
    public Connection connection() throws TimeoutException {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(IP_ADDRESS);
            factory.setPort(PORT);
            factory.setUsername("guest");
            factory.setPassword("guest");
            Connection connection = factory.newConnection();
            this.connection=connection;
            //TIP 该构造函数完成交换机、队列、极其绑定键的声明,在代码中只需要直接使用即可。
            channel = connection.createChannel();
            //TIP 声明一个持久化、非自动删除的交换机
            channel.exchangeDeclare(SECKILL_EXCHANGE,"direct",true,false,null);
            //TIP 声明一个持久化、非排他的、非自动删除的队列
            channel.queueDeclare(SECKILL_QUEUE,true,false,false,null);
            //TIP 绑定交换机与队列
            channel.queueBind(SECKILL_QUEUE,SECKILL_EXCHANGE,SECKILL_ROUTING_KEY);
            //TIP 一切就绪,在业务逻辑中可以直接使用channel来进行basicPublish了。
            channel.close();
            return connection;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() throws DestroyFailedException {
        Destroyable.super.destroy();
        try {
            //TIP 在容器关闭时,回收connection资源
            this.connection.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
