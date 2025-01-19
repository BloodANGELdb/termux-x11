package com.termux.x11;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.termux.x11.input.InputStub;
import com.termux.x11.input.TouchInputHandler;

import java.nio.charset.StandardCharsets;
import java.util.regex.PatternSyntaxException;

@Keep
@SuppressLint({"WrongConstant"})
@SuppressWarnings("deprecation")
public class LorieView extends SurfaceView implements InputStub {
    public interface Callback {
        void onSurfaceChanged(Surface surface, int surfaceWidth, int surfaceHeight, int screenWidth, int screenHeight);
    }

    interface PixelFormat {
        int BGRA_8888 = 5; // HAL_PIXEL_FORMAT_BGRA_8888
    }

    private final ClipboardManager clipboard;
    private long lastClipboardTimestamp = System.currentTimeMillis();
    private static boolean clipboardSyncEnabled = false;
    private static boolean hardwareKbdScancodesWorkaround = false;
    private final InputMethodManager mIMM;
    private String mImeLang;
    private boolean mImeCJK;
    public boolean enableGboardCJK;
    private Callback mCallback;
    private final Point p = new Point();

    private final SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            holder.setFormat(PixelFormat.BGRA_8888);
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            width = getMeasuredWidth();
            height = getMeasuredHeight();

            Log.d("SurfaceChangedListener", "Surface changed: " + width + "x" + height);
            if (mCallback != null) {
                getDimensionsFromSettings();
                mCallback.onSurfaceChanged(holder.getSurface(), width, height, p.x, p.y);
            }
            surfaceChanged(holder.getSurface());
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            if (mCallback != null) {
                mCallback.onSurfaceChanged(holder.getSurface(), 0, 0, 0, 0);
            }
            surfaceChanged(holder.getSurface());
        }
    };

    public LorieView(Context context) {
        this(context, null);
    }

    public LorieView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LorieView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        mIMM = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        init();
    }

    private void init() {
        getHolder().addCallback(mSurfaceCallback);
        setFocusable(true);
        setFocusableInTouchMode(true);
        nativeInit();
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
        triggerCallback();
    }

    public void triggerCallback() {
        setBackground(new ColorDrawable(Color.TRANSPARENT));
        Rect surfaceFrame = getHolder().getSurfaceFrame();
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> mSurfaceCallback.surfaceChanged(getHolder(), PixelFormat.BGRA_8888, surfaceFrame.width(), surfaceFrame.height()));
        }
    }

    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private void getDimensionsFromSettings() {
        // Получение настроек отображения (добавлена проверка на `null` для Prefs).
        Prefs prefs = MainActivity.getPrefs();
        if (prefs == null) return;

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        switch (prefs.displayResolutionMode.get()) {
            case "scaled":
                int scale = prefs.displayScale.get();
                p.set(width * 100 / scale, height * 100 / scale);
                break;
            case "exact":
                String[] exactResolution = prefs.displayResolutionExact.get().split("x");
                p.set(Integer.parseInt(exactResolution[0]), Integer.parseInt(exactResolution[1]));
                break;
            case "custom":
                try {
                    String[] customResolution = prefs.displayResolutionCustom.get().split("x");
                    p.set(Integer.parseInt(customResolution[0]), Integer.parseInt(customResolution[1]));
                } catch (NumberFormatException | PatternSyntaxException ignored) {
                    p.set(1280, 1024);
                }
                break;
            default:
                p.set(width, height);
                break;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        getDimensionsFromSettings();
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        if (p.x > 0 && p.y > 0) {
            int adjustedWidth = width;
            int adjustedHeight = height;

            if (width > height * p.x / p.y) {
                adjustedWidth = height * p.x / p.y;
            } else {
                adjustedHeight = width * p.y / p.x;
            }

            getHolder().setFixedSize(p.x, p.y);
            setMeasuredDimension(adjustedWidth, adjustedHeight);
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new BaseInputConnection(this, false);
    }

    // Native методы
    private native void nativeInit();
    private native void surfaceChanged(Surface surface);
}
