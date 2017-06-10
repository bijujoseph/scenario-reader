package com.eviware.soapui
class Scenario {
    String[] header;
    String scenarioId;
    def data = [:]
    List<Scenario> children = [];

    Scenario(String[] header, String scenarioId, def data) {
        this.header = header;
        this.data = data;
        this.scenarioId = scenarioId;
    }

    public Scenario addChildScenario(Scenario s){
        this.children << s;
        return this;
    }

    public String toLine(String status) {
        StringBuilder line = new StringBuilder();
        line.append(status);
        this.header.each{k->
            line.append('\t').append(data[k])
        }
        line.append('\n');
        this.children.each {c->
            line.append(c.toLine(status))
        }
        return line.toString();
    }

    public String eval(def template) {
        return template.make(this.data).toString();
    }

    public Map<String, String> getProperties() {
        def p = this.children.size() > 0 ? this.children.last().data : this.data;
        def m = [:]
        p.each {k,v ->
            if (v instanceof String) {
                m.put(k, v);
            }
        }
        return m;
    }


}