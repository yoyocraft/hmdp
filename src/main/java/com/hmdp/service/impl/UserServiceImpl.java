package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.HttpRequestConstants;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.UserConstants;
import com.hmdp.mapper.UserMapper;
import com.hmdp.model.dto.LoginFormDTO;
import com.hmdp.model.dto.Result;
import com.hmdp.model.dto.UserDTO;
import com.hmdp.model.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author codejuzi
 */
@Slf4j
@Service
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
        User user = null;
        // 校验
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 根据手机号加锁
        synchronized (phone.intern()) {
            // 优先密码登录
            if (StringUtils.isNotBlank(password)) {
                user = loginWithPassword(phone, password);
            } else if (StringUtils.isNotBlank(code)) {
                user = loginWithCode(phone, code);
            }
        }

        // 登录失败
        if (user == null) {
            return Result.fail("信息输入有误");
        }

        // 保存用户信息到redis中
        // 1、生成token和key
        String token = UUID.randomUUID().toString(true);
        String userRedisKey = RedisConstants.LOGIN_USER_KEY + token;
        // 2、将User对象转换成HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 将每个属性转换成String类型存储
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 3、存储信息
        stringRedisTemplate.opsForHash().putAll(userRedisKey, userMap);
        // 4、设置过期时间
        stringRedisTemplate.expire(userRedisKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 5、返回token
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        // 1、判断当前用户是否登录
        UserDTO userDTO = UserHolder.getUser();

        // 2、未登录返回错误信息
        if (userDTO == null) {
            return Result.fail("当前用户未登录");
        }

        // 3、登录，移除登录态，返回结果
        // 从请求头中获取token
        String token = request.getHeader(HttpRequestConstants.REQUEST_HEADER_AUTHORIZATION);
        // 判空
        if(StringUtils.isBlank(token)) {
            return Result.fail("用户未登录");
        }
        // 根据token从redis里获取用户信息
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        // 移除登录态
        stringRedisTemplate.delete(tokenKey);
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    public Result sign() {
        // 获取当前登录用户 id
        Long loginUserId = UserHolder.getUser().getId();
        // 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String userSignKey = RedisConstants.USER_SIGN_KEY + loginUserId + keySuffix;
        // 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(userSignKey, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 获取当前登录用户id
        Long loginUserId = UserHolder.getUser().getId();
        // 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String userSignKey = RedisConstants.USER_SIGN_KEY + loginUserId + keySuffix;
        // 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 统计从 0 到 dayOfMonth 的签到结果
        List<Long> result = stringRedisTemplate.opsForValue().bitField(userSignKey,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );
        if(result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num == 0) {
            return Result.ok(0);
        }
        int count = 0;
        while(true) {
            if((num & 1) == 0) {
                break;
            } else {
                count++;
            }
            num >>>= 1;
         }
        return Result.ok(count);
    }

    /**
     * 创建新用户
     *
     * @param phone 手机号
     * @return 新用户信息
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

    /**
     * 验证码登录
     *
     * @param phone 手机号
     * @param code  验证码
     * @return 用户信息
     */
    private User loginWithCode(String phone, String code) {
        // 从redis中读取code
        String codeRedisKey = RedisConstants.LOGIN_CODE_KEY + phone;
        String cacheCode = stringRedisTemplate.opsForValue().get(codeRedisKey);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return null;
        }
        // 查询数据库
        User user = query().eq("phone", phone).one();

        // 用户不存在，创建用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        return user;
    }

    /**
     * 密码登录
     *
     * @param phone    手机号
     * @param password 密码
     * @return 用户信息
     */
    private User loginWithPassword(String phone, String password) {
        // 获得加密后的密码
        String encryptedPassword = DigestUtils.md5DigestAsHex((UserConstants.USER_PASSWORD_SALT + password).getBytes());
        // 根据手机号查询数据库
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.eq("phone", phone);
        User user = this.getOne(userQueryWrapper);
        String userPassword = user.getPassword();
        // 密码未设置 || 密码不正确
        if (StringUtils.isBlank(userPassword) || !userPassword.equals(encryptedPassword)) {
            return null;
        }
        return user;
    }
}
