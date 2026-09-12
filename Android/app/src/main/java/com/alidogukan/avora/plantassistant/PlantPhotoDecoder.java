package com.alidogukan.avora.plantassistant;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;

/** Decodes phone photos at a bounded size while preserving their camera orientation. */
public final class PlantPhotoDecoder {
    private PlantPhotoDecoder() { }

    public static Bitmap decode(ContentResolver resolver, Uri uri, int maxLongestSide)
            throws IOException {
        if (resolver == null || uri == null || maxLongestSide <= 0) {
            throw new IOException("PHOTO_DECODE_FAILED");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return decodeModern(resolver, uri, maxLongestSide);
        }
        return decodeLegacy(resolver, uri, maxLongestSide);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private static Bitmap decodeModern(ContentResolver resolver, Uri uri, int maxLongestSide)
            throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
        Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, ignored) -> {
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            int longest = Math.max(width, height);
            if (longest > maxLongestSide) {
                float scale = maxLongestSide / (float) longest;
                decoder.setTargetSize(
                        Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale))
                );
            }
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        if (bitmap == null) throw new IOException("PHOTO_DECODE_FAILED");
        return bitmap;
    }

    private static Bitmap decodeLegacy(ContentResolver resolver, Uri uri, int maxLongestSide)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) throw new IOException("PHOTO_DECODE_FAILED");
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        if (longest <= 0) throw new IOException("PHOTO_DECODE_FAILED");

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        while (longest / (options.inSampleSize * 2) >= maxLongestSide) {
            options.inSampleSize *= 2;
        }
        Bitmap decoded;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) throw new IOException("PHOTO_DECODE_FAILED");
            decoded = BitmapFactory.decodeStream(stream, null, options);
        }
        if (decoded == null) throw new IOException("PHOTO_DECODE_FAILED");

        Bitmap oriented = applyOrientation(decoded, readOrientation(resolver, uri));
        Bitmap bounded = scaleToLimit(oriented, maxLongestSide);
        if (bounded != oriented && !oriented.isRecycled()) oriented.recycle();
        return bounded;
    }

    private static int readOrientation(ContentResolver resolver, Uri uri) {
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) return ExifInterface.ORIENTATION_NORMAL;
            return new ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
        } catch (Exception ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap applyOrientation(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }
        Bitmap transformed = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (transformed != bitmap && !bitmap.isRecycled()) bitmap.recycle();
        return transformed;
    }

    private static Bitmap scaleToLimit(Bitmap bitmap, int maxLongestSide) {
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= maxLongestSide) return bitmap;
        float scale = maxLongestSide / (float) longest;
        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)),
                true
        );
    }
}
