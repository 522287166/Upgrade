package com.upgradelibrary.common;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Preconditions;
import android.util.Log;
import android.widget.Toast;

import com.upgradelibrary.R;
import com.upgradelibrary.Util;
import com.upgradelibrary.bean.Upgrade;
import com.upgradelibrary.bean.UpgradeOptions;

import java.lang.ref.WeakReference;

/**
 * Author: SXF
 * E-mail: xue.com.fei@outlook.com
 * CreatedTime: 2018/2/28 11:24
 * <p>
 * 升级管理
 */

public class UpgradeManager {
    private static final String TAG = UpgradeManager.class.getSimpleName();
    private Activity activity;
    private CheckForUpdatesTask task;

    public UpgradeManager(Activity activity) {
        this.activity = activity;
    }

    /**
     * 检测更新
     *
     * @param options     更新选项
     * @param isAutoCheck 是否自动检测更新
     */
    @SuppressLint("RestrictedApi")
    public void checkForUpdates(@NonNull UpgradeOptions options, boolean isAutoCheck) {
        execute(Preconditions.checkNotNull(options), isAutoCheck);
    }

    /**
     * 检测更新
     *
     * @param options           更新选项
     * @param onUpgradeListener 更新监听回调接口
     */
    @SuppressLint("RestrictedApi")
    public void checkForUpdates(@NonNull UpgradeOptions options, @Nullable OnUpgradeListener onUpgradeListener) {
        execute(Preconditions.checkNotNull(options), onUpgradeListener);
    }

    /**
     * 执行检测更新
     *
     * @param parames
     */
    private void execute(Object... parames) {
        if (task == null || task.getStatus() == AsyncTask.Status.FINISHED) {
            task = new CheckForUpdatesTask(activity);
        }
        if (task.getStatus() == AsyncTask.Status.RUNNING) {
            return;
        }
        task.execute(parames);
    }

    /**
     * 取消检测更新
     */
    public void cancel() {
        if (task == null) {
            return;
        }
        if (!task.isCancelled()) {
            task.cancel(false);
        }
        Log.d(TAG, "cancel checked updates");
    }

    /**
     * 更新监听回调接口
     */
    public interface OnUpgradeListener {

        void onUpdateAvailable(UpgradeServiceManager manager);

        void onUpdateAvailable(Upgrade.Stable stable, UpgradeServiceManager manager);

        void onUpdateAvailable(Upgrade.Bate bate, UpgradeServiceManager manager);

        void onNoUpdateAvailable(String message);

    }

    /**
     * 检测更新任务
     */
    public static class CheckForUpdatesTask extends AsyncTask<Object, Void, Message> {
        private static final int RESULT_CODE_TRUE = 0x1024;
        private static final int RESULT_CODE_FALSE = 0x1025;
        private WeakReference<Activity> reference;

