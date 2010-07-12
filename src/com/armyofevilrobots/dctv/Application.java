package com.armyofevilrobots.dctv;

import java.util.*;

import static org.red5.server.api.ScopeUtils.getScopeService;
import static org.red5.server.api.ScopeUtils.isApp;
import static org.red5.server.api.ScopeUtils.isRoom;
import org.red5.server.adapter.ApplicationAdapter;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IConnection;
import org.red5.server.api.IScope;
import org.red5.server.api.stream.IServerStream;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.IClient;
import org.red5.server.stream.ClientBroadcastStream;
import org.red5.logging.Red5LoggerFactory;
//import org.red5.server.api.stream.support.SimpleBandwidthConfigure;
import com.armyofevilrobots.dctv.AuthAdaptor;

public class Application extends MultiThreadedApplicationAdapter {

    private IScope appScope;

    private IServerStream serverStream;

    private AuthAdaptor authAdaptor;

    /** {@inheritDoc} */
    @Override
        public boolean appStart(IScope app) {
            super.appStart(app);
            log.warn("***DCTV*** APPSTART!");
            appScope = app;
            authAdaptor = new AuthAdaptor("com.mysql.jdbc.Driver", "jdbc:mysql://127.0.0.1/dctv_auth", "root", "password");
            setClientTTL(30);

            return true;
        }

    public boolean verifyAuthDCTV(IConnection conn, Object[] params){
        if (params.length < 1){
            log.warn("There were only {} parameters passed.", params.length);
            return false;
        }
        boolean auth = authAdaptor.checkToken((String)params[0], "broadcast");
        log.warn("Auth result is {}", auth);
        if (auth){
            log.warn("Auth succeed!");
            return true;
        }else{
            log.warn("Auth failed. Bad Token? '{}'", params[0]);
        }

        return false;
    }

    @Override
        public boolean connect(IConnection conn, IScope scope, Object[] params) {
            //ensure the log is not null at this point
            if (log == null) {
                log = Red5LoggerFactory.getLogger(this.getClass());
            }

            if(!conn.hasAttribute("dctv_broadcast_auth"))conn.setAttribute("dctv_broadcast_auth", false);
            
            boolean auxauth=false;
            String auxToken = conn.getScope().getName();
            log.warn("Aux token is '{}'", auxToken);
            auxauth = authAdaptor.checkToken(auxToken, "broadcast")||verifyAuthDCTV(conn, params);
            log.warn("Aux auth is {}",auxauth);

            
            if (auxauth){
                conn.setAttribute("dctv_broadcast_auth", true);
                //We terminate the previous serverStream too...
            }


            //hit the super class first
            //Also, connect to higher level scope if this is a subroom.
            if(isApp(scope)){
                log.warn("Connecting to app scope {}",scope);
                if (!super.connect(conn, scope, params)) {
                    return false;
                }
            }
            else if(isRoom(scope)){
                //Over-ride the current scope then.
                if(scope.getName()!="dctv"){
                    scope = scope.getParent();
                }
                log.warn("Connecting to room scope parent {}",scope.getParent());
                if (!super.connect(conn, scope, params)) {
                    return false;
                }

            }
            if (log.isInfoEnabled()) {
                // log w3c connect event
                IClient client = conn.getClient();
                if (client == null) {
                    log.info("W3C x-category:session x-event:connect c-ip:{}", conn.getRemoteAddress());
                } else {
                    log.info("W3C x-category:session x-event:connect c-ip:{} c-client-id:{}", conn.getRemoteAddress(),
                            client.getId());
                }
            }
            boolean success = false;
            if (isApp(scope)) {
                log.warn("Is an app connect");
                success = appConnect(conn, params);
            } else if (isRoom(scope)) {
                log.warn("Is a room connect");
                success = roomConnect(conn, params);
            }
            return success;
        }


    /** {@inheritDoc} */
    @Override
        public void streamPublishStart(IBroadcastStream stream){
            ClientBroadcastStream cbs = ((ClientBroadcastStream)stream.getProvider());
            boolean auth=false;
            try{
                auth = cbs.getConnection().getBoolAttribute("dctv_broadcast_auth");
            }catch(Exception e){
                auth = false;
            }


            log.warn("Intercepted the streamPublishStart.");
            log.warn("Scope is {}", stream.getScope());
            log.warn("Provider is {}", stream.getProvider());
            log.warn("Found stream {}", stream);
            log.warn("Provider:provider is {}", ((ClientBroadcastStream)stream.getProvider()).getConnection());
            log.warn("Provider:publishedName is {}", ((ClientBroadcastStream)stream.getProvider()).getPublishedName());
            log.warn("Provider:publishedName is {}", ((ClientBroadcastStream)stream.getProvider()).getPublishedName());
            log.warn("BroadcastStreamNames are {}", getBroadcastStreamNames(appScope));
            log.warn("Auth is {}", auth);
            if (!auth){
                log.warn("No auth; rejecting client.");
                cbs.close();
                return;
            }else{
                //Looks like a good auth. Let's kill the old stream now.
                //
                /*

                try{
                    IBroadcastStream oldStream = getBroadcastStream(appScope, ((ClientBroadcastStream)stream.getProvider()).getPublishedName());
                    log.warn("New stream is {} and old stream is {}", stream, oldStream);

                    //Oh look, not C, but still ptr operations. Fucking java.
                    if (oldStream != null && oldStream != stream){
                        log.warn("Closing old broadcast stream for {}", ((ClientBroadcastStream)stream.getProvider()).getPublishedName());
                        oldStream.stop();
                        oldStream.close();
                        streamBroadcastClose(oldStream);
                        ((ClientBroadcastStream)oldStream.getProvider()).close();
                        killGhostConnections();
                    }
                }catch(NullPointerException e){
                    log.warn("Couldnt nuke old stream. Continuing with trepidation ;)");
                }
                */
            }
            super.streamPublishStart(stream);
        }

    /** {@inheritDoc} */
    @Override
        public boolean appConnect(IConnection conn, Object[] params) {
            log.warn("**DCTV*** appConnect");
            // Trigger calling of "onBWDone", required for some FLV players
            log.debug("Client is %s",conn.getRemoteAddress());

            log.warn("The total # of params is {}", params.length);
            for (int i=0;i<params.length;i++){
                try{
                    log.warn("Param: "+(String)params[i]);
                }catch(Exception e){
                    log.warn("Couldn't exchange param {}", i);
                }
            }






            log.warn("About to return appConnect");

            return super.appConnect(conn, params);
        }

    /** {@inheritDoc} */
    @Override
        public void appDisconnect(IConnection conn) {
            log.info("***DCTV Disconnect***");
            if (appScope == conn.getScope() && serverStream != null) {
                serverStream.close();
            }
            super.appDisconnect(conn);
        }
}
