package com.tianji.promotion.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
public class CouponExecutorConfig {


    /**
     * CPU密集型任务
     * @return
     */
    @Bean
    public ThreadPoolTaskExecutor couponExecutor(){
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        // 核心线程数
        threadPoolTaskExecutor.setCorePoolSize(availableProcessors + 1);
        // 最大线程数
        threadPoolTaskExecutor.setMaxPoolSize(availableProcessors * 2);
        // 队列大小
        threadPoolTaskExecutor.setQueueCapacity(500);
        // 线程空闲存活时间
        threadPoolTaskExecutor.setKeepAliveSeconds(60);
        // 线程前缀名称
        threadPoolTaskExecutor.setThreadNamePrefix("coupon-executor-");
        // 拒绝策略,由调用者执行
        threadPoolTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有线程执行完毕再关闭线程池
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        threadPoolTaskExecutor.setAwaitTerminationSeconds(30);
        // 初始化
        threadPoolTaskExecutor.initialize();
        log.info("初始化优惠券执行器成功");
        return threadPoolTaskExecutor;
    }



}
