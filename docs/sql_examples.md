# 常用复杂 SQL 查询案例 (Few-Shot Examples)

## 案例 1：按月统计销售额趋势
**问题**: "帮我查一下过去一年的每月销售额趋势"
**参考 SQL**:
```sql
SELECT 
    DATE_FORMAT(create_time, '%Y-%m') AS sale_month, 
    SUM(total_amount) AS monthly_gmv
FROM t_orders
WHERE 
    create_time >= DATE_SUB(NOW(), INTERVAL 1 YEAR)
    AND status IN ('PAID', 'SHIPPED', 'COMPLETED') -- 注意只统计有效订单
GROUP BY sale_month
ORDER BY sale_month ASC;
```