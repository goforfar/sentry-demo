#!/usr/bin/env python3
"""
Sentry Demo - API 随机测试脚本
随机访问各种API接口，模拟真实用户请求，用于测试Sentry异常监控
"""

import random
import time
import json
from typing import List, Dict
import requests

# 配置
BASE_URL = "http://localhost:8080"
# BASE_URL = "http://8c7bbbba95c1025975e548cee86dfadc.nebulab.app"
MIN_DELAY = 0.5  # 最小请求间隔（秒）
MAX_DELAY = 2.0  # 最大请求间隔（秒）

# 测试数据
USERNAMES = ["alice", "bob", "charlie", "david", "emma", "frank", "grace", "henry"]
DOMAINS = ["example.com", "test.com", "demo.com", "mail.com"]

# 已创建的用户ID池（用于更新和删除操作）
user_ids = []


def random_delay():
    """随机延迟"""
    delay = random.uniform(MIN_DELAY, MAX_DELAY)
    print(f"⏳ 等待 {delay:.2f} 秒...")
    time.sleep(delay)


def generate_user_data(index: int = None) -> Dict:
    """生成随机用户数据"""
    if index is not None:
        username = f"user_{index}_{random.randint(1000, 9999)}"
    else:
        username = f"{random.choice(USERNAMES)}_{random.randint(100, 999)}"

    return {
        "username": username,
        "email": f"{username}@{random.choice(DOMAINS)}",
        "phone": f"1{random.randint(300000000, 999999999)}",
        "age": random.randint(18, 65),
        "active": random.choice([True, False])
    }


def make_request(method: str, endpoint: str, data: Dict = None, params: Dict = None) -> requests.Response:
    """发送HTTP请求"""
    url = f"{BASE_URL}{endpoint}"
    headers = {"Content-Type": "application/json"}

    try:
        print(f"🔵 {method} {url}")
        if data:
            print(f"   数据: {json.dumps(data, ensure_ascii=False)}")
        if params:
            print(f"   参数: {params}")

        if method == "GET":
            response = requests.get(url, params=params)
        elif method == "POST":
            response = requests.post(url, json=data, headers=headers)
        elif method == "PUT":
            response = requests.put(url, json=data, headers=headers)
        elif method == "DELETE":
            response = requests.delete(url)
        else:
            raise ValueError(f"不支持的HTTP方法: {method}")

        print(f"   状态码: {response.status_code}")
        if response.status_code < 300:
            print(f"   ✅ 成功")
            try:
                result = response.json()
                if result.get("success"):
                    # 如果创建成功，尝试提取用户ID
                    if "data" in result and isinstance(result["data"], dict) and "id" in result["data"]:
                        user_id = result["data"]["id"]
                        if user_id not in user_ids:
                            user_ids.append(user_id)
                            print(f"   📝 保存用户ID: {user_id}")
            except:
                pass
        else:
            print(f"   ❌ 失败")
            try:
                print(f"   响应: {response.text[:200]}")
            except:
                pass

        return response

    except requests.exceptions.ConnectionError:
        print(f"   ❌ 连接失败: 请确保应用正在运行 ({BASE_URL})")
        return None
    except Exception as e:
        print(f"   ❌ 异常: {str(e)}")
        return None


def test_basic_crud():
    """测试基础CRUD操作"""
    print("\n" + "="*60)
    print("📚 测试基础CRUD操作")
    print("="*60)

    # 1. 查询所有用户
    print("\n1️⃣ 查询所有用户")
    make_request("GET", "/api/users")
    random_delay()

    # 2. 创建单个用户
    print("\n2️⃣ 创建单个用户")
    user_data = generate_user_data(1)
    make_request("POST", "/api/users", data=user_data)
    random_delay()

    # 3. 根据ID查询用户
    if user_ids:
        print("\n3️⃣ 根据ID查询用户")
        user_id = random.choice(user_ids)
        make_request("GET", f"/api/users/{user_id}")
        random_delay()

        # 4. 更新用户
        print("\n4️⃣ 更新用户")
        update_data = generate_user_data(2)
        make_request("PUT", f"/api/users/{user_id}", data=update_data)
        random_delay()

        # 5. 删除用户（删除后user_ids中会保留无效ID，模拟查询不存在的用户）
        if random.random() > 0.7:  # 30%概率删除
            print("\n5️⃣ 删除用户")
            make_request("DELETE", f"/api/users/{user_id}")
            random_delay()


