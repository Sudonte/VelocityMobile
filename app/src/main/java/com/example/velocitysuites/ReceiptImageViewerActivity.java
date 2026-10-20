package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * Full-screen viewer for the GCash receipt screenshot the guest attached: a
 * local content/file Uri before upload, the server URL after. Pinch-to-zoom,
 * a loading spinner, a plain message when the image is missing or fails to
 * load, and a close button. Never throws on a bad/blank source.
 */
public class ReceiptImageViewerActivity extends SafeActivity {

    private static final String EXTRA_SOURCE = "receipt_image_source";
    private static final int MAX_DECODE_PX = 2048;

    /** {@code source} is a content/file Uri string or an http(s) URL; null/blank shows the "not available" message. */
    public static void start(@NonNull Context context, @Nullable String source) {
        Intent intent = new Intent(context, ReceiptImageViewerActivity.class);
        intent.putExtra(EXTRA_SOURCE, source);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_image_viewer);

        ZoomableImageView image = findViewById(R.id.ivReceiptFull);
        ProgressBar progress = findViewById(R.id.progressReceiptFull);
        TextView message = findViewById(R.id.tvReceiptFullMessage);
        findViewById(R.id.btnCloseReceiptViewer).setOnClickListener(v -> finish());

        String source = getIntent() != null ? getIntent().getStringExtra(EXTRA_SOURCE) : null;
        if (source == null || source.trim().isEmpty()) {
            progress.setVisibility(View.GONE);
            message.setText(R.string.receipt_image_missing);
            message.setVisibility(View.VISIBLE);
            return;
        }

        progress.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(source.trim())
                .override(MAX_DECODE_PX, MAX_DECODE_PX)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                        if (isFinishing() || isDestroyed()) return true;
                        progress.setVisibility(View.GONE);
                        message.setText(R.string.receipt_image_load_failed);
                        message.setVisibility(View.VISIBLE);
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        if (isFinishing() || isDestroyed()) return true;
                        progress.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(image);
    }
}
