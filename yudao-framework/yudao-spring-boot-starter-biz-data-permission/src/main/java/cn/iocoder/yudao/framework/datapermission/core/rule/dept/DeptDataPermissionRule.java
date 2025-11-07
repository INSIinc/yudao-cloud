package cn.iocoder.yudao.framework.datapermission.core.rule.dept;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRule;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 基于部门的 {@link DataPermissionRule} 数据权限规则实现
 *
 * 【什么是数据权限？】
 * 数据权限就是控制用户能看到哪些数据。比如：
 * - 销售部门的人只能看到销售部门的订单
 * - 财务部门的人只能看到财务部门的报表
 *
 * 【这个类的作用】
 * 这个类会在执行 SQL 查询时，自动在 WHERE 条件中添加部门过滤条件，
 * 确保用户只能查询到自己有权限的数据。
 *
 * 【使用前提】
 * 注意，使用 DeptDataPermissionRule 时，需要保证表中有 dept_id 部门编号的字段，可自定义。
 *
 * 【实际业务场景的经典问题】
 * 当用户从 A 部门调到 B 部门时，他之前在 A 部门创建的数据怎么办？
 *
 * 方案1: dept_id 不修改【yudao-server 采用的方案】
 *   - 结果：用户看不到之前在 A 部门创建的数据
 *   - 优点：实现简单，数据归属清晰
 *   - 缺点：用户可能需要访问历史数据时会有问题
 *
 * 方案2: 希望用户还能看到之前的数据，有三种实现方式：
 *
 *  方式1）编写数据迁移脚本，将历史数据的 dept_id 修改成新部门编号【推荐】
 *      - 最终SQL条件: WHERE dept_id = ? (新部门ID)
 *      - 优点：数据一致性好，查询效率高
 *      - 缺点：需要写脚本更新数据
 *
 *  方式2）通过 user_id 过滤，获取该用户创建的所有数据
 *      - 最终SQL条件: WHERE user_id IN (?, ?, ? ...)
 *      - 优点：不需要迁移数据
 *      - 缺点：如果部门人员很多，IN 条件会很长，影响性能
 *
 *  方式3）同时使用 dept_id 和 user_id 过滤
 *      - 最终SQL条件: WHERE dept_id = ? OR user_id IN (?, ?, ? ...)
 *      - 优点：既能看到新部门数据，也能看到自己的历史数据
 *      - 缺点：实现复杂，需要改造本类代码
 *
 * @author 芋道源码
 */
@AllArgsConstructor
@Slf4j
public class DeptDataPermissionRule implements DataPermissionRule {

    /**
     * LoginUser 的 Context 缓存 Key
     *
     * 【作用】用于在用户登录信息中缓存数据权限配置，避免重复查询数据库
     *
     * 举例：用户第一次查询订单时，会从数据库获取他的数据权限（能看哪些部门的数据），
     *      然后把这个权限信息缓存起来。后续查询商品、客户等其他数据时，
     *      就不需要再次查询数据库了，直接用缓存的权限信息即可。
     */
    protected static final String CONTEXT_KEY = DeptDataPermissionRule.class.getSimpleName();

    /**
     * 默认的部门字段名称
     *
     * 【说明】大部分数据表都用 dept_id 来存储部门编号，这是默认值
     */
    private static final String DEPT_COLUMN_NAME = "dept_id";

    /**
     * 默认的用户字段名称
     *
     * 【说明】大部分数据表都用 user_id 来存储创建人/负责人的用户编号，这是默认值
     */
    private static final String USER_COLUMN_NAME = "user_id";

    /**
     * 空表达式，用于表示"查询结果为空"
     *
     * 【使用场景】当用户没有任何数据权限时，返回这个表达式，
     *            会生成 WHERE null = null 这样的条件，确保查询不到任何数据
     */
    static final Expression EXPRESSION_NULL = new NullValue();

    /**
     * 权限API接口
     *
     * 【作用】用于查询用户的数据权限配置（比如：该用户能查看哪些部门的数据）
     */
    private final PermissionCommonApi permissionApi;

