package com.eviware.soapui

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

  public void cfgSnippets() {
    this.addXSnippet('MSET_TEMPLATE', ' {"programName": "mips","category":"${category}","performanceStart":"${perf_start}","performanceEnd":"${perf_end}","submissionMethod": "${sub_method}","measurements":[${MEASUREMENTS}]}')
    this.addXSnippet('NONPROP_MEASURE_TPL', '{"measureId": "${measure_id}","value": {"isEndToEndReported": ${end_to_end},"numerator": ${numerator},"denominator": ${denominator},"denominatorException": ${denominator_exc},"numeratorExclusion":${numerator_exc}}}')
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
    this.addXSnippet('PROP_MEASURE_TPL', '{"measureId":"${measure_id}","value":{"isEndToEndReported":${end_to_end},"performanceMet":${perf_met},"eligiblePopulationException":${perf_excep},"eligiblePopulationExclusion":${perf_exclu},"performanceNotMet":${perf_not_met},"performanceRate":${performanceRate},"eligiblePopulation":${pop_total}}}')
    this.addXSnippet('PROP_MULTI_MEASURE_TPL', '{"measureId":"${measure_id}","value":{"isEndToEndReported":${end_to_end},"performanceRate":${performanceRate},"strata":[${STRATUM}]}}')
  }

  public ScenarioReader(String folder, String inputFileName, String outputFileName) {
    this.cfgSnippets()
    this.inputFile = new File(folder, inputFileName);
    this.outputFile = new File(folder, outputFileName);
    inputFile.eachLine {line, i ->
      println(line)
      if (i == 1) {
        this.header = line.split("\t");
      } else {
        def s = toScenario(line);
        this.addScenario(s);
      }
    }
    this.outputFile.delete();
    this.outputFile << "test_status\t${this.header.join('\t')}\n"
    this.it = this.scenarioMap.iterator();
  }

  public boolean isEmptyOrNull(x) {
    return (x == '' || x == null)
  }

  public void setPropsStep(def props) {
    this.propsStep = props;
  }

  public void addSinglePerformanceMeasureSnippet(String measureSnippet){
    SNIPPETS.put("SINGLE_MEASURE", TEMPLATE_ENGINE.createTemplate(measureSnippet));
  }
  public void addMultiPerformanceMeasureSnippet(String measureSnippet, String stratumSnippet) {
    SNIPPETS.put('MULTI_MEASURE', TEMPLATE_ENGINE.createTemplate(measureSnippet))
    SNIPPETS.put('STRATUM', TEMPLATE_ENGINE.createTemplate(stratumSnippet));
  }

  public void addMeasurementSetSnippet(String msetSnippet) {
    SNIPPETS.put('MSET_TEMPLATE', TEMPLATE_ENGINE.createTemplate(msetSnippet));
  }

  public void addXSnippet(String name, String newSnippet) {
    SNIPPETS.put(name, TEMPLATE_ENGINE.createTemplate(newSnippet));
  }

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

  public String[] getHeader() {
    this.header;
  }

  private Scenario toScenario(String line) {
    String[] parts = line.split("\t");
    def data = [:]
    getHeader().eachWithIndex { k, i ->
      data[k] = parts.length > i ? parts[i]?.trim() : null;
    }
    return new Scenario(this.header, data[scenarioId], data);
  }

  public String toMeasurements(List<Scenario> list, String msetConstraint) {
    def measureList = [];
    list.each { s ->
      def measureCategory = s.data.get('category')
      if (s.data.get('mset_id') != msetConstraint) { return; }
      if (measureCategory == 'aci') {
        if (s.data.get('value') != '') {
          measureList << s.eval(SNIPPETS['ACI_MEASURE_TEMPLATE'])
        } else if (s.data.get('value_numerator') != '' && s.data.get('value_denominator') != '') {
          measureList << s.eval(SNIPPETS['ACI_ALT_MEASURE_TEMPLATE'])
        }
      } else if (measureCategory == 'ia') {
        if (!this.isEmptyOrNull(s.data.get('value'))) {
          measureList << s.eval(SNIPPETS['ACI_MEASURE_TEMPLATE'])
        } else if (!this.isEmptyOrNull(s.data.get('value_numerator')) && !this.isEmptyOrNull(s.data.get('value_denominator'))) {
          measureList << s.eval(SNIPPETS['ACI_ALT_MEASURE_TEMPLATE'])
        }
      } else if (measureCategory == 'quality') {
        if(s.children.size() > 0) {
          if (!this.isEmptyOrNull(s.data.get('indexAdmissionCode')) && !this.isEmptyOrNull(s.data.get('readmissionCode')) && !this.isEmptyOrNull(s.data.get('score'))) {
            def Set acrList = [];
            def Set indexAdmissionCodes = [];
            def Set readmissionCodes = [];
            s.children.each { c->
              acrList << s.eval(SNIPPETS['IDX_READD_PAIR_COUNTS']);
              indexAdmissionCodes << s.eval(SNIPPETS['ACR_Idx_COUNTS']);
              readmissionCodes << s.eval(SNIPPETS['ACR_Readd_COUNTS']);
            }
            s.data.put('IDX_READD_PAIR_COUNTS', acrList.join(','))
            s.data.put('idxAdminCodes', indexAdmissionCodes.join(','))
            s.data.put('readdCodes', readmissionCodes.join(','))
            measureList << s.eval(SNIPPETS['ACR_MEASURE'])
          } else {
            def stratumList = [];
            stratumList << s.eval(SNIPPETS['STRATUM'])
            s.children.each { c->
              stratumList << c.eval(SNIPPETS['STRATUM'])
            }
            s.data.put('STRATUM', stratumList.join(','))
            if (!this.isEmptyOrNull(s.data.get('performanceRate'))) {
              measureList << s.eval(SNIPPETS['PROP_MULTI_MEASURE_TPL'])
            } else {
              measureList << s.eval(SNIPPETS['MULTI_MEASURE'])
            }
          }
        } else {
          if (!this.isEmptyOrNull(s.data.get('cahps_reliability')) && !this.isEmptyOrNull(s.data.get('cahps_mask')) && !this.isEmptyOrNull(s.data.get('cahps_isBelowMinimum'))) {
            measureList << s.eval(SNIPPETS['CAHPS_MEASURE_TPL'])
          } else if (!this.isEmptyOrNull(s.data.get('end_to_end')) && !this.isEmptyOrNull(s.data.get('numerator')) && !this.isEmptyOrNull(s.data.get('denominator')) && !this.isEmptyOrNull(s.data.get('denominator_exc')) && !this.isEmptyOrNull(s.data.get('numerator_exc'))) {
            measureList << s.eval(SNIPPETS['NONPROP_MEASURE_TPL'])
          } else if (!this.isEmptyOrNull(s.data.get('performanceRate'))) {
            measureList << s.eval(SNIPPETS['PROP_MEASURE_TPL'])
          } else {
            measureList << s.eval(SNIPPETS['SINGLE_MEASURE'])
          }
        }
      }
    }
    return measureList.join(',');
  }

  public String toMeasurementSets(List<Scenario> list) {
    def Set msetList = [];
    list.each { s ->
      def msetName = s.data.get('mset_id')
      s.data.put('MEASUREMENTS', '${MEASUREMENTS_' + msetName + '}' )
      msetList << s.eval(SNIPPETS['MSET_TEMPLATE'])
    }
    return msetList.join(',');
  }

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

  public void writeScenarioStatus(String status, List<Scenario> scenarios) {
    scenarios.each {s ->
      outputFile << s.toLine(status)
    }
  }

  public boolean hasNext() {
    this.it.hasNext();
  }

  public void next() {
    this.currentScenarioList = this.it.next().value;
    copyScenarioProperties(this.propsStep, this.currentScenarioList)
  }
}
