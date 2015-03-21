package com.bluesky.cloudmontain.main;

import com.bluesky.cloudmontain.repeator.TrunkManager;
import com.bluesky.common.SubscriberDatabase;
import com.bluesky.common.SubscriberDatabaseHelper;

import java.lang.Thread;

/**
 * Trunk Center, the repeator for PTTApp, responsible for
 *  - registration
 *  - call management
 *      + call validation
 *      + call setup
 *      + call maintainance
 *      + call teardown
 *
 *  Author: Liang C
 *  Revision:
 *      - 2014-12-31: initial version
 */

public class Main {

    public static void main(String[] args) {
        if( args.length != 1){
            System.err.println("trunk-mgr [config.json]");
            System.exit(-1);
        }

        System.out.println("start integration test... " + args[0]);
        SubscriberDatabase database = SubscriberDatabaseHelper.createDatabaseFromJson(args[0]);
        if(database == null){
            System.err.println("invalid database");
            System.exit(-1);
        }

        System.out.println("Trunking Control Manager started.");
        TrunkManager trunkManager = new TrunkManager(database);
        trunkManager.start();
        while(true){
            try {
                Thread.sleep(1000);
            }catch (Exception e){
                System.out.println("exception: " + e);
            }
        }
    }
}
