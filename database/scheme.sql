--  Chạy: mysql -u root -p auction_db

CREATE DATABASE IF NOT EXISTS auction_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auction_db;

--  1. USERS

CREATE TABLE IF NOT EXISTS users (
    user_id     VARCHAR(36)  NOT NULL,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(64)  NOT NULL,       -- SHA-256 hex
    email       VARCHAR(100) NOT NULL,
    role        ENUM('BIDDER','SELLER','ADMIN') NOT NULL DEFAULT 'BIDDER',
    is_banned   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),
    UNIQUE KEY uq_username (username),
    UNIQUE KEY uq_email    (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

--  2. ITEMS

CREATE TABLE IF NOT EXISTS items (
    item_id         VARCHAR(36)   NOT NULL,
    seller_id       VARCHAR(36)   NOT NULL,
    type            ENUM('ELECTRONICS','ART','VEHICLE') NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     TEXT,
    starting_price  DECIMAL(15,2) NOT NULL CHECK (starting_price >= 0),
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (item_id),
    CONSTRAINT fk_items_seller
        FOREIGN KEY (seller_id) REFERENCES users(user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Index tìm kiếm theo seller
CREATE INDEX idx_items_seller ON items(seller_id);
-- Index tìm kiếm theo type
CREATE INDEX idx_items_type   ON items(type);

--  3. AUCTIONS

CREATE TABLE IF NOT EXISTS auctions (
    auction_id      VARCHAR(36)   NOT NULL,
    item_id         VARCHAR(36)   NOT NULL,
    seller_id       VARCHAR(36)   NOT NULL,
    starting_price  DECIMAL(15,2) NOT NULL,
    current_price   DECIMAL(15,2) NOT NULL,
    status          ENUM('OPEN','RUNNING','FINISHED','PAID','CANCELED')
                    NOT NULL DEFAULT 'OPEN',
    start_time      DATETIME      NOT NULL,
    end_time        DATETIME      NOT NULL,
    winner_id       VARCHAR(36)   DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (auction_id),
    CONSTRAINT fk_auctions_item
        FOREIGN KEY (item_id)    REFERENCES items(item_id)   ON DELETE RESTRICT,
    CONSTRAINT fk_auctions_seller
        FOREIGN KEY (seller_id)  REFERENCES users(user_id)   ON DELETE CASCADE,
    CONSTRAINT fk_auctions_winner
        FOREIGN KEY (winner_id)  REFERENCES users(user_id)   ON DELETE SET NULL,
    CONSTRAINT chk_auction_time
        CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Index thường dùng
CREATE INDEX idx_auctions_status     ON auctions(status);
CREATE INDEX idx_auctions_seller     ON auctions(seller_id);
CREATE INDEX idx_auctions_end_time   ON auctions(end_time);

--  4. BID_TRANSACTIONS

CREATE TABLE IF NOT EXISTS bid_transactions (
    transaction_id  VARCHAR(36)   NOT NULL,
    auction_id      VARCHAR(36)   NOT NULL,
    bidder_id       VARCHAR(36)   NOT NULL,
    bid_amount      DECIMAL(15,2) NOT NULL CHECK (bid_amount > 0),
    bid_time        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_auto_bid     TINYINT(1)    NOT NULL DEFAULT 0,

    PRIMARY KEY (transaction_id),
    CONSTRAINT fk_bids_auction
        FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    CONSTRAINT fk_bids_bidder
        FOREIGN KEY (bidder_id)  REFERENCES users(user_id)       ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Index truy vấn lịch sử bid
CREATE INDEX idx_bids_auction  ON bid_transactions(auction_id, bid_time);
CREATE INDEX idx_bids_bidder   ON bid_transactions(bidder_id);
CREATE INDEX idx_bids_amount   ON bid_transactions(auction_id, bid_amount DESC);
