package com.gaozay.smartflight.activityfixture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 80, 24, 24);
        TextView heading = new TextView(this);
        heading.setText(getClass().getSimpleName());
        content.addView(heading);
        addButton(content, "First", FirstActivity.class);
        addButton(content, "Second", SecondActivity.class);
        Button dialog = new Button(this);
        dialog.setText("Show dialog");
        dialog.setOnClickListener(view -> new AlertDialog.Builder(this).setMessage("Dialog is not an Activity")
            .setPositiveButton("Close", (d, w) -> d.dismiss()).show());
        content.addView(dialog);
        setContentView(content);
    }
    private void addButton(LinearLayout content, String text, Class<?> target) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(view -> startActivity(new Intent(this, target)));
        content.addView(button);
    }
}
