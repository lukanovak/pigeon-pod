INSERT INTO user (id, username, password, salt, email, role)
SELECT 0,
       'root',
       '3aaf4d8727ead6bcf61512ace7801a4b',
       'fYMtZ=DHj^$G',
       'root@pigeon.pod',
       'ADMIN' WHERE NOT EXISTS (SELECT 1 FROM user WHERE id = 0);