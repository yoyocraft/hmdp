package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.SystemConstants;
import com.hmdp.model.dto.Result;
import com.hmdp.model.dto.UserDTO;
import com.hmdp.model.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.model.entity.User;
import com.hmdp.service.IBlogService;
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


    /**
     * 查询笔记对应的用户
     *
     * @param blog
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
     * @param blog
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
