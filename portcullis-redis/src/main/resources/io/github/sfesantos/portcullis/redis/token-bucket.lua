-- KEYS[1] bucket, ARGV[1] capacity, ARGV[2] window in microseconds
-- Uses the Redis clock so instances with skewed clocks still share one limit.
local capacity = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local per_token = window / capacity

local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000000 + tonumber(time[2])

local state = redis.call('HMGET', KEYS[1], 't', 'ts')
local tokens = tonumber(state[1]) or capacity
local last = tonumber(state[2]) or now
tokens = math.min(capacity, tokens + math.max(0, now - last) / per_token)

local allowed = 0
local wait = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
else
    wait = math.ceil((1 - tokens) * per_token)
end

redis.call('HSET', KEYS[1], 't', tostring(tokens), 'ts', tostring(now))
redis.call('PEXPIRE', KEYS[1], math.ceil(window / 1000))
return {allowed, wait}
