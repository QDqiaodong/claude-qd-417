# claude-qd-417 · 冰场管理系统

冰场（ice rink）全栈种子仓：冰面 / 会员 / 课程 / 选课 / 浇冰 / **冰刀租借**。技术栈 Spring Boot 3.3 + Java 17 + JPA + Vue3 + Element Plus + MySQL8 + Redis7 + nginx，`docker compose up` 即起。

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
3. **课程 course**：归属冰面必须开放；容量上限（报名数不超容量）；含排期 `sessionDate/startTime/endTime`（yyyy-MM-dd / HH:mm）。
4. **选课 enrollment**（会员×课程多对多中间实体，含 id / memberId / courseId / status / enrollDate）：
   - 同会员同课程只能一条有效报名
   - 课程容量满拦：报名事务先对课程行 `SELECT ... FOR UPDATE` 加锁再判容量、落单、同步已报人数；两个窗口同时抢最后一个名额时行锁串行化，一单成功、另一单整单失败，已报人数绝不超容量；报名单与已报人数同一事务提交，不存在"报名成功但人数没涨"的假成功
   - 过期会员拦
   - 容量允许从大改小（可小于当前已报人数）：已报学员不清退，新报名立即按新容量拦；容量改大后无需额外操作，下一单报名直接按新容量放行
   - 退课与报名共用同一把课程行锁：名额在同一事务内立刻释放给下一个人；每条报名记录只能真正退一次，重复退课（含两个窗口同时点）整单 400，名额不重复释放；已退记录不能改回「已报」绕过容量检查，重新报名请走报名接口
5. **浇冰窗口 resurfaceWindow**（id / laneId / winDate / startTime / endTime / operator 当班磨冰工）：
   - 写明哪块冰、哪一天、几点浇到几点、当班磨冰工
   - 窗口落下后，同冰面、同一天、时段相交的公开课实时标记「浇冰中」，并**只冻新报名**；已报名不清、课程不挪冰、冰面状态不变
   - 白天可落窗口（压课只冻新报名）；窗口改到打烊后或删除、不再压课时，原先冻住的课自动恢复加人
   - 同一冰面、同一天时段相交，后写的整单回掉（400），先落下的继续压课；回掉不改冰面状态、不动已报人数
   - 冰面已关闭则没有冰可浇，窗口建不起来（维护中仍可浇）
   - 时段按半开区间判定，首尾相接（前段结束=后段开始）不算相交
6. **冰刀 skate**（id / code 唯一编号 / shoeSize 尺码 / status 可借·已借·待检）：一双实物一条记录。
7. **冰刀租借单 skateRental**（id / memberId / courseId / skateId 实际那双冰刀 / shoeSize / status / rentDate+rentTime / returnDate+returnTime / damageNote）：
   - 租借单同时拴住会员、当天课程、实际领走的那双冰刀；**不存在没有冰刀的空单**
   - 只有「当天该课程仍有效报名（已报）」的会员能领取；过期会员、已退课、课程非当天、名下还有未归还冰刀的一律拦回（400）
   - 柜员按尺码发鞋：事务内 `SELECT ... FOR UPDATE` 对可借冰刀加行锁，真正占住一双再落单；两个窗口同时抢同尺码最后一双时，行锁串行化，一单成功、另一单整单失败，不扣负可用数
   - 数据库兜底：`open_skate_id` 生成列 + 唯一索引，保证一双冰刀至多一张「已领取」单
   - 正常归还：单据收口为已归还，冰刀回可借，可再借
   - 损坏归还：单据仍完整收口（损坏归还），冰刀转待检、不立即释放；检修完成后可重新上架
   - 对已归还 / 损坏归还的单再次归还直接拦回，库存不重复 +1（幂等收口）

## 接口

| 模块 | 列表 | 新增 | 改 |
| --- | --- | --- | --- |
| 冰面 | GET /api/ice-lanes | POST /api/ice-lanes | PUT /api/ice-lanes/{id} |
| 会员 | GET /api/members | POST /api/members | PUT /api/members/{id} |
| 课程 | GET /api/courses（含 `resurfacing` / `resurfaceInfo` 浇冰冻态） | POST /api/courses | PUT /api/courses/{id} |
| 选课 | GET /api/enrollments | POST /api/enrollments（报名） | PUT /api/enrollments/{id}（退课） |
| 浇冰 | GET /api/resurface-windows | POST /api/resurface-windows（落窗口；相交 / 冰面关闭回 400） | PUT /api/resurface-windows/{id}（改到夜里）、DELETE /api/resurface-windows/{id} |
| 冰刀 | GET /api/skates（按尺码列出可借/已借/待检实物） | — | PUT /api/skates/{id}/inspect-done（检修完成上架） |
| 租借 | GET /api/skates/rentals（会员/课程/冰刀编号/当前状态） | POST /api/skates/rentals（按尺码发鞋；并发抢最后一双败方整单 400） | PUT /api/skates/rentals/{id}/return（`damaged=false` 正常归还 / `damaged=true` 损坏归还转待检；重复归还 400） |

## 页面

- 冰面：泳道条（横条 + 状态色）
- 课程：课程卡片 + 报名（会员下拉选人建立关联）；浇冰中卡片橙框、按钮变「浇冰中 · 暂停报名」
- 选课：场次日历（按报名日期分格）
- 会员：会员卡（过期标红，显示到期日）
- 浇冰：按日期 + 冰面分组的窗口列表，列出每段窗口压住的公开课；可落窗口 / 改到夜里 / 删除
- 冰刀租借：顶部可借/已借/待检/总数统计；实物按尺码分行展示（编号 + 状态 + 持有人），可正常归还 / 损坏归还 / 检修上架；「按尺码发鞋」对话框选当天课程与会员（过期、已退课、有未归还者直接标灰禁用）、填尺码发鞋；下方租借记录表含会员、课程、冰刀编号与尺码、状态、领取/归还时间、损坏备注。课程卡片另有「⛸ 发冰刀」按钮直达并预选当天课程。

导航形态：右侧悬浮竖向 tab 切换模块，左侧按状态 / 冰面分类筛选。
