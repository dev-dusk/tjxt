package com.tianji.remark.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.base.Objects;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author wangchao
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {


    private final RabbitTemplate rabbitTemplate;

    private final StringRedisTemplate stringRedisTemplate;



    /**
     * 点赞或取消点赞
     *
     * @param recordDTO
     */
    @Override
    public void likedRecordService(LikeRecordFormDTO recordDTO) {
        Boolean liked = recordDTO.getLiked() ? addLikeRedis(recordDTO) : cancelLikeRedis(recordDTO);
        if (!liked) {
            log.warn("用户{}取消点赞失败", UserContext.getUser());
            return;
        }
        log.info("用户{}点赞成功", UserContext.getUser());
        // 统计点赞数,使用ZSet
        String bizKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordDTO.getBizId();
        Long likeCount = stringRedisTemplate.opsForSet().size(bizKey);
        if (likeCount == null) {
            log.warn("点赞数统计异常，请检查Redis数据");
            return;
        }
        String countKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + recordDTO.getBizId();
        stringRedisTemplate.opsForZSet().add(countKey, String.valueOf(recordDTO.getBizId()), likeCount);
    }


    /**
     * 取消点赞
     *
     * @param recordDTO
     * @return
     */
    private Boolean cancelLikeRedis(LikeRecordFormDTO recordDTO) {
        String bizKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordDTO.getBizId();
        Long remove = stringRedisTemplate.opsForSet().remove(bizKey, String.valueOf(UserContext.getUser()));
        return Objects.equal(remove, 1L);
    }


    /**
     * 点赞
     *
     * @param recordDTO
     * @return
     */
    private Boolean addLikeRedis(LikeRecordFormDTO recordDTO) {
        // 向Set记录里面添加点赞
        String bizKey = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordDTO.getBizId();
        Long added = stringRedisTemplate.opsForSet().add(bizKey, String.valueOf(UserContext.getUser()));
        return Objects.equal(added, 1L);
    }








    /**
     * 取消点赞
     *
     * @param recordDTO
     * @return
     */
    private Boolean cancelLikeRecord(LikeRecordFormDTO recordDTO) {
        return lambdaUpdate()
                .eq(LikedRecord::getBizId, recordDTO.getBizId())
                .eq(LikedRecord::getUserId, UserContext.getUser())
                .remove();
    }


    /**
     * 点赞
     *
     * @param recordDTO
     * @return
     */
    private Boolean addLikeRecord(LikeRecordFormDTO recordDTO) {
        LikedRecord record = lambdaQuery()
                .eq(LikedRecord::getBizId, recordDTO.getBizId())
                .eq(LikedRecord::getUserId, UserContext.getUser())
                .one();
        if (record != null) {
            log.warn("用户{}已经点过赞了，不能重复点赞", UserContext.getUser());
            return false;
        }
        LikedRecord likedRecord = BeanUtil.copyProperties(recordDTO, LikedRecord.class);
        likedRecord.setUserId(UserContext.getUser());
        likedRecord.setCreateTime(LocalDateTime.now());
        likedRecord.setUpdateTime(LocalDateTime.now());
        return save(likedRecord);
    }


//    /**
//     * 查询指定业务id的点赞状态
//     *
//     * @param bizIds
//     * @return
//     */
//    @Override
//    public Set<Long> isBizLiked(List<Long> bizIds) {
//        return lambdaQuery()
//                .select(LikedRecord::getBizId)
//                .eq(LikedRecord::getUserId, UserContext.getUser())
//                .in(LikedRecord::getBizId, bizIds)
//                .list()
//                .stream()
//                .map(LikedRecord::getBizId)
//                .collect(Collectors.toSet());
//    }

    /**
     * 查询指定业务id的点赞状态
     *
     * @param bizIds
     * @return
     */
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        String userId = String.valueOf(UserContext.getUser());
        List<Object> pipelined = stringRedisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                StringRedisConnection src = (StringRedisConnection) connection;
                for (Long bizId : bizIds) {
                    // pipeline会自动收集结果，并返回给executePipelined
                    src.sIsMember(RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId, userId);
                }
                return null;
            }
        });

        return IntStream.range(0, bizIds.size())
                .filter(i -> (boolean) pipelined.get(i))
                .mapToObj(bizIds::get)
                .collect(Collectors.toSet());
    }


}
