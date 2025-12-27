package com.tianji.promotion.utils;

import com.tianji.promotion.enums.MyLockType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface MyLock {

    // 锁的key
    String key();

    // 等待时间
    long waitTime() default 1;

    // 过期时间
    long expireTime() default -1;

    // 时间当位
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    // 锁的类型
    MyLockType lockType() default MyLockType.RE_ENTRANT_LOCK;

    // 拒绝策略
    MyLockStrategy lockStrategy() default MyLockStrategy.SKIP_FAST;


}
