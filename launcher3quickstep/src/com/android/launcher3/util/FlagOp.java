package com.android.launcher3.util;

public interface FlagOp {
    FlagOp NO_OP = i -> i;

    int apply(int flags);
}
