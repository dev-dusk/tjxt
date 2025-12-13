package com.tianji.learning.controller;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 控制器
 * </p>
 *
 * @author wangchao
 */
@Api(tags = "学习记录接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/learning-record")
public class LearningRecordController {

    private final ILearningRecordService learningRecordService;


    @GetMapping("/course/{courseId}")
    @ApiOperation("查询当前用户指定课程的学习进度")
    public LearningLessonDTO queryLearningRecordByCourse(@PathVariable("courseId") Long courseId) {
        return learningRecordService.queryLearningRecordByCourse(courseId);
    }



    @PostMapping
    @ApiOperation("提交学习记录")
    public void addLearningRecord(@RequestBody LearningRecordFormDTO formDTO){
        learningRecordService.addLearningRecord(formDTO);
    }


}
