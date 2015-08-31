package com.qing.utils;

import com.qing.callback.DownloadCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by zwq on 2015/08/31 16:56.<br/><br/>
 */
public class DownloadUtils {

    private static final String TAG = DownloadUtils.class.getName();

    public static boolean download(String url, String path, DownloadCallback callback){
        return download(url, path, null, callback);
    }

    public static boolean download(String url, String path, String fileName, DownloadCallback callback){
        if(url == null || path==null){
            if (callback!=null) callback.postResult(DownloadCallback.FAIL, "下载地址或保存目录不能为空");
            return false;
        }
        boolean isFile = false;
        File file = new File(path);
        if (file.isFile()){
            file = file.getParentFile();
            isFile = true;
        }else{
            if (StringUtils.isNullOrEmpty(fileName)) {
                if (callback!=null) callback.postResult(DownloadCallback.FAIL, "文件名不能为空");
                return false;
            }
        }
        if(!file.exists()){
            if(!file.mkdirs()){
                if (callback!=null) callback.postResult(DownloadCallback.FAIL, "目录创建失败");
                return false;
            }
        }
        if (isFile) {
            file = new File(path);
        }else{
            file = new File(path, fileName);
        }

        if(file.exists()) {
            if (callback!=null) callback.postResult(DownloadCallback.SUCCESS);
            return true;
        }else{
            try {
                if(!file.createNewFile()){
                    if (callback!=null) callback.postResult(DownloadCallback.FAIL, "文件创建失败");
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        long fileSize = file.length();
        int contentSize = 0;

        InputStream inputStream = null;
        ByteArrayOutputStream byteStream = null;
        FileOutputStream fileOutput = null;
        try {
            URL _url = new URL(url);
            HttpURLConnection conn = (HttpURLConnection)_url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(40*1000);
            conn.setReadTimeout(90*1000);
            conn.setDoInput(true);
            conn.connect();

            if (conn.getResponseCode() == 200) {
                if (callback!=null) callback.postResult(DownloadCallback.START);
                contentSize = conn.getContentLength();

                if(fileSize != contentSize) {
                    inputStream = conn.getInputStream();
                    byteStream = new ByteArrayOutputStream();

                    int size = 0;
                    int len = 0;
                    byte buffer[] = new byte[4096];
                    fileOutput = new FileOutputStream(file.getAbsolutePath());

                    while((len = inputStream.read(buffer, 0, buffer.length)) != -1) {
                        fileOutput.write(buffer, 0, len);
                        if (callback!=null) {
                            size += len;
                            callback.postResult(DownloadCallback.DOWNLOADING, size/contentSize);
                        }
                    }
                }
                if (callback!=null) callback.postResult(DownloadCallback.SUCCESS);
                return true;
            }
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            try {
                if (fileOutput!=null) {
                    fileOutput.close();
                    fileOutput = null;
                }
                if (byteStream!=null){
                    byteStream.close();
                    byteStream = null;
                }
                if (inputStream!=null){
                    inputStream.close();
                    inputStream = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (callback!=null) callback.postResult(DownloadCallback.FAIL, "下载失败");
        return false;
    }

}
