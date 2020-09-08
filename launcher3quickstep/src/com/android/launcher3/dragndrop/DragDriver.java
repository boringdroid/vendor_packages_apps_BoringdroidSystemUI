/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import android.content.Context;
import android.view.MotionEvent;

import com.android.launcher3.DropTarget.DragObject;

/**
 * Base class for driving a drag/drop operation.
 */
public abstract class DragDriver {
    protected final EventListener mEventListener;

    public interface EventListener {
        void onDriverDragMove(float x, float y);

        void onDriverDragEnd(float x, float y);
        void onDriverDragCancel();
    }

    public DragDriver(EventListener eventListener) {
        mEventListener = eventListener;
    }

    /**
     * Handles ending of the DragView animation.
     */
    public void onDragViewAnimationEnd() { }

    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                mEventListener.onDriverDragMove(ev.getX(), ev.getY());
                break;
            case MotionEvent.ACTION_UP:
                mEventListener.onDriverDragMove(ev.getX(), ev.getY());
                mEventListener.onDriverDragEnd(ev.getX(), ev.getY());
                break;
            case MotionEvent.ACTION_CANCEL:
                mEventListener.onDriverDragCancel();
                break;
        }

        return true;
    }


    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();

        switch (action) {
            case MotionEvent.ACTION_UP:
                mEventListener.onDriverDragEnd(ev.getX(), ev.getY());
                break;
            case MotionEvent.ACTION_CANCEL:
                mEventListener.onDriverDragCancel();
                break;
        }

        return true;
    }

    public static DragDriver create(Context context, DragController dragController,
            DragObject dragObject, DragOptions options) {
        if (options.systemDndStartPoint != null) {
            return new SystemDragDriver(dragController, context, dragObject);
        } else {
            return new InternalDragDriver(dragController);
        }
    }
}

/**
 * Class for driving a system (i.e. framework) drag/drop operation.
 */
class SystemDragDriver extends DragDriver {

    SystemDragDriver(DragController dragController, Context context, DragObject dragObject) {
        super(dragController);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

}

/**
 * Class for driving an internal (i.e. not using framework) drag/drop operation.
 */
class InternalDragDriver extends DragDriver {
    InternalDragDriver(DragController dragController) {
        super(dragController);
    }

}
