package com.bluesky.common;

import java.util.TimerTask;

/** name timer task via its id
 * Created by liangc on 11/01/15.
 */
public abstract class NamedTimerTask extends TimerTask {
    public NamedTimerTask(int id){
        mId = id;
    }

    public int id(){
        return mId;
    }

    int mId;
}
