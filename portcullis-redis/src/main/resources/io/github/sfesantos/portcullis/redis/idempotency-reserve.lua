-- KEYS[1] key, ARGV[1] lease in ms. Returns {1} when acquired, {0, stored value} otherwise.
if redis.call('SET', KEYS[1], 'p', 'NX', 'PX', ARGV[1]) then
    return {1}
end
return {0, redis.call('GET', KEYS[1])}
