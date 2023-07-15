package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class RedisHotObject {

    private Object data;

    private LocalDateTime exprieTime;

}
