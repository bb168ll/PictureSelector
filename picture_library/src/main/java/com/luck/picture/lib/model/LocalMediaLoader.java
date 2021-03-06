package com.luck.picture.lib.model;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;

import com.luck.picture.lib.R;
import com.yalantis.ucrop.entity.LocalMedia;
import com.yalantis.ucrop.entity.LocalMediaFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.Manifest.permission.RECORD_AUDIO;


/**
 * author：luck
 * project：LocalMediaLoader
 * package：com.luck.picture.ui
 * email：893855882@qq.com
 * data：16/12/31
 */

public class LocalMediaLoader {
    public boolean isGif;
    public int index = 0;
    public long videoS = 0;
    private final static String[] IMAGE_PROJECTION = {
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media._ID
    };

    private final static String[] VIDEO_PROJECTION = {
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DURATION,
    };

    private final static String[] AUDIO_PROJECTION = {
            MediaStore.Audio.Media.DATA,                //音频文件的实际路径
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media._ID,                 //内部ID
            MediaStore.Audio.Media.DURATION,

            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ARTIST,//艺术家
            MediaStore.Audio.Media.ALBUM,//唱片集
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_NOTIFICATION
    };

    private int type = FunctionConfig.TYPE_IMAGE;
    private FragmentActivity activity;


    public LocalMediaLoader(FragmentActivity activity, int type, boolean isGif, long videoS) {
        this.activity = activity;
        this.type = type;
        this.isGif = isGif;
        this.videoS = videoS;
    }

