package com.example.lanky_dziury;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity implements View.OnClickListener {
    private Button button3;
    private Button button4;
    private SeekBar Threshold;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Threshold = findViewById(R.id.Threshold);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        int savedThreshold = sharedPreferences.getInt("hole_detection_threshold", 3);
        Threshold.setProgress(savedThreshold);

        button4 = findViewById(R.id.button4);
        button4.setOnClickListener(this);
        button3 = findViewById(R.id.button3);
        button3.setOnClickListener(this);

        Threshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sharedPreferences.edit().putInt("hole_detection_threshold", progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    @Override
    public void onClick(View view) {
        Intent intent;
        if (view.getId() == R.id.button3) {
            intent = new Intent(SettingsActivity.this, MainActivity.class);
            startActivity(intent);
        }
        if (view.getId() == R.id.button4) {
            clearDatabase();
        }
    }

    private void clearDatabase() {
        try {
            SQLiteDatabase database = openOrCreateDatabase("kebab.db", MODE_PRIVATE, null);
            database.execSQL("DELETE FROM Markers");
            database.close();
            Toast.makeText(this, "Markers deleted.", Toast.LENGTH_SHORT).show();
        } catch (SQLiteException e) {
            Toast.makeText(this, "Failed to delete markers!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}
