package com.sora.orderconsumer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sora.orderconsumer.mapper.VoucherOrderMapper;
import com.sora.orderconsumer.pojo.VoucherOrder;
import com.sora.orderconsumer.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

}
