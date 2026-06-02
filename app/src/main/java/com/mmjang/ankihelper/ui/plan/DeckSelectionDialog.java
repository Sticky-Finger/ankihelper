package com.mmjang.ankihelper.ui.plan;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.mmjang.ankihelper.R;

import java.util.Map;

public class DeckSelectionDialog extends Dialog {

    private Map<Long, String> deckList;
    private long currentDeckId;
    private OnDeckSelectedListener listener;
    private DeckSearchAdapter adapter;

    public interface OnDeckSelectedListener {
        void onDeckSelected(long deckId, String deckName);
    }

    public DeckSelectionDialog(Context context,
                               Map<Long, String> deckList,
                               long currentDeckId,
                               OnDeckSelectedListener listener) {
        super(context);
        this.deckList = deckList;
        this.currentDeckId = currentDeckId;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_deck_selection);
        setTitle(R.string.title_select_deck);

        EditText searchEdit = (EditText) findViewById(R.id.deck_search_edit);
        RecyclerView recycler = (RecyclerView) findViewById(R.id.deck_list_recycler);

        // 创建适配器
        adapter = new DeckSearchAdapter(deckList, new DeckSearchAdapter.OnDeckSelectedListener() {
            @Override
            public void onDeckSelected(DeckSearchAdapter.DeckItem deck) {
                currentDeckId = deck.getDeckId();
                if (listener != null) {
                    listener.onDeckSelected(deck.getDeckId(), deck.getDeckName());
                }
                dismiss();
            }
        });

        recycler.setAdapter(adapter);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));

        // 搜索监听
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}
