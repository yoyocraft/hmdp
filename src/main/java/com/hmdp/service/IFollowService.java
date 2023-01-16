package com.hmdp.service;

import com.hmdp.model.dto.Result;
import com.hmdp.model.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author codejuzi
 * 
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注 && 取关
     *
     * @param followUserId 目标用户id
     * @param isFollow 是否已经关注
     * @return ok
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 判断当前用户是否关注目标用户
     *
     * @param followUserId 目标用户id
     * @return ok
     */
    Result isFollow(Long followUserId);

    /**
     * 查询共同关注
     *
     * @param id 目标用户id
     * @return List\<UserDTO\>
     */
    Result followCommons(Long id);
}
