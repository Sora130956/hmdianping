package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZookeeperConfig {

    @Value("${zookeeper.connectString}")
    private String connectStr;

    @Value("${zookeeper.namespace}")
    private String namespaceStr;

    @Bean
    public CuratorFramework curatorFramework() {
        //TIP 建立zookeeper的客户端bean,并启动。
        String connectString = connectStr;
        String namespace = namespaceStr;

        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .namespace(namespace)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        curatorFramework.start();

        return curatorFramework;
    }

}