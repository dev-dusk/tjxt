package com.tianji.learning.handler;

import com.tianji.learning.constans.LearningConstants;
import com.tianji.learning.constans.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.tianji.learning.constans.RedisConstants.DATETIMEFORMATTER;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate stringRedisTemplate;


    @Scheduled(cron = "*/5 * * * * ?")
    public void persistentPointsBoard() {
        log.info("持久化积分榜单");
        // 获取上个月日期
        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1L);
        // 查询赛季id
        PointsBoardSeason boardSeason = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, lastMonth)
                .ge(PointsBoardSeason::getEndTime, lastMonth)
                .one();
        if (boardSeason == null) {
            log.warn("赛季不存在，请检查数据");
            return;
        }
        String tableName = LearningConstants.DYNAMIC_TABLE_PREFIX + boardSeason.getId();
        TableInfoContext.setInfo(tableName);
        // 动态生成表
//        pointsBoardService.createDynamicTable(tableName);
        log.debug("动态生成表成功：{}", tableName);
        // 持久化榜单数据
        int shardIndex = XxlJobHelper.getShardIndex() != -1 ? XxlJobHelper.getShardIndex() : 0;
        int shardTotal = XxlJobHelper.getShardTotal() != -1 ? XxlJobHelper.getShardTotal() : 1;
        int pageSize = 100;
        String rankKey = RedisConstants.POINTS_RANK_KEY_PREFIX + LocalDate.now().format(DATETIMEFORMATTER);
        List<PointsBoard> batchList = new ArrayList<>();
        // 计算当前分片的起始位置：分片0从0开始，分片1从100开始，分片2从200开始...
        int startIndex = shardIndex * pageSize;
        int pageNo = 0;
        while (true) {
            // 当前页的起始索引 = 起始位置 + 页码 * 分片总数 * 每页大小
            int from = startIndex + pageNo * shardTotal * pageSize;
            int to = from + pageSize - 1;
            BoundZSetOperations<String, String> boundZSetOps =
                    stringRedisTemplate.boundZSetOps(rankKey);
            Set<ZSetOperations.TypedTuple<String>> typedTuples =
                    boundZSetOps.reverseRangeWithScores(from, to);
            if (typedTuples == null || typedTuples.isEmpty()) {
                break;
            }
            int rank = from + 1; // 排名 = 索引 + 1
            for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                PointsBoard pointsBoard = new PointsBoard();
                pointsBoard.setId((long) rank++); // 排名作为id
                pointsBoard.setUserId(Long.valueOf(tuple.getValue()));
                pointsBoard.setPoints(tuple.getScore().intValue());
                batchList.add(pointsBoard);
            }
            pageNo++;
        }
        pointsBoardService.saveBatch(batchList);
    }


}