    /**
     * 基于部门的表字段配置
     *
     * 【作用】配置每个数据表的部门字段名称
     * 【为什么需要】虽然大部分表用 dept_id，但有些表可能用 department_id 或其他名称
     *
     * 【数据结构】
     * - key：表名（例如：system_user、trade_order）
     * - value：该表的部门字段名（例如：dept_id、department_id）
     *
     * 【举例】
     * {
     *   "system_user": "dept_id",      // 用户表的部门字段是 dept_id
     *   "trade_order": "dept_id",      // 订单表的部门字段是 dept_id
     *   "crm_customer": "owner_dept_id" // 客户表的部门字段是 owner_dept_id（自定义）
     * }
     */
    private final Map<String, String> deptColumns = new HashMap<>();

    /**
     * 基于用户的表字段配置
     *
     * 【作用】配置每个数据表的用户字段名称（通常是创建人或负责人）
     * 【使用场景】当数据权限配置为"只能查看自己的数据"时使用
     *
     * 【数据结构】
     * - key：表名（例如：system_user、trade_order）
     * - value：该表的用户字段名（例如：user_id、creator、owner_user_id）
     *
     * 【举例】
     * {
     *   "trade_order": "user_id",        // 订单表：通过 user_id 判断是否是自己的订单
     *   "crm_customer": "owner_user_id"  // 客户表：通过 owner_user_id 判断是否是自己负责的客户
     * }
     */
    private final Map<String, String> userColumns = new HashMap<>();

    /**
     * 所有需要进行数据权限控制的表名集合
     *
     * 【作用】记录哪些表需要自动添加数据权限过滤条件
     * 【内容】是 {@link #deptColumns} 和 {@link #userColumns} 中所有表名的合集
     *
     * 【举例】如果配置了：
     * - deptColumns 包含: system_user, trade_order
     * - userColumns 包含: trade_order, crm_customer
     * 那么 TABLE_NAMES = {system_user, trade_order, crm_customer}
     */
    private final Set<String> TABLE_NAMES = new HashSet<>();

    /**
     * 获取需要进行数据权限控制的表名集合
     *
     * 【作用】告诉框架哪些表需要自动添加数据权限过滤条件
     *
     * 【执行时机】在 MyBatis 执行 SQL 前，框架会调用这个方法，判断当前查询的表
     *           是否在这个集合中。如果在，就继续调用 getExpression() 方法添加过滤条件
     *
     * @return 所有需要数据权限控制的表名集合
     */
    @Override
    public Set<String> getTableNames() {
        return TABLE_NAMES;
    }

    /**
     * 获取数据权限的 SQL 过滤表达式
     *
     * 【这是核心方法】当执行 SQL 查询时，框架会自动调用这个方法，
     * 在原始 SQL 的 WHERE 条件后面添加数据权限过滤条件
     *
     * 【举例】假设原始SQL是：
     *   SELECT * FROM trade_order WHERE status = 1
     *
     * 经过这个方法处理后，可能变成：
     *   SELECT * FROM trade_order WHERE status = 1 AND dept_id IN (10, 20, 30)
     *
     * 【处理逻辑】
     * 1. 先检查是否有登录用户，没有则不添加过滤条件
     * 2. 检查用户类型，只对管理员类型用户进行数据权限控制
     * 3. 获取用户的数据权限配置（能看哪些部门、能否看自己的数据）
     * 4. 根据配置生成对应的 SQL 过滤条件
     *
     * @param tableName 表名（例如：trade_order）
     * @param tableAlias 表别名（例如：在 SQL 中写 FROM trade_order o，这里就是 o）
     * @return SQL 过滤表达式，会被自动添加到 WHERE 条件中
     *         返回 null 表示不需要添加过滤条件（例如：用户有查看全部数据的权限）
     */
    @Override
    public Expression getExpression(String tableName, Alias tableAlias) {
        // 【步骤1】只有有登陆用户的情况下，才进行数据权限的处理
        // 说明：如果是定时任务、系统初始化等场景，没有登录用户，就不需要数据权限控制
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            return null;
        }

