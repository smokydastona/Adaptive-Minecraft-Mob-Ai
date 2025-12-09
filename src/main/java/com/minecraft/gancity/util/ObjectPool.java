package com.minecraft.gancity.util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Generic object pool for reducing garbage collection overhead
 * Reuses frequently allocated objects instead of creating new ones
 */
public class ObjectPool<T> {
    
    private final ConcurrentLinkedQueue<T> pool = new ConcurrentLinkedQueue<>();
    private final Supplier<T> factory;
    private final int maxSize;
    private int currentSize = 0;
    
    /**
     * Create object pool with factory and max size
     */
    public ObjectPool(Supplier<T> factory, int maxSize) {
        this.factory = factory;
        this.maxSize = maxSize;
    }
    
    /**
     * Acquire object from pool or create new
     */
    public T acquire() {
        T obj = pool.poll();
        if (obj == null) {
            obj = factory.get();
            synchronized (this) {
                currentSize++;
            }
        }
        return obj;
    }
    
    /**
     * Return object to pool for reuse
     */
    public void release(T obj) {
        if (obj == null) {
            return;
        }
        
        synchronized (this) {
            if (currentSize < maxSize) {
                pool.offer(obj);
            } else {
                currentSize--;
            }
        }
    }
    
    /**
     * Get current pool size
     */
    public int size() {
        return pool.size();
    }
    
    /**
     * Clear pool
     */
    public void clear() {
        pool.clear();
        synchronized (this) {
            currentSize = 0;
        }
    }
}
