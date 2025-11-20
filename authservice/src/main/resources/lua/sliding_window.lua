local key = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_millis = tonumber(ARGV[2])
local now_millis = tonumber(ARGV[3])

if max_requests <= 0 then
    return 1
end

local boundary = now_millis - window_millis
redis.call("ZREMRANGEBYSCORE", key, 0, boundary)

local current = redis.call("ZCARD", key)
if current >= max_requests then
    return 0
end

redis.call("ZADD", key, now_millis, now_millis)
redis.call("PEXPIRE", key, window_millis)

return 1

