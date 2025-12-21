package com.tianji.learning.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constans.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.tianji.learning.constans.RedisConstants.DATETIMEFORMATTER;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author wangchao
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate stringRedisTemplate;

    private final UserClient userClient;

    /**
     * 分页查询指定赛季的积分排行榜
     * @param query
     * @return
     */
    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        // 判断是否查询当前赛季
        Boolean isCurrentSeason = query.getSeason() == null || query.getSeason().equals(0L);
        // 查询排行榜
        List<PointsBoardItemVO> boardList = isCurrentSeason
                ? queryRedisBoardPage(query) : queryMysqlBoardPage(query);
        // 查询我的积分
        PointsBoard myPoint = isCurrentSeason
                ? queryRedisMyPoint() : queryMysqlMyPoint(query);

        return null;
    }

    @Override
    public void createDynamicTable(String tableName) {
        getBaseMapper().createDynamicTable(tableName);
    }


    /**
     * 查询当前赛季的积分排行榜(redis)
     * @param query
     */
    private List<PointsBoardItemVO> queryRedisBoardPage(PointsBoardQuery query) {
        // 计算分页属性
        List<PointsBoardItemVO> result = new ArrayList<>();
        int pageNo = query.getPageSize() * (query.getPageNo() - 1);
        String rankKey = RedisConstants.POINTS_RANK_KEY_PREFIX + LocalDate.now().format(DATETIMEFORMATTER);
        Set<ZSetOperations.TypedTuple<String>> rangeWithScores = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(rankKey, pageNo, pageNo + query.getPageSize() - 1);
        if (CollUtil.isEmpty(rangeWithScores)) {
            log.warn("当前赛季积分排行榜为空");
            return Collections.emptyList();
        }
        // 获取学生名字
        Set<Long> userIdSet = rangeWithScores.stream()
                .map(tuple -> Long.valueOf(Objects.requireNonNull(tuple.getValue())))
                .collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIdSet);
        Map<Long, String> userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        AtomicInteger atomicInteger = new AtomicInteger(pageNo + 1);
        rangeWithScores.forEach(tuple -> {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            // 学生姓名
            itemVO.setName(userMap.get(Long.valueOf(Objects.requireNonNull(tuple.getValue()))));
            // 积分值
            itemVO.setPoints(Objects.requireNonNull(tuple.getScore()).intValue());
            // 名次
            itemVO.setRank(atomicInteger.getAndAdd(1));
            result.add(itemVO);
        });
        return result;
    }



    /**
     * 查询我的赛季积分(redis)
     * @return
     */
    private PointsBoard queryRedisMyPoint() {
        String rankKey = RedisConstants.POINTS_RANK_KEY_PREFIX + LocalDate.now().format(DATETIMEFORMATTER);
        BoundZSetOperations<String, String> boundZSetOps = stringRedisTemplate.boundZSetOps(rankKey);
        String userId = UserContext.getUser().toString();
        Double score = boundZSetOps.score(userId);
        Long rank = boundZSetOps.reverseRank(userId);
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setUserId(Long.valueOf(userId));
        pointsBoard.setPoints(score != null ? score.intValue() : 0);
        pointsBoard.setRank(rank != null ? rank.intValue() : 0);
        return pointsBoard;
    }





    /**
     * 查询我的积分(mysql)
     * @param query
     * @return
     */
    private PointsBoard queryMysqlMyPoint(PointsBoardQuery query) {
        return null;
    }


    /**
     * 查询历史赛季的积分排行榜(mysql)
     * @param query
     */
    private List<PointsBoardItemVO> queryMysqlBoardPage(PointsBoardQuery query) {
        // TODO
        return null;
    }









}
