package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

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


    @GetMapping("/{courseId}/valid")
    @ApiOperation("校验当前用户是否可以学习当前课程")
    Long isLessonValid(@PathVariable("courseId") Long courseId) {
        return learningLessonService.isLessonValid(courseId);
    }


    @GetMapping("/{courseId}")
    @ApiOperation("查询用户课表中指定课程状态")
    LearningLessonVO queryUserCourseStatus(@PathVariable("courseId") Long courseId) {
        return learningLessonService.queryUserCourseStatus(courseId);
    }


    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlans(@Valid @RequestBody LearningPlanDTO planDTO){
        learningLessonService.createLearningPlan(planDTO.getCourseId(), planDTO.getFreq());
    }


    @ApiOperation("查询我的学习计划")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        return learningLessonService.queryMyPlans(query);
    }

}
