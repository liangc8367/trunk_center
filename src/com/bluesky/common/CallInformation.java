package com.bluesky.common;

import java.net.InetSocketAddress;

/**
 * information of a call
 * Created by liangc on 11/01/15.
 */
public class CallInformation {
    public InetSocketAddress   mSenderIpPort;

    public short mSequence;
    public long mTargetId;
    public long mSuid;
    public short       mAudioSeq;
}
