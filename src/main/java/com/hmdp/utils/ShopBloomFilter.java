package com.hmdp.utils;


import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.List;

@Component
@Slf4j
public class ShopBloomFilter {
    @Autowired
    private IShopService shopService;
    static boolean hasBeenInit = false;
    private static BloomFilter<Long> shopBloomFilter = BloomFilter.create(Funnels.longFunnel(),100000,0.01);

    public boolean init(){
        //TIP 查询出所有商铺信息,初始化布隆过滤器
        List<Shop> list = shopService.list();
        for(Shop shop : list){
            shopBloomFilter.put(shop.getId());
        }
        log.info("布隆过滤器初始化完毕！");
        hasBeenInit=true;
        return true;
    }
    public boolean exists(long id){
        if(!hasBeenInit){
            //TIP 布隆过滤器的初始化时间改为在项目启动时,当还没有初始化完成时,这里都返回true
            return true;
        }
        return shopBloomFilter.mightContain(id);
    }

    public boolean add(long id){
        //TIP 更新布隆过滤器
        shopBloomFilter.put(id);
        return true;
    }
}
