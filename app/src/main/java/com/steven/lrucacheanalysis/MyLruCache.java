package com.steven.lrucacheanalysis;

import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

/**
 * Created by StevenWang on 16/5/25.
 */
public class MyLruCache extends LruCache<String , Bitmap> {
    /**
     * @param maxSize for caches that do not override {@link #sizeOf}, this is
     *                the maximum number of entries in the cache. For all other caches,
     *                this is the maximum sum of the sizes of the entries in this cache.
     */
    public MyLruCache(int maxSize) {
        super(maxSize);
    }

    @Override
    protected int sizeOf(String key, Bitmap value) {
        int size  = value.getRowBytes() * value.getHeight();
        return size;
    }

    @Override
    protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
        super.entryRemoved(evicted, key, oldValue, newValue);


    }
}
