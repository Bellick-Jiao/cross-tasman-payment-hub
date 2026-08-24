-- ==============================================================================
-- Cross-Tasman ISO 20022 Payment Integration Gateway (CT-PIG)
-- Database Initialization Script (PostgreSQL)
-- ==============================================================================

-- 1. Create Users Table
CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Create Accounts Table (Financial-grade balances)
CREATE TABLE IF NOT EXISTS accounts (
    account_no VARCHAR(30) PRIMARY KEY,
    user_id INT REFERENCES users(user_id) ON DELETE CASCADE,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) NOT NULL DEFAULT 'NZD',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create Transactions Table
CREATE TABLE IF NOT EXISTS transactions (
    tx_id SERIAL PRIMARY KEY,
    tx_reference VARCHAR(50) UNIQUE NOT NULL,
    sender_account VARCHAR(30) NOT NULL,
    receiver_account VARCHAR(30) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Insert Mock Users
-- Password hash corresponds to Bcrypt('password123')
INSERT INTO users (username, password_hash, email) VALUES
('alice_kiwi', '$2a$10$eImiTXuWVxfM37uY4bDfV.999fM3fGkY7W7uVf7pXpB7FqKqfKaJy', 'alice@kiwi-bank.co.nz'),
('bob_kangaroo', '$2a$10$eImiTXuWVxfM37uY4bDfV.999fM3fGkY7W7uVf7pXpB7FqKqfKaJy', 'bob@kangaroo-bank.com.au')
ON CONFLICT (username) DO NOTHING;

-- 5. Insert Mock Financial Accounts
-- Alice: New Zealand domestic account with $15,000.00 NZD starting balance
-- Bob: Australian domestic account with $5,500.00 AUD starting balance
INSERT INTO accounts (account_no, user_id, balance, currency, status) VALUES
('NZ-987654321', 1, 15000.00, 'NZD', 'ACTIVE'),
('AU-123456789', 2, 5500.00, 'AUD', 'ACTIVE')
ON CONFLICT (account_no) DO NOTHING;

-- 6. Insert Seed Transaction Record
INSERT INTO transactions (tx_reference, sender_account, receiver_account, amount, currency, status) VALUES
('TX-INIT9999', 'NZ-987654321', 'AU-123456789', 150.00, 'NZD', 'COMPLETED')
ON CONFLICT (tx_reference) DO NOTHING;
