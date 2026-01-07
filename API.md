# Sentry Demo - API 接口文档

本文档包含所有用户管理和异常测试接口的详细说明和curl示例。

## 基础信息

- **基础URL**: `http://localhost:8080`
- **H2控制台**: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:testdb`
  - 用户名: `sa`
  - 密码: (留空)

---

## 1. 基础CRUD接口

### 1.1 创建用户

**接口**: `POST /api/users`

**说明**: 创建单个用户（正常情况）

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan",
    "email": "zhangsan@example.com",
    "phone": "13800138000",
    "age": 25,
    "active": true
  }'
```

**成功响应**:
```json
{
  "success": true,
  "message": "用户创建成功",
  "data": {
    "id": 1,
    "username": "zhangsan",
    "email": "zhangsan@example.com",
    "phone": "13800138000",
    "age": 25,
    "active": true
  }
}
```

---

### 1.2 查询所有用户

**接口**: `GET /api/users`

**说明**: 获取系统中所有用户列表

```bash
curl -X GET http://localhost:8080/api/users
```

**响应示例**:
```json
{
  "success": true,
  "count": 2,
  "data": [
    {
      "id": 1,
      "username": "zhangsan",
      "email": "zhangsan@example.com",
      "age": 25,
      "active": true
    },
    {
      "id": 2,
      "username": "lisi",
      "email": "lisi@example.com",
      "age": 30,
      "active": true
    }
  ]
}
```

---

### 1.3 根据ID查询用户

**接口**: `GET /api/users/{id}`

**说明**: 根据用户ID查询用户信息，如果用户不存在会抛出异常并上报到Sentry

```bash
# 查询存在的用户
curl -X GET http://localhost:8080/api/users/1

# 查询不存在的用户（会触发异常）
curl -X GET http://localhost:8080/api/users/999
```

**不存在用户时的响应**:
```json
{
  "success": false,
  "message": "查询失败: 用户不存在: 999"
}
```

---

### 1.4 根据用户名查询

**接口**: `GET /api/users/by-username/{username}`

**说明**: 根据用户名查询用户

```bash
curl -X GET http://localhost:8080/api/users/by-username/zhangsan
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "zhangsan",
    "email": "zhangsan@example.com",
    "age": 25,
    "active": true
  }
}
```

---

### 1.5 更新用户

**接口**: `PUT /api/users/{id}`

**说明**: 更新指定ID的用户信息

```bash
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan_updated",
    "email": "zhangsan_new@example.com",
    "phone": "13900139000",
    "age": 26,
    "active": false
  }'
```

**响应示例**:
```json
{
  "success": true,
  "message": "用户更新成功",
  "data": {
    "id": 1,
    "username": "zhangsan_updated",
    "email": "zhangsan_new@example.com",
    "phone": "13900139000",
    "age": 26,
    "active": false
  }
}
```

---

### 1.6 删除用户

**接口**: `DELETE /api/users/{id}`

**说明**: 删除指定ID的用户

```bash
curl -X DELETE http://localhost:8080/api/users/1
```

**响应示例**:
```json
{
  "success": true,
  "message": "用户删除成功"
}
```

---

## 2. 事务异常测试接口

### 2.1 批量创建用户 - 成功场景

**接口**: `POST /api/users/batch/success`

**说明**: 批量创建多个用户，全部成功（事务正常提交）

```bash
curl -X POST http://localhost:8080/api/users/batch/success \
  -H "Content-Type: application/json" \
  -d '[
    {
      "username": "user1",
      "email": "user1@example.com",
      "age": 25,
      "active": true
    },
    {
      "username": "user2",
      "email": "user2@example.com",
      "age": 30,
      "active": true
    },
    {
      "username": "user3",
      "email": "user3@example.com",
      "age": 35,
      "active": true
    }
  ]'
```

**成功响应**:
```json
{
  "success": true,
  "message": "批量创建成功",
  "count": 3,
  "data": [...]
}
```

---

### 2.2 批量创建用户 - 事务回滚场景

**接口**: `POST /api/users/batch/rollback`

**说明**: 批量创建用户，但第二个用户违反唯一约束（username重复），导致整个事务回滚，第一个用户也不会保存

```bash
curl -X POST http://localhost:8080/api/users/batch/rollback \
  -H "Content-Type: application/json" \
  -d '[
    {
      "username": "test_user",
      "email": "test1@example.com",
      "age": 25,
      "active": true
    },
    {
      "username": "test_user",
      "email": "test2@example.com",
      "age": 30,
      "active": true
    }
  ]'
```

