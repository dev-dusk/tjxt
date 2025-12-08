package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.promotion.PromotionClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * 促销服务降级处理
 */
@Slf4j
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {

    @Override
    public PromotionClient create(Throwable cause) {
        log.error("查询促销服务异常", cause);
        return new PromotionClient() {
            // 如果 PromotionClient 有方法，在这里实现降级逻辑
        };
    }
}

