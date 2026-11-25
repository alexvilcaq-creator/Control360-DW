CREATE TABLE order_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  request_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  fingerprint CHAR(64) CHARACTER SET ascii NOT NULL,
  order_id BIGINT UNIQUE,
  UNIQUE (user_id, request_key),
  FOREIGN KEY (user_id) REFERENCES app_users(id),
  FOREIGN KEY (order_id) REFERENCES orders(id)
);
