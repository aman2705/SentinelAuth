-- sliding_window.lua
-- KEYS[1]: rate limit key
-- ARGV[1]: limit
-- ARGV[2]: window size in milliseconds
-- ARGV[3]: current timestamp in milliseconds

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local window_start = now - window

-- Remove timestamps older than the window start
-- LREM is not suitable here because we need to remove by value range, but LISTs are ordered by insertion.
-- Since we push new timestamps to the right (RPUSH), the oldest are on the left.
-- We can check the first element (LINDEX 0) and LPOP if it's expired.

local len = redis.call('LLEN', key)

while len > 0 do
    local timestamp = tonumber(redis.call('LINDEX', key, 0))
    if timestamp < window_start then
        redis.call('LPOP', key)
        len = len - 1
    else
        break
    end
end

if len < limit then
    redis.call('RPUSH', key, now)
    redis.call('PEXPIRE', key, window) -- Refresh TTL
    return 1 -- Allowed
else
    return 0 -- Blocked
end
