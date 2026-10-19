create table roles (
    id   uuid primary key default gen_random_uuid(),
    name varchar(50) not null unique
);

create table users (
    id         uuid primary key default gen_random_uuid(),
    username   varchar(60)  not null unique,
    password   varchar(100) not null,
    full_name  varchar(150) not null,
    enabled    boolean      not null default true,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create table user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role_id uuid not null references roles(id) on delete cascade,
    primary key (user_id, role_id)
);

create table job_offer (
    id                 uuid primary key default gen_random_uuid(),
    title              varchar(200) not null,
    company            varchar(150) not null,
    description        text         not null,
    requirements       text         not null,
    location           varchar(150),
    salary             varchar(100),
    employment_type    varchar(50),
    source_url         varchar(500) not null,
    source_name        varchar(60),
    external_reference varchar(120),
    extracted_at       timestamptz  not null,
    publication_url    varchar(500),
    status             varchar(20)  not null,
    assigned_user_id   uuid references users(id) on delete set null,
    locked_at          timestamptz,
    lock_expires_at    timestamptz,
    approved_at        timestamptz,
    published_at       timestamptz,
    created_at         timestamptz  not null default now(),
    created_by         uuid references users(id) on delete set null,
    updated_at         timestamptz  not null default now(),
    updated_by         uuid references users(id) on delete set null,
    version            bigint       not null default 0,
    constraint job_offer_status_chk
        check (status in ('REGISTERED','APPROVED','IN_PROGRESS','PUBLISHED','REJECTED')),
    constraint job_offer_lock_chk
        check ((status =  'IN_PROGRESS' and assigned_user_id is not null
                                          and locked_at is not null
                                          and lock_expires_at is not null)
            or (status <> 'IN_PROGRESS' and assigned_user_id is null
                                          and locked_at is null
                                          and lock_expires_at is null)),
    constraint job_offer_dedupe_uk unique (source_name, external_reference)  -- §24
);

create index idx_job_offer_status       on job_offer (status);
create index idx_job_offer_assigned     on job_offer (assigned_user_id);
create index idx_job_offer_lock_expires on job_offer (lock_expires_at);
create index idx_job_offer_company      on job_offer (company);

create table job_offer_status_history (
    id           bigserial primary key,
    job_offer_id uuid not null references job_offer(id) on delete cascade,
    old_status   varchar(20) not null,
    new_status   varchar(20) not null,
    changed_by   uuid references users(id) on delete set null,
    actor        varchar(60) not null default 'SYSTEM',   -- 'SYSTEM' o el username
    changed_at   timestamptz not null default now(),
    reason       varchar(300)
);
create index idx_history_offer on job_offer_status_history (job_offer_id, changed_at);

create table audit_event (
    id          bigserial primary key,
    occurred_at timestamptz not null default now(),
    user_id     uuid,
    username    varchar(60),
    action      varchar(30) not null,
    entity_type varchar(50),
    entity_id   varchar(80),
    old_values  jsonb,
    new_values  jsonb,
    ip_address  inet,
    success     boolean not null,
    error_code  varchar(60),
    reason      varchar(300)
);
create index idx_audit_occurred on audit_event (occurred_at desc);
create index idx_audit_entity   on audit_event (entity_type, entity_id);
create index idx_audit_action   on audit_event (action);
create index idx_audit_user     on audit_event (user_id);