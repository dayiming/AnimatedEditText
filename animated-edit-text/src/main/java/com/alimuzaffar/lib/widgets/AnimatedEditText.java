/**
 * Copyright 2016 Ali Muzaffar
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alimuzaffar.lib.widgets;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * An EditText field that can animate the typed text.
 * Current only support LTR languages.
 *
 * @author Ali Muzaffar
 */
public class AnimatedEditText extends AppCompatEditText {
    private Paint mPaint;
    private Paint mAnimPaint;
    private ColorStateList mOriginalTextColors;
    private int mOriginalAlpha;
    private boolean mAnimated = true;
    private AnimationType mAnimationType = AnimationType.BOTTOM_UP;
    private String mMask = null;
    private StringBuilder mMaskChars = null;

    private float mRightOffset = 0;
    private float mBottomOffset = 0;
    private int mStart = 0;
    private int mEnd = 0;


    public enum AnimationType {
        RIGHT_TO_LEFT, BOTTOM_UP, MIDDLE_UP, POP_IN
    }

    public AnimatedEditText(Context context) {
        super(context);
        init(context, null);
    }

    public AnimatedEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AnimatedEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.AnimatedEditText, 0, 0);
        try {
            TypedValue outValue = new TypedValue();
            ta.getValue(R.styleable.AnimatedEditText_animationType, outValue);
            if (outValue.data == 0) {
                setAnimationType(AnimationType.BOTTOM_UP);
            } else if (outValue.data == 1) {
                setAnimationType(AnimationType.RIGHT_TO_LEFT);
            } else if (outValue.data == 2) {
                setAnimationType(mAnimationType = AnimationType.MIDDLE_UP);
            } else if (outValue.data == 3) {
                setAnimationType(mAnimationType = AnimationType.POP_IN);
            }
            mMask = ta.getString(R.styleable.AnimatedEditText_textMask);
        } finally {
            ta.recycle();
        }

        //If input type is password and no mask is set, use a default mask
        if ((getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD && TextUtils.isEmpty(mMask)) {
            mMask = "\u25CF";
        } else if ((getInputType() & InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD && TextUtils.isEmpty(mMask)) {
            mMask = "\u25CF";
        }

        if (!TextUtils.isEmpty(mMask)) {
            mMaskChars = getMaskChars();
        }
    }

    public void setTextAnimated(boolean animated) {
        mAnimated = animated;
        if (animated) {
            setTextColor(Color.TRANSPARENT);
        } else {
            setTextColor(mOriginalTextColors);
        }
    }

    public void setAnimationType(AnimationType animationType) {
        if (mAnimationType == null) {
            mAnimationType = AnimationType.BOTTOM_UP;
            setTextAnimated(false);
        } else {
            mAnimationType = animationType;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mPaint = new Paint(getPaint());
        mAnimPaint = new Paint(getPaint());
        mOriginalTextColors = getTextColors();
        // This is needed, otherwise the cursor doesn't stay in sync
        // when input type is not set to password.
        if (!TextUtils.isEmpty(mMask)) {
            mPaint.setTypeface(Typeface.MONOSPACE);
            mAnimPaint.setTypeface(Typeface.MONOSPACE);
        }
        if (mOriginalTextColors != null) {
            mPaint.setColor(mOriginalTextColors.getDefaultColor());
            mAnimPaint.setColor(mOriginalTextColors.getDefaultColor());
            mOriginalAlpha = mAnimPaint.getAlpha();
        }
        setTextColor(Color.TRANSPARENT);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mAnimated) {
            return;
        }

        updateColorsForState();

        boolean rightAligned = (getGravity() & Gravity.RIGHT) == Gravity.RIGHT || (getGravity() & Gravity.END) == Gravity.END;
        boolean leftAligned = (getGravity() & Gravity.LEFT) == Gravity.LEFT || (getGravity() & Gravity.START) == Gravity.START;

        if (rightAligned) {
            drawGravityRight(canvas);
        } else if (leftAligned) {
            drawGravityLeft(canvas);
        } else {
            drawGravityCenterHorizontal(canvas);
        }
    }


    private void drawGravityLeft(Canvas canvas) {
        String fixedText = getFixedText();
        float fixedTextWidth = mPaint.measureText(fixedText);
        float startX = getCompoundPaddingLeft();
        canvas.drawText(fixedText, startX, getLineBounds(0, null), mPaint);
        canvas.drawText(getFullText(), mStart, mEnd, startX + fixedTextWidth + mRightOffset, getLineBounds(0, null) + mBottomOffset, mAnimPaint);
    }

    /*
    Should work, but doesn't nothing seems to draw, dont know why!
     */
    private void drawGravityRight(Canvas canvas) {
        canvas.translate(getScrollX(), 0);
        String fixedText = getFixedText();
        float fixedTextWidth = mPaint.measureText(fixedText);

        String animChar = getAnimText();
        float animCharWidth = mPaint.measureText(animChar);
        float fullTexWidth = fixedTextWidth + animCharWidth;

        float startX = getWidth() - getCompoundPaddingRight();
        canvas.drawText(fixedText, startX - fullTexWidth, getLineBounds(0, null), mPaint);
        canvas.drawText(getFullText(), mStart, mEnd, startX - animCharWidth + mRightOffset, getLineBounds(0, null) + mBottomOffset, mAnimPaint);

    }

    private void drawGravityCenterHorizontal(Canvas canvas) {
        canvas.translate(getScrollX(), 0);
        String fixedText = getFixedText();
        float fixedTextWidth = mPaint.measureText(fixedText);

        String animChar = getAnimText();
        float animCharWidth = mPaint.measureText(animChar);
        float fullTexWidth = fixedTextWidth + animCharWidth;

        float startX = getWidth() / 2 - fullTexWidth / 2;
        float startXAnim = getWidth() / 2 + fullTexWidth / 2 - animCharWidth;
        canvas.drawText(fixedText, startX, getLineBounds(0, null), mPaint);
        canvas.drawText(getFullText(), mStart, mEnd, startXAnim + mRightOffset, getLineBounds(0, null) + mBottomOffset, mAnimPaint);
    }

    private void updateColorsForState() {
        if (mOriginalTextColors == null) {
            return;
        }
        int[] states = {
                isEnabled() ? android.R.attr.state_enabled : -android.R.attr.state_enabled,
                isFocused() ? android.R.attr.state_focused : -android.R.attr.state_focused,
                isSelected() ? android.R.attr.state_selected : -android.R.attr.state_selected,
        };
        int color = mOriginalTextColors.getColorForState(states, mOriginalTextColors.getDefaultColor());

        mPaint.setColor(color);

        int alpha = mAnimPaint.getAlpha();
        mAnimPaint.setColor(color);
        mAnimPaint.setAlpha(alpha); //retain alpha which may change because of animation.
    }

    private CharSequence getFullText() {
        if (TextUtils.isEmpty(mMask)) {
            return getText();
        } else {
            return getMaskChars();
        }
    }

    private String getFixedText() {
        if (TextUtils.isEmpty(mMask)) {
            return TextUtils.substring(getText(), 0, mStart);
        } else {
            return TextUtils.substring(getMaskChars(), 0, mStart);
        }
    }

    private String getAnimText() {
        if (TextUtils.isEmpty(mMask)) {
            return TextUtils.substring(getText(), mStart, mEnd);
        } else {
            return TextUtils.substring(getMaskChars(), mStart, mEnd);
        }
    }

    private StringBuilder getMaskChars() {
        if (mMaskChars == null) {
            mMaskChars = new StringBuilder();
        }
        int textLength = getText().length();
        while (mMaskChars.length() != textLength) {
            if (mMaskChars.length() < textLength) {
                mMaskChars.append(mMask);
            } else {
                mMaskChars.deleteCharAt(mMaskChars.length() - 1);
            }
        }
        return mMaskChars;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        String added = TextUtils.substring(text, start, start + lengthAfter);
        int textLength = text.length();
        //Log.d("AnimatedEditText", String.format("text=%s, textLength=%d, start=%d, lengthBefore=%d, lengthAfter=%d", text, textLength, start, lengthBefore, lengthAfter));

        if (lengthAfter == 1 && added.equals(" ")) {
            return;
        }

        if (lengthBefore == lengthAfter) {
            //either swipe/autosuggest did something, or someone edit or paste something
            //of the same length.
            mStart = textLength - 1;
            mEnd = textLength;
            return;
        }

        if (lengthBefore < lengthAfter && textLength == start + lengthAfter) {
            //if we are adding text & adding it to the end of the line.
            if (lengthBefore == 0) { //normal case when tapping keyboard.
                mStart = start;
                mEnd = start + lengthAfter;
            } else {
                //if using auto suggest, it can result in animating the whole word every
                //time a character is tapped. This forces only the last character to animate.
                mStart = textLength - 1;
                mEnd = textLength;
            }
            switch (mAnimationType) {
                case RIGHT_TO_LEFT:
                    animateInFromRight();
                    break;
                case MIDDLE_UP:
                    animateInFromMiddle();
                    break;
                case POP_IN:
                    animatePopIn();
                    break;
                default:
                    animateInFromBottom();
            }
        } else {
            mStart = 0;
            mEnd = text.length();
        }
    }

    private void animateInFromBottom() {
        ValueAnimator animUp = ValueAnimator.ofFloat(getHeight() - getCompoundPaddingBottom() - getCompoundPaddingTop(), 0);
        animUp.setDuration(300);
        animUp.setInterpolator(new OvershootInterpolator());
        animUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mBottomOffset = (Float) animation.getAnimatedValue();
                AnimatedEditText.this.invalidate();
            }
        });
        ValueAnimator animAlpha = ValueAnimator.ofInt(0, mOriginalAlpha);
        animAlpha.setDuration(300);
        animAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int a = (Integer) animation.getAnimatedValue();
                mAnimPaint.setAlpha(a);
            }
        });
        AnimatorSet set = new AnimatorSet();
        set.playTogether(animUp, animAlpha);
        set.start();
    }

    private void animateInFromRight() {
        ValueAnimator va = ValueAnimator.ofFloat(getWidth() + (getContext().getResources().getDisplayMetrics().widthPixels - getWidth()), 0);
        va.setDuration(300);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mRightOffset = (Float) animation.getAnimatedValue();
                AnimatedEditText.this.invalidate();
            }
        });
        va.start();
    }

    private void animateInFromMiddle() {
        String fixed = TextUtils.substring(getText(), 0, mStart);
        final float textWidth = mPaint.measureText(fixed);
        ValueAnimator animMiddle = ValueAnimator.ofFloat(getWidth() / 2, textWidth);
        animMiddle.setInterpolator(new DecelerateInterpolator());
        animMiddle.setDuration(200);
        animMiddle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mRightOffset = (Float) animation.getAnimatedValue();
                mRightOffset -= textWidth;
                AnimatedEditText.this.invalidate();
            }
        });
        ValueAnimator animUp = ValueAnimator.ofFloat(getHeight() - getCompoundPaddingBottom() - getCompoundPaddingTop(), 0);
        animUp.setDuration(200);
        animUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mBottomOffset = (Float) animation.getAnimatedValue();
                AnimatedEditText.this.invalidate();
            }
        });
        ValueAnimator animAlpha = ValueAnimator.ofInt(0, mOriginalAlpha);
        animAlpha.setDuration(300);
        animAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int a = (Integer) animation.getAnimatedValue();
                mAnimPaint.setAlpha(a);
            }
        });
        AnimatorSet set = new AnimatorSet();
        set.playTogether(animUp, animAlpha, animMiddle);
        set.start();
    }

    private void animatePopIn() {
        ValueAnimator va = ValueAnimator.ofFloat(1, mPaint.getTextSize());
        va.setDuration(200);
        va.setInterpolator(new OvershootInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAnimPaint.setTextSize((Float) animation.getAnimatedValue());
                AnimatedEditText.this.invalidate();
            }
        });
        va.start();
    }

}