    public void loadAllImage(final LocalMediaLoadListener imageLoadListener) {
        activity.getSupportLoaderManager().initLoader(type, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                CursorLoader cursorLoader = null;
                String select[] = null;
                String condition = "";
                if (isGif) {
                    select = new String[]{"image/jpeg", "image/png", "image/gif"};
                    condition = MediaStore.Images.Media.MIME_TYPE + "=? or "
                            + MediaStore.Images.Media.MIME_TYPE + "=?" + " or "
                            + MediaStore.Images.Media.MIME_TYPE + "=?";
                } else {
                    select = new String[]{"image/jpeg", "image/png", "image/webp"};
                    condition = MediaStore.Images.Media.MIME_TYPE + "=? or "
                            + MediaStore.Images.Media.MIME_TYPE + "=?" + " or "
                            + MediaStore.Images.Media.MIME_TYPE + "=?";
                }
                if (id == FunctionConfig.TYPE_IMAGE) {
                    cursorLoader = new CursorLoader(
                            activity, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            IMAGE_PROJECTION, condition,
                            select, IMAGE_PROJECTION[2] + " DESC");
                } else if (id == FunctionConfig.TYPE_VIDEO) {
                    String selection;
                    String selectionArgs[];
                    if (videoS <= 0) {
                        selection = null;
                        selectionArgs = null;
                    } else {
                        selection = "duration <= ?";
                        selectionArgs = new String[]{String.valueOf(videoS)};
                    }
                    cursorLoader = new CursorLoader(
                            activity, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            VIDEO_PROJECTION, selection, selectionArgs, VIDEO_PROJECTION[2] + " DESC");
                } else if (id == FunctionConfig.TYPE_AUDIO) {
                    String selection;
                    String selectionArgs[];
                    if (videoS <= 0) {
                        selection = null;
                        selectionArgs = null;
                    } else {
                        selection = "duration <= ?";
                        selectionArgs = new String[]{String.valueOf(videoS)};
                    }

                    cursorLoader = new CursorLoader(
                            activity, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            AUDIO_PROJECTION, selection, selectionArgs, AUDIO_PROJECTION[2] + " DESC");
                }

                return cursorLoader;
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                try {
                    List<LocalMediaFolder> imageFolders = new ArrayList<LocalMediaFolder>();
                    LocalMediaFolder allImageFolder = new LocalMediaFolder();
                    List<LocalMedia> latelyImages = new ArrayList<LocalMedia>();
                    index = 0;
                    if (data != null) {
                        int count = data.getCount();
                        if (count > 0) {
                            data.moveToFirst();
                            do {

                                String path = data.getString(data.getColumnIndexOrThrow(IMAGE_PROJECTION[0]));
                                // 如原图路径不存在或者路径存在但文件不存在,就结束当前循环
                                if (TextUtils.isEmpty(path) || !new File(path).exists()) {
                                    continue;
                                }
                                long dateTime = data.getLong(data.getColumnIndexOrThrow(IMAGE_PROJECTION[2]));
                                int duration = 0;

                                if (type == FunctionConfig.TYPE_VIDEO) {
                                    duration = data.getInt(data.getColumnIndexOrThrow(VIDEO_PROJECTION[4]));
                                } else if (type == FunctionConfig.TYPE_AUDIO) {
                                    duration = data.getInt(data.getColumnIndexOrThrow(AUDIO_PROJECTION[4]));
                                }

                                LocalMedia image = new LocalMedia(path, dateTime, duration, type);
                                LocalMediaFolder folder = getImageFolder(path, imageFolders);
                                folder.getImages().add(image);
                                folder.setType(type);
                                index++;
                                folder.setImageNum(folder.getImageNum() + 1);
                                // 最近相册中  只添加最新的100条
                                if (index <= 100) {
                                    latelyImages.add(image);
                                    allImageFolder.setType(type);
                                    allImageFolder.setImageNum(allImageFolder.getImageNum() + 1);
                                }

                            } while (data.moveToNext());

                            if (latelyImages.size() > 0) {
                                sortFolder(imageFolders);
                                imageFolders.add(0, allImageFolder);
                                String title = "";
                                switch (type) {
                                    case FunctionConfig.TYPE_VIDEO:
                                        title = activity.getString(R.string.picture_lately_video);
                                        break;
                                    case FunctionConfig.TYPE_IMAGE:
                                        title = activity.getString(R.string.picture_lately_image);
                                        break;
                                    case FunctionConfig.TYPE_AUDIO:
                                        title = activity.getString(R.string.picture_lately_audio);
                                        break;
                                }
                                allImageFolder.setFirstImagePath(latelyImages.get(0).getPath());
                                allImageFolder.setName(title);
                                allImageFolder.setType(type);
                                allImageFolder.setImages(latelyImages);
                            }
                            imageLoadListener.loadComplete(imageFolders);
                        } else {
                            // 如果没有相册
                            imageLoadListener.loadComplete(imageFolders);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
            }
        });
    }

    private void sortFolder(List<LocalMediaFolder> imageFolders) {
        // 文件夹按图片数量排序
        Collections.sort(imageFolders, new Comparator<LocalMediaFolder>() {
            @Override
            public int compare(LocalMediaFolder lhs, LocalMediaFolder rhs) {
                if (lhs.getImages() == null || rhs.getImages() == null) {
                    return 0;
                }
                int lsize = lhs.getImageNum();
                int rsize = rhs.getImageNum();
                return lsize == rsize ? 0 : (lsize < rsize ? 1 : -1);
            }
        });
    }

    private LocalMediaFolder getImageFolder(String path, List<LocalMediaFolder> imageFolders) {
        File imageFile = new File(path);
        File folderFile = imageFile.getParentFile();

        for (LocalMediaFolder folder : imageFolders) {
            if (folder.getName().equals(folderFile.getName())) {
                return folder;
            }
        }
        LocalMediaFolder newFolder = new LocalMediaFolder();
        newFolder.setName(folderFile.getName());
        newFolder.setPath(folderFile.getAbsolutePath());
        newFolder.setFirstImagePath(path);
        imageFolders.add(newFolder);
        return newFolder;
    }

    public interface LocalMediaLoadListener {
        void loadComplete(List<LocalMediaFolder> folders);
    }
}
