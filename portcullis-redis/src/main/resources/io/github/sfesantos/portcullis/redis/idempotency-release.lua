-- KEYS[1] key. Only an in progress reservation is dropped; a stored result stays.
if redis.call('GET', KEYS[1]) == 'p' then
    redis.call('DEL', KEYS[1])
end
return {1}
