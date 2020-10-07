package com.android.quickstep;

import android.os.UserHandle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import android.os.Process;

public class UserHandleHelper {
    public static int getIdentifier(UserHandle userHandle) {
        try {
            Method method = UserHandle.class.getDeclaredMethod("getIdentifier");
            return (int) method.invoke(userHandle);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static UserHandle of(int userId) {
        try {
            Method method = UserHandle.class.getDeclaredMethod("of", int.class);
            method.invoke(null, userId);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return Process.myUserHandle();
    }

    public static int myUserId() {
        try {
            Method method = UserHandle.class.getDeclaredMethod("myUserId");
            return (int) method.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
