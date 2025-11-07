package cn.iocoder.yudao.framework.datapermission.core.db;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRule;
import cn.iocoder.yudao.framework.datapermission.core.rule.DataPermissionRuleFactory;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.schema.Table;

import java.util.List;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.skipPermissionCheck;

/**
 * 基于 {@link DataPermissionRule} 的数据权限处理器
 *
 * 它的底层，是基于 MyBatis Plus 的 <a href="https://baomidou.com/plugins/data-permission/">数据权限插件</a>
 * 核心原理：它会在 SQL 执行前拦截 SQL 语句，并根据用户权限动态添加权限相关的 SQL 片段。这样，只有用户有权限访问的数据才会被查询出来
 *
 * 【通俗解释】
 * 这个类就像一个"SQL守门员"，在SQL真正执行之前，它会自动给SQL加上权限过滤条件。
 * 比如：原始SQL是 "SELECT * FROM user"
 * 经过处理后变成 "SELECT * FROM user WHERE dept_id IN (1,2,3)" (假设用户只能看部门1,2,3的数据)
 * 这样就实现了数据权限控制，用户只能看到自己有权限的数据
 *
 * @author 芋道源码
 */
@RequiredArgsConstructor // Lombok注解：自动生成包含final字段的构造函数
public class DataPermissionRuleHandler implements MultiDataPermissionHandler {

    // 数据权限规则工厂：用于获取和管理各种数据权限规则
    // 比如：部门权限规则、角色权限规则等
    private final DataPermissionRuleFactory ruleFactory;

    /**
     * 获取SQL片段：为SQL语句动态添加数据权限过滤条件
     *
     * 【方法作用】
     * 这是数据权限的核心方法，每次执行SQL查询时都会被调用。
     * 它会根据当前用户的权限，动态生成WHERE条件，确保用户只能查询到有权限的数据。
     *
     * @param table 当前SQL操作的表对象（包含表名、别名等信息）
     * @param where 原始SQL的WHERE条件（可能为null）
     * @param mappedStatementId Mapper方法的唯一标识（如：com.example.mapper.UserMapper.selectList）
     * @return 返回需要添加的权限过滤条件；如果返回null，表示不需要添加权限过滤
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // ========== 第一步：检查是否需要跳过权限检查 ==========
        // 特殊情况：如果是跨租户访问或管理员操作，则跳过权限检查
        // 例如：系统管理员查询所有数据、定时任务批量处理数据等场景
        if (skipPermissionCheck()) {
            return null; // 返回null表示不添加任何权限过滤条件
        }

        // ========== 第二步：获取适用于当前Mapper方法的数据权限规则 ==========
        // 根据mappedStatementId（如：UserMapper.selectList）查找对应的权限规则
        // 一个Mapper方法可能有多个权限规则（比如既要过滤部门，又要过滤数据范围）
        List<DataPermissionRule> rules = ruleFactory.getDataPermissionRule(mappedStatementId);
        if (CollUtil.isEmpty(rules)) {
            return null; // 如果没有配置权限规则，则不添加过滤条件
        }

        // ========== 第三步：遍历所有权限规则，生成过滤条件 ==========
        Expression allExpression = null; // 用于存储最终合并后的所有权限条件
        for (DataPermissionRule rule : rules) {
            // 3.1 判断当前规则是否适用于这张表
            // 获取表名（如果SQL中使用了别名，也能正确获取真实表名）
            String tableName = MyBatisUtils.getTableName(table);
            if (!rule.getTableNames().contains(tableName)) {
                continue; // 如果规则不适用于当前表，跳过该规则
            }

            // 3.2 生成单条规则的SQL过滤条件
            // 例如：部门权限规则可能生成 "dept_id IN (1,2,3)"
            //      数据范围规则可能生成 "creator = '张三'"
            Expression oneExpress = rule.getExpression(tableName, table.getAlias());
            if (oneExpress == null) {
                continue; // 如果规则没有生成条件（可能因为用户有全部权限），跳过
            }

            // 3.3 将单条规则的条件合并到总条件中
            // 使用AND连接多个条件，例如：
            // 第一个规则：dept_id IN (1,2,3)
            // 第二个规则：creator = '张三'
            // 合并后：dept_id IN (1,2,3) AND creator = '张三'
            allExpression = allExpression == null ? oneExpress
                    : new AndExpression(allExpression, oneExpress);
        }

        // ========== 第四步：返回最终的权限过滤条件 ==========
        // 这个条件会被自动拼接到原始SQL的WHERE子句中
        return allExpression;
    }

}
