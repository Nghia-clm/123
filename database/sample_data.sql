SET SQL_SAFE_UPDATES = 0;

USE auction_db;

DELETE FROM users;

INSERT INTO users (user_id, username, password, email, role, is_banned) VALUES
-- Admin
('user-admin',
 'admin',
 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f',
 'admin@auction.com',
 'ADMIN',
 0);