package com.bluesky.cloudmontain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Created by liangc on 08/02/15.
 */
public class SubscriberDatabase {
    private static class Subscriber {
        public long su_id = 0;
        public final HashSet<Long> groups = new HashSet<Long>();
    }

    private static class Group {
        public long grp_id = 0;
        public final HashSet<Long> subs = new HashSet<Long>();
    }

    private final HashMap<Long, Subscriber> mSubscribers = new HashMap<Long, Subscriber>();
    private final HashMap<Long, Group> mGroups = new HashMap<Long, Group>();
    private final HashSet<Long> mOnlineSubs = new HashSet<Long>();

    public SubscriberDatabase(){

    }

    public boolean hasSubscriber(long su_id){
        return mSubscribers.containsKey(new Long(su_id));
    }

    public boolean hasGroup(long grp_id){
        return mGroups.containsKey(new Long(grp_id));
    }

    public boolean isGroupMember(long su_id, long grp_id){
        if( hasSubscriber(su_id)){
            return mSubscribers.get(new Long(grp_id))!=null;
        }
        return false;
    }

    public List<Long> getGroupMember(long grp_id){
        return new ArrayList<Long>(mGroups.get(new Long(grp_id)).subs);
    }

    public void addSubscriber(long su_id){
        if(hasSubscriber(su_id)){
            return;
        }
        Subscriber sub = new Subscriber();
        sub.su_id = su_id;
        mSubscribers.put(new Long(su_id), sub);
    }

    public void addGroup(long grp_id){
        if(hasGroup(grp_id)){
            return;
        }
        Group grp = new Group();
        grp.grp_id = grp_id;
        mGroups.put(new Long(grp_id), grp);
    }

    public void signup(long su_id, long grp_id){
        if(!hasSubscriber(su_id) || !hasGroup(grp_id)){
            return;
        }
        mSubscribers.get(new Long(su_id)).groups.add(new Long(grp_id));
        mGroups.get(new Long(grp_id)).subs.add(new Long(su_id));
    }

    public void online(long su_id){
        if(hasSubscriber(su_id)){
            mOnlineSubs.add(new Long(su_id));
        }
    }

    public void offline(long su_id){
        if(hasSubscriber(su_id)){
            mOnlineSubs.remove(new Long(su_id));
        }
    }
}
