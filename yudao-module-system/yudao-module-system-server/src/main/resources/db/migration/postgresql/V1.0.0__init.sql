-- ----------------------------
-- Table structure for system_dept
-- ----------------------------
DROP TABLE IF EXISTS system_dept;
CREATE TABLE system_dept
(
    id             int8        NOT NULL,
    name           varchar(30) NOT NULL DEFAULT '',
    parent_id      int8        NOT NULL DEFAULT 0,
    sort           int4        NOT NULL DEFAULT 0,
    leader_user_id int8        NULL     DEFAULT NULL,
    phone          varchar(11) NULL     DEFAULT NULL,
    email          varchar(50) NULL     DEFAULT NULL,
    status         int2        NOT NULL,
    creator        varchar(64) NULL     DEFAULT '',
    create_time    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64) NULL     DEFAULT '',
    update_time    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2        NOT NULL DEFAULT 0,
    tenant_id      int8        NOT NULL DEFAULT 0
);

ALTER TABLE system_dept
    ADD CONSTRAINT pk_system_dept PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_dept_seq;
CREATE SEQUENCE system_dept_seq
    START 114;

-- ----------------------------
-- Table structure for system_dict_data
-- ----------------------------
DROP TABLE IF EXISTS system_dict_data;
CREATE TABLE system_dict_data
(
    id          int8         NOT NULL,
    sort        int4         NOT NULL DEFAULT 0,
    label       varchar(100) NOT NULL DEFAULT '',
    value       varchar(100) NOT NULL DEFAULT '',
    dict_type   varchar(100) NOT NULL DEFAULT '',
    status      int2         NOT NULL DEFAULT 0,
    color_type  varchar(100) NULL     DEFAULT '',
    css_class   varchar(100) NULL     DEFAULT '',
    remark      varchar(500) NULL     DEFAULT NULL,
    creator     varchar(64)  NULL     DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL     DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0
);

ALTER TABLE system_dict_data
    ADD CONSTRAINT pk_system_dict_data PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_dict_data_seq;
CREATE SEQUENCE system_dict_data_seq
    START 3003;

-- ----------------------------
-- Table structure for system_dict_type
-- ----------------------------
DROP TABLE IF EXISTS system_dict_type;
CREATE TABLE system_dict_type
(
    id           int8         NOT NULL,
    name         varchar(100) NOT NULL DEFAULT '',
    type         varchar(100) NOT NULL DEFAULT '',
    status       int2         NOT NULL DEFAULT 0,
    remark       varchar(500) NULL     DEFAULT NULL,
    creator      varchar(64)  NULL     DEFAULT '',
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)  NULL     DEFAULT '',
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2         NOT NULL DEFAULT 0,
    deleted_time timestamp    NULL     DEFAULT NULL
);

ALTER TABLE system_dict_type
    ADD CONSTRAINT pk_system_dict_type PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_dict_type_seq;
CREATE SEQUENCE system_dict_type_seq
    START 1014;

-- ----------------------------
-- Table structure for system_login_log
-- ----------------------------
DROP TABLE IF EXISTS system_login_log;
CREATE TABLE system_login_log
(
    id          int8         NOT NULL,
    log_type    int8         NOT NULL,
    trace_id    varchar(64)  NOT NULL DEFAULT '',
    user_id     int8         NOT NULL DEFAULT 0,
    user_type   int2         NOT NULL DEFAULT 0,
    username    varchar(50)  NOT NULL DEFAULT '',
    result      int2         NOT NULL,
    user_ip     varchar(50)  NOT NULL,
    user_agent  varchar(512) NOT NULL,
    creator     varchar(64)  NULL     DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL     DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    tenant_id   int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_login_log
    ADD CONSTRAINT pk_system_login_log PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_login_log_seq;
CREATE SEQUENCE system_login_log_seq
    START 1;

-- ----------------------------
-- Table structure for system_mail_account
-- ----------------------------
DROP TABLE IF EXISTS system_mail_account;
CREATE TABLE system_mail_account
(
    id              int8         NOT NULL,
    mail            varchar(255) NOT NULL,
    username        varchar(255) NOT NULL,
    password        varchar(255) NOT NULL,
    host            varchar(255) NOT NULL,
    port            int4         NOT NULL,
    ssl_enable      bool         NOT NULL DEFAULT '0',
    starttls_enable bool         NOT NULL DEFAULT '0',
    creator         varchar(64)  NULL     DEFAULT '',
    create_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64)  NULL     DEFAULT '',
    update_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2         NOT NULL DEFAULT 0
);

