# 谋马点评(本项目来自b站[黑马程序员](https://space.bilibili.com/37974444)[redis教程](https://www.bilibili.com/video/BV1cr4y1671t)，仅供参考)
> 本项目 基于MacOS系统开发，使用前保证项目中用的路径是可用的

项目代码包含2个分支：
- master : 主分支，包含完整版后端代码 && 修改之后的前端代码
- init (包含前端资源): 初始化分支，实战篇的初始代码，建议以这个分支作为自己开发的基础代码
  - 前端资源位于init分支src/main/resources/frontsource/nginx-1.18.0下

## 开发文档 `谋马点评.md`
位置：src/main/resources/doc/谋马点评.md

## 1.下载
克隆完整项目
```git
git clone https://gitee.com/ding-xinliang/hmdp.git
```
切换分支
```git
git checkout init
```

## 2.问题
直接使用了master分支项目来启动，控制台会一直报错:
```
NOGROUP No such key 'stream.orders' or consumer group 'g1' in XREADGROUP with GROUP option
```
原因：本项目会尝试访问Redis，连接Redis的Stream。建议切换到init分支来开发，
如果一定要运行master分支，请先在Redis客户端运行一下命令：
```shell
XGROUP CREATE stream.orders g1 0 MKSTREAM 
```