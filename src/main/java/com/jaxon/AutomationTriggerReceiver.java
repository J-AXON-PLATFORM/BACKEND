package com.jaxon;

/**
 * Public in-process Automation contract. Producers invoke it inside their delivery transaction so
 * trigger acceptance and producer delivery state are committed together.
 */
public interface AutomationTriggerReceiver {

    AutomationTriggerOutcome accept(AutomationTrigger trigger);
}
