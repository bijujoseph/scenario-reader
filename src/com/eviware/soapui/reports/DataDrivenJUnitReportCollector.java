package com.eviware.soapui.reports;

import com.eviware.soapui.model.testsuite.*;
import com.eviware.soapui.report.DataDrivenJUnitReport;
import com.eviware.soapui.report.JUnitReport;
import com.eviware.soapui.report.JUnitSecurityReportCollector;
import org.apache.commons.lang.StringUtils;
import com.eviware.soapui.support.xml.XmlUtils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class DataDrivenJUnitReportCollector extends JUnitSecurityReportCollector {




    protected boolean includeTestPropertiesInReport = false;
    protected String[] specialProps = new String[0];
    private int maxErrors = 0;


    private String TEST_SCENARIO_KEY = "scenario_id";

    public DataDrivenJUnitReportCollector() {

        String key = System.getenv().get("scenario_id");
        if (StringUtils.isNotEmpty(key)) {
            TEST_SCENARIO_KEY = key;
        }
    }

    public DataDrivenJUnitReportCollector(int maxErrors) {
        this.maxErrors = maxErrors;
    }




    @Override
    public List<String> saveReports(String path) throws Exception {

        File file = new File(path);
        if (!file.exists() || !file.isDirectory()) {
            file.mkdirs();
        }

        List<String> result = new ArrayList<String>();

        Iterator<String> keyset = getReports().keySet().iterator();
        while (keyset.hasNext()) {
            String name = keyset.next();
            JUnitReport report = getReports().get(name);
            String fileName = path + File.separatorChar + "TEST-" + StringUtils.replace(name, " " , "_") + ".xml";
            saveReport(report, fileName);
            result.add(fileName);
        }

        return result;
    }

    @Override
    public void saveReport(JUnitReport report, String filename) throws Exception {
        report.save(new File(filename));

    }

    @Override
    public void beforeRun(TestCaseRunner testRunner, TestCaseRunContext runContext) {
        TestCase testCase = testRunner.getTestCase();
        TestSuite testSuite = testCase.getTestSuite();
        if (!getReports().containsKey(testSuite.getName())) {
            JUnitReport report = new JUnitReport();
            report.setIncludeTestProperties(this.includeTestPropertiesInReport);
            report.setTestSuiteName(testSuite.getProject().getName() + "." + testSuite.getName());
            getReports().put(testSuite.getName(), report);
        }
    }

    @Override
    public void afterRun(TestCaseRunner testRunner, TestCaseRunContext runContext) {

    }

    @Override
    public void afterStep(TestCaseRunner testRunner, TestCaseRunContext runContext, TestStepResult result) {

        TestStep currentStep = result.getTestStep();
        TestCase testCase = currentStep.getTestCase();

        JUnitReport report = getReports().get(testCase.getTestSuite().getName());
        String stepName = result.getTestStep().getName();
        String scenario = testCase.getPropertyValue(TEST_SCENARIO_KEY);
        long timeTaken = result.getTimeTaken();
        String testCaseName = scenario + "-" + stepName;
        HashMap<String, String> testProperties = getTestPropertiesAsHashMap(testCase);

        if ( result.getStatus() == TestStepResult.TestStepStatus.FAILED) {
            StringBuilder buf = new StringBuilder();
            String stackTrace = "";
            buf.append("<h3><b>").append(XmlUtils.entitize(result.getTestStep().getName()))
                    .append(" Failed</b></h3><pre>");
            for (String message : result.getMessages()) {
                if (message.toLowerCase().startsWith("url:")) {
                    String url = XmlUtils.entitize(message.substring(4).trim());
                    buf.append("URL: <a target=\"new\" href=\"").append(url).append("\">").append(url)
                            .append("</a>");
                } else {
                    buf.append(message);
                }

                buf.append("\r\n");
            }


            // use string value since constant is defined in pro.. duh..
            if (testRunner.getTestCase().getSettings().getBoolean("Complete Error Logs")) {
                StringWriter stringWriter = new StringWriter();
                result.getError().printStackTrace(new PrintWriter(stringWriter));
                stackTrace = XmlUtils.entitize(stringWriter.toString());
                buf.append(stackTrace);
            }

            buf.append("</pre><hr/>");

            report.addTestCaseWithFailure(testCaseName, timeTaken, stackTrace, buf.toString(), testProperties);
        } else if (result.getStatus() == TestStepResult.TestStepStatus.OK ||
                result.getStatus() == TestStepResult.TestStepStatus.UNKNOWN ||
                result.getStatus() == TestStepResult.TestStepStatus.CANCELED) {
            report.addTestCase(testCaseName, timeTaken, testProperties);
        }

    }


    @Override
    public void beforeStep(TestCaseRunner testRunner, TestCaseRunContext runContext) {
    }

    @Override
    public void beforeStep(TestCaseRunner testRunner, TestCaseRunContext runContext, TestStep testStep) {

    }

    @Override
    public void afterRun(TestSuiteRunner testRunner, TestSuiteRunContext runContext) {
    }

    @Override
    public void afterTestCase(TestSuiteRunner testRunner, TestSuiteRunContext runContext, TestCaseRunner testCaseRunner) {
        testCaseRunner.getTestCase().removeTestRunListener(this);
    }

    @Override
    public void beforeRun(TestSuiteRunner testRunner, TestSuiteRunContext runContext) {
    }

    @Override
    public void beforeTestCase(TestSuiteRunner testRunner, TestSuiteRunContext runContext, TestCase testCase) {
        testCase.addTestRunListener(this);
    }

    @Override
    public void afterRun(ProjectRunner testScenarioRunner, ProjectRunContext runContext) {
    }

    @Override
    public void afterTestSuite(ProjectRunner testScenarioRunner, ProjectRunContext runContext, TestSuiteRunner testRunner) {
        testRunner.getTestSuite().removeTestSuiteRunListener(this);
    }

    @Override
    public void beforeRun(ProjectRunner testScenarioRunner, ProjectRunContext runContext) {
    }

    @Override
    public void beforeTestSuite(ProjectRunner testScenarioRunner, ProjectRunContext runContext, TestSuite testSuite) {
        testSuite.addTestSuiteRunListener(this);
    }

}
