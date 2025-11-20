-- Initialize MySQL databases for microservices
-- This script runs automatically when MySQL container starts for the first time

-- Create userservice database if it doesn't exist
CREATE DATABASE IF NOT EXISTS userservice CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create app_user with permissions for both databases
CREATE USER IF NOT EXISTS 'app_user'@'%' IDENTIFIED BY 'password';

-- Grant privileges
GRANT ALL PRIVILEGES ON authservice.* TO 'app_user'@'%';
GRANT ALL PRIVILEGES ON userservice.* TO 'app_user'@'%';
GRANT ALL PRIVILEGES ON authservice.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON userservice.* TO 'root'@'%';

-- Flush privileges
FLUSH PRIVILEGES;

