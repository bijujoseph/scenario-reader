package com.eviware.soapui;

/* ScenarioReader
** :: Notes ::
** ScenarioReader is a class for processing .TSV files within the SoapUI framework and converting each row or group of rows into JSON to be saved as properties in the "prop" file of the corresponding scenario.
**
** There are two primary workflows used by SoapUI. Initialization, Setting Props, Documenting Results.
** -- Initialization --
** ScenarioReader -> toScenario() -> addScenario()
** -- Setting Props --
** next() -> copyScenarioProperties() -> toMeasurementSets() -> toMeasurements()
** -- Documenting Results --
** writeScenarioStatus()
**
** -- Prereq --
** Needs access to the Scenario class located ./Scenario.groovy.
**
** ScenarioReader :: Class
** @param folder         -- Absolute path of the ScenarioReader project
** @param inputFileName  -- Path to input file for data processing
** @param outputFileName -- Path to output file for saving results
*/
class ScenarioReader {
  static TEMPLATE_ENGINE = new groovy.text.SimpleTemplateEngine()
  static SNIPPETS = [:]

  private File inputFile;
  private File outputFile;
  private BufferedReader reader;
  private BufferedWriter writer;
  private String[] header;
  private String line;
  private scenarioId = 'scenario_id';
  private measureId = 'measure_id';
  private msetId = 'mset_id';
  public Map<String, List<Scenario>> scenarioMap = [:];
  public Iterator it;
  def propsStep;
  List<Scenario> currentScenarioList;

  // cfgSnippets -- A function for configuring all measurement templates to later be used in toMeasurementSets() and toMeasurements()
  public void cfgSnippets() {
    this.addXSnippet('MSET_TEMPLATE', ' {"programName": "${program_name}","providerId": "${provider_id}","practiceId": "${practice_id}","suppressed": "${is_suppressed}","cehrtId":"${cehrtId}","category":"${category}","performanceStart":"${perf_start}","performanceEnd":"${perf_end}","submissionMethod": "${sub_method}","measurements":[${MEASUREMENTS}]}')
    this.addXSnippet('NONPROP_MEASURE_TPL', '{"measureId": "${measure_id}","value": {"isEndToEndReported": ${end_to_end},"numerator": ${numerator},"denominator": ${denominator},"denominatorException": ${denominator_exc},"numeratorExclusion":${numerator_exc},"reportingRate":${reporting_rate},"performanceRate":${performanceRate}}}')
    this.addXSnippet('2017_NONPROP_MEASURE_TPL', '{"measureId": "${measure_id}","value": {"isEndToEndReported": ${end_to_end},"numerator": ${numerator},"denominator": ${denominator},"denominatorException": ${denominator_exc},"numeratorExclusion":${numerator_exc}}}')
    this.addXSnippet('SINGLE_MEASURE', '{"measureId":"${measure_id}","value":{"isEndToEndReported":${end_to_end},"performanceMet":${perf_met},"eligiblePopulationException":${perf_excep},"eligiblePopulationExclusion":${perf_exclu},"performanceNotMet":${perf_not_met},"eligiblePopulation":${pop_total}}}')
    this.addXSnippet('MULTI_MEASURE', '{"measureId":"${measure_id}","value":{"isEndToEndReported":${end_to_end},"strata":[${STRATUM}]}}')
    this.addXSnippet('STRATUM', '{"performanceMet":${perf_met},"eligiblePopulationException":${perf_excep},"eligiblePopulationExclusion":${perf_exclu},"performanceNotMet":${perf_not_met},"eligiblePopulation":${pop_total},"stratum":"${stratum}"}')
    this.addXSnippet('ACI_MEASURE_TEMPLATE', '{"measureId":"${measure_id}","value":${value}}')
    this.addXSnippet('ACI_ALT_MEASURE_TEMPLATE', '{"measureId":"${measure_id}","value":{"numerator":${value_numerator},"denominator":${value_denominator}}}')
    this.addXSnippet('IDX_READD_PAIR_COUNTS', '{"indexAdmissionCode":"${indexAdmissionCode}","readmissionCode":"${readmissionCode}","count":${count}}')
    this.addXSnippet('ACR_Idx_COUNTS', '{"code":"${indexAdmissionCode}","count":${count}}')
    this.addXSnippet('ACR_Readd_COUNTS', '{"code":"${readmissionCode}","count":${count}}')
    this.addXSnippet('ACR_MEASURE', '{"measureId":"${measure_id}","value":{"score":${score},"details":{"numberOfIndexAdmissions":${numberOfIndexAdmissions},"numberOfReadmissions":${numberOfReadmissions},"indexReadmissionDiagnosisPairCounts":[${IDX_READD_PAIR_COUNTS}],"indexAdmissionCountByDiagnosis":[${idxAdminCodes}],"readmissionCountByDiagnosis":[${readdCodes}],"plannedReadmissions":${plannedReadmissions}}}}')
    this.addXSnippet('CAHPS_MEASURE_TPL', '{"measureId":"${measure_id}","value":{"score":${score},"reliability":"${cahps_reliability}","mask":${cahps_mask},"isBelowMinimum":${cahps_isBelowMinimum}}}')
    this.addXSnippet('PROP_MEASURE_TPL', '{"measureId":"${measure_id}","value":{"isEndToEndReported":${end_to_end},"performanceMet":${perf_met},"eligiblePopulationException":${perf_excep},"eligiblePopulationExclusion":${perf_exclu},"performanceNotMet":${perf_not_met},"reportingRate":${reportingRate},"performanceRate":${performanceRate},"eligiblePopulation":${pop_total}}}')
    this.addXSnippet('PROP_MULTI_MEASURE_TPL', '{"measureId":"${measure_id}","value":{"isEndToEndReported":${end_to_end},"reportingRate":${reportingRate},"performanceRate":${performanceRate},"strata":[${STRATUM}]}}')
    this.addXSnippet('COST_MEASURE_TEMPLATE', '{"measureId":"${measure_id}","value":{"score":${score},"details":{"ratio":${ratio},"eligibleOccurrences":${eligibleOccurrences},"costPerOccurrence":${costPerOccurrence}}}}')
  }