        // 【步骤2】只有管理员类型的用户，才进行数据权限的处理
        // 说明：前台用户（会员）一般不需要数据权限控制，他们只能看到自己的数据
        //      只有后台管理员需要根据部门进行数据权限控制
        if (ObjectUtil.notEqual(loginUser.getUserType(), UserTypeEnum.ADMIN.getValue())) {
            return null;
        }

        // 【步骤3】获得数据权限配置
        // 先从缓存（LoginUser的上下文）中获取，避免重复查询数据库
        DeptDataPermissionRespDTO deptDataPermission = loginUser.getContext(CONTEXT_KEY, DeptDataPermissionRespDTO.class);

        // 从上下文中拿不到，则调用 API 从数据库获取
        if (deptDataPermission == null) {
            deptDataPermission = permissionApi.getDeptDataPermission(loginUser.getId()).getCheckedData();
            if (deptDataPermission == null) {
                // 理论上不应该出现这种情况，如果出现了，记录错误日志并抛出异常
                log.error("[getExpression][LoginUser({}) 获取数据权限为 null]", JsonUtils.toJsonString(loginUser));
                throw new NullPointerException(String.format("LoginUser(%d) Table(%s/%s) 未返回数据权限",
                        loginUser.getId(), tableName, tableAlias.getName()));
            }
            // 添加到上下文中缓存起来，避免重复计算
            // 这样同一个请求中查询多个表时，只需要查询一次数据权限配置
            loginUser.setContext(CONTEXT_KEY, deptDataPermission);
        }

        // 【步骤4】根据数据权限配置，生成不同的 SQL 过滤条件

        // 情况一：如果用户有"查看全部"权限，则无需添加任何过滤条件
        // 举例：公司老板可以看到所有部门的数据
        if (deptDataPermission.getAll()) {
            return null;
        }

        // 情况二：用户既不能查看任何部门，又不能查看自己的数据，说明 100% 无权限
        // 返回 WHERE null = null 这样的条件，确保查询不到任何数据
        // 举例：某个用户被取消了所有数据权限
        if (CollUtil.isEmpty(deptDataPermission.getDeptIds())
                && Boolean.FALSE.equals(deptDataPermission.getSelf())) {
            return new EqualsTo(null, null); // WHERE null = null，可以保证返回的数据为空
        }

        // 情况三：根据部门和用户两个维度拼接过滤条件，最后组合起来

        // 3.1 构建部门维度的条件（例如：dept_id IN (10, 20, 30)）
        Expression deptExpression = buildDeptExpression(tableName,tableAlias, deptDataPermission.getDeptIds());

        // 3.2 构建用户维度的条件（例如：user_id = 100）
        Expression userExpression = buildUserExpression(tableName, tableAlias, deptDataPermission.getSelf(), loginUser.getId());

        // 3.3 如果两个条件都构建失败，说明配置有问题
        if (deptExpression == null && userExpression == null) {
            // TODO 芋艿：获得不到条件的时候，暂时不抛出异常，而是不返回数据
            log.warn("[getExpression][LoginUser({}) Table({}/{}) DeptDataPermission({}) 构建的条件为空]",
                    JsonUtils.toJsonString(loginUser), tableName, tableAlias, JsonUtils.toJsonString(deptDataPermission));
//            throw new NullPointerException(String.format("LoginUser(%d) Table(%s/%s) 构建的条件为空",
//                    loginUser.getId(), tableName, tableAlias.getName()));
            return EXPRESSION_NULL; // 返回空表达式，确保查询不到数据
        }

        // 3.4 如果只有用户条件，直接返回
        // 举例：配置为"只能查看自己的数据"，生成：WHERE user_id = 100
        if (deptExpression == null) {
            return userExpression;
        }

        // 3.5 如果只有部门条件，直接返回
        // 举例：配置为"可以查看销售部和市场部的数据"，生成：WHERE dept_id IN (10, 20)
        if (userExpression == null) {
            return deptExpression;
        }

