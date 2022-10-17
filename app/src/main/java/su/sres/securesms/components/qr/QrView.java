package su.sres.securesms.components.qr;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import su.sres.securesms.R;
import su.sres.securesms.components.SquareImageView;
import su.sres.securesms.qr.QrCode;
import su.sres.securesms.util.Stopwatch;
import su.sres.securesms.util.concurrent.SerialMonoLifoExecutor;
import su.sres.securesms.util.concurrent.SignalExecutors;
import su.sres.securesms.util.concurrent.SimpleTask;

import java.util.concurrent.Executor;

/**
 * Generates a bitmap asynchronously for the supplied {@link BitMatrix} data and displays it.
 */
public class QrView extends SquareImageView {

    private static final @ColorInt int DEFAULT_FOREGROUND_COLOR = Color.BLACK;
    private static final @ColorInt int DEFAULT_BACKGROUND_COLOR = Color.TRANSPARENT;

    private @Nullable Bitmap qrBitmap;
    private @ColorInt int    foregroundColor;
    private @ColorInt int    backgroundColor;

    public QrView(Context context) {
        super(context);
        init(null);
    }

    public QrView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public QrView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.QrView, 0, 0);
            foregroundColor = typedArray.getColor(R.styleable.QrView_qr_foreground_color, DEFAULT_FOREGROUND_COLOR);
            backgroundColor = typedArray.getColor(R.styleable.QrView_qr_background_color, DEFAULT_BACKGROUND_COLOR);
            typedArray.recycle();
        } else {
            foregroundColor = DEFAULT_FOREGROUND_COLOR;
            backgroundColor = DEFAULT_BACKGROUND_COLOR;
        }

        if (isInEditMode()) {
            setQrText("https://signal.org");
        }
    }

    public void setQrText(@Nullable String text) {
        setQrBitmap(QrCode.create(text, foregroundColor, backgroundColor));
    }

    private void setQrBitmap(@Nullable Bitmap qrBitmap) {
        if (this.qrBitmap == qrBitmap) {
            return;
        }

        if (this.qrBitmap != null) {
            this.qrBitmap.recycle();
        }

        this.qrBitmap = qrBitmap;

        setImageBitmap(this.qrBitmap);
    }

    public @Nullable Bitmap getQrBitmap() {
        return qrBitmap;
    }
}
