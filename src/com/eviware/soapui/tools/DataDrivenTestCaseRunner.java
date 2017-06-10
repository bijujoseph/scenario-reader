package com.eviware.soapui.tools;

import com.eviware.soapui.report.JUnitSecurityReportCollector;
import com.eviware.soapui.reports.DataDrivenJUnitReportCollector;

public class DataDrivenTestCaseRunner extends SoapUITestCaseRunner {
    @Override
    protected JUnitSecurityReportCollector createJUnitSecurityReportCollector() {
        return new DataDrivenJUnitReportCollector();
    }


    public static void main(String[] args) throws Exception {
//        String[] myargs = new String[]{
//                "-sqs_smoketest_prototype",
//                "-cquality_scenario_testcase" ,
//                "-r",
//                "-j",
//                "-ehttp://localhost:3000",
//                "-fc:/workspace/soapui/out/soap-reports",
//                "c:/workspace/soapui/SmoketestProject-soapui-project.xml"
//        };

        System.exit((new DataDrivenTestCaseRunner()).runFromCommandLine(args));
    }

}
