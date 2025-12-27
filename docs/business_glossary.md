# 电商系统业务术语定义

## 用户分层定义
1. **高净值用户 (High Net Worth Users)**:
    - 定义：累计消费总金额（total_spent）大于 10,000 元，且退款率低于 5% 的用户。
    - SQL逻辑：需要关联订单表统计 SUM(amount)。

2. **沉睡用户 (Dormant Users)**:
    - 定义：注册时间超过 180 天，且最近 90 天内没有任何下单记录的用户。
    - SQL逻辑：DATEDIFF(NOW(), last_login_time) > 90。

3. **新客 (New Users)**:
    - 定义：在当前自然月内注册，且完成了首单支付的用户。

## 交易指标定义
1. **GMV (Gross Merchandise Volume)**:
    - 定义：商品交易总额。指一定时间段内所有**已支付**（status = 'PAID'）和**待发货**（status = 'SHIPPED'）的订单金额总和，不包含已取消的订单。

2. **复购率 (Repurchase Rate)**:
    - 定义：在统计周期内，购买次数 >= 2 次的用户数 / 有购买行为的总用户数。