**失败响应**:
```json
{
  "success": false,
  "message": "批量创建失败，事务已回滚: could not execute statement..."
}
```

**说明**:
- 第一个用户会先插入成功
- 第二个用户因为username重复导致违反唯一约束
- 整个事务回滚，第一个用户也会被撤销
- 异常会上报到Sentry

---

### 2.3 创建用户 - 业务验证异常

**接口**: `POST /api/users/validate`

**说明**: 创建用户并进行业务验证，违反业务规则会抛出异常并回滚事务

#### 场景1: 用户名重复

```bash
# 先创建一个用户
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username": "duplicate_user", "email": "user1@example.com", "age": 25, "active": true}'

# 再次创建相同用户名的用户（会失败）
curl -X POST http://localhost:8080/api/users/validate \
  -H "Content-Type: application/json" \
  -d '{"username": "duplicate_user", "email": "user2@example.com", "age": 30, "active": true}'
```

**失败响应**:
```json
{
  "success": false,
  "message": "业务验证失败: 用户名已存在: duplicate_user"
}
```

#### 场景2: 邮箱重复

```bash
curl -X POST http://localhost:8080/api/users/validate \
  -H "Content-Type: application/json" \
  -d '{"username": "new_user", "email": "duplicate@example.com", "age": 25, "active": true}'
```

#### 场景3: 年龄不合法（小于18或大于120）

```bash
# 年龄小于18
curl -X POST http://localhost:8080/api/users/validate \
  -H "Content-Type: application/json" \
  -d '{"username": "underage_user", "email": "underage@example.com", "age": 15, "active": true}'

# 年龄大于120
curl -X POST http://localhost:8080/api/users/validate \
  -H "Content-Type: application/json" \
  -d '{"username": "elderly_user", "email": "elderly@example.com", "age": 150, "active": true}'
```

**失败响应**:
```json
{
  "success": false,
  "message": "业务验证失败: 年龄必须在18-120之间: 15"
}
```

---

### 2.4 创建用户 - 运行时异常回滚

**接口**: `POST /api/users/runtime-error`

**说明**: 用户名包含"error"关键字时会触发运行时异常，导致事务回滚

```bash
# 正常创建（不包含error）
curl -X POST http://localhost:8080/api/users/runtime-error \
  -H "Content-Type: application/json" \
  -d '{"username": "normal_user", "email": "normal@example.com", "age": 25, "active": true}'

# 触发运行时异常（用户名包含error）
curl -X POST http://localhost:8080/api/users/runtime-error \
  -H "Content-Type: application/json" \
  -d '{"username": "error_user", "email": "error@example.com", "age": 25, "active": true}'
```

**失败响应**:
```json
{
  "success": false,
  "message": "运行时异常，事务已回滚: 模拟运行时异常：用户名包含error关键字"
}
```

**说明**:
- 用户会先保存到数据库
- 随后因为用户名包含"error"触发异常
- 整个事务回滚，用户数据被撤销
- 异常上报到Sentry

---

### 2.5 复杂事务场景 - 部分失败回滚

**接口**: `POST /api/users/complex-transaction`

**说明**: 模拟复杂的多步骤事务操作，中间步骤失败会导致整个事务回滚

```bash
curl -X POST http://localhost:8080/api/users/complex-transaction
```

**可能的结果**:

1. **成功（50%概率）**:
```json
{
  "success": true,
  "message": "复杂事务场景执行成功"
}
```
创建了3个用户：user_a、user_b、user_c

2. **失败（50%概率）**:
```json
{
  "success": false,
  "message": "复杂事务执行失败，事务已回滚: 模拟中间处理失败，事务将回滚"
}
```

**说明**:
- 先创建用户A和用户B
- 中间随机失败（50%概率）
- 如果失败，整个事务回滚，A和B都会被撤销
- 如果成功，继续创建用户C
- 异常会上报到Sentry

---

## 3. 特殊异常测试接口

### 3.1 数据库操作超时

**接口**: `POST /api/users/timeout`

**说明**: 模拟数据库操作超时（5秒延迟）

```bash
curl -X POST http://localhost:8080/api/users/timeout \
  -H "Content-Type: application/json" \
  -d '{
    "username": "timeout_user",
    "email": "timeout@example.com",
    "age": 25,
    "active": true
  }'
```

