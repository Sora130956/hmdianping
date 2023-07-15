package com.hmdp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    ObjectMapper objectMapper;

    @Override
    public Result getTypeList() {
        String key = "cache:typelist:";
        String typeListStr = stringRedisTemplate.opsForValue().get(key);
        try {
            if(typeListStr!=null){
                List listFromCache = objectMapper.readValue(typeListStr, List.class);
                log.info("redis cache shop type list:"+listFromCache);
                return Result.ok(listFromCache);
            }else {
                List<ShopType> shopTypeList = this.list();
                log.info("miss cache:"+shopTypeList);
                stringRedisTemplate.opsForValue().set(key,objectMapper.writeValueAsString(shopTypeList));
                return Result.ok(shopTypeList);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
