package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学生课程表 控制器
 * </p>
 *
 * @author wangchao
 */
@Api(tags = "学生课程表接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/lessons")
public class LearningLessonController {

    private final ILearningLessonService learningLessonService;

    @GetMapping("/page")
    @ApiOperation("分页查询课程表")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        return learningLessonService.queryMyLessons(pageQuery);
    }


    @GetMapping("/now")
    @ApiOperation("查询最近学习的课程")
    public LearningLessonVO queryMyCurrentLesson() {
        return learningLessonService.queryMyCurrentLesson();
    }

}
