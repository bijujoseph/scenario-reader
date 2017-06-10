import com.eviware.soapui.ScenarioReader

public class ScenarioReaderTest {

    public static void main(String[] args) {

        // the following templates should be placed in "testcase properties" and pulled into variables during "Setup"

        String SINGLE_MEASURE_TEMPLATE = '{"measureId": "${measure_id}", "value": {"isEndToEndReported": ${end_to_end},"performanceMet": ${perf_met},  "performanceExclusion": ${perf_exc},"performanceNotMet": ${perf_not_met},"populationTotal": ${pop_total}}}';
        String MULTI_MEASURE_TEMPLATE = '{"measureId": "${measure_id}", "value": {"isEndToEndReported": ${end_to_end}, "strata": [${STRATUM}]}}';
        String STRATUM_TEMPLATE = '{ "performanceMet": ${perf_met},"performanceExclusion": ${perf_exc},"performanceNotMet": ${perf_not_met}, "populationTotal": ${pop_total}, "stratum":${stratum} }';

        // so to the INput file and output file
        def sr = new ScenarioReader(new File("test").absolutePath, "multi.tsv", "multi_out.tsv");

        //initialize the template
        sr.addMultiPerformanceMeasureSnippet(MULTI_MEASURE_TEMPLATE, STRATUM_TEMPLATE);
        sr.addSinglePerformanceMeasureSnippet(SINGLE_MEASURE_TEMPLATE);

        // put the iterator in context
        def it = sr.scenarioMap.iterator();


        //Below looping happens in the place where we had next line
        while(it.hasNext()) {
            def kv = it.next();
            def scenarios = kv.value;
            // copy the assertions into props step
//            sr.copyScenarioProperties(new WsdlPropertiesTestStep(), scenarios);

            // find the status of the test -- it may be failed or success
            sr.writeScenarioStatus("FAILED", scenarios) /// ask the scenario reader to write them in the output file.
        }



    }
}