package com.tianji.learning.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author wangchao
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {


    private final UserClient userClient;

    private final InteractionReplyMapper interactionReplyMapper;

    private final SearchClient searchClient;

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final CategoryCache categoryCache;

    /**
     * 新增提问
     *
     * @param questionDTO
     */
    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        InteractionQuestion question = BeanUtil.copyProperties(questionDTO, InteractionQuestion.class);
        question.setUserId(UserContext.getUser());
        save(question);
    }


    /**
     * 分页查询互动问题
     *
     * @param query
     * @return
     */
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        List<QuestionVO> result = new ArrayList<>();
        Page<InteractionQuestion> questionPage = lambdaQuery()
                .select(InteractionQuestion.class, predicate -> !predicate.getProperty().equals("description"))
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getSectionId() != null,
                        InteractionQuestion::getSectionId, query.getSectionId())
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        if (questionPage.getTotal() <= 0) {
            return PageDTO.empty(questionPage);
        }
        List<InteractionQuestion> records = questionPage.getRecords();
        Map<Long, UserDTO> userMap = getUserMap(records);
        List<Long> latestAnswerIds = records.stream()
                .filter(question -> Boolean.FALSE.equals(question.getHidden())
                        && question.getLatestAnswerId() != null)
                .map(InteractionQuestion::getLatestAnswerId)
                .collect(Collectors.toList());
        List<InteractionReply> replyList = interactionReplyMapper.selectBatchIds(latestAnswerIds);
        Map<Long, InteractionReply> replyMap = replyList.stream()
                .collect(Collectors.toMap(InteractionReply::getId, Function.identity()));
        records.forEach(question -> {
            QuestionVO questionVO = new QuestionVO();
            questionVO.setId(question.getId());
            questionVO.setTitle(question.getTitle());
            questionVO.setAnswerTimes(question.getAnswerTimes());
            questionVO.setCreateTime(question.getCreateTime());
            Boolean anonymity = question.getAnonymity();
            questionVO.setAnonymity(anonymity);
            if (Boolean.FALSE.equals(anonymity)) {
                UserDTO userDTO = userMap.get(question.getUserId());
                if (userDTO != null) {
                    questionVO.setUserId(userDTO.getId());
                    questionVO.setUserName(userDTO.getName());
                    questionVO.setUserIcon(userDTO.getIcon());
                }
            }
            Long latestAnswerId = question.getLatestAnswerId();
            if (latestAnswerId != null) {
                InteractionReply reply = replyMap.get(latestAnswerId);
                if (reply != null) {
                    questionVO.setLatestReplyContent(reply.getContent());
                    questionVO.setLatestReplyUser(String.valueOf(reply.getUserId()));
                }
            }
            result.add(questionVO);
        });

        return PageDTO.of(questionPage, result);
    }

    /**
     * 获取用户信息
     *
     * @param records
     * @return
     */
    private Map<Long, UserDTO> getUserMap(List<InteractionQuestion> records) {
        List<Long> userIds = records.stream()
                .filter(question -> Boolean.FALSE.equals(question.getAnonymity()))
                .map(InteractionQuestion::getUserId)
                .collect(Collectors.toList());
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        return userDTOS.stream()
                .collect(Collectors.toMap(UserDTO::getId, Function.identity()));
    }


    /**
     * 管理端分页查询互动问题
     *
     * @param query
     * @return
     */
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        // 判断是否搜索课程名称
        List<Long> coursesIdByName = new ArrayList<>();
        if (StrUtil.isNotBlank(query.getCourseName())) {
            coursesIdByName = searchClient.queryCoursesIdByName(query.getCourseName());
        }
        if (CollUtil.isEmpty(coursesIdByName)) {
            return PageDTO.empty();
        }
        Page<InteractionQuestion> questionPage = lambdaQuery()
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .in(CollUtil.isNotEmpty(coursesIdByName), InteractionQuestion::getCourseId, coursesIdByName)
                .ge(query.getBeginTime() != null, InteractionQuestion::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, InteractionQuestion::getCreateTime, query.getEndTime())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        if (questionPage.getTotal() <= 0) {
            return PageDTO.empty(questionPage);
        }
        List<InteractionQuestion> records = questionPage.getRecords();
        Map<Long, UserDTO> userMap = getUserMap(records);
        List<Long> chapterIds = new ArrayList<>(records.size());
        List<Long> courseIds = new ArrayList<>(records.size());
        records.forEach(question -> {
            chapterIds.add(question.getChapterId());
            chapterIds.add(question.getSectionId());
            courseIds.add(question.getCourseId());
        });
        List<CourseSimpleInfoDTO> courseSimpleList = courseClient.getSimpleInfoList(courseIds);
        Map<Long, CourseSimpleInfoDTO> courseMap = courseSimpleList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, Function.identity()));
        List<CataSimpleInfoDTO> chapterSimpleList = catalogueClient.batchQueryCatalogue(chapterIds);
        Map<Long, String> chapterMap = chapterSimpleList.stream()
                .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        List<QuestionAdminVO> result = new ArrayList<>(records.size());
        records.forEach(question -> {
            QuestionAdminVO questionVO = new QuestionAdminVO();
            BeanUtil.copyProperties(question, questionVO);
            // 提问者信息
            if (Boolean.FALSE.equals(question.getAnonymity())) {
                UserDTO userDTO = userMap.get(question.getUserId());
                if (userDTO != null) {
                    questionVO.setUserName(userDTO.getName());
                    questionVO.setUserIcon(userDTO.getIcon());
                }
            }
            // 课程名称
            CourseSimpleInfoDTO courseSimpleInfoDTO = courseMap.get(question.getCourseId());
            if (courseSimpleInfoDTO != null) {
                questionVO.setCourseName(courseSimpleInfoDTO.getName());
            }
            // 章名称
            questionVO.setChapterName(chapterMap.getOrDefault(question.getChapterId(), ""));
            // 节名称
            questionVO.setSectionName(chapterMap.getOrDefault(question.getSectionId(), ""));
            // 三级分类名称
            questionVO.setCategoryName(categoryCache.getCategoryNames(courseSimpleInfoDTO.getCategoryIds()));
            result.add(questionVO);
        });
        return PageDTO.of(questionPage, result);
    }


}
