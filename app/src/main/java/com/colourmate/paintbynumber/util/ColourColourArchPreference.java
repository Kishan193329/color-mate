package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;

public class ColourColourArchPreference {
    public static String cat_kids = "kids";

    public static Integer gettotalkids(Context context) {
        return context.getSharedPreferences(cat_kids, 0).getInt("totalkid", 0);
    }

    public static Integer gettotallove(Context context) {
        return context.getSharedPreferences(cat_kids, 0).getInt("totallove", 0);
    }

    public static void setTotalanimal(Context context, Integer num) {
        SharedPreferences.Editor edit = context.getSharedPreferences(cat_kids, 0).edit();
        edit.putInt("totalanimal", num);
        edit.apply();
    }

    public static void setTotalflower(Context context, Integer num) {
        SharedPreferences.Editor edit = context.getSharedPreferences(cat_kids, 0).edit();
        edit.putInt("totalflower", num);
        edit.commit();
    }

    public static void setTotalkids(Context context, Integer num) {
        SharedPreferences.Editor edit = context.getSharedPreferences(cat_kids, 0).edit();
        edit.putInt("totalkid", num);
        edit.commit();
    }

    public static void setTotallove(Context context, Integer num) {
        SharedPreferences.Editor edit = context.getSharedPreferences(cat_kids, 0).edit();
        edit.putInt("totallove", num);
        edit.commit();
    }

    public static void setTotalmandala(Context context, Integer num) {
        SharedPreferences.Editor edit = context.getSharedPreferences(cat_kids, 0).edit();
        edit.putInt("totalmandala", num);
        edit.commit();
    }

    public static void setTotalnature(Context context, Integer num) {
        SharedPreferences.Editor edit = context.getSharedPreferences(cat_kids, 0).edit();
        edit.putInt("totalnature", num);
        edit.commit();
    }

    public static void setTotalother(Context context, Integer num) {
        SharedPreferences.Editor edit = context.getSharedPreferences(cat_kids, 0).edit();
        edit.putInt("totalother", num);
        edit.commit();
    }

    public static void setTotalpeople(Context context, Integer num) {
        SharedPreferences.Editor edit = context.getSharedPreferences(cat_kids, 0).edit();
        edit.putInt("totalpeople", num);
        edit.commit();
    }
}
