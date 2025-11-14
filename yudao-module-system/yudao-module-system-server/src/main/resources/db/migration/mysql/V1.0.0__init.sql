-- ----------------------------
-- Table structure for system_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS `system_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(100) NOT NULL COMMENT '角色权限字符串',
    `sort` INT NOT NULL COMMENT '显示顺序',
    `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '数据范围(1=全部,2=自定义,3=本部门,4=本部门及以下,5=仅本人)',
    `data_scope_dept_ids` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '数据范围(指定部门数组)',
    `status` TINYINT NOT NULL COMMENT '角色状态(0=正常,1=停用)',
    `type` TINYINT NOT NULL COMMENT '角色类型(1=内置,2=自定义)',
    `remark` VARCHAR(500) NULL COMMENT '备注',
    `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`, `tenant_id`, `deleted`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- ----------------------------
-- Table structure for system_menu
-- ----------------------------
CREATE TABLE IF NOT EXISTS `system_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `name` VARCHAR(50) NOT NULL COMMENT '菜单名称',
    `permission` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '权限标识',
    `type` TINYINT NOT NULL COMMENT '菜单类型(1=目录,2=菜单,3=按钮)',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单ID',
    `path` VARCHAR(200) DEFAULT '' COMMENT '路由地址',
    `icon` VARCHAR(100) DEFAULT '#' COMMENT '菜单图标',
    `component` VARCHAR(255) NULL COMMENT '组件路径',
    `component_name` VARCHAR(255) NULL COMMENT '组件名',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '菜单状态(0=正常,1=停用)',
    `visible` BIT(1) NOT NULL DEFAULT 1 COMMENT '是否可见',
    `keep_alive` BIT(1) NOT NULL DEFAULT 1 COMMENT '是否缓存',
    `always_show` BIT(1) NOT NULL DEFAULT 1 COMMENT '是否总是显示',
    `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单表';

-- ----------------------------
-- Table structure for system_user_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS `system_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户和角色关联表';

-- ----------------------------
-- Table structure for system_role_menu
-- ----------------------------
CREATE TABLE IF NOT EXISTS `system_role_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增编号',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    `creator` VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` BIT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色和菜单关联表';

