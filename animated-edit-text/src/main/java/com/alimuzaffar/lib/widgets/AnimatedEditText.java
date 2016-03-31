/**
 * Copyright 2016 Ali Muzaffar
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alimuzaffar.lib.widgets;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.ViewUtils;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.Collection;

/**
 * An EditText field that can animate the typed text.
 * Current only support LTR languages.
 *
 * @author Ali Muzaffar
 */
public class AnimatedEditText extends AppCompatEditText {
    public static final String XML_NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android";

    private Paint mPaint;
    private Paint mAnimPaint;
    private Paint mCursorPaint;

    private ColorStateList mOriginalTextColors;
    private int mOriginalAlpha;
    private boolean mAnimated = true;
    private boolean mAnimatedClear = true;
    private AnimationType mAnimationType = AnimationType.BOTTOM_UP;
    private String mMask = null;
    private StringBuilder mMaskChars = null;

    private float mFixedRightOffset = 0;
    private float mFixedBottomOffset = 0;
    private float mAnimRightOffset = 0;
    private float mAnimBottomOffset = 0;
    private int mStart = 0;
    private int mEnd = 0;

    private AnimatorSet mAnimSet = null;

    private float mCursorX = 0;
    private boolean mAnimateCursor = true;
    private boolean mShouldAnimateCursor = false;
    //use as an object pool to prevent constant recreation.
    //not sure if this makes much of a difference as the Animator
    //object would still be recycled.
    Collection<Animator> mAnimationsToPlay = new ArrayList<>();


    public enum AnimationType {
        RIGHT_TO_LEFT, BOTTOM_UP, MIDDLE_UP, POP_IN, NONE
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
                setAnimationType(mAnimationType = AnimationType.BOTTOM_UP);
            } else if (outValue.data == 1) {
                setAnimationType(mAnimationType = AnimationType.RIGHT_TO_LEFT);
            } else if (outValue.data == 2) {
                setAnimationType(mAnimationType = AnimationType.MIDDLE_UP);
            } else if (outValue.data == 3) {
                setAnimationType(mAnimationType = AnimationType.POP_IN);
            } else if (outValue.data == -1) {
                setAnimationType(mAnimationType = AnimationType.NONE);
            }
            mMask = ta.getString(R.styleable.AnimatedEditText_textMask);
            mAnimatedClear = ta.getBoolean(R.styleable.AnimatedEditText_animateTextClear, mAnimatedClear);
            //Only allow animate cursor feature on API 16+
            mAnimateCursor = ta.getBoolean(R.styleable.AnimatedEditText_animateCursor, mAnimateCursor);
            mAnimateCursor = mAnimateCursor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && !ViewUtils.isLayoutRtl(this);
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

        mCursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorControlActivated,
                outValue, true);
        int colorSelected = outValue.data;
        mCursorPaint.setColor(colorSelected);
        mCursorPaint.setStrokeWidth(context.getResources().getDisplayMetrics().density * 2);
    }

    /**
     * Should the text be animated.
     *
     * @param animated should the text be animated.
     */
    public void setTextAnimated(boolean animated) {
        mAnimated = animated;
        if (animated) {
            setTextColor(Color.TRANSPARENT);
        } else if (mOriginalTextColors != null) {
            setTextColor(mOriginalTextColors);
        }
    }

    /**
     * Set the type of animation this view should use.
     *
     * @param animationType AnimationType RIGHT_TO_LEFT, BOTTOM_UP, MIDDLE_UP, POP_IN or NONE
     */
    public void setAnimationType(AnimationType animationType) {
        if (mAnimationType == null || animationType == AnimationType.NONE) {
            mAnimationType = AnimationType.NONE;
            setTextAnimated(false);
        } else {
            mAnimationType = animationType;
        }
    }

    /**
     * Animated the forward movement of the cursor.
     * <p>
     * Only available of JellyBean and above devices.
     * Does not work with RTL languages.
     *
     * @param animated Animate the cursors movement.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void setCursorAnimated(boolean animated) {
        mAnimateCursor = animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && !ViewUtils.isLayoutRtl(this);

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setupPaint();
    }

    private void setupPaint() {
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
        if (mAnimationType != AnimationType.NONE) {
            setTextColor(Color.TRANSPARENT);
        }
        if (!TextUtils.isEmpty(getText())) {
            mStart = 0;
            mEnd = getText().length();
            invalidate();
        }
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
        drawFixedText(canvas, fixedText, startX, getLineBounds(0, null));
        drawAnimText(canvas, getFullText(), startX + fixedTextWidth, getLineBounds(0, null));
        drawCursor(canvas, startX + fixedTextWidth);
    }

    /*
     * Should work, but doesn't nothing seems to draw, don't know why!
     */
    private void drawGravityRight(Canvas canvas) {
        canvas.translate(getScrollX(), 0);
        String fixedText = getFixedText();
        float fixedTextWidth = mPaint.measureText(fixedText);

        String animChar = getAnimText();
        float animCharWidth = mPaint.measureText(animChar);
        float fullTexWidth = fixedTextWidth + animCharWidth;

        float startX = getWidth() - getCompoundPaddingRight();
        drawFixedText(canvas, fixedText, startX - fullTexWidth, getLineBounds(0, null));
        drawAnimText(canvas, getFullText(), startX - animCharWidth, getLineBounds(0, null));
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
        drawFixedText(canvas, fixedText, startX, getLineBounds(0, null));
        drawAnimText(canvas, getFullText(), startXAnim, getLineBounds(0, null));
        drawCursor(canvas, startXAnim);
    }

    private void drawFixedText(Canvas canvas, String fixedText, float startX, float bottomX) {
        canvas.drawText(fixedText, startX + mFixedRightOffset, bottomX + mFixedBottomOffset, mPaint);
    }

    private void drawAnimText(Canvas canvas, CharSequence animText, float startX, float bottomX) {
        canvas.drawText(animText, mStart, mEnd, startX + mAnimRightOffset, bottomX + mAnimBottomOffset, mAnimPaint);
    }

    private void drawCursor(Canvas canvas, float startX) {
        if (mAnimateCursor && isFocused()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) { //redundant, but used to suppress lint
                if (!isCursorVisible()) {
                    float cursorStartX = startX + mCursorX;
                    float cursorStartY = getCompoundPaddingTop();
                    float cursorStopX = cursorStartX;
                    float cursorStopY = getHeight() - getContext().getResources().getDisplayMetrics().scaledDensity * 11;
                    canvas.drawLine(cursorStartX, cursorStartY, cursorStopX, cursorStopY, mCursorPaint);
                }
            }
        }
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
    public void setText(CharSequence text, final BufferType type) {

        if (mAnimated && mAnimatedClear && mPaint != null && TextUtils.isEmpty(text)) {
            AnimationEndListener endListener = new AnimationEndListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    AnimatedEditText.super.setText(null, type);
                }
            };

            mStart = 0;
            mEnd = getText().length();

            switch (mAnimationType) {
                case POP_IN:
                    animatePopIn(true, endListener);
                    break;
                case BOTTOM_UP:
                    animateInFromBottom(true, endListener);
                    break;
                case RIGHT_TO_LEFT:
                    animateInFromRight(true, endListener);
                    break;
                case MIDDLE_UP:
                    animateInFromMiddle(true, endListener);
                    break;
                default:
                    super.setText(text, type);
            }
        } else {
            super.setText(text, type);
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        mShouldAnimateCursor = mAnimateCursor && focused;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (!mAnimated) {
            return;
        }

        if (mPaint == null) {
            invalidate();
            return;
        }
        String added = TextUtils.substring(text, start, start + lengthAfter);
        int textLength = text.length();
        //Log.d("AnimatedEditText", String.format("text=%s, textLength=%d, start=%d, lengthBefore=%d, lengthAfter=%d", text, textLength, start, lengthBefore, lengthAfter));

        if (lengthAfter == 1 && added.equals(" ")) {
            return;
        }

        if (lengthBefore == lengthAfter) {
            //either swipe/auto-suggest did something, or someone edit or paste something
            //of the same length.
            mStart = textLength - 1;
            mEnd = textLength;
            return;
        }

        if (lengthBefore < lengthAfter && textLength == start + lengthAfter) {
            if (mAnimSet != null) {
                mAnimSet.cancel();
                mAnimSet = null;
            }
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
                case BOTTOM_UP:
                    animateInFromBottom();
                    break;
                default:
                    mStart = 0;
                    mEnd = text.length();
                    invalidate();
                    break;
            }
        } else {
            mStart = 0;
            mEnd = text.length();
        }
    }

    private void animateInFromBottom() {
        animateInFromBottom(false, null);
    }

    private void animateInFromBottom(boolean reverse, AnimationEndListener listener) {
        float start = reverse ? 0 : getHeight() - getCompoundPaddingBottom() - getCompoundPaddingTop();
        float end = reverse ? getHeight() - getCompoundPaddingBottom() - getCompoundPaddingTop() : 0;
        ValueAnimator animUp = ValueAnimator.ofFloat(start, end);
        animUp.setDuration(300);
        animUp.setInterpolator(new OvershootInterpolator());
        animUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAnimBottomOffset = (Float) animation.getAnimatedValue();
                AnimatedEditText.this.invalidate();
            }
        });

        int alphaStart = reverse ? mOriginalAlpha : 0;
        int alphaEnd = reverse ? 0 : mOriginalAlpha;
        ValueAnimator animAlpha = ValueAnimator.ofInt(alphaStart, alphaEnd);
        animAlpha.setDuration(reverse ? 100 : 300);
        animAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int a = (Integer) animation.getAnimatedValue();
                mAnimPaint.setAlpha(a);
            }
        });
        mAnimSet = new AnimatorSet();
        if (listener != null) {
            mAnimSet.addListener(listener);
        }
        mAnimationsToPlay.clear();
        mAnimationsToPlay.add(animAlpha);
        mAnimationsToPlay.add(animUp);
        if (mShouldAnimateCursor) {
            ValueAnimator animCursor = animateMoveCursor(reverse);
            mAnimationsToPlay.add(animCursor);
        }
        mAnimSet.playTogether(mAnimationsToPlay);
        mAnimSet.start();
    }

    private void animateInFromRight() {
        animateInFromRight(false, null);
    }

    private void animateInFromRight(boolean reverse, AnimationEndListener listener) {
        float start = reverse ? 0 : getWidth() + (getContext().getResources().getDisplayMetrics().widthPixels - getWidth());
        float end = reverse ? getWidth() + (getContext().getResources().getDisplayMetrics().widthPixels - getWidth()) : 0;
        ValueAnimator va = ValueAnimator.ofFloat(start, end);
        va.setDuration(300);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAnimRightOffset = (Float) animation.getAnimatedValue();
                AnimatedEditText.this.invalidate();
            }
        });
        mAnimSet = new AnimatorSet();
        if (listener != null) {
            mAnimSet.addListener(listener);
        }
        mAnimationsToPlay.clear();
        mAnimationsToPlay.add(va);
        if (mShouldAnimateCursor) {
            ValueAnimator animCursor = animateMoveCursor(reverse);
            mAnimationsToPlay.add(animCursor);
        }
        mAnimSet.playTogether(mAnimationsToPlay);
        mAnimSet.start();
    }

    private void animateInFromMiddle() {
        animateInFromMiddle(false, null);
    }

    private void animateInFromMiddle(boolean reverse, AnimationEndListener listener) {
        String fixed = TextUtils.substring(getText(), 0, mStart);
        final float textWidth = mPaint.measureText(fixed);
        float startMiddle = reverse ? textWidth : getWidth() / 2;
        float endMiddle = reverse ? getWidth() / 2 : textWidth;

        ValueAnimator animMiddle = ValueAnimator.ofFloat(startMiddle, endMiddle);
        animMiddle.setInterpolator(new DecelerateInterpolator());
        animMiddle.setDuration(200);
        animMiddle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAnimRightOffset = (Float) animation.getAnimatedValue();
                mAnimRightOffset -= textWidth;
                AnimatedEditText.this.invalidate();
            }
        });

        float startUp = reverse ? 0 : getHeight() - getCompoundPaddingBottom() - getCompoundPaddingTop();
        float endUp = reverse ? getHeight() - getCompoundPaddingBottom() - getCompoundPaddingTop() : 0;
        ValueAnimator animUp = ValueAnimator.ofFloat(startUp, endUp);
        animUp.setDuration(200);
        animUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAnimBottomOffset = (Float) animation.getAnimatedValue();
                AnimatedEditText.this.invalidate();
            }
        });

        int alphaStart = reverse ? mOriginalAlpha : 0;
        int alphaEnd = reverse ? 0 : mOriginalAlpha;
        ValueAnimator animAlpha = ValueAnimator.ofInt(alphaStart, alphaEnd);
        animAlpha.setDuration(300);
        animAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int a = (Integer) animation.getAnimatedValue();
                mAnimPaint.setAlpha(a);
            }
        });
        mAnimSet = new AnimatorSet();
        if (listener != null) {
            mAnimSet.addListener(listener);
        }
        mAnimationsToPlay.clear();
        mAnimationsToPlay.add(animUp);
        mAnimationsToPlay.add(animAlpha);
        mAnimationsToPlay.add(animMiddle);
        if (mShouldAnimateCursor) {
            ValueAnimator animCursor = animateMoveCursor(reverse);
            mAnimationsToPlay.add(animCursor);
        }
        mAnimSet.playTogether(mAnimationsToPlay);
        mAnimSet.start();
    }

    private void animatePopIn() {
        animatePopIn(false, null);
    }

    private void animatePopIn(final boolean reverse, AnimationEndListener listener) {
        float start = reverse ? getPaint().getTextSize() : 1;
        float end = reverse ? 1 : getPaint().getTextSize();
        ValueAnimator va = ValueAnimator.ofFloat(start, end);
        va.setInterpolator(new OvershootInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mAnimPaint.setTextSize((Float) animation.getAnimatedValue());
                AnimatedEditText.this.invalidate();
            }
        });
        mAnimSet = new AnimatorSet();
        if (listener != null) {
            mAnimSet.addListener(listener);
        }
        mAnimationsToPlay.clear();
        mAnimationsToPlay.add(va);
        if (mShouldAnimateCursor) {
            ValueAnimator animCursor = animateMoveCursor(reverse);
            mAnimationsToPlay.add(animCursor);
        }
        mAnimSet.playTogether(mAnimationsToPlay);
        mAnimSet.setDuration(200);
        mAnimSet.start();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private ValueAnimator animateMoveCursor(boolean reverse) {
        float start = reverse ? getPaint().measureText(getAnimText()) : 0;
        float end = reverse ? 0 : getPaint().measureText(getAnimText());
        ValueAnimator va = ValueAnimator.ofFloat(start, end);

        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCursorX = getCompoundPaddingLeft() + (Float) animation.getAnimatedValue();
            }
        });
        if (isCursorVisible()) {
            va.addListener(new AnimationEndListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    setCursorVisible(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    setCursorVisible(true);
                    setSelection(getText().length());
                }
            });
        }
        return va;
    }


}
