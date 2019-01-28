package com.eviware.soapui;

import org.apache.log4j.Logger
import com.eviware.soapui.impl.wsdl.teststeps.*
import com.eviware.soapui.support.types.StringToStringMap
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase
import java.io.File

/**
 * Responsible for setting endpoint and endpoint authorization for individual test cases.
 */
class TestCaseAuthorization {

    private Map ENV
    private Map TOKEN_MAP

    /**
     * Reads in the .env file. Parses key/value into map.
     *
     * @param projectPath - path to current running SoapUI project
     */
    private Map getEnvironmentValues(String projectPath) {
        if (ENV == null) {
            ENV = [:]
            new File(projectPath + "/.env").eachLine { line ->
                ENV.put(line.split("=")[0], line.split("=")[1])
            }
        }
        return ENV
    }

    /**
     * Reads in the token file from path defined in the .env file.
     * Parses tokens by environment, user, token into map.
     */
    private Map getTokenMap() {
        if (TOKEN_MAP == null) {
            TOKEN_MAP = [:]
            def lineNo = 0
            def line
            new File(ENV.get("TOKEN_FILE_PATH")).withReader { reader ->
                while ((line = reader.readLine()) != null) {
                    if (lineNo > 0) {
                        String[] values = line.split(",")
                        TOKEN_MAP.put(values[0].replaceAll("\"", "") + values[1].replaceAll("\"", ""), values[4])
                    }
                    lineNo++
                }
            }
        }
        return TOKEN_MAP
    }

    /**
     * Finds test case endpoint. Default endpoint is defined in the .env file.
     * Individual test cases can override default with property "endpoint"
     *
     * @param testCase - Case being tested.
     */
    private String getTestCaseEndPoint(WsdlTestCase testCase) {
        // See if test case has endpoint override...
        def props = testCase.testSteps["props"]
        if(props.hasProperty("endpoint")) {
            return props.getProperty("endpoint").getValue()
        }
        // Send default endpoint (.env)
        return ENV.get("ENDPOINT")
    }

    /**
     * Responsible for setting endpoint and endpoint authorization for individual test
     * cases.
     *
     * @param log - SoapUI logger.
     * @param testCase Case being tested.
     * @param projectPath - path to current running SoapUI project
     */
    public TestCaseAuthorization(Logger log, WsdlTestCase testCase, String projectPath) {
        def ENV = getEnvironmentValues(projectPath)
        def TOKEN_MAP = getTokenMap()
        log.info("Authorizing Test Case: " + testCase.getLabel())
        for (testStep in testCase.getTestStepList()) {
            if (testStep instanceof RestTestRequestStep) {
                def headers = new StringToStringMap()
                testStep.getHttpRequest().setEndpoint(getTestCaseEndPoint(testCase))
                if (testStep.getHttpRequest().getEndpoint() ==~ /(?s).*dev.*/) {
                    String key = "DEV" + ENV.get("USER_TYPE").toString()
                    String devTokens = TOKEN_MAP.get(key).toString();
                    headers.put("Authorization", "Bearer " + devTokens.replaceAll("\"", ""))
                } else if (testStep.getHttpRequest().getEndpoint() ==~ /(?s).*imp.*/) {
                    String key = "IMP" + ENV.get("USER_TYPE").toString()
                    String impTokens = TOKEN_MAP.get(key).toString();
                    headers.put("Authorization", "Bearer " + impTokens.replaceAll("\"", ""))
                }
                testStep.getHttpRequest().setRequestHeaders(headers)
            }
        }
    }
}
