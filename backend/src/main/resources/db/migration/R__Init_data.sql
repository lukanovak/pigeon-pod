INSERT INTO user (id, username, password, salt)
SELECT 0,
       'root',
       '3aaf4d8727ead6bcf61512ace7801a4b',
       'fYMtZ=DHj^$G'
    WHERE NOT EXISTS (SELECT 1 FROM user WHERE id = 0);