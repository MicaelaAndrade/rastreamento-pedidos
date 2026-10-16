CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    email         TEXT    NOT NULL UNIQUE,
    password_hash TEXT    NOT NULL,
    created_at    TEXT    NOT NULL
);

CREATE TABLE orders (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    status               TEXT    NOT NULL
        CHECK (status IN ('RECEBIDO', 'EM_PREPARO', 'SAIU_PARA_ENTREGA', 'ENTREGUE', 'CANCELADO')),
    customer_name        TEXT    NOT NULL,
    customer_phone       TEXT    NOT NULL,
    address_street       TEXT    NOT NULL,
    address_number       TEXT    NOT NULL,
    address_complement   TEXT,
    address_neighborhood TEXT    NOT NULL,
    address_city         TEXT    NOT NULL,
    address_zip_code     TEXT    NOT NULL,
    created_by           INTEGER NOT NULL REFERENCES users (id),
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL,
    version              INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_items (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id         INTEGER NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    name             TEXT    NOT NULL,
    quantity         INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE order_status_history (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id   INTEGER NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    status     TEXT    NOT NULL,
    changed_at TEXT    NOT NULL,
    changed_by TEXT    NOT NULL
);

CREATE INDEX idx_order_status_history_order ON order_status_history (order_id);
