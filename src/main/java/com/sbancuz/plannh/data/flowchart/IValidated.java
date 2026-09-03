package com.sbancuz.plannh.data.flowchart;

public interface IValidated {

    /**
     * @return true if a field has been incorrectly loaded. This usally means checking for null values.
     */
    boolean invalid();
}
