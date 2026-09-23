package com.example.velocitysuites;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;

/**
 * Full-screen room photo gallery - opened from RoomDetailsDialog's hero
 * pager (landing.xml and roombrowsing.xml both funnel into that one
 * dialog, so this is the single implementation point for both screens).
 * Manual Next/Previous (wrapping at both ends), a 5-second auto-advance
 * loop, and a pause/play toggle that stops/resumes the auto-advance while
 * leaving manual navigation available at all times.
 */
public class RoomGalleryActivity extends AppCompatActivity {

    private static final long AUTO_ADVANCE_DELAY_MS = 5000L;

    private ViewPager2 pager;
    private TextView tvPosition;
    private TextView tvRoomLabel;
    private ArrayList<String> imageLabels;
    private ImageButton btnPlayPause;
    private final Handler autoAdvanceHandler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = true;
    private int imageCount;

    private final Runnable autoAdvanceRunnable = new Runnable() {
        @Override
        public void run() {
            if (imageCount > 1) {
                int next = (pager.getCurrentItem() + 1) % imageCount;
                pager.setCurrentItem(next, true);
            }
            autoAdvanceHandler.postDelayed(this, AUTO_ADVANCE_DELAY_MS);
        }
    };

    public static Intent createIntent(Context context, ArrayList<String> imageUrls, ArrayList<String> imageLabels, int startIndex, String title) {
        Intent intent = new Intent(context, RoomGalleryActivity.class);
        intent.putStringArrayListExtra(context.getString(R.string.extra_room_gallery_images), imageUrls);
        intent.putStringArrayListExtra(context.getString(R.string.extra_room_gallery_labels), imageLabels);
        intent.putExtra(context.getString(R.string.extra_room_gallery_start_index), startIndex);
        intent.putExtra(context.getString(R.string.extra_room_gallery_title), title);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_gallery);

        ArrayList<String> imageUrls = getIntent().getStringArrayListExtra(getString(R.string.extra_room_gallery_images));
        imageLabels = getIntent().getStringArrayListExtra(getString(R.string.extra_room_gallery_labels));
        int startIndex = getIntent().getIntExtra(getString(R.string.extra_room_gallery_start_index), 0);
        if (imageUrls == null) imageUrls = new ArrayList<>();
        imageCount = imageUrls.size();

        pager = findViewById(R.id.pagerFullScreenGallery);
        tvPosition = findViewById(R.id.tvGalleryPosition);
        tvRoomLabel = findViewById(R.id.tvGalleryRoomLabel);
        btnPlayPause = findViewById(R.id.btnGalleryPlayPause);
        ImageButton btnPrevious = findViewById(R.id.btnGalleryPrevious);
        ImageButton btnNext = findViewById(R.id.btnGalleryNext);
        ImageButton btnClose = findViewById(R.id.btnCloseGallery);

        pager.setAdapter(new RoomGalleryPagerAdapter(imageUrls, imageLabels, R.drawable.ic_bed, null));
        pager.setCurrentItem(Math.max(0, Math.min(startIndex, Math.max(0, imageCount - 1))), false);
        updatePositionLabel();

        boolean hasMultiple = imageCount > 1;
        btnPrevious.setEnabled(hasMultiple);
        btnNext.setEnabled(hasMultiple);
        btnPlayPause.setEnabled(hasMultiple);
        if (!hasMultiple) {
            btnPrevious.setAlpha(0.35f);
            btnNext.setAlpha(0.35f);
            btnPlayPause.setAlpha(0.35f);
        }

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePositionLabel();
            }
        });

        btnPrevious.setOnClickListener(v -> {
            if (imageCount > 1) {
                int prev = (pager.getCurrentItem() - 1 + imageCount) % imageCount;
                pager.setCurrentItem(prev, true);
                restartAutoAdvanceIfPlaying();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (imageCount > 1) {
                int next = (pager.getCurrentItem() + 1) % imageCount;
                pager.setCurrentItem(next, true);
                restartAutoAdvanceIfPlaying();
            }
        });

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnClose.setOnClickListener(v -> finish());
    }

    private void updatePositionLabel() {
        if (imageCount > 0) {
            tvPosition.setText(getString(R.string.gallery_position_format, pager.getCurrentItem() + 1, imageCount));
        } else {
            tvPosition.setText("");
        }

        String label = (imageLabels != null && pager.getCurrentItem() < imageLabels.size())
                ? imageLabels.get(pager.getCurrentItem()) : null;
        if (tvRoomLabel != null) {
            if (label != null && !label.isEmpty()) {
                tvRoomLabel.setText(label);
                tvRoomLabel.setVisibility(android.view.View.VISIBLE);
            } else {
                tvRoomLabel.setVisibility(android.view.View.GONE);
            }
        }
    }

    private void togglePlayPause() {
        isPlaying = !isPlaying;
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            btnPlayPause.setContentDescription(getString(R.string.pause));
            startAutoAdvance();
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play);
            btnPlayPause.setContentDescription(getString(R.string.play));
            stopAutoAdvance();
        }
    }

    private void restartAutoAdvanceIfPlaying() {
        stopAutoAdvance();
        if (isPlaying) {
            startAutoAdvance();
        }
    }

    private void startAutoAdvance() {
        stopAutoAdvance();
        if (imageCount > 1) {
            autoAdvanceHandler.postDelayed(autoAdvanceRunnable, AUTO_ADVANCE_DELAY_MS);
        }
    }

    private void stopAutoAdvance() {
        autoAdvanceHandler.removeCallbacks(autoAdvanceRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isPlaying) {
            startAutoAdvance();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoAdvance();
    }
}
