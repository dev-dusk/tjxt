package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IExchangeCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author wangchao
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService couponScopeService;

    private final IExchangeCodeService exchangeCodeService;

    /**
     * 新增优惠券接口
     * @param dto
     */
    @Override
    public void saveCoupon(CouponFormDTO dto) {
        Coupon coupon = BeanUtil.copyProperties(dto, Coupon.class);
        save(coupon);
        log.info("新增优惠券成功：{}", coupon);
        // 处理优惠劵使用范围
        if (!dto.getSpecific()) {
            log.debug("优惠券没有指定使用范围");
            return;
        }
        Set<CouponScope> couponScopes = dto.getScopes().stream()
                .map(scope -> {
                    CouponScope couponScope = new CouponScope();
                    couponScope.setType(2);
                    couponScope.setCouponId(coupon.getId());
                    couponScope.setBizId(scope);
                    return couponScope;
                })
                .collect(Collectors.toSet());
        couponScopeService.saveBatch(couponScopes);
    }


    /**
     * 分页查询优惠券接口
     * @param query
     * @return
     */
    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        Integer status = query.getStatus();
        String name = query.getName();
        Integer type = query.getType();
        // 1.分页查询
        Page<Coupon> page = lambdaQuery()
                .eq(type != null, Coupon::getDiscountType, type)
                .eq(status != null, Coupon::getStatus, status)
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        // 2.处理VO
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> list = BeanUtils.copyList(records, CouponPageVO.class);
        // 3.返回
        return PageDTO.of(page, list);
    }


    /**
     * 发放优惠券接口
     * @param dto
     */
    @Override
    public void beginIssue(CouponIssueFormDTO dto) {
        // 状态判断
        Coupon couponById = getById(dto.getId());
        if (couponById == null) {
            log.warn("优惠券不存在：{}", dto.getId());
            return;
        }
        if (couponById.getStatus() != CouponStatus.DRAFT
                && couponById.getStatus() != CouponStatus.PAUSE) {
            log.warn("优惠券状态异常：{}", couponById.getStatus());
            return;
        }
        BeanUtil.copyProperties(dto, couponById);
        // 发放方式判断
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        boolean isBegin = issueBeginTime == null || issueBeginTime.isBefore(LocalDateTime.now());
        if (isBegin) {
            couponById.setStatus(CouponStatus.ISSUING);
            couponById.setIssueBeginTime(LocalDateTime.now());
        } else {
            couponById.setStatus(CouponStatus.DRAFT);
        }
        updateById(couponById);
        // 优惠卷发送
        if (couponById.getObtainWay() == ObtainType.ISSUE
                && couponById.getStatus() == CouponStatus.DRAFT) {
            exchangeCodeService.asyncGenerateCode(couponById);
        }
    }




}
