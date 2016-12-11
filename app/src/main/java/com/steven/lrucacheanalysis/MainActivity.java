package com.steven.lrucacheanalysis;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.steven.android21_lrucachebitmap.R;
import com.steven.lrucacheanalysis.disklrucache.DiskLruCache;
import com.steven.lrucacheanalysis.helper.HttpURLConnHelper;
import com.steven.lrucacheanalysis.helper.SDCardHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "MainActivity";
    private Context mContxt = this;
    private ImageView imageView_main_show;
    private ImageView imageView_main_thumbnail;
    private MyLruCache lruCache = null;
    private static final String URL_STRING = "http://img.my.csdn" +
            ".net/uploads/201309/01/1378037192_8379.jpg";
    private String fileName = "";
    private Handler handler = new Handler();
    DiskLruCache mDiskLruCache = null;
    String imageUrl = "http://img.my.csdn.net/uploads/201309/01/1378037235_7476.jpg";
    DiskLruCache.Editor editor;
    private String diskKey;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        initLruCache();
    }

    private void initLruCache()
    {
        long maxMemory = Runtime.getRuntime()
                                .maxMemory();
        long freeMemory = Runtime.getRuntime()
                                 .freeMemory();
        long totalMemory = Runtime.getRuntime()
                                  .totalMemory();
        setTitle(maxMemory + ":" + freeMemory + ":" + totalMemory);
        lruCache = new MyLruCache((int) (maxMemory / 8));
        diskKey = hashKeyForDisk(imageUrl);


        try
        {
            File cacheDir = getDiskCacheDir(mContxt, "bitmap");

            Toast.makeText(mContxt, cacheDir.toString(), Toast.LENGTH_SHORT)
                 .show();

            if (!cacheDir.exists())
            {
                cacheDir.mkdirs();
            }
            mDiskLruCache = DiskLruCache.open(cacheDir,
                                              getAppVersion(mContxt),
                                              1,
                                              10 * 1024 * 1024);
            editor = mDiskLruCache.edit(diskKey);

        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void initView()
    {
        imageView_main_show = (ImageView) findViewById(R.id.imageView_main_show);
        imageView_main_thumbnail = (ImageView) findViewById(R.id.imageView_main_thumbnail);

        fileName = URL_STRING.substring(URL_STRING.lastIndexOf("/") + 1);
        Log.i(TAG, "-->>" + fileName);
    }

    public void clickView(View view)
    {
        switch (view.getId())
        {
            case R.id.button_main_load:
                Bitmap bm = null;
                //从缓存中找图片
                bm = getBitmapFromCache(diskKey);
//              如果缓存中有就设置，如果没有就去网上加载。
                if (bm != null)
                {
                    imageView_main_show.setImageBitmap(bm);
                } else
                {
//                从网络下载图片
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
//                           从网络上拿到输入流，然后写到输出流，然后commit
                            try
                            {
                                HttpURLConnection conn = null;
                                URL url = new URL(imageUrl);
                                conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("GET");
                                conn.setDoInput(true);
                                conn.setConnectTimeout(5000);
                                conn.connect();

                                if (conn.getResponseCode() == 200)
                                {
                                    DiskLruCache.Snapshot snapShot = null;
                                    FileInputStream fileInputStream = null;
                                    FileDescriptor fileDescriptor = null;
                                    InputStream is = conn.getInputStream();
                                    OutputStream os = editor.newOutputStream(0);

                                    BufferedInputStream in = new BufferedInputStream(is, 8 * 1024);
                                    BufferedOutputStream out = new BufferedOutputStream(os,
                                                                                        8 * 1024);

                                    int b;
                                    while ((b = in.read()) != -1)
                                    {
                                        out.write(b);
                                    }
//                          数据被写入缓存
                                    editor.commit();

                                    snapShot = mDiskLruCache.get(diskKey);

                                    if (snapShot != null)
                                    {
                                        fileInputStream = (FileInputStream) snapShot.getInputStream(0);
                                        fileDescriptor = fileInputStream.getFD();
                                        final Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                                        Log.i("aaaa","网络");

                                        handler.post(new Runnable()
                                        {
                                            @Override
                                            public void run()
                                            {
                                                Log.i("aaaa", "进入Handler");
                                                imageView_main_show.setImageBitmap(bitmap);
                                            }
                                        });
//                                      放到内存缓存里
                                        if (bitmap != null)
                                        {
                                            lruCache.put(diskKey, bitmap);
                                        }
                                    }
                                }

                            } catch (MalformedURLException e)
                            {
                                e.printStackTrace();
                            } catch (IOException e)
                            {
                                e.printStackTrace();
                            }


                        }
                    }).start();
                }
                break;
        }
    }

    private Bitmap getBitmapFromCache(String key)
    {
        Bitmap bm = null;
        DiskLruCache.Snapshot snapShot = null;
        FileInputStream fileInputStream = null;
        FileDescriptor fileDescriptor = null;
        //从强引用中找图片
        bm = lruCache.get(key);
        if (bm != null)
        {
            Log.i("aaaa","内存");
            return bm;
        } else
        {

            try
            {
                snapShot = mDiskLruCache.get(key);
                if (snapShot != null)
                {
                    fileInputStream = (FileInputStream) snapShot.getInputStream(0);
                    fileDescriptor = fileInputStream.getFD();
                    bm = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                    Log.i("aaaa","磁盘");

//               放到内存缓存里
                    if (bm != null)
                    {
                        lruCache.put(diskKey, bm);
                    }
                    return bm;
                }
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return null;
    }

    public String hashKeyForDisk(String key)
    {
        String cacheKey;
        try
        {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e)
        {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++)
        {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1)
            {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    public int getAppVersion(Context context)
    {
        try
        {
            PackageInfo info = context.getPackageManager()
                                      .getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }
        return 1;
    }

    public File getDiskCacheDir(Context context, String uniqueName)
    {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable())
        {
            cachePath = context.getExternalCacheDir()
                               .getPath();
        } else
        {
            cachePath = context.getCacheDir()
                               .getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

}
