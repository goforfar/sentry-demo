# API 测试脚本使用说明

## 📖 概述

`test_api.py` 是一个自动化的API测试脚本，用于随机访问Spring Boot应用的各种接口，模拟真实用户请求，并在Sentry中监控和上报异常情况。

## 🚀 快速开始

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

或者直接安装：

```bash
pip install requests
```

### 2. 启动Spring Boot应用

```bash
mvn spring-boot:run
```

等待应用启动完成，看到类似日志：
```
Started DemoApplication in X.XXX seconds
```

### 3. 运行测试脚本

```bash
python test_api.py
```

## 🎯 测试模式

脚本提供三种测试模式：

### 模式1: 完整测试（默认）

执行所有测试场景，包括：
- ✅ 基础CRUD操作
- ✅ 事务成功场景
- ❌ 事务回滚场景
- ❌ 业务验证异常
- ❌ 运行时异常
- 🎲 复杂事务场景（随机成功/失败）
- ⚠️ 特殊异常（超时、并发冲突）

### 模式2: 随机测试

随机选择API进行指定次数的测试，适合长时间运行以生成大量请求数据。

```bash
python test_api.py
# 选择模式2
# 输入测试次数，例如：100
```

### 模式3: 快速测试

只测试正常API，不触发异常，快速验证基本功能。

## 📊 测试场景说明

### 1. 基础CRUD测试

```python
- GET    /api/users              # 查询所有用户
- POST   /api/users              # 创建用户
- GET    /api/users/{id}         # 根据ID查询
- PUT    /api/users/{id}         # 更新用户
- DELETE /api/users/{id}         # 删除用户
```

### 2. 事务测试

**成功场景**：
```python
POST /api/users/batch/success
# 批量创建3个用户，全部成功
```

**回滚场景**：
```python
POST /api/users/batch/rollback
# 第二个用户username重复
# 触发唯一约束违反，整个事务回滚
```

### 3. 业务验证异常

```python
POST /api/users/validate

测试案例：
❌ 年龄小于18 (age=15)
❌ 年龄大于120 (age=150)
❌ username重复
❌ email重复
✅ 符合所有业务规则
```

### 4. 运行时异常

```python
POST /api/users/runtime-error

# 如果username包含"error"关键字
# 触发运行时异常，事务回滚
```

### 5. 复杂事务场景

```python
POST /api/users/complex-transaction

# 模拟多步骤事务：
# 1. 创建用户A
# 2. 创建用户B
# 3. 随机失败（50%概率）
# 4. 如果成功，创建用户C
# 失败时整个事务回滚
```

### 6. 特殊异常

```python
GET /api/users/99999              # 查询不存在的用户
PUT /api/users/88888              # 更新不存在的用户
DELETE /api/users/77777           # 删除不存在的用户
POST /api/users/timeout           # 数据库操作超时（5秒）
POST /api/users/{id}/concurrent   # 并发修改冲突
```

### 7. 原有接口

```python
GET /              # 首页
GET /hello         # Hello接口
GET /health        # 健康检查
GET /crash         # 触发测试异常
```

## ⚙️ 配置选项

可以在脚本顶部修改配置：

```python
# 服务器地址
BASE_URL = "http://localhost:8080"

# 请求间隔（秒）
MIN_DELAY = 0.5  # 最小间隔
MAX_DELAY = 2.0  # 最大间隔

# 测试数据
USERNAMES = ["alice", "bob", "charlie", ...]
DOMAINS = ["example.com", "test.com", ...]
```

## 📈 Sentry监控

所有异常都会自动上报到Sentry，你可以在Sentry控制台查看：

### 异常类型

1. **业务异常**
   - `IllegalStateException`: 用户名/邮箱已存在
   - `IllegalArgumentException`: 年龄不合法

2. **数据访问异常**
   - `DataIntegrityViolationException`: 违反唯一约束
   - `EmptyResultDataAccessException`: 查询不存在的用户

3. **运行时异常**
   - `RuntimeException`: 自定义运行时异常
   - 各种事务回滚异常