  // ScenarioReader -- Class used to initialize all necessary files
  public ScenarioReader(String folder, String inputFileName, String outputFileName) {
    this.cfgSnippets()
    this.inputFile = new File(folder, inputFileName);
    this.outputFile = new File(folder, outputFileName);
    inputFile.eachLine {line, i ->
      println(line)
      if (i == 1) {
        // First line of the TSV is the columns. We're converting this into an array to later be turned into Prop keys.
        this.header = line.split("\t");
      } else {
        // Converting TSV row into a Scenario class.
        def s = toScenario(line);
        // Add Scenario classes to scenarioMap variable. Assigns Scenarios to the Scenario.children array if there are duplicate MeasurementIDs in the case of Stratum
        this.addScenario(s);
      }
    }
    this.outputFile.delete();
    this.outputFile << "test_status\t${this.header.join('\t')}\n"
    this.it = this.scenarioMap.iterator();
  }

  // isEmptyOrNull :: a -> Bool
  public boolean isEmptyOrNull(x) {
    return (x == '' || x == null)
  }

  // setPropsStep -- Function for defining SoapUI props within the class to later be manipulated and read by toMeasurementSets() and toMeasurements()
  public void setPropsStep(def props) {
    this.propsStep = props;
  }

  // addSinglePerformanceMeasureSnippet -- Deprecated legacy function since replaced by addXSnippet() & cfgSnippets()
  public void addSinglePerformanceMeasureSnippet(String measureSnippet){
    SNIPPETS.put("SINGLE_MEASURE", TEMPLATE_ENGINE.createTemplate(measureSnippet));
  }

  // addMultiPerformanceMeasureSnippet -- Deprecated legacy function since replaced by addXSnippet() & cfgSnippets()
  public void addMultiPerformanceMeasureSnippet(String measureSnippet, String stratumSnippet) {
    SNIPPETS.put('MULTI_MEASURE', TEMPLATE_ENGINE.createTemplate(measureSnippet))
    SNIPPETS.put('STRATUM', TEMPLATE_ENGINE.createTemplate(stratumSnippet));
  }

  // addMeasurementSetSnippet -- Deprecated legacy function since replaced by addXSnippet() & cfgSnippets()
  public void addMeasurementSetSnippet(String msetSnippet) {
    SNIPPETS.put('MSET_TEMPLATE', TEMPLATE_ENGINE.createTemplate(msetSnippet));
  }

  /* addXSnippet -- A function for creating snippets or templates. Primarily used to set templates for measurements or basic JSON structures for API calls.
  ** -- Note --
  ** In the newSnippet string, values wrapped in ${} are replaced by the corresponding PROP name either defined in code or the column name in the TSV.
  **
  ** @param name       :: String -- ex. "MEASUREMENT_TEMPLATE"
  ** @param newSnippet :: String -- ex. '{ "name": ${MEASURE_NAME}, "value": ${MEASURE_VALUE}}'
  */
  public void addXSnippet(String name, String newSnippet) {
    SNIPPETS.put(name, TEMPLATE_ENGINE.createTemplate(newSnippet));
  }

