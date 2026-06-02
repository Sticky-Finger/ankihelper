package com.mmjang.ankihelper.ui.plan;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mmjang.ankihelper.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeckSearchAdapter extends RecyclerView.Adapter<DeckSearchAdapter.ViewHolder> {

    private List<DeckItem> deckList;
    private List<DeckItem> originalList;
    private OnDeckSelectedListener listener;

    public interface OnDeckSelectedListener {
        void onDeckSelected(DeckItem deck);
    }

    public static class DeckItem {
        private long deckId;
        private String deckName;

        public DeckItem(long deckId, String deckName) {
            this.deckId = deckId;
            this.deckName = deckName;
        }

        public long getDeckId() {
            return deckId;
        }

        public String getDeckName() {
            return deckName;
        }

        @Override
        public String toString() {
            return deckName;
        }
    }

    public DeckSearchAdapter(Map<Long, String> deckMap, OnDeckSelectedListener listener) {
        this.originalList = convertMapToList(deckMap);
        this.deckList = new ArrayList<>(originalList);
        this.listener = listener;
    }

    private List<DeckItem> convertMapToList(Map<Long, String> deckMap) {
        List<DeckItem> list = new ArrayList<>();
        for (Map.Entry<Long, String> entry : deckMap.entrySet()) {
            list.add(new DeckItem(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    public void filter(String query) {
        deckList.clear();
        if (query.isEmpty()) {
            deckList.addAll(originalList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (DeckItem item : originalList) {
                if (item.getDeckName().toLowerCase().contains(lowerQuery)) {
                    deckList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_deck_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final DeckItem item = deckList.get(position);
        holder.textView.setText(item.getDeckName());

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onDeckSelected(item);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return deckList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public ViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(android.R.id.text1);
        }
    }
}
