local attempts_key = KEYS[1]
local lock_key = KEYS[2]

local max_attempts = tonumber(ARGV[1])
local window_millis = tonumber(ARGV[2])
local lockout_millis = tonumber(ARGV[3])
local mode = ARGV[4]

if mode == "check" then
    if redis.call("EXISTS", lock_key) == 1 then
        return 0
    end
    return 1
end

if mode == "fail" then
    if redis.call("EXISTS", lock_key) == 1 then
        return 0
    end

    local attempts = redis.call("INCR", attempts_key)
    if attempts == 1 then
        redis.call("PEXPIRE", attempts_key, window_millis)
    end

    if attempts >= max_attempts then
        redis.call("SET", lock_key, "locked", "PX", lockout_millis)
        redis.call("DEL", attempts_key)
        return 0
    end
    return 1
end

if mode == "reset" then
    redis.call("DEL", attempts_key)
    redis.call("DEL", lock_key)
    return 1
end

return 1

