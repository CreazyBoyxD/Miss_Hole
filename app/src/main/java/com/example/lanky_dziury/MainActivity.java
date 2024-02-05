package com.example.lanky_dziury;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button button1;
    private Button button2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button1 = findViewById(R.id.button1);
        button2 = findViewById(R.id.button2);
        button1.setOnClickListener(this);
        button2.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        Intent intent;
        if (view.getId() == R.id.button1) {
            intent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(intent);
        }

        if (view.getId() == R.id.button2) {
            intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        }
    }
}