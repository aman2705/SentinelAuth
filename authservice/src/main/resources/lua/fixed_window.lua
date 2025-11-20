local key = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_millis = tonumber(ARGV[2])

if max_requests <= 0 then
    return 1
end

local current = redis.call("GET", key)
if current and tonumber(current) >= max_requests then
    return 0
end

current = redis.call("INCR", key)

if current == 1 then
    redis.call("PEXPIRE", key, window_millis)
end

if current > max_requests then
    return 0
end

return 1

