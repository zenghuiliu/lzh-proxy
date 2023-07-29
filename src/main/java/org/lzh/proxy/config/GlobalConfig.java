package org.lzh.proxy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.netty.channel.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Data
public class GlobalConfig {
    private static GlobalConfig globalConfig = null;
    private static ObjectMapper objectMapper = null;

    static {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        objectMapper = new ObjectMapper(yamlFactory);
        // 解决localDate等类型处理
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    private GlobalConfig(){
    }

    public static GlobalConfig init(String filePropertiesName){
        if (!filePropertiesName.startsWith("/")) {
            filePropertiesName = "/" + filePropertiesName;
        }

        Path curDirPath = getFileInCurrentDir(filePropertiesName);
        if (Files.exists(curDirPath)) {
            try (InputStream curFolder = Files.newInputStream(curDirPath)) {
                globalConfig = objectMapper.readValue(curFolder,GlobalConfig.class);
            } catch (Exception e) {
                log.error("load config file error ! {}",curDirPath.toString(),e);
                System.exit(0);
            }
            return globalConfig;
        }
        try (InputStream classPath = GlobalConfig.class.getResourceAsStream(filePropertiesName)) {
            if (classPath != null) {
                globalConfig = objectMapper.readValue(classPath,GlobalConfig.class);
            }
        } catch (Exception e) {
            log.error("load config file error ! class path:{}",filePropertiesName,e);
            System.exit(0);
        }
        if (globalConfig == null){
            log.error("config file not exist!");
            System.exit(0);
        }
        return globalConfig;
    }

    private static Path getFileInCurrentDir(String filename) {
        return Paths.get(System.getProperty("user.dir"), filename);
    }

    public static GlobalConfig getInstance(){
        return globalConfig;
    }

    private Boolean isServer;
    private String registerIp;
    private Integer registerPort;

    private List<ServerInfo> serverInfos;
    private List<ProxyInfo> proxyInfos;



    @Data
    public static class ServerInfo {
        private String type;
        private String ip;
        private Integer port;
        private SSHInfo ssh;
    }
    @Data
    public static class ProxyInfo {
        private String ip;
        private Integer port;
        private Integer remotePort;
        private Channel channel = null;
        private Channel register = null;
        private Integer status;
    }

    @Data
    public static class SSHInfo {
        private String ip;
        private Integer port;
        private String username;
        private String password;
        private String forwardIp;
        private Integer forwardPort;
    }

}

