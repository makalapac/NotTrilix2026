package com.nottrilix.analysis;

/** The model is unavailable or did not return a usable analysis result. The API maps it to HTTP 503. */
public class AnalysisFailedException extends RuntimeException {

    public AnalysisFailedException(String message) {
        super(message);
    }
}
