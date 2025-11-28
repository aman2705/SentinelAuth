-- brute_force.lua
-- KEYS[1]: failure count key
-- KEYS[2]: lockout key
-- ARGV[1]: max failures
-- ARGV[2]: failure window in seconds
-- ARGV[3]: lockout duration in seconds

local failure_key = KEYS[1]
local lockout_key = KEYS[2]
local max_failures = tonumber(ARGV[1])
local failure_window = tonumber(ARGV[2])
local lockout_duration = tonumber(ARGV[3])

-- Check if locked out
if redis.call('EXISTS', lockout_key) == 1 then
    local ttl = redis.call('TTL', lockout_key)
    return {0, ttl} -- 0: Locked, return remaining TTL
end

-- Increment failure count
local failures = redis.call('INCR', failure_key)

if failures == 1 then
    redis.call('EXPIRE', failure_key, failure_window)
end

if failures >= max_failures then
    redis.call('SET', lockout_key, "LOCKED")
    redis.call('EXPIRE', lockout_key, lockout_duration)
    redis.call('DEL', failure_key) -- Reset failures after lockout
    return {0, lockout_duration} -- Locked
end

return {1, 0} -- 1: Allowed
