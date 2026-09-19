# claude-qd-417 · 冰场管理系统

冰场（ice rink）全栈种子仓：冰面 / 会员 / 课程 / 选课。技术栈 Spring Boot 3.3 + Java 17 + JPA + Vue3 + Element Plus + MySQL8 + Redis7 + nginx，`docker compose up` 即起。

## 启动

```bash
./start.sh
```

- 前端 http://127.0.0.1:8247
- 后端 http://127.0.0.1:8347 （接口前缀 `/api`）
- MySQL 3547 / Redis 6547

## 模块与业务规则

1. **冰面 iceLane**：编号唯一；状态 开放 / 维护 / 关闭。维护中冰面禁排课。
2. **会员 member**：卡号唯一；到期日 `yyyy-MM-dd`；过期（到期日 < 今天）禁止选课。
3. **课程 course**：归属冰面必须开放；容量上限（报名数不超容量）。
4. **选课 enrollment**（会员×课程多对多中间实体，含 id / memberId / courseId / status / enrollDate）：
   - 同会员同课程只能一条有效报名
   - 课程容量满拦
   - 过期会员拦
   - 退课后容量释放

## 接口

| 模块 | 列表 | 新增 | 改 |
| --- | --- | --- | --- |
| 冰面 | GET /api/ice-lanes | POST /api/ice-lanes | PUT /api/ice-lanes/{id} |
| 会员 | GET /api/members | POST /api/members | PUT /api/members/{id} |
| 课程 | GET /api/courses | POST /api/courses | PUT /api/courses/{id} |
| 选课 | GET /api/enrollments | POST /api/enrollments（报名） | PUT /api/enrollments/{id}（退课） |

## 页面

- 冰面：泳道条（横条 + 状态色）
- 课程：课程卡片 + 报名（会员下拉选人建立关联）
- 选课：场次日历（按日期分格）
- 会员：会员卡（过期标红，显示到期日）

导航形态：右侧悬浮竖向 tab 切换模块，左侧按状态/冰面分类筛选。
