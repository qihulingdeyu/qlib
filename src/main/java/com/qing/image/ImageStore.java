package com.qing.image;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.Images.Thumbnails;

import com.qing.log.MLog;
import com.qing.utils.FileUtils;
import com.qing.utils.StringUtils;
import com.qing.utils.ThreadUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by zwq on 2015/10/10 10:54.<br/><br/>
 */
public class ImageStore {

    private static final String TAG = ImageStore.class.getName();
    private static final String thumbSuffix = ".thumb";

    private static Context mContext;
    private static boolean hasLoad;
    private static List<ImageInfo> imageInfos = new ArrayList<>();
    private static Map<String, List<ImageInfo>> folderInfos = new HashMap<>();

    private static Handler mHandler;
    private static ThreadUtils threadUtils;
    private static ImageStoreChangeListener mListener;

    private static String cacheThumbPath;
    private static boolean deleteInvalidThumb;
    private static List<String> tempThumbPathList;

    public interface ImageStoreChangeListener{
        void onChange();
    }

    public static void setImageStoreChangeListener(ImageStoreChangeListener listener){
        mListener = listener;
    }

    /** 是否已经加载过 */
    public static boolean isHasLoad() {
        return hasLoad;
    }

    /** 自定义缩略图缓存目录 */
    public static void setCacheThumbPath(String path){
        if (StringUtils.isNullOrEmpty(path))
            return;
        File file = new File(path);
        if (!file.exists()){
            if (file.mkdirs()){
                cacheThumbPath = path;
                deleteInvalidThumb = true;
            }
        }else{
            cacheThumbPath = path;
            deleteInvalidThumb = true;
        }
        file = null;
    }

