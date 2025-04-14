import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

public class ProcessMonitor {
    static final List<String> keywords = Arrays.asList(
            "chrome", "firefox", "edge", "brave",
            "tabnine", "chatgpt",
            "idea", "code", "sublime",
            "telegram", "skype", "discord",
            "utweb", "idm",
            "game", "steam", "epic"
    );
    static final double MAX_RUNTIME_MINUTES = 30.0;
    static final String LOG_FILE = "process_monitor_powershell.log";
    static final Set<String> loggedSet = new HashSet<>();

    static class ProcInfo {
        String name;
        String pid;
        double cpuMinutes;

        ProcInfo(String name, String pid, String cpuRaw) {
            this.name = name;
            this.pid = pid;
            this.cpuMinutes = parseCpu(cpuRaw);
        }

        String logLine() {
            return String.format("Process: %-20s | PID: %-6s | CPU Minutes: %.2f", name, pid, cpuMinutes);
        }

        boolean exceedsLimit() {
            return cpuMinutes > MAX_RUNTIME_MINUTES;
        }

        static double parseCpu(String cpu) {
            try {
                if (cpu == null || cpu.trim().isEmpty()) return 0.0;
                cpu = cpu.replace(",", "."); // для десятичного разделителя
                return Double.parseDouble(cpu.trim());
            } catch (Exception e) {
                return 0.0;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        List<ProcInfo> matchingProcesses = new ArrayList<>();

        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe", "-Command", "Get-Process | Select-Object Name,Id,CPU"
        );
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine || line.trim().isEmpty() || !line.contains(" ")) {
                    isFirstLine = false;
                    continue;
                }

                // Пример строки: chrome    8936     72,890625
                String[] parts = line.trim().split("\\s{2,}");
                if (parts.length < 3) continue;

                String name = parts[0].trim().toLowerCase();
                String pid = parts[1].trim();
                String cpu = parts[2].trim();

                boolean matches = keywords.stream().anyMatch(name::contains);
                if (matches) {
                    ProcInfo proc = new ProcInfo(name, pid, cpu);
                    if (proc.cpuMinutes > 0) {
                        matchingProcesses.add(proc);
                    }
                }
            }
        }

        matchingProcesses.sort((a, b) -> Double.compare(b.cpuMinutes, a.cpuMinutes));

        try (FileWriter logWriter = new FileWriter(LOG_FILE, true)) {
            logWriter.write("\n===== " + LocalDateTime.now() + " =====\n");

            for (ProcInfo proc : matchingProcesses) {
                String key = proc.name + "_" + proc.pid;
                if (loggedSet.contains(key)) continue;
                loggedSet.add(key);

                String log = proc.logLine();
                System.out.println(log);
                logWriter.write(log);

                if (proc.exceedsLimit()) {
                    killProcess(proc.pid);
                    logWriter.write(" => TERMINATED\n");
                    System.out.println(" => TERMINATED");
                } else {
                    logWriter.write(" => OK\n");
                }
            }

            logWriter.write("====================================\n");
        }
    }

    static void killProcess(String pid) throws IOException {
        Runtime.getRuntime().exec("taskkill /PID " + pid + " /F");
    }
}