ALTER TABLE system_mail_account
    ADD CONSTRAINT pk_system_mail_account PRIMARY KEY (id);




DROP SEQUENCE IF EXISTS system_mail_account_seq;
CREATE SEQUENCE system_mail_account_seq
    START 5;

-- ----------------------------
-- Table structure for system_mail_log
-- ----------------------------
DROP TABLE IF EXISTS system_mail_log;
CREATE TABLE system_mail_log
(
    id                int8           NOT NULL,
    user_id           int8           NULL     DEFAULT NULL,
    user_type         int2           NULL     DEFAULT NULL,
    to_mail           varchar(255)   NOT NULL,
    account_id        int8           NOT NULL,
    from_mail         varchar(255)   NOT NULL,
    template_id       int8           NOT NULL,
    template_code     varchar(63)    NOT NULL,
    template_nickname varchar(255)   NULL     DEFAULT NULL,
    template_title    varchar(255)   NOT NULL,
    template_content  varchar(10240) NOT NULL,
    template_params   varchar(255)   NOT NULL,
    send_status       int2           NOT NULL DEFAULT 0,
    send_time         timestamp      NULL     DEFAULT NULL,
    send_message_id   varchar(255)   NULL     DEFAULT NULL,
    send_exception    varchar(4096)  NULL     DEFAULT NULL,
    creator           varchar(64)    NULL     DEFAULT '',
    create_time       timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           varchar(64)    NULL     DEFAULT '',
    update_time       timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           int2           NOT NULL DEFAULT 0
);

ALTER TABLE system_mail_log
    ADD CONSTRAINT pk_system_mail_log PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_mail_log_seq;
CREATE SEQUENCE system_mail_log_seq
    START 1;

-- ----------------------------
-- Table structure for system_mail_template
-- ----------------------------
DROP TABLE IF EXISTS system_mail_template;
CREATE TABLE system_mail_template
(
    id          int8           NOT NULL,
    name        varchar(63)    NOT NULL,
    code        varchar(63)    NOT NULL,
    account_id  int8           NOT NULL,
    nickname    varchar(255)   NULL     DEFAULT NULL,
    title       varchar(255)   NOT NULL,
    content     varchar(10240) NOT NULL,
    params      varchar(255)   NOT NULL,
    status      int2           NOT NULL,
    remark      varchar(255)   NULL     DEFAULT NULL,
    creator     varchar(64)    NULL     DEFAULT '',
    create_time timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)    NULL     DEFAULT '',
    update_time timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2           NOT NULL DEFAULT 0
);

ALTER TABLE system_mail_template
    ADD CONSTRAINT pk_system_mail_template PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_mail_template_seq;
CREATE SEQUENCE system_mail_template_seq
    START 16;

-- ----------------------------
-- Table structure for system_menu
-- ----------------------------
DROP TABLE IF EXISTS system_menu;
CREATE TABLE system_menu
(
    id             int8         NOT NULL,
    name           varchar(50)  NOT NULL,
    permission     varchar(100) NOT NULL DEFAULT '',
    type           int2         NOT NULL,
    sort           int4         NOT NULL DEFAULT 0,
    parent_id      int8         NOT NULL DEFAULT 0,
    path           varchar(200) NULL     DEFAULT '',
    icon           varchar(100) NULL     DEFAULT '#',
    component      varchar(255) NULL     DEFAULT NULL,
    component_name varchar(255) NULL     DEFAULT NULL,
    status         int2         NOT NULL DEFAULT 0,
    visible        bool         NOT NULL DEFAULT '1',
    keep_alive     bool         NOT NULL DEFAULT '1',
    always_show    bool         NOT NULL DEFAULT '1',
    creator        varchar(64)  NULL     DEFAULT '',
    create_time    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)  NULL     DEFAULT '',
    update_time    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2         NOT NULL DEFAULT 0
);

ALTER TABLE system_menu
    ADD CONSTRAINT pk_system_menu PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_menu_seq;
