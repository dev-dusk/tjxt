package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author wangchao
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;

    /**
     * 批量新增用户课程
     *
     * @param userId
     * @param courseIds
     */
    @Override
    public void addUserLessons(Long userId, List<Long> courseIds) {
        List<CourseSimpleInfoDTO> courseSimpleInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseSimpleInfoList)) {
            log.warn("课程不存在，课程id列表：{}", courseIds);
            return;
        }
        List<LearningLesson> lessonList = new ArrayList<>();
        courseSimpleInfoList.forEach(courseSimpleInfo -> {
            LocalDateTime now = LocalDateTime.now();
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(userId);
            lesson.setCourseId(courseSimpleInfo.getId());
            lesson.setStatus(LessonStatus.NOT_BEGIN);
            lesson.setPlanStatus(PlanStatus.NO_PLAN);
            lesson.setCreateTime(now);
            lesson.setUpdateTime(now);
            // 计算过期时间
            Integer validDuration = courseSimpleInfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime expireTime = now.plusMonths(validDuration);
                lesson.setExpireTime(expireTime);
            }
            lessonList.add(lesson);
        });
        saveBatch(lessonList);
    }


    /**
     * 分页查询课程表
     *
     * @param pageQuery
     * @return
     */
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        Page<LearningLesson> lessonPage = this.lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser())
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> lessonList = lessonPage.getRecords();
        if (CollUtils.isEmpty(lessonList)) {
            return PageDTO.empty();
        }
        List<Long> courseIds = lessonList.stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        Map<Long, CourseSimpleInfoDTO> courseMap = getCourseMap(courseIds);
        List<LearningLessonVO> lessonResult = new ArrayList<>();
        lessonList.forEach(lesson -> {
            LearningLessonVO lessonVO = new LearningLessonVO();
            BeanUtil.copyProperties(lesson, lessonVO);
            // 课程名称，封面，章节数
            lessonVO.setCourseName(courseMap.get(lesson.getCourseId()).getName());
            lessonVO.setCourseCoverUrl(courseMap.get(lesson.getCourseId()).getCoverUrl());
            lessonVO.setSections(courseMap.get(lesson.getCourseId()).getSectionNum());
            // 总已报名课程数
            lessonVO.setCourseAmount((int) lessonPage.getTotal());
            lessonResult.add(lessonVO);
        });
        return PageDTO.of(lessonPage, lessonResult);
    }

    private Map<Long, CourseSimpleInfoDTO> getCourseMap(List<Long> courseIds) {
        List<CourseSimpleInfoDTO> courseSimpleInfoList = courseClient.getSimpleInfoList(courseIds);
        return courseSimpleInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, Function.identity()));
    }


    /**
     * 查询最近学习的课程
     *
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public LearningLessonVO queryMyCurrentLesson() {
        LearningLesson learnLesson = lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser())
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (learnLesson == null) {
            log.warn("用户没有正在学习的课程");
            return null;
        }
        Long courseId = learnLesson.getCourseId();
        CourseFullInfoDTO courseInfoById = courseClient.getCourseInfoById(courseId, false, false);
        if (courseInfoById == null) {
            log.warn("课程不存在，课程id：{}", courseId);
            return null;
        }
        LearningLessonVO lessonVO = new LearningLessonVO();
        BeanUtil.copyProperties(learnLesson, lessonVO);
        // 课程名称，封面，章节数
        lessonVO.setCourseName(courseInfoById.getName());
        lessonVO.setCourseCoverUrl(courseInfoById.getCoverUrl());
        lessonVO.setSections(courseInfoById.getSectionNum());
        // 总的小结数
        lessonVO.setSections(courseInfoById.getSectionNum());
        // 最近学习的小节名和编号
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS =
                catalogueClient.batchQueryCatalogue(Collections.singletonList(learnLesson.getLatestSectionId()));
        if (CollUtils.isNotEmpty(cataSimpleInfoDTOS)) {
            CataSimpleInfoDTO cataInfo = cataSimpleInfoDTOS.get(0);
            lessonVO.setLatestSectionName(cataInfo.getName());
            lessonVO.setLatestSectionIndex(cataInfo.getCIndex());
        }
        return lessonVO;
    }


}
