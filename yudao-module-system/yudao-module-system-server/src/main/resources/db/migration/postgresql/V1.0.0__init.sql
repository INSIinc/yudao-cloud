-- PostgreSQL 数据库初始化脚本
-- 注意：这是一个示例文件，实际应包含完整的表结构定义

-- ----------------------------
-- Table structure for system_role_menu
-- ----------------------------
-- 假设表已经存在，这里只添加注释和索引
COMMENT ON TABLE "system_role_menu" IS '角色和菜单关联表';

CREATE INDEX IF NOT EXISTS "idx_role_id_menu" ON "system_role_menu" ("role_id");
CREATE INDEX IF NOT EXISTS "idx_menu_id" ON "system_role_menu" ("menu_id");

