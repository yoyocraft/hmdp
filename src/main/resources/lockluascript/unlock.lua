-- 获取锁标识，判断是否与当前线程标识一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 一致，释放锁
    return redis.call('del', KEYS[1])
end
-- 不一致，直接返回
return 0