def test_transaction_success():
    """测试事务成功场景"""
    print("\n" + "="*60)
    print("✅ 测试事务成功场景")
    print("="*60)

    users = [generate_user_data(i) for i in range(3)]
    print("\n📦 批量创建3个用户（应该成功）")
    make_request("POST", "/api/users/batch/success", data=users)


def test_transaction_rollback():
    """测试事务回滚场景"""
    print("\n" + "="*60)
    print("🔄 测试事务回滚场景")
    print("="*60)

    print("\n❌ 批量创建用户（第二个用户username重复，触发回滚）")
    users = [
        generate_user_data(10),
        generate_user_data(10),  # 相同的index，username会重复
        generate_user_data(12)
    ]
    make_request("POST", "/api/users/batch/rollback", data=users)
    random_delay()

    print("\n❌ 批量创建用户（email重复，触发回滚）")
    user1 = generate_user_data(20)
    user2 = generate_user_data(21)
    user2["email"] = user1["email"]  # email重复
    make_request("POST", "/api/users/batch/rollback", data=[user1, user2])


def test_business_validation():
    """测试业务验证异常"""
    print("\n" + "="*60)
    print("🔍 测试业务验证异常")
    print("="*60)

    # 1. 年龄太小
    print("\n❌ 创建用户 - 年龄小于18")
    user_data = generate_user_data(30)
    user_data["age"] = 15
    make_request("POST", "/api/users/validate", data=user_data)
    random_delay()

    # 2. 年龄太大
    print("\n❌ 创建用户 - 年龄大于120")
    user_data = generate_user_data(31)
    user_data["age"] = 150
    make_request("POST", "/api/users/validate", data=user_data)
    random_delay()

    # 3. 用户名重复
    if user_ids:
        print("\n❌ 创建用户 - username重复")
        # 先查询一个存在的用户
        user_id = random.choice(user_ids)
        response = make_request("GET", f"/api/users/{user_id}")
        if response and response.status_code == 200:
            try:
                existing_user = response.json().get("data")
                if existing_user:
                    user_data = generate_user_data(32)
                    user_data["username"] = existing_user["username"]  # 重复的username
                    make_request("POST", "/api/users/validate", data=user_data)
            except:
                pass
        random_delay()

    # 4. 成功创建
    print("\n✅ 创建用户 - 符合业务规则")
    user_data = generate_user_data(33)
    make_request("POST", "/api/users/validate", data=user_data)


def test_runtime_exception():
    """测试运行时异常"""
    print("\n" + "="*60)
    print("⚡ 测试运行时异常")
    print("="*60)

    # 1. 正常创建
    print("\n✅ 创建用户 - username不包含error")
    user_data = generate_user_data(40)
    make_request("POST", "/api/users/runtime-error", data=user_data)
    random_delay()

    # 2. 触发运行时异常
    print("\n❌ 创建用户 - username包含error（触发运行时异常和事务回滚）")
    user_data = generate_user_data(41)
    user_data["username"] = f"error_{user_data['username']}"
    make_request("POST", "/api/users/runtime-error", data=user_data)


def test_complex_transaction():
    """测试复杂事务场景"""
    print("\n" + "="*60)
    print("🎲 测试复杂事务场景（随机成功或失败）")
    print("="*60)

    print("\n🎯 执行复杂事务（50%概率成功，50%概率回滚）")
    make_request("POST", "/api/users/complex-transaction")


def test_special_exceptions():
    """测试特殊异常"""
    print("\n" + "="*60)
    print("⚠️  测试特殊异常")
    print("="*60)

    # 1. 查询不存在的用户
    print("\n❌ 查询不存在的用户ID")
    make_request("GET", "/api/users/99999")
    random_delay()

    # 2. 更新不存在的用户
    print("\n❌ 更新不存在的用户")
    make_request("PUT", "/api/users/88888", data=generate_user_data(50))
    random_delay()

    # 3. 删除不存在的用户
    print("\n❌ 删除不存在的用户")
    make_request("DELETE", "/api/users/77777")
    random_delay()

    # 4. 数据库超时
    print("\n⏱️  模拟数据库操作超时（5秒）")
    user_data = generate_user_data(60)
    make_request("POST", "/api/users/timeout", data=user_data)

    # 5. 并发修改冲突
    if user_ids:
        print("\n🔀 模拟并发修改冲突")
        user_id = random.choice(user_ids)
        make_request("POST", f"/api/users/{user_id}/concurrent", params={"email": "concurrent@example.com"})


