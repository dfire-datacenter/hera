package com.dfire.core.job;

import com.dfire.common.constants.RunningJobKeyConstant;
import com.dfire.config.HeraGlobalEnvironment;
import com.dfire.logs.ErrorLog;
import com.dfire.logs.HeraLog;
import org.apache.commons.lang.ArrayUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: <a href="mailto:lingxiao@2dfire.com">凌霄</a>
 * @time: Created in 上午7:59 2018/5/12
 * @desc
 */
public class HiveJob extends ProcessJob {


    public HiveJob(JobContext jobContext) {
        super(jobContext);
        jobContext.getProperties().setProperty(RunningJobKeyConstant.JOB_RUN_TYPE, "HiveJob");
    }

    @Override
    public int run() throws Exception {
        return runInner();
    }

    private Integer runInner() throws Exception {
        String script = getProperties().getLocalProperty(RunningJobKeyConstant.JOB_SCRIPT);
        File file = new File(jobContext.getWorkDir() + File.separator + System.currentTimeMillis() + ".hive");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                ErrorLog.error("创建.hive失败");
            }
        }

        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file),
                    Charset.forName(jobContext.getProperties().getProperty("hera.fs.encode", "utf-8")));
            writer.write(script.replaceAll("^--.*", "--"));
        } catch (Exception e) {
            if (jobContext.getHeraJobHistory() != null) {
                jobContext.getHeraJobHistory().getLog().appendHeraException(e);
            } else {
                jobContext.getDebugHistory().getLog().appendHeraException(e);
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        getProperties().setProperty(RunningJobKeyConstant.RUN_HIVE_PATH, file.getAbsolutePath());
        return super.run();
    }

    @Override
    public List<String> getCommandList() {
        String hiveFilePath = getProperty(RunningJobKeyConstant.RUN_HIVE_PATH, "");
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        String shellPrefix = "";
        String user = "";
        if (jobContext.getRunType() == 1 || jobContext.getRunType() == 2) {
            user = jobContext.getHeraJobHistory().getOperator();
            shellPrefix = "sudo -u " + user;
        } else if (jobContext.getRunType() == 3) {
            user = jobContext.getDebugHistory().getOwner();
            shellPrefix = "sudo -u " + user;
        } else if (jobContext.getRunType() == 4) {
            shellPrefix = "";
        } else {
            HeraLog.info("没有运行类型 runType = " + jobContext.getRunType());
        }

        String[] excludeFile = HeraGlobalEnvironment.excludeFile.split(";");
        boolean isDocToUnix = true;
        if (!ArrayUtils.isEmpty(excludeFile)) {
            String lowCaseShellPath = hiveFilePath.toLowerCase();
            for (String exclude : excludeFile) {
                if (lowCaseShellPath.endsWith("." + exclude)) {
                    isDocToUnix = false;
                    break;
                }
            }
        }

        if (isDocToUnix) {
            list.add("dos2unix " + hiveFilePath);
            log("dos2unix file" + hiveFilePath);
        }

        sb.append(" -f ").append(hiveFilePath);

        if (shellPrefix.trim().length() > 0) {

            String tmpFilePath = jobContext.getWorkDir() + File.separator + "tmp.sh";
            File tmpFile = new File(tmpFilePath);
            OutputStreamWriter tmpWriter = null;
            if (!tmpFile.exists()) {
                try {
                    tmpFile.createNewFile();
                    tmpWriter = new OutputStreamWriter(new FileOutputStream(tmpFile),
                            Charset.forName(jobContext.getProperties().getProperty("hera.fs.encode", "utf-8")));
                    tmpWriter.write("hive " + sb.toString());
                } catch (Exception e) {
                    jobContext.getHeraJobHistory().getLog().appendHeraException(e);
                } finally {
                    if (tmpWriter != null) {
                        try {
                            tmpWriter.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                list.add("chmod -R 777 " + jobContext.getWorkDir());
                list.add(shellPrefix + " sh " + tmpFilePath);
            } else {
                list.add("chmod -R 777 " + jobContext.getWorkDir());
                list.add(shellPrefix + " hive " + sb.toString());
            }

        } else {
            list.add("hive" + sb.toString());
        }
        return list;
    }
}
