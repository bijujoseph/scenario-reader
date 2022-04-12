package com.eviware.soapui.reports

import com.eviware.soapui.impl.wsdl.WsdlTestSuite
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestRunContext
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStep
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStepResult
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionContext
import com.eviware.soapui.model.testsuite.TestCase
import com.eviware.soapui.model.testsuite.TestCaseRunner
import com.eviware.soapui.ScenarioReader
import com.eviware.soapui.model.testsuite.TestStep
import com.eviware.soapui.support.GroovyUtils
import com.eviware.soapui.model.testsuite.TestStepResult
import com.eviware.soapui.model.testsuite.TestSuite
import com.eviware.soapui.model.project.Project
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCaseRunner

import groovy.json.JsonOutput

import org.apache.log4j.Logger;

class SoapUIHistoryReport {

    /**
     * Responsible for constructing initial Scenario reader to read/process individual test case scenarios,
     * and write results to output files
     * */
    void setupTestCase(WsdlTestCaseRunner testRunner, WsdlTestRunContext context, Logger log) {
        log.info('Setting up test case: ' + testRunner.getTestCase().getName())
        GroovyUtils groovyUtils = new GroovyUtils(context)
        String projectPath = groovyUtils.projectPath
        TestCase testCase = testRunner.testCase;

        new com.eviware.soapui.TestCaseAuthorization(log, testCase, projectPath)

        TestStep props = testRunner.testCase.getTestStepByName('props');

        String osName = System.properties['os.name']
        boolean isWindows = osName.toLowerCase().contains('windows')

        // Setup input/output file pathing as per os
        def inputFile = testCase.getPropertyValue("input_file")
        def outputFile = testCase.getPropertyValue("output_file")
        String inputFileName = isWindows ? inputFile.replace("/", "\\") : inputFile.replace("\\", "/");
        String outputFileName= isWindows ? outputFile.replace("/", "\\") : outputFile.replace("\\", "/");

        File projectFolder = new File(projectPath)

        ScenarioReader sr = new ScenarioReader(projectFolder.absolutePath, inputFileName, outputFileName)
        sr.setPropsStep(props)

        context.setProperty('sr', sr)
        if (sr.hasNext()) sr.next()
    }

    /**
     * Responsible for closing down the test case and initializing the results report step
     * */
    void teardownTestCase(WsdlTestCaseRunner testRunner, WsdlTestRunContext context, Logger log) {
        log.info('Tearing down test case: ' + testRunner.getTestCase().getName())
        // Close the scenario reader
        if(context.hasProperty('sr')) {
            ((ScenarioReader)context.getProperty('sr')).close()
        }
        // Grab handle to SoapUI project
        Project project = testRunner.getTestCase().getTestSuite().getProject()
        // Find and run the reporting results test step
        TestStep reportingStep = project.getTestSuiteByName('Reporting')
                                        .getTestCaseByName('reporting_results')
                                        .getTestStepByName('generate-results-report')
        reportingStep.run(testRunner, context);
    }

    /**
     * Responsible for processing the next scenario within a test step
     * */
    void nextLine(WsdlTestCaseRunner testRunner, WsdlTestRunContext context, Logger log) {
        // Find current scenario id...
        TestStep props = testRunner.getTestCase().getTestStepByName('props')
        String scenarioId = props.getPropertyValue('scenario_id')

        log.info('Processing scenario: ' + scenarioId + ' for test case: ' + testRunner.getTestCase().getName())
        // Retrieve status of last scenario within test
        WsdlTestStepResult previewResult = testRunner.getResults()
                .find{ TestStepResult r -> r.getTestStep().getName().equalsIgnoreCase('score-preview') };
        WsdlTestStepResult errorResult = testRunner.getResults()
                .find{ TestStepResult r -> r.getStatus() == TestStepResult.TestStepStatus.FAILED  };
        if(errorResult) {
            previewResult.setStatus(TestStepResult.TestStepStatus.FAILED)
            for(String message : errorResult.getMessages()) {
                if( !previewResult.getMessages().contains(message) ){
                    previewResult.addMessage(message)
                }
            }
        }

        TestStepResult.TestStepStatus status = previewResult.getStatus();

        // Write scenario status to tsv output files
        ScenarioReader sr = context.getProperty('sr')
        sr.writeScenarioStatus(status.toString(), sr.currentScenarioList)

        // Add scenario result to list of results
        SoapUITestResult scenarioResult = new SoapUITestResult(scenarioId, testRunner.getTestCase().getTestSuite(), previewResult);
        List<SoapUITestResult> resultsList = context.hasProperty('scenarioResults')
                ? context.getProperty('scenarioResults')
                : new ArrayList<SoapUITestResult>()
        resultsList.add(scenarioResult)
        context.setProperty('scenarioResults', resultsList)

        // Go to next scenario
        if (sr.hasNext()) {
            sr.next()
            testRunner.gotoStep(0)
        }
    }

