package com.tianji.learning.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.domain.po.PointsBoard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学霸天梯榜 控制器
 * </p>
 *
 * @author wangchao
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/pointsBoard")
public class PointsBoardController {

    private final IPointsBoardService pointsBoardService;


}
