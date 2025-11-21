-- fixed_window.lua
-- KEYS[1]: rate limit key
-- ARGV[1]: limit
-- ARGV[2]: window size in milliseconds

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

local current = redis.call('INCR', key)

if current == 1 then
    redis.call('PEXPIRE', key, window)
end

local ttl = redis.call('PTTL', key)

if current > limit then
    return {0, ttl} -- 0 indicates blocked
else
    return {1, ttl} -- 1 indicates allowed
end
