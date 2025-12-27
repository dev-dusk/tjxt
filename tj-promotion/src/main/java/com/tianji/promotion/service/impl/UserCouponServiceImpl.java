package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author wangchao
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    /**
     * 用户锁Map，为每个用户ID分配独立的锁
     */
    private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    /**
     * 获取用户锁（如果不存在则创建）
     */
    private ReentrantLock getUserLock(Long userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    }

    /**
     * 领取优惠券接口
     * @param couponId
     */
    @Override
    public void receiveCoupon(Long couponId) {
        // 优惠劵校验
        Coupon coupon = couponMapper.selectById(couponId);
        AssertCoupon(coupon);
        
        // 获取当前用户ID
        Long userId = UserContext.getUser();
        // 获取该用户的锁
        ReentrantLock lock = getUserLock(userId);
        lock.lock();
        try {
            UserCouponServiceImpl currentProxy = (UserCouponServiceImpl)AopContext.currentProxy();
            currentProxy.deUserCoupon(couponId, coupon, userId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void deUserCoupon(Long couponId, Coupon coupon, Long userId) {
        // 判断是否超出限领数量（加锁后再次检查）
        Integer counted = lambdaQuery()
                .eq(UserCoupon::getCouponId, coupon.getId())
                .eq(UserCoupon::getUserId, userId)
                .count();
        if (counted != null && counted >= coupon.getUserLimit()) {
            throw new BizIllegalException("超出限领数量");
        }
        // 优惠劵已发放数量+1
        int issueNum = getBaseMapper().updateIssueNum(coupon);
        if (issueNum == 0) {
            throw new BizIllegalException("优惠劵库存不足");
        }
        // 生成用户劵
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setTermBeginTime(coupon.getTermBeginTime());
        userCoupon.setTermEndTime(coupon.getTermEndTime());
        userCoupon.setCouponId(couponId);
        userCoupon.setUserId(userId);
        userCoupon.setCreateTime(LocalDateTime.now());
        userCoupon.setUpdateTime(LocalDateTime.now());
        save(userCoupon);
    }


    /**
     * 优惠劵领取校验
     * @param coupon
     */
    private void AssertCoupon(Coupon coupon) {
        // 是否存在
        if (coupon == null) {
            throw new BizIllegalException("优惠劵不存在");
        }
        // 是否正在发放
        if (coupon.getStatus() !=  CouponStatus.ISSUING) {
             throw new BizIllegalException("优惠劵未在发放中");
        }
        // 判断库存是否充足
        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
             throw new BizIllegalException("优惠劵已发放完毕");
        }
    }


    @Override
    public void exchangeCoupon(String code) {

    }



}
