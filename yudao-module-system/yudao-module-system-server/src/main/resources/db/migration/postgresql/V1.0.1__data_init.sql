-- ----------------------------
-- Records of system_mail_account
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_mail_account (id, mail, username, password, host, port, ssl_enable, starttls_enable, creator, create_time, updater, update_time, deleted) VALUES (1, '7684413@qq.com', '7684413@qq.com', '1234576', '127.0.0.1', 8080, '0', '0', '1', '2023-01-25 17:39:52', '1', '2025-04-04 16:34:40', '0');
INSERT INTO system_mail_account (id, mail, username, password, host, port, ssl_enable, starttls_enable, creator, create_time, updater, update_time, deleted) VALUES (2, 'ydym_test@163.com', 'ydym_test@163.com', 'WBZTEINMIFVRYSOE', 'smtp.163.com', 465, '1', '0', '1', '2023-01-26 01:26:03', '1', '2023-04-12 22:39:38', '0');
INSERT INTO system_mail_account (id, mail, username, password, host, port, ssl_enable, starttls_enable, creator, create_time, updater, update_time, deleted) VALUES (3, '76854114@qq.com', '3335', '11234', 'yunai1.cn', 466, '0', '0', '1', '2023-01-27 15:06:38', '1', '2023-01-27 07:08:36', '1');
INSERT INTO system_mail_account (id, mail, username, password, host, port, ssl_enable, starttls_enable, creator, create_time, updater, update_time, deleted) VALUES (4, '7685413x@qq.com', '2', '3', '4', 5, '1', '0', '1', '2023-04-12 23:05:06', '1', '2023-04-12 15:05:11', '1');
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_mail_template
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_mail_template (id, name, code, account_id, nickname, title, content, params, status, remark, creator, create_time, updater, update_time, deleted) VALUES (13, '后台用户短信登录', 'admin-sms-login', 1, '奥特曼', '你猜我猜', '<p>您的验证码是{code}，名字是{name}</p>', '["code","name"]', 0, '3', '1', '2021-10-11 08:10:00', '1', '2023-12-02 19:51:14', '0');
INSERT INTO system_mail_template (id, name, code, account_id, nickname, title, content, params, status, remark, creator, create_time, updater, update_time, deleted) VALUES (14, '测试模版', 'test_01', 2, '芋艿', '一个标题', '<p>你是 {key01} 吗？</p><p><br></p><p>是的话，赶紧 {key02} 一下！</p>', '["key01","key02"]', 0, NULL, '1', '2023-01-26 01:27:40', '1', '2023-01-27 10:32:16', '0');
INSERT INTO system_mail_template (id, name, code, account_id, nickname, title, content, params, status, remark, creator, create_time, updater, update_time, deleted) VALUES (15, '3', '2', 2, '7', '4', '<p>45</p>', '[]', 1, '80', '1', '2023-01-27 15:50:35', '1', '2023-01-27 16:34:49', '0');
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_sms_channel
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_sms_channel (id, signature, code, status, remark, api_key, api_secret, callback_url, creator, create_time, updater, update_time, deleted) VALUES (2, 'Ballcat', 'ALIYUN', 0, '你要改哦，只有我可以用！！！！', 'LTAI5tCnKso2uG3kJ5gRav88', 'fGJ5SNXL7P1NHNRmJ7DJaMJGPyE55C', NULL, '', '2021-03-31 11:53:10', '1', '2024-08-04 08:53:26', '0');
INSERT INTO system_sms_channel (id, signature, code, status, remark, api_key, api_secret, callback_url, creator, create_time, updater, update_time, deleted) VALUES (4, '测试渠道', 'DEBUG_DING_TALK', 0, '123', '696b5d8ead48071237e4aa5861ff08dbadb2b4ded1c688a7b7c9afc615579859', 'SEC5c4e5ff888bc8a9923ae47f59e7ccd30af1f14d93c55b4e2c9cb094e35aeed67', NULL, '1', '2021-04-13 00:23:14', '1', '2022-03-27 20:29:49', '0');
INSERT INTO system_sms_channel (id, signature, code, status, remark, api_key, api_secret, callback_url, creator, create_time, updater, update_time, deleted) VALUES (7, 'mock腾讯云', 'TENCENT', 0, '', '1 2', '2 3', '', '1', '2024-09-30 08:53:45', '1', '2024-09-30 08:55:01', '0');
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_sms_template
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (2, 1, 0, 'test_01', '测试验证码短信', '正在进行登录操作{operation}，您的验证码是{code}', '["operation","code"]', '测试备注', '4383920', 4, 'DEBUG_DING_TALK', '', '2021-03-31 10:49:38', '1', '2024-08-18 11:57:18', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (3, 1, 0, 'test_02', '公告通知', '您的验证码{code}，该验证码5分钟内有效，请勿泄漏于他人！', '["code"]', NULL, 'SMS_207945135', 2, 'ALIYUN', '', '2021-03-31 11:56:30', '1', '2021-04-10 01:22:02', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (6, 3, 0, 'test-01', '测试模板', '哈哈哈 {name}', '["name"]', 'f哈哈哈', '4383920', 4, 'DEBUG_DING_TALK', '1', '2021-04-10 01:07:21', '1', '2024-08-18 11:57:07', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (7, 3, 0, 'test-04', '测试下', '老鸡{name}，牛逼{code}', '["name","code"]', '哈哈哈哈', 'suibian', 7, 'DEBUG_DING_TALK', '1', '2021-04-13 00:29:53', '1', '2024-09-30 00:56:24', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (8, 1, 0, 'user-sms-login', '前台用户短信登录', '您的验证码是{code}', '["code"]', NULL, '4372216', 4, 'DEBUG_DING_TALK', '1', '2021-10-11 08:10:00', '1', '2024-08-18 11:57:06', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (9, 2, 0, 'bpm_task_assigned', '【工作流】任务被分配', '您收到了一条新的待办任务：{processInstanceName}-{taskName}，申请人：{startUserNickname}，处理链接：{detailUrl}', '["processInstanceName","taskName","startUserNickname","detailUrl"]', NULL, 'suibian', 4, 'DEBUG_DING_TALK', '1', '2022-01-21 22:31:19', '1', '2022-01-22 00:03:36', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (10, 2, 0, 'bpm_process_instance_reject', '【工作流】流程被不通过', '您的流程被审批不通过：{processInstanceName}，原因：{reason}，查看链接：{detailUrl}', '["processInstanceName","reason","detailUrl"]', NULL, 'suibian', 4, 'DEBUG_DING_TALK', '1', '2022-01-22 00:03:31', '1', '2022-05-01 12:33:14', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (11, 2, 0, 'bpm_process_instance_approve', '【工作流】流程被通过', '您的流程被审批通过：{processInstanceName}，查看链接：{detailUrl}', '["processInstanceName","detailUrl"]', NULL, 'suibian', 4, 'DEBUG_DING_TALK', '1', '2022-01-22 00:04:31', '1', '2022-03-27 20:32:21', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (12, 2, 0, 'demo', '演示模板', '我就是测试一下下', '[]', NULL, 'biubiubiu', 4, 'DEBUG_DING_TALK', '1', '2022-04-10 23:22:49', '1', '2024-08-18 11:57:04', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (14, 1, 0, 'user-update-mobile', '会员用户 - 修改手机', '您的验证码{code}，该验证码 5 分钟内有效，请勿泄漏于他人！', '["code"]', '', 'null', 4, 'DEBUG_DING_TALK', '1', '2023-08-19 18:58:01', '1', '2023-08-19 11:34:04', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (15, 1, 0, 'user-update-password', '会员用户 - 修改密码', '您的验证码{code}，该验证码 5 分钟内有效，请勿泄漏于他人！', '["code"]', '', 'null', 4, 'DEBUG_DING_TALK', '1', '2023-08-19 18:58:01', '1', '2023-08-19 11:34:18', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (16, 1, 0, 'user-reset-password', '会员用户 - 重置密码', '您的验证码{code}，该验证码 5 分钟内有效，请勿泄漏于他人！', '["code"]', '', 'null', 4, 'DEBUG_DING_TALK', '1', '2023-08-19 18:58:01', '1', '2023-12-02 22:35:27', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (17, 2, 0, 'bpm_task_timeout', '【工作流】任务审批超时', '您收到了一条超时的待办任务：{processInstanceName}-{taskName}，处理链接：{detailUrl}', '["processInstanceName","taskName","detailUrl"]', '', 'X', 4, 'DEBUG_DING_TALK', '1', '2024-08-16 21:59:15', '1', '2024-08-16 21:59:34', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (18, 1, 0, 'admin-reset-password', '后台用户 - 忘记密码', '您的验证码{code}，该验证码 5 分钟内有效，请勿泄漏于他人！', '["code"]', '', 'null', 4, 'DEBUG_DING_TALK', '1', '2025-03-16 14:19:34', '1', '2025-03-16 14:19:45', '0');
INSERT INTO system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) VALUES (19, 1, 0, 'admin-sms-login', '后台用户短信登录', '您的验证码是{code}', '["code"]', '', '4372216', 4, 'DEBUG_DING_TALK', '1', '2025-04-08 09:36:03', '1', '2025-04-08 09:36:17', '0');
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_social_client
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_social_client (id, name, social_type, user_type, client_id, client_secret, agent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1, '钉钉', 20, 2, 'dingvrnreaje3yqvzhxg', 'i8E6iZyDvZj51JIb0tYsYfVQYOks9Cq1lgryEjFRqC79P3iJcrxEwT6Qk2QvLrLI', NULL, 0, '', '2023-10-18 11:21:18', '1', '2023-12-20 21:28:26', '1', 1);
INSERT INTO system_social_client (id, name, social_type, user_type, client_id, client_secret, agent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (2, '钉钉（王土豆）', 20, 2, 'dingtsu9hpepjkbmthhw', 'FP_bnSq_HAHKCSncmJjw5hxhnzs6vaVDSZZn3egj6rdqTQ_hu5tQVJyLMpgCakdP', NULL, 0, '', '2023-10-18 11:21:18', '', '2023-12-20 21:28:26', '1', 121);
INSERT INTO system_social_client (id, name, social_type, user_type, client_id, client_secret, agent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (3, '微信公众号', 31, 1, 'wx5b23ba7a5589ecbb', '2a7b3b20c537e52e74afd395eb85f61f', NULL, 0, '', '2023-10-18 16:07:46', '1', '2023-12-20 21:28:23', '1', 1);
INSERT INTO system_social_client (id, name, social_type, user_type, client_id, client_secret, agent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (43, '微信小程序', 34, 1, 'wx63c280fe3248a3e7', '6f270509224a7ae1296bbf1c8cb97aed', NULL, 0, '', '2023-10-19 13:37:41', '1', '2023-12-20 21:28:25', '1', 1);
INSERT INTO system_social_client (id, name, social_type, user_type, client_id, client_secret, agent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (44, '1', 10, 1, '2', '3', NULL, 0, '1', '2025-04-06 20:36:28', '1', '2025-04-06 20:43:12', '1', 1);
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_tenant
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_tenant (id, name, contact_user_id, contact_name, contact_mobile, status, websites, package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted) VALUES (1, '芋道源码', NULL, '芋艿', '17321315478', 0, 'www.iocoder.cn', 0, '2099-02-19 17:14:16', 9999, '1', '2021-01-05 17:03:47', '1', '2023-11-06 11:41:41', '0');
INSERT INTO system_tenant (id, name, contact_user_id, contact_name, contact_mobile, status, websites, package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted) VALUES (121, '小租户', 110, '小王2', '15601691300', 0, 'zsxq.iocoder.cn', 111, '2026-07-10 00:00:00', 30, '1', '2022-02-22 00:56:14', '1', '2025-04-03 21:33:01', '0');
INSERT INTO system_tenant (id, name, contact_user_id, contact_name, contact_mobile, status, websites, package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted) VALUES (122, '测试租户', 113, '芋道', '15601691300', 0, 'test.iocoder.cn', 111, '2022-04-29 00:00:00', 50, '1', '2022-03-07 21:37:58', '1', '2024-09-22 12:10:50', '0');
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_tenant_package
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_tenant_package (id, name, status, remark, menu_ids, creator, create_time, updater, update_time, deleted) VALUES (111, '普通套餐', 0, '小功能', '[1,2,5,1031,1032,1033,1034,1035,1036,1037,1038,1039,1050,1051,1052,1053,1054,1056,1057,1058,1059,1060,1063,1064,1065,1066,1067,1070,1075,1077,1078,1082,1083,1084,1085,1086,1087,1088,1089,1090,1091,1092,1118,1119,1120,100,101,102,103,106,107,110,111,112,113,1138,114,1139,115,1140,116,1141,1142,1143,2713,2714,2715,2716,2717,2718,2720,1185,2721,1186,2722,1187,2723,1188,2724,1189,2725,1190,2726,1191,2727,2472,1192,2728,1193,2729,1194,2730,1195,2731,1196,2732,1197,2733,2478,1198,2734,2479,1199,2735,2480,1200,2481,1201,2482,1202,2483,2739,2484,2740,2485,2486,2487,1207,2488,1208,2489,1209,2490,1210,2491,1211,2492,1212,2493,1213,2494,2495,1215,1216,2497,1217,1218,1219,1220,1221,1222,1224,1225,1226,1227,1228,1229,1237,1238,1239,1240,1241,1242,1243,2525,1255,1256,1001,1257,1002,1258,1003,1259,1004,1260,1005,1006,1007,1008,1009,1010,1011,1012,1013,1014,1015,1016,1017,1018,1019,1020]', '1', '2022-02-22 00:54:00', '1', '2024-07-13 22:37:24', '0');
INSERT INTO system_tenant_package (id, name, status, remark, menu_ids, creator, create_time, updater, update_time, deleted) VALUES (112, '再来一个套餐', 0, '1234', '[1024,1,1025,1026,2,1027,1028,1029,1030,1031,1032,1033,1034,1035,1036,1037,1038,1039,1040,1042,1043,1045,1046,1048,1050,1051,1052,1053,1054,1056,1057,1058,2083,1059,1060,1063,1064,1065,1066,1067,1070,1075,1077,1078,1082,1083,1084,1085,1086,1087,1088,1089,1090,1091,1092,1093,1094,1095,1096,1097,1098,1100,1101,1102,1103,1104,1105,1106,2130,1107,2131,1108,2132,1109,2133,2134,2135,2136,2137,2138,2139,2140,2141,2142,2143,2144,2145,2146,2147,100,2148,101,2149,102,2150,103,2151,104,2152,105,106,107,108,109,110,111,112,113,1138,114,1139,115,1140,116,1141,1142,1143,2739,2740,1224,1225,1226,1227,1228,1229,1237,1238,1239,1240,1241,1242,1243,1255,1256,1257,1258,1259,1260,1261,1263,1264,1265,1266,1267,2447,2448,2449,2450,2451,2452,2453,2472,2478,2479,2480,2481,2482,2483,2484,2485,2486,2487,2488,2489,2490,2491,2492,2493,2494,2495,2497,2525,1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012,500,1013,501,1014,1015,1016,1017,1018,1019,1020,1021,1022,1023]', '1', '2025-04-04 08:15:02', '1', '2025-04-04 08:15:21', '0');
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_post
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_post (id, code, name, sort, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1, 'ceo', '董事长', 1, 0, '', 'admin', '2021-01-06 17:03:48', '1', '2023-02-11 15:19:04', '0', 1);
INSERT INTO system_post (id, code, name, sort, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (2, 'se', '项目经理', 2, 0, '', 'admin', '2021-01-05 17:03:48', '1', '2023-11-15 09:18:20', '0', 1);
INSERT INTO system_post (id, code, name, sort, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (4, 'user', '普通员工', 4, 0, '111222', 'admin', '2021-01-05 17:03:48', '1', '2025-03-24 21:32:40', '0', 1);
INSERT INTO system_post (id, code, name, sort, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (5, 'HR', '人力资源', 5, 0, '`', '1', '2024-03-24 20:45:40', '1', '2025-03-29 19:08:10', '0', 1);
COMMIT;
-- @formatter:on


-- ----------------------------
-- Records of system_user_post
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (112, 1, 1, 'admin', '2022-05-02 07:25:24', 'admin', '2022-05-02 07:25:24', '0', 1);
INSERT INTO system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (113, 100, 1, 'admin', '2022-05-02 07:25:24', 'admin', '2022-05-02 07:25:24', '0', 1);
INSERT INTO system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (115, 104, 1, '1', '2022-05-16 19:36:28', '1', '2022-05-16 19:36:28', '0', 1);
INSERT INTO system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (116, 117, 2, '1', '2022-07-09 17:40:26', '1', '2022-07-09 17:40:26', '0', 1);
INSERT INTO system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (117, 118, 1, '1', '2022-07-09 17:44:44', '1', '2022-07-09 17:44:44', '0', 1);
INSERT INTO system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (119, 114, 5, '1', '2024-03-24 20:45:51', '1', '2024-03-24 20:45:51', '0', 1);
INSERT INTO system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (123, 115, 1, '1', '2024-04-04 09:37:14', '1', '2024-04-04 09:37:14', '0', 1);
INSERT INTO system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (124, 115, 2, '1', '2024-04-04 09:37:14', '1', '2024-04-04 09:37:14', '0', 1);
INSERT INTO system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (125, 1, 2, '1', '2024-07-13 22:31:39', '1', '2024-07-13 22:31:39', '0', 1);
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_role
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1, '超级管理员', 'super_admin', 1, 1, '', 0, 1, '超级管理员', 'admin', '2021-01-05 17:03:48', '', '2022-02-22 05:08:21', '0', 1);
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (2, '普通角色', 'common', 2, 2, '', 0, 1, '普通角色', 'admin', '2021-01-05 17:03:48', '', '2022-02-22 05:08:20', '0', 1);
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (3, 'CRM 管理员', 'crm_admin', 2, 1, '', 0, 1, 'CRM 专属角色', '1', '2024-02-24 10:51:13', '1', '2024-02-24 02:51:32', '0', 1);
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (101, '测试账号', 'test', 0, 1, '[]', 0, 2, '123', '', '2021-01-06 13:49:35', '1', '2025-04-30 17:38:28', '0', 1);
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (109, '租户管理员', 'tenant_admin', 0, 1, '', 0, 1, '系统自动生成', '1', '2022-02-22 00:56:14', '1', '2022-02-22 00:56:14', '0', 121);
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (111, '租户管理员', 'tenant_admin', 0, 1, '', 0, 1, '系统自动生成', '1', '2022-03-07 21:37:58', '1', '2022-03-07 21:37:58', '0', 122);
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (155, '测试数据权限', 'test-dp', 3, 2, '[100,102,103,104,105,108]', 0, 2, '', '1', '2025-03-31 14:58:06', '1', '2025-04-17 23:07:44', '0', 1);
INSERT INTO system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (158, '2', '3', 4, 1, '', 0, 2, NULL, '1', '2025-04-17 20:08:08', '1', '2025-04-17 23:05:31', '0', 1);
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_user_role
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1, 1, 1, '', '2022-01-11 13:19:45', '', '2022-05-12 12:35:17', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (2, 2, 2, '', '2022-01-11 13:19:45', '', '2022-05-12 12:35:13', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (4, 100, 101, '', '2022-01-11 13:19:45', '', '2022-05-12 12:35:13', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (5, 100, 1, '', '2022-01-11 13:19:45', '', '2022-05-12 12:35:12', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (6, 100, 2, '', '2022-01-11 13:19:45', '', '2022-05-12 12:35:11', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (10, 103, 1, '1', '2022-01-11 13:19:45', '1', '2022-01-11 13:19:45', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (14, 110, 109, '1', '2022-02-22 00:56:14', '1', '2022-02-22 00:56:14', '0', 121);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (15, 111, 110, '110', '2022-02-23 13:14:38', '110', '2022-02-23 13:14:38', '0', 121);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (16, 113, 111, '1', '2022-03-07 21:37:58', '1', '2022-03-07 21:37:58', '0', 122);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (18, 1, 2, '1', '2022-05-12 20:39:29', '1', '2022-05-12 20:39:29', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (22, 115, 2, '1', '2022-07-21 22:08:30', '1', '2022-07-21 22:08:30', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (35, 112, 1, '1', '2024-03-15 20:00:24', '1', '2024-03-15 20:00:24', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (36, 118, 1, '1', '2024-03-17 09:12:08', '1', '2024-03-17 09:12:08', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (38, 114, 101, '1', '2024-03-24 22:23:03', '1', '2024-03-24 22:23:03', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (46, 117, 1, '1', '2024-10-02 10:16:11', '1', '2024-10-02 10:16:11', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (47, 104, 2, '1', '2025-01-04 10:40:33', '1', '2025-01-04 10:40:33', '0', 1);
INSERT INTO system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (48, 100, 155, '1', '2025-04-04 10:41:14', '1', '2025-04-04 10:41:14', '0', 1);
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_users
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1, 'admin', '$2a$04$KljJDa/LK7QfDm0lF5OhuePhlPfjRH3tB2Wu351Uidz.oQGJXevPi', '芋道源码', '管理员', 103, '[1,2]', '11aoteman@126.com', '18818260277', 2, 'http://test.yudao.iocoder.cn/test/20250502/avatar_1746154660449.png', 0, '0:0:0:0:0:0:0:1', '2025-05-10 18:03:15', 'admin', '2021-01-05 17:03:47', NULL, '2025-05-10 18:03:15', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (100, 'yudao', '$2a$04$h.aaPKgO.odHepnk5PCsWeEwKdojFWdTItxGKfx1r0e1CSeBzsTJ6', '芋道', '不要吓我', 104, '[1]', 'yudao@iocoder.cn', '15601691300', 1, NULL, 0, '0:0:0:0:0:0:0:1', '2025-04-08 09:36:40', '', '2021-01-07 09:07:17', NULL, '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (103, 'yuanma', '$2a$04$fUBSmjKCPYAUmnMzOb6qE.eZCGPhHi1JmAKclODbfS/O7fHOl2bH6', '源码', NULL, 106, NULL, 'yuanma@iocoder.cn', '15601701300', 0, NULL, 0, '0:0:0:0:0:0:0:1', '2024-08-11 17:48:12', '', '2021-01-13 23:50:35', NULL, '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (104, 'test', '$2a$04$BrwaYn303hjA/6TnXqdGoOLhyHOAA0bVrAFu6.1dJKycqKUnIoRz2', '测试号', NULL, 107, '[1,2]', '111@qq.com', '15601691200', 1, NULL, 0, '0:0:0:0:0:0:0:1', '2025-03-28 20:01:16', '', '2021-01-21 02:13:53', NULL, '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (107, 'admin107', '$2a$10$dYOOBKMO93v/.ReCqzyFg.o67Tqk.bbc2bhrpyBGkIw9aypCtr2pm', '芋艿', NULL, NULL, NULL, '', '15601691300', 0, NULL, 0, '', NULL, '1', '2022-02-20 22:59:33', '1', '2025-04-21 14:23:08', '0', 118);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (108, 'admin108', '$2a$10$y6mfvKoNYL1GXWak8nYwVOH.kCWqjactkzdoIDgiKl93WN3Ejg.Lu', '芋艿', NULL, NULL, NULL, '', '15601691300', 0, NULL, 0, '', NULL, '1', '2022-02-20 23:00:50', '1', '2025-04-21 14:23:08', '0', 119);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (109, 'admin109', '$2a$10$JAqvH0tEc0I7dfDVBI7zyuB4E3j.uH6daIjV53.vUS6PknFkDJkuK', '芋艿', NULL, NULL, NULL, '', '15601691300', 0, NULL, 0, '', NULL, '1', '2022-02-20 23:11:50', '1', '2025-04-21 14:23:08', '0', 120);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (110, 'admin110', '$2a$10$mRMIYLDtRHlf6.9ipiqH1.Z.bh/R9dO9d5iHiGYPigi6r5KOoR2Wm', '小王', NULL, NULL, NULL, '', '15601691300', 0, NULL, 0, '0:0:0:0:0:0:0:1', '2024-07-20 22:23:17', '1', '2022-02-22 00:56:14', NULL, '2025-04-21 14:23:08', '0', 121);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (111, 'test', '$2a$10$mRMIYLDtRHlf6.9ipiqH1.Z.bh/R9dO9d5iHiGYPigi6r5KOoR2Wm', '测试用户', NULL, NULL, '[]', '', '', 0, NULL, 0, '0:0:0:0:0:0:0:1', '2023-12-30 11:42:17', '110', '2022-02-23 13:14:33', NULL, '2025-04-21 14:23:08', '0', 121);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (112, 'newobject', '$2a$04$dB0z8Q819fJWz0hbaLe6B.VfHCjYgWx6LFfET5lyz3JwcqlyCkQ4C', '新对象', NULL, 100, '[]', '', '15601691235', 1, NULL, 0, '0:0:0:0:0:0:0:1', '2024-03-16 23:11:38', '1', '2022-02-23 19:08:03', NULL, '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (113, 'aoteman', '$2a$10$0acJOIk2D25/oC87nyclE..0lzeu9DtQ/n3geP4fkun/zIVRhHJIO', '芋道1', NULL, NULL, NULL, '', '15601691300', 0, NULL, 0, '127.0.0.1', '2022-03-19 18:38:51', '1', '2022-03-07 21:37:58', '1', '2025-05-05 15:30:53', '0', 122);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (114, 'hrmgr', '$2a$10$TR4eybBioGRhBmDBWkqWLO6NIh3mzYa8KBKDDB5woiGYFVlRAi.fu', 'hr 小姐姐', NULL, NULL, '[5]', '', '15601691236', 1, NULL, 0, '0:0:0:0:0:0:0:1', '2024-03-24 22:21:05', '1', '2022-03-19 21:50:58', NULL, '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (115, 'aotemane', '$2a$04$GcyP0Vyzb2F2Yni5PuIK9ueGxM0tkZGMtDwVRwrNbtMvorzbpNsV2', '阿呆', '11222', 102, '[1,2]', '7648@qq.com', '15601691229', 2, NULL, 0, '', NULL, '1', '2022-04-30 02:55:43', '1', '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (117, 'admin123', '$2a$04$sEtimsHu9YCkYY4/oqElHem2Ijc9ld20eYO6lN.g/21NfLUTDLB9W', '测试号02', '1111', 100, '[2]', '', '15601691234', 1, NULL, 0, '0:0:0:0:0:0:0:1', '2024-10-02 10:16:20', '1', '2022-07-09 17:40:26', NULL, '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (118, 'goudan', '$2a$04$jth0yOj8cSJq84D6vrzusOHDwW/LpBfgBnQ6bfFlD8zNZfM632Ta2', '狗蛋', NULL, 103, '[1]', '', '15601691239', 1, NULL, 0, '0:0:0:0:0:0:0:1', '2024-03-17 09:10:27', '1', '2022-07-09 17:44:43', '1', '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (131, 'hh', '$2a$04$jyH9h6.gaw8mpOjPfHIpx.8as2Rzfcmdlj5rlJFwgCw4rsv/MTb2K', '呵呵', NULL, 100, '[]', '777@qq.com', '15601882312', 1, NULL, 0, '', NULL, '1', '2024-04-27 08:45:56', '1', '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (139, 'wwbwwb', '$2a$04$aOHoFbQU6zfBk/1Z9raF/ugTdhjNdx7culC1HhO0zvoczAnahCiMq', '小秃头', NULL, NULL, NULL, '', '', 0, NULL, 0, '0:0:0:0:0:0:0:1', '2024-09-10 21:03:58', NULL, '2024-09-10 21:03:58', NULL, '2025-04-21 14:23:08', '0', 1);
INSERT INTO system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (141, 'admin1', '$2a$04$oj6F6d7HrZ70kYVD3TNzEu.m3TPUzajOVuC66zdKna8KRerK1FmVa', '新用户', NULL, NULL, NULL, '', '', 0, '', 0, '0:0:0:0:0:0:0:1', '2025-04-08 13:09:07', '1', '2025-04-08 13:09:07', '1', '2025-04-08 13:09:07', '0', 1);
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_notice
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_notice (id, title, content, type, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1, '芋道的公众', '<p>新版本内容133</p>', 1, 0, 'admin', '2021-01-05 17:03:48', '1', '2022-05-04 21:00:20', '0', 1);
INSERT INTO system_notice (id, title, content, type, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (2, '维护通知：2018-07-01 系统凌晨维护', '<p><img src="http://test.yudao.iocoder.cn/b7cb3cf49b4b3258bf7309a09dd2f4e5.jpg" alt="" data-href="">11112222<img src="http://test.yudao.iocoder.cn/fe44fc7bdb82ca421184b2eebbaee9e2148d4a1827479a4eb4521e11d2a062ba.png" alt="image" data-href="http://test.yudao.iocoder.cn/fe44fc7bdb82ca421184b2eebbaee9e2148d4a1827479a4eb4521e11d2a062ba.png">3333</p>', 2, 1, 'admin', '2021-01-05 17:03:48', '1', '2025-04-18 23:56:40', '0', 1);
INSERT INTO system_notice (id, title, content, type, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (4, '我是测试标题', '<p>哈哈哈哈123</p>', 1, 0, '110', '2022-02-22 01:01:25', '110', '2022-02-22 01:01:46', '0', 121);
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_notify_message
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (2, 1, 2, 1, 'test', '123', '我是 1，我开始 2 了', 1, '{"name":"1","what":"2"}', '1', '2025-04-21 14:59:37', '1', '2023-01-28 11:44:08', '1', '2025-04-21 14:59:37', '0', 1);
INSERT INTO system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (3, 1, 2, 1, 'test', '123', '我是 1，我开始 2 了', 1, '{"name":"1","what":"2"}', '1', '2025-04-21 14:59:37', '1', '2023-01-28 11:45:04', '1', '2025-04-21 14:59:37', '0', 1);
INSERT INTO system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (4, 103, 2, 2, 'register', '系统消息', '你好，欢迎 哈哈 加入大家庭！', 2, '{"name":"哈哈"}', '0', NULL, '1', '2023-01-28 21:02:20', '1', '2023-01-28 21:02:20', '0', 1);
INSERT INTO system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (5, 1, 2, 1, 'test', '123', '我是 芋艿，我开始 写代码 了', 1, '{"name":"芋艿","what":"写代码"}', '1', '2025-04-21 14:59:37', '1', '2023-01-28 22:21:42', '1', '2025-04-21 14:59:37', '0', 1);
INSERT INTO system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (6, 1, 2, 1, 'test', '123', '我是 芋艿，我开始 写代码 了', 1, '{"name":"芋艿","what":"写代码"}', '1', '2025-04-21 14:59:36', '1', '2023-01-28 22:22:07', '1', '2025-04-21 14:59:36', '0', 1);
INSERT INTO system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (7, 1, 2, 1, 'test', '123', '我是 2，我开始 3 了', 1, '{"name":"2","what":"3"}', '1', '2025-04-21 14:59:35', '1', '2023-01-28 23:45:21', '1', '2025-04-21 14:59:35', '0', 1);
INSERT INTO system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (8, 1, 2, 2, 'register', '系统消息', '你好，欢迎 123 加入大家庭！', 2, '{"name":"123"}', '1', '2025-04-21 14:59:35', '1', '2023-01-28 23:50:21', '1', '2025-04-21 14:59:35', '0', 1);
INSERT INTO system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9, 247, 1, 4, 'brokerage_withdraw_audit_approve', 'system', '您在2023-09-28 08:35:46提现￥0.09元的申请已通过审核', 2, '{"reason":null,"createTime":"2023-09-28 08:35:46","price":"0.09"}', '0', NULL, '1', '2023-09-28 16:36:22', '1', '2023-09-28 16:36:22', '0', 1);
INSERT INTO system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (10, 247, 1, 4, 'brokerage_withdraw_audit_approve', 'system', '您在2023-09-30 20:59:40提现￥1.00元的申请已通过审核', 2, '{"reason":null,"createTime":"2023-09-30 20:59:40","price":"1.00"}', '0', NULL, '1', '2023-10-03 12:11:34', '1', '2023-10-03 12:11:34', '0', 1);
COMMIT;
-- @formatter:on

-- ----------------------------
-- Records of system_oauth2_client
-- ----------------------------
-- @formatter:off
BEGIN;
INSERT INTO system_oauth2_client (id, client_id, secret, name, logo, description, status, access_token_validity_seconds, refresh_token_validity_seconds, redirect_uris, authorized_grant_types, scopes, auto_approve_scopes, authorities, resource_ids, additional_information, creator, create_time, updater, update_time, deleted) VALUES (1, 'default', 'admin123', '芋道源码', 'http://test.yudao.iocoder.cn/20250502/sort2_1746189740718.png', '我是描述', 0, 1800, 2592000, '["https://www.iocoder.cn","https://doc.iocoder.cn"]', '["password","authorization_code","implicit","refresh_token"]', '["user.read","user.write"]', '[]', '["user.read","user.write"]', '[]', '{}', '1', '2022-05-11 21:47:12', '1', '2025-05-02 20:42:22', '0');
INSERT INTO system_oauth2_client (id, client_id, secret, name, logo, description, status, access_token_validity_seconds, refresh_token_validity_seconds, redirect_uris, authorized_grant_types, scopes, auto_approve_scopes, authorities, resource_ids, additional_information, creator, create_time, updater, update_time, deleted) VALUES (40, 'test', 'test2', 'biubiu', 'http://test.yudao.iocoder.cn/xx/20250502/ed07110a37464b5299f8bd7c67ad65c7_1746187077009.jpg', '啦啦啦啦', 0, 1800, 43200, '["https://www.iocoder.cn"]', '["password","authorization_code","implicit"]', '["user_info","projects"]', '["user_info"]', '[]', '[]', '{}', '1', '2022-05-12 00:28:20', '1', '2025-05-02 19:58:08', '0');
INSERT INTO system_oauth2_client (id, client_id, secret, name, logo, description, status, access_token_validity_seconds, refresh_token_validity_seconds, redirect_uris, authorized_grant_types, scopes, auto_approve_scopes, authorities, resource_ids, additional_information, creator, create_time, updater, update_time, deleted) VALUES (41, 'yudao-sso-demo-by-code', 'test', '基于授权码模式，如何实现 SSO 单点登录？', 'http://test.yudao.iocoder.cn/it/20250502/sign_1746181948685.png', NULL, 0, 1800, 43200, '["http://127.0.0.1:18080"]', '["authorization_code","refresh_token"]', '["user.read","user.write"]', '[]', '[]', '[]', NULL, '1', '2022-09-29 13:28:31', '1', '2025-05-02 18:32:30', '0');
INSERT INTO system_oauth2_client (id, client_id, secret, name, logo, description, status, access_token_validity_seconds, refresh_token_validity_seconds, redirect_uris, authorized_grant_types, scopes, auto_approve_scopes, authorities, resource_ids, additional_information, creator, create_time, updater, update_time, deleted) VALUES (42, 'yudao-sso-demo-by-password', 'test', '基于密码模式，如何实现 SSO 单点登录？', 'http://test.yudao.iocoder.cn/604bdc695e13b3b22745be704d1f2aa8ee05c5f26f9fead6d1ca49005afbc857.jpeg', NULL, 0, 1800, 43200, '["http://127.0.0.1:18080"]', '["password","refresh_token"]', '["user.read","user.write"]', '[]', '[]', '[]', NULL, '1', '2022-10-04 17:40:16', '1', '2025-05-04 16:00:46', '0');
COMMIT;
-- @formatter:on