local key = KEYS[1]
local window_start = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local max_count = tonumber(ARGV[3])
local expire_seconds = tonumber(ARGV[4])
local member = ARGV[5]

redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
local count = redis.call('ZCARD', key)

if count < max_count then
    redis.call('ZADD', key, now, member)
    redis.call('EXPIRE', key, expire_seconds)
    return count + 1
end

redis.call('EXPIRE', key, expire_seconds)
return -1