        public CheckForUpdatesTask(Activity activity) {
            this.reference = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Message doInBackground(Object... objects) {
            Message message = new Message();
            message.what = RESULT_CODE_TRUE;
            message.obj = objects[1];
            message.setData(new Bundle());
            try {
                UpgradeOptions upgradeOptions = (UpgradeOptions) objects[0];
                if (upgradeOptions.getUrl() != null && upgradeOptions.getUrl().endsWith(".apk")) {
                    message.getData().putParcelable("upgrade_options", upgradeOptions);
                    return message;
                }

                if (upgradeOptions.getUrl() != null && upgradeOptions.getUrl().endsWith(".xml")) {
                    Upgrade upgrade = Upgrade.parser(upgradeOptions.getUrl());
                    if (upgrade != null) {
                        message.getData().putParcelable("upgrade", upgrade);
                        message.getData().putParcelable("upgrade_options", upgradeOptions);
                        return message;
                    }
                }
                throw new IllegalArgumentException("Url：" + upgradeOptions.getUrl() + " link error");
            } catch (Exception e) {
                e.printStackTrace();
                message.what = RESULT_CODE_FALSE;
                message.getData().putString("message", e.getMessage());
            }
            return message;
        }

        @Override
        protected void onCancelled(Message message) {
            super.onCancelled(message);
        }

        @Override
        protected void onPostExecute(Message message) {
            Activity activity = reference.get();
            if (activity == null) {
                return;
            }
            Bundle bundle = message.getData();
            Upgrade upgrade = bundle.getParcelable("upgrade");
            UpgradeOptions upgradeOptions = bundle.getParcelable("upgrade_options");
            UpgradeOptions.Builder builder = new UpgradeOptions.Builder()
                    .setIcon(upgradeOptions.getIcon())
                    .setTitle(upgradeOptions.getTitle())
                    .setDescription(upgradeOptions.getDescription())
                    .setStorage(upgradeOptions.getStorage())
                    .setUrl(upgradeOptions.getUrl())
                    .setMutiThreadEnabled(upgradeOptions.isMultithreadEnabled())
                    .setMaxThreadPools(upgradeOptions.getMaxThreadPools())
                    .setMd5(upgradeOptions.getMd5());
            switch (message.what) {
                case RESULT_CODE_TRUE:
                    if (upgrade == null) {
                        if (message.obj instanceof Boolean) {
                            new UpgradeServiceManager(activity, builder.build()).start();
                        } else {
                            if (message.obj == null) {
                                return;
                            }
                            OnUpgradeListener onUpgradeListener = (OnUpgradeListener) message.obj;
                            onUpgradeListener.onUpdateAvailable(new UpgradeServiceManager(activity, builder.build()));
                        }
                    } else {
                        if (upgrade.getStable() != null && upgrade.getBate() != null) {
                            if (!upgrade.getBate().getDevice().contains(Util.getSerial()) || upgrade.getStable().getVersionCode() >= upgrade.getBate().getVersionCode()) {
                                if (message.obj instanceof Boolean) {
                                    boolean isAutoCheck = (boolean) message.obj;
                                    if (isAutoCheck) {
                                        if (UpgradeHistorical.isIgnoreVersion(activity, upgrade.getStable().getVersionCode())) {
                                            return;
                                        }
                                    }
                                    upgrade.setBate(null);
                                    UpgradeDialog.newInstance(activity, upgrade, builder
                                            .setUrl(upgrade.getStable().getDowanloadUrl())
                                            .setMd5(upgrade.getStable().getMd5())
                                            .build()).show();
                                } else {
                                    if (message.obj == null) {
                                        return;
                                    }
                                    OnUpgradeListener onUpgradeListener = (OnUpgradeListener) message.obj;
                                    if (upgrade.getStable().getVersionCode() <= Util.getVersionCode(activity)) {
                                        onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_notfound));
                                        return;
                                    }
                                    if (UpgradeHistorical.isIgnoreVersion(activity, upgrade.getStable().getVersionCode())) {
                                        onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_notfound));
                                        return;
                                    }
                                    upgrade.setBate(null);
                                    UpgradeDialog.newInstance(activity, upgrade, builder
                                            .setUrl(upgrade.getStable().getDowanloadUrl())
                                            .setMd5(upgrade.getStable().getMd5())
                                            .build()).show();
                                }
                                return;
                            }

                            if (message.obj instanceof Boolean) {
                                boolean isAutoCheck = (boolean) message.obj;
                                if (isAutoCheck) {
                                    if (UpgradeHistorical.isIgnoreVersion(activity, upgrade.getBate().getVersionCode())) {
                                        return;
                                    }
                                }
                                upgrade.setStable(null);
                                UpgradeDialog.newInstance(activity, upgrade, builder
                                        .setUrl(upgrade.getBate().getDowanloadUrl())
                                        .setMd5(upgrade.getBate().getMd5())
                                        .build()).show();
                            } else {
                                if (message.obj == null) {
                                    return;
                                }
                                OnUpgradeListener onUpgradeListener = (OnUpgradeListener) message.obj;
                                if (upgrade.getBate().getVersionCode() <= Util.getVersionCode(activity)) {
                                    onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_notfound));
                                    return;
                                }
                                if (UpgradeHistorical.isIgnoreVersion(activity, upgrade.getBate().getVersionCode())) {
                                    onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_notfound));
                                    return;
                                }
                                upgrade.setStable(null);
                                UpgradeDialog.newInstance(activity, upgrade, builder
                                        .setUrl(upgrade.getBate().getDowanloadUrl())
                                        .setMd5(upgrade.getBate().getMd5())
                                        .build()).show();
                            }
                            return;
                        }

                        if (upgrade.getBate() != null) {
                            if (message.obj instanceof Boolean) {
                                boolean isAutoCheck = (boolean) message.obj;
                                if (isAutoCheck) {
                                    if (UpgradeHistorical.isIgnoreVersion(activity, upgrade.getBate().getVersionCode())) {
                                        return;
                                    }
                                }
                                UpgradeDialog.newInstance(activity, upgrade, builder
                                        .setUrl(upgrade.getBate().getDowanloadUrl())
                                        .setMd5(upgrade.getBate().getMd5())
                                        .build()).show();
                            } else {
                                if (message.obj == null) {
                                    return;
                                }
                                OnUpgradeListener onUpgradeListener = (OnUpgradeListener) message.obj;
                                if (upgrade.getBate().getVersionCode() <= Util.getVersionCode(activity)) {
                                    onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_notfound));
                                    return;
                                }
                                if (!upgrade.getBate().getDevice().contains(Util.getSerial())) {
                                    onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_notfound));
                                    return;
                                }
                                if (UpgradeHistorical.isIgnoreVersion(activity, upgrade.getBate().getVersionCode())) {
                                    onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_notfound));
                                    return;
                                }
                                onUpgradeListener.onUpdateAvailable(upgrade.getBate(), new UpgradeServiceManager(activity, builder
                                        .setUrl(upgrade.getBate().getDowanloadUrl())
                                        .setMd5(upgrade.getBate().getMd5())
                                        .build()));
                            }
                            return;
                        }
                        if (upgrade.getStable() != null) {
                            if (message.obj instanceof Boolean) {
                                boolean isAutoCheck = (boolean) message.obj;
                                if (isAutoCheck) {
                                    if (UpgradeHistorical.isIgnoreVersion(activity, upgrade.getStable().getVersionCode())) {
                                        return;
                                    }
                                }
                                UpgradeDialog.newInstance(activity, upgrade, builder
                                        .setUrl(upgrade.getStable().getDowanloadUrl())
                                        .setMd5(upgrade.getStable().getMd5())
                                        .build()).show();
                            } else {
                                if (message.obj == null) {
                                    return;
                                }
                                OnUpgradeListener onUpgradeListener = (OnUpgradeListener) message.obj;
                                if (upgrade.getStable().getVersionCode() <= Util.getVersionCode(activity)) {
                                    onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_notfound));
                                    return;
                                }
                                if (UpgradeHistorical.isIgnoreVersion(activity, upgrade.getStable().getVersionCode())) {
                                    onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_notfound));
                                    return;
                                }
                                onUpgradeListener.onUpdateAvailable(upgrade.getStable(), new UpgradeServiceManager(activity, builder
                                        .setUrl(upgrade.getStable().getDowanloadUrl())
                                        .setMd5(upgrade.getStable().getMd5())
                                        .build()));
                            }
                            return;
                        }
                    }
                    break;
                case RESULT_CODE_FALSE:
                    if (message.obj instanceof Boolean) {
                        boolean isAutoCheck = (boolean) message.obj;
                        if (!isAutoCheck) {
                            Toast.makeText(activity, activity.getString(R.string.check_for_update_failure), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        OnUpgradeListener onUpgradeListener = (OnUpgradeListener) message.obj;
                        if (onUpgradeListener != null) {
                            onUpgradeListener.onNoUpdateAvailable(activity.getString(R.string.check_for_update_failure));
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

}