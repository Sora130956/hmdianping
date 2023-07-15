package com.hmdp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderInfo {
    private Long voucherId;
    private Long userId;
    private Long orderId;
}
