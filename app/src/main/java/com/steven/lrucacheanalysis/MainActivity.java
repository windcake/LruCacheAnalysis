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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        initLruCache();
    }

    private void initLruCache() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        setTitle(maxMemory + ":" + freeMemory + ":" + totalMemory);
        lruCache = new MyLruCache((int) (maxMemory / 8));
        String key = hashKeyForDisk(imageUrl);


        try {
            File cacheDir = getDiskCacheDir(mContxt, "bitmap");

            Toast.makeText(mContxt, cacheDir.toString(), Toast.LENGTH_SHORT).show();

            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            mDiskLruCache = DiskLruCache.open(cacheDir, getAppVersion(mContxt), 1, 10 * 1024 * 1024);
            editor = mDiskLruCache.edit(key);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initView() {
        imageView_main_show = (ImageView) findViewById(R.id.imageView_main_show);
        imageView_main_thumbnail = (ImageView) findViewById(R.id.imageView_main_thumbnail);

        fileName = URL_STRING.substring(URL_STRING.lastIndexOf("/") + 1);
        Log.i(TAG, "-->>" + fileName);
    }

    public void clickView(View view) {
        switch (view.getId()) {
            case R.id.button_main_load:
//                Bitmap bm = null;
//                //从缓存中找图片
//                bm = getBitmapFromCache(URL_STRING);
////              如果缓存中有就设置，如果没有就去网上加载。
//                if (bm != null) {
//                    imageView_main_show.setImageBitmap(bm);
//                } else {
                //从网络下载图片
                new Thread(new Runnable() {
                    @Override
                    public void run() {
//                           从网络上拿到输入流，然后写到输出流，然后commit
                        try {
                            InputStream is = getInputStreamFromUrl(imageUrl);
                            OutputStream os = editor.newOutputStream(0);
                            BufferedInputStream in = new BufferedInputStream(is, 8 * 1024);
                            ;
                            BufferedOutputStream out = new BufferedOutputStream(os, 8 * 1024);
                            int b;
                            while ((b = in.read()) != -1) {
                                out.write(b);
                            }
                            editor.commit();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
//                }
                break;
        }
    }

    private InputStream getInputStreamFromUrl(String imageUrl) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(imageUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);

            return in;

        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    private Bitmap getBitmapFromCache(String key) {
        Bitmap bm = null;
        //从强引用中找图片
        bm = lruCache.get(key);
        if (bm != null) {
            return bm;
        } else {
            //从磁盘缓存中找图片
            String filePath = SDCardHelper.getSDCardPrivateCacheDir(mContxt) + File.separator +
                    fileName;
            byte[] data = SDCardHelper.loadFileFromSDCard(filePath);
            if (data != null) {
                bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                //将bitmap放到强引用缓存中
                lruCache.put(key, bm);
                return bm;
            }
        }
        return null;
    }

    private Bitmap createThumbnail(byte[] data, int newWidth, int newHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        //是否只采集图像的边界信息
        options.inJustDecodeBounds = true;
        //第一次采样:只采集边界信息，不采集像素信息
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        int width = options.outWidth;
        int height = options.outHeight;

        int widthRatio = 0;
        int heightRatio = 0;
        int sampleSize = 0;

        //通过第一次采样，获取到原图的边界信息，从而计算出缩放比例
        if (newWidth != 0 && newHeight == 0) {
            widthRatio = (int) (width / (float) newWidth);
            sampleSize = widthRatio;
        } else if (newWidth == 0 && newHeight != 0) {
            heightRatio = (int) (height / (float) newHeight);
            sampleSize = heightRatio;
        } else {
            widthRatio = (int) (width / (float) newWidth);
            heightRatio = (int) (height / (float) newHeight);
            sampleSize = widthRatio > heightRatio ? widthRatio : heightRatio;
        }

        //第二次采样
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    public String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    public int getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    private boolean downloadUrlToStream(String urlString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(), 8 * 1024);
            out = new BufferedOutputStream(outputStream, 8 * 1024);
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }


}
