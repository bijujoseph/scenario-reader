package com.eviware.soapui

def groovyUtils=new  com.eviware.soapui.support.GroovyUtils(context)
def projectPath = groovyUtils.projectPath
def testCase = testRunner.testCase;

def props = testRunner.testCase.testSteps["props"];

def SINGLE_MEASURE_TEMPLATE = testCase.getPropertyValue("single_measure_tpl")
def MULTI_MEASURE_TEMPLATE = testCase.getPropertyValue("multi_measure_tpl")
def STRATUM_TEMPLATE = testCase.getPropertyValue("stratum_tpl")
def MSET_TEMPLATE = testCase.getPropertyValue("mset_tpl")
def ACR_MEASURE_TPL = testCase.getPropertyValue("ACR_MEASURE_TPL")
def ACR_MEASURE_PAIR_TPL = testCase.getPropertyValue("ACR_MEASURE_PAIR_TPL")
def ACR_IDX_MEASURE_TPL = testCase.getPropertyValue("ACR_IDX_MEASURE_TPL")
def ACR_READD_MEASURE_TPL = testCase.getPropertyValue("ACR_READD_MEASURE_TPL")

if (System.properties['os.name'].toLowerCase().contains('windows')) {
	inputFileName = testCase.getPropertyValue("input_file").replace("/", "\\")
	outputFileName = testCase.getPropertyValue("output_file").replace("/", "\\")
} else { 
	inputFileName = testCase.getPropertyValue("input_file").replace("\\", "/")
	outputFileName = testCase.getPropertyValue("output_file").replace("\\", "/")
}
def projectFolder = new File(projectPath)

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

    public ScenarioReader(String folder, String inputFileName, String outputFileName) {
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
      	  	if (s.data.get('value') != '') {
        	  		measureList << s.eval(SNIPPETS['ACI_MEASURE_TEMPLATE'])
        	  	} else if (s.data.get('value_numerator') != '' && s.data.get('value_denominator') != '') {
        	  		measureList << s.eval(SNIPPETS['ACI_ALT_MEASURE_TEMPLATE'])
        	  	}
        	  } else if (measureCategory == 'quality') {
        	      if(s.children.size() > 0) {
        	      	if (s.data.get('indexAdmissionCode') != '' && s.data.get('readmissionCode') != '' && s.data.get('code') != '') {
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
                		measureList << s.eval(SNIPPETS['MULTI_MEASURE'])
        	      	}
           	 } else {
              	  measureList << s.eval(SNIPPETS['SINGLE_MEASURE'])
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
