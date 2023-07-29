package org.lzh.proxy.server.ssh;

import java.util.concurrent.ConcurrentHashMap;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SSHClient {
    private static JSch jSch = new JSch();
    private static Integer defaultPort = 22;
    private static ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();

    public Boolean isExistSession(String host, Integer port) {
        String hostPort = host + ":" + port;
        if (sessionMap.containsKey(hostPort)) {
            return true;
        }
        return false;
    }

    public Session getSession(String host, Integer port) {
        String hostPort = host + ":" + port;
        return sessionMap.get(hostPort);
    }

    public static Session connectSession(String host, Integer port, String username, String password) {
        try{
            if(port == null || port == 0){
                port = defaultPort;
            }
            Session session = null;
            String hostPort = host + ":" + port;
            if(sessionMap.containsKey(hostPort)){
                session = sessionMap.get(hostPort);
                if(session.isConnected()){
                    return session;
                }
            }
            session = jSch.getSession(username, host, port);
            session.setPassword(password);
            // 关闭确认提示
            session.setConfig("StrictHostKeyChecking", "no"); 
            session.connect(10000);
            
            sessionMap.put(hostPort, session);
            return session;
        }catch(Exception exception){
            exception.printStackTrace();
        }
        return null;

    }

    public static Boolean forwardLocal(Session session, Integer localPort,String forwardHost,Integer forwardPort){
        try {
            if(session != null && session.isConnected()){
                session.setPortForwardingL(localPort, forwardHost, forwardPort);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
