package com.github.wallev.maidsoulkitchen.entity.data.inner.task;

import java.util.List;

public abstract class FarmData implements ITaskData{

    protected List<String> rules;

    public FarmData(List<String> rules) {
        this.rules = CookDataRules.normalizeRecipes(rules);
    }

    protected void setRules(List<String> rules) {
        this.rules = CookDataRules.normalizeRecipes(rules);
    }

    public List<String> rules() {
        return java.util.Collections.unmodifiableList(rules);
    }

    public void addRule(String rule) {
        this.rules.add(rule);
    }

    public void removeRule(String rule) {
        this.rules.remove(rule);
    }

    public void addOrRemoveRule(String rule) {
        if (this.rules.contains(rule)) {
            this.rules.remove(rule);
        } else {
            this.rules.add(rule);
        }
    }
}
