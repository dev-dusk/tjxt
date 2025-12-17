package com.tianji.learning.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.domain.po.PointsRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学习积分记录，每个月底清零 控制器
 * </p>
 *
 * @author wangchao
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/pointsRecord")
public class PointsRecordController {

    private final IPointsRecordService pointsRecordService;


}
