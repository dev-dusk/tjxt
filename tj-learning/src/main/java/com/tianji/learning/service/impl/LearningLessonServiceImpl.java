package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final LearningRecordMapper learningRecordMapper;

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


    /**
     * 校验当前用户是否可以学习当前课程
     * @param courseId
     * @return
     */
    @Override
    public Long isLessonValid(Long courseId) {
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser())
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
        return lesson.getId();
    }


    /**
     * 查询用户课表中指定课程状态
     * @param courseId
     * @return
     */
    @Override
    public LearningLessonVO queryUserCourseStatus(Long courseId) {
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser())
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
        return BeanUtil.toBean(lesson, LearningLessonVO.class);
    }


    /**
     * 根据用户id和课程id查询课程表
     * @param userId
     * @param courseId
     * @return
     */
    @Override
    public LearningLesson queryByUserAndCourseId(Long userId, Long courseId) {
        return this.lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .eq(LearningLesson::getUserId, userId)
                .one();
    }


    /**
     * 创建学习计划
     * @param courseId
     * @param freq
     */
    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        LearningLesson learningLesson = lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser())
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (learningLesson == null) {
            log.warn("用户没有该课程，用户id：{}，课程id：{}", UserContext.getUser(), courseId);
            return;
        }
        learningLesson.setWeekFreq(freq);
        learningLesson.setPlanStatus(PlanStatus.PLAN_RUNNING);
        updateById(learningLesson);
    }


    /**
     * 查询我的学习计划
     * @param query
     * @return
     */
    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        LearningPlanPageVO result = new LearningPlanPageVO();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.with(DayOfWeek.MONDAY).with(LocalTime.MIN);
        LocalDateTime end = now.with(DayOfWeek.SUNDAY).with(LocalTime.MAX);
        List<LearningLesson> lessonList = lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser()).list();
        if (CollUtils.isEmpty(lessonList)) {
            log.warn("用户没有课程，用户id：{}", UserContext.getUser());
            return null;
        }
        List<Long> lessonIds = lessonList.stream()
                .map(LearningLesson::getId).collect(Collectors.toList());
        List<LearningRecord> learningRecords = learningRecordMapper.queryLearningRecordList(lessonIds, start, end, UserContext.getUser());
        if (CollUtils.isEmpty(learningRecords)) {
            return new LearningPlanPageVO();
        }
        // 本周实际学习小节数
        result.setWeekFinished(learningRecords.size());
        // 本周计划学习小节数
        AtomicInteger currentWeekFreq = new AtomicInteger();
        lessonList.forEach(lesson -> {
            currentWeekFreq.addAndGet(lesson.getWeekFreq());
        });
        result.setWeekTotalPlan(currentWeekFreq.get());
        // TODO 本周学习积分
        result.setWeekPoints(100);

        // 分页查询课程记录表
        Page<LearningLesson> lessonPage = lambdaQuery()
                .eq(LearningLesson::getUserId, UserContext.getUser())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Long> courseIds = lessonPage.getRecords().stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        List<IdAndNumDTO> lessonIdAndNum = learningRecordMapper.queryLearningRecordCount(UserContext.getUser(), start, end);
        Map<Long, CourseSimpleInfoDTO> courseMap = getCourseMap(courseIds);
        Map<Long, Integer> countMap = IdAndNumDTO.toMap(lessonIdAndNum);
        List<LearningPlanVO> planVOList = lessonPage.getRecords().stream().map(lesson -> {
            LearningPlanVO planVO = new LearningPlanVO();
            planVO.setId(lesson.getId());
            planVO.setCourseId(lesson.getCourseId());
            // 课程名称
            planVO.setCourseName(courseMap.get(lesson.getCourseId()).getName());
            // 每周计划学习章节数
            planVO.setWeekFreq(lesson.getWeekFreq());
            // 本周已学习章节数
            planVO.setWeekLearnedSections(countMap.get(lesson.getId()));
            // 总已学习章节数
            planVO.setLearnedSections(lesson.getLearnedSections());
            // 课程总数
            planVO.setSections(courseMap.get(lesson.getCourseId()).getSectionNum());
            // 最近一次学习时间
            planVO.setLatestLearnTime(lesson.getLatestLearnTime());
            return planVO;
        }).collect(Collectors.toList());
        result.setTotal(lessonPage.getTotal());
        result.setPages(lessonPage.getPages());
        result.setList(planVOList);
        return result;

    }





}
