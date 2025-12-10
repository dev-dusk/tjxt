package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService learningLessonService;


    /**
     * 获取课程下单信息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO orderBasicDTO) {
        if (orderBasicDTO == null || orderBasicDTO.getUserId() == null || CollUtils.isEmpty(orderBasicDTO.getCourseIds())) {
            log.error("订单支付，异常消息，信息未空");
            return;
        }
        log.debug("处理订单支付消息：{}", orderBasicDTO);
        learningLessonService.addUserLessons(orderBasicDTO.getUserId(), orderBasicDTO.getCourseIds());
    }


}
