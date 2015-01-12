package com.bluesky.cloudmontain;

import java.net.InetSocketAddress;

/**
 * information of a call
 * Created by liangc on 11/01/15.
 */
public class CallInformation {
    InetSocketAddress   mSenderIpPort;

    public short mSequence;
    public long mTargetId;
    public long mSuid;
    public short       mAudioSeq;
}
