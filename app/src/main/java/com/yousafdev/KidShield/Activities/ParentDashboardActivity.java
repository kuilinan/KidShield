package com.yousafdev.KidShield.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yousafdev.KidShield.Adapters.ChildAdapter;
import com.yousafdev.KidShield.Models.Child;
import com.yousafdev.KidShield.R;

import java.util.ArrayList;
import java.util.List;

public class ParentDashboardActivity extends AppCompatActivity implements ChildAdapter.OnChildListener {

    private RecyclerView recyclerView;
    private ChildAdapter adapter;
    private List<Child> childList;
    private ProgressBar progressBar;

// ⚠️ REMOVED FIREBASE: private DatabaseReference databaseReference;
// ⚠️ REMOVED FIREBASE: private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_dashboard);

        recyclerView = findViewById(R.id.recyclerView_children);
        progressBar = findViewById(R.id.progressBar_dashboard);

        // 使用自建 API

        childList = new ArrayList<>();
        adapter = new ChildAdapter(childList, this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fetchChildren();
    }

    private void fetchChildren() {
        progressBar.setVisibility(View.VISIBLE);
        if (currentUser == null) return;

        Query query = databaseReference.orderByChild("parentEmail").equalTo(currentUser.getEmail());

// ⚠️ REMOVED FIREBASE: query.addValueEventListener(new ValueEventListener() {
            @Override
// ⚠️ REMOVED FIREBASE: public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                childList.clear();
// ⚠️ REMOVED FIREBASE: for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String uid = snapshot.getKey();
                    String email = snapshot.child("email").getValue(String.class);
                    if (email != null && uid != null) {
                        childList.add(new Child(uid, email));
                    }
                }
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
            }

            @Override
// ⚠️ REMOVED FIREBASE: public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(ParentDashboardActivity.this, "加载孩子列表失败：" + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onChildClick(int position) {
        Child selectedChild = childList.get(position);
        Intent intent = new Intent(this, ChildDetailActivity.class);
        intent.putExtra("CHILD_UID", selectedChild.getUid());
        intent.putExtra("CHILD_EMAIL", selectedChild.getEmail());
        startActivity(intent);
    }
}