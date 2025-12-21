package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.constans.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static com.tianji.learning.constans.RedisConstants.DATETIMEFORMATTER;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningPointsListener {

    private final IPointsRecordService pointsRecordService;

    private final StringRedisTemplate redisTemplate;



    /**
     * 监听签到积分更新消息
     */
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            value = @Queue(value = "sign.points.queue", durable = "true"),
            key = MqConstants.Key.SIGN_IN
    ))
    public void handlePointsChange(SignInMessage signInMessage) {
        try {
            log.info("用户签到{}积分已更新", signInMessage);
            if (signInMessage == null) {
                log.error("用户签到积分失败");
                return;
            }
            PointsRecord pointsRecord = new PointsRecord();
            pointsRecord.setType(PointsRecordType.SIGN);
            pointsRecord.setUserId(signInMessage.getUserId());
            pointsRecord.setPoints(signInMessage.getPoints());
            boolean save = pointsRecordService.save(pointsRecord);
            if (!save) {
                log.error("用户签到积分保存失败");
            }
            log.info("用户签到积分已保存");
            // 积分统计排行榜
            String rankKey = RedisConstants.POINTS_RANK_KEY_PREFIX + LocalDate.now().format(DATETIMEFORMATTER);
            redisTemplate.opsForZSet().incrementScore(rankKey, String.valueOf(signInMessage.getUserId()), signInMessage.getPoints());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }






}
