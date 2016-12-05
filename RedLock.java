package com.cus.redis;

import redis.clients.jedis.Jedis;

/**
 * 使用redis实现分布式锁
 *
 * @author beyond
 * @version 1.0
 * @date 2016/11/30
 */
public class RedLock {
    private static final String LOCK_KEY = "redis_lock";
    private static final int RETRY_TIME = 10 * 1000; //等待锁的时间
    private static final int EXPIRE_TIME = 60 * 1000;//锁超时的时间
    private boolean locked;
    private long lockValue;

    public synchronized boolean lock(Jedis jedis){
        int retryTime = RETRY_TIME;
        try {
            while (retryTime > 0) {
                lockValue = System.currentTimeMillis() + EXPIRE_TIME + 1;//锁到期时间
                String lockValueStr = String.valueOf(lockValue);
                //判断能否获取锁
                if (jedis.setnx(LOCK_KEY, lockValueStr) == 1) {
                    //成功获取锁
                    locked = true;
                    return locked;
                }
                String currLockVal = jedis.get(LOCK_KEY);
                // 判断锁是否已经失效
                if (currLockVal != null && Long.valueOf(currLockVal) < System.currentTimeMillis()) {
                    //锁已经失效，使用命令getset设置最新的过期时间
                    String oldLockVal = jedis.getSet(LOCK_KEY, lockValueStr);
                    //判断锁是否已经被抢占
                    if (oldLockVal != null && oldLockVal.equals(currLockVal)) {
                        locked = true;
                        return locked;
                    }
                }
                retryTime -= 100;
                Thread.sleep(100);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    public synchronized void unlock(Jedis jedis){
        if(locked) {
            String currLockVal = jedis.get(LOCK_KEY);
            if(currLockVal != null && Long.valueOf(currLockVal) == lockValue) {
                jedis.del(LOCK_KEY);
                locked = false;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Jedis jedis = new Jedis("192.168.75.129", 6379);
        RedLock redLock = new RedLock();
        if(redLock.lock(jedis)) {
            System.out.println(Thread.currentThread().getName() + ": 获得锁！");
            Thread.sleep(150000);
            System.out.println(Thread.currentThread().getName() + ": 处理完成！");
            redLock.unlock(jedis);
            System.out.println(Thread.currentThread().getName() + ": 释放锁！");
        }else {
            System.out.println("get lock fail!!!");
        }
    }
}
