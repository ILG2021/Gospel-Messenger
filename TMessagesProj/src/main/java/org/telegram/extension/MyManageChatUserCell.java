package org.telegram.extension;

import android.content.Context;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.widget.ImageView;

import com.blankj.utilcode.util.LogUtils;

import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MyManageChatUserCell extends ManageChatUserCell {
    public long chatId;
    private UnExcludeListener excludeListener;
    private CountDownTimer countDownTimer;

    public interface UnExcludeListener {
        void onExclude();
    }

    public MyManageChatUserCell(Context context, int avatarPadding, int nPadding, boolean needOption) {
        super(context, avatarPadding, nPadding, needOption);
    }

    public MyManageChatUserCell(Context context, int avatarPadding, int nPadding, boolean needOption, Theme.ResourcesProvider resourcesProvider) {
        super(context, avatarPadding, nPadding, needOption, resourcesProvider);
    }

    public void setInviteCustomImage(int resId) {
        customImageView = new ImageView(getContext());
        customImageView.setImageResource(resId);
        customImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addView(customImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP));
    }

    public TLRPC.User getUser() {
        if (currentObject instanceof TLRPC.User) {
            return (TLRPC.User) currentObject;
        }
        return null;
    }

    public boolean isExcluding() {
        return customImageView.getAlpha() == 1.0f;
    }

    public void setExcludeListener(UnExcludeListener excludeListener) {
        this.excludeListener = excludeListener;
    }

    public boolean isCalling() {
        Set<String> callingUsers = GroupCallUtil.callingSp().getStringSet(chatId + "", new HashSet<>());
        for (String value : callingUsers) {
            if (value.contains(getUserId() + "_")) {
                long startTime = Long.parseLong(value.replace(getUserId() + "_", ""));
                if (System.currentTimeMillis() - startTime > 60 * 1000) {
                    callingUsers.remove(value);
                    GroupCallUtil.callingSp().put(chatId + "", callingUsers);
                    break;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private long getCallStartTime() {
        Set<String> callingUsers = GroupCallUtil.callingSp().getStringSet(chatId + "", new HashSet<>());
        for (String value : callingUsers) {
            if (value.contains(getUserId() + "_")) {
                return Long.parseLong(value.replace(getUserId() + "_", ""));
            }
        }
        return 0;
    }

    public void startCountDown(long millisInFuture) {
        LogUtils.d("millisInFuture" + millisInFuture);
        countDownTimer = new CountDownTimer(millisInFuture, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                statusTextView.setText(LocaleController.getString("VoipGroupCalling", R.string.VoipGroupCalling) + " " + seconds + "s");
            }

            @Override
            public void onFinish() {
                Set<String> callingUsers = GroupCallUtil.callingSp().getStringSet(chatId + "", new HashSet<>());
                Iterator<String> iterator = callingUsers.iterator();
                while (iterator.hasNext()) {
                    String value = iterator.next();
                    if (value.contains(getUserId() + "_")) {
                        iterator.remove();
                        GroupCallUtil.callingSp().put(chatId + "", callingUsers);
                        update(0);
                    }
                }
            }
        }.start();
    }

    public void cancelCountDown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    @Override
    public void recycle() {
        super.recycle();
        cancelCountDown();
    }

    @Override
    public void update(int mask) {
        super.update(mask);
        if (isCalling()) {
            statusTextView.setTextColor(statusOnlineColor);
            long startTime = getCallStartTime();
            startCountDown(GroupCallUtil.RING_COUNT_DOWN - (System.currentTimeMillis() - startTime));
        } else
            cancelCountDown();
    }

    public void setCustomImageData(long chatId, TLRPC.User user) {
        Set<String> excludeUsers = GroupCallUtil.excludeSp().getStringSet(chatId + "", new HashSet<>());

        if (excludeUsers.contains(user.id + "")) {
            customImageView.setAlpha(1.0f);
        } else {
            customImageView.setAlpha(0.3f);
        }
        customImageView.setOnClickListener(v -> {
            Set<String> excludeUsers1 = GroupCallUtil.excludeSp().getStringSet(chatId + "", new HashSet<>());

            if (customImageView.getAlpha() == 1.0f) {
                customImageView.setAlpha(0.3f);
                excludeUsers1.remove(user.id + "");
                GroupCallUtil.sendCommandMessage(GroupCallUtil.UNEXCLUDE, chatId, user);
            } else {
                customImageView.setAlpha(1.0f);
                excludeUsers1.add(user.id + "");
                GroupCallUtil.sendCommandMessage(GroupCallUtil.EXCLUDE, chatId, user);

                Set<String> callingUsers = GroupCallUtil.callingSp().getStringSet(chatId + "", new HashSet<>());
                callingUsers.removeIf(it -> it.contains(user.id + "_"));
                GroupCallUtil.callingSp().put(chatId + "", callingUsers);
                if (excludeListener != null)
                    excludeListener.onExclude();
            }
            GroupCallUtil.excludeSp().put(chatId + "", excludeUsers1);
        });
    }
}
