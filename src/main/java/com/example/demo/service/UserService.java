package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 创建用户 - 正常情况
     */
    public User createUser(User user) {
        log.info("创建用户: {}", user.getUsername());
        return userRepository.save(user);
    }

    /**
     * 批量创建用户 - 事务成功场景
     */
    @Transactional
    public List<User> batchCreateUsersSuccess(List<User> users) {
        log.info("批量创建用户，数量: {}", users.size());
        List<User> savedUsers = userRepository.saveAll(users);
        log.info("批量创建成功");
        return savedUsers;
    }

    /**
     * 批量创建用户 - 事务回滚场景（违反唯一约束）
     */
    @Transactional
    public List<User> batchCreateUsersWithRollback(List<User> users) {
        log.info("批量创建用户（包含重复用户），数量: {}", users.size());
        try {
            // 第一个用户会成功保存
            userRepository.save(users.get(0));
            log.info("第一个用户保存成功: {}", users.get(0).getUsername());

            // 第二个用户使用相同的username，会违反唯一约束
            userRepository.save(users.get(1));
            log.info("第二个用户保存成功: {}", users.get(1).getUsername());

            return userRepository.saveAll(users);
        } catch (Exception e) {
            log.error("批量创建失败，事务回滚", e);

            throw e;
        }
    }

    /**
     * 模拟业务异常导致事务回滚
     */
    @Transactional
    public User createUserWithBusinessValidation(User user) {
        log.info("创建用户并进行业务验证");

        // 检查用户名是否已存在
        if (userRepository.existsByUsername(user.getUsername())) {
            String errorMsg = "用户名已存在: " + user.getUsername();
            log.error(errorMsg);
            IllegalStateException exception = new IllegalStateException(errorMsg);

            throw exception;
        }

        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(user.getEmail())) {
            String errorMsg = "邮箱已存在: " + user.getEmail();
            log.error(errorMsg);
            IllegalStateException exception = new IllegalStateException(errorMsg);

            throw exception;
        }

        // 模拟业务规则：年龄必须在18-120之间
        if (user.getAge() < 18 || user.getAge() > 120) {
            String errorMsg = "年龄必须在18-120之间: " + user.getAge();
            log.error(errorMsg);
            IllegalArgumentException exception = new IllegalArgumentException(errorMsg);

            throw exception;
        }

        return userRepository.save(user);
    }

    /**
     * 模拟运行时异常导致事务回滚
     */
    @Transactional
    public User createUserWithRuntimeException(User user) {
        log.info("创建用户（模拟运行时异常）");
        userRepository.save(user);
        log.info("用户保存成功: {}", user.getUsername());

        // 模拟后续处理中发生异常
        if (user.getUsername().contains("error")) {
            String errorMsg = "模拟运行时异常：用户名包含error关键字-no";

            log.error(errorMsg);
            RuntimeException exception = new RuntimeException(errorMsg);
            throw exception;
        }

        return user;
    }

    /**
     * 模拟部分失败的事务场景
     */
    @Transactional
    public void transferUserScenario() {
        log.info("执行复杂事务场景：用户转移操作");

        try {
            // 1. 创建用户A
            User userA = new User();
            userA.setUsername("user_a_" + System.currentTimeMillis());
            userA.setEmail("user_a@example.com");
            userA.setAge(25);
            userA.setActive(true);
            userRepository.save(userA);
            log.info("用户A创建成功: {}", userA.getUsername());

            // 2. 创建用户B
            User userB = new User();
            userB.setUsername("user_b_" + System.currentTimeMillis());
            userB.setEmail("user_b@example.com");
            userB.setAge(30);
            userB.setActive(true);
            userRepository.save(userB);
            log.info("用户B创建成功: {}", userB.getUsername());

            // 3. 模拟中间处理失败
            if (Math.random() > 0.5) {
                throw new RuntimeException("模拟中间处理失败，事务将回滚");
            }

            // 4. 创建用户C
            User userC = new User();
            userC.setUsername("user_c_" + System.currentTimeMillis());
            userC.setEmail("user_c@example.com");
            userC.setAge(35);
            userC.setActive(true);
            userRepository.save(userC);
            log.info("用户C创建成功: {}", userC.getUsername());

            log.info("复杂事务场景执行成功");

        } catch (Exception e) {
            log.error("复杂事务场景执行失败，事务回滚", e);

            throw e;
        }
    }

    /**
     * 查询所有用户
     */
    public List<User> getAllUsers() {
        log.info("查询所有用户");
        return userRepository.findAll();
    }

    /**
     * 根据ID查询用户
     */
    public User getUserById(Long id) {
        log.info("查询用户ID: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    String errorMsg = "该用户不存在: " + id;
                    log.error(errorMsg);
                    log.error("新err msg {}",errorMsg);
                    RuntimeException exception = new RuntimeException(errorMsg);

                    return exception;
                });
    }

    /**
     * 根据用户名查询
     */
    public Optional<User> getUserByUsername(String username) {
        log.info("查询用户名: {}", username);
        return userRepository.findByUsername(username);
    }

    /**
     * 更新用户
     */
    @Transactional
    public User updateUser(Long id, User userDetails) {
        log.info("更新用户ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    String errorMsg = "用户不存在: " + id;
                    log.error(errorMsg);
                    RuntimeException exception = new RuntimeException(errorMsg);

                    return exception;
                });

        user.setUsername(userDetails.getUsername());
        user.setEmail(userDetails.getEmail());
        user.setPhone(userDetails.getPhone());
        user.setAge(userDetails.getAge());
        user.setActive(userDetails.getActive());

        User updatedUser = userRepository.save(user);
        log.info("用户更新成功: {}", updatedUser.getUsername());
        return updatedUser;
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        log.info("删除用户ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    String errorMsg = "用户不存在: " + id;
                    log.error(errorMsg);
                    RuntimeException exception = new RuntimeException(errorMsg);

                    return exception;
                });

        userRepository.delete(user);
        log.info("用户删除成功: {}", user.getUsername());
    }

    /**
     * 模拟数据库连接超时
     */
    @Transactional
    public User simulateDatabaseTimeout(User user) {
        log.info("模拟数据库操作超时");
        try {
            Thread.sleep(5000); // 模拟长时间操作
            return userRepository.save(user);
        } catch (InterruptedException e) {
            log.error("数据库操作被中断", e);

            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断", e);
        }
    }

    /**
     * 模拟并发修改异常
     */
    @Transactional
    public User simulateConcurrentModification(Long id, String newEmail) {
        log.info("模拟并发修改异常，用户ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + id));

        // 模拟另一个事务已经修改了数据
        user.setEmail(newEmail + "_concurrent");

        // 这里会触发乐观锁异常（如果配置了@Version字段）
        // 或者触发唯一约束冲突
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            log.error("并发修改冲突", e);

            throw new RuntimeException("并发修改冲突", e);
        }
    }
}