    /**
     * Responsible for constructing and adding execution results at the step level.
     * */
    void setupInitialHistoryFolders(TestSuite testSuite, PropertyExpansionContext context) {
        if(!context.hasProperty('historyPath')) {
            def projectPath = new com.eviware.soapui.support.GroovyUtils(context).projectPath
            def historyPath = projectPath.substring(0, projectPath.lastIndexOf(File.separator))

            /* 1) Create the path hierarchy for the history output folder */
            def folderPath = historyPath +
                    File.separator + 'soapui' +
                    File.separator + 'history' +
                    File.separator + testSuite.getProject().name.toUpperCase() +
                    File.separator + 'SOAPUI_' + (new Date()).format( 'MM-DD-YYYY_h_mm_ss' ) + File.separator
            new File(folderPath).mkdirs()

            // save the path to memory for downstream usage
            context.setProperty('historyPath', folderPath)
        }
    }

    /**
     * Responsible for deconstructing application specific properties.
     * */
    void tearDownSuite(PropertyExpansionContext context, Logger log) {
        log.info('Tearing Down Suite...')
        // clear out history folder properties
        context.removeProperty('historyPath')
    }

    /**
     * Responsible for constructing and adding execution results at the step level.
     * */
    void saveRequestResponseValues(WsdlTestCaseRunner testRunner, WsdlTestRunContext context, Logger log) {
        // Find the current test case from runner
        TestCase testCase = testRunner.getTestCase()

        log.info('Saving request/response values from test case: ' + testRunner.getTestCase().getName())

        WsdlTestStep scorePreviewStep = testCase.getTestStepByName('score-preview')

        // Find current scenario id from properties...
        WsdlTestStep props = testCase.getTestStepByName('props')
        String scenarioId = props.getPropertyValue('scenario_id')

        // Some scenario id's have forward slashes which messes with file pathing swap with dash...
        if(scenarioId) scenarioId = scenarioId.replace('/', '-')

        // grab path to history folder...
        String historyPath = context.getProperty('historyPath')

        if(historyPath) {
            // Save request/response from scenario run
            String testCasePath = historyPath + testCase.name.toUpperCase() + File.separator
            String scenarioPath = testCasePath + scenarioId + File.separator
            new File(scenarioPath).mkdirs()

            // Find the response value
            String response = scorePreviewStep.getPropertyValue('response')
            if(response){
                // define a file
                File file = new File(scenarioPath + scenarioId + '-score.json')
                String pretty = JsonOutput.prettyPrint(response)
                // get the response and write to file
                file.withWriter('utf-8') { writer -> writer.write(pretty) }
            }

            // Find the request value
            String request = scorePreviewStep.getPropertyValue('rawrequest')
            if(request){
                // define a file
                File file = new File(scenarioPath + scenarioId + '-submission.json')
                String pretty = JsonOutput.prettyPrint(request)
                // get the request and write to file
                file.withWriter('utf-8') { writer -> writer.write(pretty) }
            }

        }

    }

    /**
     * Responsible for constructing and adding execution results at the step level.
     * */
    void generateTestRunnerReport(TestCaseRunner testRunner, PropertyExpansionContext context, Logger log) {
        log.info('Generating results report for test case: ' + testRunner.getTestCase().getName())

        // Grab next step results list from context...
        List<SoapUITestResult> resultsList = context.getProperty('scenarioResults')
        // Find history folder path...
        String historyPath = context.getProperty('historyPath')

        if(historyPath) {
            // Create a File object for Report csv file (with timestamp)
            File reportFile = new File(historyPath, "TestRunnerResults.csv");
            // Check for existence of report file and create a file
            if(!reportFile.exists())
            {
                reportFile.createNewFile();
                // Create required column names in the report file
                reportFile.write('"Project","Test Suite","Test Case","Scenario Name","Test Step","Step Type","Step Status",'
                        +'"Result message","Execution Date"');
            }

            // Iterate over all the test steps results
            for(stepResult in resultsList)
            {
                // Creating new line in report file
                reportFile.append('\n');
                // Write all the necessary information in the file
                reportFile.append('"' + stepResult.projectName + '",');
                reportFile.append('"' + stepResult.suiteName + '",');
                reportFile.append('"' + stepResult.testCaseName + '",');
                reportFile.append('"' + stepResult.scenarioName + '",');
                reportFile.append('"score-preview",');
                reportFile.append('"' + stepResult.stepType + '",');
                reportFile.append('"' + stepResult.status + '",');
                reportFile.append('"' + stepResult.message + '",');
                reportFile.append('"' + stepResult.executedOn + '",');
            }
        }
    }
}

class SoapUITestResult {

    String projectName
    String suiteName
    String testCaseName
    String scenarioName
    String stepType
    String status
    String message
    String executedOn

    SoapUITestResult(String scenarioId, WsdlTestSuite testSuite, WsdlTestStepResult testStepResult) {
        this.projectName = testSuite.getProject().getName()
        this.suiteName = testSuite.getName()
        this.testCaseName = testStepResult.getTestStep().getTestCase().getName()
        this.scenarioName = scenarioId
        this.stepType = 'Rest Request'
        this.status = testStepResult.getStatus()
        StringBuffer buffer = new StringBuffer()
        for(String message in testStepResult.getMessages()) buffer.append(message + '\n')
        this.message = buffer.toString()
        this.executedOn = (new Date()).format('MM-dd-yyyy h:mm:ss')
    }

    String getProjectName() { return projectName }
    String getSuiteName() { return suiteName }
    String getTestCaseName() { return testCaseName }
    String getScenarioName() { return scenarioName }
    String getStepType() { return stepType }
    String getStatus() { return status }
    String getMessage() { return message }
    String getExecutedOn() { return executedOn }
}

