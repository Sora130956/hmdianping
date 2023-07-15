package com.hmdp.aspects;

import com.hmdp.dto.Result;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.time.LocalDateTime;

@Component
@Aspect
public class InsertAspects {
    @Pointcut(value = "@annotation(com.hmdp.utils.AutoInsert)")
    public void pointcut(){
    }

    @Around("pointcut()")
    public void insert(ProceedingJoinPoint joinPoint){
        Object[] args = joinPoint.getArgs();
        for(Object stuff : args){
            String name = stuff.getClass().getName();
            if(name.startsWith("com.hmdp.entity.")){
                insertTime(stuff);
            }
        }
        try {
            joinPoint.proceed();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void insertTime(Object object){
        try {
            Field updateTime = object.getClass().getDeclaredField("updateTime");
            updateTime.setAccessible(true);
            updateTime.set(object, LocalDateTime.now());
            Field createTime = object.getClass().getDeclaredField("createTime");
            createTime.setAccessible(true);
            createTime.set(object,LocalDateTime.now());
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