CREATE SEQUENCE system_menu_seq
    START 5013;

-- ----------------------------
-- Table structure for system_notice
-- ----------------------------
DROP TABLE IF EXISTS system_notice;
CREATE TABLE system_notice
(
    id          int8        NOT NULL,
    title       varchar(50) NOT NULL,
    content     text        NOT NULL,
    type        int2        NOT NULL,
    status      int2        NOT NULL DEFAULT 0,
    creator     varchar(64) NULL     DEFAULT '',
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64) NULL     DEFAULT '',
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2        NOT NULL DEFAULT 0,
    tenant_id   int8        NOT NULL DEFAULT 0
);

ALTER TABLE system_notice
    ADD CONSTRAINT pk_system_notice PRIMARY KEY (id);




DROP SEQUENCE IF EXISTS system_notice_seq;
CREATE SEQUENCE system_notice_seq
    START 5;

-- ----------------------------
-- Table structure for system_notify_message
-- ----------------------------
DROP TABLE IF EXISTS system_notify_message;
CREATE TABLE system_notify_message
(
    id                int8          NOT NULL,
    user_id           int8          NOT NULL,
    user_type         int2          NOT NULL,
    template_id       int8          NOT NULL,
    template_code     varchar(64)   NOT NULL,
    template_nickname varchar(63)   NOT NULL,
    template_content  varchar(1024) NOT NULL,
    template_type     int4          NOT NULL,
    template_params   varchar(255)  NOT NULL,
    read_status       bool          NOT NULL,
    read_time         timestamp     NULL     DEFAULT NULL,
    creator           varchar(64)   NULL     DEFAULT '',
    create_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           varchar(64)   NULL     DEFAULT '',
    update_time       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           int2          NOT NULL DEFAULT 0,
    tenant_id         int8          NOT NULL DEFAULT 0
);

ALTER TABLE system_notify_message
    ADD CONSTRAINT pk_system_notify_message PRIMARY KEY (id);




DROP SEQUENCE IF EXISTS system_notify_message_seq;
CREATE SEQUENCE system_notify_message_seq
    START 11;

-- ----------------------------
-- Table structure for system_notify_template
-- ----------------------------
DROP TABLE IF EXISTS system_notify_template;
CREATE TABLE system_notify_template
(
    id          int8          NOT NULL,
    name        varchar(63)   NOT NULL,
    code        varchar(64)   NOT NULL,
    nickname    varchar(255)  NOT NULL,
    content     varchar(1024) NOT NULL,
    type        int2          NOT NULL,
    params      varchar(255)  NULL     DEFAULT NULL,
    status      int2          NOT NULL,
    remark      varchar(255)  NULL     DEFAULT NULL,
    creator     varchar(64)   NULL     DEFAULT '',
    create_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)   NULL     DEFAULT '',
    update_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2          NOT NULL DEFAULT 0
);

ALTER TABLE system_notify_template
    ADD CONSTRAINT pk_system_notify_template PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_notify_template_seq;
CREATE SEQUENCE system_notify_template_seq
    START 1;

-- ----------------------------
-- Table structure for system_oauth2_access_token
-- ----------------------------
DROP TABLE IF EXISTS system_oauth2_access_token;
CREATE TABLE system_oauth2_access_token
(
    id            int8         NOT NULL,
    user_id       int8         NOT NULL,
    user_type     int2         NOT NULL,
    user_info     varchar(512) NOT NULL,
    access_token  varchar(255) NOT NULL,
    refresh_token varchar(32)  NOT NULL,
    client_id     varchar(255) NOT NULL,
    scopes        varchar(255) NULL     DEFAULT NULL,
    expires_time  timestamp    NOT NULL,
    creator       varchar(64)  NULL     DEFAULT '',
    create_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)  NULL     DEFAULT '',
    update_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2         NOT NULL DEFAULT 0,
    tenant_id     int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_oauth2_access_token
    ADD CONSTRAINT pk_system_oauth2_access_token PRIMARY KEY (id);

CREATE INDEX idx_system_oauth2_access_token_01 ON system_oauth2_access_token (access_token);
CREATE INDEX idx_system_oauth2_access_token_02 ON system_oauth2_access_token (refresh_token);


