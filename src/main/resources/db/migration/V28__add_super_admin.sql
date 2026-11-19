ALTER TABLE tb_admin ADD COLUMN super_admin BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE tb_admin SET super_admin = TRUE WHERE nickname = 'gsmstarup';
