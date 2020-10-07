/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.uioverrides;

import android.app.Activity;
import android.content.Context;
import android.util.Base64;

import com.android.launcher3.Utilities;
import com.android.quickstep.RecentsModel;
import com.android.systemui.shared.system.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.zip.Deflater;

public class UiFactory extends RecentsUiFactory {

    public static void onEnterAnimationComplete(Context context) {
        // After the transition to home, enable the high-res thumbnail loader if it wasn't enabled
        // as a part of quickstep, so that high-res thumbnails can load the next time we enter
        // overview
        RecentsModel.INSTANCE.get(context).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    public static void onTrimMemory(Context context, int level) {
        RecentsModel model = RecentsModel.INSTANCE.get(context);
        if (model != null) {
            model.onTrimMemory(level);
        }
    }

    public static boolean dumpActivity(Activity activity, PrintWriter writer) {
        if (!Utilities.IS_DEBUG_DEVICE) {
            return false;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!(new ActivityCompat(activity).encodeViewHierarchy(out))) {
            return false;
        }

        Deflater deflater = new Deflater();
        deflater.setInput(out.toByteArray());
        deflater.finish();

        out.reset();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer); // returns the generated code... index
            out.write(buffer, 0, count);
        }

        writer.println("--encoded-view-dump-v0--");
        writer.println(Base64.encodeToString(
                out.toByteArray(), Base64.NO_WRAP | Base64.NO_PADDING));
        return true;
    }
}
