package com.tianji.learning.service.impl;

import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constans.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate stringRedisTemplate;

    private final RabbitTemplate rabbitTemplate;

    /**
     * 签到功能接口
     *
     * @return
     */
    @Override
    public SignResultVO addSignRecords() {
        SignResultVO result = new SignResultVO();
        // 获取时间
        LocalDate today = LocalDate.now();
        String userId = String.valueOf(UserContext.getUser());
        String yearMonth = today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + ":" + yearMonth;
        int offset = today.getDayOfMonth() - 1;
        // 当天签到
        Boolean setBit = stringRedisTemplate.opsForValue().setBit(key, offset, true);
        if (Boolean.FALSE.equals(setBit)) {
            log.warn("用户{}今天已经签到过了", userId);
            return result;
        }
        // 000000000000000000010000
        // 统计连续签到
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(offset)).valueAt(0);
        List<Long> bitFields = stringRedisTemplate.opsForValue().bitField(key, bitFieldSubCommands);
        if (bitFields == null || bitFields.isEmpty()) {
            log.warn("用户{}今天签到失败", userId);
            return result;
        }
        int signDays = bitFields.get(0).intValue();
        int recordCount = 0;
        // 循环与001与运算，获取最后一位
        while ((signDays & 1) == 1) {
            signDays >>>= 1;
            recordCount++;
        }
        // 计算连续签到积分
        int rewardPoints = 0;
        switch(recordCount) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        // 发送积分消息
        rabbitTemplate.convertAndSend(MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN, SignInMessage.of(Long.valueOf(userId), rewardPoints + 1));
        result.setSignDays(recordCount);
        result.setRewardPoints(rewardPoints);
        return result;
    }


}