  // addScenario -- Add Scenario classes to scenarioMap variable. Assigns Scenarios to the Scenario.children array if there are duplicate MeasurementIDs in the case of Stratum
  private void addScenario(Scenario s) {
    if (scenarioMap.containsKey(s.scenarioId)) {
      List<Scenario> scenarios = scenarioMap[s.scenarioId];
      def last = scenarios.last();
      if (last != null && last.getData().get(measureId).equals(s.getData().get(measureId))) {
        last.addChildScenario(s);
      } else {
        scenarios << s;
      }
    } else {
      scenarioMap.put(s.scenarioId, [s]);
    }
  }

  // getHeader :: [ String ]
  public String[] getHeader() {
    this.header;
  }

  // Convert TSV row into Scenario class and groups them based on the `scenario_id` column
  private Scenario toScenario(String line) {
    String[] parts = line.split("\t");
    def data = [:]
    getHeader().eachWithIndex { k, i ->
      data[k] = parts.length > i ? parts[i]?.trim() : null;
    }
    return new Scenario(this.header, data[scenarioId], data);
  }

  /* toMeasurements -- Receives an array of Scenarios and the value under `mset_id`
  ** to set Props specific for generating measurements in the proper measurementSet in the Submissions Obj for `score-preview`
  **
  ** @param list           :: [Scenario]
  ** @param msetConstraint :: String
  */
  public String toMeasurements(List<Scenario> list, String msetConstraint) {
    def measureList = [];
    list.each { s ->
      def measureCategory = s.data.get('category')
      def perfYear = s.data.get('perf_year')
      if (s.data.get('mset_id') != msetConstraint) { return; }
      // Begin ACI Measurement Creation
      if (measureCategory == 'aci' || measureCategory == 'pi') {
        if (s.data.get('value') != '') {
          measureList << s.eval(SNIPPETS['ACI_MEASURE_TEMPLATE'])
        } else if (s.data.get('value_numerator') != '' && s.data.get('value_denominator') != '') {
          measureList << s.eval(SNIPPETS['ACI_ALT_MEASURE_TEMPLATE'])
        }
      // End ACI Measurement Creation
      // Begin IA Measurement Creation
      } else if (measureCategory == 'ia') {
        if (!this.isEmptyOrNull(s.data.get('value'))) {
          measureList << s.eval(SNIPPETS['ACI_MEASURE_TEMPLATE'])
        } else if (!this.isEmptyOrNull(s.data.get('value_numerator')) && !this.isEmptyOrNull(s.data.get('value_denominator'))) {
          measureList << s.eval(SNIPPETS['ACI_ALT_MEASURE_TEMPLATE'])
        }
      // End IA Measurement Creation
      // Begin Quality Measurement Creation
      } else if (measureCategory == 'quality') {
        if(s.children.size() > 0) {
          // ACR Measures
          if (!this.isEmptyOrNull(s.data.get('indexAdmissionCode')) && !this.isEmptyOrNull(s.data.get('readmissionCode')) && !this.isEmptyOrNull(s.data.get('score'))) {
            def Set acrList = [];
            def Set indexAdmissionCodes = [];
            def Set readmissionCodes = [];
            if (s.data.get('measureId') == 'MCC1') {
              measureList << s.eval(SNIPPETS['PROP_MULTI_MEASURE_TPL'])
            } else {
              s.children.each { c->
                acrList << s.eval(SNIPPETS['IDX_READD_PAIR_COUNTS']);
                indexAdmissionCodes << s.eval(SNIPPETS['ACR_Idx_COUNTS']);
                readmissionCodes << s.eval(SNIPPETS['ACR_Readd_COUNTS']);
              }
              s.data.put('IDX_READD_PAIR_COUNTS', acrList.join(','))
              s.data.put('idxAdminCodes', indexAdmissionCodes.join(','))
              s.data.put('readdCodes', readmissionCodes.join(','))
              measureList << s.eval(SNIPPETS['ACR_MEASURE'])
            }
          } else {
            // Stratum Measures
            def stratumList = [];
            stratumList << s.eval(SNIPPETS['STRATUM'])
            s.children.each { c->
              stratumList << c.eval(SNIPPETS['STRATUM'])
            }
            s.data.put('STRATUM', stratumList.join(','))
            // Proportional Stratum Measures
            if (!this.isEmptyOrNull(s.data.get('performanceRate'))) {
              measureList << s.eval(SNIPPETS['PROP_MULTI_MEASURE_TPL'])
            // Stratum Measures
            } else {
              measureList << s.eval(SNIPPETS['MULTI_MEASURE'])
            }
          }
        } else {
          // CAHPS Measure
          if (!this.isEmptyOrNull(s.data.get('cahps_reliability')) && !this.isEmptyOrNull(s.data.get('cahps_mask')) && !this.isEmptyOrNull(s.data.get('cahps_isBelowMinimum'))) {
            measureList << s.eval(SNIPPETS['CAHPS_MEASURE_TPL'])
          // NonProportional Measure
          } else if (!this.isEmptyOrNull(s.data.get('end_to_end')) && !this.isEmptyOrNull(s.data.get('numerator')) && !this.isEmptyOrNull(s.data.get('denominator')) && !this.isEmptyOrNull(s.data.get('denominator_exc')) && !this.isEmptyOrNull(s.data.get('numerator_exc'))) {
            measureList << s.eval((perfYear == '2017') ? SNIPPETS['2017_NONPROP_MEASURE_TPL'] : SNIPPETS['NONPROP_MEASURE_TPL'])
          // Proportional Measure
          } else if (!this.isEmptyOrNull(s.data.get('performanceRate'))) {
            measureList << s.eval(SNIPPETS['PROP_MEASURE_TPL'])
          // Standard Single Measure
          } else {
            measureList << s.eval(SNIPPETS['SINGLE_MEASURE'])
          }
        }
      } else if (measureCategory == 'cost') {
        if (!this.isEmptyOrNull(s.data.get('score')) && !this.isEmptyOrNull(s.data.get('ratio')) && !this.isEmptyOrNull(s.data.get('eligibleOccurrences')) && !this.isEmptyOrNull(s.data.get('costPerOccurrence'))) {
          measureList << s.eval(SNIPPETS['COST_MEASURE_TEMPLATE'])
        }
      }
      // End Quality Measurement Creation
    }
    return measureList.join(',');
  }

