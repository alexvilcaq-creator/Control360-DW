CREATE TABLE categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE
);
CREATE TABLE locations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  zone VARCHAR(20) NOT NULL,
  aisle VARCHAR(20) NOT NULL,
  shelf VARCHAR(20) NOT NULL,
  UNIQUE (zone, aisle, shelf)
);
CREATE TABLE customers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  document VARCHAR(30) NOT NULL UNIQUE,
  email VARCHAR(180)
);
CREATE TABLE shifts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE,
  starts_at TIME NOT NULL,
  ends_at TIME NOT NULL,
  grace_minutes INT NOT NULL CHECK (grace_minutes >= 0),
  days VARCHAR(13) NOT NULL
);
CREATE TABLE workers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  document VARCHAR(30) NOT NULL UNIQUE,
  email VARCHAR(180) NOT NULL UNIQUE,
  position VARCHAR(100) NOT NULL,
  active BIT NOT NULL,
  shift_id BIGINT,
  FOREIGN KEY (shift_id) REFERENCES shifts(id)
);
CREATE TABLE app_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','VENDEDOR','ALMACEN')),
  active BIT NOT NULL,
  worker_id BIGINT UNIQUE,
  FOREIGN KEY (worker_id) REFERENCES workers(id)
);
CREATE TABLE products (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku VARCHAR(60) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  price DECIMAL(12,2) NOT NULL CHECK (price >= 0),
  min_stock INT NOT NULL CHECK (min_stock >= 0),
  active BIT NOT NULL,
  category_id BIGINT NOT NULL,
  location_id BIGINT NOT NULL,
  FOREIGN KEY (category_id) REFERENCES categories(id),
  FOREIGN KEY (location_id) REFERENCES locations(id)
);
CREATE TABLE inventory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL UNIQUE,
  quantity INT NOT NULL CHECK (quantity >= 0),
  FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  warehouse_worker_id BIGINT,
  status VARCHAR(24) NOT NULL,
  total DECIMAL(18,2) NOT NULL CHECK (total >= 0),
  created_at DATETIME(6) NOT NULL,
  INDEX (created_at),
  INDEX (status),
  FOREIGN KEY (customer_id) REFERENCES customers(id),
  FOREIGN KEY (seller_id) REFERENCES app_users(id),
  FOREIGN KEY (warehouse_worker_id) REFERENCES workers(id)
);
CREATE TABLE order_lines (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL CHECK (quantity > 0),
  price DECIMAL(12,2) NOT NULL,
  UNIQUE (order_id, product_id),
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE order_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE TABLE stock_movements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  order_id BIGINT,
  user_id BIGINT NOT NULL,
  delta INT NOT NULL,
  balance INT NOT NULL,
  type VARCHAR(20) NOT NULL,
  note VARCHAR(250) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  FOREIGN KEY (product_id) REFERENCES products(id),
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE TABLE attendance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  worker_id BIGINT NOT NULL,
  shift_id BIGINT NOT NULL,
  work_date DATE NOT NULL,
  entry DATETIME(6) NOT NULL,
  exited_at DATETIME(6),
  scheduled_end DATETIME(6) NOT NULL,
  late_minutes INT NOT NULL,
  additional_minutes INT NOT NULL,
  approved_minutes INT NOT NULL,
  UNIQUE (worker_id, work_date),
  FOREIGN KEY (worker_id) REFERENCES workers(id),
  FOREIGN KEY (shift_id) REFERENCES shifts(id)
);
CREATE TABLE notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(40) NOT NULL,
  title VARCHAR(120) NOT NULL,
  message VARCHAR(250) NOT NULL,
  order_id BIGINT,
  created_at DATETIME(6) NOT NULL,
  INDEX (created_at),
  FOREIGN KEY (order_id) REFERENCES orders(id)
);
CREATE TABLE notification_reads (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  notification_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  UNIQUE (notification_id, user_id),
  FOREIGN KEY (notification_id) REFERENCES notifications(id),
  FOREIGN KEY (user_id) REFERENCES app_users(id)
);
