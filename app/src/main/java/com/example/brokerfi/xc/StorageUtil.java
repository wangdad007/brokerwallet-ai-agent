package com.example.brokerfi.xc;

import android.content.Context;
import android.content.SharedPreferences;

public class StorageUtil {
    private static final String PREFS_NAME = "MyPrefsFile";
    private static final String PREF_ACCOUNT = "accountkey";
    private static final String Curacc = "curacc";
    private static final String PREFS_NAME2 = "MyPrefsFile2";
    private static final String NoticeIdName = "NoticeIdName";
    private static final String NoticeId = "NoticeId";

    public static void savePrivateKey(Context context, String acc){
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREF_ACCOUNT, acc);
        editor.apply();
    }
    public static void saveNoticeId(Context context, String id){
        SharedPreferences settings = context.getSharedPreferences(NoticeIdName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(NoticeId, id);
        editor.apply();
    }
    public static String getNoticeId(Context context){
        SharedPreferences settings =context.getSharedPreferences(NoticeIdName, Context.MODE_PRIVATE);
        return settings.getString(NoticeId, null);
    }


    public static String getPrivateKey(Context context) {
        SharedPreferences settings =context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return settings.getString(PREF_ACCOUNT, null);
    }

    public static String getCurrentAccount(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME2, Context.MODE_PRIVATE);
        return settings.getString(Curacc, null);
    }

    public static void saveCurrentAccount(Context context, String s) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME2, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(Curacc, s);
        editor.apply();
    }

    public static String getCurrentPrivatekey(Context context){
        String account = StorageUtil.getPrivateKey(context);
        String acc = StorageUtil.getCurrentAccount(context);
        int i;
        if (acc == null){
            i = 0;
        } else {
            try {
                i = Integer.parseInt(acc);
            } catch (NumberFormatException e) {
                i = 0;
            }
        }
        if (account != null) {
            String[] split = account.split(";");
            // 边界检查：防止数组越界崩溃
            if (i < 0 || i >= split.length) {
                i = 0;
                saveCurrentAccount(context, "0");
            }
            if (split.length > 0) {
                return split[i];
            }
        }
        return null;
    }
}