DROP SEQUENCE IF EXISTS system_oauth2_access_token_seq;
CREATE SEQUENCE system_oauth2_access_token_seq
    START 1;

-- ----------------------------
-- Table structure for system_oauth2_approve
-- ----------------------------
DROP TABLE IF EXISTS system_oauth2_approve;
CREATE TABLE system_oauth2_approve
(
    id           int8         NOT NULL,
    user_id      int8         NOT NULL,
    user_type    int2         NOT NULL,
    client_id    varchar(255) NOT NULL,
    scope        varchar(255) NOT NULL DEFAULT '',
    approved     bool         NOT NULL DEFAULT '0',
    expires_time timestamp    NOT NULL,
    creator      varchar(64)  NULL     DEFAULT '',
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)  NULL     DEFAULT '',
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2         NOT NULL DEFAULT 0,
    tenant_id    int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_oauth2_approve
    ADD CONSTRAINT pk_system_oauth2_approve PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_oauth2_approve_seq;
CREATE SEQUENCE system_oauth2_approve_seq
    START 1;

-- ----------------------------
-- Table structure for system_oauth2_client
-- ----------------------------
DROP TABLE IF EXISTS system_oauth2_client;
CREATE TABLE system_oauth2_client
(
    id                             int8          NOT NULL,
    client_id                      varchar(255)  NOT NULL,
    secret                         varchar(255)  NOT NULL,
    name                           varchar(255)  NOT NULL,
    logo                           varchar(255)  NOT NULL,
    description                    varchar(255)  NULL     DEFAULT NULL,
    status                         int2          NOT NULL,
    access_token_validity_seconds  int4          NOT NULL,
    refresh_token_validity_seconds int4          NOT NULL,
    redirect_uris                  varchar(255)  NOT NULL,
    authorized_grant_types         varchar(255)  NOT NULL,
    scopes                         varchar(255)  NULL     DEFAULT NULL,
    auto_approve_scopes            varchar(255)  NULL     DEFAULT NULL,
    authorities                    varchar(255)  NULL     DEFAULT NULL,
    resource_ids                   varchar(255)  NULL     DEFAULT NULL,
    additional_information         varchar(4096) NULL     DEFAULT NULL,
    creator                        varchar(64)   NULL     DEFAULT '',
    create_time                    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                        varchar(64)   NULL     DEFAULT '',
    update_time                    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                        int2          NOT NULL DEFAULT 0
);

ALTER TABLE system_oauth2_client
    ADD CONSTRAINT pk_system_oauth2_client PRIMARY KEY (id);




DROP SEQUENCE IF EXISTS system_oauth2_client_seq;
CREATE SEQUENCE system_oauth2_client_seq
    START 43;

-- ----------------------------
-- Table structure for system_oauth2_code
-- ----------------------------
DROP TABLE IF EXISTS system_oauth2_code;
CREATE TABLE system_oauth2_code
(
    id           int8         NOT NULL,
    user_id      int8         NOT NULL,
    user_type    int2         NOT NULL,
    code         varchar(32)  NOT NULL,
    client_id    varchar(255) NOT NULL,
    scopes       varchar(255) NULL     DEFAULT '',
    expires_time timestamp    NOT NULL,
    redirect_uri varchar(255) NULL     DEFAULT NULL,
    state        varchar(255) NOT NULL DEFAULT '',
    creator      varchar(64)  NULL     DEFAULT '',
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)  NULL     DEFAULT '',
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2         NOT NULL DEFAULT 0,
    tenant_id    int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_oauth2_code
    ADD CONSTRAINT pk_system_oauth2_code PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_oauth2_code_seq;
CREATE SEQUENCE system_oauth2_code_seq
    START 1;

-- ----------------------------
-- Table structure for system_oauth2_refresh_token
-- ----------------------------
DROP TABLE IF EXISTS system_oauth2_refresh_token;
CREATE TABLE system_oauth2_refresh_token
(
    id            int8         NOT NULL,
    user_id       int8         NOT NULL,
    refresh_token varchar(32)  NOT NULL,
    user_type     int2         NOT NULL,
    client_id     varchar(255) NOT NULL,
    scopes        varchar(255) NULL     DEFAULT NULL,
    expires_time  timestamp    NOT NULL,
    creator       varchar(64)  NULL     DEFAULT '',
    create_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)  NULL     DEFAULT '',
    update_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2         NOT NULL DEFAULT 0,
    tenant_id     int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_oauth2_refresh_token
    ADD CONSTRAINT pk_system_oauth2_refresh_token PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_oauth2_refresh_token_seq;
