-- Add custom title and custom cover storage path for feeds
ALTER TABLE channel ADD COLUMN custom_title TEXT NULL;
ALTER TABLE channel ADD COLUMN custom_cover_path TEXT NULL;

ALTER TABLE playlist ADD COLUMN custom_title TEXT NULL;
ALTER TABLE playlist ADD COLUMN custom_cover_path TEXT NULL;

