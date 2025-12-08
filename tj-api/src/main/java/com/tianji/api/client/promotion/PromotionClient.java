package com.tianji.api.client.promotion;

import com.tianji.api.client.promotion.fallback.PromotionClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 促销服务客户端
 * 如果不需要使用，可以保留空接口
 */
@FeignClient(value = "promotion-service", fallbackFactory = PromotionClientFallback.class)
public interface PromotionClient {
    // 如果不需要具体方法，可以保留空接口
}

