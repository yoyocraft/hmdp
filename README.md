# 类大众点评
> 本项目 基于MacOS系统开发，使用前保证项目中用的路径是可用的

项目代码包含2个分支：
- master : 主分支，包含完整版后端代码 && 修改之后的前端代码
- init (包含前端资源): 初始化分支，实战篇的初始代码，建议以这个分支作为自己开发的基础代码
  - 前端资源位于init分支src/main/resources/frontsource/nginx-1.18.0下

## 项目所需要的资源
- 开发文档 `doc.md` => 位置：src/main/resources/doc/doc.md
- 数据库文件 => 位置：src/main/resources/db/hmdp.sql
- jmeter测试文件 => 位置：src/main/resources/jmxSource/*
- 修改后的前端代码 => 位置：src/main/resources/frontsource/finishedSource/finishedFrontend.zip

## 1.下载
克隆完整项目
```git
git clone https://github.com/dingxinliang88/hmdp.git
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

## 3.运行
### 后端
- 创建数据库
- 在配置文件`application.yml`中配置自己的相关信息：Redis、MySQL、port and so on
- 按照`2.问题`建立对应的Redis Stream，充当简易的消息队列
- 确保maven、database等环境正常
### 前端
- Nginx部署`修改后的前端代码`即可

## 4.参与本项目
- Fork 本仓库
- 根据要修改的代码业务新建 Feat_xxx 分支，xxx为业务、功能
- 提交代码
- 新建 Pull Request

## 5.关于作者
- [博客地址](codejuzi.icu)
- [Gitee](https://gitee.com/codejuzi)
- 邮箱
  - 谷歌：dingxinliang1118@gmail.com
  - 网易：d1741530592@163.com
- QQ：1741530592
