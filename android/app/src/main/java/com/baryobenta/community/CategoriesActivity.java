package com.baryobenta.community;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Category list from PostgreSQL; tapping one opens Browse filtered to it. */
public class CategoriesActivity extends AppCompatActivity implements CategoryRowAdapter.OnCategoryClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);

        findViewById(R.id.button_categories_back).setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_categories_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        final CategoryRowAdapter adapter = new CategoryRowAdapter(this);
        recyclerView.setAdapter(adapter);

        CategoryRepo.load(this, false, new CategoryRepo.Listener() {
            @Override
            public void onLoaded(List<Category> categories) {
                adapter.setItems(categories);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CategoriesActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onCategoryClick(Category category) {
        Intent i = new Intent(this, ListingActivity.class);
        i.putExtra(ListingActivity.EXTRA_CATEGORY_ID, category.getId());
        startActivity(i);
    }
}
