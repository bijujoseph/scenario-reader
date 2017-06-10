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

    public String toMeasurements(List<Scenario> list) {
        def measureList = [];
        list.each { s ->
            if(s.children.size() > 0) {
                def stratumList = [];
                stratumList << s.eval(SNIPPETS['STRATUM'])
                s.children.each { c->
                    stratumList << c.eval(SNIPPETS['STRATUM'])
                }
                s.data.put('STRATUM', stratumList.join(','))
                measureList << s.eval(SNIPPETS['MULTI_MEASURE'])
            } else {
                measureList << s.eval(SNIPPETS['SINGLE_MEASURE'])
            }
        }
        return measureList.join(',');
    }

    public String copyScenarioProperties(def propsStep, List<Scenario> scenarios) {
        if (scenarios.size() > 0) {
            def last = scenarios.last();
            last.getProperties().each {k,v ->
                propsStep.setPropertyValue(k, v);
            }
            propsStep.setPropertyValue('MEASUREMENTS', this.toMeasurements(scenarios))
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
