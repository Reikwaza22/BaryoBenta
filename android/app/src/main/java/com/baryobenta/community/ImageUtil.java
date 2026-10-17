package com.baryobenta.community;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/** Shrinks a picked photo to a small JPEG so uploads are fast and fit PHP's upload limit. */
public final class ImageUtil {

    private static final int MAX_SIDE = 1280;

    private ImageUtil() {
    }

    /** Runs on a background thread. Returns null if the image can't be read. */
    public static File prepareJpeg(Context context, Uri uri, String name) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while ((bounds.outWidth / sample) > MAX_SIDE * 2 || (bounds.outHeight / sample) > MAX_SIDE * 2) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;

            // Fix sideways photos using EXIF orientation
            int degrees = 0;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                ExifInterface exif = new ExifInterface(in);
                int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            } catch (Exception ignored) {
                // no EXIF data -> leave as is
            }

            float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bmp.getWidth(), bmp.getHeight()));
            if (degrees != 0 || scale < 1f) {
                Matrix m = new Matrix();
                if (scale < 1f) m.postScale(scale, scale);
                if (degrees != 0) m.postRotate(degrees);
                Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                if (out != bmp) bmp.recycle();
                bmp = out;
            }

            File file = new File(context.getCacheDir(), name + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            }
            bmp.recycle();
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    public static RequestBody text(String value) {
        return RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), value == null ? "" : value);
    }

    public static MultipartBody.Part photoPart(String field, File file) {
        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), file);
        return MultipartBody.Part.createFormData(field, file.getName(), body);
    }
}