        // 3.6 如果两个条件都有，使用 OR 连接
        // 举例：配置为"可以查看销售部的数据 + 可以查看自己的数据"
        //      生成：WHERE (dept_id IN (10, 20) OR user_id = 100)
        // 这样即使用户不在销售部，也能看到自己创建的数据
        return new ParenthesedExpressionList(new OrExpression(deptExpression, userExpression));
    }

    /**
     * 构建基于部门的 SQL 过滤表达式
     *
     * 【作用】根据用户有权限查看的部门ID列表，生成 dept_id IN (?, ?, ?) 这样的 SQL 条件
     *
     * 【举例】假设用户可以查看部门 10、20、30 的数据，生成的SQL条件为：
     *   dept_id IN (10, 20, 30)
     *
     * @param tableName 表名（例如：trade_order）
     * @param tableAlias 表别名（例如：o）
     * @param deptIds 用户有权限查看的部门ID集合
     * @return SQL 过滤表达式，如果该表没有配置部门字段或部门ID为空，返回 null
     */
    private Expression buildDeptExpression(String tableName, Alias tableAlias, Set<Long> deptIds) {
        // 检查该表是否配置了部门字段
        // 如果没有配置，说明这个表不需要按部门过滤，返回 null
        String columnName = deptColumns.get(tableName);
        if (StrUtil.isEmpty(columnName)) {
            return null;
        }

        // 如果部门ID集合为空，说明用户没有任何部门权限，返回 null
        // 注意：这里返回 null 不代表没有权限，最终会在 getExpression 方法中综合判断
        if (CollUtil.isEmpty(deptIds)) {
            return null;
        }

        // 拼接 SQL 条件：dept_id IN (10, 20, 30)
        // MyBatisUtils.buildColumn 会处理表别名，生成 o.dept_id 或 trade_order.dept_id
        return new InExpression(MyBatisUtils.buildColumn(tableName, tableAlias, columnName),
                // ParenthesedExpressionList 的目的是提供 (10, 20, 30) 的左右括号
                new ParenthesedExpressionList(new ExpressionList<LongValue>(CollectionUtils.convertList(deptIds, LongValue::new))));
    }

    /**
     * 构建基于用户的 SQL 过滤表达式
     *
     * 【作用】当用户有"只能查看自己数据"的权限时，生成 user_id = ? 这样的 SQL 条件
     *
     * 【使用场景】
     * - 某些业务场景下，即使用户不在某个部门，也应该能看到自己创建的数据
     * - 例如：销售人员离职转到行政部，但仍需要查看自己之前跟进的客户
     *
     * 【举例】假设当前登录用户ID是 100，生成的SQL条件为：
     *   user_id = 100
     *
     * @param tableName 表名（例如：trade_order）
     * @param tableAlias 表别名（例如：o）
     * @param self 是否允许查看自己的数据（true=允许，false=不允许）
     * @param userId 当前登录用户的ID
     * @return SQL 过滤表达式，如果不允许查看自己的数据或该表没有配置用户字段，返回 null
     */
    private Expression buildUserExpression(String tableName, Alias tableAlias, Boolean self, Long userId) {
        // 如果数据权限配置为"不能查看自己的数据"，直接返回 null
        if (Boolean.FALSE.equals(self)) {
            return null;
        }

        // 检查该表是否配置了用户字段
        // 如果没有配置，说明这个表不支持按用户过滤，返回 null
        String columnName = userColumns.get(tableName);
        if (StrUtil.isEmpty(columnName)) {
            return null;
        }

        // 拼接 SQL 条件：user_id = 100
        // MyBatisUtils.buildColumn 会处理表别名，生成 o.user_id 或 trade_order.user_id
        return new EqualsTo(MyBatisUtils.buildColumn(tableName, tableAlias, columnName), new LongValue(userId));
    }

    // ==================== 添加配置 ====================
    // 以下方法用于配置哪些表需要数据权限控制，以及这些表的部门字段和用户字段名称

    /**
     * 添加部门字段配置（使用默认字段名 dept_id）
     *
     * 【使用场景】当数据表的部门字段就是 dept_id 时，使用这个方法
     *
     * 【举例】
     * rule.addDeptColumn(TradeOrderDO.class);
     * // 效果：trade_order 表会使用 dept_id 字段进行部门过滤
     *
     * @param entityClass 实体类（例如：TradeOrderDO.class）
     */
    public void addDeptColumn(Class<? extends BaseDO> entityClass) {
        addDeptColumn(entityClass, DEPT_COLUMN_NAME);
    }

    /**
     * 添加部门字段配置（自定义字段名）
     *
     * 【使用场景】当数据表的部门字段不是 dept_id，而是其他名称时，使用这个方法
     *
     * 【举例】
     * rule.addDeptColumn(CrmCustomerDO.class, "owner_dept_id");
     * // 效果：crm_customer 表会使用 owner_dept_id 字段进行部门过滤
     *
     * @param entityClass 实体类（例如：CrmCustomerDO.class）
     * @param columnName 自定义的部门字段名（例如：owner_dept_id）
     */
    public void addDeptColumn(Class<? extends BaseDO> entityClass, String columnName) {
        // 从实体类获取对应的数据表名
        String tableName = TableInfoHelper.getTableInfo(entityClass).getTableName();
        addDeptColumn(tableName, columnName);
    }

    /**
     * 添加部门字段配置（直接指定表名和字段名）
     *
     * 【使用场景】当没有实体类，需要直接配置表名时使用
     *
     * 【举例】
     * rule.addDeptColumn("trade_order", "dept_id");
     *
     * @param tableName 表名（例如：trade_order）
     * @param columnName 部门字段名（例如：dept_id）
     */
    public void addDeptColumn(String tableName, String columnName) {
        // 将配置保存到 deptColumns 中
        deptColumns.put(tableName, columnName);
        // 同时添加到 TABLE_NAMES 中，表示这个表需要数据权限控制
        TABLE_NAMES.add(tableName);
    }

    /**
     * 添加用户字段配置（使用默认字段名 user_id）
     *
     * 【使用场景】当数据表的用户字段就是 user_id 时，使用这个方法
     *
     * 【举例】
     * rule.addUserColumn(TradeOrderDO.class);
     * // 效果：trade_order 表会使用 user_id 字段进行用户过滤（查看自己的数据）
     *
     * @param entityClass 实体类（例如：TradeOrderDO.class）
     */
    public void addUserColumn(Class<? extends BaseDO> entityClass) {
        addUserColumn(entityClass, USER_COLUMN_NAME);
    }

    /**
     * 添加用户字段配置（自定义字段名）
     *
     * 【使用场景】当数据表的用户字段不是 user_id，而是其他名称时，使用这个方法
     *
     * 【举例】
     * rule.addUserColumn(CrmCustomerDO.class, "owner_user_id");
     * // 效果：crm_customer 表会使用 owner_user_id 字段进行用户过滤
     *
     * @param entityClass 实体类（例如：CrmCustomerDO.class）
     * @param columnName 自定义的用户字段名（例如：owner_user_id、creator）
     */
    public void addUserColumn(Class<? extends BaseDO> entityClass, String columnName) {
        // 从实体类获取对应的数据表名
        String tableName = TableInfoHelper.getTableInfo(entityClass).getTableName();
        addUserColumn(tableName, columnName);
    }

    /**
     * 添加用户字段配置（直接指定表名和字段名）
     *
     * 【使用场景】当没有实体类，需要直接配置表名时使用
     *
     * 【举例】
     * rule.addUserColumn("trade_order", "user_id");
     *
     * @param tableName 表名（例如：trade_order）
     * @param columnName 用户字段名（例如：user_id、creator、owner_user_id）
     */
    public void addUserColumn(String tableName, String columnName) {
        // 将配置保存到 userColumns 中
        userColumns.put(tableName, columnName);
        // 同时添加到 TABLE_NAMES 中，表示这个表需要数据权限控制
        TABLE_NAMES.add(tableName);
    }

}
