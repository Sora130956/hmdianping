package com.sora.orderconsumer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sora.orderconsumer.pojo.SeckillVoucher;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface ISeckillVoucherService extends IService<SeckillVoucher> {
}
