package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSearchDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author wangchao
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final LearningRecordMapper learningRecordMapper;

    private final ILearningLessonService learningLessonService;

    private final CourseClient courseClient;

    /**
     * 查询当前用户指定课程的学习进度
     *
     * @param courseId
     * @return
     */
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 查询课程信息
        Long userId = UserContext.getUser();
        LearningLesson learningLesson = learningLessonService.queryByUserAndCourseId(userId, courseId);
        if (learningLesson == null) {
            log.error("用户{}查询课程{}的进度信息失败，课程不存在", userId, courseId);
            return null;
        }
        LearningLessonDTO result = new LearningLessonDTO();
        result.setId(learningLesson.getId());
        result.setLatestSectionId(learningLesson.getLatestSectionId());
        List<LearningRecord> learningRecords = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, learningLesson.getId())
                .eq(LearningRecord::getUserId, userId)
                .list();
        List<LearningRecordDTO> learningRecordDTOS = BeanUtil.copyToList(learningRecords, LearningRecordDTO.class);
        result.setRecords(learningRecordDTOS);
        return result;
    }


    /**
     * 提交学习记录
     *
     * @param formDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addLearningRecord(LearningRecordFormDTO formDTO) {
        SectionType sectionType = formDTO.getSectionType();
        // 如果是考试
        if (sectionType == SectionType.EXAM) {
            processLessonAndRecord(formDTO, true);
            return;
        }

        // 否则是视频
        LearningRecord learningRecord = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, formDTO.getLessonId())
                .eq(LearningRecord::getUserId, UserContext.getUser())
                .eq(LearningRecord::getSectionId, formDTO.getSectionId())
                .one();
        // 新增学习记录
        if (learningRecord == null) {
            processLessonAndRecord(formDTO, false);
            return;
        }
        // 更新学习记录（判断视频是否是第一次完成）
        if (learningRecord.getFinished()) {
            // 视频重复播放
            log.error("用户{}提交记录视频已学完", UserContext.getUser());
            processLessons(formDTO, false);
            learningRecord.setUpdateTime(formDTO.getCommitTime());
            learningRecord.setMoment(formDTO.getMoment());
            this.updateById(learningRecord);
            return;
        }
        // 第一次学习，判断小节是否达到完成
        Boolean isFinished = formDTO.getMoment() >= formDTO.getDuration() / 2 && !learningRecord.getFinished();
        if (isFinished) {
            learningRecord.setFinished(true);
            learningRecord.setMoment(formDTO.getMoment());
            learningRecord.setFinishTime(formDTO.getCommitTime());
            learningRecord.setUpdateTime(formDTO.getCommitTime());
            this.updateById(learningRecord);
        } else {
            // 未完成，更新学习记录
            learningRecord.setUpdateTime(formDTO.getCommitTime());
            learningRecord.setMoment(formDTO.getMoment());
            this.updateById(learningRecord);
        }
        processLessons(formDTO, isFinished);
    }


    /**
     * 查询当前用户的学习记录
     * @return
     */
    @Override
    public List<LearningRecord> queryLearningRecordList(List<Long> LessonIds) {
        // 计算本周时间范围
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.with(DayOfWeek.MONDAY).with(LocalTime.MIN);
        LocalDateTime end = now.with(DayOfWeek.SUNDAY).with(LocalTime.MAX);
        return lambdaQuery()
                .eq(LearningRecord::getUserId, UserContext.getUser())
                .in(LearningRecord::getLessonId, LessonIds)
                .gt(LearningRecord::getFinishTime, start)
                .lt(LearningRecord::getFinishTime, end)
                .list();
    }

    /**
     * 处理课程和学习记录的模版方法
     *
     * @param formDTO
     * @param isFinished
     */
    private void processLessonAndRecord(LearningRecordFormDTO formDTO, boolean isFinished) {
        // 新增学习记录
        saveLearningRecord(formDTO, isFinished);
        // 处理课表学习小节数和状态
        processLessons(formDTO, isFinished);
    }


    /**
     * 新增学习记录
     *
     * @param formDTO
     */
    private void saveLearningRecord(LearningRecordFormDTO formDTO, Boolean isFinished) {
        LearningRecord learningRecord = new LearningRecord();
        learningRecord.setLessonId(formDTO.getLessonId());
        learningRecord.setSectionId(formDTO.getSectionId());
        learningRecord.setUserId(UserContext.getUser());
        learningRecord.setMoment(formDTO.getMoment());
        learningRecord.setFinished(isFinished);
        learningRecord.setCreateTime(formDTO.getCommitTime());
        learningRecord.setUpdateTime(formDTO.getCommitTime());
        if (isFinished) {
            learningRecord.setFinishTime(formDTO.getCommitTime());
        }
        this.save(learningRecord);
    }

    /**
     * 处理课表学习小节数和状态
     *
     * @param formDTO
     */
    private void processLessons(LearningRecordFormDTO formDTO, Boolean isFinished) {
        LearningLesson learningLesson = learningLessonService.getById(formDTO.getLessonId());
        if (learningLesson == null) {
            log.error("用户{}提交记录失败，课程不存在", UserContext.getUser());
            return;
        }
        if (learningLesson.getStatus() == LessonStatus.FINISHED
                || learningLesson.getStatus() == LessonStatus.EXPIRED) {
            log.error("用户{}提交记录失败，课程已结束", UserContext.getUser());
            return;
        }
        if (isFinished) {
            log.debug("{}小节已学完", formDTO.getSectionId());
            learningLesson.setLearnedSections(learningLesson.getLearnedSections() + 1);
            // 判断课程是否完成
            CourseSearchDTO courseSearchDTO = courseClient.getSearchInfo(learningLesson.getCourseId());
            if (Objects.equals(learningLesson.getLearnedSections(), courseSearchDTO.getSections())) {
                learningLesson.setStatus(LessonStatus.FINISHED);
            }
        }
        learningLesson.setLatestSectionId(formDTO.getSectionId());
        learningLesson.setLatestLearnTime(formDTO.getCommitTime());
        learningLesson.setUpdateTime(formDTO.getCommitTime());
        learningLessonService.updateById(learningLesson);
    }


}
