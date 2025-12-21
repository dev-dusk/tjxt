package com.tianji.learning.constans;

import java.time.format.DateTimeFormatter;

public interface RedisConstants {

    /**
     * 签到记录的KEY前缀
     */
    String SIGN_RECORD_KEY_PREFIX = "sign:uid:";


    /**
     * 积分排行榜的KEY前缀
     */
    String POINTS_RANK_KEY_PREFIX = "points:rank:";


    DateTimeFormatter DATETIMEFORMATTER = DateTimeFormatter.ofPattern("yyyyMM");


}
