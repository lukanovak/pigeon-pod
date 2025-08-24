CREATE TABLE IF NOT EXISTS config
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT              NOT NULL,
    value     TEXT              NULL,
    is_public INTEGER DEFAULT 0 NOT NULL,
    UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS user
(
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    username   TEXT                                NOT NULL,
    password   TEXT                                NOT NULL,
    salt       TEXT                                NOT NULL,
    email      TEXT                                NULL,
    role       TEXT      DEFAULT 'USER'            NOT NULL,
    status     INTEGER   DEFAULT 1                 NOT NULL,
    api_key    TEXT                                NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (username),
    UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS channel
(
    id               TEXT      NOT NULL UNIQUE,
    handler          TEXT      NOT NULL UNIQUE,
    name             TEXT      NOT NULL,
    avatar_url       TEXT      NOT NULL,
    description      TEXT      NOT NULL,
    registered_at    TIMESTAMP NULL,
    video_count      INTEGER   NULL,
    subscriber_count INTEGER   NULL,
    view_count       INTEGER   NULL,
    channel_source   TEXT      NOT NULL
);

CREATE TABLE IF NOT EXISTS program
(
    id                TEXT      NOT NULL UNIQUE,
    channel_id        TEXT      NOT NULL,
    position          INTEGER   NOT NULL,
    title             TEXT      NOT NULL,
    description       TEXT      NOT NULL,
    published_at      TIMESTAMP NULL,
    default_cover_url TEXT      NOT NULL,
    max_cover_url     TEXT      NOT NULL,
    FOREIGN KEY (channel_id) REFERENCES channel (id)
);

CREATE INDEX IF NOT EXISTS idx_program_title ON program (title);
CREATE INDEX IF NOT EXISTS idx_program_description ON program (description);
CREATE INDEX IF NOT EXISTS idx_program_channel_id ON program (channel_id);
