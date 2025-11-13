COMMENT ON TABLE "system_role_menu" IS '角色和菜单关联表';

CREATE INDEX IF NOT EXISTS "idx_role_id_menu" ON "system_role_menu" ("role_id");
CREATE INDEX IF NOT EXISTS "idx_menu_id" ON "system_role_menu" ("menu_id");