CREATE SEQUENCE system_oauth2_refresh_token_seq
    START 1;

-- ----------------------------
-- Table structure for system_operate_log
-- ----------------------------
DROP TABLE IF EXISTS system_operate_log;
CREATE TABLE system_operate_log
(
    id             int8          NOT NULL,
    trace_id       varchar(64)   NOT NULL DEFAULT '',
    user_id        int8          NOT NULL,
    user_type      int2          NOT NULL DEFAULT 0,
    type           varchar(50)   NOT NULL,
    sub_type       varchar(50)   NOT NULL,
    biz_id         int8          NOT NULL,
    action         varchar(2000) NOT NULL DEFAULT '',
    success        bool          NOT NULL DEFAULT '1',
    extra          varchar(2000) NOT NULL DEFAULT '',
    request_method varchar(16)   NULL     DEFAULT '',
    request_url    varchar(255)  NULL     DEFAULT '',
    user_ip        varchar(50)   NULL     DEFAULT NULL,
    user_agent     varchar(512)  NULL     DEFAULT NULL,
    creator        varchar(64)   NULL     DEFAULT '',
    create_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)   NULL     DEFAULT '',
    update_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2          NOT NULL DEFAULT 0,
    tenant_id      int8          NOT NULL DEFAULT 0
);

ALTER TABLE system_operate_log
    ADD CONSTRAINT pk_system_operate_log PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_operate_log_seq;
CREATE SEQUENCE system_operate_log_seq
    START 1;

