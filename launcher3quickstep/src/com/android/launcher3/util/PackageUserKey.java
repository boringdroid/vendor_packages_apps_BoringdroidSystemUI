package com.android.launcher3.util;

import android.os.UserHandle;

import java.util.Arrays;

/** Creates a hash key based on package name and user. */
public class PackageUserKey {

    public String mPackageName;
    public UserHandle mUser;
    private int mHashCode;

    public PackageUserKey(String packageName, UserHandle user) {
        update(packageName, user);
    }

    public void update(String packageName, UserHandle user) {
        mPackageName = packageName;
        mUser = user;
        mHashCode = Arrays.hashCode(new Object[] {packageName, user});
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PackageUserKey)) return false;
        PackageUserKey otherKey = (PackageUserKey) obj;
        return mPackageName.equals(otherKey.mPackageName) && mUser.equals(otherKey.mUser);
    }
}
