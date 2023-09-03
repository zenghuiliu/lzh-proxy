package org.lzh.proxy.server.ssh;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lzh.proxy.Main;
import org.lzh.proxy.config.GlobalConfig;
import org.lzh.proxy.config.GlobalConfig.SSHInfo;
import org.lzh.proxy.config.GlobalConfig.ServerInfo;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.cron.timingwheel.TimerTask;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SSHClient {
    private static JSch jSch = new JSch();
    private static Integer defaultPort = 22;
    private static ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();
    private static Map<String, List<ServerInfo>> forwardMap = new ConcurrentHashMap<>();

    public void init() {
        List<SSHInfo> sshInfos = GlobalConfig.getInstance().getSshInfos();
        sshInfos.forEach(sshInfo -> {
            connectSession(sshInfo);
        });
    }

    public static Boolean addSession(String id, Session session) {
        removeSession(id);
        sessionMap.put(id, session);
        return true;
    }

    public static Boolean removeSession(String id) {
        Session session = sessionMap.get(id);
        if (session != null) {
            JschUserInfo userInfo = (JschUserInfo) session.getUserInfo();
            userInfo.setKeepAliveFlag(false);
            session.disconnect();
            sessionMap.remove(id);
        }
        return true;
    }

    public static Boolean addForward(ServerInfo serverInfo) {
        removeForward(serverInfo);
        if (serverInfo == null || CharSequenceUtil.isBlank(serverInfo.getSshId())) {
            log.warn("addForward parameters is invalid");
            return false;
        }
        try {
            Session session = getSession(serverInfo.getSshId());
            if (session == null) {
                log.warn("not found session[{}]！", serverInfo.getSshId());
                return false;
            }
            forwardLocal(session, serverInfo.getPort(), serverInfo.getForwardIp(),
                    serverInfo.getForwardPort());
            List<ServerInfo> serverInfos = forwardMap.get(serverInfo.getSshId());
            if (serverInfos == null) {
                serverInfos = new ArrayList<>();
                forwardMap.put(serverInfo.getSshId(), serverInfos);
            }
            serverInfos.add(serverInfo);
        } catch (Exception e) {
            log.error("session[{}] forward[ip:{};port:{}] fail.", serverInfo.getSshId(), serverInfo.getForwardIp(),
                    serverInfo.getForwardPort(), e);
        }
        return true;
    }

    public static Boolean removeForward(ServerInfo serverInfo) {
        Session session = sessionMap.get(serverInfo.getSshId());
        if (session != null) {
            if (session.isConnected()) {
                try {
                    String[] pfls = session.getPortForwardingL();
                    if (pfls != null && pfls.length > 0) {
                        for (String pfl : pfls) {
                            String[] temp = pfl.split(":");
                            if (temp[0].equals(serverInfo.getPort().toString())) {
                                session.delPortForwardingL(serverInfo.getPort());
                            }
                        }
                    }
                } catch (JSchException e) {
                    log.info("delete forward[ip:{};port:{}] exception", serverInfo.getForwardIp(),
                            serverInfo.getForwardPort(), e);
                }
            }
        }
        List<ServerInfo> serverInfos = forwardMap.get(serverInfo.getSshId());
        if (serverInfos != null) {
            Iterator<ServerInfo> iterator = serverInfos.iterator();
            while (iterator.hasNext()) {
                ServerInfo info = iterator.next();
                if (serverInfo.getSshId().equals(info.getSshId())
                        && serverInfo.getForwardIp().equals(info.getForwardIp())
                        && serverInfo.getForwardPort().equals(info.getForwardPort())) {
                    log.info("remove server info!");
                    iterator.remove();
                }
            }
            // System.out.println(serverInfos.size());;
        }
        return true;
    }

    public Boolean isExistSession(String id) {
        if (CharSequenceUtil.isBlank(id)) {
            return false;
        }
        if (sessionMap.containsKey(id)) {
            return true;
        }
        return false;
    }

    public static Session getSession(String id) {
        Session session = sessionMap.get(id);
        if (session != null) {
            if (session.isConnected()) {
                return session;
            } else {
                removeSession(id);
            }
        }
        return null;
    }

    public static Session connectSession(SSHInfo sshInfo) {
        try {
            if (sshInfo.getPort() == null || sshInfo.getPort() == 0) {
                sshInfo.setPort(defaultPort);
            }
            Session session = null;
            if (sessionMap.containsKey(sshInfo.getId())) {
                session = sessionMap.get(sshInfo.getId());
                if (session.isConnected()) {
                    return session;
                }
            }

            session = jSch.getSession(sshInfo.getUsername(), sshInfo.getIp(), sshInfo.getPort());
            session.setPassword(sshInfo.getPassword());
            // 关闭确认提示
            session.setConfig("StrictHostKeyChecking", "no");

            JschUserInfo jschUserInfo = new JschUserInfo();
            jschUserInfo.setKeepAliveFlag(true);
            jschUserInfo.setSshInfo(sshInfo);
            session.setUserInfo(jschUserInfo);

            session.connect(10000);

            addSession(sshInfo.getId(), session);

            sessionKeepAlive(session);
            return session;
        } catch (Exception exception) {
            log.error("ssh连接失败:id[{}];ip[{}];port:{}", sshInfo.getId(), sshInfo.getIp(), sshInfo.getPort(), exception);
        }
        return null;

    }

    private static void sessionKeepAlive(Session session) {
        if (session != null) {
            JschUserInfo userInfo = (JschUserInfo) session.getUserInfo();
            synchronized (userInfo.getSshInfo().getId().intern()) {
                // log.debug("session[{}] send keep alive", userInfo.getSshInfo().getId());
                if (session.isConnected()) {
                    try {
                        session.sendKeepAliveMsg();
                        if (userInfo.getKeepAliveFlag()) {
                            Main.SYSTEM_TIMER.addTask(new TimerTask(() -> {
                                sessionKeepAlive(session);
                            }, 1000));
                        }
                    } catch (Exception e) {
                        log.error("session[{}] keep alive fail !", userInfo.getSshInfo().getId(), e);
                    }
                } else {
                    reInitSession(session);
                    log.debug("session[{}] is reconnected", userInfo.getSshInfo().getId());
                }
            }
        }
    }

    public static void reInitSession(Session session) {
        JschUserInfo userInfo = (JschUserInfo) session.getUserInfo();
        connectSession(userInfo.getSshInfo());
        List<ServerInfo> serverInfos = forwardMap.get(userInfo.getSshInfo().getId());
        if (serverInfos != null) {
            Iterator<ServerInfo> infos = serverInfos.iterator();
            while (infos.hasNext()) {
                ServerInfo info = infos.next();
                addForward(info);
            }
            ;
            // System.out.println(serverInfos.size());
        }
    }

    public static Boolean forwardLocal(Session session, Integer localPort, String forwardHost, Integer forwardPort) {
        try {
            if (session != null && session.isConnected()) {
                session.setPortForwardingL(localPort, forwardHost, forwardPort);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void disconnect() {
        sessionMap.forEach((key, session) -> {
            session.disconnect();
        });
    }

}
