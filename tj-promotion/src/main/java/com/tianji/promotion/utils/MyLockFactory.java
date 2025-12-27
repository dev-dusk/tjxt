package com.tianji.promotion.utils;

import com.tianji.promotion.enums.MyLockType;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.function.Function;

@Component
public class MyLockFactory {

    private final EnumMap<MyLockType, Function<String, RLock>> lockMap = new EnumMap<>(MyLockType.class);

    public MyLockFactory(RedissonClient redissonClient) {
        // 可重入锁
        lockMap.put(MyLockType.RE_ENTRANT_LOCK, redissonClient::getLock);
        // 公平锁
        lockMap.put(MyLockType.FAIR_LOCK, redissonClient::getFairLock);
        // 读锁
        lockMap.put(MyLockType.READ_LOCK, key -> redissonClient.getReadWriteLock(key).readLock());
        // 写锁
        lockMap.put(MyLockType.WRITE_LOCK, key -> redissonClient.getReadWriteLock(key).writeLock());
    }


    public RLock getLock(MyLockType type, String key) {
        return lockMap.get(type).apply(key);
    }


}
