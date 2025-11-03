package cn.iocoder.yudao.module.bpm.dal.redis;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;

import static cn.hutool.core.date.DatePattern.*;

/**
 * BPM 流程 ID 编码的 Redis DAO（数据访问对象）。
 * <p>
 * 本类利用 Redis 的原子性自增特性，按用户配置的规则生成全局唯一、带时间语义的流程编号（如：ORDER202511030001）。
 * 主要用于在高并发场景下安全地生成递增流水号，避免数据库自增 ID 的性能瓶颈或分布式环境下的冲突。
 * </p>
 *
 * @author Lesan
 */
@Repository
public class BpmProcessIdRedisDAO {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据指定的流程 ID 生成规则（{@link BpmModelMetaInfoVO.ProcessIdRule}），生成一个唯一的流程编号。
     * <p>
     * 生成逻辑分为两步：
     * 1. 构造编号前缀（含用户定义的固定前缀 + 动态时间中缀 + 固定后缀）；
     * 2. 使用 Redis 的 INCR 命令对该前缀对应的 key 执行原子自增，获得当前序列号，并格式化为固定长度数字。
     * </p>
     * <p>
     * 示例：
     * - 规则：prefix="ORDER_", infix="DAY", postfix="_BJ", length=4
     * - 当前日期：2025-11-03
     * - 生成结果可能为：ORDER_20251103_BJ0001、ORDER_20251103_BJ0002 ...
     * </p>
     *
     * @param processIdRule 流程 ID 生成规则，包含前缀、时间粒度（中缀）、后缀、序列号长度等信息
     * @return 按规则生成的完整流程 ID 字符串
     */
    public String generate(BpmModelMetaInfoVO.ProcessIdRule processIdRule) {
        // Step 1: 根据规则中的时间粒度（infix）生成动态时间字符串（作为编号中缀）
        String infix = "";
        switch (processIdRule.getInfix()) {
            case "DAY":
                // 仅包含年月日，格式如：20251103
                infix = DateUtil.format(LocalDateTime.now(), PURE_DATE_PATTERN);
                break;
            case "HOUR":
                // 包含年月日+小时，格式如：2025110315
                infix = DateUtil.format(LocalDateTime.now(), PURE_DATE_PATTERN + "HH");
                break;
            case "MINUTE":
                // 包含年月日+时分，格式如：202511031530
                infix = DateUtil.format(LocalDateTime.now(), PURE_DATE_PATTERN + "HHmm");
                break;
            case "SECOND":
                // 包含年月日时分秒，格式如：20251103153045
                infix = DateUtil.format(LocalDateTime.now(), PURE_DATETIME_PATTERN);
                break;
            // 注意：若 infix 为空或未知，默认 infix 保持为空字符串
        }

        // Step 2: 拼接 Redis key 的公共前缀部分（不含序列号）
        // 格式为：{固定前缀}{时间中缀}{固定后缀}
        String noPrefix = processIdRule.getPrefix() + infix + processIdRule.getPostfix();
        // 完整 Redis key：BPM_PROCESS_ID:{noPrefix}
        String key = RedisKeyConstants.BPM_PROCESS_ID + noPrefix;

        // Step 3: 使用 Redis 原子自增获取当前序列号（从 1 开始）
        Long no = stringRedisTemplate.opsForValue().increment(key);

        // Step 4: 设置 Redis key 的过期时间（仅当日/小时等带时间中缀时才设过期）
        // 特殊逻辑：如果 infix 为空（即编号不包含时间信息），则不能设置过期时间，
        // 否则每次重启或过期后序列号会重置为 1，导致 ID 重复。
        // 参见讨论：https://t.zsxq.com/MU1E2
        if (StrUtil.isNotEmpty(infix)) {
            // 带时间中缀的 key，通常只需保留一天（例如 DAY 粒度），避免 Redis 内存无限增长
            stringRedisTemplate.expire(key, Duration.ofDays(1L));
        }
        // 注意：若 infix 为空（即全局连续编号），则 key 永不过期，确保序列号持续递增

        // Step 5: 将序列号格式化为固定长度（不足位补 0），并拼接到前缀后返回完整 ID
        // 例如：length=4, no=5 → "0005"
        return noPrefix + String.format("%0" + processIdRule.getLength() + "d", no);
    }

}