  // toMeasurementSets -- Responsible for creating Props for multiple measurementSets defined by the `mset_id` column.
  public String toMeasurementSets(List<Scenario> list) {
    def Set msetList = [];
    list.each { s ->
      def msetName = s.data.get('mset_id')
      if (this.isEmptyOrNull(s.data.get('provider_id'))) {
        s.data.put('provider_id', '')
      }
      if (this.isEmptyOrNull(s.data.get('program_name'))) {
        s.data.put('program_name', 'mips')
      }
      if (this.isEmptyOrNull(s.data.get('is_suppressed'))) {
        s.data.put('is_suppressed', 'false')
      }
      if (this.isEmptyOrNull(s.data.get('practice_id'))) {
        s.data.put('practice_id', '')
      }
      if (this.isEmptyOrNull(s.data.get('cehrtId'))) {
        s.data.put('cehrtId', '')
      }
      s.data.put('MEASUREMENTS', '${MEASUREMENTS_' + msetName + '}' )
      msetList << s.eval(SNIPPETS['MSET_TEMPLATE'])
    }
    return msetList.join(',');
  }

  // copyScenarioProperties -- Responsible for creating and updating all Props in the `prop` test step.
  public String copyScenarioProperties(def propsStep, List<Scenario> scenarios) {
    if (scenarios.size() > 0) {
      def last = scenarios.last();
      last.getProperties().each {k,v ->
        propsStep.setPropertyValue(k, v);
      }
      propsStep.setPropertyValue('MSETS', this.toMeasurementSets(scenarios))

      def Set mSetsMeasurementProps = [];
      scenarios.each { s ->
        mSetsMeasurementProps << ['MEASUREMENTS_' + s.data.get('mset_id'), s.data.get('mset_id')]
      }
      mSetsMeasurementProps.each { measurementProp ->
        propsStep.setPropertyValue(measurementProp[0], this.toMeasurements(scenarios, measurementProp[1]))
      }
    }
  }

  // writeScenarioStatus -- Writes results of test scenarios to the assigned output file.
  public void writeScenarioStatus(String status, List<Scenario> scenarios) {
    scenarios.each {s ->
      outputFile << s.toLine(status)
    }
  }

  // Checks iterator defined in ScenarioReader to see if the end of the TSV has been met.
  public boolean hasNext() {
    this.it.hasNext();
  }

  // Prepares next set of scenarios grouped by `scenario_id`, configures props, and sets the stage for the test steps to be run again.
  public void next() {
    this.currentScenarioList = this.it.next().value;
    copyScenarioProperties(this.propsStep, this.currentScenarioList)
  }

  // Make SoapUI Client Happy
  public void close() { }
}
