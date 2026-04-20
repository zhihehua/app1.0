package com.example.NoteMind;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.MultiBrowseCarouselStrategy;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements MainActivity.OnDatabaseUpdatedListener {

    private ChipGroup chipGroupTags;
    private String currentSelectedTag = "";
    private String currentMainCategory = Constants.CAT_STUDY;
    private QuestionDao questionDao;
    private NoteMindAgent agent;
    private CameraUtils cameraUtils;
    private TextView tvSceneFocus;
    private RecyclerView recyclerViewCarousel;
    private CarouselSnapHelper snapHelper;
    private CarouselLayoutManager layoutManager;
    
    private static final int LOOP_FACTOR = 100;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tab_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;

        questionDao = activity.getQuestionDao();
        agent = activity.getAgent();
        cameraUtils = activity.getCameraUtils();
        activity.registerUpdateListener(this);

        chipGroupTags = view.findViewById(R.id.chip_group_tags);
        tvSceneFocus = view.findViewById(R.id.tv_scene_focus);
        recyclerViewCarousel = view.findViewById(R.id.category_carousel_rv);

        mainHandler.postDelayed(() -> {
            if (isAdded()) {
                initCarousel();
                refreshTagChips();
            }
        }, 50);

        // 1. AI 智能对话（原有功能）
        view.findViewById(R.id.btn_input_text_container).setOnClickListener(v -> showInputTextDialog());

        // 2. 普通手动录入（新功能）
        view.findViewById(R.id.btn_manual_input_container).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ManualInputActivity.class);
            intent.putExtra("PRE_TAG", currentSelectedTag);
            startActivity(intent);
        });

        view.findViewById(R.id.btn_take_photo_container).setOnClickListener(v -> {
            MainActivity act = (MainActivity) getActivity();
            if (act != null) {
                act.setCurrentSelectedTag(currentSelectedTag);
                cameraUtils.openCamera();
            }
        });

        view.findViewById(R.id.btn_gallery_container).setOnClickListener(v -> {
            MainActivity act = (MainActivity) getActivity();
            if (act != null) {
                act.setCurrentSelectedTag(currentSelectedTag);
                cameraUtils.openGallery();
            }
        });
    }

    private void initCarousel() {
        layoutManager = new CarouselLayoutManager(new MultiBrowseCarouselStrategy());
        recyclerViewCarousel.setLayoutManager(layoutManager);
        recyclerViewCarousel.setClipChildren(false);
        recyclerViewCarousel.setClipToPadding(false);
        
        snapHelper = new CarouselSnapHelper();
        snapHelper.attachToRecyclerView(recyclerViewCarousel);

        List<CarouselItem> items = new ArrayList<>();
        items.add(new CarouselItem(Constants.CAT_STUDY, R.drawable.ic_study, R.drawable.study));
        items.add(new CarouselItem(Constants.CAT_WORK, R.drawable.ic_work, R.drawable.work));
        items.add(new CarouselItem(Constants.CAT_LIFE, R.drawable.ic_life, R.drawable.life));
        items.add(new CarouselItem(Constants.CAT_OTHER, R.drawable.ic_other, R.drawable.other));

        CarouselAdapter adapter = new CarouselAdapter(items);
        recyclerViewCarousel.setAdapter(adapter);

        int startPos = (LOOP_FACTOR / 2) * items.size();
        recyclerViewCarousel.scrollToPosition(startPos);

        recyclerViewCarousel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private int lastSettledPos = -1;
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int centerX = recyclerView.getWidth() / 2;
                View closest = null;
                float min = Float.MAX_VALUE;
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View child = recyclerView.getChildAt(i);
                    float d = Math.abs(centerX - (child.getLeft() + child.getRight()) / 2);
                    if (d < min) { min = d; closest = child; }
                }
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View child = recyclerView.getChildAt(i);
                    float ratio = 1 - Math.min(1.0f, Math.abs(centerX - (child.getLeft() + child.getRight()) / 2) / (recyclerView.getWidth() / 2.0f));
                    if (child == closest) {
                        child.setScaleX(1.0f); child.setScaleY(1.0f); child.setAlpha(1.0f);
                        int pos = layoutManager.getPosition(child);
                        if (pos != lastSettledPos) {
                            lastSettledPos = pos;
                            CarouselItem currentItem = items.get(pos % items.size());
                            if (currentItem != null && currentItem.name != null) {
                                updateCategoryLogic(currentItem.name);
                            }
                        }
                    } else {
                        float scale = 0.75f + (ratio * 0.25f);
                        child.setScaleX(scale); child.setScaleY(scale); child.setAlpha(0.2f + (ratio * 0.8f));
                    }
                }
            }
        });
    }

    private void updateCategoryLogic(String category) {
        if (category != null && !category.equals(currentMainCategory)) {
            currentMainCategory = category;
            currentSelectedTag = "";
            tvSceneFocus.setText("当前聚焦场景: " + category);
            refreshTagChips();
        }
    }

    public void refreshTagChips() {
        if (chipGroupTags == null || getContext() == null) return;
        chipGroupTags.removeAllViews();
        Chip addBtn = new Chip(getContext());
        addBtn.setText("自定义");
        addBtn.setChipIcon(ContextCompat.getDrawable(getContext(), android.R.drawable.ic_input_add));
        addBtn.setOnClickListener(v -> showNewTagInput());
        chipGroupTags.addView(addBtn);
        List<String> subNodes = new ArrayList<>();
        if (Constants.CAT_STUDY.equals(currentMainCategory)) subNodes.addAll(Constants.STUDY_ROOTS);
        else subNodes.addAll(questionDao.getCategoriesByTag(currentMainCategory));
        if (!currentSelectedTag.isEmpty() && !subNodes.contains(currentSelectedTag)) subNodes.add(0, currentSelectedTag);
        for (String tag : subNodes) {
            Chip chip = new Chip(getContext());
            chip.setText(tag);
            chip.setCheckable(true);
            if (tag.equals(currentSelectedTag)) chip.setChecked(true);
            chip.setOnClickListener(v -> {
                if (chip.isChecked()) {
                    currentSelectedTag = tag;
                    for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
                        View child = chipGroupTags.getChildAt(i);
                        if (child instanceof Chip && child != chip) ((Chip) child).setChecked(false);
                    }
                    MainActivity act = (MainActivity) getActivity();
                    if (act != null) act.setCurrentSelectedTag(tag);
                } else if (currentSelectedTag.equals(tag)) currentSelectedTag = "";
            });
            chipGroupTags.addView(chip);
        }
    }

    @Override public void onDatabaseUpdated() { if (isAdded()) refreshTagChips(); }

    private static class CarouselItem {
        String name; int iconRes, imageRes;
        CarouselItem(String name, int icon, int img) { 
            this.name = name; 
            this.iconRes = icon; 
            this.imageRes = img; 
        }
    }

    private class CarouselAdapter extends RecyclerView.Adapter<CarouselAdapter.ViewHolder> {
        private final List<CarouselItem> items;
        CarouselAdapter(List<CarouselItem> items) { this.items = items; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_category_carousel, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            CarouselItem item = items.get(p % items.size());
            h.textView.setText(item.name); h.iconView.setImageResource(item.iconRes); h.imageView.setImageResource(item.imageRes);
            h.itemView.setOnClickListener(v -> {
                recyclerViewCarousel.smoothScrollToPosition(p);
            });
        }
        @Override public int getItemCount() { return items.size() * LOOP_FACTOR; }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView; ImageView iconView, imageView;
            ViewHolder(View v) { super(v); textView = v.findViewById(R.id.carousel_text); iconView = v.findViewById(R.id.carousel_icon); imageView = v.findViewById(R.id.carousel_image); }
        }
    }

    private void showNewTagInput() {
        if (getContext() == null) return;
        View v = LayoutInflater.from(getContext()).inflate(R.layout.layout_custom_dialog, null);
        EditText et = v.findViewById(R.id.dialog_input);
        Button btn = v.findViewById(R.id.dialog_btn_confirm);
        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext()).setView(v).create();
        dialog.show();
        btn.setOnClickListener(view -> {
            String val = et.getText().toString().trim();
            if(!val.isEmpty()) { 
                currentSelectedTag = val; 
                refreshTagChips(); 
                dialog.dismiss(); 
                MainActivity act = (MainActivity) getActivity();
                if (act != null) act.setCurrentSelectedTag(val); 
            }
        });
    }

    private void showInputTextDialog() {
        if (getContext() == null) return;
        View v = LayoutInflater.from(getContext()).inflate(R.layout.layout_custom_dialog, null);
        TextView tv = v.findViewById(R.id.dialog_title);
        EditText et = v.findViewById(R.id.dialog_input);
        Button btn = v.findViewById(R.id.dialog_btn_confirm);
        String label = currentSelectedTag.isEmpty() ? currentMainCategory : currentSelectedTag;
        tv.setText("AI 智能对话: [" + label + "]");
        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext()).setView(v).create();
        dialog.show();
        btn.setOnClickListener(view -> {
            String text = et.getText().toString().trim();
            if (!text.isEmpty()) { 
                dialog.dismiss(); 
                MainActivity act = (MainActivity) getActivity();
                if (act != null) act.requestAiTextWithCallback("【场景：" + label + "】" + text); 
            }
        });
    }
}