-- ----------------------------
-- Table structure for system_post
-- ----------------------------
DROP TABLE IF EXISTS system_post;
CREATE TABLE system_post
(
    id          int8         NOT NULL,
    code        varchar(64)  NOT NULL,
    name        varchar(50)  NOT NULL,
    sort        int4         NOT NULL,
    status      int2         NOT NULL,
    remark      varchar(500) NULL     DEFAULT NULL,
    creator     varchar(64)  NULL     DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL     DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    tenant_id   int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_post
    ADD CONSTRAINT pk_system_post PRIMARY KEY (id);




DROP SEQUENCE IF EXISTS system_post_seq;
CREATE SEQUENCE system_post_seq
    START 6;

-- ----------------------------
-- Table structure for system_role
-- ----------------------------
DROP TABLE IF EXISTS system_role;
CREATE TABLE system_role
(
    id                  int8         NOT NULL,
    name                varchar(30)  NOT NULL,
    code                varchar(100) NOT NULL,
    sort                int4         NOT NULL,
    data_scope          int2         NOT NULL DEFAULT 1,
    data_scope_dept_ids varchar(500) NOT NULL DEFAULT '',
    status              int2         NOT NULL,
    type                int2         NOT NULL,
    remark              varchar(500) NULL     DEFAULT NULL,
    creator             varchar(64)  NULL     DEFAULT '',
    create_time         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(64)  NULL     DEFAULT '',
    update_time         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             int2         NOT NULL DEFAULT 0,
    tenant_id           int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_role
    ADD CONSTRAINT pk_system_role PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_role_seq;
CREATE SEQUENCE system_role_seq
    START 159;

-- ----------------------------
-- Table structure for system_role_menu
-- ----------------------------
DROP TABLE IF EXISTS system_role_menu;
CREATE TABLE system_role_menu
(
    id          int8        NOT NULL,
    role_id     int8        NOT NULL,
    menu_id     int8        NOT NULL,
    creator     varchar(64) NULL     DEFAULT '',
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64) NULL     DEFAULT '',
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2        NOT NULL DEFAULT 0,
    tenant_id   int8        NOT NULL DEFAULT 0
);

ALTER TABLE system_role_menu
    ADD CONSTRAINT pk_system_role_menu PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_role_menu_seq;
CREATE SEQUENCE system_role_menu_seq
    START 6139;

-- ----------------------------
-- Table structure for system_sms_channel
-- ----------------------------
DROP TABLE IF EXISTS system_sms_channel;
CREATE TABLE system_sms_channel
(
    id           int8         NOT NULL,
    signature    varchar(12)  NOT NULL,
    code         varchar(63)  NOT NULL,
    status       int2         NOT NULL,
    remark       varchar(255) NULL     DEFAULT NULL,
    api_key      varchar(128) NOT NULL,
    api_secret   varchar(128) NULL     DEFAULT NULL,
    callback_url varchar(255) NULL     DEFAULT NULL,
    creator      varchar(64)  NULL     DEFAULT '',
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      varchar(64)  NULL     DEFAULT '',
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      int2         NOT NULL DEFAULT 0
);

ALTER TABLE system_sms_channel
    ADD CONSTRAINT pk_system_sms_channel PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_sms_channel_seq;
CREATE SEQUENCE system_sms_channel_seq
    START 8;

-- ----------------------------
-- Table structure for system_sms_code
-- ----------------------------
DROP TABLE IF EXISTS system_sms_code;
CREATE TABLE system_sms_code
(
    id          int8         NOT NULL,
    mobile      varchar(11)  NOT NULL,
    code        varchar(6)   NOT NULL,
    create_ip   varchar(15)  NOT NULL,
    scene       int2         NOT NULL,
    today_index int2         NOT NULL,
    used        int2         NOT NULL,
    used_time   timestamp    NULL     DEFAULT NULL,
    used_ip     varchar(255) NULL     DEFAULT NULL,
    creator     varchar(64)  NULL     DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL     DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    tenant_id   int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_sms_code
    ADD CONSTRAINT pk_system_sms_code PRIMARY KEY (id);

CREATE INDEX idx_system_sms_code_01 ON system_sms_code (mobile);



DROP SEQUENCE IF EXISTS system_sms_code_seq;
CREATE SEQUENCE system_sms_code_seq
    START 1;

-- ----------------------------
-- Table structure for system_sms_log
-- ----------------------------
DROP TABLE IF EXISTS system_sms_log;
CREATE TABLE system_sms_log
(
    id               int8         NOT NULL,
    channel_id       int8         NOT NULL,
    channel_code     varchar(63)  NOT NULL,
    template_id      int8         NOT NULL,
    template_code    varchar(63)  NOT NULL,
    template_type    int2         NOT NULL,
    template_content varchar(255) NOT NULL,
    template_params  varchar(255) NOT NULL,
    api_template_id  varchar(63)  NOT NULL,
    mobile           varchar(11)  NOT NULL,
    user_id          int8         NULL     DEFAULT NULL,
    user_type        int2         NULL     DEFAULT NULL,
    send_status      int2         NOT NULL DEFAULT 0,
    send_time        timestamp    NULL     DEFAULT NULL,
    api_send_code    varchar(63)  NULL     DEFAULT NULL,
    api_send_msg     varchar(255) NULL     DEFAULT NULL,
    api_request_id   varchar(255) NULL     DEFAULT NULL,
    api_serial_no    varchar(255) NULL     DEFAULT NULL,
    receive_status   int2         NOT NULL DEFAULT 0,
    receive_time     timestamp    NULL     DEFAULT NULL,
    api_receive_code varchar(63)  NULL     DEFAULT NULL,
    api_receive_msg  varchar(255) NULL     DEFAULT NULL,
    creator          varchar(64)  NULL     DEFAULT '',
    create_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          varchar(64)  NULL     DEFAULT '',
    update_time      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          int2         NOT NULL DEFAULT 0
);

ALTER TABLE system_sms_log
    ADD CONSTRAINT pk_system_sms_log PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_sms_log_seq;
CREATE SEQUENCE system_sms_log_seq
    START 1;

-- ----------------------------
-- Table structure for system_sms_template
-- ----------------------------
DROP TABLE IF EXISTS system_sms_template;
CREATE TABLE system_sms_template
(
    id              int8         NOT NULL,
    type            int2         NOT NULL,
    status          int2         NOT NULL,
    code            varchar(63)  NOT NULL,
    name            varchar(63)  NOT NULL,
    content         varchar(255) NOT NULL,
    params          varchar(255) NOT NULL,
    remark          varchar(255) NULL     DEFAULT NULL,
    api_template_id varchar(63)  NOT NULL,
    channel_id      int8         NOT NULL,
    channel_code    varchar(63)  NOT NULL,
    creator         varchar(64)  NULL     DEFAULT '',
    create_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64)  NULL     DEFAULT '',
    update_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2         NOT NULL DEFAULT 0
);

ALTER TABLE system_sms_template
    ADD CONSTRAINT pk_system_sms_template PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_sms_template_seq;
CREATE SEQUENCE system_sms_template_seq
    START 20;

-- ----------------------------
-- Table structure for system_social_client
-- ----------------------------
DROP TABLE IF EXISTS system_social_client;
CREATE TABLE system_social_client
(
    id            int8         NOT NULL,
    name          varchar(255) NOT NULL,
    social_type   int2         NOT NULL,
    user_type     int2         NOT NULL,
    client_id     varchar(255) NOT NULL,
    client_secret varchar(255) NOT NULL,
    agent_id      varchar(255) NULL     DEFAULT NULL,
    status        int2         NOT NULL,
    creator       varchar(64)  NULL     DEFAULT '',
    create_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       varchar(64)  NULL     DEFAULT '',
    update_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       int2         NOT NULL DEFAULT 0,
    tenant_id     int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_social_client
    ADD CONSTRAINT pk_system_social_client PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_social_client_seq;
CREATE SEQUENCE system_social_client_seq
    START 45;

-- ----------------------------
-- Table structure for system_social_user
-- ----------------------------
DROP TABLE IF EXISTS system_social_user;
CREATE TABLE system_social_user
(
    id             int8          NOT NULL,
    type           int2          NOT NULL,
    openid         varchar(32)   NOT NULL,
    token          varchar(256)  NULL     DEFAULT NULL,
    raw_token_info varchar(1024) NOT NULL,
    nickname       varchar(32)   NOT NULL,
    avatar         varchar(255)  NULL     DEFAULT NULL,
    raw_user_info  varchar(1024) NOT NULL,
    code           varchar(256)  NOT NULL,
    state          varchar(256)  NULL     DEFAULT NULL,
    creator        varchar(64)   NULL     DEFAULT '',
    create_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)   NULL     DEFAULT '',
    update_time    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2          NOT NULL DEFAULT 0,
    tenant_id      int8          NOT NULL DEFAULT 0
);

ALTER TABLE system_social_user
    ADD CONSTRAINT pk_system_social_user PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_social_user_seq;
CREATE SEQUENCE system_social_user_seq
    START 1;

-- ----------------------------
-- Table structure for system_social_user_bind
-- ----------------------------
DROP TABLE IF EXISTS system_social_user_bind;
CREATE TABLE system_social_user_bind
(
    id             int8        NOT NULL,
    user_id        int8        NOT NULL,
    user_type      int2        NOT NULL,
    social_type    int2        NOT NULL,
    social_user_id int8        NOT NULL,
    creator        varchar(64) NULL     DEFAULT '',
    create_time    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64) NULL     DEFAULT '',
    update_time    timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2        NOT NULL DEFAULT 0,
    tenant_id      int8        NOT NULL DEFAULT 0
);