def test_original_endpoints():
    """测试原有接口"""
    print("\n" + "="*60)
    print("🔙 测试原有接口")
    print("="*60)

    print("\n🏠 访问首页")
    make_request("GET", "/")
    random_delay()

    print("\n👋 Hello接口")
    names = ["World", "Alice", "Bob", "Charlie"]
    make_request("GET", f"/hello?name={random.choice(names)}")
    random_delay()

    print("\n💚 健康检查")
    make_request("GET", "/health")
    random_delay()

    print("\n💥 触发测试异常")
    make_request("GET", "/crash")


def random_test_mode(iterations: int = 20):
    """随机测试模式 - 随机选择API进行测试"""
    print("\n" + "="*60)
    print(f"🎲 随机测试模式 - 将执行 {iterations} 次随机请求")
    print("="*60)

    test_functions = [
        lambda: make_request("GET", "/"),
        lambda: make_request("GET", "/api/users"),
        lambda: make_request("POST", "/api/users", data=generate_user_data()),
        lambda: make_request("GET", f"/api/users/by-username/{random.choice(USERNAMES)}_{random.randint(100, 999)}"),
        lambda: make_request("GET", "/crash"),
    ]

    # 如果有用户ID，添加更多测试
    if user_ids:
        test_functions.extend([
            lambda: make_request("GET", f"/api/users/{random.choice(user_ids)}"),
            lambda: make_request("PUT", f"/api/users/{random.choice(user_ids)}", data=generate_user_data()),
        ])

    for i in range(iterations):
        print(f"\n{'='*60}")
        print(f"第 {i+1}/{iterations} 次随机测试")
        print(f"{'='*60}")

        test_func = random.choice(test_functions)
        test_func()
        random_delay()


def main():
    """主函数"""
    print("\n" + "="*60)
    print("🚀 Sentry Demo - API 随机测试脚本")
    print("="*60)
    print(f"📡 目标服务器: {BASE_URL}")
    print(f"⏱️  请求间隔: {MIN_DELAY}-{MAX_DELAY} 秒")

    # 检查服务器是否可用
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=5)
        if response.status_code == 200:
            print("✅ 服务器连接正常")
        else:
            print("⚠️  服务器响应异常，但继续执行...")
    except:
        print("❌ 无法连接到服务器，请确保应用正在运行")
        print("   启动命令: mvn spring-boot:run")
        return

    print("\n选择测试模式:")
    print("1. 完整测试（执行所有测试场景）")
    print("2. 随机测试（随机选择API进行测试）")
    print("3. 快速测试（只测试正常API）")

    choice = input("\n请输入选择 (1/2/3，默认1): ").strip() or "1"

    if choice == "2":
        iterations = input("请输入测试次数 (默认20): ").strip() or "20"
        random_test_mode(int(iterations))
    elif choice == "3":
        test_basic_crud()
        test_transaction_success()
        test_original_endpoints()
    else:
        # 完整测试
        test_original_endpoints()
        random_delay()

        test_basic_crud()
        random_delay()

        test_transaction_success()
        random_delay()

        test_transaction_rollback()
        random_delay()

        test_business_validation()
        random_delay()

        test_runtime_exception()
        random_delay()

        test_complex_transaction()
        random_delay()

        test_special_exceptions()

    print("\n" + "="*60)
    print("✨ 测试完成！")
    print("="*60)
    print(f"📊 共创建了 {len(user_ids)} 个用户")
    print("📈 请查看Sentry控制台查看异常报告:")
    print("   https://sentry.io/")
    print("\n💡 提示: 部分异常是预期的，用于测试Sentry的异常监控功能")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️  测试被用户中断")
    except Exception as e:
        print(f"\n\n❌ 发生错误: {str(e)}")
