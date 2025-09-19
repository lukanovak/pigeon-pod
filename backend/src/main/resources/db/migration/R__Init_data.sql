INSERT INTO user (id, username, password, salt)
SELECT 0,
       'root',
       'a57b7e53b7253a4ab71eac631647f61f',
       'fYMtZ=DHj^$G'
    WHERE NOT EXISTS (SELECT 1 FROM user WHERE id = 0);