package cn.iocoder.yudao.framework.ip.core.utils;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.iocoder.yudao.framework.common.util.object.ObjectUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.ip.core.enums.AreaTypeEnum;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.findFirst;

/**
 * 区域工具类
 *
 * 功能说明：
 * 1. 从 CSV 文件加载全国地区数据（省、市、区）
 * 2. 构建地区的树形结构（父子关系）
 * 3. 提供地区查询、格式化、路径解析等功能
 *
 * @author 芋道源码
 */
@Slf4j
public class AreaUtils {

    /**
     * 初始化 INSTANCE 实例
     * 使用静态初始化，确保类加载时就创建唯一实例
     */
    @SuppressWarnings("InstantiationOfUtilityClass")
    private final static AreaUtils INSTANCE = new AreaUtils();

    /**
     * Area 内存缓存，提升访问速度
     * Key: 区域ID（例如：110000 代表北京市）
     * Value: Area 对象（包含区域名称、类型、父子关系等信息）
     */
    private static Map<Integer, Area> areas;

    /**
     * 私有构造函数，在类加载时执行初始化逻辑
     *
     * 初始化步骤：
     * 1. 创建全球根节点
     * 2. 从 area.csv 文件读取所有地区数据
     * 3. 将地区数据加载到内存 Map 中
     * 4. 构建地区之间的父子关系（树形结构）
     */
    private AreaUtils() {
        long now = System.currentTimeMillis();
        // 初始化 areas 缓存 Map
        areas = new HashMap<>();
        // 添加全球根节点（ID=0），作为所有国家的父节点
        areas.put(Area.ID_GLOBAL, new Area(Area.ID_GLOBAL, "全球", 0,
                null, new ArrayList<>()));

        // 从 resources ��录下的 area.csv 文件中读取地区数据
        List<CsvRow> rows = CsvUtil.getReader().read(ResourceUtil.getUtf8Reader("area.csv")).getRows();
        rows.remove(0); // 删除 CSV 文件的表头行（header）

        // 第一次遍历：创建所有 Area 对象并放入 Map
        for (CsvRow row : rows) {
            // CSV 格式：区域ID, 区域名称, 区域类型, 父区域ID
            // 创建 Area 对象（此时还没有设置父子关系）
            Area area = new Area(Integer.valueOf(row.get(0)), row.get(1), Integer.valueOf(row.get(2)),
                    null, new ArrayList<>());
            // 将 Area 对象添加到缓存 Map 中
            areas.put(area.getId(), area);
        }

        // 第二次遍历：构建父子关系
        // 为什么需要两次遍历？因为第一次遍历时，父节点可能还没有创建
        for (CsvRow row : rows) {
            Area area = areas.get(Integer.valueOf(row.get(0))); // 获取当前区域对象
            Area parent = areas.get(Integer.valueOf(row.get(3))); // 获取父区域对象
            // 断言：父子节点不能相同（数据验证）
            Assert.isTrue(area != parent, "{}:父子节点相同", area.getName());
            // 设置当前区域的父节点
            area.setParent(parent);
            // 将当前区域添加到父节点的子节点列表中
            parent.getChildren().add(area);
        }
        log.info("启动加载 AreaUtils 成功，耗时 ({}) 毫秒", System.currentTimeMillis() - now);
    }

    /**
     * 获得指定编号对应的区域
     *
     * 使用示例：
     * Area area = AreaUtils.getArea(110000); // 获取北京市的区域信息
     *
     * @param id 区域编号（例如：110000 代表北京市）
     * @return 区域对象，如果不存在则返回 null
     */
    public static Area getArea(Integer id) {
        return areas.get(id);
    }

    /**
     * 获得指定区域对应的编号
     *
     * 根据区域路径字符串解析出对应的 Area 对象
     * 解析逻辑：从左到右依次匹配每一级地区名称
     *
     * 使用示例：
     * Area area = AreaUtils.parseArea("河南省/石家庄市/新华区");
     * 返回：新华区对应的 Area 对象
     *
     * @param pathStr 区域路径，格式：省/市/区（例如：河南省/石家庄市/新华区）
     * @return 区域对象，如果路径不存在则返回 null
     */
    public static Area parseArea(String pathStr) {
        // 用 "/" 分割路径字符串，得到各级地区名称
        String[] paths = pathStr.split("/");
        Area area = null;
        // 逐级查找匹配的地区
        for (String path : paths) {
            if (area == null) {
                // 第一级：从所有地区中查找（通常是省或国家）
                area = findFirst(areas.values(), item -> item.getName().equals(path));
            } else {
                // 后续级别：从上一级的子节点中查找
                area = findFirst(area.getChildren(), item -> item.getName().equals(path));
            }
        }
        return area;
    }

    /**
     * 获取所有节点的全路径名称
     *
     * 将地区树转换为路径字符串列表，格式：省/市/区
     *
     * 使用示例：
     * List<Area> areaList = ...; // 某个地区树
     * List<String> paths = AreaUtils.getAreaNodePathList(areaList);
     * 结果可能包含：["河南省", "河南省/石家庄市", "河南省/石家庄市/新华区", ...]
     *
     * @param areas 地区树（可以是多个根节点）
     * @return 所有节点的全路径名称列表
     */
    public static List<String> getAreaNodePathList(List<Area> areas) {
        List<String> paths = new ArrayList<>();
        // 遍历每个地区树的根节点，递归构建路径
        areas.forEach(area -> getAreaNodePathList(area, "", paths));
        return paths;
    }

