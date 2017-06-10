package com.eviware.soapui.report;

import com.eviware.soapui.junit.Testcase;

import java.util.HashMap;

/**
 * Created by manju on 5/8/17.
 */
public class DataDrivenJUnitReport extends JUnitReport {
    @Override
    public void setTestSuiteName(String name) {
        super.setTestSuiteName(name);
    }

    @Override
    public Testcase addTestCase(String name, double time, HashMap<String, String> testProperties) {
        return super.addTestCase(testProperties.get("scenario_id"), time, testProperties);
    }

    @Override
    public Testcase addTestCaseWithFailure(String name, double time, String failure, String stacktrace, HashMap<String, String> testProperties) {
        return super.addTestCaseWithFailure(testProperties.get("scenario_id"), time, failure, stacktrace, testProperties);
    }

    @Override
    public Testcase addTestCaseWithError(String name, double time, String error, String stacktrace, HashMap<String, String> testProperties) {
        return super.addTestCaseWithError(testProperties.get("scenario_id"), time, error, stacktrace, testProperties);
    }
}
