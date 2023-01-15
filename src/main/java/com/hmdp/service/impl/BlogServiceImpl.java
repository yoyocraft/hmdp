package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constants.SystemConstants;
import com.hmdp.model.dto.Result;
import com.hmdp.model.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.model.entity.User;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author codejuzi
 * 
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(String id) {
        Blog blog = this.getById(id);
        if(blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        return Result.ok(blog);
    }


    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


}
