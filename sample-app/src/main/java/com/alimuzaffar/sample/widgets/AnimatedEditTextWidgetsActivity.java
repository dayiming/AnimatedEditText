package com.alimuzaffar.sample.widgets;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.alimuzaffar.lib.widgets.AnimatedEditText;
import com.alimuzaffar.lib.widgets.PinEntryEditText;
import com.alimuzaffar.lib.widgets.TextDrawable;

public class AnimatedEditTextWidgetsActivity extends AppCompatActivity {
    AnimatedEditText mTxtPopIn;
    AnimatedEditText mTxtBottomUp;
    AnimatedEditText mTxtRightIn;
    AnimatedEditText mTxtMiddleUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_text);
        mTxtPopIn = (AnimatedEditText) findViewById(R.id.txt_pop_in);
        mTxtBottomUp = (AnimatedEditText) findViewById(R.id.txt_bottom_up);
        mTxtRightIn = (AnimatedEditText) findViewById(R.id.txt_right_in);
        mTxtMiddleUp = (AnimatedEditText) findViewById(R.id.txt_middle_up);

        EditText regular = (EditText) findViewById(R.id.txt_regular);
        final PinEntryEditText pinEntry = (PinEntryEditText) findViewById(R.id.txt_pin_entry);
        if (pinEntry != null) {
            pinEntry.setAnimateText(true);
            pinEntry.setOnPinEnteredListener(new PinEntryEditText.OnPinEnteredListener() {
                @Override
                public void onPinEntered(CharSequence str) {
                    if (str.toString().equals("1234")) {
                        Toast.makeText(AnimatedEditTextWidgetsActivity.this, "SUCCESS", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AnimatedEditTextWidgetsActivity.this, "FAIL", Toast.LENGTH_SHORT).show();
                        pinEntry.setText(null);
                    }
                }
            });
        }
        if (regular != null) {
            regular.setCompoundDrawables(new TextDrawable(regular, "+61 "), null, new TextDrawable(regular, "\u2605"), null);
        }
    }

    public void onClick_clear(View view) {
        toggle(mTxtPopIn);
        toggle(mTxtBottomUp);
        toggle(mTxtRightIn);
        toggle(mTxtMiddleUp);
    }

    public void toggle(AnimatedEditText v) {
        if (TextUtils.isEmpty(v.getText())) {
            v.setText("Hello World");
        } else {
            v.setText(null);
        }
    }

}
