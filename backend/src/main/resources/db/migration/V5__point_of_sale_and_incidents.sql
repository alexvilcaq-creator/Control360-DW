ALTER TABLE products ADD COLUMN barcode VARCHAR(64) NULL, ADD UNIQUE KEY uk_products_barcode (barcode);

CREATE TABLE order_payments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL UNIQUE,
  cashier_id BIGINT NOT NULL,
  method VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  cash_received DECIMAL(18,2),
  cash_change DECIMAL(18,2),
  recorded_at DATETIME(6) NOT NULL,
  voided_by BIGINT,
  voided_at DATETIME(6),
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (cashier_id) REFERENCES app_users(id),
  FOREIGN KEY (voided_by) REFERENCES app_users(id)
);

CREATE TABLE inventory_incidents (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reporter_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  request_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  type VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  observation VARCHAR(500) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  reviewer_id BIGINT,
  review_note VARCHAR(500),
  reviewed_at DATETIME(6),
  UNIQUE KEY uk_incident_request (reporter_id, request_key),
  FOREIGN KEY (reporter_id) REFERENCES app_users(id),
  FOREIGN KEY (product_id) REFERENCES products(id),
  FOREIGN KEY (reviewer_id) REFERENCES app_users(id)
);