### 查看异常报告

1. 登录Sentry控制台: https://sentry.io/
2. 选择项目: `java-spring-boot`
3. 查看"Issues"标签页
4. 可以按以下维度筛选：
   - 异常类型
   - 发生时间
   - 请求路径
   - 用户信息

## 🎨 输出示例

```
============================================================
🚀 Sentry Demo - API 随机测试脚本
============================================================
📡 目标服务器: http://localhost:8080
⏱️  请求间隔: 0.5-2.0 秒
✅ 服务器连接正常

选择测试模式:
1. 完整测试（执行所有测试场景）
2. 随机测试（随机选择API进行测试）
3. 快速测试（只测试正常API）

请输入选择 (1/2/3，默认1): 1

============================================================
🔙 测试原有接口
============================================================

🏠 访问首页
🔵 GET http://localhost:8080/
   状态码: 200
   ✅ 成功
⏳ 等待 0.87 秒...

👋 Hello接口
🔵 GET http://localhost:8080/hello?name=Alice
   状态码: 200
   ✅ 成功
⏳ 等待 1.23 秒...

...

============================================================
✨ 测试完成！
============================================================
📊 共创建了 15 个用户
📈 请查看Sentry控制台查看异常报告:
   https://sentry.io/

💡 提示: 部分异常是预期的，用于测试Sentry的异常监控功能
```

## 🔧 高级用法

### 持续运行测试

使用随机测试模式并设置较大的迭代次数：

```bash
python test_api.py
# 选择模式2
# 输入次数: 1000
```

### 修改请求频率

编辑脚本中的配置：

```python
# 更快的测试
MIN_DELAY = 0.1
MAX_DELAY = 0.5

# 更慢的测试
MIN_DELAY = 2.0
MAX_DELAY = 5.0
```

### 自定义测试数据

修改 `USERNAMES` 和 `DOMAINS` 数组：

```python
USERNAMES = ["user1", "user2", "test_user"]
DOMAINS = ["company.com", "org.com"]
```

### 只测试特定场景

注释掉主函数中不需要的测试函数调用：

```python
def main():
    # 只测试事务相关
    test_transaction_success()
    test_transaction_rollback()
    test_business_validation()
    # test_runtime_exception()  # 跳过此测试
```

## 🐛 故障排除

### 问题1: 连接失败

```
❌ 连接失败: 请确保应用正在运行
```

**解决方案**：
- 确认Spring Boot应用已启动
- 检查端口8080是否被占用
- 尝试访问 http://localhost:8080/health

### 问题2: 依赖缺失

```
ModuleNotFoundError: No module named 'requests'
```

**解决方案**：
```bash
pip install requests
```

### 问题3: 数据库错误

如果在H2数据库中遇到错误，重启应用即可清空数据库（因为是内存数据库）。

## 📝 注意事项

1. ⚠️ H2是内存数据库，应用重启后所有数据会丢失
2. ⚠️ 某些测试会故意触发异常，这是正常的
3. ⚠️ 测试过程中会在Sentry中创建大量issue，注意清理
4. ✅ 脚本会自动跟踪创建的用户ID用于后续测试
5. ✅ 所有异常都会被Sentry捕获和上报

## 🎓 学习建议

通过这个脚本，你可以学习：

1. **Spring Boot事务管理**
   - 事务提交与回滚
   - 异常对事务的影响

2. **异常处理**
   - 业务异常
   - 系统异常
   - 异常传播

3. **Sentry监控**
   - 异常捕获
   - 错误追踪
   - 性能监控

4. **API测试**
   - 自动化测试
   - 随机测试
   - 压力测试

## 📚 相关文档

- [API文档](./API.md) - 详细的API接口说明
- [Sentry文档](https://docs.sentry.io/) - Sentry官方文档
- [Spring Boot文档](https://spring.io/projects/spring-boot) - Spring Boot官方文档

## 🤝 贡献

欢迎改进测试脚本，添加更多测试场景！
