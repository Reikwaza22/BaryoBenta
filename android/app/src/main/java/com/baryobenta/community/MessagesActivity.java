package com.baryobenta.community;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Conversation list built from the messages table. */
public class MessagesActivity extends AppCompatActivity implements ConversationAdapter.OnConversationClickListener {

    private ConversationAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        findViewById(R.id.button_messages_back).setOnClickListener(v -> finish());
        emptyView = findViewById(R.id.text_empty_messages);

        RecyclerView recyclerView = findViewById(R.id.recycler_messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversationAdapter(this);
        recyclerView.setAdapter(adapter);

        ((EditText) findViewById(R.id.input_search_messages)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApiHelper.call(this, RetrofitClient.getApi(this).conversations(),
                new ApiHelper.Result<List<Conversation>>() {
                    @Override
                    public void onSuccess(List<Conversation> data, String message) {
                        adapter.setItems(data);
                        emptyView.setText(R.string.empty_messages);
                        emptyView.setVisibility(data == null || data.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onError(String message) {
                        emptyView.setText(message);
                        emptyView.setVisibility(View.VISIBLE);
                    }
                });
    }

    @Override
    public void onConversationClick(Conversation c) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra(ChatActivity.EXTRA_LISTING_ID, c.getListingId());
        i.putExtra(ChatActivity.EXTRA_OTHER_ID, c.getOtherId());
        i.putExtra(ChatActivity.EXTRA_OTHER_NAME, c.getOtherName());
        i.putExtra(ChatActivity.EXTRA_LISTING_TITLE, c.getListingTitle());
        startActivity(i);
    }
}
