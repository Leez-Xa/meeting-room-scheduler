using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

[assembly: AssemblyTitle("会议室预约系统")]
[assembly: AssemblyDescription("本机会议室预约系统一键启动入口")]
[assembly: AssemblyVersion("1.0.0.0")]
[assembly: AssemblyFileVersion("1.0.0.0")]

internal static class MeetingLauncher
{
    [STAThread]
    private static int Main()
    {
        Application.EnableVisualStyles();
        string projectRoot = AppDomain.CurrentDomain.BaseDirectory;
        string script = Path.Combine(projectRoot, "scripts", "project-launcher.ps1");
        if (!File.Exists(script))
        {
            MessageBox.Show("请将此程序放在项目根目录，与 scripts、backend、frontend 文件夹放在一起。\n也可以为它创建快捷方式。",
                "启动位置不正确", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return 1;
        }
        try
        {
            string shell = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),
                "WindowsPowerShell", "v1.0", "powershell.exe");
            var start = new ProcessStartInfo
            {
                FileName = shell,
                Arguments = "-NoProfile -STA -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File \"" + script + "\" -AutoStart",
                WorkingDirectory = projectRoot,
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden
            };
            // Wait only for the controller. Descendant processes may inherit stream handles,
            // so redirecting and draining its console streams can keep this EXE alive on exit.
            using (var process = new Process { StartInfo = start })
            {
                process.Start();
                process.WaitForExit();
                if (process.ExitCode != 0)
                {
                    MessageBox.Show("启动工具未能正常运行，请查看项目 work\\launcher 中的日志。", "启动未完成", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
                return process.ExitCode;
            }
        }
        catch (Exception error)
        {
            MessageBox.Show("无法打开启动工具：\n" + error.Message, "启动未完成", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return 1;
        }
    }
}