ALTER TABLE system_social_user_bind
    ADD CONSTRAINT pk_system_social_user_bind PRIMARY KEY (id);

DROP SEQUENCE IF EXISTS system_social_user_bind_seq;
CREATE SEQUENCE system_social_user_bind_seq
    START 1;

-- ----------------------------
-- Table structure for system_tenant
-- ----------------------------
DROP TABLE IF EXISTS system_tenant;
CREATE TABLE system_tenant
(
    id              int8         NOT NULL,
    name            varchar(30)  NOT NULL,
    contact_user_id int8         NULL     DEFAULT NULL,
    contact_name    varchar(30)  NOT NULL,
    contact_mobile  varchar(500) NULL     DEFAULT NULL,
    status          int2         NOT NULL DEFAULT 0,
    websites        varchar(256) NULL     DEFAULT '',
    package_id      int8         NOT NULL,
    expire_time     timestamp    NOT NULL,
    account_count   int4         NOT NULL,
    creator         varchar(64)  NOT NULL DEFAULT '',
    create_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64)  NULL     DEFAULT '',
    update_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2         NOT NULL DEFAULT 0
);

ALTER TABLE system_tenant
    ADD CONSTRAINT pk_system_tenant PRIMARY KEY (id);




DROP SEQUENCE IF EXISTS system_tenant_seq;
CREATE SEQUENCE system_tenant_seq
    START 123;