**说明**:
- 线程会睡眠5秒模拟长时间操作
- 用于测试数据库连接超时场景
- 超时异常会上报到Sentry

---

### 3.2 并发修改冲突

**接口**: `POST /api/users/{id}/concurrent`

**说明**: 模拟并发修改冲突场景

```bash
curl -X POST "http://localhost:8080/api/users/1/concurrent?email=newemail@example.com"
```

**失败响应**:
```json
{
  "success": false,
  "message": "并发修改冲突: ..."
}
```

**说明**:
- 模拟多个事务同时修改同一用户数据
- 可能触发乐观锁异常或数据不一致
- 异常上报到Sentry

---

## 4. 原有测试接口

### 4.1 首页

**接口**: `GET /`

```bash
curl -X GET http://localhost:8080/
```

---

### 4.2 Hello接口

**接口**: `GET /hello?name=YourName`

```bash
curl -X GET "http://localhost:8080/hello?name=World"
```

---

### 4.3 健康检查

**接口**: `GET /health`

```bash
curl -X GET http://localhost:8080/health
```

---

### 4.4 异常测试

**接口**: `GET /crash`

**说明**: 触发一个简单的测试异常

```bash
curl -X GET http://localhost:8080/crash
```

---

## 5. 测试场景示例

### 场景1: 测试完整的事务回滚流程

```bash
# 1. 创建第一个用户
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "alice@example.com", "age": 25, "active": true}'

# 2. 尝试批量创建，第二个用户username重复，触发回滚
curl -X POST http://localhost:8080/api/users/batch/rollback \
  -H "Content-Type: application/json" \
  -d '[
    {"username": "bob", "email": "bob@example.com", "age": 30, "active": true},
    {"username": "alice", "email": "bob2@example.com", "age": 35, "active": true}
  ]'

# 3. 查询所有用户，验证bob也没有被创建（因为回滚）
curl -X GET http://localhost:8080/api/users
```

### 场景2: 测试业务验证

```bash
# 1. 年龄验证失败
curl -X POST http://localhost:8080/api/users/validate \
  -H "Content-Type: application/json" \
  -d '{"username": "child", "email": "child@example.com", "age": 10, "active": true}'

# 2. 用户名重复验证
curl -X POST http://localhost:8080/api/users/validate \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "new@example.com", "age": 25, "active": true}'

# 3. 成功创建
curl -X POST http://localhost:8080/api/users/validate \
  -H "Content-Type: application/json" \
  -d '{"username": "charlie", "email": "charlie@example.com", "age": 25, "active": true}'
```

### 场景3: 测试运行时异常回滚

```bash
# 1. 正常创建（用户名不含error）
curl -X POST http://localhost:8080/api/users/runtime-error \
  -H "Content-Type: application/json" \
  -d '{"username": "david", "email": "david@example.com", "age": 25, "active": true}'

# 2. 触发运行时异常（用户名含error）
curl -X POST http://localhost:8080/api/users/runtime-error \
  -H "Content-Type: application/json" \
  -d '{"username": "error_david", "email": "error@example.com", "age": 25, "active": true}'

# 3. 验证error_david没有被创建
curl -X GET http://localhost:8080/api/users/by-username/error_david
```

---

## 6. Sentry监控

所有异常都会自动上报到Sentry，你可以在Sentry控制台查看：

- **异常类型**: RuntimeException、IllegalStateException、IllegalArgumentException等
- **异常位置**: 完整的堆栈跟踪
- **请求信息**: HTTP方法、URL、参数等
- **用户信息**: 如果设置了send-default-pii
- **标签**: 可以根据事务类型、操作类型等进行分类

---

## 7. H2数据库控制台

访问 `http://localhost:8080/h2-console` 查看数据库：

**连接信息**:
- JDBC URL: `jdbc:h2:mem:testdb`
- 用户名: `sa`
- 密码: (留空)

**示例SQL查询**:
```sql
-- 查看所有用户
SELECT * FROM users;

-- 查看用户数量
SELECT COUNT(*) FROM users;

-- 查看特定用户
SELECT * FROM users WHERE username = 'alice';
```

---

## 8. 注意事项

1. **数据不持久化**: H2是内存数据库，应用重启后数据会丢失
2. **事务隔离**: 所有使用`@Transactional`的方法都在事务中执行
3. **异常上报**: 所有捕获的异常都会通过Sentry上报
4. **唯一约束**: username和email字段有唯一约束
5. **业务规则**: 年龄必须在18-120之间
