package com.nottrilix.analysis;

/** Model nije dostupan ili nije vratio upotrebljiv nalaz. API je mapira na HTTP 503. */
public class AnalysisFailedException extends RuntimeException {

    public AnalysisFailedException(String message) {
        super(message);
    }
}
