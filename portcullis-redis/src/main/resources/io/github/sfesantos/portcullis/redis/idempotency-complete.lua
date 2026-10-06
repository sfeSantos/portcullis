-- KEYS[1] key, ARGV[1] value, ARGV[2] ttl in ms
redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
return {1}
