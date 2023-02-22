package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.constants.RedisConstants;
import com.hmdp.model.dto.Result;
import com.hmdp.model.dto.UserDTO;
import com.hmdp.model.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author codejuzi
 * 
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;


    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 获取当前登录用户id
        Long loginUserId = UserHolder.getUser().getId();

        String followsKey = RedisConstants.FOLLOWS_KEY_PREFIX + loginUserId;

        // 判断是否关注目标用户
        if(Boolean.TRUE.equals(isFollow)) {
            // 关注 新增
            Follow follow = new Follow();
            follow.setUserId(loginUserId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = this.save(follow);
            if(isSuccess) {
                // 保存到redis中
                stringRedisTemplate.opsForSet().add(followsKey, followUserId.toString());
            }
        } else {
            // 取关 删除
            QueryWrapper<Follow> followQueryWrapper = new QueryWrapper<>();
            followQueryWrapper.eq("follow_user_id", followUserId)
                    .eq("user_id", loginUserId);
            boolean isSuccess = this.remove(followQueryWrapper);
            if(isSuccess) {
                // 从redis中删除
                stringRedisTemplate.opsForSet().remove(followsKey, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 获取当前登录用户id
        Long loginUserId = UserHolder.getUser().getId();
        // 判断是否关注
        QueryWrapper<Follow> followQueryWrapper = new QueryWrapper<>();
        followQueryWrapper.eq("follow_user_id", followUserId)
                .eq("user_id", loginUserId);
        int count = this.count(followQueryWrapper);
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        // 获取当前登录用户id
        Long loginUserId = UserHolder.getUser().getId();
        String loginUserFollowsKey = RedisConstants.FOLLOWS_KEY_PREFIX + loginUserId;
        String targetUserFollowsKey = RedisConstants.FOLLOWS_KEY_PREFIX + id;

        // 取两个集合的交集
        Set<String> commonFollowUserIdSet = stringRedisTemplate.opsForSet().intersect(loginUserFollowsKey, targetUserFollowsKey);
        if(commonFollowUserIdSet == null) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> commonFollowUserIdList = commonFollowUserIdSet.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> commonFollowUserList
                = userService.listByIds(commonFollowUserIdList)
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(commonFollowUserList);
    }
}
