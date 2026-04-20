package com.example.NoteMind;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.MultiBrowseCarouselStrategy;

import java.util.ArrayList;
import java.util.List;

public class MyFragment extends Fragment {

    private RecyclerView recyclerView;
    private CarouselLayoutManager layoutManager;
    // 优化：从 1000 降至 100，平衡无限滑动感与布局性能
    private static final int LOOP_FACTOR = 100;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tab_my, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.rv_my_cards);
        
        // 延时初始化，确保导航栏切换动画先完成
        recyclerView.postDelayed(this::initCarousel, 50);
    }

    private void initCarousel() {
        if (!isAdded()) return;
        
        layoutManager = new CarouselLayoutManager(new MultiBrowseCarouselStrategy(), RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setClipChildren(false);
        recyclerView.setClipToPadding(false);
        
        CarouselSnapHelper snapHelper = new CarouselSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);

        List<MyCard> cards = new ArrayList<>();
        cards.add(new MyCard("我的笔记列表", R.drawable.list, "#CC253545", () -> {
            startActivity(new Intent(getActivity(), NoteListActivity.class));
        }));
        cards.add(new MyCard("导出PDF", R.drawable.my_pdf, "#CCFF8C00", () -> {
            startActivity(new Intent(getActivity(), MyPdfActivity.class));
        }));
        cards.add(new MyCard("智能总结", R.drawable.atom_summary, "#CC4169E1", () -> {
            startActivity(new Intent(getActivity(), AtomSummaryActivity.class));
        }));
        cards.add(new MyCard("系统设置", R.drawable.setting, "#CC6A5ACD", () -> {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
        }));
        cards.add(new MyCard("联系我们", R.drawable.contact_us, "#CC2E8B57", () -> {
            startActivity(new Intent(getActivity(), ContactActivity.class));
        }));

        MyCardAdapter adapter = new MyCardAdapter(cards);
        recyclerView.setAdapter(adapter);

        int startPos = (LOOP_FACTOR / 2) * cards.size();
        recyclerView.scrollToPosition(startPos);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int centerY = recyclerView.getHeight() / 2;
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View child = recyclerView.getChildAt(i);
                    int childCenterY = (child.getTop() + child.getBottom()) / 2;
                    float distance = Math.abs(centerY - childCenterY);
                    float ratio = 1 - Math.min(1.0f, distance / (recyclerView.getHeight() / 1.5f));
                    float scale = 0.82f + (ratio * 0.18f);
                    child.setScaleX(scale);
                    child.setScaleY(scale);
                    child.setAlpha(0.6f + (ratio * 0.4f));
                    if (distance < 50) {
                        child.setTranslationZ(20f);
                    } else {
                        child.setTranslationZ(0f);
                    }
                }
            }
        });
    }

    private static class MyCard {
        String title;
        int iconRes;
        String colorHex;
        Runnable action;
        MyCard(String title, int iconRes, String colorHex, Runnable action) {
            this.title = title; this.iconRes = iconRes; this.colorHex = colorHex; this.action = action;
        }
    }

    private class MyCardAdapter extends RecyclerView.Adapter<MyCardAdapter.ViewHolder> {
        private final List<MyCard> cards;
        MyCardAdapter(List<MyCard> cards) { this.cards = cards; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MyCard card = cards.get(position % cards.size());
            holder.tvTitle.setText(card.title);
            holder.ivIcon.setImageResource(card.iconRes);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor(card.colorHex));
            gd.setCornerRadius(60f); 
            holder.container.setBackground(gd);

            holder.itemView.setOnClickListener(v -> {
                centerPosition(position);
                v.postDelayed(card.action::run, 350);
            });
        }

        private void centerPosition(int position) {
            LinearSmoothScroller scroller = new LinearSmoothScroller(getContext()) {
                @Override protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                    int itemCenter = (targetView.getTop() + targetView.getBottom()) / 2;
                    int rvCenter = recyclerView.getHeight() / 2;
                    int dy = itemCenter - rvCenter;
                    action.update(0, dy, 400, new android.view.animation.DecelerateInterpolator());
                }
            };
            scroller.setTargetPosition(position);
            layoutManager.startSmoothScroll(scroller);
        }

        @Override public int getItemCount() { return cards.size() * LOOP_FACTOR; }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            ImageView ivIcon;
            LinearLayout container;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.my_card_title);
                ivIcon = v.findViewById(R.id.my_card_icon);
                container = v.findViewById(R.id.my_card_container);
            }
        }
    }
}
