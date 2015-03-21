package test.com.bluesky.cloudmontain; 

import com.bluesky.cloudmontain.database.SubscriberDatabase_old;
import org.junit.Test;
import org.junit.Before; 
import org.junit.After;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.ListIterator;

import static org.junit.Assert.*;

/** 
* SubscriberDatabase Tester. 
* 
* @author <Authors name> 
* @since <pre>Feb 15, 2015</pre> 
* @version 1.0 
*/ 
public class SubscriberDatabaseTest {

@Before
public void before() throws Exception { 
} 

@After
public void after() throws Exception { 
}

    /** sanity test
     *
     *          grp1(900),      grp2(902)
     *  su1(11)   Y                 Y
     *  su2(22)   Y
     *
     * @throws Exception
     */
@Test
public void testSanity() throws Exception {
    SubscriberDatabase_old database = new SubscriberDatabase_old();
    long su1 = 11;
    assertFalse(database.hasSubscriber(su1));
    database.addSubscriber(su1);
    assertTrue(database.hasSubscriber(su1));

    long grp1 = 900;
    assertFalse(database.hasGroup(grp1));
    database.addGroup(grp1);
    assertTrue(database.hasGroup(grp1));

    long su2 = 22;
    long grp2 = 902;

    database.addGroup(grp2);
    database.addSubscriber(su2);

    database.signup(su1, grp1);
    database.signup(su2, grp1);
    database.signup(su1, grp2);

    assertTrue(database.isGroupMember(su1,grp1));
    assertTrue(database.isGroupMember(su2,grp1));
    assertFalse(database.isGroupMember(su2, grp2));

    InetSocketAddress addr1 = new InetSocketAddress("host1", 100);
    InetSocketAddress addr2 = new InetSocketAddress("host2", 200);
    database.online(su1, addr1);

    boolean hasSu1, hasSu2;
    hasSu1 = hasSu2 = false;
    List<SubscriberDatabase_old.OnlineRecord> list = database.getOnlineMembers(grp1);
    for(ListIterator<SubscriberDatabase_old.OnlineRecord> it = list.listIterator(); it.hasNext();){
        SubscriberDatabase_old.OnlineRecord record = it.next();
        if(record.su_id == su1 && record.addr == addr1){
            hasSu1 = true;
        }
        if(record.su_id == su2 && record.addr == addr2){
            hasSu2 = true;
        }
    }

    assertTrue(hasSu1);
    assertFalse(hasSu2);

    list = database.getOnlineMembers(grp2);
    for(ListIterator<SubscriberDatabase_old.OnlineRecord> it = list.listIterator(); it.hasNext();){
        SubscriberDatabase_old.OnlineRecord record = it.next();
        if(record.su_id == su1 && record.addr == addr1){
            hasSu1 = true;
        }
        if(record.su_id == su2 && record.addr == addr2){
            hasSu2 = true;
        }
    }
    assertTrue(hasSu1);
    assertFalse(hasSu2);

    ////////////// test su2 online ////////////////////
    database.online(su2, addr2);
    hasSu1 = hasSu2 = false;
    list = database.getOnlineMembers(grp1);
    for(ListIterator<SubscriberDatabase_old.OnlineRecord> it = list.listIterator(); it.hasNext();){
        SubscriberDatabase_old.OnlineRecord record = it.next();
        if(record.su_id == su1 && record.addr == addr1){
            hasSu1 = true;
        }
        if(record.su_id == su2 && record.addr == addr2){
            hasSu2 = true;
        }
    }
    assertTrue(hasSu1);
    assertTrue(hasSu2);

    hasSu1 = hasSu2 = false;
    list = database.getOnlineMembers(grp2);
    for(ListIterator<SubscriberDatabase_old.OnlineRecord> it = list.listIterator(); it.hasNext();){
        SubscriberDatabase_old.OnlineRecord record = it.next();
        if(record.su_id == su1 && record.addr == addr1){
            hasSu1 = true;
        }
        if(record.su_id == su2 && record.addr == addr2){
            hasSu2 = true;
        }
    }

    assertTrue(hasSu1);
    assertFalse(hasSu2);

    ///////////// test su1 offline /////////////////
    database.offline(su1);

    hasSu1 = hasSu2 = false;
    list = database.getOnlineMembers(grp1);
    for(ListIterator<SubscriberDatabase_old.OnlineRecord> it = list.listIterator(); it.hasNext();){
        SubscriberDatabase_old.OnlineRecord record = it.next();
        if(record.su_id == su1 && record.addr == addr1){
            hasSu1 = true;
        }
        if(record.su_id == su2 && record.addr == addr2){
            hasSu2 = true;
        }
    }

    assertFalse(hasSu1);
    assertTrue(hasSu2);

    hasSu1 = hasSu2 = false;
    list = database.getOnlineMembers(grp2);
    for(ListIterator<SubscriberDatabase_old.OnlineRecord> it = list.listIterator(); it.hasNext();){
        SubscriberDatabase_old.OnlineRecord record = it.next();
        if(record.su_id == su1 && record.addr == addr1){
            hasSu1 = true;
        }
        if(record.su_id == su2 && record.addr == addr2){
            hasSu2 = true;
        }
    }

    assertFalse(hasSu1);
    assertFalse(hasSu2);
}


} 