    /**
     * 构建一棵树的所有节点的全路径名称，并将其存储为 "祖先/父级/子级" 的形式
     *
     * 这是一个递归方法，深度优先遍历地区树
     *
     * 递归过程示例：
     * 1. 访问 "河南省"，路径 = "河南省"
     * 2. 访问 "石家庄市"，路径 = "河南省/石家庄市"
     * 3. 访问 "新华区"，路径 = "河南省/石家庄市/新华区"
     *
     * @param node  当前节点
     * @param path  从根节点到当前节点父节点的全路径
     * @param paths 全路径名称列表（结果容器），格式：省份/城市/地区
     */
    private static void getAreaNodePathList(Area node, String path, List<String> paths) {
        if (node == null) {
            return;
        }
        // 构建当前节点的完整路径
        // 如果 path 为空，说明是根节点，直接使用节点名称
        // 否则，在父路径后面追加 "/" 和当前节点名称
        String currentPath = path.isEmpty() ? node.getName() : path + "/" + node.getName();
        // 将当前节点的完整路径添加到结果列表
        paths.add(currentPath);

        // 递归遍历当前节点的所有子节点
        for (Area child : node.getChildren()) {
            getAreaNodePathList(child, currentPath, paths);
        }
    }

    /**
     * 格式化区域（使用默认分隔符：空格）
     *
     * 使用示例：
     * String result = AreaUtils.format(310101); // 返回："上海 上海市 黄浦区"
     *
     * @param id 区域编号
     * @return 格式化后的区域字符串
     */
    public static String format(Integer id) {
        return format(id, " ");
    }

    /**
     * 格式化区域（自定义分隔符）
     *
     * 将区域ID转换为易读的字符串格式，从下到上显示层级关系
     *
     * 格式化示例：
     * 1. id = 310101（黄浦区）：上海 上海市 黄浦区
     * 2. id = 310000（上海市）：上海 上海市
     * 3. id = 100000（北京）：北京
     * 4. id = 200000（美国）：美国
     *
     * 特殊规则：当区域在中国时，默认不显示"中国"二字
     *
     * @param id        区域编号
     * @param separator 分隔符（例如：" "、"-"、"/"）
     * @return 格式化后的区域字符串，如果区域不存在则返回 null
     */
    public static String format(Integer id, String separator) {
        // 根据ID获取区域对象
        Area area = areas.get(id);
        if (area == null) {
            return null;
        }

        // 使用 StringBuilder 构建格式化字符串
        StringBuilder sb = new StringBuilder();
        // 从当前区域向上遍历到根节点（最多遍历所有区域类型的层级数）
        for (int i = 0; i < AreaTypeEnum.values().length; i++) { // 避免死循环
            // 在字符串开头插入当前区域名称（这样最终顺序是：省 市 区）
            sb.insert(0, area.getName());
            // "递归"到父节点
            area = area.getParent();
            // 终止条件：
            // 1. 没有父节点了（到达根节点）
            // 2. 父节点是"全球"或"中国"（跳过这两个节点）
            if (area == null
                    || ObjectUtils.equalsAny(area.getId(), Area.ID_GLOBAL, Area.ID_CHINA)) {
                break;
            }
            // 在开头插入分隔符
            sb.insert(0, separator);
        }
        return sb.toString();
    }

    /**
     * 获取指定类型的区域列表
     *
     * 根据区域类型（省、市、区）筛选并转换区域数据
     *
     * 使用示例：
     * // 获取所有省份的ID列表
     * List<Integer> provinceIds = AreaUtils.getByType(AreaTypeEnum.PROVINCE, Area::getId);
     * // 获取所有城市的名称列表
     * List<String> cityNames = AreaUtils.getByType(AreaTypeEnum.CITY, Area::getName);
     *
     * @param type 区域类型（省、市、区等）
     * @param func 转换函数，将 Area 对象转换为需要的类型
     * @param <T>  返回结果的类型
     * @return 指定类型的区域列表
     */
    public static <T> List<T> getByType(AreaTypeEnum type, Function<Area, T> func) {
        // convertList 方法：遍历 areas.values()，应用转换函数 func，并根据条件过滤
        // 过滤条件：区域的类型等于指定的 type
        return convertList(areas.values(), func, area -> type.getType().equals(area.getType()));
    }

    /**
     * 根据区域编号、上级区域类型，获取上级区域编号
     *
     * 向上查找指定类型的祖先区域
     *
     * 使用示例：
     * Integer provinceId = AreaUtils.getParentIdByType(310101, AreaTypeEnum.PROVINCE);
     * // 从黄浦区（310101）向上查找，返回上海市所属的省份ID
     *
     * @param id   区域编号（起始区域）
     * @param type 要查找的上级区域类型（不能为 null）
     * @return 上级区域编号，如果找不到则返回 null
     */
    public static Integer getParentIdByType(Integer id, @NonNull AreaTypeEnum type) {
        // 最多向上查找 Byte.MAX_VALUE（127）层，避免死循环
        for (int i = 0; i < Byte.MAX_VALUE; i++) {
            // 根据ID获取当前区域对象
            Area area = AreaUtils.getArea(id);
            if (area == null) {
                return null; // 区域不存在
            }
            // 情况一：当前区域的类型匹配目标类型，返回当前区域ID
            if (type.getType().equals(area.getType())) {
                return area.getId();
            }
            // 情况二：已经到达根节点（没有父节点），返回 null
            if (area.getParent() == null || area.getParent().getId() == null) {
                return null;
            }
            // 情况三：继续向上查找，更新 id 为父节点ID
            id = area.getParent().getId();
        }
        return null; // 超过最大查找层数，返回 null
    }

}
