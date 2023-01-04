package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.UserConstants;
import com.hmdp.mapper.UserMapper;
import com.hmdp.model.dto.LoginFormDTO;
import com.hmdp.model.dto.Result;
import com.hmdp.model.dto.UserDTO;
import com.hmdp.model.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author codejuzi
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 手机号合法，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 保存验证码到redis中，设置两分钟有效
        String codeRedisKey = String.format("%s%s", RedisConstants.LOGIN_CODE_KEY, phone);
        stringRedisTemplate.opsForValue()
                .set(codeRedisKey, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送验证码,简单模拟下
        log.info("发送给 {} 的验证码为：{}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        String password = loginForm.getPassword();
        // 校验
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        if (StringUtils.isNotBlank(password)) {
            // TODO: 2023/1/4 密码登录实现
        }
        // TODO: 2023/1/4 提取出方法
        if (StringUtils.isBlank(code)) {
            return Result.fail("验证码为空");
        }
        // 从redis中读取code
        String codeRedisKey = RedisConstants.LOGIN_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(codeRedisKey);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 查询数据库
        User user = query().eq("phone", phone).one();

        // 用户不存在，创建用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 保存用户信息到redis中
        // 1、生成token和key
        String token = UUID.randomUUID().toString(true);
        String userRedisKey = RedisConstants.LOGIN_USER_KEY + token;
        // 2、将User对象转换成HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 将每个属性转换成String类型存储
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 3、存储信息
        stringRedisTemplate.opsForHash().putAll(userRedisKey, userMap);
        // 4、设置过期时间
        stringRedisTemplate.expire(userRedisKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 5、返回token
        return Result.ok(token);
    }

    /**
     * 创建新用户
     *
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        String userNickName = UserConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(phone, 10);
        user.setNickName(userNickName);
        // 保存用户
        this.save(user);
        return user;
    }
}
