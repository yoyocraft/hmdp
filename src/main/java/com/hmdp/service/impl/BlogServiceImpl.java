package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.SystemConstants;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.model.dto.Result;
import com.hmdp.model.dto.ScrollResult;
import com.hmdp.model.dto.UserDTO;
import com.hmdp.model.entity.Blog;
import com.hmdp.model.entity.Follow;
import com.hmdp.model.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author codejuzi
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.queryBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(String id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1、获取当前登录用户
        UserDTO loginUser = UserHolder.getUser();
        if (loginUser == null) {
            return Result.fail("未登录");
        }
        // 2、判断当前用户是否点赞过
        Long userId = loginUser.getId();
        String userLikedKey = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(userLikedKey, userId.toString());
        if (score != null) {
            // 3、点赞过，取消点赞，数据库 -1
            boolean isSuccess = this.update()
                    .setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                // ZSet集合移除
                stringRedisTemplate.opsForZSet().remove(userLikedKey, userId.toString());
            }
        } else {
            // 4、未点赞过，点赞，数据库 + 1
            boolean isSuccess = this.update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // ZSet集合添加，score为当前时间戳
                stringRedisTemplate.opsForZSet().add(userLikedKey, userId.toString(), System.currentTimeMillis());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(String id) {
        String userLikedKey = RedisConstants.BLOG_LIKED_KEY + id;
        // 查询top5的点赞用户
        Set<String> likedTopFive = stringRedisTemplate.opsForZSet().range(userLikedKey, 0, 4);
        if(likedTopFive == null) {
            return Result.ok(Collections.emptyList());
        }
        // 解析出其中的用户id
        List<Long> userIdList = likedTopFive.stream().map(Long::valueOf).collect(Collectors.toList());
        if(CollUtil.isEmpty(userIdList)) {
            return Result.ok(Collections.emptyList());
        }
        String userIdString = CharSequenceUtil.join(",", userIdList);
        // 根据id查询用户，按照查询出来的id顺序，并对用户脱敏
        List<UserDTO> userList = userService.query()
                .in("id", userIdList)
                .last("order by field(id, " + userIdString + ")")
                .list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取当前登录用户id
        Long loginUserId = UserHolder.getUser().getId();
        // 保存探店博文
        blog.setUserId(loginUserId);
        boolean isSuccess = this.save(blog);
        if(!isSuccess) {
            return Result.fail("新增失败");
        }
        // 查询作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", loginUserId).list();
        if(CollUtil.isEmpty(follows)) {
            return Result.ok(blog.getId());
        }
        // 推送博文给粉丝

        for (Follow follow : follows) {
            Long followId = follow.getId();
            stringRedisTemplate.opsForZSet()
                    .add(RedisConstants.FEED_KEY_PREFIX + followId,
                            blog.getId().toString(),
                            System.currentTimeMillis());
        }
        // 返回博文id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前登录用户id
        Long loginUserId = UserHolder.getUser().getId();
        // 查询收件箱（每次读3条）
        String feedKey = RedisConstants.FEED_KEY_PREFIX + loginUserId;
        Set<ZSetOperations.TypedTuple<String>> blogTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(feedKey, 0, max, offset, 3);
        if(blogTuples == null) {
            return Result.ok(Collections.emptyList());
        }

        int computedOffset = 1;
        long minTime = 0;
        List<Long> followBlogIdList = new ArrayList<>(blogTuples.size());
        for (ZSetOperations.TypedTuple<String> blogTuple : blogTuples) {
            // 添加博文id
            followBlogIdList.add(Long.valueOf(Objects.requireNonNull(blogTuple.getValue())));
            // 获取时间戳（分数）
            long timestamp = Optional.ofNullable(blogTuple.getScore()).orElse(Double.MAX_VALUE).longValue();
            if(timestamp == minTime) {
                computedOffset++;
            } else {
                minTime = timestamp;
                computedOffset = 1;
            }
        }
        String followBlogIdStr = CharSequenceUtil.join(",", followBlogIdList);
        // 根据id查询博文按照 输入id的顺序
        List<Blog> blogList
                = this.query().
                in("id", followBlogIdList)
                .last("order by field(id, " + followBlogIdStr + ")")
                .list();
        // 封装结果
        ScrollResult blogScrollResult = new ScrollResult();
        blogScrollResult.setList(blogList);
        blogScrollResult.setMinTime(minTime);
        blogScrollResult.setOffset(computedOffset);
        return Result.ok(blogScrollResult);
    }


    /**
     * 查询笔记对应的用户
     *
     * @param blog blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 查询笔记是否被当前用户喜欢
     *
     * @param blog blog
     */
    private void queryBlogLiked(Blog blog) {
        UserDTO loginUser = UserHolder.getUser();
        if(loginUser == null) {
            return;
        }
        Long userId = loginUser.getId();
        String userLikedKey = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(userLikedKey, userId.toString());
        blog.setIsLike(score != null);
    }


}
