import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SystemOpsLab {

    enum OS { WINDOWS, LINUX, OTHER }

    public static void main(String[] args) {
        try {
            OS os = detectOS();
            String desktop = getDesktopPath(os);
            Path flagPath = Paths.get(desktop, "restart_flag.txt");

            if (Files.exists(flagPath)) {
                // Second run (after restart)
                Files.delete(flagPath);
                log("Shutdown initiated after restart", desktop);
                unscheduleAutostart(os);  // Clean up autostart
                shutdownSystem(os);
            } else {
                // First run (manual execution)
                log("First run: logging and restarting", desktop);
                Files.write(flagPath, "Restart pending".getBytes());
                scheduleAutostart(os);
                restartSystem(os);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static OS detectOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return OS.WINDOWS;
        if (os.contains("nux") || os.contains("nix")) return OS.LINUX;
        return OS.OTHER;
    }

    private static String getDesktopPath(OS os) {
        return Paths.get(System.getProperty("user.home"), "Desktop").toString();
    }

    private static void log(String message, String desktopPath) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Path logFile = Paths.get(desktopPath, "SystemLog_" + timestamp + ".txt");

        String content = String.join("\n",
                "System Operations Log",
                "====================",
                "Timestamp: " + new Date(),
                "Message: " + message,
                "OS: " + System.getProperty("os.name"),
                "User: " + System.getProperty("user.name"),
                "Java Version: " + System.getProperty("java.version")
        );

        Files.write(logFile, content.getBytes());
    }

    private static void scheduleAutostart(OS os) throws IOException {
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        String className = SystemOpsLab.class.getName();

        if (os == OS.WINDOWS) {
            Path startupDir = Paths.get(System.getenv("APPDATA"), "Microsoft", "Windows",
                    "Start Menu", "Programs", "Startup");
            Path batPath = startupDir.resolve("RestartShutdownTask.bat");

            String javaCmd = String.format("\"%s\" -cp \"%s\" %s", javaBin, classpath, className);
            String batContent = "@echo off\r\n" + javaCmd + "\r\n";
            Files.write(batPath, batContent.getBytes());

        } else if (os == OS.LINUX) {
            Path autostartDir = Paths.get(System.getProperty("user.home"), ".config", "autostart");
            Path desktopFile = autostartDir.resolve("restart_shutdown.desktop");

            String desktopContent = String.join("\n",
                    "[Desktop Entry]",
                    "Type=Application",
                    "Name=RestartShutdownTask",
                    "Exec=" + javaBin + " -cp \"" + classpath + "\" " + className,
                    "Terminal=false",
                    "X-GNOME-Autostart-enabled=true"
            );

            Files.createDirectories(autostartDir);
            Files.write(desktopFile, desktopContent.getBytes());
        }
    }

    private static void unscheduleAutostart(OS os) throws IOException {
        if (os == OS.WINDOWS) {
            Path batPath = Paths.get(System.getenv("APPDATA"), "Microsoft", "Windows",
                    "Start Menu", "Programs", "Startup", "RestartShutdownTask.bat");
            Files.deleteIfExists(batPath);
        } else if (os == OS.LINUX) {
            Path desktopFile = Paths.get(System.getProperty("user.home"), ".config", "autostart",
                    "restart_shutdown.desktop");
            Files.deleteIfExists(desktopFile);
        }
    }

    private static void restartSystem(OS os) throws IOException {
        if (os == OS.WINDOWS) {
            Runtime.getRuntime().exec("shutdown /r /t 5 /f /c \"Restarting system...\"");
        } else if (os == OS.LINUX) {
            Runtime.getRuntime().exec(new String[]{"shutdown", "-r", "now"});
        }
    }

    private static void shutdownSystem(OS os) throws IOException {
        if (os == OS.WINDOWS) {
            Runtime.getRuntime().exec("shutdown /s /t 10 /f /c \"Shutting down after restart\"");
        } else if (os == OS.LINUX) {
            Runtime.getRuntime().exec(new String[]{"shutdown", "-h", "now"});
        }
    }
}