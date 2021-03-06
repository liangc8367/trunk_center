package com.bluesky.cloudmontain.database;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * Created by liangc on 08/02/15.
 */
public class SubscriberDatabase_old {
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

    public SubscriberDatabase_old(){

    }

    public boolean hasSubscriber(long su_id){
        return mSubscribers.containsKey(new Long(su_id));
    }

    public boolean hasGroup(long grp_id){
        return mGroups.containsKey(new Long(grp_id));
    }

    public boolean isGroupMember(long su_id, long grp_id){
        Subscriber su = mSubscribers.get(new Long(su_id));
        if(su == null){
            return false;
        }

        return su.groups.contains(new Long(grp_id));
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

    // online methods

    private final HashMap<Long, InetSocketAddress> mOnlineSubs = new HashMap<Long, InetSocketAddress>();

    public void online(long su_id, InetSocketAddress addr){
        mOnlineSubs.put(new Long(su_id), addr);
    }

    public void offline(long su_id){
        mOnlineSubs.remove(new Long(su_id));
    }

    public static class OnlineRecord{
        public OnlineRecord(Long su_id, InetSocketAddress addr){
            this.su_id = su_id;
            this.addr = addr;
        }
        public Long su_id;
        public InetSocketAddress addr;
    }

    public List<OnlineRecord> getOnlineMembers(long grp_id){
        List<OnlineRecord> onlineMembers = new LinkedList<OnlineRecord>();
        Iterator<Long> iter = mGroups.get(new Long(grp_id)).subs.iterator();
        while(iter.hasNext()){
            Long suid = iter.next();
            InetSocketAddress addr = mOnlineSubs.get(suid);
            if(addr != null) {
                OnlineRecord record = new OnlineRecord(suid, addr);
                onlineMembers.add(record);
            }
        }
        return onlineMembers;
    }
}