-- ----------------------------
-- Table structure for system_tenant_package
-- ----------------------------
DROP TABLE IF EXISTS system_tenant_package;
CREATE TABLE system_tenant_package
(
    id          int8          NOT NULL,
    name        varchar(30)   NOT NULL,
    status      int2          NOT NULL DEFAULT 0,
    remark      varchar(256)  NULL     DEFAULT '',
    menu_ids    varchar(4096) NOT NULL,
    creator     varchar(64)   NOT NULL DEFAULT '',
    create_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)   NULL     DEFAULT '',
    update_time timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2          NOT NULL DEFAULT 0
);

ALTER TABLE system_tenant_package
    ADD CONSTRAINT pk_system_tenant_package PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_tenant_package_seq;
CREATE SEQUENCE system_tenant_package_seq
    START 113;

-- ----------------------------
-- Table structure for system_user_post
-- ----------------------------
DROP TABLE IF EXISTS system_user_post;
CREATE TABLE system_user_post
(
    id          int8        NOT NULL,
    user_id     int8        NOT NULL DEFAULT 0,
    post_id     int8        NOT NULL DEFAULT 0,
    creator     varchar(64) NULL     DEFAULT '',
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64) NULL     DEFAULT '',
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2        NOT NULL DEFAULT 0,
    tenant_id   int8        NOT NULL DEFAULT 0
);

ALTER TABLE system_user_post
    ADD CONSTRAINT pk_system_user_post PRIMARY KEY (id);



DROP SEQUENCE IF EXISTS system_user_post_seq;
CREATE SEQUENCE system_user_post_seq
    START 126;

-- ----------------------------
-- Table structure for system_user_role
-- ----------------------------
DROP TABLE IF EXISTS system_user_role;
CREATE TABLE system_user_role
(
    id          int8        NOT NULL,
    user_id     int8        NOT NULL,
    role_id     int8        NOT NULL,
    creator     varchar(64) NULL     DEFAULT '',
    create_time timestamp   NULL     DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64) NULL     DEFAULT '',
    update_time timestamp   NULL     DEFAULT CURRENT_TIMESTAMP,
    deleted     int2        NOT NULL DEFAULT 0,
    tenant_id   int8        NOT NULL DEFAULT 0
);

ALTER TABLE system_user_role
    ADD CONSTRAINT pk_system_user_role PRIMARY KEY (id);




DROP SEQUENCE IF EXISTS system_user_role_seq;
CREATE SEQUENCE system_user_role_seq
    START 49;

-- ----------------------------
-- Table structure for system_users
-- ----------------------------
DROP TABLE IF EXISTS system_users;
CREATE TABLE system_users
(
    id          int8         NOT NULL,
    username    varchar(30)  NOT NULL,
    password    varchar(100) NOT NULL DEFAULT '',
    nickname    varchar(30)  NOT NULL,
    remark      varchar(500) NULL     DEFAULT NULL,
    dept_id     int8         NULL     DEFAULT NULL,
    post_ids    varchar(255) NULL     DEFAULT NULL,
    email       varchar(50)  NULL     DEFAULT '',
    mobile      varchar(11)  NULL     DEFAULT '',
    sex         int2         NULL     DEFAULT 0,
    avatar      varchar(512) NULL     DEFAULT '',
    status      int2         NOT NULL DEFAULT 0,
    login_ip    varchar(50)  NULL     DEFAULT '',
    login_date  timestamp    NULL     DEFAULT NULL,
    creator     varchar(64)  NULL     DEFAULT '',
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     varchar(64)  NULL     DEFAULT '',
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     int2         NOT NULL DEFAULT 0,
    tenant_id   int8         NOT NULL DEFAULT 0
);

ALTER TABLE system_users
    ADD CONSTRAINT pk_system_users PRIMARY KEY (id);


DROP SEQUENCE IF EXISTS system_users_seq;
CREATE SEQUENCE system_users_seq
    START 142;