    public static void loadImage(Context context){
        mContext = context;

        if (mHandler == null){
            mHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    MLog.i(TAG, "--loadImage-finish-");
                    hasLoad = true;

                    if (threadUtils != null){
                        threadUtils.clearAll();
                        threadUtils = null;
                    }
                    if (mListener != null) {
                        mListener.onChange();
                    }
                    return false;
                }
            });
        }

        if (threadUtils != null){
            MLog.i(TAG, "--loadImage-stop running thread-");
            threadUtils.stop();
            threadUtils.clearAll();
            threadUtils = null;
        }
        threadUtils = new ThreadUtils() {
            @Override
            public void execute() {
                MLog.i(TAG, "--loadImage-start-");

                //获取图片数据
                if (!isRunning()){ return; }
                getImageInfos();

                //获取缩略图数据
                if (!isRunning()){ return; }
                getImageThumbInfos();

                //按文件夹分类
                if (!isRunning()){ return; }
                sortByFolder();

//                printList();
            }

            @Override
            public void finish() {
                super.finish();
                if (mHandler != null)
                    mHandler.sendEmptyMessage(1);

                if (deleteInvalidThumb && cacheThumbPath != null){
                    deleteInvalidThumb = false;
                    deleteInvalidThumb();
                }
            }
        };
        threadUtils.start();
    }

    private static synchronized void getImageInfos(){
        Cursor cursor = null;
        ImageInfo imageInfo = null;
        cursor = mContext.getContentResolver().query(Media.EXTERNAL_CONTENT_URI, null, null, null, " "+ Media._ID+" desc ");

        if (cursor != null){
            synchronized (imageInfos) {
                imageInfos.clear();
                while (cursor.moveToNext()){
                    imageInfo = new ImageInfo();

                    imageInfo.set_id(cursor.getInt(cursor.getColumnIndex(Media._ID)));
                    imageInfo.setImage_id(imageInfo.get_id());
                    imageInfo.setTitle(cursor.getString(cursor.getColumnIndex(Media.TITLE)));
                    imageInfo.setName(cursor.getString(cursor.getColumnIndex(Media.DISPLAY_NAME)));
                    imageInfo.setPath(cursor.getString(cursor.getColumnIndex(Media.DATA)));

                    imageInfo.setDate_added(cursor.getLong(cursor.getColumnIndex(Media.DATE_ADDED)));
                    imageInfo.setDate_modified(cursor.getLong(cursor.getColumnIndex(Media.DATE_MODIFIED)));
                    imageInfo.setWidth(cursor.getInt(cursor.getColumnIndex(Media.WIDTH)));
                    imageInfo.setHeight(cursor.getInt(cursor.getColumnIndex(Media.HEIGHT)));

                    imageInfo.setSize(cursor.getInt(cursor.getColumnIndex(Media.SIZE)));
                    imageInfo.setOrientation(cursor.getInt(cursor.getColumnIndex(Media.ORIENTATION)));
                    imageInfo.setFolder_id(cursor.getInt(cursor.getColumnIndex(Media.BUCKET_ID)));
                    imageInfo.setFolder_name(cursor.getString(cursor.getColumnIndex(Media.BUCKET_DISPLAY_NAME)));

                    imageInfos.add(imageInfo);
                }
            }
            cursor.close();
        }
        cursor = null;
    }

    /**
     * 获取缩略图信息
     */
    private static synchronized void getImageThumbInfos() {
        //判断thumb_path是否为null
        //
        if (imageInfos!=null && !imageInfos.isEmpty()){
            synchronized (imageInfos){
                if (deleteInvalidThumb){
                    if (tempThumbPathList == null)
                        tempThumbPathList = new ArrayList<>();
                    tempThumbPathList.clear();
                }
                for (ImageInfo imageInfo : imageInfos) {
                    if (imageInfo != null && StringUtils.isNullOrEmpty(imageInfo.getThumb_path())){
                        if (cacheThumbPath == null){
                            MLog.i(TAG, "--Thumb_path-use system thumb-");
                            getImageThumbInfo(imageInfo);

                        }else{
                            //从自定义缩略图目录获取
                            String thumbName = StringUtils.getMD5(imageInfo.getPath()) + thumbSuffix;
                            File file = new File(cacheThumbPath, thumbName);

                            imageInfo.setThumb_id(-1);
                            imageInfo.setThumb_kind(Thumbnails.MINI_KIND);
                            if (!file.exists()){
                                //创建缩略图
                                Bitmap thumb = null;
//                                thumb = getImageThumbnail(imageInfo);
                                thumb = getImageThumbnail(imageInfo.getPath(), Thumbnails.MINI_KIND);
                                if (thumb != null && !thumb.isRecycled()){
                                    imageInfo.setThumb_width(thumb.getWidth());
                                    imageInfo.setThumb_height(thumb.getHeight());
                                }

                                //保存缩略图
                                if (!FileUtils.write2SD(thumb, file.getAbsolutePath(), true)){
                                    continue;
                                }
                                MLog.i(TAG, "--create--Thumb_path:"+file.getAbsolutePath());
                            }else{
                                MLog.i(TAG, "Thumb_path:"+file.getAbsolutePath());

                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inJustDecodeBounds = true;
                                BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                                options.inJustDecodeBounds = false;

                                imageInfo.setThumb_width(options.outWidth);
                                imageInfo.setThumb_height(options.outHeight);
                            }
                            imageInfo.setThumb_path(file.getAbsolutePath());
                            if (deleteInvalidThumb){
                                tempThumbPathList.add(file.getAbsolutePath());
                            }
                            file = null;
                        }
                    }
                }
            }
        }
    }

    private static void getImageThumbInfo(ImageInfo imageInfo){
        if (imageInfo != null){
            Cursor cursor = mContext.getContentResolver().query(Thumbnails.EXTERNAL_CONTENT_URI,
                    null, Thumbnails.IMAGE_ID+"=?", new String[]{""+imageInfo.get_id()}, null);
            if (cursor != null){
                while (cursor.moveToNext()){
                    imageInfo.setThumb_id(cursor.getInt(cursor.getColumnIndex(Thumbnails._ID)));
                    imageInfo.setThumb_path(cursor.getString(cursor.getColumnIndex(Thumbnails.DATA)));
                    imageInfo.setThumb_width(cursor.getInt(cursor.getColumnIndex(Thumbnails.WIDTH)));
                    imageInfo.setThumb_height(cursor.getInt(cursor.getColumnIndex(Thumbnails.HEIGHT)));
                    imageInfo.setThumb_kind(cursor.getInt(cursor.getColumnIndex(Thumbnails.KIND)));
                }
                cursor.close();
            }
            cursor = null;
        }
    }

    /**
     * 获取图片缩略图
     * @param imagePath 原始图片目录
     * @param kind 缩略图类型
     * @return
     */
    public static Bitmap getImageThumbnail(String imagePath, int kind) {
        if (kind == Thumbnails.MINI_KIND){//1
            //512 x 384
            return getImageThumbnail(imagePath, 512 / 2, 384 / 2);
        }else if (kind == Thumbnails.MICRO_KIND){//3
            //96 x 96
            return getImageThumbnail(imagePath, 96, 96);
        }
        return null;
    }

    /**
     * 根据指定的图像路径和大小来获取缩略图
     * 此方法有两点好处：
     *     1. 使用较小的内存空间，第一次获取的bitmap实际上为null，只是为了读取宽度和高度，
     *        第二次读取的bitmap是根据比例压缩过的图像，第三次读取的bitmap是所要的缩略图。
     *     2. 缩略图对于原图像来讲没有拉伸，这里使用了2.2版本的新工具ThumbnailUtils，使
     *        用这个工具生成的图像不会被拉伸。
     * @param imagePath 图像的路径
     * @param width 指定输出图像的宽度
     * @param height 指定输出图像的高度
     * @return 生成的缩略图
     */
    public static Bitmap getImageThumbnail(String imagePath, int width, int height) {
        Bitmap bitmap = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        // 获取这个图片的宽和高，注意此处的bitmap为null
        bitmap = BitmapFactory.decodeFile(imagePath, options);
        options.inJustDecodeBounds = false; // 设为 false
        // 计算缩放比
        int w = options.outWidth;
        int h = options.outHeight;

        int ratio = 1;
        //图片宽高都比原图宽高小时，使用原图的大小
        if ((w > 0 && w <= width) && (h > 0 && h <= height)) {
            width = w;
            height = h;
        }else{
            int ratioWidth = w / width;
            int ramainW = w % width;
            int ratioHeight = h / height;
            int ramainH = h % height;
            if (ratioWidth < ratioHeight) {
                ratio = ratioWidth + (ramainW > 0 ? 1 : 0);
            } else {
                ratio = ratioHeight + (ramainH > 0 ? 1 : 0);
            }
//            if (ratio <= 0) {
//                ratio = 1;//1 2 4 8
//            }
            if (ratio < 2) {
                ratio = 2;
            }
            if (ratio > 1 && ratio < 6){  //1 2 3 4 5 6
                int tempRatio = ratio / 2;//0 1 1 2 4 4
                if (ratio % 2 > 0){
                    tempRatio += 1;
                }
                ratio = (int) Math.pow(2, tempRatio);
            }
            width = w / ratio;
            height = h / ratio;
        }
        options.inSampleSize = ratio;
//        options.inPreferredConfig = Bitmap.Config.ARGB_4444;
        // 重新读入图片，读取缩放后的bitmap，注意这次要把options.inJustDecodeBounds 设为 false
        bitmap = BitmapFactory.decodeFile(imagePath, options);
        // 利用ThumbnailUtils来创建缩略图，这里要指定要缩放哪个Bitmap对象
//        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
//        bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        return bitmap;
    }


    private static synchronized void sortByFolder() {
        if (imageInfos!=null && !imageInfos.isEmpty()){
            String folderName = null;
            List<ImageInfo> folderItem = null;

            synchronized (imageInfos){
                folderInfos.clear();
                for (ImageInfo imageInfo : imageInfos) {
                    if (imageInfo!=null){
                        folderName = imageInfo.getFolder_name();

                        if (!folderInfos.containsKey(folderName)){
                            folderItem = new ArrayList<>();
                            folderInfos.put(folderName, folderItem);
                        }else{
                            folderItem = folderInfos.get(folderName);
                        }
                        if (folderItem != null){
                            folderItem.add(imageInfo);
                        }
                    }
                }
            }
            //按文件夹名称升序排序
            folderInfos = sortMapByKey(folderInfos);

            folderItem = null;
            folderName = null;
        }
    }

    private static Map<String, List<ImageInfo>> sortMapByKey(Map<String, List<ImageInfo>> src){
        if (src == null || src.isEmpty()){
            return src;
        }
        Map<String, List<ImageInfo>> sortMap = new TreeMap<String, List<ImageInfo>>(new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                return lhs.compareTo(rhs);
            }
        });
        sortMap.putAll(src);
        return sortMap;
    }

    /**
     * 删除无效的缩略图
     */
    private static void deleteInvalidThumb() {
        if (tempThumbPathList != null && !tempThumbPathList.isEmpty()){
            String[] files =  new File(cacheThumbPath).list();
            if (files != null && files.length > 0){
                List<String> filesList = Arrays.asList(files);

                if (filesList != null && !filesList.isEmpty() && filesList.removeAll(tempThumbPathList)){
                    for (String filepath : filesList) {
                        FileUtils.deleteSDFile(filepath);
                    }
                    filesList.clear();
                }
                filesList = null;
            }
            tempThumbPathList.clear();
        }
        tempThumbPathList = null;
    }

    public static List<ImageInfo> getImageInfosList(){
        return imageInfos;
    }

    public static Map<String, List<ImageInfo>> getImageInfosFolderList(){
        return folderInfos;
    }

    private static void printList(){
        for (Map.Entry<String, List<ImageInfo>> entry: folderInfos.entrySet()) {
            for (ImageInfo imageInfo : entry.getValue()) {
                MLog.i(TAG, imageInfo.toString());
            }
        }
    }

    public static void clearAll(){
        if (imageInfos != null){
            imageInfos.clear();
            imageInfos = null;
        }
        if (folderInfos != null){
            folderInfos.clear();
            folderInfos = null;
        }
        if (mListener != null){
            mListener = null;
        }
        if (mHandler != null){
            mHandler = null;
        }
    }

    /**
     * 获取缩略图
     * @param imageInfo 原图信息
     * @return
     */
    public static Bitmap getImageThumbnail(ImageInfo imageInfo){
        imageInfo.setThumb_id(-1);
        imageInfo.setThumb_kind(Thumbnails.MINI_KIND);
        Bitmap bitmap = getImageThumbnail(imageInfo.getImage_id());
        if (bitmap != null && !bitmap.isRecycled()){
            imageInfo.setThumb_width(bitmap.getWidth());
            imageInfo.setThumb_height(bitmap.getHeight());
        }
        return bitmap;
    }

    /**
     * 获取缩略图
     * @param id 原图在ContentProvider中的id
     * @return
     */
    public static Bitmap getImageThumbnail(int id){
        Bitmap bitmap = null;
        if (mContext != null)
            bitmap = Thumbnails.getThumbnail(mContext.getContentResolver(), id, Thumbnails.MINI_KIND, null);
        return bitmap;
    }


    private static void getImageInfo(int id){
        Cursor cursor = null;
        ImageInfo imageInfo = null;
        if (id>0){
            cursor = mContext.getContentResolver().query(Media.EXTERNAL_CONTENT_URI, null, Media._ID+"=?", new String[]{""+id}, null);
        }else{
            cursor = mContext.getContentResolver().query(Media.EXTERNAL_CONTENT_URI, null, null, null, " "+ Media._ID+" desc ");
        }
        if (cursor != null){
            while (cursor.moveToNext()){
                imageInfo = new ImageInfo();

                imageInfo.set_id(cursor.getInt(cursor.getColumnIndex(Media._ID)));
                imageInfo.setImage_id(imageInfo.get_id());
                imageInfo.setTitle(cursor.getString(cursor.getColumnIndex(Media.TITLE)));
                imageInfo.setName(cursor.getString(cursor.getColumnIndex(Media.DISPLAY_NAME)));
                imageInfo.setPath(cursor.getString(cursor.getColumnIndex(Media.DATA)));

                imageInfo.setDate_added(cursor.getLong(cursor.getColumnIndex(Media.DATE_ADDED)));
                imageInfo.setDate_modified(cursor.getLong(cursor.getColumnIndex(Media.DATE_MODIFIED)));
                imageInfo.setWidth(cursor.getInt(cursor.getColumnIndex(Media.WIDTH)));
                imageInfo.setHeight(cursor.getInt(cursor.getColumnIndex(Media.HEIGHT)));

                imageInfo.setSize(cursor.getInt(cursor.getColumnIndex(Media.SIZE)));
                imageInfo.setOrientation(cursor.getInt(cursor.getColumnIndex(Media.ORIENTATION)));
                imageInfo.setFolder_id(cursor.getInt(cursor.getColumnIndex(Media.BUCKET_ID)));
                imageInfo.setFolder_name(cursor.getString(cursor.getColumnIndex(Media.BUCKET_DISPLAY_NAME)));


                MLog.i(TAG, imageInfo.toString());
            }
            cursor.close();
        }
        cursor = null;
    }

}