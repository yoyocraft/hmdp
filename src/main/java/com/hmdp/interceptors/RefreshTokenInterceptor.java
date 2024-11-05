package com.hmdp.interceptors;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.constants.HttpRequestConstants;
import com.hmdp.constants.RedisConstants;
import com.hmdp.model.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Order(0)
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        // 从请求头中获取token
        String token = request.getHeader(HttpRequestConstants.REQUEST_HEADER_AUTHORIZATION);
        // 判空
        if (StringUtils.isBlank(token)) {
            return true;
        }
        // 根据token从redis里获取用户信息
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        // 判空
        if (userMap.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 转换对象
        UserDTO loginUser = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 保存到ThreadLocal
        UserHolder.saveUser(loginUser);
        // 刷新token过期时间
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }
}
